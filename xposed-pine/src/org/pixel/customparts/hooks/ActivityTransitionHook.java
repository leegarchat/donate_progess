package org.pixel.customparts.hooks;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.SparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import org.pixel.customparts.core.BaseHook;

/**
 * Replaces activity ENTER (open) and EXIT (close/back) transitions with one
 * of 30 built-in animation styles or a custom theme APK.
 *
 * <p>Each animation is a fully independent mode — no separate direction flags.
 * Directional variants (e.g. Slide Right, Slide Left) are their own modes.</p>
 *
 * <p><b>Mode numbering</b> (type × 10 + direction):</p>
 * <ul>
 *   <li>0 = Disabled</li>
 *   <li>−1 = Custom theme APK</li>
 *   <li>10–13 = Slide (Right, Left, Top, Bottom)</li>
 *   <li>20–23 = Card Stack</li>
 *   <li>30–33 = Train</li>
 *   <li>40–43 = iOS Parallax</li>
 *   <li>50 = Fade</li>
 *   <li>60 = Zoom</li>
 *   <li>70–73 = Modal / Slide Up</li>
 *   <li>80–83 = Depth</li>
 *   <li>90–93 = Pivot</li>
 * </ul>
 *
 * <p>Open and close transitions are set independently via:</p>
 * <ul>
 *   <li>{@code activity_open_transition} — mode ID (default 10 = Slide Right)</li>
 *   <li>{@code activity_close_transition} — mode ID (default 0 = disabled)</li>
 *   <li>{@code activity_transition_custom_package} — package name of installed theme APK</li>
 * </ul>
 */
public class ActivityTransitionHook extends BaseHook {

    private static final String KEY_OPEN  = "activity_open_transition";
    private static final String KEY_CLOSE = "activity_close_transition";
    private static final String KEY_CUSTOM_PACKAGE = "activity_transition_custom_package";
    private static final String KEY_CUSTOM_OPEN_PACKAGE  = "activity_open_custom_package";
    private static final String KEY_CUSTOM_CLOSE_PACKAGE = "activity_close_custom_package";

    // ── Mode constants ──────────────────────────────────────────────────

    static final int MODE_DISABLED      = 0;
    static final int MODE_CUSTOM        = -1;
    static final int MODE_NO_ANIMATION  = -2;

    // Slide
    static final int MODE_SLIDE_RIGHT  = 10;
    static final int MODE_SLIDE_LEFT   = 11;
    static final int MODE_SLIDE_TOP    = 12;
    static final int MODE_SLIDE_BOTTOM = 13;

    // Card Stack
    static final int MODE_CARD_STACK_RIGHT  = 20;
    static final int MODE_CARD_STACK_LEFT   = 21;
    static final int MODE_CARD_STACK_TOP    = 22;
    static final int MODE_CARD_STACK_BOTTOM = 23;

    // Train
    static final int MODE_TRAIN_RIGHT  = 30;
    static final int MODE_TRAIN_LEFT   = 31;
    static final int MODE_TRAIN_TOP    = 32;
    static final int MODE_TRAIN_BOTTOM = 33;

    // iOS
    static final int MODE_IOS_RIGHT  = 40;
    static final int MODE_IOS_LEFT   = 41;
    static final int MODE_IOS_TOP    = 42;
    static final int MODE_IOS_BOTTOM = 43;

    // Fade
    static final int MODE_FADE = 50;

    // Zoom
    static final int MODE_ZOOM = 60;

    // Modal / Slide Up
    static final int MODE_MODAL_RIGHT  = 70;
    static final int MODE_MODAL_LEFT   = 71;
    static final int MODE_MODAL_TOP    = 72;
    static final int MODE_MODAL_BOTTOM = 73;

    // Depth
    static final int MODE_DEPTH_RIGHT  = 80;
    static final int MODE_DEPTH_LEFT   = 81;
    static final int MODE_DEPTH_TOP    = 82;
    static final int MODE_DEPTH_BOTTOM = 83;

