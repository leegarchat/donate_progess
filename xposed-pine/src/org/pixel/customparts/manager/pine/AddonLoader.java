package org.pixel.customparts.manager.pine;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.pixel.customparts.core.IAddonHook;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

import org.json.JSONArray;
import org.json.JSONObject;

public class AddonLoader {

    private static final String TAG = "AddonLoader";

    public static final String ADDON_DIR = "/data/pixelparts/addons";
    private static final String SYSTEM_ADDON_DIR = "/system_ext/etc/pixelparts/addons";
    private static final String SYSTEM_ADDON_DATA_DIR = "/data/pixelparts/system_addons_data";

    // Boot guard: crash-loop detection for critical processes
    private static final String BOOT_GUARD_FILE = "/data/pixelparts/.boot_guard";
    private static final int BOOT_GUARD_THRESHOLD = 3;
    private static final long BOOT_GUARD_WINDOW_MS = 180_000L;        // 3 minutes
    private static final long BOOT_GUARD_CLEAR_DELAY_MS = 90_000L;    // 90 seconds
    private static final String SAFE_MODE_SETTING = "pixel_addon_safe_mode";

    private static final String ADDON_PREFIX = "pixel_addon_";
    private static final String INJECT_PREFIX = "pixel_extra_parts_inject_package_";

    private static final Map<String, AddonInfo> loadedAddons = new HashMap<>();
    private static boolean scanned = false;
    private static boolean safeMode = false;
    private static boolean bootGuardClearScheduled = false;

    // === Кэш: package → список ID аддонов, которые его таргетят ===
    // Строится один раз при сканировании, инвалидируется при rescan
    private static final Map<String, List<String>> targetIndex = new HashMap<>();

    public static class AddonInfo {
        public final String id;
        public final String entryClass;
        public final String name;
        public final String author;
        public final String description;
        public final String version;
        public final Set<String> defaultTargetPackages;
        public final String jarPath;
        public IAddonHook hook;            // null до первой загрузки DEX (lazy)
        public boolean dexLoaded = false;  // true после попытки загрузки DEX
        // Кэш для enabled-состояния и effective targets — чтобы не дёргать Settings.Global на каждый вызов
        private int cachedEnabled = -1;    // -1 = не кэшировано, 0 = disabled, 1 = enabled
        private Set<String> cachedEffectiveTargets = null;

        public AddonInfo(String id, String entryClass, String name, String author,
                         String description, String version, Set<String> defaultTargetPackages,
                         String jarPath) {
            this.id = id;
            this.entryClass = entryClass;
            this.name = name;
            this.author = author;
            this.description = description;
            this.version = version;
            this.defaultTargetPackages = defaultTargetPackages;
            this.jarPath = jarPath;
        }

        void invalidateCache() {
            cachedEnabled = -1;
            cachedEffectiveTargets = null;
        }
    }

    // Package excluded from addon injection entirely (system_server — can't hook reliably)
    private static final String IGNORED_PACKAGE = "android";

