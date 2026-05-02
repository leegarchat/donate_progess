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