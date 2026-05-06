#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class VariantRule:
    order: int
    version_label: str
    sourceforge_dir: str


@dataclass(frozen=True)
class SourceRule:
    order: int
    version_suffix: str


SUPPORTED_DEVICES = ("shiba", "husky", "akita")
SOURCEFORGE_PROJECT = "evolutionx-unofficial-leegar"
GDRIVE_BASE_URL = "https://leegarchat.mooo.com/files"
VARIANT_RULES = {
    "stock": VariantRule(
        order=0,
        version_label="Stock GKI",
        sourceforge_dir="Build-GKI-stock",
    ),
    "sultan-wksu-r6": VariantRule(
        order=1,
        version_label="Sultan WildKSU",
        sourceforge_dir="Build-Sultan-WKSU-SUSFS-r6",
    ),
    "wksu-r19-6.1.145": VariantRule(
        order=2,
        version_label="GKI WildKSU",
        sourceforge_dir="Build-GKI-WKSU-SUSFS-r19",
    ),
}
SOURCE_RULES = (
    # SourceRule(order=0, version_suffix="SF"),
    SourceRule(order=1, version_suffix=""),
)
REQUIRED_SOURCE_KEYS = (
    "maintainer",
    "currently_maintained",
    "oem",
    "device",
    "filename",
    "timestamp",
    "md5",
    "sha256",
    "size",
    "buildtype",
    "forum",
    "firmware",
    "paypal",
    "github",
    "initial_installation_images",
    "extra_images",
)


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def default_release_root() -> Path:
    return repo_root().parent / "release"


def builds_dir() -> Path:
    return Path(__file__).resolve().parent / "builds"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Update OTA/builds/{shiba,husky,akita}.json from release/<date>/<device> "
            "variant metadata."
        )
    )
    parser.add_argument(
        "release",
        help="Release date like 20260503 or a path to the release directory.",
    )
    parser.add_argument(
        "--devices",
        nargs="+",
        choices=SUPPORTED_DEVICES,
        default=list(SUPPORTED_DEVICES),
        help="Subset of supported devices to update.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Validate and print what would be updated without writing files.",
    )
    return parser.parse_args()


def resolve_release_dir(release_arg: str) -> Path:
    explicit_path = Path(release_arg)
    if explicit_path.is_dir():
        return explicit_path.resolve()

    dated_dir = default_release_root() / release_arg
    if dated_dir.is_dir():
        return dated_dir.resolve()

    raise SystemExit(
        f"Release directory not found for '{release_arg}'. Checked '{dated_dir}'."
    )