    public static void loadAndRunAddons(Context context, ClassLoader appClassLoader, String packageName) {
        // system_server is excluded — hooking it is unreliable
        if (IGNORED_PACKAGE.equals(packageName)) return;

        // Global safe mode check (cross-process via Settings.Global)
        if (isGlobalSafeMode(context)) {
            Log.w(TAG, "Safe mode active — skipping all addons for " + packageName);
            return;
        }

        // Boot guard: crash-loop detection for critical processes
        boolean guarded = isGuardedPackage(packageName);
        if (guarded && checkAndUpdateBootGuard(context)) {
            return; // safe mode activated — skip addons
        }

        ensureAddonsLoaded(context);

        List<AddonInfo> applicable = getApplicableAddons(context, packageName);
        if (applicable.isEmpty()) {
            // No addons to load — clear guard immediately, this boot is fine
            if (guarded) clearBootGuard();
            return;
        }

        // Lazy-load DEX только для тех аддонов, которые реально нужны
        for (AddonInfo info : applicable) {
            ensureDexLoaded(context, info);
        }

        // Сортировка по приоритету
        Collections.sort(applicable, new Comparator<AddonInfo>() {
            @Override
            public int compare(AddonInfo a, AddonInfo b) {
                int pa = a.hook != null ? a.hook.getPriority() : 0;
                int pb = b.hook != null ? b.hook.getPriority() : 0;
                return Integer.compare(pb, pa);
            }
        });

        int count = 0;
        for (AddonInfo info : applicable) {
            try {
                if (info.hook != null) {
                    info.hook.handleLoadPackage(context, appClassLoader, packageName);
                    count++;
                }
            } catch (Throwable t) {
                Log.e(TAG, "Addon [" + info.id + "] failed for " + packageName, t);
            }
        }

        if (count > 0) {
            Log.d(TAG, count + " addon(s) applied for " + packageName);
        }

        // Schedule boot guard clear — process survived addon loading
        if (guarded && !bootGuardClearScheduled) {
            bootGuardClearScheduled = true;
            scheduleBootGuardClear(context);
        }
    }

