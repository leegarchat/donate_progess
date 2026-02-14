/*
 * =========================================================================================
 * ИНСТРУКЦИЯ ПО ВНЕДРЕНИЮ OVERSCROLL В AOSP (EdgeEffect.java)
 * Файл назначения: frameworks/base/core/java/android/widget/EdgeEffect.java
 * =========================================================================================
 */

// -----------------------------------------------------------------------------------------
// ШАГ 1: ДОБАВИТЬ ИМПОРТЫ (В начало файла, к остальным импортам)
// -----------------------------------------------------------------------------------------
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.util.WeakHashMap;

// -----------------------------------------------------------------------------------------
// ШАГ 2: ДОБАВИТЬ ПОЛЯ И КОНСТАНТЫ (В начало класса EdgeEffect, после существующих полей)
// -----------------------------------------------------------------------------------------

    // --- НАЧАЛО CUSTOM FIELDS ---
    private SpringDynamics mCustomSpring;
    private Context mCustomContext; // Нам нужно хранить Context, оригинал EdgeEffect его не хранит как поле
    
    // Поля для анимации и логики
    private float mCustomSmoothOffsetY = 0f;
    private float mCustomSmoothScale = 1.0f;
    private float mCustomSmoothZoom = 1.0f;
    private float mCustomSmoothHScale = 1.0f;
    private float mCustomLastDelta = 0f;
    private float mCustomTargetFingerX = 0.5f;
    private float mCustomCurrentFingerX = 0.5f;
    private boolean mCustomFirstTouch = true;
    
    private float mCustomScreenHeight = 2200f;
    private float mCustomScreenWidth = 1080f;
    
    private float mCfgScale = 1.0f;
    private boolean mCfgFilter = false;
    private boolean mCfgIgnore = false;

    // Кеши и вспомогательные объекты
    private final Matrix mCustomMatrix = new Matrix();
    private final float[] mCustomPoints = new float[4];
    private static final WeakHashMap<Object, Boolean> sComposeCache = new WeakHashMap<>();

    // Настройки (Все ключи переименованы с суффиксом _pine)
    private static final String KEY_ENABLED = "overscroll_enabled_pine";
    private static final String KEY_PACKAGES_CONFIG = "overscroll_packages_config_pine";
    private static final String KEY_PULL_COEFF = "overscroll_pull_pine";
    private static final String KEY_STIFFNESS = "overscroll_stiffness_pine";
    private static final String KEY_DAMPING = "overscroll_damping_pine";
    private static final String KEY_FLING = "overscroll_fling_pine";
    private static final String KEY_PHYSICS_MIN_VEL = "overscroll_physics_min_vel_pine";
    private static final String KEY_PHYSICS_MIN_VAL = "overscroll_physics_min_val_pine";
    private static final String KEY_INPUT_SMOOTH_FACTOR = "overscroll_input_smooth_pine";
    private static final String KEY_RESISTANCE_EXPONENT = "overscroll_res_exponent_pine";
    private static final String KEY_LERP_MAIN_IDLE = "overscroll_lerp_main_idle_pine";
    private static final String KEY_LERP_MAIN_RUN = "overscroll_lerp_main_run_pine";
    private static final String KEY_COMPOSE_SCALE = "overscroll_compose_scale_pine";
    private static final String KEY_SCALE_MODE = "overscroll_scale_mode_pine";
    private static final String KEY_SCALE_INTENSITY = "overscroll_scale_intensity_pine";
    private static final String KEY_SCALE_LIMIT_MIN = "overscroll_scale_limit_min_pine";
    private static final String KEY_ZOOM_MODE = "overscroll_zoom_mode_pine";
    private static final String KEY_ZOOM_INTENSITY = "overscroll_zoom_intensity_pine";
    private static final String KEY_ZOOM_LIMIT_MIN = "overscroll_zoom_limit_min_pine";
    private static final String KEY_ZOOM_ANCHOR_X = "overscroll_zoom_anchor_x_pine";
    private static final String KEY_ZOOM_ANCHOR_Y = "overscroll_zoom_anchor_y_pine";
    private static final String KEY_H_SCALE_MODE = "overscroll_h_scale_mode_pine";
    private static final String KEY_H_SCALE_INTENSITY = "overscroll_h_scale_intensity_pine";
    private static final String KEY_H_SCALE_LIMIT_MIN = "overscroll_h_scale_limit_min_pine";
    private static final String KEY_SCALE_ANCHOR_Y = "overscroll_scale_anchor_y_pine";
    private static final String KEY_H_SCALE_ANCHOR_X = "overscroll_h_scale_anchor_x_pine";
    private static final String KEY_SCALE_ANCHOR_X_HORIZ = "overscroll_scale_anchor_x_horiz_pine";
    private static final String KEY_H_SCALE_ANCHOR_Y_HORIZ = "overscroll_h_scale_anchor_y_horiz_pine";
    private static final String KEY_ZOOM_ANCHOR_X_HORIZ = "overscroll_zoom_anchor_x_horiz_pine";
    private static final String KEY_ZOOM_ANCHOR_Y_HORIZ = "overscroll_zoom_anchor_y_horiz_pine";
    private static final String KEY_SCALE_INTENSITY_HORIZ = "overscroll_scale_intensity_horiz_pine";
    private static final String KEY_ZOOM_INTENSITY_HORIZ = "overscroll_zoom_intensity_horiz_pine";
    private static final String KEY_H_SCALE_INTENSITY_HORIZ = "overscroll_h_scale_intensity_horiz_pine";
    private static final String KEY_INVERT_ANCHOR = "overscroll_invert_anchor_pine";
    
    private static final float FILTER_THRESHOLD = 0.08f;
    // --- КОНЕЦ CUSTOM FIELDS ---

