package org.pixel.customparts.hooks;

import android.content.Context;
import android.provider.Settings;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import org.pixel.customparts.core.BaseHook;

/**
 * Disables the predictive back gesture animation (shrink-to-center effect).
 *
 * <h3>Root cause (AOSP, Android 14+):</h3>
 * <p>{@code BackNavigationController.startBackNavigation()} checks
 * {@code callbackInfo.isSystemCallback()} — if {@code true}, Shell plays the
 * cross-activity shrink animation; if {@code false}, it's treated as
 * {@code TYPE_CALLBACK} and no animation is prepared.</p>
 * <pre>
 *   isSystemCallback() = mPriority == PRIORITY_SYSTEM(-1)
 *                      || mOverrideBehavior != OVERRIDE_UNDEFINED(0)
 * </pre>
 *
 * <h3>Strategy:</h3>
 * <p>Hook {@code WindowOnBackInvokedDispatcher.setTopOnBackInvokedCallback()}.
 * In {@code beforeHookedMethod}, temporarily change the callback's priority
 * in the internal {@code mAllCallbacks} map from {@code -1} to {@code 0}.
 * The original method reads the map, creates {@code OnBackInvokedCallbackInfo}
 * with priority 0, and sends it to WMS.
 * In {@code afterHookedMethod}, the original priority {@code -1} is
 * <b>restored</b> so that the internal state stays consistent and
 * {@code unregisterOnBackInvokedCallback} works correctly.</p>
 *
 * Setting: {@code Settings.Global "disable_predictive_back_anim"}
 *          0 = keep system animation (default), 1 = disable
 */
public class PredictiveBackDisableHook extends BaseHook {

    private static final String KEY = "disable_predictive_back_anim";

    private static final int PRIORITY_SYSTEM = -1;
    private static final int PRIORITY_DEFAULT = 0;

    @Override public String getHookId() { return "PredictiveBackDisableHook"; }
    @Override public int getPriority()  { return 25; }
    @Override public boolean isEnabled(Context context) { return true; }

    @Override
    protected void onInit(ClassLoader classLoader) {
        hookSetTopCallback();
        log("initialized");
    }

    // ── cached reflection ───────────────────────────────────────────────

    private static volatile Field sMapField;          // mAllCallbacks
    private static volatile boolean sMapFieldResolved;

    // ── core hook ───────────────────────────────────────────────────────

    /**
     * Thread-local to carry the (callback, originalPriority) pair from
     * {@code before} to {@code after} on the same call.
     */
    private static final ThreadLocal<Object[]> sPendingRestore = new ThreadLocal<>();

    private void hookSetTopCallback() {
        try {
            Class<?> dispatcherClass = Class.forName(
                    "android.window.WindowOnBackInvokedDispatcher");

            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    dispatcherClass, "setTopOnBackInvokedCallback", new XC_MethodHook() {

                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!isCurrentlyEnabled()) return;
                                Object callback = param.args[0];
                                if (callback == null) return;

                                Map<Object, Integer> map = getMap(param.thisObject);
                                if (map == null) return;

                                Integer priority = map.get(callback);
                                if (priority != null && priority == PRIORITY_SYSTEM) {
                                    // Temporarily set to DEFAULT so the original
                                    // method creates CallbackInfo with priority 0
                                    map.put(callback, PRIORITY_DEFAULT);
                                    // Remember to restore
                                    sPendingRestore.set(new Object[]{ callback, priority });
                                    log("before: priority " + priority + " → " + PRIORITY_DEFAULT);
                                }
                            } catch (Throwable t) {
                                logError("before hook failed", t);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object[] pending = sPendingRestore.get();
                                if (pending == null) return;
                                sPendingRestore.remove();

                                Object callback = pending[0];
                                int originalPriority = (Integer) pending[1];

                                Map<Object, Integer> map = getMap(param.thisObject);
                                if (map == null) return;

                                // Restore the original priority so unregister
                                // and other internal bookkeeping stays consistent
                                if (map.containsKey(callback)) {
                                    map.put(callback, originalPriority);
                                    log("after: restored priority → " + originalPriority);
                                }
                            } catch (Throwable t) {
                                logError("after hook failed", t);
                            }
                        }
                    });

            log("setTopOnBackInvokedCallback: hooked " + hooks.size() + " method(s)");
        } catch (Throwable t) {
            logError("hookSetTopCallback failed", t);
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<Object, Integer> getMap(Object dispatcher) {
        try {
            if (!sMapFieldResolved) {
                synchronized (PredictiveBackDisableHook.class) {
                    if (!sMapFieldResolved) {
                        sMapField = findField(dispatcher.getClass(), "mAllCallbacks");
                        sMapFieldResolved = true;
                        if (sMapField == null) {
                            logError("mAllCallbacks not found", null);
                        } else {
                            log("resolved mAllCallbacks field");
                        }
                    }
                }
            }
            if (sMapField == null) return null;
            return (Map<Object, Integer>) sMapField.get(dispatcher);
        } catch (Throwable t) {
            logError("getMap failed", t);
            return null;
        }
    }

    private static Field findField(Class<?> cls, String... names) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {}
            }
        }
        return null;
    }

    // ── settings ────────────────────────────────────────────────────────

    private static volatile int sLogCount;

    private boolean isCurrentlyEnabled() {
        try {
            Context ctx = currentApplication();
            if (ctx == null) return false;
            boolean val = isSettingEnabled(ctx, KEY, false);
            if (sLogCount++ < 5) {
                log("enabled=" + val);
            }
            return val;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Context currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            return (Context) at.getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
