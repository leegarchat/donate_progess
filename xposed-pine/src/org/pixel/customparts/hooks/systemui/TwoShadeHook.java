package org.pixel.customparts.hooks.systemui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import org.pixel.customparts.core.BaseHook;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * TwoShadeHook — V5.1 Container-First Architecture
 *
 * Two-page horizontal shade: notifications (left) / quick settings (right).
 *
 * V5.1 fixes from V5 device testing:
 *   - mShadeVisible guard: hooks only active when shade is actually visible
 *     (prevents interfering with status bar calculation when shade is closed)
 *   - setQsFullScreen hook: prevents notification stack collapse + scroll disable
 *   - setRoundedClippingBounds hook: prevents notification clipping
 *   - PreDrawListener on NSSL: forces alpha=1 (counteracts NPVC alpha=0)
 *   - getMinExpansionHeight hook: ensures non-zero value for status bar logic
 *   - setExpandImmediate only called on page 1 commit, cleared on page 0
 *
 * 15 hooks: NQSC(2) + QSCtrl(5) + NSSL(6+PreDraw)
 */
public class TwoShadeHook extends BaseHook {

    private static final String TAG = "TwoShadeHook";
    private static final String KEY_TWO_SHADE = "two_shade_hook";

    // ── SystemUI class paths ──
    private static final String CLS_QS_CTRL =
            "com.android.systemui.shade.QuickSettingsControllerImpl";
    private static final String CLS_NQSC =
            "com.android.systemui.shade.NotificationsQuickSettingsContainer";
    private static final String CLS_NSSL =
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout";

    // ── StatusBarState ──
    private static final int STATE_SHADE = 0;

    // ── Touch state machine ──
    private static final int T_IDLE = 0;
    private static final int T_UNDECIDED = 1;
    private static final int T_HORIZONTAL = 2;
    private static final int T_SETTLING = 3;

    // ── Tuning ──
    private static final int FLING_VEL_DP = 300;
    private static final int SETTLE_MS = 280;
    private static final float H_V_RATIO = 1.4f;
    private static final int INDICATOR_MARGIN_DP = 24;
    /** Minimum value for getMinExpansionHeight so status bar logic works (px). */
    private static final int MIN_EXPANSION_HEIGHT_FLOOR = 100;

    // ═══════════════════════ View references ═══════════════════════
    private volatile View mQsFrame;
    private volatile View mNssl;
    private volatile ViewGroup mContainer;
    private volatile PageIndicatorView mIndicator;

    // ═══════════════════════ State ═══════════════════════
    private volatile boolean mReady;
    private volatile int mCurrentPage;
    private volatile float mScrollX;
    private volatile int mContainerWidth;
    /** True ONLY when shade is actually visible (between setExpanded true/false). */
    private volatile boolean mShadeVisible;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // ═══════════════════════ Touch ═══════════════════════
    private int mTouchState = T_IDLE;
    private float mDownX, mDownY, mLastX;
    private VelocityTracker mVelTracker;
    private int mTouchSlop;
    private int mFlingThresholdPx;

    // ═══════════════════════ QS state ═══════════════════════
    private volatile Object mQsCtrl;
    private volatile float mQsMaxHeight;

    // ═══════════════════════ Animation ═══════════════════════
    private ValueAnimator mSettleAnim;

    // ═══════════════════════ Helpers ═══════════════════════

    /** Read mBarState directly from QSControllerImpl via field access. */
    private int readBarState() {
        Object ctrl = mQsCtrl;
        if (ctrl == null) return STATE_SHADE;
        try {
            return XposedHelpers.getIntField(ctrl, "mBarState");
        } catch (Throwable t) {
            return STATE_SHADE;
        }
    }

    private boolean isShade() {
        return readBarState() == STATE_SHADE;
    }

    /** Main guard for all hooks: ready + shade visible + in SHADE bar state. */
    private boolean isActive() {
        return mReady && mShadeVisible && isShade();
    }

    private int effectivePage() {
        if (mContainerWidth <= 0) return mCurrentPage;
        return mScrollX > mContainerWidth / 2f ? 1 : 0;
    }