// -----------------------------------------------------------------------------------------
// ШАГ 3: МОДИФИЦИРОВАТЬ КОНСТРУКТОРЫ
// В оригинальном EdgeEffect есть конструкторы public EdgeEffect(Context context) и (Context, AttributeSet)
// Вам нужно добавить вызов initCustomInstance(context) в конец ОБОИХ конструкторов.
// -----------------------------------------------------------------------------------------

    // /* ПРИМЕР:
    public EdgeEffect(@NonNull Context context, @Nullable AttributeSet attrs) {
        // ... оригинальный код ...
        mPaint.setBlendMode(DEFAULT_BLEND_MODE);
        
        // ДОБАВИТЬ ЭТУ СТРОКУ:
        initCustomInstance(context);
    }
    // */

// -----------------------------------------------------------------------------------------
// ШАГ 4: ЗАМЕНИТЬ МЕТОД setSize (Опционально, но полезно для сброса)
// -----------------------------------------------------------------------------------------
    
    public void setSize(int width, int height) {
        // ... весь оригинальный код setSize ...
        
        // В конце метода добавить:
        if (mCustomContext != null) {
            // Обновляем метрики экрана при изменении размера, если нужно
            // В принципе можно оставить как есть, initCustomInstance уже задал дефолты
        }
    }

// -----------------------------------------------------------------------------------------
// ШАГ 5: ЗАМЕНИТЬ МЕТОД isFinished()
// -----------------------------------------------------------------------------------------

    public boolean isFinished() {
        if (!isBounceEnabled()) {
            return mState == STATE_IDLE; // Оригинальная логика
        }

        float minVal = getFloatSetting(KEY_PHYSICS_MIN_VAL, 4.0f);

        if (mCustomSpring != null) {
            boolean physicsDone = !mCustomSpring.isRunning() && Math.abs(mCustomSpring.mValue) < minVal;
            boolean visualDone = (Math.abs(mCustomSmoothOffsetY) < minVal);
            boolean fullyFinished = physicsDone && visualDone;

            if (physicsDone && !visualDone) {
                float diff = Math.abs(mCustomSmoothOffsetY);
                if (diff < minVal * 3) {
                    mCustomSmoothOffsetY = 0f;
                    fullyFinished = true;
                }
            }

            if (fullyFinished) {
                resetCustomState();
                mState = STATE_IDLE;
                mDistance = 0f;
            }
            return fullyFinished;
        }
        return true;
    }

