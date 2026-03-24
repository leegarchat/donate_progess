package org.pixel.customparts.hooks.systemui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import org.pixel.customparts.core.BaseHook;

/**
 * Forces the shade to be visually single-layer by disabling the notification scrim layer.
 *
 * On Android 16 QPR1+ (and some ROM variants), QS/behind scrim and notification scrim can be
 * rendered with different tint/alpha, creating a split appearance. The most robust fix is to
 * remove the notifications scrim at the final rendering point.
 */
public class ShadeUnifiedSurfaceHook extends BaseHook {

    private static final String KEY_SHADE_BLUR_INTENSITY = "shade_blur_intensity";
    private static final String KEY_SHADE_ZOOM_INTENSITY = "shade_zoom_intensity";
    private static final String KEY_SHADE_DISABLE_SCALE_THRESHOLD = "shade_disable_scale_threshold";

    // New keys
    private static final String KEY_SHADE_NOTIF_SCRIM_ALPHA = "shade_notif_scrim_alpha";
    private static final String KEY_SHADE_NOTIF_SCRIM_TINT = "shade_notif_scrim_tint";
    private static final String KEY_SHADE_NOTIF_SCRIM_TINT_ENABLED = "shade_notif_scrim_tint_enabled";
    private static final String KEY_SHADE_MAIN_SCRIM_ALPHA = "shade_main_scrim_alpha";
    private static final String KEY_SHADE_MAIN_SCRIM_TINT = "shade_main_scrim_tint";
    private static final String KEY_SHADE_MAIN_SCRIM_TINT_ENABLED = "shade_main_scrim_tint_enabled";

    private static final int DEFAULT_SHADE_BLUR_INTENSITY_PERCENT = 100;
    private static final int DEFAULT_SHADE_ZOOM_INTENSITY_PERCENT = 100;
    private static final int DEFAULT_DISABLE_SCALE_THRESHOLD = 40;
    private static final int MAIN_SCRIM_MAX_PERCENT = 138;
    private static final int NOTIF_SCRIM_MAX_PERCENT = 201;
    
    // Default values for new keys (-1 implies "use system default")
    private static final int DEFAULT_SCRIM_ALPHA = -1;
    private static final int DEFAULT_SCRIM_TINT = 0; // 0 usually means no tint override or transparent

    private static final String SCRIM_VIEW_CLASS =
        "com.android.systemui.scrim.ScrimView";

    // Static config model: settings are read once per SystemUI process start.
    private static volatile boolean sConfigLoaded;
    private static volatile int sCfgBlurIntensity = DEFAULT_SHADE_BLUR_INTENSITY_PERCENT;
    private static volatile int sCfgZoomIntensity = DEFAULT_SHADE_ZOOM_INTENSITY_PERCENT;
    private static volatile int sCfgDisableScaleThreshold = DEFAULT_DISABLE_SCALE_THRESHOLD;
    private static volatile int sCfgNotifScrimAlpha = DEFAULT_SCRIM_ALPHA;
    private static volatile int sCfgNotifScrimTint = DEFAULT_SCRIM_TINT;
    private static volatile int sCfgMainScrimAlpha = DEFAULT_SCRIM_ALPHA;
    private static volatile int sCfgMainScrimTint = DEFAULT_SCRIM_TINT;

    // Fast-path flags (avoid any extra math/branching in default mode).
    private static volatile boolean sBlurRadiusScalingActive;
    private static volatile boolean sBlurScaleAdjustmentActive;
    private static volatile boolean sNotifAlphaOverrideActive;
    private static volatile boolean sMainAlphaOverrideActive;
    private static volatile boolean sNotifTintOverrideActive;
    private static volatile boolean sMainTintOverrideActive;

    // Lock-screen state: skip tint on KEYGUARD / BOUNCER / AOD
    private static volatile boolean sIsKeyguardState;

    private static final long DEBUG_LOG_THROTTLE_MS = 1500;
    private static volatile long sLastDebugLogUptime;

    // Reflection caches for hot paths
    private static volatile Method sGetContextMethod;
    private static volatile Field sScrimNameField;
    private static volatile Field sGroupHeaderField;
    private static volatile Field sTintColorField;
    private static volatile boolean sColorFieldResolved;
    private static volatile Field sDrawableField;
    private static volatile boolean sDrawableFieldResolved;

    // applyBlur() scale arg index cache: -2 unknown, -1 absent, >=0 actual index
    private static volatile int sApplyBlurScaleArgIndex = -2;

