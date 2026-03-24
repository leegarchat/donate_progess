#!/bin/bash
# A small local builder for addon-hook into a DEX JAR.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

require_command() {
    local cmd="$1"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        echo "Error: command '$cmd' not found. Please install it and retry."
        return 1
    fi
}

require_file() {
    local path="$1"
    local hint="$2"
    if [[ ! -f "$path" ]]; then
        echo "Error: file '$path' not found. $hint"
        return 1
    fi
}

require_dir() {
    local path="$1"
    local hint="$2"
    if [[ ! -d "$path" ]]; then
        echo "Error: directory '$path' not found. $hint"
        return 1
    fi
}

check_java_version() {
    local raw
    local version
    local major
    raw="$(java -version 2>&1 | head -n 1)"
    version="$(echo "$raw" | sed -E 's/.*"([0-9]+)(\.[0-9]+\.[0-9_]+)?".*/\1/')"
    if [[ ! "$version" =~ ^[0-9]+$ ]]; then
        echo "Error: could not determine Java version from string: $raw"
        return 1
    fi
    major="$version"
    if (( major < 11 )); then
        echo "Error: Java 11+ is required, current: $raw"
        return 1
    fi
}

check_environment() {
    local missing=0

    require_command java || missing=1
    require_command javac || missing=1
    require_command jar || missing=1
    require_command find || missing=1
    require_command sort || missing=1
    require_command tail || missing=1
    require_command sed || missing=1
    require_command head || missing=1

    if (( missing != 0 )); then
        echo "Hint: for Ubuntu/WSL, 'sudo apt install openjdk-17-jdk' is usually sufficient."
        return 1
    fi

    check_java_version || return 1
    return 0
}

resolve_d8_jar() {
    local local_d8="$SCRIPT_DIR/prebuild/sdk/d8.jar"
    local explicit_d8="${D8_JAR:-}"
    local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    local best_sdk_d8=""

    find_best_d8_in_root() {
        local root="$1"
        local candidate=""
        [[ -z "$root" || ! -d "$root/build-tools" ]] && return 0
        candidate="$(find "$root/build-tools" -type f \( -path "*/lib/d8.jar" -o -path "*/d8.jar" \) 2>/dev/null | sort -V | tail -n 1)"
        [[ -n "$candidate" ]] && echo "$candidate"
    }

    if [[ -n "$explicit_d8" && -f "$explicit_d8" ]]; then
        echo "$explicit_d8"
        return 0
    fi

    if [[ -f "$local_d8" ]]; then
        echo "$local_d8"
        return 0
    fi

    best_sdk_d8="$(find_best_d8_in_root "$sdk_root")"
    if [[ -z "$best_sdk_d8" ]]; then
        best_sdk_d8="$(find_best_d8_in_root "/home/leegar/android-sdk")"
    fi
    if [[ -n "$best_sdk_d8" ]]; then
        echo "$best_sdk_d8"
        return 0
    fi

    return 1
}

resolve_android_jar() {
    local local_android_jar="$SCRIPT_DIR/prebuild/android.jar"
    local explicit_android_jar="${ANDROID_JAR:-}"
    local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
    local best_sdk_android_jar=""

    find_best_android_jar_in_root() {
        local root="$1"
        local candidate=""
        [[ -z "$root" || ! -d "$root/platforms" ]] && return 0
        candidate="$(find "$root/platforms" -type f -path "*/android-*/android.jar" 2>/dev/null | sort -V | tail -n 1)"
        [[ -n "$candidate" ]] && echo "$candidate"
    }

    if [[ -n "$explicit_android_jar" && -f "$explicit_android_jar" ]]; then
        echo "$explicit_android_jar"
        return 0
    fi

    if [[ -f "$local_android_jar" ]]; then
        echo "$local_android_jar"
        return 0
    fi

    best_sdk_android_jar="$(find_best_android_jar_in_root "$sdk_root")"
    if [[ -z "$best_sdk_android_jar" ]]; then
        best_sdk_android_jar="$(find_best_android_jar_in_root "/home/leegar/android-sdk")"
    fi
    if [[ -n "$best_sdk_android_jar" ]]; then
        echo "$best_sdk_android_jar"
        return 0
    fi

    return 1
}

show_help() {
    echo "Builds an addon module into a DEX JAR without requiring full Android SDK."
    echo ""
    echo "Usage:"
    echo "  $(basename "$0") <ADDON_NAME> [PROJECT_PATH]"
    echo ""
    echo "Parameters:"
    echo "  ADDON_NAME      Output JAR file name (without spaces)"
    echo "  PROJECT_PATH    Project directory (if not specified, current or ./<ADDON_NAME> is used)"
    echo ""
    echo "Examples:"
    echo "  $(basename "$0") test_project"
    echo "  $(basename "$0") my_hook ./my_hook"
    echo ""
    echo "You can override paths via environment variables:"
    echo "  ANDROID_JAR=/path/to/android.jar D8_JAR=/path/to/d8.jar $(basename "$0") my_hook"
    echo ""
    echo "By default, the script takes files from prebuild/ first, and only then searches the SDK."
    exit 0
}