// -----------------------------------------------------------------------------------------
// ШАГ 6: ЗАМЕНИТЬ МЕТОД finish()
// -----------------------------------------------------------------------------------------

    public void finish() {
        if (isBounceEnabled()) {
            if (mCustomSpring != null) {
                mCustomSpring.cancel();
                mCustomSpring.mValue = 0;
                mCustomSpring.mVelocity = 0;
            }
            resetCustomState();
        }
        
        mState = STATE_IDLE;
        mDistance = 0;
        mVelocity = 0;
    }

// -----------------------------------------------------------------------------------------
// ШАГ 7: В НАЧАЛЕ  onPull(float deltaDistance, float displacement)
// (Метод onPull(float deltaDistance) можно не трогать, он вызывает этот)
// -----------------------------------------------------------------------------------------

    public void onPull(float deltaDistance, float displacement) {
        if (isBounceEnabled()) {
            // --- ЛОГИКА OVERSCROLL ---
            if (isComposeCaller()) {
                float composeDivisor = getFloatSetting(KEY_COMPOSE_SCALE, 3.33f);
                if (composeDivisor < 0.01f) composeDivisor = 1.0f;
                deltaDistance /= composeDivisor;
            }

            if (mCustomSpring == null) return;

            if (mCfgFilter && Math.abs(deltaDistance) > FILTER_THRESHOLD) return;
            
            float correctedDelta = (Math.abs(mCfgScale) > 0.001f) ? deltaDistance / mCfgScale : deltaDistance;

            float inputSmoothFactor = getFloatSetting(KEY_INPUT_SMOOTH_FACTOR, 0.5f);
            
            if (mCustomFirstTouch) {
                mCustomLastDelta = correctedDelta;
                mCustomFirstTouch = false;
            }

            boolean directionChanged = (correctedDelta > 0 && mCustomLastDelta < 0) || (correctedDelta < 0 && mCustomLastDelta > 0);
            float filteredDelta = directionChanged ? correctedDelta : (correctedDelta * (1.0f - inputSmoothFactor) + mCustomLastDelta * inputSmoothFactor);
            
            mCustomLastDelta = filteredDelta;
            mCustomTargetFingerX = displacement;

            mState = STATE_PULL;
            mCustomSpring.cancel();

            float currentTranslation = mCustomSpring.mValue;
            float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
            if (effectiveSize < 1f) effectiveSize = mCustomScreenHeight;

            float rawMove = filteredDelta * effectiveSize;
            float pullCoeff = getFloatSetting(KEY_PULL_COEFF, 0.5f);
            float resExponent = getFloatSetting(KEY_RESISTANCE_EXPONENT, 4.0f);

            boolean isPullingAway = (currentTranslation > 0 && rawMove > 0) || (currentTranslation < 0 && rawMove < 0);
            float change;

            if (pullCoeff >= 1.0f) {
                change = rawMove * pullCoeff;
            } else {
                if (isPullingAway) {
                    float ratio = Math.min(Math.abs(currentTranslation) / mCustomScreenHeight, 1f);
                    float resistance = (float) Math.pow(1.0f - ratio, resExponent);
                    change = rawMove * resistance;
                } else {
                    change = rawMove;
                }
            }

            float nextTranslation = currentTranslation + change;
            
            // Предотвращение пересечения нуля (смена знака) при пулле
            if ((currentTranslation > 0 && nextTranslation < 0) || (currentTranslation < 0 && nextTranslation > 0)) {
                nextTranslation = 0f;
            }

            mCustomSpring.mValue = nextTranslation;
            mDistance = nextTranslation / effectiveSize;
            return;
        }

        
    }

