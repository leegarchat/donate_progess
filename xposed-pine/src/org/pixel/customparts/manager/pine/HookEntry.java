package org.pixel.customparts.manager.pine;

import android.content.Context;
import org.pixel.customparts.core.BaseHook;
import org.pixel.customparts.core.IHookEnvironment;
// Импорты твоих хуков
import org.pixel.customparts.hooks.*;
import org.pixel.customparts.hooks.recents.*;
import org.pixel.customparts.hooks.systemui.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HookEntry {
    private static final String TAG = "HookEntry";
    private static final String PACKAGE_SYSTEMUI = "com.android.systemui";

    /** Hardcoded whitelist — packages that always get built-in hooks */
    private static final Set<String> BUILTIN_WHITELIST = new HashSet<>();
    private static final Set<String> LAUNCHER_PACKAGES = new HashSet<>();
    private static final Set<String> initializedPackages = new HashSet<>();
    private static final IHookEnvironment environment = new PineEnvironment();

    static {
        LAUNCHER_PACKAGES.add("com.google.android.apps.nexuslauncher");
        LAUNCHER_PACKAGES.add("com.google.android.apps.pixel.launcher");
        LAUNCHER_PACKAGES.add("com.android.launcher3");

        // Built-in whitelist = launcher + systemui
        BUILTIN_WHITELIST.addAll(LAUNCHER_PACKAGES);
        BUILTIN_WHITELIST.add(PACKAGE_SYSTEMUI);
    }

    /**
     * Check if a package is in the hardcoded built-in whitelist.
     * Used externally by AddonLoader logic.
     */
    public static boolean isInBuiltinWhitelist(String packageName) {
        return BUILTIN_WHITELIST.contains(packageName);
    }

    public static void init(Context context, ClassLoader classLoader, String packageName) {
        if (initializedPackages.contains(packageName)) {
            return;
        }
        initializedPackages.add(packageName);

        boolean inWhitelist = BUILTIN_WHITELIST.contains(packageName);
        boolean hasAddons = false;

        try {
            hasAddons = AddonLoader.hasAddonsForPackage(context, packageName);
        } catch (Throwable t) {
            environment.logError(TAG, "Failed to check addons for " + packageName, t);
        }

        /*
         * === Injection logic ===
         * 1. Package in whitelist (launcher/systemui):
         *    → Apply built-in hooks FIRST, then addon hooks
         * 2. Package NOT in whitelist, but has addon:
         *    → Apply ONLY addon hooks (no built-in)
         * 3. Package NOT in whitelist, no addon:
         *    → Should not happen (ActivityThread wouldn't inject), but skip gracefully
         */

        if (inWhitelist) {
            // Built-in hooks first
            // initGlobalHooks(context, classLoader);

            if (LAUNCHER_PACKAGES.contains(packageName)) {
                environment.log(TAG, "MATCHED LAUNCHER PACKAGE: " + packageName);
                initLauncherHooks(context, classLoader);
            }

            if (PACKAGE_SYSTEMUI.equals(packageName)) {
                environment.log(TAG, "MATCHED SYSTEMUI PACKAGE");
                initSystemUIHooks(context, classLoader);
            }
        } else {
            environment.log(TAG, "Addon-only package: " + packageName + " (not in built-in whitelist)");
        }

        // Addon hooks — always run after built-in (if package is in whitelist)
        // or as sole hooks (if package is addon-only)
        if (hasAddons) {
            try {
                AddonLoader.loadAndRunAddons(context, classLoader, packageName);
            } catch (Throwable t) {
                environment.logError(TAG, "Addon loading failed for " + packageName, t);
            }
        }
    }

    private static void initGlobalHooks(Context context, ClassLoader classLoader) {
        List<BaseHook> hooks = new ArrayList<>();
        
        EdgeEffectHookWrapper edgeHook = new EdgeEffectHookWrapper();
        // Настройка полей напрямую, так как apply в Java нет
        edgeHook.setKeySuffix("_pine"); 
        edgeHook.setUseGlobalSettings(true);
        hooks.add(edgeHook);

        hooks.add(new MagnifierHook());
        hooks.add(new ActivityTransitionHook());

        applyHooks(hooks, context, classLoader, "global");
    }

    private static void initLauncherHooks(Context context, ClassLoader classLoader) {
        List<BaseHook> hooks = new ArrayList<>();

        hooks.add(new GridSizeAppMenuHook());
        hooks.add(new UnifiedLauncherHook());
        hooks.add(new RecentsUnifiedHook());
        // hooks.add(new OxygenRecentsIconStripHook());

        applyHooks(hooks, context, classLoader, "launcher");
    }

    private static void initSystemUIHooks(Context context, ClassLoader classLoader) {
        List<BaseHook> hooks = new ArrayList<>();

        hooks.add(new DozeTapDozeHook());
        hooks.add(new DozeTapShadeHook());
        hooks.add(new KeyguardBatteryPowerHook());
        hooks.add(new ShadeUnifiedSurfaceHook());
        hooks.add(new ShadeCompactMediaHook());
        hooks.add(new SystemUIRestartHook());

        applyHooks(hooks, context, classLoader, "systemui");
    }

    private static void applyHooks(List<BaseHook> hooks, Context context, ClassLoader classLoader, String group) {
        // Сортировка по приоритету (по убыванию)
        Collections.sort(hooks, new Comparator<BaseHook>() {
            @Override
            public int compare(BaseHook o1, BaseHook o2) {
                // Descending order: 
                return Integer.compare(o2.getPriority(), o1.getPriority());
            }
        });

        int successCount = 0;

        for (BaseHook hook : hooks) {
            try {
                hook.setup(environment);
                // Проверяем включен ли хук (RecentsUnifiedHook проверит общий тумблер внутри)
                if (hook.isEnabled(context)) {
                    hook.init(classLoader);
                    successCount++;
                    environment.log(TAG, "Hook " + hook.getHookId() + " applied (" + group + ")");
                }
            } catch (Throwable t) {
                environment.logError(TAG, "Failed to init " + hook.getHookId() + " (" + group + ")", t);
            }
        }

        if (successCount > 0) {
            environment.log(TAG, "Init complete for " + group + ": " + successCount + " hooks active");
        }
    }
}