package org.pixel.customparts.hooks.recents;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.pixel.customparts.core.BaseHook;

public class RecentsUnifiedHook extends BaseHook {

    // --- STATIC SETTINGS ---
    private static class Settings {
        static boolean loaded = false;
        static boolean enabled = false;
        
        // Carousel
        static int spacingOffset = 0;
        static float scaleMin = 1.0f;
        static float alphaMin = 1.0f;
        static int maxBlur = 0;
        static boolean blurOverflow = false;
        static int tintColor = Color.BLACK;
        static float maxTintIntensity = 0f;
        static float iconOffsetX = 0f;
        static float iconOffsetY = 0f;
        static boolean hasIconOffset = false;
        
        // Misc
        static boolean commonScaleEnabled = false;
        static int commonScalePercent = 100;
        static boolean disableLiveTile = false;

        // Clear All
        static boolean clearAllEnabled = false;
        static int clearAllMode = 0;
        static float clearAllMargin = 3.0f;
    }

    // --- KEYS ---
    private static final String KEY_ENABLE = "launcher_recents_modify_enable";
    private static final String KEY_SPACING = "launcher_recents_carousel_spacing";
    private static final String KEY_CAROUSEL_SCALE = "launcher_recents_carousel_scale";
    private static final String KEY_ALPHA = "launcher_recents_carousel_alpha";
    private static final String KEY_BLUR_RADIUS = "launcher_recents_carousel_blur_radius";
    private static final String KEY_BLUR_OVERFLOW = "launcher_recents_carousel_blur_overflow";
    private static final String KEY_TINT_COLOR = "launcher_recents_carousel_tint_color";
    private static final String KEY_TINT_INTENSITY = "launcher_recents_carousel_tint_intensity";
    private static final String KEY_ICON_OFFSET_X = "launcher_recents_carousel_icon_offset_x";
    private static final String KEY_ICON_OFFSET_Y = "launcher_recents_carousel_icon_offset_y";
    private static final String KEY_DISABLE_LIVETILE = "launcher_recents_disable_livetile";
    private static final String KEY_COMMON_SCALE_ENABLE = "launcher_recents_scale_enable";
    private static final String KEY_COMMON_SCALE_PERCENT = "launcher_recents_scale_percent";
    
    // Clear All Keys
    private static final String KEY_CLEAR_ALL_ENABLED = "launcher_clear_all";
    private static final String KEY_CLEAR_ALL_MODE = "launcher_replace_on_clear";
    private static final String KEY_CLEAR_ALL_MARGIN = "launcher_clear_all_bottom_margin";

    // --- CONSTANTS ---
    private static final String CLASS_OVERVIEW_ACTIONS = "com.android.quickstep.views.OverviewActionsView";
    private static final String TAG_CLEAR_ALL_BTN = "custom_clear_all_btn";
    private static final String TAG_TRANSFORMED = "transformed_clear_all";
    private static final int CLEAR_MODE_BOTTOM = 0;
    private static final int CLEAR_MODE_REPLACE_SCREENSHOT = 1;
    private static final int CLEAR_MODE_REPLACE_SELECT = 2;

    // --- TAGS ---
    private static final int TAG_CACHE_ICONS = "cache_icons_list".hashCode();
    private static final int TAG_CACHE_LAST_RADIUS = "cache_last_radius".hashCode();
    private static final int TAG_CACHE_LAST_TINT = "cache_last_tint".hashCode();

    // --- REFLECTION (Only needed for render effect now) ---
    private Method setRenderEffectMethod;
    
    // Ссылка на RecentsView для кнопки очистки
    private static WeakReference<ViewGroup> recentsViewRef = null;