    // Pivot
    static final int MODE_PIVOT_RIGHT  = 90;
    static final int MODE_PIVOT_LEFT   = 91;
    static final int MODE_PIVOT_TOP    = 92;
    static final int MODE_PIVOT_BOTTOM = 93;

    /** Well-known anim resource names inside custom theme APKs */
    private static final String ANIM_CUSTOM_OPEN_ENTER  = "custom_open_enter";
    private static final String ANIM_CUSTOM_OPEN_EXIT   = "custom_open_exit";
    private static final String ANIM_CUSTOM_CLOSE_ENTER = "custom_close_enter";
    private static final String ANIM_CUSTOM_CLOSE_EXIT  = "custom_close_exit";

    // Both build variants share the same resource names
    private static final String[] MODULE_PACKAGES = {
            "org.pixel.customparts.xposed",
            "org.pixel.customparts"
    };

    // ── Animation definition table ──────────────────────────────────────

    /**
     * Holds the four resource name/ID pairs for a single animation mode.
     * Resource names are resolved to IDs in {@link #ensureResources}.
     */
    private static final class AnimDef {
        final String openEnterName, openExitName;
        final String closeEnterName, closeExitName;
        int oe, ox, ce, cx; // resolved resource IDs

        AnimDef(String oe, String ox, String ce, String cx) {
            this.openEnterName = oe;  this.openExitName = ox;
            this.closeEnterName = ce; this.closeExitName = cx;
        }
    }