// -----------------------------------------------------------------------------------------
// ШАГ 8: В НАЧАЛЕ onRelease()
// -----------------------------------------------------------------------------------------

    public void onRelease() {
        if (isBounceEnabled()) {
            if (mCustomSpring != null && Math.abs(mCustomSpring.mValue) > 0.5f) {
                float stiffness = getFloatSetting(KEY_STIFFNESS, 450f);
                float damping = getFloatSetting(KEY_DAMPING, 0.7f);
                float minVel = getFloatSetting(KEY_PHYSICS_MIN_VEL, 80.0f);
                float minVal = getFloatSetting(KEY_PHYSICS_MIN_VAL, 4.0f);

                mCustomSpring.setParams(stiffness, damping, minVel, minVal);
                mCustomSpring.setTargetValue(0);
                mCustomSpring.setVelocity(0);
                mCustomSpring.start();
                mState = STATE_RECEDE; // Используем стандартный стейт для анимации возврата
            } else {
                mState = STATE_IDLE;
                mDistance = 0f;
            }

            mCustomTargetFingerX = 0.5f;
            mCustomLastDelta = 0f;
            mCustomFirstTouch = true;
            return;
        }

        
    }

// -----------------------------------------------------------------------------------------
// ШАГ 9: В НАЧАЛЕ onAbsorb(int velocity)
// -----------------------------------------------------------------------------------------

    public void onAbsorb(int velocity) {
        if (isBounceEnabled()) {
            mState = STATE_RECEDE;

            if (mCustomSpring != null) {
                mCustomSpring.cancel();

                float flingMult = getFloatSetting(KEY_FLING, 0.6f);
                float stiffness = getFloatSetting(KEY_STIFFNESS, 450f);
                float damping = getFloatSetting(KEY_DAMPING, 0.7f);
                float minVel = getFloatSetting(KEY_PHYSICS_MIN_VEL, 80.0f);
                float minVal = getFloatSetting(KEY_PHYSICS_MIN_VAL, 4.0f);

                float velocityPx = velocity * flingMult;
                if (flingMult > 1.0f) stiffness /= flingMult;

                float maxVel = mCustomScreenHeight * 10f;
                if (Math.abs(velocityPx) > maxVel) velocityPx = Math.signum(velocityPx) * maxVel;

                mCustomSpring.setParams(stiffness, damping, minVel, minVal);
                mCustomSpring.setTargetValue(0);
                mCustomSpring.setVelocity(velocityPx);
                mCustomSpring.start();
            }
            
            mCustomTargetFingerX = 0.5f;
            mCustomLastDelta = 0f;
            mCustomFirstTouch = true;
            return;
        }

        
    }