    @Override
    public String getHookId() {
        return "RecentsUnifiedHook";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean isEnabled(Context context) {
        if (!Settings.loaded) loadSettings(context);
        return Settings.enabled || Settings.clearAllEnabled;
    }

    private void loadSettings(Context context) {
        Settings.enabled = isSettingEnabled(context, KEY_ENABLE);
        Settings.clearAllEnabled = isSettingEnabled(context, KEY_CLEAR_ALL_ENABLED);
        
        if (Settings.enabled) {
            Settings.spacingOffset = getIntSetting(context, KEY_SPACING, 0);
            Settings.scaleMin = getFloatSetting(context, KEY_CAROUSEL_SCALE, 1.0f);
            Settings.alphaMin = getFloatSetting(context, KEY_ALPHA, 1.0f);

            if (Build.VERSION.SDK_INT >= 31) {
                Settings.maxBlur = getIntSetting(context, KEY_BLUR_RADIUS, 0);
                Settings.blurOverflow = isSettingEnabled(context, KEY_BLUR_OVERFLOW, false);
                int tintInt = getIntSetting(context, KEY_TINT_INTENSITY, 0);
                if (tintInt > 0) {
                    Settings.tintColor = getIntSetting(context, KEY_TINT_COLOR, Color.BLACK);
                    Settings.maxTintIntensity = tintInt / 100f;
                } else {
                    Settings.maxTintIntensity = 0f;
                }
            }

            float ox = getIntSetting(context, KEY_ICON_OFFSET_X, 0);
            float oy = getIntSetting(context, KEY_ICON_OFFSET_Y, 0);
            Settings.iconOffsetX = ox;
            Settings.iconOffsetY = oy;
            Settings.hasIconOffset = (Math.abs(ox) > 0.1f || Math.abs(oy) > 0.1f);

            Settings.commonScaleEnabled = isSettingEnabled(context, KEY_COMMON_SCALE_ENABLE);
            Settings.commonScalePercent = getIntSetting(context, KEY_COMMON_SCALE_PERCENT, 100);
            Settings.disableLiveTile = isSettingEnabled(context, KEY_DISABLE_LIVETILE, false);
        }

        if (Settings.clearAllEnabled) {
            Settings.clearAllMode = getIntSetting(context, KEY_CLEAR_ALL_MODE, CLEAR_MODE_BOTTOM);
            Settings.clearAllMargin = getFloatSetting(context, KEY_CLEAR_ALL_MARGIN, 3.0f);
        }
        
        Settings.loaded = true;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                setRenderEffectMethod = View.class.getMethod("setRenderEffect", RenderEffect.class);
            }
            
            Class<?> taskViewClass = XposedHelpers.findClass(RecentsState.CLASS_TASK_VIEW, classLoader);
            Class<?> recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader);
            Class<?> overviewActionsClass = XposedHelpers.findClass(CLASS_OVERVIEW_ACTIONS, classLoader);

            // 1. Recents Logic (Carousel + Lifecycle)
            hookRecentsView(recentsViewClass);
            
            // 2. TaskView Interceptors (Lightweight!)
            hookTaskViewInterceptors(taskViewClass);

            // 3. Clear All Button
            hookOverviewActions(overviewActionsClass);