    /** All built-in modes. Key = mode constant, value = anim definition. */
    private static final SparseArray<AnimDef> MODES = new SparseArray<>();
    static {
        // ── Slide ────────────────────────────────────────────────────
        MODES.put(MODE_SLIDE_RIGHT,  new AnimDef("slide_in_right",  "no_anim",       "no_anim",       "slide_out_right"));
        MODES.put(MODE_SLIDE_LEFT,   new AnimDef("slide_in_left",   "no_anim",       "no_anim",       "train_exit"));
        MODES.put(MODE_SLIDE_TOP,    new AnimDef("slide_in_top",    "no_anim",       "no_anim",       "slide_out_top"));
        MODES.put(MODE_SLIDE_BOTTOM, new AnimDef("slide_up_enter",  "no_anim",       "no_anim",       "slide_down_exit"));
        // ── Card Stack ───────────────────────────────────────────────
        MODES.put(MODE_CARD_STACK_RIGHT,  new AnimDef("slide_in_right", "card_stack_exit",        "card_stack_enter",        "slide_out_right"));
        MODES.put(MODE_CARD_STACK_LEFT,   new AnimDef("slide_in_left",  "card_stack_exit_left",   "card_stack_enter_left",   "train_exit"));
        MODES.put(MODE_CARD_STACK_TOP,    new AnimDef("slide_in_top",   "card_stack_exit_top",    "card_stack_enter_top",    "slide_out_top"));
        MODES.put(MODE_CARD_STACK_BOTTOM, new AnimDef("slide_up_enter", "card_stack_exit_bottom", "card_stack_enter_bottom", "slide_down_exit"));
        // ── Train ────────────────────────────────────────────────────
        MODES.put(MODE_TRAIN_RIGHT,  new AnimDef("slide_in_right", "train_exit",      "slide_in_left",   "slide_out_right"));
        MODES.put(MODE_TRAIN_LEFT,   new AnimDef("slide_in_left",  "slide_out_right", "slide_in_right",  "train_exit"));
        MODES.put(MODE_TRAIN_TOP,    new AnimDef("slide_in_top",   "slide_down_exit", "slide_up_enter",  "slide_out_top"));
        MODES.put(MODE_TRAIN_BOTTOM, new AnimDef("slide_up_enter", "slide_out_top",   "slide_in_top",    "slide_down_exit"));
        // ── iOS Parallax ─────────────────────────────────────────────
        MODES.put(MODE_IOS_RIGHT,  new AnimDef("slide_in_right", "ios_open_exit",        "ios_close_enter",        "slide_out_right"));
        MODES.put(MODE_IOS_LEFT,   new AnimDef("slide_in_left",  "ios_open_exit_left",   "ios_close_enter_left",   "train_exit"));
        MODES.put(MODE_IOS_TOP,    new AnimDef("slide_in_top",   "ios_open_exit_top",    "ios_close_enter_top",    "slide_out_top"));
        MODES.put(MODE_IOS_BOTTOM, new AnimDef("slide_up_enter", "ios_open_exit_bottom", "ios_close_enter_bottom", "slide_down_exit"));
        // ── Fade (no direction) ──────────────────────────────────────
        MODES.put(MODE_FADE, new AnimDef("fade_in", "fade_out", "fade_in", "fade_out"));
        // ── Zoom (no direction) ──────────────────────────────────────
        MODES.put(MODE_ZOOM, new AnimDef("zoom_enter", "zoom_exit", "zoom_close_enter", "zoom_close_exit"));
        // ── Modal / Slide Up ─────────────────────────────────────────
        MODES.put(MODE_MODAL_RIGHT,  new AnimDef("slide_in_right", "slide_up_exit_right", "slide_up_close_enter_right", "slide_out_right"));
        MODES.put(MODE_MODAL_LEFT,   new AnimDef("slide_in_left",  "slide_up_exit_left",  "slide_up_close_enter_left",  "train_exit"));
        MODES.put(MODE_MODAL_TOP,    new AnimDef("slide_in_top",   "slide_up_exit_top",   "slide_up_close_enter_top",   "slide_out_top"));
        MODES.put(MODE_MODAL_BOTTOM, new AnimDef("slide_up_enter", "slide_up_exit",       "slide_up_close_enter",       "slide_down_exit"));
        // ── Depth ────────────────────────────────────────────────────
        MODES.put(MODE_DEPTH_RIGHT,  new AnimDef("slide_in_right", "depth_exit", "depth_close_enter", "slide_out_right"));
        MODES.put(MODE_DEPTH_LEFT,   new AnimDef("slide_in_left",  "depth_exit", "depth_close_enter", "train_exit"));
        MODES.put(MODE_DEPTH_TOP,    new AnimDef("slide_in_top",   "depth_exit", "depth_close_enter", "slide_out_top"));
        MODES.put(MODE_DEPTH_BOTTOM, new AnimDef("slide_up_enter", "depth_exit", "depth_close_enter", "slide_down_exit"));
        // ── Pivot ────────────────────────────────────────────────────
        MODES.put(MODE_PIVOT_RIGHT,  new AnimDef("pivot_enter",        "pivot_exit",        "pivot_close_enter",        "pivot_close_exit"));
        MODES.put(MODE_PIVOT_LEFT,   new AnimDef("pivot_enter_left",   "pivot_exit_left",   "pivot_close_enter_left",   "pivot_close_exit_left"));
        MODES.put(MODE_PIVOT_TOP,    new AnimDef("pivot_enter_top",    "pivot_exit_top",    "pivot_close_enter_top",    "pivot_close_exit_top"));
        MODES.put(MODE_PIVOT_BOTTOM, new AnimDef("pivot_enter_bottom", "pivot_exit_bottom", "pivot_close_enter_bottom", "pivot_close_exit_bottom"));
    }

    // ── Cached state ────────────────────────────────────────────────────

    private static volatile boolean sResolved;
    private static volatile Context sModuleCtx;
    private static volatile String  sModulePackage;

    // re-entrancy guard
    private static final ThreadLocal<Boolean> sInHook = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Cached custom theme APK context
    private static volatile Context sCustomThemeCtx;
    private static volatile String  sCustomThemePackage;

    // Cached reflection for close transition override
    private static volatile Method  sOverrideMethod;
    private static volatile boolean sOverrideMethodResolved;

    // ── BaseHook overrides ──────────────────────────────────────────────