if [[ $# -lt 1 || "$1" == "-h" || "$1" == "--help" ]]; then
    show_help
fi

ADDON_NAME="$1"
if [[ "$ADDON_NAME" =~ [[:space:]] ]]; then
    echo "Error: addon name must not contain spaces: '$ADDON_NAME'"
    exit 1
fi

# If path is explicitly provided, use it.
# Otherwise try a directory with the addon name next to the script, then current.
if [[ $# -ge 2 ]]; then
    PROJECT_ROOT="$2"
elif [[ -d "$SCRIPT_DIR/$ADDON_NAME" ]]; then
    PROJECT_ROOT="$SCRIPT_DIR/$ADDON_NAME"
else
    PROJECT_ROOT="$SCRIPT_DIR"
fi

PROJECT_ROOT="$(cd "$PROJECT_ROOT" && pwd)"

SRC_DIR="$PROJECT_ROOT/src"
META_DIR="$PROJECT_ROOT/META-INF"
OUT_DIR="$PROJECT_ROOT/out"
BUILD_DIR="$PROJECT_ROOT/build"

ANDROID_JAR="$(resolve_android_jar || true)"
PINE_XPOSED_JAR="$SCRIPT_DIR/prebuild/pine/pine-xposed.jar"
PINE_CORE_JAR="$SCRIPT_DIR/prebuild/pine/pine-core.jar"
XPOSED_API_JAR="$SCRIPT_DIR/prebuild/xposed/api-82.jar"
CORE_SRC="$SCRIPT_DIR/prebuild/IAddonHook.java"

D8_JAR="$(resolve_d8_jar || true)"

echo "=== Building addon: $ADDON_NAME ==="

echo "  Checking environment..."
check_environment

require_dir "$SRC_DIR" "Expected a project structure with Java sources."
require_dir "$META_DIR" "Expected a directory with META-INF/addon.json."
require_file "$META_DIR/addon.json" "Check the addon manifest."
require_file "$CORE_SRC" "The file should be in prebuild/IAddonHook.java."

if [[ ! -f "$PINE_XPOSED_JAR" ]]; then
    echo "  Warning: $PINE_XPOSED_JAR not found (compilation may fail if using Pine/Xposed API)."
fi
if [[ ! -f "$PINE_CORE_JAR" ]]; then
    echo "  Warning: $PINE_CORE_JAR not found (compilation may fail if using Pine Core classes)."
fi
if [[ ! -f "$XPOSED_API_JAR" ]]; then
    echo "  Warning: $XPOSED_API_JAR not found (compilation may fail if using Xposed API)."
fi

if [[ ! -f "$D8_JAR" ]]; then
    echo "Error: d8.jar not found. Place it in prebuild/sdk/d8.jar or set D8_JAR=/path/to/d8.jar"
    exit 1
fi

if [[ ! -f "$ANDROID_JAR" ]]; then
    echo "Error: android.jar not found. Place it in prebuild/android.jar or set ANDROID_JAR=/path/to/android.jar"
    exit 1
fi

echo "  Using android.jar: $ANDROID_JAR"
echo "  Using d8: $D8_JAR"

rm -rf "$BUILD_DIR" "$OUT_DIR"
mkdir -p "$BUILD_DIR/stubs" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$OUT_DIR"

echo "  [1/4] Compiling IAddonHook..."
# Keep bytecode within Java 11 (for min-api 26) and pass -parameters,
# to avoid an old d8 bug on some synthetic parameters.
javac -J-Dfile.encoding=UTF-8 -encoding UTF-8 --release 11 -parameters \
    -classpath "$ANDROID_JAR" \
    -d "$BUILD_DIR/stubs" \
    "$CORE_SRC"

echo "  [2/4] Compiling addon sources..."
ADDON_CP="$ANDROID_JAR:$BUILD_DIR/stubs"
[[ -f "$PINE_XPOSED_JAR" ]] && ADDON_CP="$ADDON_CP:$PINE_XPOSED_JAR"
[[ -f "$PINE_CORE_JAR" ]] && ADDON_CP="$ADDON_CP:$PINE_CORE_JAR"
[[ -f "$XPOSED_API_JAR" ]] && ADDON_CP="$ADDON_CP:$XPOSED_API_JAR"

find "$SRC_DIR" -name "*.java" > "$BUILD_DIR/sources.txt"

javac -J-Dfile.encoding=UTF-8 -encoding UTF-8 --release 11 -parameters \
    -classpath "$ADDON_CP" \
    -d "$BUILD_DIR/classes" \
    @"$BUILD_DIR/sources.txt"

echo "  [3/4] Converting to DEX..."

# d8 needs --lib android.jar for correct type resolution.
D8_CP_ARGS=()
[[ -f "$PINE_XPOSED_JAR" ]] && D8_CP_ARGS+=(--classpath "$PINE_XPOSED_JAR")
[[ -f "$PINE_CORE_JAR" ]]   && D8_CP_ARGS+=(--classpath "$PINE_CORE_JAR")
[[ -f "$XPOSED_API_JAR" ]]  && D8_CP_ARGS+=(--classpath "$XPOSED_API_JAR")

# Feed d8 with a single jar, it's more stable for inner classes.
CLASSES_JAR="$BUILD_DIR/classes_tmp.jar"
(cd "$BUILD_DIR/classes" && jar -J-Dfile.encoding=UTF-8 cf "$CLASSES_JAR" .)

java -Dfile.encoding=UTF-8 -cp "$D8_JAR" com.android.tools.r8.D8 \
    --lib "$ANDROID_JAR" \
    "${D8_CP_ARGS[@]}" \
    --output "$BUILD_DIR/dex" \
    --min-api 26 \
    "$CLASSES_JAR"
echo "  [4/4] Packaging JAR..."
cd "$BUILD_DIR/dex"

mkdir -p META-INF
cp "$META_DIR/"* META-INF/

jar -J-Dfile.encoding=UTF-8 cf "$OUT_DIR/$ADDON_NAME.jar" classes.dex META-INF/

echo "  Cleaning up build directory..."
rm -rf "$BUILD_DIR"

echo ""
echo "=== Done ==="
echo "  Result: $OUT_DIR/$ADDON_NAME.jar"
echo ""