            // 4. Common Scale (Grid)
            hookCommonScale(classLoader);

        } catch (Throwable e) {
            logError("Failed to init RecentsUnifiedHook", e);
        }
    }

    // =========================================================================
    // SECTION 1: RECENTS VIEW LIFECYCLE & MAIN LOOP
    // =========================================================================

    private void hookRecentsView(Class<?> recentsViewClass) {
        XposedHelpers.findAndHookMethod(recentsViewClass, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof ViewGroup)) return;
                final ViewGroup view = (ViewGroup) param.thisObject;
                
                recentsViewRef = new WeakReference<>(view);

                if (!Settings.loaded) loadSettings(view.getContext());

                if (view.getTag(RecentsState.TAG_HOOK_INSTALLED) != null) return;

                final ViewTreeObserver.OnPreDrawListener listener = new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        return handlePreDraw(view);
                    }
                };

                view.getViewTreeObserver().addOnPreDrawListener(listener);
                view.setTag(RecentsState.TAG_PREDRAW_LISTENER, listener);
                view.setTag(RecentsState.TAG_HOOK_INSTALLED, true);

                view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {}
                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        Object l = v.getTag(RecentsState.TAG_PREDRAW_LISTENER);
                        if (l instanceof ViewTreeObserver.OnPreDrawListener) {
                            v.getViewTreeObserver().removeOnPreDrawListener((ViewTreeObserver.OnPreDrawListener) l);
                        }
                        v.setTag(RecentsState.TAG_HOOK_INSTALLED, null);
                        resetState();
                    }
                });
            }
        });

        XposedBridge.hookAllMethods(recentsViewClass, "onGestureAnimationStart", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                RecentsState.isGestureInProgress = true;
                if (param.thisObject instanceof ViewGroup) {
                    startEntryAnimation((ViewGroup) param.thisObject);
                }
            }
        });

        XposedBridge.hookAllMethods(recentsViewClass, "onGestureAnimationEnd", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                 if (param.thisObject instanceof ViewGroup) {
                     try {
                        Object endTarget = XposedHelpers.getObjectField(param.thisObject, "mCurrentGestureEndTarget");
                        ((View)param.thisObject).setTag(RecentsState.TAG_PENDING_END_TARGET, endTarget != null ? endTarget.toString() : "");
                     } catch (Throwable t) {}
                 }
            }
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                RecentsState.isGestureInProgress = false;
                ViewGroup view = (ViewGroup) param.thisObject;
                
                if (Settings.enabled && Settings.disableLiveTile) {
                    disableLiveTile(view);
                }

                String target = (String) view.getTag(RecentsState.TAG_PENDING_END_TARGET);
                if (target != null && target.contains("RECENTS")) {
                    RecentsState.isInRecentsMode = true;
                    RecentsState.enteringRecentsUntil = 0L;
                }
            }
        });

        String[] exitMethods = {"launchTask", "startHome", "confirmTask"};
        for (String m : exitMethods) {
            try {
                XposedBridge.hookAllMethods(recentsViewClass, m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        RecentsState.isAnimatingExit = true;
                        RecentsState.isInRecentsMode = false;
                    }
                });
            } catch (Throwable t) {}
        }
        
        XposedBridge.hookAllMethods(recentsViewClass, "onTaskLaunchAnimationEnd", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                resetState();
            }
        });

         XposedHelpers.findAndHookMethod(recentsViewClass, "onLayout", boolean.class, int.class, int.class, int.class, int.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    ViewGroup view = (ViewGroup) param.thisObject;
                    if (view.getVisibility() == View.VISIBLE && !RecentsState.isGestureInProgress 
                        && !RecentsState.isInRecentsMode && RecentsState.carouselIntensity < 0.5f
                        && view.getChildCount() > 0) {
                        startEntryAnimation(view);
                    }
                }
            });
    }

    // =========================================================================
    // SECTION 2: LIGHTWEIGHT INTERCEPTORS
    // =========================================================================
    
    private void hookTaskViewInterceptors(Class<?> taskViewClass) {
        // ЧИТАЕМ системные значения, но НЕ ИСПОЛЬЗУЕМ invoke в onPreDraw
        
        XposedBridge.hookAllMethods(View.class, "setTranslationX", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(RecentsState.applyingEffects.get())) return;
                if (taskViewClass.isInstance(param.thisObject)) {
                    ((View) param.thisObject).setTag(RecentsState.TAG_SYS_TRANS_X, param.args[0]);
                }
            }
        });

        XposedHelpers.findAndHookMethod(taskViewClass, "setStableAlpha", float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(RecentsState.applyingEffects.get())) return;
                ((View) param.thisObject).setTag(RecentsState.TAG_SYS_STABLE_ALPHA, param.args[0]);
            }
        });

        XposedHelpers.findAndHookMethod(taskViewClass, "setNonGridScale", float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Boolean.TRUE.equals(RecentsState.applyingEffects.get())) return;
                ((View) param.thisObject).setTag(RecentsState.TAG_SYS_NON_GRID_SCALE, param.args[0]);
            }
        });
    }

    // =========================================================================
    // SECTION 3: PRE-DRAW LOGIC
    // =========================================================================

    private boolean handlePreDraw(ViewGroup recentsView) {
        if (!Settings.enabled) return true;

        if (!RecentsState.isInRecentsMode && !RecentsState.isAnimatingExit && !RecentsState.isGestureInProgress) {
            resetEffectsOnChildren(recentsView);
            return true;
        }

        float intensity = RecentsState.carouselIntensity;
        if (intensity <= 0f) {
            if (!RecentsState.isGestureInProgress) resetEffectsOnChildren(recentsView);
            return true;
        }

        int spacingVal = (int) (Settings.spacingOffset * intensity);
        float scaleVal = 1.0f + ((Settings.scaleMin - 1.0f) * intensity);
        float alphaVal = 1.0f - ((1.0f - Settings.alphaMin) * intensity);
        float blurVal = Settings.maxBlur * intensity;
        float tintVal = Settings.maxTintIntensity * intensity;
        float offX = Settings.iconOffsetX * intensity;
        float offY = Settings.iconOffsetY * intensity;

        int screenCenter = recentsView.getScrollX() + recentsView.getWidth() / 2;
        int childCount = recentsView.getChildCount();

        RecentsState.applyingEffects.set(true);
        try {
            for (int i = 0; i < childCount; i++) {
                View child = recentsView.getChildAt(i);
                if (child == null) continue;
                if (child.getClass().getSimpleName().contains("ClearAll")) continue;

                // --- 1. SPACING ---
                Object sysTransObj = child.getTag(RecentsState.TAG_SYS_TRANS_X);
                float sysTrans = (sysTransObj instanceof Float) ? (Float) sysTransObj : child.getTranslationX();
                if (sysTransObj == null) child.setTag(RecentsState.TAG_SYS_TRANS_X, sysTrans);

                float childCenter = (child.getLeft() + child.getRight()) / 2f + sysTrans;
                float influenceDist = recentsView.getWidth() * 0.55f;
                float dist = Math.abs(screenCenter - childCenter);
                float factor = (influenceDist <= 0 || dist > influenceDist) ? 1.0f : (dist / influenceDist);

                float spacingOffset = 0f;
                if (spacingVal != 0) {
                    float direction = (childCenter > screenCenter) ? 1f : -1f;
                    spacingOffset = direction * spacingVal * factor;
                }
                
                child.setTranslationX(sysTrans + spacingOffset);
                child.setTag(RecentsState.TAG_OFFSET_TRANS, spacingOffset);

                // --- 2. SCALE ---
                Object sysScaleObj = child.getTag(RecentsState.TAG_SYS_NON_GRID_SCALE);
                float sysScale = (sysScaleObj instanceof Float) ? (Float) sysScaleObj : 1.0f;
                
                if (Math.abs(scaleVal - 1.0f) > 0.001f) {
                    float s = 1.0f - ((1.0f - scaleVal) * factor);
                    // ИСПРАВЛЕНИЕ: Прямая установка свойств, без reflection
                    float target = sysScale * s;
                    child.setScaleX(target);
                    child.setScaleY(target);
                } else if (sysScaleObj != null) {
                    child.setScaleX(sysScale);
                    child.setScaleY(sysScale);
                }

                // --- 3. ALPHA ---
                Object sysAlphaObj = child.getTag(RecentsState.TAG_SYS_STABLE_ALPHA);
                float sysAlpha = (sysAlphaObj instanceof Float) ? (Float) sysAlphaObj : 1.0f;

                if (Math.abs(alphaVal - 1.0f) > 0.001f) {
                    float a = Math.max(0f, 1.0f - ((1.0f - alphaVal) * factor));
                    // ИСПРАВЛЕНИЕ: Прямая установка свойства
                    child.setAlpha(sysAlpha * a);
                } else if (sysAlphaObj != null) {
                    child.setAlpha(sysAlpha);
                }

                // --- 4. RENDER EFFECTS ---
                if (Build.VERSION.SDK_INT >= 31) {
                    applyRenderEffects(child, factor, blurVal, Settings.blurOverflow, Settings.tintColor, tintVal);
                }

                // --- 5. ICONS ---
                if (child instanceof ViewGroup) {
                    if (Settings.hasIconOffset) {
                        applyIconOffset((ViewGroup) child, offX, offY);
                    } else if (child.getTag(RecentsState.TAG_ICON_ORIG_DELEGATE) != null) {
                        applyIconOffset((ViewGroup) child, 0, 0);
                    }
                }
            }
        } finally {
            RecentsState.applyingEffects.set(false);
        }
        return true;
    }

    private void applyRenderEffects(View child, float factor, float maxBlurRadius, boolean overflow, int tintColor, float maxTintIntensity) {
        float blurRadius = 0f;
        if (maxBlurRadius > 0) {
            float raw = maxBlurRadius * factor;
            blurRadius = (raw < 2f) ? 0f : (float) (Math.floor(raw / 4f) * 4f);
        }

        int tintAlpha = 0;
        if (maxTintIntensity > 0) {
            tintAlpha = (int) (255 * maxTintIntensity * factor);
            if (tintAlpha > 255) tintAlpha = 255;
            if (tintAlpha < 5) tintAlpha = 0;
        }

        Object lastRad = child.getTag(TAG_CACHE_LAST_RADIUS);
        Object lastTint = child.getTag(TAG_CACHE_LAST_TINT);
        float cachedR = (lastRad instanceof Float) ? (Float) lastRad : -1f;
        int cachedA = (lastTint instanceof Integer) ? (Integer) lastTint : -1;

        if (Math.abs(blurRadius - cachedR) < 0.1f && tintAlpha == cachedA) return;

        child.setTag(TAG_CACHE_LAST_RADIUS, blurRadius);
        child.setTag(TAG_CACHE_LAST_TINT, tintAlpha);

        RenderEffect effect = null;
        if (blurRadius > 0) {
            Shader.TileMode mode = overflow ? Shader.TileMode.DECAL : Shader.TileMode.CLAMP;
            effect = RenderEffect.createBlurEffect(blurRadius, blurRadius, mode);
        }
        if (tintAlpha > 0) {
            int color = (tintColor & 0x00FFFFFF) | (tintAlpha << 24);
            RenderEffect tint = RenderEffect.createColorFilterEffect(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
            effect = (effect == null) ? tint : RenderEffect.createChainEffect(tint, effect);
        }

        try {
            if (overflow) {
                child.setClipToOutline(false);
                if (child instanceof ViewGroup) {
                    ((ViewGroup)child).setClipChildren(false);
                    ((ViewGroup)child).setClipToPadding(false);
                }
                callSetRenderEffect(child, effect);
                View thumb = getCachedThumbnailView(child);
                if (thumb != null) callSetRenderEffect(thumb, null);
            } else {
                child.setClipToOutline(true);
                callSetRenderEffect(child, null);
                View thumb = getCachedThumbnailView(child);
                if (thumb != null) {
                    callSetRenderEffect(thumb, effect);
                    thumb.setClipToOutline(true);
                } else {
                    callSetRenderEffect(child, effect);
                }
            }
        } catch (Exception e) {}
    }

    // =========================================================================
    // SECTION 4: CLEAR ALL BUTTON
    // =========================================================================

    private void hookOverviewActions(Class<?> overviewActionsClass) {
        XposedHelpers.findAndHookMethod(overviewActionsClass, "onFinishInflate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!Settings.clearAllEnabled) return;
                final ViewGroup view = (ViewGroup) param.thisObject;
                final Context context = view.getContext();
                
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        updateClearAllButton(view, context);
                    }
                });
            }
        });
    }

    @SuppressLint("DiscouragedApi")
    private void updateClearAllButton(ViewGroup parent, Context context) {
        try {
            Resources res = context.getResources();
            String pkg = context.getPackageName();
            
            int containerId = res.getIdentifier("action_buttons", "id", pkg);
            ViewGroup container = (containerId != 0) ? parent.findViewById(containerId) : null;
            if (container == null) {
                for(int i=0; i<parent.getChildCount(); i++) {
                    if (parent.getChildAt(i) instanceof LinearLayout) {
                        container = (ViewGroup) parent.getChildAt(i);
                        break;
                    }
                }
            }
            if (container == null) return;

            View oldBtn = container.findViewWithTag(TAG_CLEAR_ALL_BTN);
            if (oldBtn != null) container.removeView(oldBtn);
            View oldParentBtn = parent.findViewWithTag(TAG_CLEAR_ALL_BTN);
            if (oldParentBtn != null) parent.removeView(oldParentBtn);

            int screenshotId = res.getIdentifier("action_screenshot", "id", pkg);
            int selectId = res.getIdentifier("action_select", "id", pkg);
            Button screenshotBtn = (screenshotId != 0) ? (Button) container.findViewById(screenshotId) : null;
            Button selectBtn = (selectId != 0) ? (Button) container.findViewById(selectId) : null;

            switch (Settings.clearAllMode) {
                case CLEAR_MODE_REPLACE_SCREENSHOT:
                    if (screenshotBtn != null) transformButton(context, screenshotBtn);
                    else addButtonToContainer(context, container, selectBtn, 0);
                    break;
                case CLEAR_MODE_REPLACE_SELECT:
                    if (selectBtn != null) transformButton(context, selectBtn);
                    else addButtonToContainer(context, container, screenshotBtn, -1);
                    break;
                default: 
                    addButtonToBottom(context, parent, container, selectBtn != null ? selectBtn : screenshotBtn);
                    break;
            }
        } catch (Throwable e) {
            logError("Failed to update ClearAll", e);
        }
    }

    private void transformButton(final Context context, Button btn) {
        btn.setText(getClearAllText(context));
        btn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        btn.setTag(TAG_TRANSFORMED);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { performClearAll(v); }
        });
    }

    private void addButtonToContainer(Context ctx, ViewGroup container, Button styleSrc, int pos) {
        Button btn = createButton(ctx, styleSrc);
        syncState(btn, container);
        int idx = (pos < 0) ? container.getChildCount() : 0;
        container.addView(btn, Math.min(idx, container.getChildCount()));
    }

    private void addButtonToBottom(Context ctx, ViewGroup parent, ViewGroup container, Button styleSrc) {
        Button btn = createButton(ctx, styleSrc);
        syncState(btn, container);
        
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        int spacing = getButtonSpacing(ctx);
        lp.bottomMargin = (int) (spacing * Settings.clearAllMargin);
        
        btn.setLayoutParams(lp);
        btn.setPadding(btn.getPaddingLeft(), btn.getPaddingTop()+10, btn.getPaddingRight(), btn.getPaddingBottom()+10);
        parent.addView(btn);
    }

    private Button createButton(final Context ctx, Button styleSrc) {
        Context contextToUse = (styleSrc != null) ? styleSrc.getContext() : ctx;
        Button btn = new Button(contextToUse);
        btn.setTag(TAG_CLEAR_ALL_BTN);
        btn.setText(getClearAllText(ctx));
        
        if (styleSrc != null) {
            btn.setTextColor(styleSrc.getTextColors());
            if (styleSrc.getBackground() != null && styleSrc.getBackground().getConstantState() != null)
                btn.setBackground(styleSrc.getBackground().getConstantState().newDrawable());
            btn.setTypeface(styleSrc.getTypeface());
            btn.setPadding(styleSrc.getPaddingLeft(), styleSrc.getPaddingTop(), styleSrc.getPaddingRight(), styleSrc.getPaddingBottom());
        } else {
             int spacing = getButtonSpacing(ctx);
             LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
             lp.setMarginStart(spacing);
             btn.setLayoutParams(lp);
        }
        
        btn.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View v) { performClearAll(v); }
        });
        return btn;
    }

    private void performClearAll(View trigger) {
        ViewGroup recents = (recentsViewRef != null) ? recentsViewRef.get() : null;
        if (recents == null) return;
        
        try {
            Object nativeBtn = XposedHelpers.getObjectField(recents, "mClearAllButton");
            if (nativeBtn instanceof View) {
                ((View)nativeBtn).performClick();
                return;
            }
        } catch (Throwable t) {}

        try {
            XposedHelpers.callMethod(recents, "dismissAllTasks", trigger);
        } catch (Throwable t) {
            try { XposedHelpers.callMethod(recents, "dismissAllTasks"); } catch (Throwable t2) {}
        }
    }
    
    // =========================================================================
    // SECTION 5: UTILS
    // =========================================================================

    private void resetEffectsOnChildren(ViewGroup recentsView) {
        RecentsState.applyingEffects.set(true);
        try {
            int count = recentsView.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = recentsView.getChildAt(i);
                
                Object sysTrans = child.getTag(RecentsState.TAG_SYS_TRANS_X);
                if (sysTrans instanceof Float) child.setTranslationX((Float) sysTrans);
                
                Object sysScale = child.getTag(RecentsState.TAG_SYS_NON_GRID_SCALE);
                if (sysScale instanceof Float) {
                     child.setScaleX((Float) sysScale);
                     child.setScaleY((Float) sysScale);
                }
                
                Object sysAlpha = child.getTag(RecentsState.TAG_SYS_STABLE_ALPHA);
                if (sysAlpha instanceof Float) child.setAlpha((Float) sysAlpha);
                
                child.setTag(RecentsState.TAG_OFFSET_TRANS, null);
                
                if (Build.VERSION.SDK_INT >= 31) {
                    child.setTag(TAG_CACHE_LAST_RADIUS, null);
                    child.setTag(TAG_CACHE_LAST_TINT, null);
                    callSetRenderEffect(child, null);
                    View thumb = getCachedThumbnailView(child);
                    if (thumb != null) callSetRenderEffect(thumb, null);
                }

                if (child instanceof ViewGroup) {
                    applyIconOffset((ViewGroup) child, 0, 0);
                }
            }
        } finally {
            RecentsState.applyingEffects.set(false);
        }
    }
    
    private void startEntryAnimation(final View view) {
        if (RecentsState.carouselAnimator != null) RecentsState.carouselAnimator.cancel();
        RecentsState.isInRecentsMode = true;
        RecentsState.enteringRecentsUntil = SystemClock.uptimeMillis() + 250L;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(250);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                RecentsState.carouselIntensity = (Float) animation.getAnimatedValue();
                view.invalidate();
            }
        });
        anim.start();
        RecentsState.carouselAnimator = anim;
    }
    
    private void resetState() {
        if (RecentsState.carouselAnimator != null) {
            RecentsState.carouselAnimator.cancel();
            RecentsState.carouselAnimator = null;
        }
        RecentsState.carouselIntensity = 0f;
        RecentsState.isInRecentsMode = false;
        RecentsState.isGestureInProgress = false;
        RecentsState.isAnimatingExit = false;
    }
    
    private void disableLiveTile(ViewGroup view) {
        try {
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        XposedHelpers.callMethod(view, "finishRecentsAnimation", false, false, null);
                        XposedHelpers.callMethod(view, "setEnableDrawingLiveTile", false);
                    } catch (Exception e) {}
                }
            };
            try { XposedHelpers.callMethod(view, "switchToScreenshot", r); } catch (Throwable t) { r.run(); }
        } catch (Exception e) {}
    }

    private void hookCommonScale(ClassLoader classLoader) {
        try {
            Class<?> baseClass = XposedHelpers.findClass(RecentsState.CLASS_BASE_CONTAINER, classLoader);
            Method targetMethod = null;
            for (Method m : baseClass.getDeclaredMethods()) {
                if (m.getName().equals("calculateTaskSize")) {
                    for (Class<?> p : m.getParameterTypes()) {
                        if (p == Rect.class) { targetMethod = m; break; }
                    }
                }
            }
            if (targetMethod != null) {
                XposedBridge.hookMethod(targetMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!Settings.enabled || !Settings.commonScaleEnabled || Settings.commonScalePercent == 100) return;
                        Rect rect = null;
                        for (Object arg : param.args) {
                            if (arg instanceof Rect) { rect = (Rect) arg; break; }
                        }
                        if (rect != null && !rect.isEmpty()) {
                            float s = Settings.commonScalePercent / 100f;
                            int cx = rect.centerX(), cy = rect.centerY();
                            int w = (int)(rect.width() * s), h = (int)(rect.height() * s);
                            rect.set(cx - w/2, cy - h/2, cx + w/2, cy + h/2);
                        }
                    }
                });
            }
        } catch (Throwable t) {}
    }

    @SuppressWarnings("unchecked")
    private void applyIconOffset(ViewGroup taskView, float x, float y) {
        Object cache = taskView.getTag(TAG_CACHE_ICONS);
        List<View> icons;
        if (cache instanceof List) icons = (List<View>) cache;
        else {
            icons = new ArrayList<>();
            scanForIcons(taskView, icons);
            taskView.setTag(TAG_CACHE_ICONS, icons);
        }
        for (View icon : icons) {
            icon.setTranslationX(x);
            icon.setTranslationY(y);
        }
        
        boolean active = (x != 0 || y != 0);
        if (active) {
            taskView.setClipChildren(false);
            taskView.setClipToPadding(false);
            taskView.setClipToOutline(false);
            View thumbnail = getCachedThumbnailView(taskView);
            if (thumbnail != null) {
                thumbnail.setClipToOutline(true);
            }

            if (taskView.getParent() instanceof ViewGroup) {
                ((ViewGroup) taskView.getParent()).setClipChildren(false);
            }
            
            if (taskView.getTouchDelegate() != null && taskView.getTag(RecentsState.TAG_ICON_ORIG_DELEGATE) == null) {
                taskView.setTag(RecentsState.TAG_ICON_ORIG_DELEGATE, taskView.getTouchDelegate());
                taskView.setTouchDelegate(null);
            }
        } else {
             Object orig = taskView.getTag(RecentsState.TAG_ICON_ORIG_DELEGATE);
            if (orig instanceof TouchDelegate) {
                taskView.setTouchDelegate((TouchDelegate) orig);
                taskView.setTag(RecentsState.TAG_ICON_ORIG_DELEGATE, null);
            }
        }
    }

    private void scanForIcons(ViewGroup root, List<View> out) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i);
            if (v == null) continue;
            String name = v.getClass().getSimpleName();
            String fullName = v.getClass().getName();
            if (name.contains("Icon") || name.contains("Chip") || fullName.contains("IconView") || fullName.contains("AppChip")) {
                out.add(v);
            } else if (v instanceof ViewGroup) {
                scanForIcons((ViewGroup) v, out);
            }
        }
    }

    private void callSetRenderEffect(View v, RenderEffect e) {
        if (setRenderEffectMethod != null) {
            try { setRenderEffectMethod.invoke(v, e); } catch (Exception ex) {}
        }
    }

    private View getCachedThumbnailView(View root) {
        Object cached = root.getTag(RecentsState.TAG_CACHE_THUMBNAIL_VIEW);
        if (cached instanceof View) return (View) cached;
        View found = findThumbnailView(root);
        if (found != null) root.setTag(RecentsState.TAG_CACHE_THUMBNAIL_VIEW, found);
        return found;
    }

    private View findThumbnailView(View root) {
        if (!(root instanceof ViewGroup)) return null;
        ArrayDeque<View> q = new ArrayDeque<>();
        q.add(root);
        int depth = 0;
        while (!q.isEmpty() && depth < 3) {
            int size = q.size();
            for (int i=0; i<size; i++) {
                View curr = q.removeFirst();
                String name = curr.getClass().getSimpleName();
                if (name.contains("TaskThumbnailView") || name.contains("Snapshot")) return curr;
                if (curr instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) curr;
                    for (int k=0; k<vg.getChildCount(); k++) q.add(vg.getChildAt(k));
                }
            }
            depth++;
        }
        return null;
    }
    
    @SuppressLint("DiscouragedApi")
    private String getClearAllText(Context ctx) {
        Resources res = ctx.getResources();
        int id = res.getIdentifier("recents_clear_all", "string", ctx.getPackageName());
        if (id == 0) id = res.getIdentifier("clear_all", "string", "android");
        return (id != 0) ? res.getString(id) : "Clear All";
    }

    @SuppressLint("DiscouragedApi")
    private int getButtonSpacing(Context context) {
        Resources res = context.getResources();
        int spacingId = res.getIdentifier("overview_actions_button_spacing", "dimen", context.getPackageName());
        return (spacingId != 0) ? res.getDimensionPixelSize(spacingId) : 24;
    }
    
    private void syncState(final View target, final View source) {
        target.setAlpha(source.getAlpha());
        if (source.getVisibility() == View.VISIBLE) target.setVisibility(View.VISIBLE);
        
        final ViewTreeObserver.OnPreDrawListener l = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (Math.abs(target.getAlpha() - source.getAlpha()) > 0.01f) target.setAlpha(source.getAlpha());
                if (source instanceof ViewGroup && source.getVisibility() != target.getVisibility()) 
                    target.setVisibility(source.getVisibility());
                return true;
            }
        };
        source.getViewTreeObserver().addOnPreDrawListener(l);
        target.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) {
                if (source.getViewTreeObserver().isAlive()) source.getViewTreeObserver().removeOnPreDrawListener(l);
            }
        });
    }
}



