    @Override
    public String getHookId() {
        return "ActivityTransitionHook";
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public boolean isEnabled(Context context) {
        return getIntSetting(context, KEY_OPEN, MODE_SLIDE_RIGHT) != MODE_DISABLED
                || getIntSetting(context, KEY_CLOSE, MODE_DISABLED) != MODE_DISABLED;
    }

    private int getOpenMode(Context context) {
        return getIntSetting(context, KEY_OPEN, MODE_SLIDE_RIGHT);
    }

    private int getCloseMode(Context context) {
        return getIntSetting(context, KEY_CLOSE, MODE_DISABLED);
    }

    private String getCustomPackage(Context context) {
        return getStringSetting(context, KEY_CUSTOM_PACKAGE, null);
    }

    private String getCustomOpenPackage(Context context) {
        String pkg = getStringSetting(context, KEY_CUSTOM_OPEN_PACKAGE, null);
        // Fallback to legacy shared key
        return (pkg != null && !pkg.isEmpty()) ? pkg : getCustomPackage(context);
    }

    private String getCustomClosePackage(Context context) {
        String pkg = getStringSetting(context, KEY_CUSTOM_CLOSE_PACKAGE, null);
        // Fallback to legacy shared key
        return (pkg != null && !pkg.isEmpty()) ? pkg : getCustomPackage(context);
    }

    // ── init ────────────────────────────────────────────────────────────

    @Override
    protected void onInit(ClassLoader classLoader) {
        hookStartActivity();
        hookStartActivityForResult();
        hookFinish();
        log("initialized (data-driven, " + MODES.size() + " built-in modes)");
    }

    // ── resource resolution ─────────────────────────────────────────────

    /**
     * Resolve ALL anim resource IDs from the installed module APK.
     * Iterates the {@link #MODES} table and fills in the four IDs per mode.
     */
    private boolean ensureResources(Context anyContext) {
        if (sResolved) return sModuleCtx != null;
        synchronized (ActivityTransitionHook.class) {
            if (sResolved) return sModuleCtx != null;
            for (String pkg : MODULE_PACKAGES) {
                try {
                    Context mc = anyContext.createPackageContext(pkg,
                            Context.CONTEXT_IGNORE_SECURITY);
                    android.content.res.Resources res = mc.getResources();

                    // Quick sanity: check that at least slide_in_right exists
                    int test = res.getIdentifier("slide_in_right", "anim", pkg);
                    if (test == 0) {
                        log("package " + pkg + " found but resources not resolved, skip");
                        continue;
                    }

                    sModuleCtx     = mc;
                    sModulePackage = pkg;

                    // Resolve all mode definitions
                    int resolved = 0;
                    for (int i = 0; i < MODES.size(); i++) {
                        AnimDef def = MODES.valueAt(i);
                        def.oe = res.getIdentifier(def.openEnterName,  "anim", pkg);
                        def.ox = res.getIdentifier(def.openExitName,   "anim", pkg);
                        def.ce = res.getIdentifier(def.closeEnterName, "anim", pkg);
                        def.cx = res.getIdentifier(def.closeExitName,  "anim", pkg);
                        if (def.oe != 0) resolved++;
                    }

                    sResolved = true;
                    log("resources resolved via " + pkg + ": " + resolved + "/" + MODES.size() + " modes");
                    return true;
                } catch (PackageManager.NameNotFoundException ignored) {
                    log("package " + pkg + " not installed, trying next");
                } catch (Throwable t) {
                    logError("Failed with package " + pkg, t);
                }
            }
            logError("Could not resolve module anim resources from any package", null);
            sResolved = true;
            return false;
        }
    }

    // ── custom theme APK helpers ──────────────────────────────────────

    private Context resolveCustomThemeContext(Context anyContext, String direction) {
        String pkg = "open".equals(direction)
                ? getCustomOpenPackage(anyContext)
                : getCustomClosePackage(anyContext);
        if (pkg == null || pkg.isEmpty()) {
            log("custom: no " + direction + " theme package configured");
            return null;
        }
        if (sCustomThemeCtx != null && pkg.equals(sCustomThemePackage)) {
            return sCustomThemeCtx;
        }
        try {
            Context themeCtx = anyContext.createPackageContext(pkg,
                    Context.CONTEXT_IGNORE_SECURITY);
            sCustomThemeCtx = themeCtx;
            sCustomThemePackage = pkg;
            log("custom: resolved " + direction + " theme package " + pkg);
            return themeCtx;
        } catch (PackageManager.NameNotFoundException e) {
            log("custom: " + direction + " theme package " + pkg + " not installed");
            sCustomThemeCtx = null;
            sCustomThemePackage = null;
            return null;
        }
    }

    static void invalidateCustomThemeCache() {
        sCustomThemeCtx = null;
        sCustomThemePackage = null;
    }

    private int resolveThemeAnim(Context themeCtx, String animName) {
        if (themeCtx == null || sCustomThemePackage == null) return 0;
        return themeCtx.getResources().getIdentifier(animName, "anim", sCustomThemePackage);
    }

    private void handleCustomOpen(Activity activity, XC_MethodHook.MethodHookParam param,
                                  int bundleIdx) {
        Context themeCtx = resolveCustomThemeContext(activity, "open");
        if (themeCtx == null) return;

        int enterId = resolveThemeAnim(themeCtx, ANIM_CUSTOM_OPEN_ENTER);
        int exitId  = resolveThemeAnim(themeCtx, ANIM_CUSTOM_OPEN_EXIT);
        log("handleCustomOpen: pkg=" + sCustomThemePackage
                + " enter=0x" + Integer.toHexString(enterId)
                + " exit=0x" + Integer.toHexString(exitId));

        if (enterId == 0 && exitId == 0) return;
        try {
            ActivityOptions opts = ActivityOptions.makeCustomAnimation(
                    themeCtx, enterId, exitId);
            Bundle animBundle = opts.toBundle();
            if (param.args[bundleIdx] instanceof Bundle) {
                // Split-screen fix: merge animation into existing bundle
                ((Bundle) param.args[bundleIdx]).putAll(animBundle);
            } else {
                param.args[bundleIdx] = animBundle;
            }
        } catch (Throwable t) {
            logError("custom open makeCustomAnimation failed", t);
        }
    }

    private void handleCustomClose(Activity activity) {
        Context themeCtx = resolveCustomThemeContext(activity, "close");
        if (themeCtx == null) return;

        int enterId = resolveThemeAnim(themeCtx, ANIM_CUSTOM_CLOSE_ENTER);
        int exitId  = resolveThemeAnim(themeCtx, ANIM_CUSTOM_CLOSE_EXIT);
        log("handleCustomClose: pkg=" + sCustomThemePackage
                + " enter=0x" + Integer.toHexString(enterId)
                + " exit=0x" + Integer.toHexString(exitId));

        if (enterId == 0 && exitId == 0) return;
        overrideCloseTransition(activity, enterId, exitId, sCustomThemePackage);
    }

    // ── hooks ───────────────────────────────────────────────────────────

    private void hookStartActivity() {
        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    Activity.class, "startActivity", new TransitionInjector());
            log("startActivity: hooked " + hooks.size() + " methods");
        } catch (Throwable t) {
            logError("hookStartActivity failed", t);
        }
    }

    private void hookStartActivityForResult() {
        try {
            Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                    Activity.class, "startActivityForResult", new TransitionInjector());
            log("startActivityForResult: hooked " + hooks.size() + " methods");
        } catch (Throwable t) {
            logError("hookStartActivityForResult failed", t);
        }
    }

    private void hookFinish() {
        CloseTransitionOverride callback = new CloseTransitionOverride();
        try {
            Set<XC_MethodHook.Unhook> h1 = XposedBridge.hookAllMethods(
                    Activity.class, "finish", callback);
            log("finish: hooked " + h1.size() + " methods");
        } catch (Throwable t) {
            logError("hookFinish(finish) failed", t);
        }
        try {
            Set<XC_MethodHook.Unhook> h2 = XposedBridge.hookAllMethods(
                    Activity.class, "finishAndRemoveTask", callback);
            log("finishAndRemoveTask: hooked " + h2.size() + " methods");
        } catch (Throwable t) {
            logError("hookFinish(finishAndRemoveTask) failed", t);
        }
        try {
            Set<XC_MethodHook.Unhook> h3 = XposedBridge.hookAllMethods(
                    Activity.class, "finishAfterTransition", callback);
            log("finishAfterTransition: hooked " + h3.size() + " methods");
        } catch (Throwable t) {
            logError("hookFinish(finishAfterTransition) failed", t);
        }
    }

    // ── close transition override (reflection) ──────────────────────────

    private void overrideCloseTransition(Activity activity, int closeEnter, int closeExit,
                                         String packageName) {
        try {
            Field tokenField = Activity.class.getDeclaredField("mToken");
            tokenField.setAccessible(true);
            IBinder token = (IBinder) tokenField.get(activity);

            Class<?> clientCls = Class.forName("android.app.ActivityClient");
            Object client = clientCls.getMethod("getInstance").invoke(null);

            if (!sOverrideMethodResolved) {
                synchronized (ActivityTransitionHook.class) {
                    if (!sOverrideMethodResolved) {
                        discoverOverrideMethod(clientCls);
                        sOverrideMethodResolved = true;
                    }
                }
            }

            if (sOverrideMethod == null) {
                logError("No transition override method found on ActivityClient", null);
                return;
            }

            Class<?>[] pts = sOverrideMethod.getParameterTypes();
            Object[] args = new Object[pts.length];
            int intIdx = 0;
            for (int i = 0; i < pts.length; i++) {
                if (IBinder.class.isAssignableFrom(pts[i])) {
                    args[i] = token;
                } else if (pts[i] == String.class) {
                    args[i] = packageName;
                } else if (pts[i] == boolean.class || pts[i] == Boolean.class) {
                    args[i] = false;
                } else if (pts[i] == int.class) {
                    switch (intIdx) {
                        case 0: args[i] = closeEnter; break;
                        case 1: args[i] = closeExit;  break;
                        default: args[i] = 0;          break;
                    }
                    intIdx++;
                } else {
                    args[i] = null;
                }
            }
            sOverrideMethod.invoke(client, args);
        } catch (Throwable t) {
            logError("overrideCloseTransition failed", t);
        }
    }

    private void overrideCloseTransition(Activity activity, int closeEnter, int closeExit) {
        overrideCloseTransition(activity, closeEnter, closeExit, sModulePackage);
    }

    private void discoverOverrideMethod(Class<?> clientCls) {
        for (Method m : clientCls.getDeclaredMethods()) {
            if (m.getName().equals("overridePendingTransition")) {
                m.setAccessible(true);
                sOverrideMethod = m;
                log("discovered: " + m.getName() + "(" + Arrays.toString(m.getParameterTypes()) + ")");
                return;
            }
        }

        Method bestFallback = null;
        for (Method m : clientCls.getDeclaredMethods()) {
            if (m.getName().equals("overrideActivityTransition")) {
                boolean hasString = false;
                for (Class<?> pt : m.getParameterTypes()) {
                    if (pt == String.class) { hasString = true; break; }
                }
                if (hasString) {
                    m.setAccessible(true);
                    sOverrideMethod = m;
                    log("fallback discovered: " + m.getName()
                            + "(" + Arrays.toString(m.getParameterTypes()) + ")");
                    return;
                }
                bestFallback = m;
            }
        }

        if (bestFallback != null) {
            bestFallback.setAccessible(true);
            sOverrideMethod = bestFallback;
            log("no-pkg fallback: " + bestFallback.getName()
                    + "(" + Arrays.toString(bestFallback.getParameterTypes()) + ")");
            return;
        }

        StringBuilder sb = new StringBuilder("ActivityClient methods: ");
        for (Method m : clientCls.getDeclaredMethods()) {
            sb.append(m.getName()).append('(').append(Arrays.toString(m.getParameterTypes())).append("), ");
        }
        logError(sb.toString(), null);
    }

    // ── hook implementations ────────────────────────────────────────────

    private class TransitionInjector extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (Boolean.TRUE.equals(sInHook.get())) return;
                sInHook.set(Boolean.TRUE);

                int intentIdx = -1;
                int bundleIdx = -1;
                for (int i = 0; i < param.args.length; i++) {
                    if (param.args[i] instanceof Intent && intentIdx == -1) {
                        intentIdx = i;
                    } else if ((param.args[i] == null || param.args[i] instanceof Bundle)
                            && i > 0 && bundleIdx == -1) {
                        bundleIdx = i;
                    }
                }

                Activity activity = (Activity) param.thisObject;
                int mode = getOpenMode(activity);
                if (mode == MODE_DISABLED) return;

                // MODE_NO_ANIMATION: force no-animation flag on intent
                if (mode == MODE_NO_ANIMATION) {
                    if (intentIdx >= 0) {
                        Intent intent = (Intent) param.args[intentIdx];
                        if (intent != null) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        }
                    }
                    return;
                }

                if (intentIdx >= 0) {
                    Intent intent = (Intent) param.args[intentIdx];
                    if (intent != null
                            && (intent.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0) {
                        return;
                    }
                }

                if (bundleIdx < 0) return;
                if (!ensureResources(activity)) return;

                if (mode == MODE_CUSTOM) {
                    handleCustomOpen(activity, param, bundleIdx);
                } else {
                    AnimDef def = MODES.get(mode);
                    if (def == null || def.oe == 0) return;
                    try {
                        ActivityOptions opts = ActivityOptions.makeCustomAnimation(
                                sModuleCtx, def.oe, def.ox);
                        Bundle animBundle = opts.toBundle();
                        if (param.args[bundleIdx] instanceof Bundle) {
                            // Split-screen fix: merge animation into existing bundle
                            ((Bundle) param.args[bundleIdx]).putAll(animBundle);
                        } else {
                            param.args[bundleIdx] = animBundle;
                        }
                    } catch (Throwable t) {
                        logError("makeCustomAnimation failed for mode " + mode, t);
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                sInHook.set(Boolean.FALSE);
            }
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            // Split-screen fallback: also apply via overridePendingTransition
            try {
                Activity activity = (Activity) param.thisObject;
                int mode = getOpenMode(activity);
                if (mode == MODE_DISABLED || mode == MODE_NO_ANIMATION) return;
                if (!ensureResources(activity)) return;

                if (mode == MODE_CUSTOM) {
                    Context themeCtx = resolveCustomThemeContext(activity, "open");
                    if (themeCtx == null) return;
                    int enterId = resolveThemeAnim(themeCtx, ANIM_CUSTOM_OPEN_ENTER);
                    int exitId = resolveThemeAnim(themeCtx, ANIM_CUSTOM_OPEN_EXIT);
                    if (enterId != 0 || exitId != 0) {
                        overrideCloseTransition(activity, enterId, exitId, sCustomThemePackage);
                    }
                } else {
                    AnimDef def = MODES.get(mode);
                    if (def == null || def.oe == 0) return;
                    overrideCloseTransition(activity, def.oe, def.ox);
                }
            } catch (Throwable ignored) {}
        }
    }

    private class CloseTransitionOverride extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            try {
                Activity activity = (Activity) param.thisObject;
                int closeMode = getCloseMode(activity);
                if (closeMode == MODE_DISABLED) return;

                // MODE_NO_ANIMATION: override with empty animations
                if (closeMode == MODE_NO_ANIMATION) {
                    overrideCloseTransition(activity, 0, 0, activity.getPackageName());
                    return;
                }

                if (!ensureResources(activity)) return;

                if (closeMode == MODE_CUSTOM) {
                    handleCustomClose(activity);
                } else {
                    AnimDef def = MODES.get(closeMode);
                    if (def == null || (def.ce == 0 && def.cx == 0)) return;
                    overrideCloseTransition(activity, def.ce, def.cx);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
