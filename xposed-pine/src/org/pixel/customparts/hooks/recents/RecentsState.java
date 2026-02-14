package org.pixel.customparts.hooks.recents;

import android.animation.ValueAnimator;

public class RecentsState {
    public static final String CLASS_RECENTS_VIEW = "com.android.quickstep.views.RecentsView";
    public static final String CLASS_TASK_VIEW = "com.android.quickstep.views.TaskView";
    public static final String CLASS_BASE_CONTAINER = "com.android.quickstep.BaseContainerInterface";

    // Уникальные ID для тегов (чтобы не конфликтовать с системными)
    public static final int TAG_HOOK_INSTALLED = 0x7F0B0001;
    public static final int TAG_PREDRAW_LISTENER = 0x7F0B0002;
    public static final int TAG_PENDING_END_TARGET = 0x7F0B0003;
    public static final int TAG_ICON_ORIG_DELEGATE = 0x7F0B0004;

    // Теги для хранения системных значений (до модификации)
    public static final int TAG_SYS_TRANS_X = 0x7F0B0010;
    public static final int TAG_SYS_STABLE_ALPHA = 0x7F0B0011;
    public static final int TAG_SYS_NON_GRID_SCALE = 0x7F0B0012;

    // Теги для кеширования вычисленных значений (для оптимизации)
    public static final int TAG_CACHE_LAST_RADIUS = 0x7F0B0020;
    public static final int TAG_CACHE_LAST_TINT_ALPHA = 0x7F0B0021;
    public static final int TAG_CACHE_THUMBNAIL_VIEW = 0x7F0B0022;

    // Теги для хранения примененных оффсетов (чтобы не накладывать дважды)
    public static final int TAG_OFFSET_TRANS = 0x7F0B0030;
    public static final int TAG_OFFSET_SCALE = 0x7F0B0031;
    public static final int TAG_OFFSET_ALPHA = 0x7F0B0032;

    // ThreadLocal для предотвращения рекурсии (когда хук вызывает метод, который тоже хукнут)
    public static final ThreadLocal<Boolean> applyingEffects = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    // Глобальное состояние анимации
    public static volatile float carouselIntensity = 0f;
    public static volatile ValueAnimator carouselAnimator = null;
    public static volatile boolean isInRecentsMode = false;
    public static volatile boolean isGestureInProgress = false;
    public static volatile boolean isAnimatingExit = false;
    public static volatile long enteringRecentsUntil = 0L;
}


















// package org.pixel.customparts.hooks.recents;

// import android.animation.ValueAnimator;
// import android.graphics.RenderEffect;
// import android.os.Build;
// import android.view.View;
// import android.view.ViewGroup;

// import de.robv.android.xposed.XposedHelpers;

// public class RecentsState {
//     public static final String CLASS_RECENTS_VIEW = "com.android.quickstep.views.RecentsView";
//     public static final String CLASS_TASK_VIEW = "com.android.quickstep.views.TaskView";
//     public static final String CLASS_BASE_CONTAINER = "com.android.quickstep.BaseContainerInterface";
    
//     public static final int TAG_CAROUSEL_PREDRAW_INSTALLED = 0x7F0B0002;
//     public static final int TAG_CAROUSEL_PREDRAW_LISTENER = 0x7F0B0003;
//     public static final int TAG_PENDING_END_TARGET = 0x7F0B0004;
//     public static final int TAG_SYS_TRANS_X = 0x7F0B0013;
//     public static final int TAG_SYS_ALPHA = 0x7F0B0012;
//     public static final int TAG_SYS_STABLE_ALPHA = 0x7F0B0014;
//     public static final int TAG_SYS_NON_GRID_SCALE = 0x7F0B0015;
//     public static final int TAG_OFFSET_TRANS = 0x7F0B0021;
//     public static final int TAG_OFFSET_ALPHA = 0x7F0B0022;
//     public static final int TAG_OFFSET_SCALE = 0x7F0B0023;

//     public static final int TAG_SCALE_PREDRAW = 0x7F0B0030;
//     public static final int TAG_TINT_PREDRAW = 0x7F0B0031;
//     public static final int TAG_SPACING_PREDRAW = 0x7F0B0032;
//     public static final int TAG_BLUR_PREDRAW = 0x7F0B0033;
//     public static final int TAG_ALPHA_PREDRAW = 0x7F0B0034;
//     public static final int TAG_ICON_PREDRAW = 0x7F0B0035;

//     public static final int TAG_BLUR_EFFECT = 0x7F0B0040;
//     public static final int TAG_TINT_EFFECT = 0x7F0B0041;
//     public static final int TAG_ICON_ORIG_DELEGATE = 0x7F0B0042;

//     public static final ThreadLocal<Boolean> applyingCarousel = new ThreadLocal<Boolean>() {
//         @Override
//         protected Boolean initialValue() {
//             return false;
//         }
//     };

//     public static volatile float carouselIntensity = 0f;
//     public static volatile ValueAnimator carouselAnimator = null;
//     public static volatile boolean isInRecentsMode = false;
//     public static volatile boolean isGestureInProgress = false;
//     public static volatile long enteringRecentsUntil = 0L;
//     public static volatile boolean isAnimatingExit = false;

//     public static void composeAndApplyRenderEffects(View child) {
//         if (Build.VERSION.SDK_INT < 31) return;
        
//         try {
//             Object blurObj = child.getTag(TAG_BLUR_EFFECT);
//             Object tintObj = child.getTag(TAG_TINT_EFFECT);
            
//             RenderEffect blur = (blurObj instanceof RenderEffect) ? (RenderEffect) blurObj : null;
//             RenderEffect tint = (tintObj instanceof RenderEffect) ? (RenderEffect) tintObj : null;
            
//             RenderEffect result = null;
            
//             if (blur != null && tint != null) {
//                 result = RenderEffect.createChainEffect(tint, blur);
//             } else if (blur != null) {
//                 result = blur;
//             } else if (tint != null) {
//                 result = tint;
//             }
            
//             XposedHelpers.callMethod(child, "setRenderEffect", result);
//         } catch (Throwable t) {
//             // ignore
//         }
//     }

//     public static float getTaskViewFactor(View child, ViewGroup parent) {
//         int scrollX = parent.getScrollX();
//         int viewWidth = parent.getWidth();
//         if (viewWidth <= 0) return 0f;
        
//         float screenCenter = scrollX + viewWidth / 2f;
//         float influenceDistance = viewWidth * 0.55f;
        
//         Object offsetTransObj = child.getTag(TAG_OFFSET_TRANS);
//         float lastAppliedOffset = (offsetTransObj instanceof Float) ? (Float) offsetTransObj : 0f;
        
//         float currentTrans = child.getTranslationX();
        
//         Object sysTransObj = child.getTag(TAG_SYS_TRANS_X);
//         Float taggedSys = (sysTransObj instanceof Float) ? (Float) sysTransObj : null;
        
//         float systemTrans = (taggedSys != null) ? taggedSys : (currentTrans - lastAppliedOffset);
        
//         if (taggedSys == null) {
//             child.setTag(TAG_SYS_TRANS_X, systemTrans);
//         }

//         float childCenter = ((child.getLeft() + child.getRight()) / 2f) + systemTrans;
//         float distanceFromCenter = Math.abs(screenCenter - childCenter);
        
//         if (influenceDistance <= 0f) return 0f;
//         else if (distanceFromCenter > influenceDistance) return 1.0f;
//         else return (distanceFromCenter / influenceDistance);
//     }
// }