// package org.pixel.customparts.hooks.recents;

// import android.content.Context;
// import android.view.TouchDelegate;
// import android.view.View;
// import android.view.ViewGroup;
// import android.view.ViewTreeObserver;

// import java.util.ArrayList;
// import java.util.List;

// import de.robv.android.xposed.XC_MethodHook;
// import de.robv.android.xposed.XposedHelpers;
// import org.pixel.customparts.core.BaseHook;

// public class RecentsTextIconOffsetHook extends BaseHook {

//     private static final String KEY_MODIFY_ENABLE = "launcher_recents_modify_enable";
//     private static final String KEY_OFFSET_X = "launcher_recents_carousel_icon_offset_x";
//     private static final String KEY_OFFSET_Y = "launcher_recents_carousel_icon_offset_y";

//     @Override
//     public String getHookId() {
//         return "RecentsTextIconOffsetHook";
//     }

//     @Override
//     public int getPriority() {
//         return 50;
//     }

//     @Override
//     public boolean isEnabled(Context context) {
//         int x = getIntSetting(context, KEY_OFFSET_X, 0);
//         int y = getIntSetting(context, KEY_OFFSET_Y, 0);
//         return isSettingEnabled(context, KEY_MODIFY_ENABLE) && (x != 0 || y != 0);
//     }

