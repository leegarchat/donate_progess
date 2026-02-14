package org.pixel.customparts.manager.pine;

import android.content.Context;
import org.pixel.customparts.core.BaseHook;
import org.pixel.customparts.core.IHookEnvironment;
// Импорты твоих хуков
import org.pixel.customparts.hooks.*;
import org.pixel.customparts.hooks.recents.*;
import org.pixel.customparts.hooks.systemui.DozeTapDozeHook;
import org.pixel.customparts.hooks.systemui.DozeTapShadeHook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HookEntry {
    private static final String TAG = "HookEntry";
    private static final String PACKAGE_SYSTEMUI = "com.android.systemui";

    private static final Set<String> LAUNCHER_PACKAGES = new HashSet<>();
    private static final Set<String> initializedPackages = new HashSet<>();
    private static final IHookEnvironment environment = new PineEnvironment();

    static {
        LAUNCHER_PACKAGES.add("com.google.android.apps.nexuslauncher");
        LAUNCHER_PACKAGES.add("com.google.android.apps.pixel.launcher");
        LAUNCHER_PACKAGES.add("com.android.launcher3");
    }

    public static void init(Context context, ClassLoader classLoader, String packageName) {
        if (initializedPackages.contains(packageName)) {
            return;
        }
        initializedPackages.add(packageName);

        if (LAUNCHER_PACKAGES.contains(packageName)) {
            environment.log(TAG, "MATCHED LAUNCHER PACKAGE: " + packageName);
            initLauncherHooks(context, classLoader);
        }

        if (PACKAGE_SYSTEMUI.equals(packageName)) {
            environment.log(TAG, "MATCHED SYSTEMUI PACKAGE");
            initSystemUIHooks(context, classLoader);
        }
    }

    private static void initGlobalHooks(Context context, ClassLoader classLoader) {
        List<BaseHook> hooks = new ArrayList<>();
        
        EdgeEffectHookWrapper edgeHook = new EdgeEffectHookWrapper();
        // Настройка полей напрямую, так как apply в Java нет
        edgeHook.setKeySuffix("_pine"); 
        edgeHook.setUseGlobalSettings(true);
        hooks.add(edgeHook);

        applyHooks(hooks, context, classLoader, "global");
    }

    private static void initLauncherHooks(Context context, ClassLoader classLoader) {
        List<BaseHook> hooks = new ArrayList<>();

        hooks.add(new GridSizeAppMenuHook());
        hooks.add(new UnifiedLauncherHook());
        hooks.add(new RecentsUnifiedHook());

        applyHooks(hooks, context, classLoader, "launcher");
    }

    private static void initSystemUIHooks(Context context, ClassLoader classLoader) {
        List<BaseHook> hooks = new ArrayList<>();

        hooks.add(new DozeTapDozeHook());
        hooks.add(new DozeTapShadeHook());

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