    @Override
    public String getHookId() {
        return "ShadeUnifiedSurfaceHook";
    }

    @Override
    public int getPriority() {
        return 65;
    }

    @Override
    public boolean isEnabled(Context context) {
        if (context == null) return false;
        return true; 
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        ensureConfigLoaded(getCurrentApplication());
        hookScrimControllerState(classLoader);
        hookScrimViewNotificationAlpha(classLoader);
        hookScrimViewTint(classLoader);
        hookBackgroundBlurRadiusIfPresent(classLoader);
        hookBlurUtils(classLoader);
        hookNotificationGroupHeaderBackground(classLoader);
    }

    private void hookBlurUtils(ClassLoader classLoader) {
        try {
            Class<?> blurUtilsClass = XposedHelpers.findClass("com.android.systemui.statusbar.BlurUtils", classLoader);
            
            XposedBridge.hookAllMethods(blurUtilsClass, "applyBlur", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length < 3) return;

                        int scaleIndex = resolveApplyBlurScaleArgIndex(param.args);
                        if (scaleIndex == -1) return;

                        ensureConfigLoaded(getCurrentApplication());
                        if (!sConfigLoaded || !sBlurScaleAdjustmentActive) return;

                        int intensityPercent = sCfgBlurIntensity;
                        int disableScaleThreshold = sCfgDisableScaleThreshold;

                        if (intensityPercent > 0 && intensityPercent < disableScaleThreshold) {
                            param.args[scaleIndex] = 1.0f; 
                            debugOnce("BlurUtils scale: forced 1.0 (intensity=" + intensityPercent + "%)");
                        } else if (intensityPercent > 0) {
                            int zoomIntensity = sCfgZoomIntensity;
                            
                            if (zoomIntensity != 100) {
                                Object argScale = param.args[scaleIndex];
                                if (!(argScale instanceof Float)) return;
                                
                                float originalScale = (Float) argScale;
                                float delta = 1.0f - originalScale;
                                float newDelta = delta * (zoomIntensity / 100f);
                                float newScale = 1.0f - newDelta;
                                
                                param.args[scaleIndex] = newScale;
                                
                                debugOnce("BlurUtils scale: adjusted to " + newScale + " (original=" + originalScale + ", intensity=" + zoomIntensity + "%)");
                            }
                        }
                    } catch (Throwable t) {
                        logError("Failed in BlurUtils#applyBlur hook", t);
                    }
                }
            });
            
            log("BlurUtils#applyBlur hook installed");
        } catch (Throwable t) {
            logError("Failed to hook BlurUtils", t);
        }
    }

    private void hookScrimControllerState(ClassLoader classLoader) {
        try {
            Class<?> scrimCtrl = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.phone.ScrimController", classLoader);

            XposedBridge.hookAllMethods(scrimCtrl, "transitionTo", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                            String stateName = param.args[0].toString();
                            boolean keyguard = stateName.contains("KEYGUARD")
                                    || stateName.contains("BOUNCER")
                                    || stateName.contains("PULSING")
                                    || stateName.contains("DREAMING")
                                    || stateName.contains("AOD");
                            sIsKeyguardState = keyguard;
                            debugOnce("ScrimState -> " + stateName + " (lockscreen=" + keyguard + ")");
                        }
                    } catch (Throwable t) {
                        logError("Failed in ScrimController state track", t);
                    }
                }
            });

            log("ScrimController#transitionTo state tracker installed");
        } catch (Throwable t) {
            logError("Failed to hook ScrimController#transitionTo", t);
        }
    }

    private void hookScrimViewNotificationAlpha(ClassLoader classLoader) {
        try {
            Class<?> scrimViewClass = XposedHelpers.findClass(SCRIM_VIEW_CLASS, classLoader);

            Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
                    scrimViewClass,
                    "setViewAlpha",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!sConfigLoaded) ensureConfigLoaded(getContextFromHookObject(param.thisObject));
                                if (!sConfigLoaded) return;
                                if (!sNotifAlphaOverrideActive && !sMainAlphaOverrideActive) return;

                                String name = getScrimName(param.thisObject);
                                if ("notifications_scrim".equals(name) && sNotifAlphaOverrideActive) {
                                    float alphaVal = sCfgNotifScrimAlpha;
                                    if (alphaVal >= 0 && param.args[0] instanceof Float) {
                                        float original = (Float) param.args[0];
                                        float scalar = toEffectiveScalar((int) alphaVal, NOTIF_SCRIM_MAX_PERCENT);
                                        float newAlpha = original * scalar;
                                        if (newAlpha > 1.0f) newAlpha = 1.0f;
                                        param.args[0] = newAlpha;
                                    }
                                } else if (name != null && (name.contains("behind") || "back_scrim".equals(name))) {
                                    if (sMainAlphaOverrideActive) {
                                        float alphaVal = sCfgMainScrimAlpha;
                                        if (alphaVal >= 0 && param.args[0] instanceof Float) {
                                            float original = (Float) param.args[0];
                                            float scalar = toEffectiveScalar((int) alphaVal, MAIN_SCRIM_MAX_PERCENT);
                                            float newAlpha = original * scalar;
                                            if (newAlpha > 1.0f) newAlpha = 1.0f;
                                            param.args[0] = newAlpha;
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                logError("Failed in ScrimView#setViewAlpha before-hook", t);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            // After system updates internals, forcefully apply tint overlay to Drawable
                            applyTintEnforcement(param.thisObject);
                        }
                    }
            );

            log("ScrimView#setViewAlpha hook installed (" + unhooks.size() + " methods hooked)");
        } catch (Throwable t) {
            logError("Failed to hook ScrimView#setViewAlpha", t);
        }
    }

    private void hookScrimViewTint(ClassLoader classLoader) {
        XC_MethodHook recolorCallback = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (!sConfigLoaded) ensureConfigLoaded(getContextFromHookObject(param.thisObject));
                    if (!sConfigLoaded || sIsKeyguardState) return;
                    if (!sNotifTintOverrideActive && !sMainTintOverrideActive) return;

                    int colorArgIndex = -1;
                    if (param.args != null) {
                        for (int i = 0; i < param.args.length; i++) {
                            if (param.args[i] instanceof Integer) { colorArgIndex = i; break; }
                        }
                    }
                    if (colorArgIndex < 0) return;

                    int original = (Integer) param.args[colorArgIndex];
                    int sysAlpha = (original >>> 24) & 0xFF;
                    String name = getScrimName(param.thisObject);

                    if ("notifications_scrim".equals(name) && sNotifTintOverrideActive) {
                        int newColor = (sysAlpha << 24) | (sCfgNotifScrimTint & 0x00FFFFFF);
                        param.args[colorArgIndex] = newColor;
                        debugOnce("setTint arg swap: notif 0x" + Integer.toHexString(original) + " -> 0x" + Integer.toHexString(newColor));
                    } else if (name != null && (name.contains("behind") || "back_scrim".equals(name)) && sMainTintOverrideActive) {
                        int newColor = (sysAlpha << 24) | (sCfgMainScrimTint & 0x00FFFFFF);
                        param.args[colorArgIndex] = newColor;
                        debugOnce("setTint arg swap: " + name + " 0x" + Integer.toHexString(original) + " -> 0x" + Integer.toHexString(newColor));
                    }
                } catch (Throwable t) {
                    logError("Failed in ScrimView setTint recolor", t);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                applyTintEnforcement(param.thisObject);
            }
        };

        XC_MethodHook forceEnforcementHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                applyTintEnforcement(param.thisObject);
            }
        };

        try {
            Class<?> scrimViewClass = XposedHelpers.findClass(SCRIM_VIEW_CLASS, classLoader);

            Set<XC_MethodHook.Unhook> u1 = XposedBridge.hookAllMethods(scrimViewClass, "setTint", recolorCallback);
            Set<XC_MethodHook.Unhook> u2 = XposedBridge.hookAllMethods(scrimViewClass, "setScrimColor", recolorCallback);

            // Hook any other update methods to ensure our tint survives internal re-draw triggers
            String[] extraMethods = {"setColors", "updateColorWithTint", "updateColors"};
            for (String method : extraMethods) {
                try {
                    XposedBridge.hookAllMethods(scrimViewClass, method, forceEnforcementHook);
                } catch (Throwable ignored) { }
            }

            log("ScrimView colour hooks total: setTint=" + u1.size() + " setScrimColor=" + u2.size());
        } catch (Throwable t) {
            logError("Failed to hook ScrimView colour", t);
        }
    }

    private void hookBackgroundBlurRadiusIfPresent(ClassLoader classLoader) {
        try {
            Class<?> vri = XposedHelpers.findClassIfExists("android.view.ViewRootImpl", null);
            if (vri != null) {
                Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
                        vri, "setBackgroundBlurRadius", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    if (param.args == null || param.args.length < 1) return;
                                    if (!(param.args[0] instanceof Integer)) return;

                                    ensureConfigLoaded(getCurrentApplication());
                                    if (!sConfigLoaded || !sBlurRadiusScalingActive) return;

                                    int intensityPercent = sCfgBlurIntensity;

                                    int base = (Integer) param.args[0];
                                    if (intensityPercent <= 0) {
                                        param.args[0] = 0;
                                        debugOnce("Background blur: forced radius=0 (intensity=0)");
                                        return;
                                    }
                                    int scaled = Math.round(base * (intensityPercent / 100f));
                                    if (scaled < 0) scaled = 0;

                                    param.args[0] = scaled;
                                    debugOnce("Background blur: radius=" + scaled
                                            + " (base=" + base + ", intensity="
                                            + intensityPercent + "%)");
                                } catch (Throwable t) {
                                    logError("Failed in ViewRootImpl#setBackgroundBlurRadius override", t);
                                }
                            }
                        }
                );
                log("ViewRootImpl#setBackgroundBlurRadius scaler installed (" + unhooks.size() + " methods hooked)");
            }
        } catch (Throwable t) { }

        try {
            Class<?> txn = XposedHelpers.findClassIfExists("android.view.SurfaceControl$Transaction", null);
            if (txn != null) {
                Set<XC_MethodHook.Unhook> unhooks = XposedBridge.hookAllMethods(
                        txn, "setBackgroundBlurRadius", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    if (param.args == null || param.args.length < 2) return;
                                    Object radiusObj = param.args[param.args.length - 1];
                                    if (!(radiusObj instanceof Integer)) return;

                                    ensureConfigLoaded(getCurrentApplication());
                                    if (!sConfigLoaded || !sBlurRadiusScalingActive) return;

                                    int intensityPercent = sCfgBlurIntensity;

                                    int base = (Integer) radiusObj;
                                    if (intensityPercent <= 0) {
                                        param.args[param.args.length - 1] = 0;
                                        debugOnce("SurfaceControl blur: forced radius=0 (intensity=0)");
                                        return;
                                    }
                                    int scaled = Math.round(base * (intensityPercent / 100f));
                                    if (scaled < 0) scaled = 0;

                                    param.args[param.args.length - 1] = scaled;
                                    debugOnce("SurfaceControl blur: radius=" + scaled
                                            + " (base=" + base + ", intensity="
                                            + intensityPercent + "%)");
                                } catch (Throwable t) {
                                    logError("Failed in SurfaceControl.Transaction#setBackgroundBlurRadius override", t);
                                }
                            }
                        }
                );
                log("SurfaceControl.Transaction#setBackgroundBlurRadius scaler installed (" + unhooks.size() + " methods hooked)");
            }
        } catch (Throwable t) { }
    }

    private void debugOnce(String msg) {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sLastDebugLogUptime < DEBUG_LOG_THROTTLE_MS) return;
        sLastDebugLogUptime = now;
        log(msg);
    }

    private void ensureConfigLoaded(Context context) {
        if (sConfigLoaded) return;
        synchronized (ShadeUnifiedSurfaceHook.class) {
            if (sConfigLoaded) return;
            Context ctx = (context != null) ? context : getCurrentApplication();
            if (ctx == null) return;

            int blur = getIntSetting(ctx, KEY_SHADE_BLUR_INTENSITY, DEFAULT_SHADE_BLUR_INTENSITY_PERCENT);
            int zoom = getIntSetting(ctx, KEY_SHADE_ZOOM_INTENSITY, DEFAULT_SHADE_ZOOM_INTENSITY_PERCENT);
            int threshold = getIntSetting(ctx, KEY_SHADE_DISABLE_SCALE_THRESHOLD, DEFAULT_DISABLE_SCALE_THRESHOLD);

            int notifAlpha = getIntSetting(ctx, KEY_SHADE_NOTIF_SCRIM_ALPHA, DEFAULT_SCRIM_ALPHA);
            int notifTint = getIntSetting(ctx, KEY_SHADE_NOTIF_SCRIM_TINT, DEFAULT_SCRIM_TINT);
            boolean notifTintEnabled = isSettingEnabled(ctx, KEY_SHADE_NOTIF_SCRIM_TINT_ENABLED, false);
            int mainAlpha = getIntSetting(ctx, KEY_SHADE_MAIN_SCRIM_ALPHA, DEFAULT_SCRIM_ALPHA);
            int mainTint = getIntSetting(ctx, KEY_SHADE_MAIN_SCRIM_TINT, DEFAULT_SCRIM_TINT);
            boolean mainTintEnabled = isSettingEnabled(ctx, KEY_SHADE_MAIN_SCRIM_TINT_ENABLED, false);

            sCfgBlurIntensity = blur;
            sCfgZoomIntensity = zoom;
            sCfgDisableScaleThreshold = threshold;
            sCfgNotifScrimAlpha = notifAlpha;
            sCfgNotifScrimTint = notifTint;
            sCfgMainScrimAlpha = mainAlpha;
            sCfgMainScrimTint = mainTint;

            sBlurRadiusScalingActive = (blur != DEFAULT_SHADE_BLUR_INTENSITY_PERCENT);
            sBlurScaleAdjustmentActive = (blur > 0 && blur < threshold) || (blur > 0 && zoom != DEFAULT_SHADE_ZOOM_INTENSITY_PERCENT);
            sNotifAlphaOverrideActive = (notifAlpha >= 0);
            sMainAlphaOverrideActive = (mainAlpha >= 0);
            sNotifTintOverrideActive = notifTintEnabled;
            sMainTintOverrideActive = mainTintEnabled;

            sConfigLoaded = true;
            log("Shade config loaded: blur=" + blur + "% zoom=" + zoom
                    + "% threshold=" + threshold
                    + " notifAlpha=" + notifAlpha + " mainAlpha=" + mainAlpha
                    + " notifTint=" + notifTint + "(en=" + notifTintEnabled + ")"
                    + " mainTint=" + mainTint + "(en=" + mainTintEnabled + ")");
        }
    }

    private Context getContextFromHookObject(Object obj) {
        if (obj == null) return null;
        try {
            Method method = sGetContextMethod;
            if (method == null) {
                method = obj.getClass().getMethod("getContext");
                method.setAccessible(true);
                sGetContextMethod = method;
            }
            Object ctx = method.invoke(obj);
            return (ctx instanceof Context) ? (Context) ctx : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String getScrimName(Object obj) {
        if (obj == null) return "";
        try {
            Field field = sScrimNameField;
            if (field == null) {
                field = obj.getClass().getDeclaredField("mScrimName");
                field.setAccessible(true);
                sScrimNameField = field;
            }
            Object value = field.get(obj);
            return (value instanceof String) ? (String) value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private int resolveApplyBlurScaleArgIndex(Object[] args) {
        int cached = sApplyBlurScaleArgIndex;
        if (cached >= 0) {
            if (args.length > cached && args[cached] instanceof Float) return cached;
        } else if (cached == -1) {
            return -1;
        }

        int resolved = -1;
        if (args.length >= 4 && args[3] instanceof Float) {
            resolved = 3;
        } else if (args.length >= 3 && args[2] instanceof Float) {
            resolved = 2;
        }
        sApplyBlurScaleArgIndex = resolved;
        return resolved;
    }

    private void applyTintEnforcement(Object scrimView) {
        try {
            if (!sConfigLoaded || sIsKeyguardState) return;
            boolean anyTint = sNotifTintOverrideActive || sMainTintOverrideActive;
            if (!anyTint) return;

            String name = getScrimName(scrimView);
            if ("notifications_scrim".equals(name) && sNotifTintOverrideActive) {
                forceScrimBaseColor(scrimView, sCfgNotifScrimTint);
            } else if (name != null && (name.contains("behind") || "back_scrim".equals(name)) && sMainTintOverrideActive) {
                forceScrimBaseColor(scrimView, sCfgMainScrimTint);
            }
        } catch (Throwable t) {
            logError("Failed in applyTintEnforcement", t);
        }
    }

    /**
     * Bypasses internal blending properties and applies the RGB tint directly to the 
     * final underlying drawables. Preserves existing alpha transparency from the system view.
     */
    private void forceScrimBaseColor(Object scrimView, int desiredColor) {
        try {
            View view = (View) scrimView;

            if (!sColorFieldResolved) {
                synchronized (ShadeUnifiedSurfaceHook.class) {
                    if (!sColorFieldResolved) {
                        Class<?> clz = scrimView.getClass();
                        String[] candidates = {"mTintColor", "mScrimColor", "mMainColor",
                                               "mCurrentColor", "mColor", "mTint"};
                        for (String name : candidates) {
                            try {
                                Field f = clz.getDeclaredField(name);
                                f.setAccessible(true);
                                sTintColorField = f;
                                break;
                            } catch (NoSuchFieldException ignored) { }
                        }
                        sColorFieldResolved = true;
                    }
                }
            }

            int sysAlpha = 255;
            if (sTintColorField != null) {
                int current = sTintColorField.getInt(scrimView);
                sysAlpha = (current >>> 24) & 0xFF;
                if (sysAlpha == 0) {
                    sysAlpha = Math.round(view.getAlpha() * 255f);
                }
                // Update variable so getters recognize our override
                int targetColorWithAlpha = (sysAlpha << 24) | (desiredColor & 0x00FFFFFF);
                sTintColorField.setInt(scrimView, targetColorWithAlpha);
            } else {
                sysAlpha = Math.round(view.getAlpha() * 255f);
            }

            if (!sDrawableFieldResolved) {
                synchronized (ShadeUnifiedSurfaceHook.class) {
                    if (!sDrawableFieldResolved) {
                        try {
                            Field f = scrimView.getClass().getDeclaredField("mDrawable");
                            f.setAccessible(true);
                            sDrawableField = f;
                        } catch (Throwable ignored) { }
                        sDrawableFieldResolved = true;
                    }
                }
            }

            // A fully opaque version of the tint. By passing it to SRC_IN, the PorterDuff 
            // logic will naturally inherit the existing alpha layout output by the ScrimView paint.
            int opaqueTint = 0xFF000000 | (desiredColor & 0x00FFFFFF);
            int targetColorWithAlpha = (sysAlpha << 24) | (desiredColor & 0x00FFFFFF);

            boolean appliedToDrawable = false;
            if (sDrawableField != null) {
                Drawable d = (Drawable) sDrawableField.get(scrimView);
                if (d != null) {
                    if (d instanceof ColorDrawable) {
                        ((ColorDrawable) d).setColor(targetColorWithAlpha);
                    } else {
                        d.setColorFilter(new PorterDuffColorFilter(opaqueTint, PorterDuff.Mode.SRC_IN));
                    }
                    appliedToDrawable = true;
                }
            }

            if (!appliedToDrawable) {
                Drawable bg = view.getBackground();
                if (bg instanceof ColorDrawable) {
                    ((ColorDrawable) bg).setColor(targetColorWithAlpha);
                } else if (bg != null) {
                    bg.setColorFilter(new PorterDuffColorFilter(opaqueTint, PorterDuff.Mode.SRC_IN));
                }
            }

            view.invalidate();
        } catch (Throwable t) {
            logError("forceScrimBaseColor failed", t);
        }
    }

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private float toEffectiveScalar(int userPercent, int maxPercent) {
        int clamped = clamp(userPercent, 0, 100);
        return (clamped / 100f) * (maxPercent / 100f);
    }

    private Context getCurrentApplication() {
        try {
            Class<?> atClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object app = XposedHelpers.callStaticMethod(atClass, "currentApplication");
            return (app instanceof Context) ? (Context) app : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void hookNotificationGroupHeaderBackground(ClassLoader classLoader) {
        try {
            final Class<?> containerClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.notification.stack.NotificationChildrenContainer", 
                    classLoader
            );

            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    containerClass, 
                    "recreateNotificationHeader", 
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            try {
                                if (!sConfigLoaded) ensureConfigLoaded(getContextFromHookObject(param.thisObject));
                                if (!sConfigLoaded || !sNotifAlphaOverrideActive) return;
                                int alphaVal = sCfgNotifScrimAlpha;
                                
                                if (alphaVal >= 0) {
                                    float scalar = toEffectiveScalar(alphaVal, NOTIF_SCRIM_MAX_PERCENT);
                                    if (scalar < 1.0f) {
                                        Object headerView = getGroupHeader(param.thisObject);
                                        if (headerView != null) {
                                            XposedHelpers.callMethod(headerView, "setBackground", (Object) null);
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                logError("Failed in recreateNotificationHeader hook", t);
                            }
                        }
                    }
            );
            
            log("NotificationChildrenContainer#recreateNotificationHeader hook installed (methods=" + hooks.size() + ")");
        } catch (Throwable t) {
            logError("Failed to hook NotificationChildrenContainer#recreateNotificationHeader", t);
        }
    }

    private Object getGroupHeader(Object obj) {
        if (obj == null) return null;
        try {
            Field field = sGroupHeaderField;
            if (field == null) {
                field = obj.getClass().getDeclaredField("mGroupHeader");
                field.setAccessible(true);
                sGroupHeaderField = field;
            }
            return field.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }
}