//     @Override
//     protected void onInit(ClassLoader classLoader) {
//         try {
//             Class<?> recentsViewClass = XposedHelpers.findClass(RecentsState.CLASS_RECENTS_VIEW, classLoader);

//             XposedHelpers.findAndHookMethod(recentsViewClass, "onAttachedToWindow", new XC_MethodHook() {
//                 @Override
//                 protected void afterHookedMethod(MethodHookParam param) {
//                     if (!(param.thisObject instanceof ViewGroup)) return;
//                     final ViewGroup view = (ViewGroup) param.thisObject;

//                     Object oldListenerObj = view.getTag(RecentsState.TAG_ICON_PREDRAW);
//                     if (oldListenerObj instanceof ViewTreeObserver.OnPreDrawListener) {
//                         view.getViewTreeObserver().removeOnPreDrawListener((ViewTreeObserver.OnPreDrawListener) oldListenerObj);
//                     }

//                     ViewTreeObserver.OnPreDrawListener listener = new ViewTreeObserver.OnPreDrawListener() {
//                         @Override
//                         public boolean onPreDraw() {
//                             applyEffect(view);
//                             return true;
//                         }
//                     };

//                     view.getViewTreeObserver().addOnPreDrawListener(listener);
//                     view.setTag(RecentsState.TAG_ICON_PREDRAW, listener);
//                 }
//             });
//         } catch (Throwable e) {
//             logError("Failed to hook RecentsView.onAttachedToWindow", e);
//         }
//     }