    public static boolean hasAddonsForPackage(Context context, String packageName) {
        ensureAddonsLoaded(context);
        // Быстрый поиск по индексу — O(1)
        List<String> addonIds = targetIndex.get(packageName);
        if (addonIds != null) {
            for (String id : addonIds) {
                AddonInfo info = loadedAddons.get(id);
                if (info != null && isAddonEnabled(context, info)) {
                    return true;
                }
            }
        }
        // Проверяем аддоны с пустыми таргетами (применяются ко всем пакетам)
        List<String> wildcardIds = targetIndex.get("*");
        if (wildcardIds != null) {
            for (String id : wildcardIds) {
                AddonInfo info = loadedAddons.get(id);
                if (info != null && isAddonEnabled(context, info)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static Set<String> getAllAddonTargetPackages(Context context) {
        ensureAddonsLoaded(context);
        Set<String> result = new HashSet<>();
        for (AddonInfo info : loadedAddons.values()) {
            if (isAddonEnabled(context, info)) {
                result.addAll(getEffectiveTargets(context, info));
            }
        }
        return result;
    }

    public static List<AddonInfo> getAllAddons(Context context) {
        ensureAddonsLoaded(context);
        return new ArrayList<>(loadedAddons.values());
    }

    public static AddonInfo getAddon(String id) {
        return loadedAddons.get(id);
    }

    public static Set<String> getLoadedAddonIds() {
        return Collections.unmodifiableSet(loadedAddons.keySet());
    }

    public static boolean isAddonEnabled(Context context, AddonInfo info) {
        if (info.cachedEnabled != -1) {
            return info.cachedEnabled == 1;
        }
        try {
            int val = Settings.Global.getInt(
                context.getContentResolver(),
                ADDON_PREFIX + info.id + "_enabled",
                1
            );
            info.cachedEnabled = (val != 0) ? 1 : 0;
            return val != 0;
        } catch (Throwable t) {
            info.cachedEnabled = 1;
            return true;
        }
    }

    public static void setAddonEnabled(Context context, String addonId, boolean enabled) {
        try {
            Settings.Global.putInt(
                context.getContentResolver(),
                ADDON_PREFIX + addonId + "_enabled",
                enabled ? 1 : 0
            );
            AddonInfo info = loadedAddons.get(addonId);
            if (info != null) info.invalidateCache();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to set addon enabled state", t);
        }
    }

    public static Set<String> getEffectiveTargets(Context context, AddonInfo info) {
        if (info.cachedEffectiveTargets != null) {
            return info.cachedEffectiveTargets;
        }
        Set<String> result = new HashSet<>();
        int scopeMode = 0;
        try {
            scopeMode = Settings.Global.getInt(
                context.getContentResolver(),
                ADDON_PREFIX + info.id + "_scope_mode",
                0
            );
        } catch (Throwable ignored) {}

        if (scopeMode == 0 || scopeMode == 2) {
            if (info.defaultTargetPackages != null) {
                result.addAll(info.defaultTargetPackages);
            }
        }
        if (scopeMode == 1 || scopeMode == 2) {
            try {
                String custom = Settings.Global.getString(
                    context.getContentResolver(),
                    ADDON_PREFIX + info.id + "_packages"
                );
                if (custom != null && !custom.isEmpty()) {
                    for (String pkg : custom.split(",")) {
                        String trimmed = pkg.trim();
                        if (!trimmed.isEmpty()) {
                            result.add(trimmed);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        // "android" (system_server) is never a valid target
        result.remove(IGNORED_PACKAGE);
        info.cachedEffectiveTargets = Collections.unmodifiableSet(result);
        return info.cachedEffectiveTargets;
    }

    public static void setCustomTargets(Context context, String addonId, Set<String> packages) {
        try {
            StringBuilder sb = new StringBuilder();
            for (String pkg : packages) {
                if (sb.length() > 0) sb.append(",");
                sb.append(pkg);
            }
            Settings.Global.putString(
                context.getContentResolver(),
                ADDON_PREFIX + addonId + "_packages",
                sb.toString()
            );
            AddonInfo info = loadedAddons.get(addonId);
            if (info != null) {
                info.invalidateCache();
                rebuildTargetIndex(context);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to set custom targets", t);
        }
    }

    public static void setScopeMode(Context context, String addonId, int mode) {
        try {
            Settings.Global.putInt(
                context.getContentResolver(),
                ADDON_PREFIX + addonId + "_scope_mode",
                mode
            );
            AddonInfo info = loadedAddons.get(addonId);
            if (info != null) {
                info.invalidateCache();
                rebuildTargetIndex(context);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to set scope mode", t);
        }
    }

    public static int getScopeMode(Context context, String addonId) {
        try {
            return Settings.Global.getInt(
                context.getContentResolver(),
                ADDON_PREFIX + addonId + "_scope_mode",
                0
            );
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void addToInjectionWhitelist(Context context, String packageName) {
        try {
            Settings.Global.putInt(
                context.getContentResolver(),
                INJECT_PREFIX + packageName,
                1
            );
        } catch (Throwable t) {
            Log.e(TAG, "Failed to add to whitelist: " + packageName, t);
        }
    }

    public static void removeFromInjectionWhitelist(Context context, String packageName) {
        try {
            Settings.Global.putInt(
                context.getContentResolver(),
                INJECT_PREFIX + packageName,
                0
            );
        } catch (Throwable t) {
            Log.e(TAG, "Failed to remove from whitelist: " + packageName, t);
        }
    }

    public static void syncWhitelist(Context context) {
        Set<String> allTargets = getAllAddonTargetPackages(context);
        for (String pkg : allTargets) {
            addToInjectionWhitelist(context, pkg);
        }
        Log.d(TAG, "Whitelist synced: " + allTargets.size() + " packages");
    }

    public static boolean deleteAddon(Context context, String addonId) {
        AddonInfo info = loadedAddons.get(addonId);
        if (info == null) return false;


        Set<String> thisTargets = getEffectiveTargets(context, info);
        loadedAddons.remove(addonId);


        Set<String> remainingTargets = getAllAddonTargetPackages(context);
        for (String pkg : thisTargets) {
            if (!remainingTargets.contains(pkg)) {
                removeFromInjectionWhitelist(context, pkg);
            }
        }

        try {
            File jar = new File(info.jarPath);
            if (jar.exists()) {
                jar.delete();
            }
            File desc = new File(info.jarPath + ".json");
            if (desc.exists()) {
                desc.delete();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to delete addon files: " + addonId, t);
        }
        deleteGlobalSetting(context, ADDON_PREFIX + addonId + "_enabled");
        deleteGlobalSetting(context, ADDON_PREFIX + addonId + "_packages");
        deleteGlobalSetting(context, ADDON_PREFIX + addonId + "_scope_mode");

        return true;
    }

    private static void deleteGlobalSetting(Context context, String key) {
        try {
            context.getContentResolver().delete(Settings.Global.getUriFor(key), null, null);
        } catch (Throwable ignored) {}
    }


    private static synchronized void ensureAddonsLoaded(Context context) {
        if (scanned) return;
        scanned = true;

        scanDirectory(context, SYSTEM_ADDON_DIR);
        scanDirectory(context, ADDON_DIR);

        rebuildTargetIndex(context);
        Log.d(TAG, "Addon scan complete. " + loadedAddons.size() + " addon(s) indexed.");
    }

    public static synchronized void rescan(Context context) {
        loadedAddons.clear();
        targetIndex.clear();
        scanned = false;
        ensureAddonsLoaded(context);
    }

    /**
     * Rebuild the package→addonId reverse index for fast lookups.
     * Called after scan and after any target/scope change.
     */
    private static void rebuildTargetIndex(Context context) {
        targetIndex.clear();
        for (AddonInfo info : loadedAddons.values()) {
            Set<String> targets = getEffectiveTargets(context, info);
            if (targets == null || targets.isEmpty()) {
                // Wildcard: applies to all packages
                targetIndex.computeIfAbsent("*", k -> new ArrayList<>()).add(info.id);
            } else {
                for (String pkg : targets) {
                    targetIndex.computeIfAbsent(pkg, k -> new ArrayList<>()).add(info.id);
                }
            }
        }
    }

    private static void scanDirectory(Context context, String dirPath) {
        File dir = new File(dirPath);
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.getName().endsWith(".jar") || !file.canRead()) continue;
            try {
                loadAddonMetadata(context, file);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to load addon metadata: " + file.getName(), t);
            }
        }
    }

    /**
     * Phase 1: Parse metadata only (JSON descriptor). No DEX loading.
     * This is fast — just reads addon.json from the JAR or external file.
     */
    private static void loadAddonMetadata(Context context, File jarFile) throws Exception {
        JSONObject desc = null;
        File externalDesc = new File(jarFile.getAbsolutePath() + ".json");
        if (externalDesc.exists()) {
            desc = readJsonFile(externalDesc);
        }
        if (desc == null || !desc.has("entryClass")) {
            desc = readAddonJsonFromJar(jarFile);
        }
        if (desc == null || !desc.has("entryClass")) {
            return;
        }

        String entryClass = desc.getString("entryClass");
        String id = desc.optString("id", entryClass);
        String name = desc.optString("name", id);
        String author = desc.optString("author", "Unknown");
        String description = desc.optString("description", "");
        String version = desc.optString("version", "1.0");
        boolean enabled = desc.optBoolean("enabled", true);

        Set<String> defaultTargets = new HashSet<>();
        JSONArray targetsArray = desc.optJSONArray("targetPackages");
        if (targetsArray != null) {
            for (int i = 0; i < targetsArray.length(); i++) {
                String pkg = targetsArray.optString(i);
                if (pkg != null && !pkg.isEmpty()) {
                    defaultTargets.add(pkg);
                }
            }
        }

        if (!enabled) {
            try {
                int override = Settings.Global.getInt(
                    context.getContentResolver(),
                    ADDON_PREFIX + id + "_enabled",
                    -1
                );
                if (override != 1) return;
            } catch (Throwable t) {
                return;
            }
        }

        AddonInfo info = new AddonInfo(id, entryClass, name, author, description,
                                       version, defaultTargets, jarFile.getAbsolutePath());
        loadedAddons.put(id, info);
        Log.d(TAG, "Indexed addon: " + id + " -> " + defaultTargets);
    }

    /**
     * Phase 2: Lazy DEX loading. Called only when an addon is actually needed
     * for the current package. Loads the DEX and instantiates the hook class.
     */
    private static synchronized void ensureDexLoaded(Context context, AddonInfo info) {
        if (info.dexLoaded) return;
        info.dexLoaded = true;

        try {
            String optimizedDir = getOptimizedDir(context);
            DexClassLoader dexLoader = new DexClassLoader(
                info.jarPath,
                optimizedDir,
                null,
                AddonLoader.class.getClassLoader()
            );

            Class<?> clazz = dexLoader.loadClass(info.entryClass);
            Object instance = clazz.newInstance();

            if (!(instance instanceof IAddonHook)) {
                Log.e(TAG, info.entryClass + " does not implement IAddonHook");
                return;
            }

            info.hook = (IAddonHook) instance;
            Log.d(TAG, "DEX loaded for addon: " + info.id);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load DEX for addon: " + info.id, t);
        }
    }

    /**
     * Find applicable addons for a package using the reverse index.
     * O(1) lookup instead of iterating all addons.
     */
    private static List<AddonInfo> getApplicableAddons(Context context, String packageName) {
        List<AddonInfo> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // Direct match by package name
        List<String> directIds = targetIndex.get(packageName);
        if (directIds != null) {
            for (String id : directIds) {
                AddonInfo info = loadedAddons.get(id);
                if (info != null && isAddonEnabled(context, info) && seen.add(id)) {
                    result.add(info);
                }
            }
        }

        // Wildcard addons (empty targets = applies to all)
        List<String> wildcardIds = targetIndex.get("*");
        if (wildcardIds != null) {
            for (String id : wildcardIds) {
                AddonInfo info = loadedAddons.get(id);
                if (info != null && isAddonEnabled(context, info) && seen.add(id)) {
                    result.add(info);
                }
            }
        }

        return result;
    }

    private static JSONObject readJsonFile(File file) {
        try {
            byte[] bytes = new byte[(int) file.length()];
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            fis.read(bytes);
            fis.close();
            return new JSONObject(new String(bytes, "UTF-8"));
        } catch (Throwable t) {
            Log.e(TAG, "Failed to read external JSON: " + file.getName(), t);
            return null;
        }
    }

    private static JSONObject readAddonJsonFromJar(File jarFile) {
        try (ZipFile zip = new ZipFile(jarFile)) {
            ZipEntry entry = zip.getEntry("META-INF/addon.json");
            if (entry == null) return null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return new JSONObject(sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "Failed to read addon.json from: " + jarFile.getName(), t);
            return null;
        }
    }

    /**
     * Returns the writable data directory for an addon.
     * For system addons (in /system_ext/...), data is stored under /data/pixelparts/system_addons_data/{id}/
     * For user addons (in /data/pixelparts/addons/), data is stored next to the JAR: {jar_name}_data/
     */
    public static File getAddonDataDir(AddonInfo info) {
        if (info.jarPath.startsWith(SYSTEM_ADDON_DIR)) {
            File dir = new File(SYSTEM_ADDON_DATA_DIR, info.id);
            if (!dir.exists()) dir.mkdirs();
            return dir;
        } else {
            File dir = new File(info.jarPath.replaceAll("\\.jar$", "_data"));
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }
    }

    /**
     * Returns the writable data directory for an addon by its ID.
     */
    public static File getAddonDataDir(String addonId) {
        AddonInfo info = loadedAddons.get(addonId);
        if (info != null) return getAddonDataDir(info);
        // Fallback — use user addon layout
        File dir = new File(ADDON_DIR, addonId + "_data");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Check if an addon is a system addon (read-only, shipped with firmware).
     */
    public static boolean isSystemAddon(AddonInfo info) {
        return info.jarPath.startsWith(SYSTEM_ADDON_DIR);
    }

    // =====================================================================
    // Boot guard — crash-loop protection
    // =====================================================================

    /**
     * Whether safe mode is active in this process.
     */
    public static boolean isSafeMode() {
        return safeMode;
    }

    /**
     * Check global safe mode flag (Settings.Global) — works cross-process.
     */
    private static boolean isGlobalSafeMode(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), SAFE_MODE_SETTING, 0) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Critical processes whose crash-loop would cause a bootloop.
     */
    private static boolean isGuardedPackage(String pkg) {
        return "com.android.systemui".equals(pkg);
    }

    /**
     * Read crash counter, decide if safe mode should be activated.
     * If not, increment counter for current boot attempt.
     * @return true if safe mode was activated (caller must skip addon loading)
     */
    private static boolean checkAndUpdateBootGuard(Context context) {
        File guardFile = new File(BOOT_GUARD_FILE);
        int count = 0;
        long lastTimestamp = 0;

        if (guardFile.exists()) {
            try {
                byte[] bytes = new byte[(int) guardFile.length()];
                java.io.FileInputStream fis = new java.io.FileInputStream(guardFile);
                fis.read(bytes);
                fis.close();
                String content = new String(bytes, "UTF-8").trim();
                String[] parts = content.split("\n");
                if (parts.length >= 2) {
                    count = Integer.parseInt(parts[0].trim());
                    lastTimestamp = Long.parseLong(parts[1].trim());
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to read boot guard file", t);
            }
        }

        long now = System.currentTimeMillis();

        // Reset if outside the crash window
        if (lastTimestamp > 0 && (now - lastTimestamp) > BOOT_GUARD_WINDOW_MS) {
            count = 0;
        }

        // Threshold reached — activate safe mode
        if (count >= BOOT_GUARD_THRESHOLD) {
            safeMode = true;
            try {
                Settings.Global.putInt(context.getContentResolver(), SAFE_MODE_SETTING, 1);
            } catch (Throwable ignored) {}
            // Reset counter so exitSafeMode won't re-trigger immediately
            clearBootGuard();
            Log.e(TAG, "SAFE MODE ACTIVATED: " + count + " crashes detected within "
                    + BOOT_GUARD_WINDOW_MS / 1000 + "s window. All addons disabled.");
            return true;
        }

        // Increment crash counter
        count++;
        try {
            guardFile.getParentFile().mkdirs();
            java.io.FileOutputStream fos = new java.io.FileOutputStream(guardFile);
            fos.write((count + "\n" + now + "\n").getBytes("UTF-8"));
            fos.close();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to write boot guard file", t);
        }

        if (count > 1) {
            Log.w(TAG, "Boot guard: crash count = " + count + "/" + BOOT_GUARD_THRESHOLD);
        }
        return false;
    }

    /**
     * Clear the boot guard file — called when process survived long enough.
     */
    private static void clearBootGuard() {
        try {
            File guardFile = new File(BOOT_GUARD_FILE);
            if (guardFile.exists()) guardFile.delete();
        } catch (Throwable ignored) {}
    }

    /**
     * Schedule boot guard clear after delay. If the process stays alive
     * for BOOT_GUARD_CLEAR_DELAY_MS without crashing, the guard resets.
     */
    private static void scheduleBootGuardClear(final Context context) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(BOOT_GUARD_CLEAR_DELAY_MS);
                    clearBootGuard();
                    safeMode = false;
                    try {
                        Settings.Global.putInt(context.getContentResolver(), SAFE_MODE_SETTING, 0);
                    } catch (Throwable ignored) {}
                    Log.d(TAG, "Boot guard cleared — process survived "
                            + (BOOT_GUARD_CLEAR_DELAY_MS / 1000) + "s");
                } catch (InterruptedException ignored) {}
            }
        }, "AddonBootGuardClear");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Manually exit safe mode. Called from the UI when user confirms.
     * Clears both the file marker and the global Settings flag.
     */
    public static void exitSafeMode(Context context) {
        safeMode = false;
        bootGuardClearScheduled = false;
        clearBootGuard();
        try {
            Settings.Global.putInt(context.getContentResolver(), SAFE_MODE_SETTING, 0);
        } catch (Throwable ignored) {}
        Log.i(TAG, "Safe mode exited manually");
    }

    // =====================================================================

    private static String getOptimizedDir(Context context) {
        File dir = new File("/data/pixelparts/addons-dex");
        if (!dir.exists()) dir.mkdirs();
        if (dir.exists() && dir.canWrite()) return dir.getAbsolutePath();
        if (context != null && context.getCacheDir() != null) {
            return context.getCacheDir().getAbsolutePath();
        }
        return "/data/local/tmp";
    }
}