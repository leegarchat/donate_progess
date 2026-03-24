package org.pixel.customparts.hooks;

import android.content.Context;
import android.view.View;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import org.pixel.customparts.core.BaseHook;

/**
 * Hook for android.widget.Magnifier — allows customizing the text loupe zoom level.
 *
 * Works globally (all processes) since Magnifier is a framework class.
 *
 * Hooked points:
 * 1. Magnifier(Builder) constructor BEFORE — modify Builder's mZoom so the constructor
 *    creates InternalPopupWindow with consistent source dimensions from the start.
 * 2. setZoom(float) BEFORE — override the zoom parameter for runtime changes.
 *
 * Settings (bare keys — suffix is added by IHookEnvironment):
 * - magnifier_custom_enabled  (int 0/1)
 * - magnifier_custom_zoom     (float, default 1.25)
 * - magnifier_custom_size     (float, default 1.0) — window size scale multiplier
 * - magnifier_custom_shape    (int, 0=default, 1=square, 2=circle)
 * - magnifier_custom_offset_y (int, dp offset, default 0) — vertical position shift
 */
public class MagnifierHook extends BaseHook {

    private static final String KEY_ENABLED = "magnifier_custom_enabled";
    private static final String KEY_ZOOM = "magnifier_custom_zoom";
    private static final String KEY_SIZE = "magnifier_custom_size";
    private static final String KEY_SHAPE = "magnifier_custom_shape";
    private static final String KEY_OFFSET_Y = "magnifier_custom_offset_y";

    private static final float DEFAULT_ZOOM = 1.25f;
    private static final float DEFAULT_SIZE = 1.0f;

    private static final int SHAPE_DEFAULT = 0;
    private static final int SHAPE_SQUARE = 1;
    private static final int SHAPE_CIRCLE = 2;

    @Override
    public String getHookId() {
        return "MagnifierHook";
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean isEnabled(Context context) {
        // Always install hooks — the enabled check is done lazily inside each
        // hook callback (per Magnifier instance creation), so toggling the setting
        // takes effect immediately without restarting the process.
        return true;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            hookMagnifier(classLoader);
            log("Magnifier hook installed");
        } catch (Throwable e) {
            logError("Failed to initialize Magnifier hook", e);
        }
    }

    // ─── Context Helpers ────────────────────────────────────────────────

    private static Context getContextFromView(Object viewObj) {
        try {
            if (viewObj instanceof View) {
                return ((View) viewObj).getContext();
            }
        } catch (Throwable t) {
            // ignore
        }
        return null;
    }