//     private void applyEffect(ViewGroup recentsView) {
//         Context context = recentsView.getContext();
//         if (!isSettingEnabled(context, KEY_MODIFY_ENABLE)) return;
//         if (!RecentsState.isInRecentsMode) return;

//         float intensity = RecentsState.carouselIntensity;
//         float offsetX = getIntSetting(context, KEY_OFFSET_X, 0) * intensity;
//         float offsetY = getIntSetting(context, KEY_OFFSET_Y, 0) * intensity;

//         for (int i = 0; i < recentsView.getChildCount(); i++) {
//             View child = recentsView.getChildAt(i);
//             if (!(child instanceof ViewGroup)) continue;
            
//             String className = child.getClass().getSimpleName();

//             // Пропускаем кнопку "Очистить все"
//             if (className.contains("ClearAll")) continue;

//             // Пропускаем сгруппированные задачи (если нужно, логика из оригинала)
//             if (className.contains("Grouped")) continue;

//             applyToTaskView((ViewGroup) child, offsetX, offsetY);
//         }
//     }

//     private void applyToTaskView(ViewGroup taskView, float x, float y) {
//         List<View> icons = new ArrayList<>();
//         scanForIcons(taskView, icons);

//         for (View icon : icons) {
//             icon.setTranslationX(x);
//             icon.setTranslationY(y);
//         }

