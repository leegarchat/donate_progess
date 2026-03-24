package org.pixel.customparts.hooks;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.provider.Settings;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import android.widget.EdgeEffect;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class EdgeEffectHook {
    private static final String TAG = "PixelPartsOverscroll";
    private static String sKeySuffix = "_xposed";
    public static void configure(boolean useGlobal, String suffix) {
        if (!useGlobal) {
            Log.w(TAG, "Settings.Secure is no longer supported. Forcing Settings.Global.");
        }
        sKeySuffix = suffix;
    }
    private static String resolveKey(String key) {
        String base = key.replaceAll("_(xposed|pine)$", "");
        return base + sKeySuffix;
    }

    private static final String FIELD_SPRING = "mCustomSpring";
    private static final String FIELD_CONTEXT = "mCustomContext";
    private static final String FIELD_CFG_SCALE = "mCfgScale";
    private static final String FIELD_CFG_IGNORE = "mCfgIgnore";
    private static final String FIELD_CFG_FILTER = "mCfgFilter";
    private static final String FIELD_SMOOTH_OFFSET_Y = "mCustomSmoothOffsetY";
    private static final String FIELD_SMOOTH_SCALE = "mCustomSmoothScale";
    private static final String FIELD_LAST_DELTA = "mCustomLastDelta";
    private static final String FIELD_TARGET_FINGER_X = "mCustomTargetFingerX";
    private static final String FIELD_CURRENT_FINGER_X = "mCustomCurrentFingerX";
    private static final String FIELD_SCREEN_HEIGHT = "mCustomScreenHeight";
    private static final String FIELD_SCREEN_WIDTH = "mCustomScreenWidth";
    private static final String FIELD_FIRST_TOUCH = "mCustomFirstTouch";
    private static final String FIELD_SMOOTH_ZOOM = "mCustomSmoothZoom";
    private static final String FIELD_SMOOTH_H_SCALE = "mCustomSmoothHScale";
    private static final String FIELD_MATRIX = "mCustomMatrix";
    private static final String FIELD_POINTS = "mCustomPoints";
    private static final String FIELD_SETTINGS_CACHE = "mCustomSettingsCache";
    private static final String FIELD_GESTURE_ID = "mCustomGestureId";
    private static final String FIELD_GESTURE_SEQ = "mCustomGestureSeq";
    private static final String FIELD_RECORD_LAST_NS = "mCustomRecordLastNs";
    private static final String RECORD_TAG = "EdgePatternRec";

    // ── Per-instance поля для интеллектуальной нормализации дельт ──
    private static final String FIELD_NORM_WINDOW = "mNormDeltaWindow";
    private static final String FIELD_NORM_IDX = "mNormWindowIdx";
    private static final String FIELD_NORM_COUNT = "mNormWindowCount";
    private static final String FIELD_NORM_FACTOR = "mNormCurrentFactor";
    // true если конструктор вызван из Compose (определено по стек-трейсу)
    private static final String FIELD_IS_COMPOSE = "mIsComposeCaller";
    private static final String FIELD_CALLER_INFO = "mCallerInfo";

    private static final String KEY_ENABLED = "overscroll_enabled";
    private static final String KEY_PACKAGES_CONFIG = "overscroll_packages_config";
    private static final String KEY_PULL_COEFF = "overscroll_pull";
    private static final String KEY_STIFFNESS = "overscroll_stiffness";
    private static final String KEY_DAMPING = "overscroll_damping";
    private static final String KEY_FLING = "overscroll_fling";
    private static final String KEY_PHYSICS_MIN_VEL = "overscroll_physics_min_vel";
    private static final String KEY_PHYSICS_MIN_VAL = "overscroll_physics_min_val";
    private static final String KEY_INPUT_SMOOTH_FACTOR = "overscroll_input_smooth";
    private static final String KEY_ANIMATION_SPEED = "overscroll_anim_speed";
    private static final String KEY_RESISTANCE_EXPONENT = "overscroll_res_exponent";
    private static final String KEY_LERP_MAIN_IDLE = "overscroll_lerp_main_idle";
    private static final String KEY_LERP_MAIN_RUN = "overscroll_lerp_main_run";
    private static final String KEY_DISABLE_ARBITRARY_RENDERING = "overscroll_disable_arbitrary_rendering";
    private static final String KEY_SCALE_MODE = "overscroll_scale_mode";
    private static final String KEY_SCALE_INTENSITY = "overscroll_scale_intensity";
    private static final String KEY_SCALE_LIMIT_MIN = "overscroll_scale_limit_min";
    private static final String KEY_ZOOM_MODE = "overscroll_zoom_mode";
    private static final String KEY_ZOOM_INTENSITY = "overscroll_zoom_intensity";
    private static final String KEY_ZOOM_LIMIT_MIN = "overscroll_zoom_limit_min";
    private static final String KEY_ZOOM_ANCHOR_X = "overscroll_zoom_anchor_x";
    private static final String KEY_ZOOM_ANCHOR_Y = "overscroll_zoom_anchor_y";
    private static final String KEY_H_SCALE_MODE = "overscroll_h_scale_mode";
    private static final String KEY_H_SCALE_INTENSITY = "overscroll_h_scale_intensity";
    private static final String KEY_H_SCALE_LIMIT_MIN = "overscroll_h_scale_limit_min";
    private static final String KEY_SCALE_ANCHOR_Y = "overscroll_scale_anchor_y";
    private static final String KEY_H_SCALE_ANCHOR_X = "overscroll_h_scale_anchor_x";
    private static final String KEY_SCALE_ANCHOR_X_HORIZ = "overscroll_scale_anchor_x_horiz";
    private static final String KEY_H_SCALE_ANCHOR_Y_HORIZ = "overscroll_h_scale_anchor_y_horiz";
    private static final String KEY_ZOOM_ANCHOR_X_HORIZ = "overscroll_zoom_anchor_x_horiz";
    private static final String KEY_ZOOM_ANCHOR_Y_HORIZ = "overscroll_zoom_anchor_y_horiz";
    private static final String KEY_SCALE_INTENSITY_HORIZ = "overscroll_scale_intensity_horiz";
    private static final String KEY_ZOOM_INTENSITY_HORIZ = "overscroll_zoom_intensity_horiz";
    private static final String KEY_H_SCALE_INTENSITY_HORIZ = "overscroll_h_scale_intensity_horiz";
    private static final String KEY_INVERT_ANCHOR = "overscroll_invert_anchor";
    private static final String KEY_RECORD_PATTERNS = "record_patterns_edge_effect";

    // ═══════════════════════════════════════════════════════════════════
    //  Ключи Settings.Global: интеллектуальная нормализация дельт
    //  (Phase 2 — автоматическое подавление усиленных Compose-дельт)
    // ═══════════════════════════════════════════════════════════════════
    //
    //  Принцип: скользящее окно |correctedDelta| → running mean.
    //  Если mean > refDelta × detectMul → дельты усилены (Compose),
    //  применяется плавное домножение на normFactor.
    //  Без привязки к имени пакета — чисто поведенческий детектор.
    //
    //  Все ключи читаются с суффиксом _pine/_xposed через resolveKey().
    //  Для отладки на живую:
    //    adb shell settings put global overscroll_norm_enabled_pine 1
    //    adb shell settings put global overscroll_norm_factor_pine 0.3
    // ═══════════════════════════════════════════════════════════════════

    // Главный выключатель нормализации: 0 = выкл (дефолт), 1 = вкл
    private static final String KEY_NORM_ENABLED = "overscroll_norm_enabled";

    // Эталонная «нормальная» дельта View-приложения (доля от effectiveSize).
    // Определена эмпирически: Settings median ≈ 0.01 (робо-тест).
    // Если средняя дельта экземпляра превышает это × detect_mul → усиление.
    private static final String KEY_NORM_REF_DELTA = "overscroll_norm_ref_delta";

    // Множитель порога обнаружения. Активация: running_mean > ref × это.
    // 3.0 = надёжно ловит Compose (~5× от View), не срабатывает на View.
    // Увеличить если ложные срабатывания, уменьшить если не ловит.
    private static final String KEY_NORM_DETECT_MUL = "overscroll_norm_detect_mul";

    // Фактор нормализации — на сколько домножить усиленную дельту.
    // 0.2 = привести к уровню View (из анализа: ref/compose ≈ 0.195).
    // 0.5 = уменьшить вдвое. 1.0 = не менять (нормализация выключена).
    // Регулируйте на глаз: меньше = слабее bounce, больше = сильнее.
    private static final String KEY_NORM_FACTOR = "overscroll_norm_factor";

    // Размер скользящего окна — сколько последних |delta| анализировать.
    // 8 = быстрое обнаружение (за 4 события), но чувствительно к выбросам.
    // 16 = стабильнее, но медленнее реагирует. Допустимо: 2..64.
    private static final String KEY_NORM_WINDOW = "overscroll_norm_window";

    // Плавность перехода — за сколько событий перейти от 1.0 к factor.
    // 4 = быстрый ramp (почти мгновенно). 12 = плавный. Минимум 1.
    private static final String KEY_NORM_RAMP = "overscroll_norm_ramp";

    // Режим определения caller'а: 0 = только поведение (окно дельт),
    // 1 = стек-трейс + поведение (Compose-экземпляры получают normFactor
    //     мгновенно, без ожидания окна). 2 = только стек-трейс (без окна).
    // По умолчанию 1 — гибрид: стек даёт мгновенный старт, окно подстрахует.
    private static final String KEY_NORM_DETECT_MODE = "overscroll_norm_detect_mode";

    // ═══════════════════════════════════════════════════════════════════

    private static final float FILTER_THRESHOLD = 0.08f;
    private static final float MICRO_DELTA_EPS = 0.00035f;
    private static final float DIRECTION_FLIP_DAMPING = 0.2f;
    private static final float NORMAL_FLIP_DAMPING = 0.65f;
    private static final long SETTINGS_CACHE_TTL_MS = 120L;
    private static Method sSetTranslationX, sSetTranslationY, sSetScaleX, sSetScaleY, sSetPivotX, sSetPivotY;
    private static boolean sReflectionInited = false;

    private static class SettingsCache {
        long updatedAt;
        float pullCoeff;
        float stiffness;
        float damping;
        float fling;
        float minVel;
        float minVal;
        float inputSmooth;
        float animationSpeedPercent;
        float animationSpeedMul;
        float resExponent;
        float lerpMainIdle;
        float lerpMainRun;
        boolean disableArbitraryRendering;
        int scaleMode;
        float scaleIntensity;
        float scaleIntensityHoriz;
        float scaleLimitMin;
        int zoomMode;
        float zoomIntensity;
        float zoomIntensityHoriz;
        float zoomLimitMin;
        float zoomAnchorX;
        float zoomAnchorY;
        float zoomAnchorXHoriz;
        float zoomAnchorYHoriz;
        int hScaleMode;
        float hScaleIntensity;
        float hScaleIntensityHoriz;
        float hScaleLimitMin;
        float scaleAnchorY;
        float hScaleAnchorX;
        float scaleAnchorXHoriz;
        float hScaleAnchorYHoriz;
        boolean invertAnchor;
        boolean recordPatterns;
        // ── Delta normalization ──
        boolean normEnabled;
        float normRefDelta;
        float normDetectMul;
        float normFactor;
        int normWindow;
        int normRamp;
        int normDetectMode;  // 0=behavior, 1=hybrid, 2=stacktrace-only
    }

    public static void initWithClassLoader(ClassLoader classLoader) {
        Class<?> edgeClass = XposedHelpers.findClass("android.widget.EdgeEffect", classLoader);
        hookEdgeEffect(edgeClass);
    }

    private static void hookEdgeEffect(Class<?> edgeClass) {
        
        XposedHelpers.findAndHookConstructor(edgeClass, Context.class, AttributeSet.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                initInstance(param.thisObject, (Context) param.args[0]);
            }
        });

        XposedHelpers.findAndHookConstructor(edgeClass, Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                initInstance(param.thisObject, (Context) param.args[0]);
            }
        });

        
        XposedHelpers.findAndHookMethod(edgeClass, "isFinished", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                Float smoothY = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y);
                SettingsCache cache = getSettingsCache(ctx, thiz, false);
                float minVal = cache.minVal;

                if (mSpring != null) {
                    mSpring.setSpeedMultiplier(cache.animationSpeedMul);
                    
                    // [FIX] Do NOT advance physics in isFinished().
                    // Only draw() or explicit advance methods should drive the spring.
                    // Calling doFrame() here causes erratic behavior and time jumps.
                    // if (mSpring.isRunning()) {
                    //    mSpring.doFrame(System.nanoTime());
                    // }

                    // [FIX] Read-only check — do NOT modify FIELD_SMOOTH_OFFSET_Y here.
                    // draw() is the sole driver of visual smoothing. Modifying smooth here
                    // causes double-advancing per frame in Compose (isInProgress + draw + post-draw).
                    float smooth = (smoothY != null) ? smoothY : 0f;
                    float smoothSc = getF(thiz, FIELD_SMOOTH_SCALE, 1f);
                    float smoothZ = getF(thiz, FIELD_SMOOTH_ZOOM, 1f);
                    float smoothH = getF(thiz, FIELD_SMOOTH_H_SCALE, 1f);

                    boolean physicsDone = !mSpring.isRunning()
                            && Math.abs(mSpring.mValue) < minVal;
                    boolean visualDone = Math.abs(smooth) < minVal
                            && Math.abs(smoothSc - 1f) < 0.001f
                            && Math.abs(smoothZ - 1f) < 0.001f
                            && Math.abs(smoothH - 1f) < 0.001f;
                    boolean fullyFinished = physicsDone && visualDone;

                    if (fullyFinished) {
                        forceFinish(thiz, mSpring);
                    }
                    return fullyFinished;
                }
                return true;
            }
        });

        
        XposedHelpers.findAndHookMethod(edgeClass, "finish", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                SettingsCache cache = getSettingsCache(ctx, thiz, false);

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                if (mSpring != null) {
                    mSpring.cancel();
                    mSpring.mValue = 0;
                    mSpring.mVelocity = 0;
                }

                forceFinish(thiz, mSpring);
                return null;
            }
        });

        
        XC_MethodReplacement onPullHook = new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                float deltaDistance = (float) param.args[0];
                float displacement = (param.args.length > 1) ? (float) param.args[1] : 0.5f;

                SettingsCache cache = getSettingsCache(ctx, thiz, true);
                boolean strictHold = cache.disableArbitraryRendering;

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                if (mSpring == null) return deltaDistance;
                mSpring.setSpeedMultiplier(cache.animationSpeedMul);

                Float cfgScaleObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CFG_SCALE);
                Boolean cfgFilterObj = (Boolean) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CFG_FILTER);
                float cfgScale = (cfgScaleObj != null) ? cfgScaleObj : 1.0f;
                boolean cfgFilter = (cfgFilterObj != null) ? cfgFilterObj : false;

                if (cfgFilter && Math.abs(deltaDistance) > FILTER_THRESHOLD) return deltaDistance;
                float correctedDelta = (Math.abs(cfgScale) > 0.001f) ? deltaDistance / cfgScale : deltaDistance;
                if (Math.abs(correctedDelta) < MICRO_DELTA_EPS) {
                    correctedDelta = 0f;
                }

                // ── Intelligent delta normalization (Phase 2) ──
                // Автоматически уменьшает усиленные Compose-дельты до View-уровня.
                // Работает per-instance через скользящее окно, без проверки пакета.
                if (cache.normEnabled && correctedDelta != 0f) {
                    correctedDelta = applyDeltaNormalization(thiz, cache, correctedDelta);
                }

                float inputSmoothFactor = cache.inputSmooth;
                Float lastDeltaObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_LAST_DELTA);
                float lastDelta = (lastDeltaObj != null) ? lastDeltaObj : 0f;
                Boolean firstTouchObj = (Boolean) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH);
                boolean isFirstTouch = (firstTouchObj != null) ? firstTouchObj : true;

                if (isFirstTouch) {
                    lastDelta = correctedDelta;
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, false);
                }

                boolean directionChanged = (correctedDelta > 0 && lastDelta < 0) || (correctedDelta < 0 && lastDelta > 0);
                float filteredDelta;
                if (directionChanged) {
                    if (strictHold) {
                        if (Math.abs(correctedDelta) < Math.abs(lastDelta) * 1.2f) {
                            filteredDelta = 0f;
                        } else {
                            filteredDelta = correctedDelta * DIRECTION_FLIP_DAMPING + lastDelta * (1.0f - DIRECTION_FLIP_DAMPING);
                        }
                    } else {
                        filteredDelta = correctedDelta * NORMAL_FLIP_DAMPING + lastDelta * (1.0f - NORMAL_FLIP_DAMPING);
                    }
                } else {
                    filteredDelta = correctedDelta * (1.0f - inputSmoothFactor) + lastDelta * inputSmoothFactor;
                }
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, filteredDelta);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, displacement);

                XposedHelpers.setIntField(thiz, "mState", 1);
                mSpring.cancel();

                float currentTranslation = mSpring.mValue;

                float mHeight = XposedHelpers.getFloatField(thiz, "mHeight");
                float mWidth = XposedHelpers.getFloatField(thiz, "mWidth");
                Float screenHObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SCREEN_HEIGHT);
                float screenHeight = (screenHObj != null) ? screenHObj : 2200f;
                float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
                if (effectiveSize < 1f) effectiveSize = screenHeight;

                float rawMove = filteredDelta * effectiveSize;
                float pullCoeff = cache.pullCoeff;
                float resExponent = cache.resExponent;

                boolean isPullingAway = (currentTranslation > 0 && rawMove > 0) || (currentTranslation < 0 && rawMove < 0);
                float change;

                if (pullCoeff >= 1.0f) {
                    change = rawMove * pullCoeff;
                } else {
                    if (isPullingAway) {
                        float ratio = Math.min(Math.abs(currentTranslation) / screenHeight, 1f);
                        float resistance = (float) Math.pow(1.0f - ratio, resExponent);
                        change = rawMove * resistance;
                    } else {
                        change = rawMove;
                    }
                }

                float nextTranslation = currentTranslation + change;
                if ((currentTranslation > 0 && nextTranslation < 0) || (currentTranslation < 0 && nextTranslation > 0)) {
                    nextTranslation = 0f;
                }
                if (strictHold && directionChanged && Math.abs(filteredDelta) <= Math.abs(lastDelta)) {
                    nextTranslation = currentTranslation;
                }

                mSpring.mValue = nextTranslation;
                // [FIX] mDistance must be non-negative — onPullDistance() assumes >= 0
                float distance = Math.abs(nextTranslation) / effectiveSize;
                XposedHelpers.setFloatField(thiz, "mDistance", distance);

                if (distance == 0f) {
                    XposedHelpers.setIntField(thiz, "mState", 0); // STATE_IDLE
                    mSpring.mValue = 0f;
                    mSpring.mVelocity = 0f;
                    // [FIX] Don't call resetState() here — smooth offset may still be
                    // non-zero from the previous draw() frame. If we zero smooth now,
                    // isFinished() returns true and AOSP never calls draw() again,
                    // leaving the RenderNode with stale translation → phantom shifted list.
                    // Let draw() handle the visual decay and reset the RenderNode properly.
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, 0f);
                }

                // --- Delta pattern recording ---
                if (cache.recordPatterns) {
                    long nowNs = SystemClock.elapsedRealtimeNanos();
                    Integer gidObj = (Integer) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_GESTURE_ID);
                    Integer seqObj = (Integer) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_GESTURE_SEQ);
                    Long lastRecNs = (Long) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_RECORD_LAST_NS);
                    int gid = (gidObj != null) ? gidObj : 0;
                    int seq = (seqObj != null) ? seqObj : 0;
                    long prevNs = (lastRecNs != null) ? lastRecNs : 0L;
                    if (isFirstTouch) { gid++; seq = 0; }
                    seq++;
                    long dtUs = (!isFirstTouch && prevNs > 0) ? (nowNs - prevNs) / 1000L : 0L;
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_GESTURE_ID, gid);
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_GESTURE_SEQ, seq);
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_RECORD_LAST_NS, nowNs);
                    recordPattern(thiz, nowNs, ctx.getPackageName(), gid, seq, dtUs,
                            deltaDistance, correctedDelta, filteredDelta,
                            displacement, currentTranslation, effectiveSize);
                }

                return deltaDistance;
            }
        };

        XposedHelpers.findAndHookMethod(edgeClass, "onPull", float.class, float.class, onPullHook);
        XposedHelpers.findAndHookMethod(edgeClass, "onPull", float.class, onPullHook);

        // Hook onPullDistance — AOSP ScrollView/RecyclerView call this directly.
        // The original onPullDistance clamps mDistance >= 0 and delegates to onPull(delta).
        // We must hook it to return consumed delta consistent with our spring model.
        XposedHelpers.findAndHookMethod(edgeClass, "onPullDistance", float.class, float.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                float deltaDistance = (float) param.args[0];
                float displacement = (float) param.args[1];

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                if (mSpring == null) return 0f;

                float mHeight = XposedHelpers.getFloatField(thiz, "mHeight");
                float mWidth = XposedHelpers.getFloatField(thiz, "mWidth");
                float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
                Float screenHObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SCREEN_HEIGHT);
                float screenHeight = (screenHObj != null) ? screenHObj : 2200f;
                if (effectiveSize < 1f) effectiveSize = screenHeight;

                float currentDistance = Math.abs(mSpring.mValue) / effectiveSize;
                float finalDistance = Math.max(0f, deltaDistance + currentDistance);
                float delta = finalDistance - currentDistance;

                if (delta == 0f && currentDistance == 0f) {
                    return 0f;
                }

                // Delegate to our onPull logic
                XposedHelpers.callMethod(thiz, "onPull", delta, displacement);
                return delta;
            }
        });

        // Hook getDistance — must return value consistent with spring model
        XposedHelpers.findAndHookMethod(edgeClass, "getDistance", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                return XposedHelpers.getFloatField(thiz, "mDistance");
            }
        });

        
        XposedHelpers.findAndHookMethod(edgeClass, "onRelease", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                // [FIX] Match AOSP: onRelease() is a no-op unless in STATE_PULL (1).
                // AOSP EdgeEffect.java line 487: if (mState != STATE_PULL && mState != STATE_PULL_DECAY) return;
                // Without this guard, onRelease kills spring animations started by onAbsorb,
                // because Compose/AOSP call onRelease() right after onAbsorb().
                int currentState = XposedHelpers.getIntField(thiz, "mState");
                if (currentState != 1 /* STATE_PULL */) {
                    // Still reset touch tracking for next interaction
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, 0.5f);
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, 0f);
                    XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);
                    return null;
                }

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                SettingsCache cache = getSettingsCache(ctx, thiz, true);
                if (mSpring != null && Math.abs(mSpring.mValue) > 0.5f) {
                    mSpring.setSpeedMultiplier(cache.animationSpeedMul);
                    float stiffness = cache.stiffness;
                    float damping = cache.damping;
                    float minVel = cache.minVel;
                    float minVal = cache.minVal;

                    mSpring.setParams(stiffness, damping, minVel, minVal);
                    mSpring.setTargetValue(0);
                    mSpring.setVelocity(0);
                    mSpring.start();
                    XposedHelpers.setIntField(thiz, "mState", 3);
                } else {
                    // Value <= 0.5 — negligible pull, reset immediately
                    if (mSpring != null) {
                        mSpring.cancel();
                        mSpring.mValue = 0f;
                        mSpring.mVelocity = 0f;
                    }
                    resetState(thiz);
                    XposedHelpers.setIntField(thiz, "mState", 0);
                    XposedHelpers.setFloatField(thiz, "mDistance", 0f);
                }

                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, 0.5f);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, 0f);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);
                return null;
            }
        });

        
        XposedHelpers.findAndHookMethod(edgeClass, "onAbsorb", int.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) {
                    return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                }

                int velocity = (int) param.args[0];
                XposedHelpers.setIntField(thiz, "mState", 3);
                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                SettingsCache cache = getSettingsCache(ctx, thiz, true);

                if (mSpring != null) {
                    mSpring.setSpeedMultiplier(cache.animationSpeedMul);
                    mSpring.cancel();

                    float flingMult = cache.fling;
                    float stiffness = cache.stiffness;
                    float damping = cache.damping;
                    float minVel = cache.minVel;
                    float minVal = cache.minVal;

                    float velocityPx = velocity * flingMult;
                    if (flingMult > 1.0f) stiffness /= flingMult;

                    Float screenHObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SCREEN_HEIGHT);
                    float screenHeight = (screenHObj != null) ? screenHObj : 2200f;
                    float maxVel = screenHeight * 10f;

                    if (Math.abs(velocityPx) > maxVel) velocityPx = Math.signum(velocityPx) * maxVel;

                    mSpring.setParams(stiffness, damping, minVel, minVal);
                    mSpring.setTargetValue(0);
                    mSpring.setVelocity(velocityPx);
                    mSpring.start();
                }
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, 0.5f);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, 0f);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);
                return null;
            }
        });

        
        XposedHelpers.findAndHookMethod(edgeClass, "draw", Canvas.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                EdgeEffect thiz = (EdgeEffect) param.thisObject;
                Canvas canvas = (Canvas) param.args[0];
                Context ctx = (Context) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CONTEXT);

                if (!isBounceEnabled(ctx, thiz)) return XposedBridge.invokeOriginalMethod(param.method, thiz, param.args);
                SettingsCache cache = getSettingsCache(ctx, thiz, false);
                if (!canvas.isHardwareAccelerated()) {
                    forceFinish(thiz, mSpringFrom(thiz));
                    return false;
                }

                SpringDynamics mSpring = (SpringDynamics) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
                if (mSpring == null) {
                    forceFinish(thiz, null);
                    return false;
                }

                mSpring.setSpeedMultiplier(cache.animationSpeedMul);

                if (mSpring.isRunning()) mSpring.doFrame(System.nanoTime());

                Object renderNode = null;
                try {
                    renderNode = XposedHelpers.getObjectField(canvas, "mNode");
                } catch (Throwable t) {
                    forceFinish(thiz, mSpring);
                    return false;
                }
                if (renderNode == null) {
                    forceFinish(thiz, mSpring);
                    return false;
                }

                ensureReflection();

                Matrix matrix = (Matrix) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_MATRIX);
                float[] vecCache = (float[]) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_POINTS);

                canvas.getMatrix(matrix);
                vecCache[0] = 0; vecCache[1] = 1;
                matrix.mapVectors(vecCache);

                float vx = vecCache[0];
                float vy = vecCache[1];

                if (Math.abs(vx) > Math.abs(vy)) {
                    vx = Math.signum(vx);
                    vy = 0f;
                } else {
                    vy = Math.signum(vy);
                    vx = 0f;
                }

                boolean isVertical = (vy != 0);

                float lerpMainIdle = cache.lerpMainIdle;
                float lerpMainRun = cache.lerpMainRun;
                float lerpFactorMain = mSpring.isRunning() ? lerpMainRun : lerpMainIdle;
                lerpFactorMain = Math.min(1.0f, lerpFactorMain * cache.animationSpeedMul);

                float targetOffset = mSpring.mValue;
                Float currentOffsetObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y);
                float currentOffset = (currentOffsetObj != null) ? currentOffsetObj : 0f;
                float newOffset = currentOffset + (targetOffset - currentOffset) * lerpFactorMain;

                float minVal = cache.minVal;
                if (Math.abs(targetOffset - newOffset) < 0.5f) newOffset = targetOffset;
                if (Math.abs(targetOffset) < 0.1f && Math.abs(newOffset) < minVal) newOffset = 0f;

                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y, newOffset);

                Float scrH = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SCREEN_HEIGHT);
                Float scrW = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SCREEN_WIDTH);
                float screenHeight = (scrH != null) ? scrH : 2200f;
                float screenWidth = (scrW != null) ? scrW : 1080f;

                float maxDistance = isVertical ? screenHeight : screenWidth;
                float ratio = (maxDistance > 0) ? Math.min(Math.abs(newOffset) / maxDistance, 1.0f) : 0f;
                boolean isActive = Math.abs(newOffset) > 1.0f;

                boolean zoomActive = cache.zoomMode != 0;
                boolean scaleActive = cache.scaleMode != 0;
                boolean hScaleActive = cache.hScaleMode != 0;

                float targetScaleV = 1f, targetScaleZ = 1f, targetScaleH = 1f;

                if (isActive) {
                        targetScaleV = calcScale(cache.scaleMode,
                            isVertical ? cache.scaleIntensity : cache.scaleIntensityHoriz,
                            cache.scaleLimitMin, ratio);
                        targetScaleZ = calcScale(cache.zoomMode,
                            isVertical ? cache.zoomIntensity : cache.zoomIntensityHoriz,
                            cache.zoomLimitMin, ratio);
                        targetScaleH = calcScale(cache.hScaleMode,
                            isVertical ? cache.hScaleIntensity : cache.hScaleIntensityHoriz,
                            cache.hScaleLimitMin, ratio);
                }

                float newScaleV = lerp(getF(thiz, FIELD_SMOOTH_SCALE, 1f), targetScaleV, lerpFactorMain);
                float newScaleZ = lerp(getF(thiz, FIELD_SMOOTH_ZOOM, 1f), targetScaleZ, lerpFactorMain);
                float newScaleH = lerp(getF(thiz, FIELD_SMOOTH_H_SCALE, 1f), targetScaleH, lerpFactorMain);

                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_SCALE, newScaleV);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_ZOOM, newScaleZ);
                XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_H_SCALE, newScaleH);

                boolean isResting = Math.abs(newOffset) < 0.1f
                        && Math.abs(newScaleV - 1f) < 0.001f
                        && Math.abs(newScaleZ - 1f) < 0.001f
                        && Math.abs(newScaleH - 1f) < 0.001f;
                if (isResting && !mSpring.isRunning()) {
                    float rW = XposedHelpers.getFloatField(thiz, "mWidth");
                    float rH = XposedHelpers.getFloatField(thiz, "mHeight");
                    safeResetRenderNode(renderNode, rW, rH);
                    forceFinish(thiz, mSpring);
                    return false;
                }

                float mHeight = XposedHelpers.getFloatField(thiz, "mHeight");
                float mWidth = XposedHelpers.getFloatField(thiz, "mWidth");

                float canvasW = (float) canvas.getWidth();
                float canvasH = (float) canvas.getHeight();

                float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
                if (effectiveSize < 1f) effectiveSize = screenHeight;
                // [FIX] Use spring physics value for mDistance, not the smooth visual offset.
                // Using smooth would make mDistance > 0 after the spring has settled at 0,
                // causing AOSP to feed scroll deltas via onPullDistance instead of scrolling
                // the list — this is the root cause of the "phantom list" bug.
                XposedHelpers.setFloatField(thiz, "mDistance", Math.abs(mSpring.mValue) / effectiveSize);

                try {
                    if (sSetTranslationX != null) {
                        sSetTranslationX.invoke(renderNode, newOffset * vx);
                        sSetTranslationY.invoke(renderNode, newOffset * vy);

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

                        if (isVertical) {
                            if (zoomActive) {
                                ax = cache.zoomAnchorX;
                                ay = cache.zoomAnchorY;
                            } else if (scaleActive) {
                                ax = 0.5f;
                                ay = cache.scaleAnchorY;
                            } else if (hScaleActive) {
                                ax = cache.hScaleAnchorX;
                                ay = 0.5f;
                            }
                        } else {
                            if (zoomActive) {
                                ax = cache.zoomAnchorXHoriz;
                                ay = cache.zoomAnchorYHoriz;
                            } else if (scaleActive) {
                                ax = cache.scaleAnchorXHoriz;
                                ay = 0.5f;
                            } else if (hScaleActive) {
                                ax = 0.5f;
                                ay = cache.hScaleAnchorYHoriz;
                            }
                        }

                        boolean invertAnchor = cache.invertAnchor;

                        float pivotX, pivotY;

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

                        sSetPivotX.invoke(renderNode, pivotX);
                        sSetPivotY.invoke(renderNode, pivotY);
                        sSetScaleX.invoke(renderNode, finalScaleX);
                        sSetScaleY.invoke(renderNode, finalScaleY);
                    }
                    XposedHelpers.callMethod(renderNode, "stretch", 0f, 0f, mWidth, mHeight);
                } catch (Throwable t) {}

                boolean continueAnim = mSpring.isRunning()
                        || Math.abs(newOffset) >= minVal
                        || Math.abs(newScaleV - 1f) >= 0.001f
                        || Math.abs(newScaleZ - 1f) >= 0.001f
                        || Math.abs(newScaleH - 1f) >= 0.001f;

                if (!continueAnim) {
                    safeResetRenderNode(renderNode, mWidth, mHeight);
                    forceFinish(thiz, mSpring);
                }
                return continueAnim;
            }
        });
    }


    private static boolean matchesWildcard(String pattern, String text) {
        if (pattern.equals("*")) return true;
        if (!pattern.contains("*")) return pattern.equals(text);
        // Split by '*' and match segments in order
        String[] segments = pattern.split("\\*", -1);
        int textIdx = 0;
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isEmpty()) continue;
            int found = text.indexOf(seg, textIdx);
            if (found < 0) return false;
            // First segment must match at start if pattern doesn't start with '*'
            if (i == 0 && !pattern.startsWith("*") && found != 0) return false;
            textIdx = found + seg.length();
        }
        // Last segment must match at end if pattern doesn't end with '*'
        if (!pattern.endsWith("*")) {
            String lastSeg = segments[segments.length - 1];
            if (!lastSeg.isEmpty() && !text.endsWith(lastSeg)) return false;
        }
        return true;
    }

    /**
     * Анализирует стек-трейс при создании EdgeEffect, чтобы определить,
     * кто инициировал создание — Compose или классический View.
     *
     * @return строка вида "compose:ClassName", "view:ClassName" или "unknown"
     */
    private static String detectCaller() {
        // Compose-характерные фрагменты в имени класса
        final String[] COMPOSE_SIGS = {
            "AndroidEdgeEffectOverscrollEffect",
            "OverscrollKt",
            "androidx.compose.foundation",
            "EdgeEffectCompat"                     // иногда Compose идёт через EdgeEffectCompat
        };
        // Классические View-caller'ы
        final String[] VIEW_SIGS = {
            "RecyclerView",
            "ScrollView",
            "AbsListView",
            "NestedScrollView",
            "ListView",
            "HorizontalScrollView",
            "ViewPager"
        };

        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            // Пропускаем первые фреймы (getStackTrace, detectCaller, initInstance, hook, <init>)
            int startIdx = Math.min(4, stack.length);

            // Сначала ищем Compose (приоритет — он ближе к вызову)
            for (int i = startIdx; i < stack.length; i++) {
                String cls = stack[i].getClassName();
                for (String sig : COMPOSE_SIGS) {
                    if (cls.contains(sig)) {
                        // Извлекаем короткое имя класса
                        String shortName = cls.substring(cls.lastIndexOf('.') + 1);
                        return "compose:" + shortName;
                    }
                }
            }
            // Если Compose не найден, ищем View
            for (int i = startIdx; i < stack.length; i++) {
                String cls = stack[i].getClassName();
                for (String sig : VIEW_SIGS) {
                    if (cls.contains(sig)) {
                        String shortName = cls.substring(cls.lastIndexOf('.') + 1);
                        return "view:" + shortName;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Стек-трейс может не быть доступен в некоторых окружениях
        }
        return "unknown";
    }

    private static void initInstance(Object thiz, Context context) {
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CONTEXT, context);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SPRING, new SpringDynamics());
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y, 0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_SCALE, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_ZOOM, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_H_SCALE, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_MATRIX, new Matrix());
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_POINTS, new float[4]);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_LAST_DELTA, 0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, 0.5f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CURRENT_FINGER_X, 0.5f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);

        // Normalization state — per-instance, persists across gestures
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_WINDOW, null);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_IDX, 0);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_COUNT, 0);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_FACTOR, 1.0f);

        // Определяем caller по стек-трейсу (один раз при создании)
        String callerInfo = detectCaller();
        boolean isCompose = callerInfo.startsWith("compose");
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_IS_COMPOSE, isCompose);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CALLER_INFO, callerInfo);

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
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SCREEN_HEIGHT, screenHeight);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SCREEN_WIDTH, screenWidth);

        
        float myScale = 1.0f;
        boolean myFilter = false;
        boolean myIgnore = false;
        try {
            String pkgName = context.getPackageName();
            String configString = getStringSetting(context, KEY_PACKAGES_CONFIG);
            if (!TextUtils.isEmpty(configString) && pkgName != null) {
                String[] apps = configString.split(" ");
                for (String appConfig : apps) {
                    String[] parts = appConfig.split(":");
                    if (parts.length >= 3 && matchesWildcard(parts[0], pkgName)) {
                        myFilter = Integer.parseInt(parts[1]) == 1;
                        myScale = Float.parseFloat(parts[2]);
                        if (parts.length >= 4) myIgnore = parts[3].equals("1");
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CFG_SCALE, myScale);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CFG_FILTER, myFilter);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CFG_IGNORE, myIgnore);
    }


    private static void ensureReset(Object thiz) { resetState(thiz); }

    private static SpringDynamics mSpringFrom(Object thiz) {
        Object spring = XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SPRING);
        return (spring instanceof SpringDynamics) ? (SpringDynamics) spring : null;
    }

    private static void forceFinish(Object thiz, SpringDynamics spring) {
        if (spring != null) {
            spring.cancel();
            spring.mValue = 0f;
            spring.mVelocity = 0f;
        }
        ensureReset(thiz);
        XposedHelpers.setIntField(thiz, "mState", 0);
        XposedHelpers.setFloatField(thiz, "mDistance", 0f);
    }

    private static void resetState(Object thiz) {
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_OFFSET_Y, 0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_SCALE, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_ZOOM, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SMOOTH_H_SCALE, 1.0f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_TARGET_FINGER_X, 0.5f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_CURRENT_FINGER_X, 0.5f);
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_FIRST_TOUCH, true);
    }

    /**
     * Logs one onPull event via logcat with tag RECORD_TAG.
     * Active only when global setting record_patterns_edge_effect = 1.
     * Format: elapsed_ns\tpkg\tgesture_id\tseq\tdt_us\traw\tcorrected\tfiltered\tdisp\tspring\tsize\tcaller
     * Use `adb logcat -s EdgePatternRec:D` to capture on PC.
     */
    private static void recordPattern(Object thiz, long elapsedNs, String pkg, int gestureId, int seq,
                                       long dtUs, float raw, float corrected, float filtered,
                                       float disp, float springVal, float effSize) {
        String callerInfo = "?";
        try {
            Object ci = XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CALLER_INFO);
            if (ci != null) callerInfo = (String) ci;
        } catch (Throwable ignored) {}
        android.util.Log.d(RECORD_TAG, elapsedNs + "\t" + pkg + "\t" + gestureId + "\t" + seq + "\t" + dtUs
                + "\t" + raw + "\t" + corrected + "\t" + filtered
                + "\t" + disp + "\t" + springVal + "\t" + effSize + "\t" + callerInfo);
    }

    /**
     * Intelligent delta normalization (Phase 2).
     *
     * Tracks a sliding window of |correctedDelta| for each EdgeEffect instance.
     * When the running mean exceeds refDelta × detectMul, the instance is
     * identified as "amplified" (e.g. Compose) and deltas are smoothly scaled
     * down toward normFactor to match normal View-level deltas.
     *
     * The transition ramps over normRamp events to avoid visual jumps.
     * The window persists across gestures — once an instance is identified
     * as amplified, normalization stays active (with ramp-back if pattern changes).
     *
     * normDetectMode:
     *   0 = behavior-only  — только скользящее окно, стек-трейс игнорируется
     *   1 = hybrid (default) — Compose-инстансы (по стеку) сразу получают normFactor
     *       без ожидания прогрева окна; окно продолжает работать и может отменить
     *       если дельты окажутся нормальными
     *   2 = stacktrace-only — Compose → всегда normFactor, View/unknown → 1.0,
     *       окно не используется
     */
    private static float applyDeltaNormalization(Object thiz, SettingsCache cache, float correctedDelta) {
        float absDelta = Math.abs(correctedDelta);

        // Читаем флаг стек-трейса (установлен один раз в initInstance)
        Boolean isComposeObj = (Boolean) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_IS_COMPOSE);
        boolean isCompose = (isComposeObj != null) && isComposeObj;
        int detectMode = cache.normDetectMode;

        // ── Mode 2: stacktrace-only — мгновенное решение, без окна ──
        if (detectMode == 2) {
            float factor = isCompose ? cache.normFactor : 1.0f;
            XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_FACTOR, factor);
            return correctedDelta * factor;
        }

        // ── Mode 0 и 1 используют скользящее окно ──

        // Get or recreate circular buffer (handles dynamic window size changes)
        float[] window = (float[]) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_NORM_WINDOW);
        int windowSize = cache.normWindow;
        if (window == null || window.length != windowSize) {
            window = new float[windowSize];
            XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_WINDOW, window);
            XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_IDX, 0);
            XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_COUNT, 0);
        }

        Integer idxObj = (Integer) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_NORM_IDX);
        Integer countObj = (Integer) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_NORM_COUNT);
        Float factorObj = (Float) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_NORM_FACTOR);
        int idx = (idxObj != null) ? idxObj : 0;
        int count = (countObj != null) ? countObj : 0;
        float currentFactor = (factorObj != null) ? factorObj : 1.0f;

        // Only real deltas go into the window — micro-deltas (noise) are excluded
        if (absDelta > MICRO_DELTA_EPS) {
            window[idx] = absDelta;
            idx = (idx + 1) % windowSize;
            if (count < windowSize) count++;
            XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_IDX, idx);
            XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_COUNT, count);
        }

        // ── Mode 1 (hybrid): Compose-инстансы нормализуются мгновенно,
        //    пока окно ещё не набрало достаточно данных ──
        int minSamples = Math.max(windowSize / 2, 2);
        boolean windowReady = (count >= minSamples);

        if (detectMode == 1 && isCompose && !windowReady) {
            // Стек-трейс определил Compose — применяем normFactor сразу,
            // не дожидаясь прогрева окна. Ramp здесь тоже нужен.
            float rampStep = Math.abs(1.0f - cache.normFactor) / Math.max(cache.normRamp, 1);
            float targetFactor = cache.normFactor;
            if (currentFactor > targetFactor) {
                currentFactor = Math.max(currentFactor - rampStep, targetFactor);
            } else if (currentFactor < targetFactor) {
                currentFactor = Math.min(currentFactor + rampStep, targetFactor);
            }
            XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_FACTOR, currentFactor);
            return correctedDelta * currentFactor;
        }

        // ── Окно набрало достаточно данных — решение по поведению ──
        if (windowReady) {
            // Running mean of the sliding window
            float sum = 0;
            for (int i = 0; i < count; i++) sum += window[i];
            float runningMean = sum / count;

            float threshold = cache.normRefDelta * cache.normDetectMul;
            float targetFactor;

            if (runningMean > threshold) {
                // ▼ Deltas amplified — ramp toward normFactor
                targetFactor = cache.normFactor;
            } else {
                // ▲ Deltas normal — ramp back to 1.0 (no change)
                targetFactor = 1.0f;
            }

            // Smooth ramp: step size = |1 - factor| / ramp events
            float rampStep = Math.abs(1.0f - cache.normFactor) / Math.max(cache.normRamp, 1);
            if (currentFactor > targetFactor) {
                currentFactor = Math.max(currentFactor - rampStep, targetFactor);
            } else if (currentFactor < targetFactor) {
                currentFactor = Math.min(currentFactor + rampStep, targetFactor);
            }
        }

        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_NORM_FACTOR, currentFactor);
        return correctedDelta * currentFactor;
    }

    private static void ensureReflection() {
        if (sReflectionInited) return;
        try {
            Class<?> rnClass = null;
            try { rnClass = Class.forName("android.graphics.RenderNode"); } catch (ClassNotFoundException e) {
                try { rnClass = Class.forName("android.view.RenderNode"); } catch (ClassNotFoundException ignored) {}
            }
            if (rnClass != null) {
                sSetTranslationX = rnClass.getMethod("setTranslationX", float.class);
                sSetTranslationY = rnClass.getMethod("setTranslationY", float.class);
                sSetScaleX = rnClass.getMethod("setScaleX", float.class);
                sSetScaleY = rnClass.getMethod("setScaleY", float.class);
                sSetPivotX = rnClass.getMethod("setPivotX", float.class);
                sSetPivotY = rnClass.getMethod("setPivotY", float.class);
            }
            sReflectionInited = true;
        } catch (Exception ignored) {}
    }

    private static void safeResetRenderNode(Object renderNode, float viewWidth, float viewHeight) {
        if (renderNode == null) return;
        ensureReflection();
        try {
            if (sSetTranslationX != null) {
                sSetTranslationX.invoke(renderNode, 0f);
                sSetTranslationY.invoke(renderNode, 0f);
                sSetScaleX.invoke(renderNode, 1f);
                sSetScaleY.invoke(renderNode, 1f);
            }
            // [FIX] Pass actual W/H to stretch() — stretch(0,0,0,0) may not properly clear
            float sw = viewWidth > 0 ? viewWidth : 1f;
            float sh = viewHeight > 0 ? viewHeight : 1f;
            XposedHelpers.callMethod(renderNode, "stretch", 0f, 0f, sw, sh);
        } catch (Throwable ignored) {}
    }

    private static SettingsCache getSettingsCache(Context ctx, Object thiz, boolean force) {
        Object cacheObj = XposedHelpers.getAdditionalInstanceField(thiz, FIELD_SETTINGS_CACHE);
        SettingsCache cache = (cacheObj instanceof SettingsCache) ? (SettingsCache) cacheObj : null;
        long now = SystemClock.uptimeMillis();

        if (cache == null) {
            cache = new SettingsCache();
            force = true;
        }

        if (!force && (now - cache.updatedAt) < SETTINGS_CACHE_TTL_MS) {
            return cache;
        }

        cache.pullCoeff = getFloatSetting(ctx, KEY_PULL_COEFF, 1.5141f);
        cache.stiffness = getFloatSetting(ctx, KEY_STIFFNESS, 148.6191f);
        cache.damping = getFloatSetting(ctx, KEY_DAMPING, 0.9976f);
        cache.fling = getFloatSetting(ctx, KEY_FLING, 1.3679f);
        cache.minVel = getFloatSetting(ctx, KEY_PHYSICS_MIN_VEL, 8.0f);
        cache.minVal = getFloatSetting(ctx, KEY_PHYSICS_MIN_VAL, 0.6f);
        cache.inputSmooth = getFloatSetting(ctx, KEY_INPUT_SMOOTH_FACTOR, 0.5f);
        cache.animationSpeedPercent = getFloatSetting(ctx, KEY_ANIMATION_SPEED, 168.5232f);
        if (cache.animationSpeedPercent < 1.0f) cache.animationSpeedPercent = 1.0f;
        if (cache.animationSpeedPercent > 300.0f) cache.animationSpeedPercent = 300.0f;
        cache.animationSpeedMul = cache.animationSpeedPercent / 100.0f;
        cache.resExponent = getFloatSetting(ctx, KEY_RESISTANCE_EXPONENT, 4.0f);
        cache.lerpMainIdle = getFloatSetting(ctx, KEY_LERP_MAIN_IDLE, 0.4f);
        cache.lerpMainRun = getFloatSetting(ctx, KEY_LERP_MAIN_RUN, 0.6999f);
        cache.disableArbitraryRendering = getIntSetting(ctx, KEY_DISABLE_ARBITRARY_RENDERING, 0) == 1;

        cache.scaleMode = getIntSetting(ctx, KEY_SCALE_MODE, 0);
        cache.scaleIntensity = getFloatSetting(ctx, KEY_SCALE_INTENSITY, 0.31f);
        cache.scaleIntensityHoriz = getFloatSetting(ctx, KEY_SCALE_INTENSITY_HORIZ, 0.3786f);
        cache.scaleLimitMin = getFloatSetting(ctx, KEY_SCALE_LIMIT_MIN, 0.1f);

        cache.zoomMode = getIntSetting(ctx, KEY_ZOOM_MODE, 0);
        cache.zoomIntensity = getFloatSetting(ctx, KEY_ZOOM_INTENSITY, 0.2f);
        cache.zoomIntensityHoriz = getFloatSetting(ctx, KEY_ZOOM_INTENSITY_HORIZ, 0.2f);
        cache.zoomLimitMin = getFloatSetting(ctx, KEY_ZOOM_LIMIT_MIN, 0.1f);
        cache.zoomAnchorX = getFloatSetting(ctx, KEY_ZOOM_ANCHOR_X, 0.5f);
        cache.zoomAnchorY = getFloatSetting(ctx, KEY_ZOOM_ANCHOR_Y, 0.5f);
        cache.zoomAnchorXHoriz = getFloatSetting(ctx, KEY_ZOOM_ANCHOR_X_HORIZ, 0.5f);
        cache.zoomAnchorYHoriz = getFloatSetting(ctx, KEY_ZOOM_ANCHOR_Y_HORIZ, 0.5f);

        cache.hScaleMode = getIntSetting(ctx, KEY_H_SCALE_MODE, 0);
        cache.hScaleIntensity = getFloatSetting(ctx, KEY_H_SCALE_INTENSITY, 0.2f);
        cache.hScaleIntensityHoriz = getFloatSetting(ctx, KEY_H_SCALE_INTENSITY_HORIZ, 0.0f);
        cache.hScaleLimitMin = getFloatSetting(ctx, KEY_H_SCALE_LIMIT_MIN, 0.1f);

        cache.scaleAnchorY = getFloatSetting(ctx, KEY_SCALE_ANCHOR_Y, 0.5f);
        cache.hScaleAnchorX = getFloatSetting(ctx, KEY_H_SCALE_ANCHOR_X, 0.5f);
        cache.scaleAnchorXHoriz = getFloatSetting(ctx, KEY_SCALE_ANCHOR_X_HORIZ, 0.5f);
        cache.hScaleAnchorYHoriz = getFloatSetting(ctx, KEY_H_SCALE_ANCHOR_Y_HORIZ, 0.5f);
        cache.invertAnchor = getIntSetting(ctx, KEY_INVERT_ANCHOR, 1) == 1;
        cache.recordPatterns = getIntSetting(ctx, KEY_RECORD_PATTERNS, 0) == 1;

        // ── Delta normalization keys ──
        // normEnabled and normDetectMode are written as Int from UI (Switch / RadioGroup)
        // normWindow and normRamp are written as Float from UI (Slider), so read with getFloatSetting and cast
        cache.normEnabled = getIntSetting(ctx, KEY_NORM_ENABLED, 1) == 1;
        cache.normRefDelta = getFloatSetting(ctx, KEY_NORM_REF_DELTA, 9.9999f);
        cache.normDetectMul = getFloatSetting(ctx, KEY_NORM_DETECT_MUL, 0f);
        cache.normFactor = getFloatSetting(ctx, KEY_NORM_FACTOR, 0.33f);
        cache.normWindow = (int) getFloatSetting(ctx, KEY_NORM_WINDOW, 2f);
        cache.normRamp = (int) getFloatSetting(ctx, KEY_NORM_RAMP, 1f);
        cache.normDetectMode = getIntSetting(ctx, KEY_NORM_DETECT_MODE, 1);
        if (cache.normWindow < 2) cache.normWindow = 2;
        if (cache.normWindow > 64) cache.normWindow = 64;
        if (cache.normRamp < 1) cache.normRamp = 1;
        if (cache.normDetectMode < 0 || cache.normDetectMode > 2) cache.normDetectMode = 1;

        cache.updatedAt = now;
        XposedHelpers.setAdditionalInstanceField(thiz, FIELD_SETTINGS_CACHE, cache);
        return cache;
    }

    private static float calcScale(int mode, float intensity, float limit, float ratio) {
        if (mode == 0 || intensity <= 0) return 1.0f;
        if (mode == 1) return Math.max(1.0f - (ratio * intensity), limit);
        if (mode == 2) return 1.0f + (ratio * intensity);
        return 1.0f;
    }

    private static float lerp(float start, float end, float factor) {
        return start + (end - start) * factor;
    }

    private static float getF(Object obj, String field, float def) {
        Float val = (Float) XposedHelpers.getAdditionalInstanceField(obj, field);
        return (val != null) ? val : def;
    }

    private static boolean isBounceEnabled(Context ctx, Object thiz) {
        if (ctx == null) return true;
        try {
            if (getIntSetting(ctx, KEY_ENABLED, 1) != 1) return false;
            if (thiz != null) {
                Boolean ignored = (Boolean) XposedHelpers.getAdditionalInstanceField(thiz, FIELD_CFG_IGNORE);
                if (ignored != null && ignored) return false;
            }
            return true;
        } catch (Exception ignored) { return true; }
    }

    private static float getFloatSetting(Context ctx, String key, float def) {
        if (ctx == null) return def;
        try {
            String resolved = resolveKey(key);
            return Settings.Global.getFloat(ctx.getContentResolver(), resolved, def);
        } catch (Exception ignored) { return def; }
    }

    private static int getIntSetting(Context ctx, String key, int def) {
        if (ctx == null) return def;
        try {
            String resolved = resolveKey(key);
            return Settings.Global.getInt(ctx.getContentResolver(), resolved, def);
        } catch (Exception ignored) { return def; }
    }

    private static String getStringSetting(Context ctx, String key) {
        if (ctx == null) return null;
        try {
            String resolved = resolveKey(key);
            return Settings.Global.getString(ctx.getContentResolver(), resolved);
        } catch (Exception ignored) { return null; }
    }

    public static class SpringDynamics {
        private float mStiffness = 450.0f;
        private float mDampingRatio = 0.7f;
        private float mMinVel = 1.0f;
        private float mMinVal = 0.5f;
        private float mSpeedMultiplier = 1.0f;

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

        public void setSpeedMultiplier(float speedMultiplier) {
            if (speedMultiplier < 0.01f) speedMultiplier = 0.01f;
            mSpeedMultiplier = speedMultiplier;
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
            float dt = (deltaTimeNanos / 1_000_000_000.0f) * mSpeedMultiplier;

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
}
