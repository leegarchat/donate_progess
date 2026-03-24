/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.widget;

import android.animation.ValueAnimator;
import android.annotation.ColorInt;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.compat.Compatibility;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledSince;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This class performs the graphical effect used at the edges of scrollable widgets
 * when the user scrolls beyond the content bounds in 2D space.
 *
 * <p>EdgeEffect is stateful. Custom widgets using EdgeEffect should create an
 * instance for each edge that should show the effect, feed it input data using
 * the methods {@link #onAbsorb(int)}, {@link #onPull(float)}, and {@link #onRelease()},
 * and draw the effect using {@link #draw(Canvas)} in the widget's overridden
 * {@link android.view.View#draw(Canvas)} method. If {@link #isFinished()} returns
 * false after drawing, the edge effect's animation is not yet complete and the widget
 * should schedule another drawing pass to continue the animation.</p>
 *
 * <p>When drawing, widgets should draw their main content and child views first,
 * usually by invoking <code>super.draw(canvas)</code> from an overridden <code>draw</code>
 * method. (This will invoke onDraw and dispatch drawing to child views as needed.)
 * The edge effect may then be drawn on top of the view's content using the
 * {@link #draw(Canvas)} method.</p>
 */
public class EdgeEffect {

    // ═══════════════════════════════════════════════════════════════════
    //  Original AOSP constants
    // ═══════════════════════════════════════════════════════════════════

    /**
     * This sets the edge effect to use stretch instead of glow.
     *
     * @hide
     */
    @ChangeId
    @EnabledSince(targetSdkVersion = Build.VERSION_CODES.BASE)
    public static final long USE_STRETCH_EDGE_EFFECT_BY_DEFAULT = 171228096L;

    /**
     * The default blend mode used by {@link EdgeEffect}.
     */
    public static final BlendMode DEFAULT_BLEND_MODE = BlendMode.SRC_ATOP;

    /**
     * Completely disable edge effect
     */
    private static final int TYPE_NONE = -1;

    /**
     * Use a color edge glow for the edge effect.
     */
    private static final int TYPE_GLOW = 0;

    /**
     * Use a stretch for the edge effect.
     */
    private static final int TYPE_STRETCH = 1;

    /**
     * The velocity threshold before the spring animation is considered settled.
     * The idea here is that velocity should be less than 0.1 pixel per second.
     */
    private static final double VELOCITY_THRESHOLD = 0.01;

    /**
     * The speed at which we should start linearly interpolating to the destination.
     * When using a spring, as it gets closer to the destination, the speed drops off exponentially.
     * Instead of landing very slowly, a better experience is achieved if the final
     * destination is arrived at quicker.
     */
    private static final float LINEAR_VELOCITY_TAKE_OVER = 200f;

    /**
     * The value threshold before the spring animation is considered close enough to
     * the destination to be settled. This should be around 0.01 pixel.
     */
    private static final double VALUE_THRESHOLD = 0.001;

    /**
     * The maximum distance at which we should start linearly interpolating to the destination.
     * When using a spring, as it gets closer to the destination, the speed drops off exponentially.
     * Instead of landing very slowly, a better experience is achieved if the final
     * destination is arrived at quicker.
     */
    private static final double LINEAR_DISTANCE_TAKE_OVER = 8.0;

    /**
     * The natural frequency of the stretch spring.
     */
    private static final double NATURAL_FREQUENCY = 24.657;

    /**
     * The damping ratio of the stretch spring.
     */
    private static final double DAMPING_RATIO = 0.98;

    /**
     * The variation of the velocity for the stretch effect when it meets the bound.
     * if value is > 1, it will accentuate the absorption of the movement.
     */
    private static final float ON_ABSORB_VELOCITY_ADJUSTMENT = 13f;

    /** @hide */
    @IntDef({TYPE_NONE, TYPE_GLOW, TYPE_STRETCH})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EdgeEffectType {
    }

    private static final float LINEAR_STRETCH_INTENSITY = 0.016f;

    private static final float EXP_STRETCH_INTENSITY = 0.016f;

    private static final float SCROLL_DIST_AFFECTED_BY_EXP_STRETCH = 0.33f;

    @SuppressWarnings("UnusedDeclaration")
    private static final String TAG = "EdgeEffect";

    // Time it will take the effect to fully recede in ms
    private static final int RECEDE_TIME = 600;

    // Time it will take before a pulled glow begins receding in ms
    private static final int PULL_TIME = 167;

    // Time it will take in ms for a pulled glow to decay to partial strength before release
    private static final int PULL_DECAY_TIME = 2000;

    private static final float MAX_ALPHA = 0.15f;
    private static final float GLOW_ALPHA_START = .09f;

    private static final float MAX_GLOW_SCALE = 2.f;

    private static final float PULL_GLOW_BEGIN = 0.f;

    // Minimum velocity that will be absorbed
    private static final int MIN_VELOCITY = 100;
    // Maximum velocity, clamps at this value
    private static final int MAX_VELOCITY = 10000;

    private static final float EPSILON = 0.001f;

    private static final double ANGLE = Math.PI / 6;
    private static final float SIN = (float) Math.sin(ANGLE);
    private static final float COS = (float) Math.cos(ANGLE);
    private static final float RADIUS_FACTOR = 0.6f;

    // ═══════════════════════════════════════════════════════════════════
    //  Original AOSP fields
    // ═══════════════════════════════════════════════════════════════════

    private float mGlowAlpha;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.R, trackingBug = 170729553)
    private float mGlowScaleY;
    private float mDistance;
    private float mVelocity; // only for stretch animations

    private float mGlowAlphaStart;
    private float mGlowAlphaFinish;
    private float mGlowScaleYStart;
    private float mGlowScaleYFinish;

    private long mStartTime;
    private float mDuration;

    private final Interpolator mInterpolator = new DecelerateInterpolator();

    private static final int STATE_IDLE = 0;
    private static final int STATE_PULL = 1;
    private static final int STATE_ABSORB = 2;
    private static final int STATE_RECEDE = 3;
    private static final int STATE_PULL_DECAY = 4;

    private static final float PULL_DISTANCE_ALPHA_GLOW_FACTOR = 0.8f;

    private static final int VELOCITY_GLOW_FACTOR = 6;

    private int mState = STATE_IDLE;

    private float mPullDistance;

    private final Rect mBounds = new Rect();
    private float mWidth;
    private float mHeight;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P, trackingBug = 123769450)
    private final Paint mPaint = new Paint();
    private float mRadius;
    private float mBaseGlowScale;
    private float mDisplacement = 0.5f;
    private float mTargetDisplacement = 0.5f;

    /**
     * Current edge effect type, consumers should always query
     * {@link #getCurrentEdgeEffectBehavior()} instead of this parameter
     * directly in case animations have been disabled (ex. for accessibility reasons)
     */
    private @EdgeEffectType int mEdgeEffectType = TYPE_GLOW;
    private Matrix mTmpMatrix = null;
    private float[] mTmpPoints = null;

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: static configuration
    // ═══════════════════════════════════════════════════════════════════

    private static final String TAG_CUSTOM = "PixelPartsOverscroll";
    private static String sKeySuffix = "_pine";

    /**
     * @hide
     */
    public static void configure(boolean useGlobal, @NonNull String suffix) {
        if (!useGlobal) {
            Log.w(TAG_CUSTOM, "Settings.Secure is no longer supported. Forcing Settings.Global.");
        }
        sKeySuffix = suffix;
    }

    private static String resolveKey(String key) {
        String base = key.replaceAll("_(xposed|pine|native)$", "");
        return base + sKeySuffix;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: Settings keys
    // ═══════════════════════════════════════════════════════════════════

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

    // Главный выключатель нормализации: 0 = выкл (дефолт), 1 = вкл
    private static final String KEY_NORM_ENABLED = "overscroll_norm_enabled";

    // Эталонная «нормальная» дельта View-приложения (доля от effectiveSize).
    private static final String KEY_NORM_REF_DELTA = "overscroll_norm_ref_delta";

    // Множитель порога обнаружения. Активация: running_mean > ref × это.
    private static final String KEY_NORM_DETECT_MUL = "overscroll_norm_detect_mul";

    // Фактор нормализации — на сколько домножить усиленную дельту.
    private static final String KEY_NORM_FACTOR = "overscroll_norm_factor";

    // Размер скользящего окна — сколько последних |delta| анализировать.
    private static final String KEY_NORM_WINDOW = "overscroll_norm_window";

    // Плавность перехода — за сколько событий перейти от 1.0 к factor.
    private static final String KEY_NORM_RAMP = "overscroll_norm_ramp";

    // Режим определения caller'а: 0 = только поведение (окно дельт),
    // 1 = стек-трейс + поведение, 2 = только стек-трейс.
    private static final String KEY_NORM_DETECT_MODE = "overscroll_norm_detect_mode";

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: constants
    // ═══════════════════════════════════════════════════════════════════

    private static final float FILTER_THRESHOLD = 0.08f;
    private static final float MICRO_DELTA_EPS = 0.00035f;
    private static final float DIRECTION_FLIP_DAMPING = 0.2f;
    private static final float NORMAL_FLIP_DAMPING = 0.65f;
    private static final long SETTINGS_CACHE_TTL_MS = 120L;
    private static final String RECORD_TAG = "EdgePatternRec";

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: per-instance fields
    //  (were XposedHelpers.additionalInstanceField in the hook)
    // ═══════════════════════════════════════════════════════════════════

    private Context mCustomContext;
    private SpringDynamics mCustomSpring;
    private float mCustomSmoothOffsetY;
    private float mCustomSmoothScale = 1.0f;
    private float mCustomSmoothZoom = 1.0f;
    private float mCustomSmoothHScale = 1.0f;
    private Matrix mCustomMatrix;
    private float[] mCustomPoints;
    private float mCustomLastDelta;
    private float mCustomTargetFingerX = 0.5f;
    private float mCustomCurrentFingerX = 0.5f;
    private float mCustomScreenHeight = 2200f;
    private float mCustomScreenWidth = 1080f;
    private boolean mCustomFirstTouch = true;
    private SettingsCache mCustomSettingsCache;
    private float mCfgScale = 1.0f;
    private boolean mCfgIgnore = false;
    private boolean mCfgFilter = false;
    private int mCustomGestureId;
    private int mCustomGestureSeq;
    private long mCustomRecordLastNs;

    // Per-instance поля для интеллектуальной нормализации дельт
    private float[] mNormDeltaWindow;
    private int mNormWindowIdx;
    private int mNormWindowCount;
    private float mNormCurrentFactor = 1.0f;
    // true если конструктор вызван из Compose (определено по стек-трейсу)
    private boolean mIsComposeCaller;
    private String mCallerInfo;

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: SettingsCache
    // ═══════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════
    //  Constructors
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Construct a new EdgeEffect with a theme appropriate for the provided context.
     * @param context Context used to provide theming and resource information for the EdgeEffect
     */
    public EdgeEffect(Context context) {
        this(context, null);
    }

    /**
     * Construct a new EdgeEffect with a theme appropriate for the provided context.
     * @param context Context used to provide theming and resource information for the EdgeEffect
     * @param attrs The attributes of the XML tag that is inflating the view
     */
    public EdgeEffect(@NonNull Context context, @Nullable AttributeSet attrs) {
        // ── Original AOSP constructor logic ──
        final TypedArray a = context.obtainStyledAttributes(
                attrs, com.android.internal.R.styleable.EdgeEffect);
        final int themeColor = a.getColor(
                com.android.internal.R.styleable.EdgeEffect_colorEdgeEffect, 0xff666666);
        mEdgeEffectType = Compatibility.isChangeEnabled(USE_STRETCH_EDGE_EFFECT_BY_DEFAULT)
                ? TYPE_STRETCH : TYPE_GLOW;
        a.recycle();

        mPaint.setAntiAlias(true);
        mPaint.setColor((themeColor & 0xffffff) | 0x33000000);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setBlendMode(DEFAULT_BLEND_MODE);

        // ── Custom bounce initialization ──
        initCustomInstance(context);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Original AOSP helpers
    // ═══════════════════════════════════════════════════════════════════

    @EdgeEffectType
    private int getCurrentEdgeEffectBehavior() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            return TYPE_NONE;
        } else {
            return mEdgeEffectType;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Set the size of this edge effect in pixels.
     *
     * @param width Effect width in pixels
     * @param height Effect height in pixels
     */
    public void setSize(int width, int height) {
        final float r = width * RADIUS_FACTOR / SIN;
        final float y = COS * r;
        final float h = r - y;
        final float or = height * RADIUS_FACTOR / SIN;
        final float oy = COS * or;
        final float oh = or - oy;

        mRadius = r;
        mBaseGlowScale = h > 0 ? Math.min(oh / h, 1.f) : 1.f;

        mBounds.set(mBounds.left, mBounds.top, width, (int) Math.min(height, h));

        mWidth = width;
        mHeight = height;
    }

    /**
     * Reports if this EdgeEffect's animation is finished. If this method returns false
     * after a call to {@link #draw(Canvas)} the host widget should schedule another
     * drawing pass to continue the animation.
     *
     * @return true if animation is finished, false if drawing should continue on the next frame.
     */
    public boolean isFinished() {
        if (!isBounceEnabled()) {
            // ── Original AOSP logic ──
            return mState == STATE_IDLE;
        }

        // ── Custom bounce logic ──
        SettingsCache cache = getSettingsCache(mCustomContext, false);
        float minVal = cache.minVal;

        if (mCustomSpring != null) {
            mCustomSpring.setSpeedMultiplier(cache.animationSpeedMul);

            float smooth = mCustomSmoothOffsetY;
            float smoothSc = mCustomSmoothScale;
            float smoothZ = mCustomSmoothZoom;
            float smoothH = mCustomSmoothHScale;

            boolean physicsDone = !mCustomSpring.isRunning()
                    && Math.abs(mCustomSpring.mValue) < minVal;
            boolean visualDone = Math.abs(smooth) < minVal
                    && Math.abs(smoothSc - 1f) < 0.001f
                    && Math.abs(smoothZ - 1f) < 0.001f
                    && Math.abs(smoothH - 1f) < 0.001f;
            boolean fullyFinished = physicsDone && visualDone;

            if (fullyFinished) {
                forceFinish(mCustomSpring);
            }
            return fullyFinished;
        }
        return true;
    }

    /**
     * Immediately finish the current animation.
     * After this call {@link #isFinished()} will return true.
     */
    public void finish() {
        if (!isBounceEnabled()) {
            // ── Original AOSP logic ──
            mState = STATE_IDLE;
            mDistance = 0;
            mVelocity = 0;
            return;
        }

        // ── Custom bounce logic ──
        if (mCustomSpring != null) {
            mCustomSpring.cancel();
            mCustomSpring.mValue = 0;
            mCustomSpring.mVelocity = 0;
        }

        forceFinish(mCustomSpring);
    }

    /**
     * A view should call this when content is pulled away from an edge by the user.
     * This will update the state of the current visual effect and its associated animation.
     * The host view should always {@link android.view.View#invalidate()} after this
     * and draw the results accordingly.
     *
     * <p>Views using EdgeEffect should favor {@link #onPull(float, float)} when the displacement
     * of the pull point is known.</p>
     *
     * @param deltaDistance Change in distance since the last call. Values may be 0 (no change) to
     *                      1.f (full length of the view) or negative values to express change
     *                      back toward the edge reached to initiate the effect.
     */
    public void onPull(float deltaDistance) {
        onPull(deltaDistance, 0.5f);
    }

    /**
     * A view should call this when content is pulled away from an edge by the user.
     * This will update the state of the current visual effect and its associated animation.
     * The host view should always {@link android.view.View#invalidate()} after this
     * and draw the results accordingly.
     *
     * @param deltaDistance Change in distance since the last call. Values may be 0 (no change) to
     *                      1.f (full length of the view) or negative values to express change
     *                      back toward the edge reached to initiate the effect.
     * @param displacement The displacement from the starting side of the effect of the point
     *                     initiating the pull. In the case of touch this is the finger position.
     *                     Values may be from 0-1.
     */
    public void onPull(float deltaDistance, float displacement) {
        if (!isBounceEnabled()) {
            // ── Original AOSP logic ──
            onPullOriginal(deltaDistance, displacement);
            return;
        }

        // ── Custom bounce logic ──
        SettingsCache cache = getSettingsCache(mCustomContext, true);
        boolean strictHold = cache.disableArbitraryRendering;

        if (mCustomSpring == null) return;
        mCustomSpring.setSpeedMultiplier(cache.animationSpeedMul);

        float cfgScale = mCfgScale;
        boolean cfgFilter = mCfgFilter;

        if (cfgFilter && Math.abs(deltaDistance) > FILTER_THRESHOLD) return;
        float correctedDelta = (Math.abs(cfgScale) > 0.001f) ? deltaDistance / cfgScale : deltaDistance;
        if (Math.abs(correctedDelta) < MICRO_DELTA_EPS) {
            correctedDelta = 0f;
        }

        // ── Intelligent delta normalization (Phase 2) ──
        if (cache.normEnabled && correctedDelta != 0f) {
            correctedDelta = applyDeltaNormalization(cache, correctedDelta);
        }

        float inputSmoothFactor = cache.inputSmooth;
        float lastDelta = mCustomLastDelta;
        boolean isFirstTouch = mCustomFirstTouch;

        if (isFirstTouch) {
            lastDelta = correctedDelta;
            mCustomFirstTouch = false;
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
        mCustomLastDelta = filteredDelta;
        mCustomTargetFingerX = displacement;

        mState = STATE_PULL;
        mCustomSpring.cancel();

        float currentTranslation = mCustomSpring.mValue;

        float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
        if (effectiveSize < 1f) effectiveSize = mCustomScreenHeight;

        float rawMove = filteredDelta * effectiveSize;
        float pullCoeff = cache.pullCoeff;
        float resExponent = cache.resExponent;

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
        if ((currentTranslation > 0 && nextTranslation < 0) || (currentTranslation < 0 && nextTranslation > 0)) {
            nextTranslation = 0f;
        }
        if (strictHold && directionChanged && Math.abs(filteredDelta) <= Math.abs(lastDelta)) {
            nextTranslation = currentTranslation;
        }

        mCustomSpring.mValue = nextTranslation;
        // [FIX] mDistance must be non-negative — onPullDistance() assumes >= 0
        float distance = Math.abs(nextTranslation) / effectiveSize;
        mDistance = distance;

        if (distance == 0f) {
            mState = STATE_IDLE;
            mCustomSpring.mValue = 0f;
            mCustomSpring.mVelocity = 0f;
            // [FIX] Don't call resetCustomState() here — smooth offset may still be
            // non-zero from the previous draw() frame. If we zero smooth now,
            // isFinished() returns true and AOSP never calls draw() again,
            // leaving the RenderNode with stale translation → phantom shifted list.
            // Let draw() handle the visual decay and reset the RenderNode properly.
            mCustomFirstTouch = true;
            mCustomLastDelta = 0f;
        }

        // --- Delta pattern recording ---
        if (cache.recordPatterns) {
            long nowNs = SystemClock.elapsedRealtimeNanos();
            int gid = mCustomGestureId;
            int seq = mCustomGestureSeq;
            long prevNs = mCustomRecordLastNs;
            if (isFirstTouch) { gid++; seq = 0; }
            seq++;
            long dtUs = (!isFirstTouch && prevNs > 0) ? (nowNs - prevNs) / 1000L : 0L;
            mCustomGestureId = gid;
            mCustomGestureSeq = seq;
            mCustomRecordLastNs = nowNs;
            recordPattern(nowNs, mCustomContext.getPackageName(), gid, seq, dtUs,
                    deltaDistance, correctedDelta, filteredDelta,
                    displacement, currentTranslation, effectiveSize);
        }
    }

    /**
     * A view should call this when content is pulled away from an edge by the user.
     * This will update the state of the current visual effect and its associated animation.
     * The host view should always {@link android.view.View#invalidate()} after this
     * and draw the results accordingly. This works similarly to {@link #onPull(float, float)},
     * but returns the amount of <code>deltaDistance</code> that has been consumed. If the
     * {@link #getDistance()} is currently 0 and <code>deltaDistance</code> is negative, this
     * function will return 0 and the drawn value will remain unchanged.
     *
     * This method can be used to reverse the effect from a pull or absorb and partially consume
     * some of a motion:
     *
     * <pre class="prettyprint">
     *     if (deltaY < 0) {
     *         float consumed = edgeEffect.onPullDistance(deltaY / getHeight(), x / getWidth());
     *         deltaY -= consumed * getHeight();
     *         if (edgeEffect.getDistance() == 0f) edgeEffect.onRelease();
     *     }
     * </pre>
     *
     * @param deltaDistance Change in distance since the last call. Values may be 0 (no change) to
     *                      1.f (full length of the view) or negative values to express change
     *                      back toward the edge reached to initiate the effect.
     * @param displacement The displacement from the starting side of the effect of the point
     *                     initiating the pull. In the case of touch this is the finger position.
     *                     Values may be from 0-1.
     * @return The amount of <code>deltaDistance</code> that was consumed, a number between
     * 0 and <code>deltaDistance</code>.
     */
    public float onPullDistance(float deltaDistance, float displacement) {
        if (!isBounceEnabled()) {
            // ── Original AOSP logic ──
            return onPullDistanceOriginal(deltaDistance, displacement);
        }

        // ── Custom bounce logic ──
        if (mCustomSpring == null) return 0f;

        float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
        if (effectiveSize < 1f) effectiveSize = mCustomScreenHeight;

        float currentDistance = Math.abs(mCustomSpring.mValue) / effectiveSize;
        float finalDistance = Math.max(0f, deltaDistance + currentDistance);
        float delta = finalDistance - currentDistance;

        if (delta == 0f && currentDistance == 0f) {
            return 0f;
        }

        // Delegate to our onPull logic
        onPull(delta, displacement);
        return delta;
    }

    /**
     * Returns the pull distance needed to be released to remove the showing effect.
     * It is determined by the {@link #onPull(float, float)} <code>deltaDistance</code> and
     * any animating values, including from {@link #onAbsorb(int)} and {@link #onRelease()}.
     *
     * This can be used in conjunction with {@link #onPullDistance(float, float)} to
     * release the currently showing effect.
     *
     * @return The pull distance that must be released to remove the showing effect.
     */
    public float getDistance() {
        return mDistance;
    }

    /**
     * Call when the object is released after being pulled.
     * This will begin the "decay" phase of the effect. After calling this method
     * the host view should {@link android.view.View#invalidate()} and thereby
     * draw the results accordingly.
     */
    public void onRelease() {
        if (!isBounceEnabled()) {
            // ── Original AOSP logic ──
            onReleaseOriginal();
            return;
        }

        // ── Custom bounce logic ──
        // [FIX] Match AOSP: onRelease() is a no-op unless in STATE_PULL (1).
        // Without this guard, onRelease kills spring animations started by onAbsorb,
        // because Compose/AOSP call onRelease() right after onAbsorb().
        if (mState != STATE_PULL) {
            // Still reset touch tracking for next interaction
            mCustomTargetFingerX = 0.5f;
            mCustomLastDelta = 0f;
            mCustomFirstTouch = true;
            return;
        }

        SettingsCache cache = getSettingsCache(mCustomContext, true);
        if (mCustomSpring != null && Math.abs(mCustomSpring.mValue) > 0.5f) {
            mCustomSpring.setSpeedMultiplier(cache.animationSpeedMul);
            float stiffness = cache.stiffness;
            float damping = cache.damping;
            float minVel = cache.minVel;
            float minVal = cache.minVal;

            mCustomSpring.setParams(stiffness, damping, minVel, minVal);
            mCustomSpring.setTargetValue(0);
            mCustomSpring.setVelocity(0);
            mCustomSpring.start();
            mState = STATE_RECEDE;
        } else {
            // Value <= 0.5 — negligible pull, reset immediately
            if (mCustomSpring != null) {
                mCustomSpring.cancel();
                mCustomSpring.mValue = 0f;
                mCustomSpring.mVelocity = 0f;
            }
            resetCustomState();
            mState = STATE_IDLE;
            mDistance = 0f;
        }

        mCustomTargetFingerX = 0.5f;
        mCustomLastDelta = 0f;
        mCustomFirstTouch = true;
    }

    /**
     * Call when the effect absorbs an impact at the given velocity.
     * Used when a fling reaches the scroll boundary.
     *
     * <p>When using a {@link android.widget.Scroller} or {@link android.widget.OverScroller},
     * the method <code>getCurrVelocity</code> will provide a reasonable approximation
     * to use here.</p>
     *
     * @param velocity Velocity at impact in pixels per second.
     */
    public void onAbsorb(int velocity) {
        if (!isBounceEnabled()) {
            // ── Original AOSP logic ──
            onAbsorbOriginal(velocity);
            return;
        }

        // ── Custom bounce logic ──
        mState = STATE_RECEDE;
        SettingsCache cache = getSettingsCache(mCustomContext, true);

        if (mCustomSpring != null) {
            mCustomSpring.setSpeedMultiplier(cache.animationSpeedMul);
            mCustomSpring.cancel();

            float flingMult = cache.fling;
            float stiffness = cache.stiffness;
            float damping = cache.damping;
            float minVel = cache.minVel;
            float minVal = cache.minVal;

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
    }

    /**
     * Set the color of this edge effect in argb.
     *
     * @param color Color in argb
     */
    public void setColor(@ColorInt int color) {
        mPaint.setColor(color);
    }

    /**
     * Set or clear the blend mode. A blend mode defines how source pixels
     * (generated by a drawing command) are composited with the destination pixels
     * (content of the render target).
     * <p />
     * Pass null to clear any previous blend mode.
     * <p />
     *
     * @see BlendMode
     *
     * @param blendmode May be null. The blend mode to be installed in the paint
     */
    public void setBlendMode(@Nullable BlendMode blendmode) {
        mPaint.setBlendMode(blendmode);
    }

    /**
     * Return the color of this edge effect in argb.
     * @return The color of this edge effect in argb
     */
    @ColorInt
    public int getColor() {
        return mPaint.getColor();
    }

    /**
     * Returns the blend mode. A blend mode defines how source pixels
     * (generated by a drawing command) are composited with the destination pixels
     * (content of the render target).
     * <p />
     *
     * @return BlendMode
     */
    @Nullable
    public BlendMode getBlendMode() {
        return mPaint.getBlendMode();
    }

    /**
     * Draw into the provided canvas. Assumes that the canvas has been rotated
     * accordingly and the size has been set. The effect will be drawn the full
     * width of X=0 to X=width, beginning from Y=0 and extending to some factor <
     * 1.f of height. The effect will only be visible on a
     * hardware canvas, e.g. {@link RenderNode#beginRecording()}.
     *
     * @param canvas Canvas to draw into
     * @return true if drawing should continue beyond this frame to continue the
     *         animation
     */
    public boolean draw(Canvas canvas) {
        if (!isBounceEnabled()) {
            // ── Original AOSP draw logic ──
            return drawOriginal(canvas);
        }

        // ── Custom bounce draw logic ──
        SettingsCache cache = getSettingsCache(mCustomContext, false);
        if (!canvas.isHardwareAccelerated()) {
            forceFinish(mCustomSpring);
            return false;
        }

        if (mCustomSpring == null) {
            forceFinish(null);
            return false;
        }

        mCustomSpring.setSpeedMultiplier(cache.animationSpeedMul);

        if (mCustomSpring.isRunning()) mCustomSpring.doFrame(System.nanoTime());

        RenderNode renderNode = null;
        if (canvas instanceof RecordingCanvas) {
            renderNode = ((RecordingCanvas) canvas).mNode;
        }
        if (renderNode == null) {
            forceFinish(mCustomSpring);
            return false;
        }

        //noinspection deprecation
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

        float lerpMainIdle = cache.lerpMainIdle;
        float lerpMainRun = cache.lerpMainRun;
        float lerpFactorMain = mCustomSpring.isRunning() ? lerpMainRun : lerpMainIdle;
        lerpFactorMain = Math.min(1.0f, lerpFactorMain * cache.animationSpeedMul);

        float targetOffset = mCustomSpring.mValue;
        float currentOffset = mCustomSmoothOffsetY;
        float newOffset = currentOffset + (targetOffset - currentOffset) * lerpFactorMain;

        float minVal = cache.minVal;
        if (Math.abs(targetOffset - newOffset) < 0.5f) newOffset = targetOffset;
        if (Math.abs(targetOffset) < 0.1f && Math.abs(newOffset) < minVal) newOffset = 0f;

        mCustomSmoothOffsetY = newOffset;

        float screenHeight = mCustomScreenHeight;
        float screenWidth = mCustomScreenWidth;

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

        float newScaleV = lerp(mCustomSmoothScale, targetScaleV, lerpFactorMain);
        float newScaleZ = lerp(mCustomSmoothZoom, targetScaleZ, lerpFactorMain);
        float newScaleH = lerp(mCustomSmoothHScale, targetScaleH, lerpFactorMain);

        mCustomSmoothScale = newScaleV;
        mCustomSmoothZoom = newScaleZ;
        mCustomSmoothHScale = newScaleH;

        boolean isResting = Math.abs(newOffset) < 0.1f
                && Math.abs(newScaleV - 1f) < 0.001f
                && Math.abs(newScaleZ - 1f) < 0.001f
                && Math.abs(newScaleH - 1f) < 0.001f;
        if (isResting && !mCustomSpring.isRunning()) {
            safeResetRenderNode(renderNode, mWidth, mHeight);
            forceFinish(mCustomSpring);
            return false;
        }

        float canvasW = (float) canvas.getWidth();
        float canvasH = (float) canvas.getHeight();

        float effectiveSize = Math.max(Math.abs(mHeight), Math.abs(mWidth));
        if (effectiveSize < 1f) effectiveSize = screenHeight;
        // [FIX] Use spring physics value for mDistance, not the smooth visual offset.
        mDistance = Math.abs(mCustomSpring.mValue) / effectiveSize;

        try {
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

            renderNode.setPivotX(pivotX);
            renderNode.setPivotY(pivotY);
            renderNode.setScaleX(finalScaleX);
            renderNode.setScaleY(finalScaleY);
            renderNode.stretch(0f, 0f, mWidth, mHeight);
        } catch (Throwable t) {}

        boolean continueAnim = mCustomSpring.isRunning()
                || Math.abs(newOffset) >= minVal
                || Math.abs(newScaleV - 1f) >= 0.001f
                || Math.abs(newScaleZ - 1f) >= 0.001f
                || Math.abs(newScaleH - 1f) >= 0.001f;

        if (!continueAnim) {
            safeResetRenderNode(renderNode, mWidth, mHeight);
            forceFinish(mCustomSpring);
        }
        return continueAnim;
    }

    /**
     * Return the maximum height that the edge effect will be drawn at given the original
     * {@link #setSize(int, int) input size}.
     * @return The maximum height of the edge effect
     */
    public int getMaxHeight() {
        return (int) mHeight;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Original AOSP method implementations (used when bounce is disabled)
    // ═══════════════════════════════════════════════════════════════════

    private void onPullOriginal(float deltaDistance, float displacement) {
        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_NONE) {
            mState = STATE_IDLE;
            mDistance = 0;
            mVelocity = 0;
            return;
        }
        final long now = AnimationUtils.currentAnimationTimeMillis();
        mTargetDisplacement = displacement;
        if (mState == STATE_PULL_DECAY && now - mStartTime < mDuration
                && edgeEffectBehavior == TYPE_GLOW) {
            return;
        }
        if (mState != STATE_PULL) {
            if (edgeEffectBehavior == TYPE_STRETCH) {
                mPullDistance = mDistance;
            } else {
                mGlowScaleY = Math.max(PULL_GLOW_BEGIN, mGlowScaleY);
            }
        }
        mState = STATE_PULL;

        mStartTime = now;
        mDuration = PULL_TIME;

        mPullDistance += deltaDistance;
        if (edgeEffectBehavior == TYPE_STRETCH) {
            mPullDistance = Math.min(1f, mPullDistance);
        }
        mDistance = Math.max(0f, mPullDistance);
        mVelocity = 0;

        if (mPullDistance == 0) {
            mGlowScaleY = mGlowScaleYStart = 0;
            mGlowAlpha = mGlowAlphaStart = 0;
        } else {
            final float absdd = Math.abs(deltaDistance);
            mGlowAlpha = mGlowAlphaStart = Math.min(MAX_ALPHA,
                    mGlowAlpha + (absdd * PULL_DISTANCE_ALPHA_GLOW_FACTOR));

            final float scale = (float) (Math.max(0, 1 - 1 /
                    Math.sqrt(Math.abs(mPullDistance) * mBounds.height()) - 0.3d) / 0.7d);

            mGlowScaleY = mGlowScaleYStart = scale;
        }

        mGlowAlphaFinish = mGlowAlpha;
        mGlowScaleYFinish = mGlowScaleY;
        if (edgeEffectBehavior == TYPE_STRETCH && mDistance == 0) {
            mState = STATE_IDLE;
        }
    }

    private float onPullDistanceOriginal(float deltaDistance, float displacement) {
        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_NONE) {
            return 0f;
        }
        float finalDistance = Math.max(0f, deltaDistance + mDistance);
        float delta = finalDistance - mDistance;
        if (delta == 0f && mDistance == 0f) {
            return 0f;
        }

        if (mState != STATE_PULL && mState != STATE_PULL_DECAY && edgeEffectBehavior == TYPE_GLOW) {
            mPullDistance = mDistance;
            mState = STATE_PULL;
        }
        onPullOriginal(delta, displacement);
        return delta;
    }

    private void onReleaseOriginal() {
        mPullDistance = 0;

        if (mState != STATE_PULL && mState != STATE_PULL_DECAY) {
            return;
        }

        mState = STATE_RECEDE;
        mGlowAlphaStart = mGlowAlpha;
        mGlowScaleYStart = mGlowScaleY;

        mGlowAlphaFinish = 0.f;
        mGlowScaleYFinish = 0.f;
        mVelocity = 0.f;

        mStartTime = AnimationUtils.currentAnimationTimeMillis();
        mDuration = RECEDE_TIME;
    }

    private void onAbsorbOriginal(int velocity) {
        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_STRETCH) {
            mState = STATE_RECEDE;
            mVelocity = velocity * ON_ABSORB_VELOCITY_ADJUSTMENT;
            mStartTime = AnimationUtils.currentAnimationTimeMillis();
        } else if (edgeEffectBehavior == TYPE_GLOW) {
            mState = STATE_ABSORB;
            mVelocity = 0;
            velocity = Math.min(Math.max(MIN_VELOCITY, Math.abs(velocity)), MAX_VELOCITY);

            mStartTime = AnimationUtils.currentAnimationTimeMillis();
            mDuration = 0.15f + (velocity * 0.02f);

            mGlowAlphaStart = GLOW_ALPHA_START;
            mGlowScaleYStart = Math.max(mGlowScaleY, 0.f);

            mGlowScaleYFinish = Math.min(0.025f + (velocity * (velocity / 100) * 0.00015f) / 2,
                    1.f);
            mGlowAlphaFinish = Math.max(
                    mGlowAlphaStart,
                    Math.min(velocity * VELOCITY_GLOW_FACTOR * .00001f, MAX_ALPHA));
            mTargetDisplacement = 0.5f;
        } else {
            mState = STATE_IDLE;
            mDistance = 0;
            mVelocity = 0;
        }
    }

    private boolean drawOriginal(Canvas canvas) {
        int edgeEffectBehavior = getCurrentEdgeEffectBehavior();
        if (edgeEffectBehavior == TYPE_GLOW) {
            update();
            final int count = canvas.save();

            final float centerX = mBounds.centerX();
            final float centerY = mBounds.height() - mRadius;

            canvas.scale(1.f, Math.min(mGlowScaleY, 1.f) * mBaseGlowScale, centerX, 0);

            final float displacement = Math.max(0, Math.min(mDisplacement, 1.f)) - 0.5f;
            float translateX = mBounds.width() * displacement / 2;

            canvas.clipRect(mBounds);
            canvas.translate(translateX, 0);
            mPaint.setAlpha((int) (0xff * mGlowAlpha));
            canvas.drawCircle(centerX, centerY, mRadius, mPaint);
            canvas.restoreToCount(count);
        } else if (edgeEffectBehavior == TYPE_STRETCH && canvas instanceof RecordingCanvas) {
            if (mState == STATE_RECEDE) {
                updateSpring();
            }
            if (mDistance != 0f) {
                RecordingCanvas recordingCanvas = (RecordingCanvas) canvas;
                if (mTmpMatrix == null) {
                    mTmpMatrix = new Matrix();
                    mTmpPoints = new float[12];
                }
                //noinspection deprecation
                recordingCanvas.getMatrix(mTmpMatrix);

                mTmpPoints[0] = 0;
                mTmpPoints[1] = 0; // top-left
                mTmpPoints[2] = mWidth;
                mTmpPoints[3] = 0; // top-right
                mTmpPoints[4] = mWidth;
                mTmpPoints[5] = mHeight; // bottom-right
                mTmpPoints[6] = 0;
                mTmpPoints[7] = mHeight; // bottom-left
                mTmpPoints[8] = mWidth * mDisplacement;
                mTmpPoints[9] = 0; // drag start point
                mTmpPoints[10] = mWidth * mDisplacement;
                mTmpPoints[11] = mHeight * mDistance; // drag point
                mTmpMatrix.mapPoints(mTmpPoints);

                RenderNode renderNode = recordingCanvas.mNode;

                float left = renderNode.getLeft()
                    + min(mTmpPoints[0], mTmpPoints[2], mTmpPoints[4], mTmpPoints[6]);
                float top = renderNode.getTop()
                    + min(mTmpPoints[1], mTmpPoints[3], mTmpPoints[5], mTmpPoints[7]);
                float right = renderNode.getLeft()
                    + max(mTmpPoints[0], mTmpPoints[2], mTmpPoints[4], mTmpPoints[6]);
                float bottom = renderNode.getTop()
                    + max(mTmpPoints[1], mTmpPoints[3], mTmpPoints[5], mTmpPoints[7]);
                // assume rotations of increments of 90 degrees
                float x = mTmpPoints[10] - mTmpPoints[8];
                float width = right - left;
                float vecX = dampStretchVector(Math.max(-1f, Math.min(1f, x / width)));

                float y = mTmpPoints[11] - mTmpPoints[9];
                float height = bottom - top;
                float vecY = dampStretchVector(Math.max(-1f, Math.min(1f, y / height)));

                boolean hasValidVectors = Float.isFinite(vecX) && Float.isFinite(vecY);
                if (right > left && bottom > top && mWidth > 0 && mHeight > 0 && hasValidVectors) {
                    renderNode.stretch(
                        vecX, // horizontal stretch intensity
                        vecY, // vertical stretch intensity
                        mWidth, // max horizontal stretch in pixels
                        mHeight // max vertical stretch in pixels
                    );
                }
            }
        } else {
            mState = STATE_IDLE;
            mDistance = 0;
            mVelocity = 0;
        }

        boolean oneLastFrame = false;
        if (mState == STATE_RECEDE && mDistance == 0 && mVelocity == 0) {
            mState = STATE_IDLE;
            oneLastFrame = true;
        }

        return mState != STATE_IDLE || oneLastFrame;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Original AOSP private helpers
    // ═══════════════════════════════════════════════════════════════════

    private float min(float f1, float f2, float f3, float f4) {
        float min = Math.min(f1, f2);
        min = Math.min(min, f3);
        return Math.min(min, f4);
    }

    private float max(float f1, float f2, float f3, float f4) {
        float max = Math.max(f1, f2);
        max = Math.max(max, f3);
        return Math.max(max, f4);
    }

    private void update() {
        final long time = AnimationUtils.currentAnimationTimeMillis();
        final float t = Math.min((time - mStartTime) / mDuration, 1.f);

        final float interp = mInterpolator.getInterpolation(t);

        mGlowAlpha = mGlowAlphaStart + (mGlowAlphaFinish - mGlowAlphaStart) * interp;
        mGlowScaleY = mGlowScaleYStart + (mGlowScaleYFinish - mGlowScaleYStart) * interp;
        if (mState != STATE_PULL) {
            mDistance = calculateDistanceFromGlowValues(mGlowScaleY, mGlowAlpha);
        }
        mDisplacement = (mDisplacement + mTargetDisplacement) / 2;

        if (t >= 1.f - EPSILON) {
            switch (mState) {
                case STATE_ABSORB:
                    mState = STATE_RECEDE;
                    mStartTime = AnimationUtils.currentAnimationTimeMillis();
                    mDuration = RECEDE_TIME;

                    mGlowAlphaStart = mGlowAlpha;
                    mGlowScaleYStart = mGlowScaleY;

                    mGlowAlphaFinish = 0.f;
                    mGlowScaleYFinish = 0.f;
                    break;
                case STATE_PULL:
                    mState = STATE_PULL_DECAY;
                    mStartTime = AnimationUtils.currentAnimationTimeMillis();
                    mDuration = PULL_DECAY_TIME;

                    mGlowAlphaStart = mGlowAlpha;
                    mGlowScaleYStart = mGlowScaleY;

                    mGlowAlphaFinish = 0.f;
                    mGlowScaleYFinish = 0.f;
                    break;
                case STATE_PULL_DECAY:
                    mState = STATE_RECEDE;
                    break;
                case STATE_RECEDE:
                    mState = STATE_IDLE;
                    break;
            }
        }
    }

    private void updateSpring() {
        final long time = AnimationUtils.currentAnimationTimeMillis();
        final float deltaT = (time - mStartTime) / 1000f;
        if (deltaT < 0.001f) {
            return;
        }
        mStartTime = time;

        if (Math.abs(mVelocity) <= LINEAR_VELOCITY_TAKE_OVER
                && Math.abs(mDistance * mHeight) < LINEAR_DISTANCE_TAKE_OVER
                && Math.signum(mVelocity) == -Math.signum(mDistance)
        ) {
            mVelocity = Math.signum(mVelocity) * LINEAR_VELOCITY_TAKE_OVER;

            float targetDistance = mDistance + (mVelocity * deltaT / mHeight);
            if (Math.signum(targetDistance) != Math.signum(mDistance)) {
                mDistance = 0;
                mVelocity = 0;
            } else {
                mDistance = targetDistance;
            }
            return;
        }
        final double mDampedFreq = NATURAL_FREQUENCY * Math.sqrt(1 - DAMPING_RATIO * DAMPING_RATIO);

        double cosCoeff = mDistance * mHeight;
        double sinCoeff = (1 / mDampedFreq) * (DAMPING_RATIO * NATURAL_FREQUENCY
                * mDistance * mHeight + mVelocity);
        double distance = Math.pow(Math.E, -DAMPING_RATIO * NATURAL_FREQUENCY * deltaT)
                * (cosCoeff * Math.cos(mDampedFreq * deltaT)
                + sinCoeff * Math.sin(mDampedFreq * deltaT));
        double velocity = distance * (-NATURAL_FREQUENCY) * DAMPING_RATIO
                + Math.pow(Math.E, -DAMPING_RATIO * NATURAL_FREQUENCY * deltaT)
                * (-mDampedFreq * cosCoeff * Math.sin(mDampedFreq * deltaT)
                + mDampedFreq * sinCoeff * Math.cos(mDampedFreq * deltaT));
        mDistance = (float) distance / mHeight;
        mVelocity = (float) velocity;
        if (mDistance > 1f) {
            mDistance = 1f;
            mVelocity = 0f;
        }
        if (isAtEquilibrium()) {
            mDistance = 0;
            mVelocity = 0;
        }
    }

    /**
     * @return The estimated pull distance as calculated from mGlowScaleY.
     */
    private float calculateDistanceFromGlowValues(float scale, float alpha) {
        if (scale >= 1f) {
            return 1f;
        }
        if (scale > 0f) {
            float v = 1f / 0.7f / (mGlowScaleY - 1f);
            return v * v / mBounds.height();
        }
        return alpha / PULL_DISTANCE_ALPHA_GLOW_FACTOR;
    }

    /**
     * @return true if the spring used for calculating the stretch animation is
     * considered at rest or false if it is still animating.
     */
    private boolean isAtEquilibrium() {
        double displacement = mDistance * mHeight;
        double velocity = mVelocity;

        return displacement < 0 || (Math.abs(velocity) < VELOCITY_THRESHOLD
                && displacement < VALUE_THRESHOLD);
    }

    private float dampStretchVector(float normalizedVec) {
        float sign = normalizedVec > 0 ? 1f : -1f;
        float overscroll = Math.abs(normalizedVec);
        float linearIntensity = LINEAR_STRETCH_INTENSITY * overscroll;
        double scalar = Math.E / SCROLL_DIST_AFFECTED_BY_EXP_STRETCH;
        double expIntensity = EXP_STRETCH_INTENSITY * (1 - Math.exp(-overscroll * scalar));
        return sign * (float) (linearIntensity + expIntensity);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: initialization
    // ═══════════════════════════════════════════════════════════════════

    private void initCustomInstance(Context context) {
        mCustomContext = context;
        mCustomSpring = new SpringDynamics();
        mCustomSmoothOffsetY = 0f;
        mCustomSmoothScale = 1.0f;
        mCustomSmoothZoom = 1.0f;
        mCustomSmoothHScale = 1.0f;
        mCustomMatrix = new Matrix();
        mCustomPoints = new float[4];
        mCustomLastDelta = 0f;
        mCustomTargetFingerX = 0.5f;
        mCustomCurrentFingerX = 0.5f;
        mCustomFirstTouch = true;

        // Normalization state — per-instance, persists across gestures
        mNormDeltaWindow = null;
        mNormWindowIdx = 0;
        mNormWindowCount = 0;
        mNormCurrentFactor = 1.0f;

        // Определяем caller по стек-трейсу (один раз при создании)
        mCallerInfo = detectCaller();
        mIsComposeCaller = mCallerInfo.startsWith("compose");

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

        // Per-package configuration
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
        mCfgScale = myScale;
        mCfgFilter = myFilter;
        mCfgIgnore = myIgnore;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: state management
    // ═══════════════════════════════════════════════════════════════════

    private void resetCustomState() {
        mCustomSmoothOffsetY = 0f;
        mCustomSmoothScale = 1.0f;
        mCustomSmoothZoom = 1.0f;
        mCustomSmoothHScale = 1.0f;
        mCustomTargetFingerX = 0.5f;
        mCustomCurrentFingerX = 0.5f;
        mCustomFirstTouch = true;
    }

    private void forceFinish(SpringDynamics spring) {
        if (spring != null) {
            spring.cancel();
            spring.mValue = 0f;
            spring.mVelocity = 0f;
        }
        resetCustomState();
        mState = STATE_IDLE;
        mDistance = 0f;
    }

    private void safeResetRenderNode(RenderNode renderNode, float viewWidth, float viewHeight) {
        if (renderNode == null) return;
        try {
            renderNode.setTranslationX(0f);
            renderNode.setTranslationY(0f);
            renderNode.setScaleX(1f);
            renderNode.setScaleY(1f);
            // [FIX] Pass actual W/H to stretch() — stretch(0,0,0,0) may not properly clear
            float sw = viewWidth > 0 ? viewWidth : 1f;
            float sh = viewHeight > 0 ? viewHeight : 1f;
            renderNode.stretch(0f, 0f, sw, sh);
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: caller detection
    // ═══════════════════════════════════════════════════════════════════

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
            // Пропускаем первые фреймы (getStackTrace, detectCaller, initCustomInstance, <init>)
            int startIdx = Math.min(4, stack.length);

            // Сначала ищем Compose (приоритет — он ближе к вызову)
            for (int i = startIdx; i < stack.length; i++) {
                String cls = stack[i].getClassName();
                for (String sig : COMPOSE_SIGS) {
                    if (cls.contains(sig)) {
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

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: delta normalization (Phase 2)
    // ═══════════════════════════════════════════════════════════════════

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
    private float applyDeltaNormalization(SettingsCache cache, float correctedDelta) {
        float absDelta = Math.abs(correctedDelta);

        boolean isCompose = mIsComposeCaller;
        int detectMode = cache.normDetectMode;

        // ── Mode 2: stacktrace-only — мгновенное решение, без окна ──
        if (detectMode == 2) {
            float factor = isCompose ? cache.normFactor : 1.0f;
            mNormCurrentFactor = factor;
            return correctedDelta * factor;
        }

        // ── Mode 0 и 1 используют скользящее окно ──

        // Get or recreate circular buffer (handles dynamic window size changes)
        int windowSize = cache.normWindow;
        if (mNormDeltaWindow == null || mNormDeltaWindow.length != windowSize) {
            mNormDeltaWindow = new float[windowSize];
            mNormWindowIdx = 0;
            mNormWindowCount = 0;
        }

        int idx = mNormWindowIdx;
        int count = mNormWindowCount;
        float currentFactor = mNormCurrentFactor;

        // Only real deltas go into the window — micro-deltas (noise) are excluded
        if (absDelta > MICRO_DELTA_EPS) {
            mNormDeltaWindow[idx] = absDelta;
            idx = (idx + 1) % windowSize;
            if (count < windowSize) count++;
            mNormWindowIdx = idx;
            mNormWindowCount = count;
        }

        // ── Mode 1 (hybrid): Compose-инстансы нормализуются мгновенно,
        //    пока окно ещё не набрало достаточно данных ──
        int minSamples = Math.max(windowSize / 2, 2);
        boolean windowReady = (count >= minSamples);

        if (detectMode == 1 && isCompose && !windowReady) {
            float rampStep = Math.abs(1.0f - cache.normFactor) / Math.max(cache.normRamp, 1);
            float targetFactor = cache.normFactor;
            if (currentFactor > targetFactor) {
                currentFactor = Math.max(currentFactor - rampStep, targetFactor);
            } else if (currentFactor < targetFactor) {
                currentFactor = Math.min(currentFactor + rampStep, targetFactor);
            }
            mNormCurrentFactor = currentFactor;
            return correctedDelta * currentFactor;
        }

        // ── Окно набрало достаточно данных — решение по поведению ──
        if (windowReady) {
            float sum = 0;
            for (int i = 0; i < count; i++) sum += mNormDeltaWindow[i];
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

            float rampStep = Math.abs(1.0f - cache.normFactor) / Math.max(cache.normRamp, 1);
            if (currentFactor > targetFactor) {
                currentFactor = Math.max(currentFactor - rampStep, targetFactor);
            } else if (currentFactor < targetFactor) {
                currentFactor = Math.min(currentFactor + rampStep, targetFactor);
            }
        }

        mNormCurrentFactor = currentFactor;
        return correctedDelta * currentFactor;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: pattern recording
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Logs one onPull event via logcat with tag RECORD_TAG.
     * Active only when global setting record_patterns_edge_effect = 1.
     * Format: elapsed_ns\tpkg\tgesture_id\tseq\tdt_us\traw\tcorrected\tfiltered\tdisp\tspring\tsize\tcaller
     * Use `adb logcat -s EdgePatternRec:D` to capture on PC.
     */
    private void recordPattern(long elapsedNs, String pkg, int gestureId, int seq,
                               long dtUs, float raw, float corrected, float filtered,
                               float disp, float springVal, float effSize) {
        String callerInfo = (mCallerInfo != null) ? mCallerInfo : "?";
        Log.d(RECORD_TAG, elapsedNs + "\t" + pkg + "\t" + gestureId + "\t" + seq + "\t" + dtUs
                + "\t" + raw + "\t" + corrected + "\t" + filtered
                + "\t" + disp + "\t" + springVal + "\t" + effSize + "\t" + callerInfo);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: settings & helpers
    // ═══════════════════════════════════════════════════════════════════

    private boolean isBounceEnabled() {
        if (mCustomContext == null) return false;
        try {
            if (getIntSetting(mCustomContext, KEY_ENABLED, 1) != 1) return false;
            if (mCfgIgnore) return false;
            return true;
        } catch (Exception ignored) { return true; }
    }

    private SettingsCache getSettingsCache(Context ctx, boolean force) {
        SettingsCache cache = mCustomSettingsCache;
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
        mCustomSettingsCache = cache;
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

    // ═══════════════════════════════════════════════════════════════════
    //  Custom bounce: SpringDynamics
    // ═══════════════════════════════════════════════════════════════════

    /**
     * @hide
     */
    static class SpringDynamics {
        private float mStiffness = 450.0f;
        private float mDampingRatio = 0.7f;
        private float mMinVel = 1.0f;
        private float mMinVal = 0.5f;
        private float mSpeedMultiplier = 1.0f;

        float mValue;
        float mVelocity;
        float mTargetValue = 0f;
        private boolean mIsRunning = false;
        private long mLastFrameTimeNanos = 0;

        void setParams(float stiffness, float damping, float minVel, float minVal) {
            mStiffness = stiffness > 0 ? stiffness : 0.1f;
            mDampingRatio = damping >= 0 ? damping : 0;
            mMinVel = minVel;
            mMinVal = minVal;
        }

        void setSpeedMultiplier(float speedMultiplier) {
            if (speedMultiplier < 0.01f) speedMultiplier = 0.01f;
            mSpeedMultiplier = speedMultiplier;
        }

        void setTargetValue(float targetValue) { mTargetValue = targetValue; }
        void setVelocity(float velocity) { mVelocity = velocity; }
        boolean isRunning() { return mIsRunning; }
        void cancel() { mIsRunning = false; }

        void start() {
            if (mIsRunning) return;
            mIsRunning = true;
            mLastFrameTimeNanos = System.nanoTime();
        }

        void doFrame(long frameTimeNanos) {
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