// -----------------------------------------------------------------------------------------
// ШАГ 10: В НАЧАЛЕ draw(Canvas canvas)
// ЭТО САМЫЙ ВАЖНЫЙ МЕТОД
// -----------------------------------------------------------------------------------------

    public boolean draw(Canvas canvas) {
        if (isBounceEnabled()) {
            // --- ЛОГИКА OVERSCROLL ---
            if (!canvas.isHardwareAccelerated()) return false;
            if (mCustomSpring == null) return false;

            if (mCustomSpring.isRunning()) mCustomSpring.doFrame(System.nanoTime());

            // В AOSP мы можем получить доступ к mNode, если приводим к RecordingCanvas
            if (!(canvas instanceof RecordingCanvas)) return false;
            RecordingCanvas recordingCanvas = (RecordingCanvas) canvas;
            RenderNode renderNode = recordingCanvas.mNode; // В AOSP поле mNode доступно (package-private или public)
            
            if (renderNode == null) return false;

            // Расчет векторов трансформации
            canvas.getMatrix(mCustomMatrix);
            mCustomPoints[0] = 0; mCustomPoints[1] = 1;
            mCustomMatrix.mapVectors(mCustomPoints);

            float vx = mCustomPoints[0];
            float vy = mCustomPoints[1];

            if (Math.abs(vx) > Math.abs(vy)) {
                vx = Math.signum(vx);
                vy = 0f;
            } else {
                vy = Math.signum(vy);
                vx = 0f;
            }

            boolean isVertical = (vy != 0);

            float lerpMainIdle = getFloatSetting(KEY_LERP_MAIN_IDLE, 0.4f);
            float lerpMainRun = getFloatSetting(KEY_LERP_MAIN_RUN, 0.7f);
            float lerpFactorMain = mCustomSpring.isRunning() ? lerpMainRun : lerpMainIdle;

            float targetOffset = mCustomSpring.mValue;
            float currentOffset = mCustomSmoothOffsetY;
            float newOffset = currentOffset + (targetOffset - currentOffset) * lerpFactorMain;

            float minVal = getFloatSetting(KEY_PHYSICS_MIN_VAL, 4.0f);
            if (Math.abs(targetOffset - newOffset) < 0.5f) newOffset = targetOffset;
            if (Math.abs(targetOffset) < 0.1f && Math.abs(newOffset) < minVal) newOffset = 0f;

            mCustomSmoothOffsetY = newOffset;

            float maxDistance = isVertical ? mCustomScreenHeight : mCustomScreenWidth;
            float ratio = (maxDistance > 0) ? Math.min(Math.abs(newOffset) / maxDistance, 1.0f) : 0f;
            boolean isActive = Math.abs(newOffset) > 1.0f;

            // Расчет скейла
            float targetScaleV = 1f, targetScaleZ = 1f, targetScaleH = 1f;

            if (isActive) {
                String keyScaleInt = isVertical ? KEY_SCALE_INTENSITY : KEY_SCALE_INTENSITY_HORIZ;
                String keyZoomInt = isVertical ? KEY_ZOOM_INTENSITY : KEY_ZOOM_INTENSITY_HORIZ;
                String keyHScaleInt = isVertical ? KEY_H_SCALE_INTENSITY : KEY_H_SCALE_INTENSITY_HORIZ;

                targetScaleV = calcScale(KEY_SCALE_MODE, keyScaleInt, KEY_SCALE_LIMIT_MIN, ratio);
                targetScaleZ = calcScale(KEY_ZOOM_MODE, keyZoomInt, KEY_ZOOM_LIMIT_MIN, ratio);
                targetScaleH = calcScale(KEY_H_SCALE_MODE, keyHScaleInt, KEY_H_SCALE_LIMIT_MIN, ratio);
            }

            float newScaleV = lerp(mCustomSmoothScale, targetScaleV, lerpFactorMain);
            float newScaleZ = lerp(mCustomSmoothZoom, targetScaleZ, lerpFactorMain);
            float newScaleH = lerp(mCustomSmoothHScale, targetScaleH, lerpFactorMain);

            mCustomSmoothScale = newScaleV;
            mCustomSmoothZoom = newScaleZ;
            mCustomSmoothHScale = newScaleH;

            boolean isResting = Math.abs(newOffset) < 0.1f && Math.abs(newScaleV - 1f) < 0.001f;
            if (isResting && !mCustomSpring.isRunning()) {
                // Reset RenderNode params
                renderNode.setTranslationX(0f);
                renderNode.setTranslationY(0f);
                renderNode.setScaleX(1f);
                renderNode.setScaleY(1f);
                // stretch reset
                renderNode.stretch(0f, 0f, 0f, 0f);
                return false;
            }

            float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
            if (effectiveSize < 1f) effectiveSize = mCustomScreenHeight;
            mDistance = newOffset / effectiveSize;

            // --- ПРИМЕНЕНИЕ ТРАНСФОРМАЦИЙ К RENDER NODE ---
            // Так как мы в AOSP, методы доступны напрямую без рефлексии
            renderNode.setTranslationX(newOffset * vx);
            renderNode.setTranslationY(newOffset * vy);

            float axisMainScale = newScaleV * newScaleZ;
            float axisCrossScale = newScaleH * newScaleZ;

            float finalScaleX, finalScaleY;

            if (isVertical) {
                finalScaleX = axisCrossScale;
                finalScaleY = axisMainScale;
            } else {
                finalScaleX = axisMainScale;
                finalScaleY = axisCrossScale;
            }

            float ax = 0.5f;
            float ay = 0.5f;
            
            boolean zoomActive = getIntSetting(KEY_ZOOM_MODE, 0) != 0;
            boolean scaleActive = getIntSetting(KEY_SCALE_MODE, 0) != 0;
            boolean hScaleActive = getIntSetting(KEY_H_SCALE_MODE, 0) != 0;

            if (isVertical) {
                if (zoomActive) {
                    ax = getFloatSetting(KEY_ZOOM_ANCHOR_X, 0.5f);
                    ay = getFloatSetting(KEY_ZOOM_ANCHOR_Y, 0.5f);
                } else if (scaleActive) {
                    ax = 0.5f;
                    ay = getFloatSetting(KEY_SCALE_ANCHOR_Y, 0.5f);
                } else if (hScaleActive) {
                    ax = getFloatSetting(KEY_H_SCALE_ANCHOR_X, 0.5f);
                    ay = 0.5f;
                }
            } else {
                if (zoomActive) {
                    ax = getFloatSetting(KEY_ZOOM_ANCHOR_X_HORIZ, 0.5f);
                    ay = getFloatSetting(KEY_ZOOM_ANCHOR_Y_HORIZ, 0.5f);
                } else if (scaleActive) {
                    ax = getFloatSetting(KEY_SCALE_ANCHOR_X_HORIZ, 0.5f);
                    ay = 0.5f;
                } else if (hScaleActive) {
                    ax = 0.5f;
                    ay = getFloatSetting(KEY_H_SCALE_ANCHOR_Y_HORIZ, 0.5f);
                }
            }

            boolean invertAnchor = getIntSetting(KEY_INVERT_ANCHOR, 1) == 1;
            float pivotX, pivotY;
            float canvasW = (float) canvas.getWidth();
            float canvasH = (float) canvas.getHeight();

            if (isVertical) {
                pivotX = canvasW * ax;
                if (vy > 0) {
                    pivotY = canvasH * ay;
                } else {
                    pivotY = canvasH * (invertAnchor ? (1.0f - ay) : ay);
                }
            } else {
                pivotY = canvasH * ay;
                if (vx > 0) {
                    pivotX = canvasW * ax;
                } else {
                    pivotX = canvasW * (invertAnchor ? (1.0f - ax) : ax);
                }
            }

            renderNode.setPivotX(pivotX);
            renderNode.setPivotY(pivotY);
            renderNode.setScaleX(finalScaleX);
            renderNode.setScaleY(finalScaleY);
            
            // Сбрасываем стандартный стрейч эффект, чтобы не мешал
            renderNode.stretch(0f, 0f, mWidth, mHeight);

            return true;
        }

        
    }