    private static Context getContextFromMagnifier(Object magnifier) {
        try {
            Object view = XposedHelpers.getObjectField(magnifier, "mView");
            return getContextFromView(view);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Context getContextFromBuilder(Object builder) {
        try {
            Object view = XposedHelpers.getObjectField(builder, "mView");
            return getContextFromView(view);
        } catch (Throwable t) {
            return null;
        }
    }

    // ─── Hook Entry ─────────────────────────────────────────────────────

    private void hookMagnifier(ClassLoader classLoader) {
        Class<?> magnifierClass;
        try {
            magnifierClass = XposedHelpers.findClass("android.widget.Magnifier", classLoader);
        } catch (Throwable t) {
            log("Magnifier class not found, skipping");
            return;
        }

        Class<?> builderClass;
        try {
            builderClass = XposedHelpers.findClass("android.widget.Magnifier$Builder", classLoader);
        } catch (Throwable t) {
            log("Magnifier.Builder class not found, skipping constructor hook");
            return;
        }

        // --- Hook 1: Constructor Magnifier(Builder) — BEFORE ---
        // Modify the Builder's mZoom field BEFORE the constructor runs.
        // This way the constructor computes mSourceWidth/mSourceHeight from the new zoom
        // and creates InternalPopupWindow with consistent dimensions.
        // Modifying fields AFTER the constructor causes mismatch → magnifier disappears.
        XposedHelpers.findAndHookConstructor(magnifierClass, builderClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object builder = param.args[0];
                Context ctx = getContextFromBuilder(builder);
                if (!isSettingEnabled(ctx, KEY_ENABLED)) return;

                float customZoom = getFloatSetting(ctx, KEY_ZOOM, DEFAULT_ZOOM);
                float sizeScale = getFloatSetting(ctx, KEY_SIZE, DEFAULT_SIZE);
                int shape = getIntSetting(ctx, KEY_SHAPE, SHAPE_DEFAULT);
                int offsetYDp = getIntSetting(ctx, KEY_OFFSET_Y, 0);

                try {
                    float originalZoom = XposedHelpers.getFloatField(builder, "mZoom");
                    int originalWidth = XposedHelpers.getIntField(builder, "mWidth");
                    int originalHeight = XposedHelpers.getIntField(builder, "mHeight");

                    // Scale the magnifier window dimensions.
                    // Larger window keeps source area reasonable at high zoom values.
                    int scaledWidth = Math.round(originalWidth * sizeScale);
                    int scaledHeight = Math.round(originalHeight * sizeScale);
                    if (scaledWidth < 1) scaledWidth = 1;
                    if (scaledHeight < 1) scaledHeight = 1;

                    boolean changed = false;

                    if (Math.abs(sizeScale - 1.0f) > 0.01f) {
                        XposedHelpers.setIntField(builder, "mWidth", scaledWidth);
                        XposedHelpers.setIntField(builder, "mHeight", scaledHeight);
                        changed = true;
                    }

                    if (Math.abs(customZoom - originalZoom) > 0.01f) {
                        XposedHelpers.setFloatField(builder, "mZoom", customZoom);
                        changed = true;
                    }

                    if (changed) {
                        // Recompute source dimensions in Builder to match new zoom & window size.
                        int finalWidth = (Math.abs(sizeScale - 1.0f) > 0.01f) ? scaledWidth : originalWidth;
                        int finalHeight = (Math.abs(sizeScale - 1.0f) > 0.01f) ? scaledHeight : originalHeight;
                        float finalZoom = (Math.abs(customZoom - originalZoom) > 0.01f) ? customZoom : originalZoom;

                        try {
                            boolean fishEye = false;
                            try {
                                fishEye = XposedHelpers.getBooleanField(builder, "mIsFishEyeStyle");
                            } catch (Throwable ignored) {}

                            XposedHelpers.setIntField(builder, "mSourceWidth",
                                    fishEye ? finalWidth : Math.round(finalWidth / finalZoom));
                            XposedHelpers.setIntField(builder, "mSourceHeight",
                                    Math.round(finalHeight / finalZoom));
                        } catch (Throwable ignored) {
                            // Builder may not have mSourceWidth/mSourceHeight — constructor will compute them
                        }

                        log("Builder: zoom " + originalZoom + " → " + customZoom
                                + ", size " + originalWidth + "x" + originalHeight
                                + " → " + finalWidth + "x" + finalHeight);
                    }

                    // --- Shape: square or circle ---
                    // Square: make width == height (use the larger side), keep default cornerRadius.
                    // Circle: make square + set cornerRadius = side / 2.
                    if (shape != SHAPE_DEFAULT) {
                        try {
                            int w = XposedHelpers.getIntField(builder, "mWidth");
                            int h = XposedHelpers.getIntField(builder, "mHeight");
                            int side = Math.max(w, h);

                            // Make it square
                            XposedHelpers.setIntField(builder, "mWidth", side);
                            XposedHelpers.setIntField(builder, "mHeight", side);

                            // Recompute source dimensions for the new square window
                            float curZoom = XposedHelpers.getFloatField(builder, "mZoom");
                            try {
                                boolean fishEye = false;
                                try {
                                    fishEye = XposedHelpers.getBooleanField(builder, "mIsFishEyeStyle");
                                } catch (Throwable ignored) {}
                                XposedHelpers.setIntField(builder, "mSourceWidth",
                                        fishEye ? side : Math.round(side / curZoom));
                                XposedHelpers.setIntField(builder, "mSourceHeight",
                                        Math.round(side / curZoom));
                            } catch (Throwable ignored) {}

                            if (shape == SHAPE_CIRCLE) {
                                float radius = side / 2.0f;
                                XposedHelpers.setFloatField(builder, "mCornerRadius", radius);
                                log("Builder: shape=circle, " + w + "x" + h
                                        + " → " + side + "x" + side + ", cornerRadius=" + radius);
                            } else {
                                log("Builder: shape=square, " + w + "x" + h
                                        + " → " + side + "x" + side);
                            }
                        } catch (Throwable t) {
                            logError("Failed to modify shape", t);
                        }
                    }

                    // --- Vertical offset: shift magnifier position ---
                    if (offsetYDp != 0) {
                        try {
                            Object viewObj = XposedHelpers.getObjectField(builder, "mView");
                            float density = ((View) viewObj).getContext().getResources()
                                    .getDisplayMetrics().density;
                            int offsetPx = Math.round(offsetYDp * density);
                            int originalOffset = XposedHelpers.getIntField(builder,
                                    "mVerticalDefaultSourceToMagnifierOffset");
                            XposedHelpers.setIntField(builder,
                                    "mVerticalDefaultSourceToMagnifierOffset",
                                    originalOffset + offsetPx);
                            log("Builder: verticalOffset " + originalOffset
                                    + " → " + (originalOffset + offsetPx) + " (" + offsetYDp + "dp)");
                        } catch (Throwable t) {
                            logError("Failed to modify vertical offset", t);
                        }
                    }
                } catch (Throwable t) {
                    logError("Failed to modify Builder", t);
                }
            }
        });

        // --- Hook 2: setZoom(float) — BEFORE ---
        // Override the zoom value for runtime changes (e.g. fish-eye animations).
        XposedHelpers.findAndHookMethod(magnifierClass, "setZoom", float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Object magnifier = param.thisObject;
                Context ctx = getContextFromMagnifier(magnifier);
                if (!isSettingEnabled(ctx, KEY_ENABLED)) return;

                float customZoom = getFloatSetting(ctx, KEY_ZOOM, DEFAULT_ZOOM);
                float requestedZoom = (float) param.args[0];
                if (Math.abs(customZoom - requestedZoom) > 0.01f) {
                    param.args[0] = customZoom;
                }
            }
        });
    }
}