//         if (x != 0f || y != 0f) {
//             taskView.setClipChildren(false);
//             taskView.setClipToPadding(false);
            
//             if (taskView.getParent() instanceof ViewGroup) {
//                 ((ViewGroup) taskView.getParent()).setClipChildren(false);
//             }

//             if (taskView.getTouchDelegate() != null && taskView.getTag(RecentsState.TAG_ICON_ORIG_DELEGATE) == null) {
//                 taskView.setTag(RecentsState.TAG_ICON_ORIG_DELEGATE, taskView.getTouchDelegate());
//                 taskView.setTouchDelegate(null);
//             }
//         } else {
//             Object origDelegateObj = taskView.getTag(RecentsState.TAG_ICON_ORIG_DELEGATE);
//             if (origDelegateObj instanceof TouchDelegate) {
//                 taskView.setTouchDelegate((TouchDelegate) origDelegateObj);
//                 taskView.setTag(RecentsState.TAG_ICON_ORIG_DELEGATE, null);
//             }
//         }
//     }

//     private void scanForIcons(ViewGroup root, List<View> out) {
//         for (int i = 0; i < root.getChildCount(); i++) {
//             View v = root.getChildAt(i);
//             if (v == null) continue;
            
//             String name = v.getClass().getSimpleName();
//             String fullName = v.getClass().getName();

//             boolean isMatch = false;
//             if (name.contains("Icon") || name.contains("Chip") ||
//                 fullName.contains("IconView") || fullName.contains("iconView") ||
//                 fullName.contains("AppChip")) {
//                 out.add(v);
//                 isMatch = true;
//             }

//             if (!isMatch && v instanceof ViewGroup) {
//                 scanForIcons((ViewGroup) v, out);
//             }
//         }
//     }
// }