def load_source_entry(json_path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(json_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SystemExit(f"Invalid JSON in '{json_path}': {exc}") from exc

    response = payload.get("response")
    if not isinstance(response, list) or len(response) != 1:
        raise SystemExit(
            f"Expected one entry in 'response' for '{json_path}', got: {response!r}"
        )

    entry = response[0]
    if not isinstance(entry, dict):
        raise SystemExit(f"Expected a JSON object in '{json_path}', got: {entry!r}")

    missing_keys = [key for key in REQUIRED_SOURCE_KEYS if key not in entry]
    if missing_keys:
        raise SystemExit(f"Missing keys in '{json_path}': {', '.join(missing_keys)}")

    return entry


def parse_filename(filename: str, expected_device: str, expected_date: str) -> str:
    if not filename.endswith(".zip"):
        raise SystemExit(f"Unexpected OTA filename '{filename}'. Expected a .zip file.")

    parts = filename[:-4].split("-")
    if len(parts) != 6:
        raise SystemExit(
            f"Unexpected OTA filename format '{filename}'. Expected 6 dash-separated parts."
        )

    if parts[0] != "EvolutionX" or parts[2] != expected_date or parts[3] != expected_device:
        raise SystemExit(
            f"OTA filename '{filename}' does not match device/date '{expected_device}/{expected_date}'."
        )

    return parts[4]


def variant_key_for_dir(variant_dir: Path, device_code: str) -> str:
    parts = variant_dir.name.split("-", 2)
    if len(parts) != 3 or parts[0] != device_code:
        raise SystemExit(
            f"Unexpected variant directory '{variant_dir.name}' for device '{device_code}'."
        )

    variant_key = parts[2]
    if variant_key not in VARIANT_RULES:
        raise SystemExit(
            f"Unsupported variant '{variant_key}' in '{variant_dir}'. "
            f"Supported variants: {', '.join(VARIANT_RULES)}"
        )

    return variant_key


def build_download_url(
    source_rule: SourceRule,
    device_code: str,
    release_date: str,
    sourceforge_dir: str,
    filename: str,
) -> str:
    if source_rule.version_suffix == "SF":
        return (
            f"https://sourceforge.net/projects/{SOURCEFORGE_PROJECT}/files/"
            f"{device_code}/{release_date}/{sourceforge_dir}/{filename}/download"
        )

    return f"{GDRIVE_BASE_URL}/{device_code}/{release_date}/{sourceforge_dir}/{filename}"


def normalize_entries(
    source_entry: dict[str, Any],
    device_code: str,
    release_date: str,
    variant_rule: VariantRule,
    timestamp: int,
) -> list[dict[str, Any]]:
    filename = str(source_entry["filename"])
    base_version = parse_filename(filename, device_code, release_date)
    version_base = f"{base_version} {variant_rule.version_label}".strip()
    entry_base = {
        "maintainer": source_entry["maintainer"],
        "currently_maintained": source_entry["currently_maintained"],
        "oem": source_entry["oem"],
        "device": source_entry["device"],
        "filename": filename,
        "timestamp": timestamp,
        "md5": source_entry["md5"],
        "sha256": source_entry["sha256"],
        "size": source_entry["size"],
        "buildtype": source_entry["buildtype"],
        "forum": source_entry["forum"],
        "firmware": source_entry["firmware"],
        "paypal": source_entry["paypal"],
        "github": source_entry["github"],
        "initial_installation_images": source_entry["initial_installation_images"],
        "extra_images": source_entry["extra_images"],
    }

    entries = []
    for source_rule in sorted(SOURCE_RULES, key=lambda item: item.order):
        entry = dict(entry_base)
        entry["download"] = build_download_url(
            source_rule,
            device_code,
            release_date,
            variant_rule.sourceforge_dir,
            filename,
        )
        entry["version"] = f"{version_base} {source_rule.version_suffix}".strip()
        entries.append(entry)

    return entries


def collect_device_entries(release_dir: Path, device_code: str) -> list[dict[str, Any]]:
    device_dir = release_dir / device_code
    if not device_dir.is_dir():
        raise SystemExit(f"Missing device release directory '{device_dir}'.")

    collected: list[tuple[VariantRule, dict[str, Any]]] = []
    seen_variants: set[str] = set()

    for variant_dir in sorted(path for path in device_dir.iterdir() if path.is_dir()):
        variant_key = variant_key_for_dir(variant_dir, device_code)
        if variant_key in seen_variants:
            raise SystemExit(f"Duplicate variant '{variant_key}' for '{device_code}'.")
        seen_variants.add(variant_key)

        json_files = sorted(variant_dir.glob("EvolutionX-*.json"))
        if len(json_files) != 1:
            raise SystemExit(
                f"Expected one OTA JSON in '{variant_dir}', found {len(json_files)}."
            )

        source_entry = load_source_entry(json_files[0])
        ota_zip = variant_dir / str(source_entry["filename"])
        if not ota_zip.is_file():
            raise SystemExit(
                f"OTA zip '{ota_zip.name}' referenced by '{json_files[0]}' was not found."
            )

        collected.append((VARIANT_RULES[variant_key], source_entry))

    if not collected:
        raise SystemExit(f"No OTA variants found in '{device_dir}'.")

    timestamp = min(int(entry["timestamp"]) for _, entry in collected)
    release_date = release_dir.name
    normalized = []
    for rule, source_entry in sorted(collected, key=lambda item: item[0].order):
        normalized.extend(
            normalize_entries(source_entry, device_code, release_date, rule, timestamp)
        )
    return normalized


def render_payload(entries: list[dict[str, Any]]) -> str:
    return json.dumps({"response": entries}, indent=2) + "\n"


def update_device_builds(release_dir: Path, device_code: str, dry_run: bool) -> tuple[Path, int, int]:
    entries = collect_device_entries(release_dir, device_code)
    output_path = builds_dir() / f"{device_code}.json"
    payload = render_payload(entries)

    if not dry_run:
        output_path.write_text(payload, encoding="utf-8")

    return output_path, len(entries), int(entries[0]["timestamp"])


def main() -> int:
    args = parse_args()
    release_dir = resolve_release_dir(args.release)
    release_date = release_dir.name

    results = []
    for device_code in args.devices:
        output_path, entry_count, timestamp = update_device_builds(
            release_dir,
            device_code,
            args.dry_run,
        )
        results.append((output_path, entry_count, timestamp))

    action = "Would update" if args.dry_run else "Updated"
    for output_path, entry_count, timestamp in results:
        print(f"{action} {output_path} with {entry_count} entries, timestamp={timestamp}")

    print(f"Release date: {release_date}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())