// -----------------------------------------------------------------------------------------
// ШАГ 11: ДОБАВИТЬ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ И ВНУТРЕННИЙ КЛАСС (В конец класса EdgeEffect)
// -----------------------------------------------------------------------------------------

    private void initCustomInstance(Context context) {
        mCustomContext = context;
        mCustomSpring = new SpringDynamics();
        
        // Defaults
        float screenHeight = 2200f;
        float screenWidth = 1080f;
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics dm = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(dm);
                screenHeight = dm.heightPixels;
                screenWidth = dm.widthPixels;
            }
        } catch (Exception ignored) {}
        mCustomScreenHeight = screenHeight;
        mCustomScreenWidth = screenWidth;

        // Package config check
        updatePackageConfig();
    }

    private void updatePackageConfig() {
        if (mCustomContext == null) return;
        try {
            String pkgName = mCustomContext.getPackageName();
            String configString = getStringSetting(KEY_PACKAGES_CONFIG);
            if (!TextUtils.isEmpty(configString) && pkgName != null) {
                String[] apps = configString.split(" ");
                for (String appConfig : apps) {
                    String[] parts = appConfig.split(":");
                    if (parts.length >= 3 && parts[0].equals(pkgName)) {
                        mCfgFilter = Integer.parseInt(parts[1]) == 1;
                        mCfgScale = Float.parseFloat(parts[2]);
                        if (parts.length >= 4) mCfgIgnore = parts[3].equals("1");
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void resetCustomState() {
        mCustomSmoothOffsetY = 0f;
        mCustomSmoothScale = 1.0f;
        mCustomSmoothZoom = 1.0f;
        mCustomSmoothHScale = 1.0f;
        mCustomTargetFingerX = 0.5f;
        mCustomCurrentFingerX = 0.5f;
        mCustomFirstTouch = true;
    }

    private boolean isBounceEnabled() {
        if (mCustomContext == null) return true;
        try {
            if (getIntSetting(KEY_ENABLED, 1) != 1) return false;
            if (mCfgIgnore) return false;
            return true;
        } catch (Exception ignored) { return true; }
    }

    private boolean isComposeCaller() {
        if (sComposeCache.containsKey(this)) return Boolean.TRUE.equals(sComposeCache.get(this));
        boolean isCompose = false;
        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stack) {
                if (element.getClassName().startsWith("androidx.compose")) {
                    isCompose = true;
                    break;
                }
            }
        } catch (Exception ignored) {}
        sComposeCache.put(this, isCompose);
        return isCompose;
    }

    // Хелперы настроек (используем Settings.Global, так как это системный код)
    private float getFloatSetting(String key, float def) {
        if (mCustomContext == null) return def;
        try {
            return Settings.Global.getFloat(mCustomContext.getContentResolver(), key, def);
        } catch (Exception ignored) { return def; }
    }

    private int getIntSetting(String key, int def) {
        if (mCustomContext == null) return def;
        try {
            return Settings.Global.getInt(mCustomContext.getContentResolver(), key, def);
        } catch (Exception ignored) { return def; }
    }

    private String getStringSetting(String key) {
        if (mCustomContext == null) return null;
        try {
            return Settings.Global.getString(mCustomContext.getContentResolver(), key);
        } catch (Exception ignored) { return null; }
    }

    private float calcScale(String modeKey, String intKey, String limKey, float ratio) {
        int mode = getIntSetting(modeKey, 0);
        float intensity = getFloatSetting(intKey, 0.0f);
        float limit = getFloatSetting(limKey, 0.3f);
        if (mode == 0 || intensity <= 0) return 1.0f;
        if (mode == 1) return Math.max(1.0f - (ratio * intensity), limit);
        if (mode == 2) return 1.0f + (ratio * intensity);
        return 1.0f;
    }

    private float lerp(float start, float end, float factor) {
        return start + (end - start) * factor;
    }

    // Внутренний класс физики
    private static class SpringDynamics {
        private float mStiffness = 450.0f;
        private float mDampingRatio = 0.7f;
        private float mMinVel = 1.0f;
        private float mMinVal = 0.5f;

        public float mValue;
        public float mVelocity;
        public float mTargetValue = 0f;
        private boolean mIsRunning = false;
        private long mLastFrameTimeNanos = 0;

        public void setParams(float stiffness, float damping, float minVel, float minVal) {
            mStiffness = stiffness > 0 ? stiffness : 0.1f;
            mDampingRatio = damping >= 0 ? damping : 0;
            mMinVel = minVel;
            mMinVal = minVal;
        }

        public void setTargetValue(float targetValue) { mTargetValue = targetValue; }
        public void setVelocity(float velocity) { mVelocity = velocity; }
        public boolean isRunning() { return mIsRunning; }
        public void cancel() { mIsRunning = false; }

        public void start() {
            if (mIsRunning) return;
            mIsRunning = true;
            mLastFrameTimeNanos = System.nanoTime();
        }

        public void doFrame(long frameTimeNanos) {
            if (!mIsRunning) return;
            long deltaTimeNanos = frameTimeNanos - mLastFrameTimeNanos;
            if (deltaTimeNanos > 100_000_000) deltaTimeNanos = 16_000_000;
            mLastFrameTimeNanos = frameTimeNanos;
            float dt = deltaTimeNanos / 1_000_000_000.0f;

            float displacement = mValue - mTargetValue;
            float dampingCoefficient = 2 * mDampingRatio * (float) Math.sqrt(mStiffness);
            float force = -mStiffness * displacement - dampingCoefficient * mVelocity;

            if (Float.isNaN(force) || Float.isInfinite(force)) force = 0;

            mVelocity += force * dt;
            mValue += mVelocity * dt;

            if (Float.isNaN(mValue) || Float.isInfinite(mValue)) {
                mValue = mTargetValue;
                mVelocity = 0;
                cancel();
                return;
            }

            if (Math.abs(mVelocity) < mMinVel && Math.abs(mValue - mTargetValue) < mMinVal) {
                mValue = mTargetValue;
                mVelocity = 0;
                cancel();
            }
        }
    }