    private float pageFraction() {
        if (mContainerWidth <= 0) return 0f;
        return clamp01(mScrollX / mContainerWidth);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void refreshMaxHeight() {
        Object ctrl = mQsCtrl;
        if (ctrl == null) return;
        try {
            int h = XposedHelpers.getIntField(ctrl, "mMaxExpansionHeight");
            if (h > 0) mQsMaxHeight = h;
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════ BaseHook ═══════════════════════

    @Override
    public String getHookId() { return TAG; }

    @Override
    public int getPriority() { return 50; }

    @Override
    public boolean isEnabled(Context context) {
        return context != null && isSettingEnabled(context, KEY_TWO_SHADE, false);
    }

    @Override
    protected void onInit(ClassLoader cl) {
        log("V5.1 init");
        int ok = 0;
        ok += safe("NQSC.onFinishInflate",      () -> hookOnFinishInflate(cl));
        ok += safe("NQSC.dispatchTouchEvent",    () -> hookDispatchTouch(cl));
        ok += safe("QS.isExpansionEnabled",      () -> hookIsExpansionEnabled(cl));
        ok += safe("QS.setExpansionHeight",      () -> hookSetExpansionHeight(cl));
        ok += safe("QS.calcNotifTopPadding",     () -> hookCalcNotifTopPadding(cl));
        ok += safe("QS.setExpanded",             () -> hookSetExpanded(cl));
        ok += safe("QS.updateExpansion",         () -> hookUpdateExpansion(cl));
        ok += safe("NSSL.setQsExpFraction",      () -> hookNsslQsExpFraction(cl));
        ok += safe("NSSL.updateTopPadding",      () -> hookNsslUpdateTopPadding(cl));
        ok += safe("NSSL.setIntrinsicPadding",   () -> hookNsslSetIntrinsicPad(cl));
        ok += safe("NSSL.getIntrinsicPadding",   () -> hookNsslGetIntrinsicPad(cl));
        ok += safe("NSSL.setQsFullScreen",       () -> hookNsslSetQsFullScreen(cl));
        ok += safe("NSSL.setRoundedClipBounds",  () -> hookNsslSetRoundedClipBounds(cl));
        ok += safe("NSSL.getMinExpansionHeight", () -> hookNsslGetMinExpHeight(cl));
        log("V5.1 init: " + ok + "/14 hooks installed");
    }

    private int safe(String name, Runnable r) {
        try { r.run(); log("  + " + name); return 1; }
        catch (Throwable t) { logError("  X " + name, t); return 0; }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 1 — NQSC.onFinishInflate
    //  Capture views, restructure layout, install PreDrawListener
    // ═══════════════════════════════════════════════════════════════

    private void hookOnFinishInflate(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_NQSC, cl);
        XposedBridge.hookAllMethods(cls, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    ViewGroup container = (ViewGroup) param.thisObject;
                    mContainer = container;
                    Context ctx = container.getContext();

                    // ── Find core views ──
                    int qsId = ctx.getResources().getIdentifier(
                            "qs_frame", "id", ctx.getPackageName());
                    int nsslId = ctx.getResources().getIdentifier(
                            "notification_stack_scroller", "id", ctx.getPackageName());
                    if (qsId != 0) mQsFrame = container.findViewById(qsId);
                    if (nsslId != 0) mNssl = container.findViewById(nsslId);

                    if (mQsFrame == null || mNssl == null) {
                        logError("[1] Views not found qs=" + (mQsFrame != null)
                                + " nssl=" + (mNssl != null), null);
                        return;
                    }

                    // ── Disable clipping on container + ancestors ──
                    disableClipHierarchy(container);

                    // ── Force NSSL constraints: top -> parent with 0 margin ──
                    forceNsslFullScreen(container, nsslId);

                    // ── Initial visual state: page 0, QS hidden ──
                    mQsFrame.setAlpha(0f);

                    // ── Touch config ──
                    float density = ctx.getResources().getDisplayMetrics().density;
                    mTouchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();
                    mFlingThresholdPx = (int) (FLING_VEL_DP * density);

                    // ── Layout listener: track width, enforce margins ──
                    container.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> {
                        int w = r - l;
                        if (w > 0) {
                            if (w != mContainerWidth) {
                                mContainerWidth = w;
                                applyScroll(mCurrentPage == 0 ? 0 : w);
                            }
                            enforceNsslMargin();
                        }
                    });

                    // ── PreDraw listener on NSSL: force alpha=1 and visibility ──
                    installAlphaGuard(mNssl);

                    // ── Inject page indicator ──
                    injectIndicator(container, ctx);

                    mReady = true;
                    log("[1] Container restructured: qs_frame + NSSL captured");

                } catch (Throwable t) {
                    logError("[1] onFinishInflate error", t);
                }
            }
        });
    }

    /**
     * PreDrawListener that forces NSSL alpha=1 before each frame draw.
     * This counteracts NPVC.onHeightUpdated which sets alpha=0 during
     * expandImmediate transitions.
     */
    private void installAlphaGuard(final View nssl) {
        nssl.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (mShadeVisible && isShade()) {
                    if (nssl.getAlpha() < 1.0f) {
                        nssl.setAlpha(1.0f);
                    }
                    if (nssl.getVisibility() != View.VISIBLE) {
                        nssl.setVisibility(View.VISIBLE);
                    }
                }
                return true;
            }
        });
        log("[1] Alpha guard PreDrawListener installed on NSSL");
    }

    /** Force NSSL constraints via reflection (ConstraintLayout at runtime). */
    private void forceNsslFullScreen(ViewGroup container, int nsslId) {
        try {
            Class<?> clsCL = Class.forName("androidx.constraintlayout.widget.ConstraintLayout");
            if (clsCL.isInstance(container)) {
                Class<?> csClass = Class.forName("androidx.constraintlayout.widget.ConstraintSet");
                Object cs = csClass.getDeclaredConstructor().newInstance();
                csClass.getMethod("clone", clsCL).invoke(cs, container);
                int CS_TOP = csClass.getField("TOP").getInt(null);
                int CS_PARENT = csClass.getField("PARENT_ID").getInt(null);
                csClass.getMethod("connect", int.class, int.class, int.class, int.class, int.class)
                        .invoke(cs, nsslId, CS_TOP, CS_PARENT, CS_TOP, 0);
                csClass.getMethod("setMargin", int.class, int.class, int.class)
                        .invoke(cs, nsslId, CS_TOP, 0);
                csClass.getMethod("applyTo", clsCL).invoke(cs, container);
                log("[1] ConstraintSet: NSSL top->parent margin=0");
            }
        } catch (Throwable t) {
            logError("[1] ConstraintSet failed", t);
        }
        enforceNsslMargin();
    }

    private void enforceNsslMargin() {
        View nssl = mNssl;
        if (nssl == null) return;
        ViewGroup.LayoutParams lp = nssl.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            if (mlp.topMargin != 0) {
                mlp.topMargin = 0;
                nssl.setLayoutParams(mlp);
            }
        }
    }

    private void disableClipHierarchy(ViewGroup v) {
        v.setClipChildren(false);
        v.setClipToPadding(false);
        ViewParent p = v.getParent();
        for (int i = 0; i < 3 && p instanceof ViewGroup; i++) {
            ((ViewGroup) p).setClipChildren(false);
            ((ViewGroup) p).setClipToPadding(false);
            p = p.getParent();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 2 — NQSC.dispatchTouchEvent
    // ═══════════════════════════════════════════════════════════════

    private void hookDispatchTouch(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_NQSC, cl);
        XposedBridge.hookAllMethods(cls, "dispatchTouchEvent", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!canTouch()) return;
                MotionEvent ev = (MotionEvent) param.args[0];
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        handleDown(ev);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (handleMove(ev)) { param.setResult(true); return; }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (mTouchState == T_HORIZONTAL) {
                            handleUp(ev); param.setResult(true); return;
                        }
                        resetTouch();
                        break;
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!canTouch()) return;
                MotionEvent ev = (MotionEvent) param.args[0];
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    Object r = param.getResult();
                    if (r == null || !(Boolean) r) param.setResult(true);
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 3 — QSCtrl.isExpansionEnabled -> false
    //  Block vertical QS expansion gesture
    // ═══════════════════════════════════════════════════════════════

    private void hookIsExpansionEnabled(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_QS_CTRL, cl);
        Set<XC_MethodHook.Unhook> h = XposedBridge.hookAllMethods(cls,
                "isExpansionEnabled", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!isActive()) return;
                param.setResult(false);
            }
        });
        if (h.isEmpty()) throw new RuntimeException("isExpansionEnabled not found");
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 4 — QSCtrl.setExpansionHeight -> force max
    //  QS always at full height (visible on page 1, invisible on page 0)
    // ═══════════════════════════════════════════════════════════════

    private void hookSetExpansionHeight(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_QS_CTRL, cl);
        XposedBridge.hookAllMethods(cls, "setExpansionHeight", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                mQsCtrl = param.thisObject;
                refreshMaxHeight();
                if (!isActive()) return;
                if (mQsMaxHeight > 0 && param.args.length > 0) {
                    param.args[0] = mQsMaxHeight;
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 5 — QSCtrl.calculateNotificationsTopPadding -> 0
    // ═══════════════════════════════════════════════════════════════

    private void hookCalcNotifTopPadding(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_QS_CTRL, cl);
        XposedBridge.hookAllMethods(cls, "calculateNotificationsTopPadding",
                new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!isActive()) return;
                param.setResult(0.0f);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 6 — QSCtrl.setExpanded
    //  Track shade visibility. NO setExpandImmediate here.
    // ═══════════════════════════════════════════════════════════════

    private void hookSetExpanded(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_QS_CTRL, cl);
        XposedBridge.hookAllMethods(cls, "setExpanded", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!mReady) return;
                if (param.args.length < 1) return;
                boolean expanded = (boolean) param.args[0];
                mQsCtrl = param.thisObject;

                if (expanded && isShade()) {
                    mShadeVisible = true;
                    showIndicator(true);
                    log("Shade visible = true");
                } else if (!expanded) {
                    mShadeVisible = false;
                    snapToPage(0, false);
                    showIndicator(false);
                    // Reset expandImmediate to clean state
                    clearExpandImmediate();
                    log("Shade visible = false");
                }
            }
        });
    }

    /** Call setExpandImmediate(true) on QS controller. */
    private void triggerQsExpandImmediate() {
        Object ctrl = mQsCtrl;
        if (ctrl == null) return;
        try {
            XposedHelpers.callMethod(ctrl, "setExpandImmediate", true);
        } catch (Throwable t1) {
            try {
                XposedHelpers.setBooleanField(ctrl, "mExpandImmediate", true);
            } catch (Throwable t2) {
                logError("triggerQsExpandImmediate failed", t2);
            }
        }
    }

    /** Clear expandImmediate flag so status bar recovers. */
    private void clearExpandImmediate() {
        Object ctrl = mQsCtrl;
        if (ctrl == null) return;
        try {
            XposedHelpers.callMethod(ctrl, "setExpandImmediate", false);
        } catch (Throwable t1) {
            try {
                XposedHelpers.setBooleanField(ctrl, "mExpandImmediate", false);
            } catch (Throwable ignored) {}
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 7 — QSCtrl.updateExpansion
    //  Force QS fragment to show fully expanded content
    // ═══════════════════════════════════════════════════════════════

    private void hookUpdateExpansion(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_QS_CTRL, cl);
        XposedBridge.hookAllMethods(cls, "updateExpansion", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!isActive()) return;
                try {
                    Object qs = XposedHelpers.getObjectField(param.thisObject, "mQs");
                    if (qs != null) {
                        for (Method m : qs.getClass().getMethods()) {
                            if ("setQsExpansion".equals(m.getName())) {
                                int argc = m.getParameterCount();
                                if (argc == 4) {
                                    m.invoke(qs, 1.0f, 1.0f, 0f, 1.0f);
                                } else if (argc == 3) {
                                    m.invoke(qs, 1.0f, 1.0f, 0f);
                                } else if (argc == 1) {
                                    m.invoke(qs, 1.0f);
                                }
                                break;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 8 — NSSL.setQsExpansionFraction -> force 0
    // ═══════════════════════════════════════════════════════════════

    private void hookNsslQsExpFraction(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_NSSL, cl);
        XposedBridge.hookAllMethods(cls, "setQsExpansionFraction", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isActive()) return;
                if (param.args.length > 0) param.args[0] = 0.0f;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 9 — NSSL.updateTopPadding -> force 0
    // ═══════════════════════════════════════════════════════════════

    private void hookNsslUpdateTopPadding(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_NSSL, cl);
        XposedBridge.hookAllMethods(cls, "updateTopPadding", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isActive()) return;
                if (param.args.length > 0) param.args[0] = 0.0f;
                if (param.args.length > 1) param.args[1] = false;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 10 — NSSL.setIntrinsicPadding -> force 0
    // ═══════════════════════════════════════════════════════════════

    private void hookNsslSetIntrinsicPad(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_NSSL, cl);
        XposedBridge.hookAllMethods(cls, "setIntrinsicPadding", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isActive()) return;
                if (param.args.length > 0) param.args[0] = 0;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 11 — NSSL.getIntrinsicPadding -> return 0
    // ═══════════════════════════════════════════════════════════════

    private void hookNsslGetIntrinsicPad(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_NSSL, cl);
        XposedBridge.hookAllMethods(cls, "getIntrinsicPadding", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!isActive()) return;
                param.setResult(0);
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 12 — NSSL.setQsFullScreen -> force false
    //  Prevents notification stack collapse (height→shelf) and scroll disable
    // ═══════════════════════════════════════════════════════════════

    private void hookNsslSetQsFullScreen(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_NSSL, cl);
        XposedBridge.hookAllMethods(cls, "setQsFullScreen", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isActive()) return;
                if (param.args.length > 0) param.args[0] = false;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 13 — NSSL.setRoundedClippingBounds -> expand bounds
    //  Prevents notification stack being clipped behind QS
    //  Signature: setRoundedClippingBounds(int l, int t, int r, int b, int topR, int botR)
    // ═══════════════════════════════════════════════════════════════

    private void hookNsslSetRoundedClipBounds(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_NSSL, cl);
        XposedBridge.hookAllMethods(cls, "setRoundedClippingBounds", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isActive()) return;
                // Expand top bound to 0 (top of screen) so NSSL is not clipped
                if (param.args.length >= 4) {
                    param.args[1] = 0; // top = 0
                    // Expand bottom to full container height
                    View nssl = mNssl;
                    if (nssl != null) {
                        int h = nssl.getHeight();
                        if (h > 0) param.args[3] = h;
                    }
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //  HOOK 14 — NSSL.getMinExpansionHeight -> ensure floor value
    //  Prevents getOpeningHeight()=0 which breaks status bar icon logic
    // ═══════════════════════════════════════════════════════════════

    private void hookNsslGetMinExpHeight(ClassLoader cl) {
        Class<?> cls = XposedHelpers.findClass(CLS_NSSL, cl);
        XposedBridge.hookAllMethods(cls, "getMinExpansionHeight", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                // Always ensure a minimum so status bar logic works.
                // Only patch when shade hook is enabled (mReady), not just mShadeVisible,
                // because this affects idle status bar too.
                if (!mReady || !isShade()) return;
                Object result = param.getResult();
                if (result instanceof Integer) {
                    int val = (Integer) result;
                    if (val < MIN_EXPANSION_HEIGHT_FLOOR) {
                        param.setResult(MIN_EXPANSION_HEIGHT_FLOOR);
                    }
                }
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════
    //                    TOUCH HANDLING
    // ═══════════════════════════════════════════════════════════════

    private boolean canTouch() {
        return mReady && mShadeVisible && mContainerWidth > 0 && isShade();
    }

    private void handleDown(MotionEvent ev) {
        cancelSettle();
        mDownX = ev.getRawX();
        mDownY = ev.getRawY();
        mLastX = ev.getRawX();
        mTouchState = T_UNDECIDED;
        if (mVelTracker != null) mVelTracker.recycle();
        mVelTracker = VelocityTracker.obtain();
        mVelTracker.addMovement(ev);
    }

    private boolean handleMove(MotionEvent ev) {
        if (mTouchState == T_IDLE || mTouchState == T_SETTLING) return false;
        if (mVelTracker != null) mVelTracker.addMovement(ev);

        float dx = ev.getRawX() - mDownX;
        float dy = ev.getRawY() - mDownY;

        if (mTouchState == T_UNDECIDED) {
            float adx = Math.abs(dx), ady = Math.abs(dy);
            if (adx > mTouchSlop || ady > mTouchSlop) {
                if (adx > ady * H_V_RATIO) {
                    mTouchState = T_HORIZONTAL;
                    mLastX = ev.getRawX();
                    cancelChildren(ev);
                    return true;
                } else {
                    mTouchState = T_IDLE;
                    return false;
                }
            }
            return false;
        }

        if (mTouchState == T_HORIZONTAL) {
            float delta = mLastX - ev.getRawX();
            mLastX = ev.getRawX();
            float newSx = Math.max(0, Math.min(mScrollX + delta, mContainerWidth));
            applyScroll(newSx);
            return true;
        }
        return false;
    }

    private void handleUp(MotionEvent ev) {
        int target;
        if (mVelTracker != null) {
            mVelTracker.addMovement(ev);
            mVelTracker.computeCurrentVelocity(1000);
            float vx = mVelTracker.getXVelocity();
            mVelTracker.recycle();
            mVelTracker = null;
            target = Math.abs(vx) > mFlingThresholdPx
                    ? (vx < 0 ? 1 : 0)
                    : (mScrollX > mContainerWidth / 2f ? 1 : 0);
        } else {
            target = mScrollX > mContainerWidth / 2f ? 1 : 0;
        }
        settleTo(target);
        mTouchState = T_IDLE;
    }

    private void resetTouch() {
        mTouchState = T_IDLE;
        if (mVelTracker != null) { mVelTracker.recycle(); mVelTracker = null; }
    }

    private void cancelChildren(MotionEvent base) {
        ViewGroup c = mContainer;
        if (c == null) return;
        MotionEvent cancel = MotionEvent.obtain(base);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        for (int i = 0, n = c.getChildCount(); i < n; i++) {
            c.getChildAt(i).dispatchTouchEvent(cancel);
        }
        cancel.recycle();
    }

    // ═══════════════════════════════════════════════════════════════
    //                    PAGE ANIMATION
    // ═══════════════════════════════════════════════════════════════

    private void settleTo(int page) {
        cancelSettle();
        float target = page == 0 ? 0 : mContainerWidth;
        if (Math.abs(target - mScrollX) < 1f) {
            applyScroll(target);
            commitPage(page);
            return;
        }
        mTouchState = T_SETTLING;
        mSettleAnim = ValueAnimator.ofFloat(mScrollX, target);
        mSettleAnim.setDuration(SETTLE_MS);
        mSettleAnim.setInterpolator(new DecelerateInterpolator(1.5f));
        mSettleAnim.addUpdateListener(a -> applyScroll((float) a.getAnimatedValue()));
        mSettleAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                mTouchState = T_IDLE;
                applyScroll(target);
                commitPage(page);
            }
        });
        mSettleAnim.start();
    }

    private void cancelSettle() {
        if (mSettleAnim != null && mSettleAnim.isRunning()) {
            mSettleAnim.cancel();
            mSettleAnim = null;
        }
    }

    private void snapToPage(int page, boolean animate) {
        if (animate && mContainerWidth > 0) {
            settleTo(page);
        } else {
            cancelSettle();
            mCurrentPage = page;
            float sx = page == 0 ? 0 : mContainerWidth;
            mScrollX = sx;
            applyScroll(sx);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //                VISUAL POSITION APPLICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Page 0 (sx=0): NSSL at origin, qs_frame off-screen right
     * Page 1 (sx=W): NSSL off-screen left, qs_frame at origin
     */
    private void applyScroll(float sx) {
        mScrollX = sx;
        if (mContainerWidth <= 0) return;
        float f = sx / mContainerWidth;

        View nssl = mNssl;
        if (nssl != null) {
            nssl.setTranslationX(-sx);
            nssl.setAlpha(clamp01(1f - f * 0.3f));
        }

        View qs = mQsFrame;
        if (qs != null) {
            qs.setTranslationX(mContainerWidth - sx);
            qs.setAlpha(clamp01(f));
        }

        PageIndicatorView ind = mIndicator;
        if (ind != null) ind.setFraction(f);
    }

    /** Called when page transition is complete. */
    private void commitPage(int page) {
        int prevPage = mCurrentPage;
        mCurrentPage = page;
        log("Page -> " + page + (page == 0 ? " [Notifications]" : " [QS]"));

        if (page == 1) {
            // Entering QS page: trigger full QS expansion
            triggerQsExpandImmediate();
            Object ctrl = mQsCtrl;
            if (ctrl != null && mQsMaxHeight > 0) {
                try {
                    XposedHelpers.callMethod(ctrl, "setExpansionHeight", mQsMaxHeight);
                } catch (Throwable ignored) {}
            }
        } else if (page == 0 && prevPage == 1) {
            // Leaving QS page: clear expandImmediate to restore normal behavior
            clearExpandImmediate();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //                    PAGE INDICATOR
    // ═══════════════════════════════════════════════════════════════

    private void showIndicator(boolean show) {
        PageIndicatorView ind = mIndicator;
        if (ind != null) {
            ind.post(() -> ind.setVisibility(show ? View.VISIBLE : View.GONE));
        }
    }

    private void injectIndicator(ViewGroup parent, Context ctx) {
        if (mIndicator != null) return;
        PageIndicatorView v = new PageIndicatorView(ctx);
        mIndicator = v;
        float d = ctx.getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = (int) (INDICATOR_MARGIN_DP * d);
        parent.addView(v, lp);
        v.setVisibility(View.GONE);
        v.setElevation(12 * d);
        log("Indicator injected");
    }

    // ═══════════════════════════════════════════════════════════════
    //            PageIndicatorView — 2-dot Canvas widget
    // ═══════════════════════════════════════════════════════════════

    static class PageIndicatorView extends View {
        private static final int DOTS = 2;
        private static final float R_DP = 4f, SPACE_DP = 16f, ACTIVE_SCALE = 1.6f;
        private static final int C_OFF = 0x66FFFFFF, C_ON = 0xFFFFFFFF;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float r, space;
        private float fraction;

        PageIndicatorView(Context ctx) {
            super(ctx);
            float d = ctx.getResources().getDisplayMetrics().density;
            r = R_DP * d;
            space = SPACE_DP * d;
        }

        void setFraction(float f) { fraction = clamp01(f); invalidate(); }

        @Override protected void onMeasure(int wS, int hS) {
            int pad = (int)(r * 4);
            setMeasuredDimension(
                    (int)((DOTS-1)*space + r*2*ACTIVE_SCALE*DOTS) + pad,
                    (int)(r*2*ACTIVE_SCALE) + pad);
        }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth()/2f, cy = getHeight()/2f;
            float half = (DOTS-1)*space/2f;
            for (int i = 0; i < DOTS; i++) {
                float x = cx - half + i * space;
                float w = i == 0 ? 1f - fraction : fraction;
                paint.setColor(lerpColor(C_OFF, C_ON, w));
                c.drawCircle(x, cy, r * (1f + (ACTIVE_SCALE-1f)*w), paint);
            }
        }

        private static int lerpColor(int a, int b, float t) {
            float inv = 1f - t;
            return Color.argb(
                    (int)(Color.alpha(a)*inv + Color.alpha(b)*t),
                    (int)(Color.red(a)*inv   + Color.red(b)*t),
                    (int)(Color.green(a)*inv + Color.green(b)*t),
                    (int)(Color.blue(a)*inv  + Color.blue(b)*t));
        }

        private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
    }
}
