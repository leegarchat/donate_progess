package org.pixel.customparts.hooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.pixel.customparts.core.BaseHook;

public class UnifiedLauncherHook extends BaseHook {

    private static final String KEY_HOME_ENABLE = "launcher_homepage_sizer";
    private static final String KEY_HOME_COLS = "launcher_homepage_h";
    private static final String KEY_HOME_ROWS = "launcher_homepage_v";
    private static final String KEY_HOME_ICON_SIZE = "launcher_homepage_icon_size";
    private static final String KEY_HOME_TEXT_MODE = "launcher_homepage_text_mode";
    private static final String KEY_DOCK_ENABLE = "launcher_dock_enable";
    private static final String KEY_HOTSEAT_ICONS = "launcher_hotseat_icons";
    private static final String KEY_HOTSEAT_ICON_SIZE = "launcher_hotseat_icon_size";
    private static final String KEY_HIDE_DOCK = "launcher_hidden_dock";
    private static final String KEY_PADDING_DOCK = "launcher_padding_dock";
    private static final String KEY_PADDING_SEARCH = "launcher_padding_search";
    private static final String KEY_HIDE_SEARCH = "launcher_hidden_search";
    private static final String KEY_PADDING_HOMEPAGE = "launcher_padding_homepage";
    private static final String KEY_DISABLE_FEED = "launcher_disable_google_feed";
    private static final String KEY_PADDING_DOTS = "launcher_padding_dots";
    private static final String KEY_PADDING_DOTS_X = "launcher_padding_dots_x";
    private static final String KEY_DT2S_ENABLED = "launcher_dt2s_enabled";
    private static final String KEY_DT2S_TIMEOUT = "launcher_dt2s_timeout";
    private static final String KEY_TOP_WIDGET_ENABLE = "launcher_disable_top_widget";
    private static final int DEFAULT_PADDING_DOTS = 0;
    private static final int SETTINGS_DEFAULT_PADDING = -45;
    private static final int LAUNCHER_ORIGINAL_BOTTOM_DP = 200;
    private static final int DISPLAY_WORKSPACE = 0;
    private static final int DISPLAY_FOLDER = 2;
    private static final int CONTAINER_HOTSEAT = -101;
    private static final int CONTAINER_HOTSEAT_PREDICTION = -103;
    private static final int TAG_VIEW_LISTENER = 0x7f010002;
    private static final int TAG_DT2S_LISTENER = 0x7f010004;
    private int lastAppliedPaddingHash = 0;
    private int lastAppliedDockHash = 0;
    private static Field doubleTapTimeoutField = null;
    private final WeakHashMap<View, View.OnLayoutChangeListener> layoutListeners = new WeakHashMap<>();
    private static final String PREFS_NAME = "top_row_keeper";
    private static final String PREF_GRID_ROWS = "last_grid_rows";
    private static final String PREF_GRID_COLS = "last_grid_cols";
    private static final String PREF_SAVED_ITEMS = "saved_items_json";

    static {
        try {
            doubleTapTimeoutField = GestureDetector.class.getDeclaredField("mDoubleTapTimeout");
            doubleTapTimeoutField.setAccessible(true);
        } catch (Exception e) { /* ignore */ }
    }

    @Override
    public String getHookId() {
        return "UnifiedLauncherHook";
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public boolean isEnabled(Context context) {
        return true;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            final Class<?> launcherClass = XposedHelpers.findClass("com.android.launcher3.Launcher", classLoader);
            final Class<?> workspaceClass = XposedHelpers.findClass("com.android.launcher3.Workspace", classLoader);
            final Class<?> bubbleTextViewClass = XposedHelpers.findClass("com.android.launcher3.BubbleTextView", classLoader);
            final Class<?> idpClass = XposedHelpers.findClass("com.android.launcher3.InvariantDeviceProfile", classLoader);
            
            hookLauncherLifecycle(launcherClass, classLoader);
            hookInvariantDeviceProfile(idpClass);
            hookBubbleTextView(bubbleTextViewClass, classLoader);
            hookFloatingIconView(classLoader);
            hookWorkspace(workspaceClass, classLoader);
            hookDockAnimationCorrection(classLoader);
            hookPersistenceLogic(classLoader);

            log("UnifiedLauncherHook installed successfully");

        } catch (Throwable e) {
            logError("Failed to initialize UnifiedLauncherHook", e);
        }
    }

    // =========================================================================
    // SECTION 1: LIFECYCLE & GLOBAL UI
    // =========================================================================

    private void hookLauncherLifecycle(Class<?> launcherClass, ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(launcherClass, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Activity activity = (Activity) param.thisObject;
                
                if (isSettingEnabled(activity, KEY_DISABLE_FEED)) {
                    disableFeedOverlay(activity);
                }

                View rootView = activity.findViewById(android.R.id.content);
                Runnable updateTask = new Runnable() {
                    @Override
                    public void run() {
                        applyHomepagePadding(activity);
                        applyDockSettings(activity);
                        forceUpdateDots(activity);
                        applyDT2SListener(activity);
                    }
                };

                if (rootView != null) {
                    rootView.post(updateTask);
                } else {
                    updateTask.run();
                }
            }
        });

        try {
            Class<?> overlayProxyClass = XposedHelpers.findClass(
                "com.android.systemui.plugins.shared.LauncherOverlayManager$LauncherOverlayTouchProxy",
                classLoader
            );
            XposedHelpers.findAndHookMethod(launcherClass, "setLauncherOverlay", overlayProxyClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (isSettingEnabled(getCurrentApplication(), KEY_DISABLE_FEED)) {
                        param.args[0] = null;
                    }
                }
            });
        } catch (Throwable e) { /* ignore */ }
    }

    private void disableFeedOverlay(Object launcherActivity) {
        try {
            XposedHelpers.callMethod(launcherActivity, "setLauncherOverlay", new Object[]{null});
        } catch (Throwable e) { /* ignore */ }
    }

    // =========================================================================
    // SECTION 2: GRID & IDP (InvariantDeviceProfile)
    // =========================================================================

    private void hookInvariantDeviceProfile(Class<?> idpClass) {
        XC_MethodHook initGridHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object idp = param.thisObject;
                Context context = getContextFromIDP(idp);
                
                if (context != null) {
                    if (isSettingEnabled(context, KEY_HOME_ENABLE)) {
                        int homeCols = getIntSetting(context, KEY_HOME_COLS, 0);
                        int homeRows = getIntSetting(context, KEY_HOME_ROWS, 0);
                        if (homeCols > 0) {
                            XposedHelpers.setIntField(idp, "numColumns", homeCols);
                            XposedHelpers.setIntField(idp, "numShownHotseatIcons", Math.max(homeCols, 4));
                            try { XposedHelpers.setIntField(idp, "minColumns", Math.min(homeCols, 4)); } catch (Throwable e) {}
                        }
                        if (homeRows > 0) {
                            XposedHelpers.setIntField(idp, "numRows", homeRows);
                            try { XposedHelpers.setIntField(idp, "minRows", Math.min(homeRows, 4)); } catch (Throwable e) {}
                        }
                    }

                    int hotseatIcons = getIntSetting(context, KEY_HOTSEAT_ICONS, 0);
                    if (hotseatIcons > 0) {
                        XposedHelpers.setIntField(idp, "numShownHotseatIcons", hotseatIcons);
                        XposedHelpers.setIntField(idp, "numDatabaseHotseatIcons", hotseatIcons);
                        updateWindowProfiles(idp, hotseatIcons);
                    }
                }
            }
        };

        XposedBridge.hookAllMethods(idpClass, "initGrid", initGridHook);
    }

    // =========================================================================
    // SECTION 3: ICONS (BubbleTextView) & ANIMATIONS
    // =========================================================================

    private void hookBubbleTextView(final Class<?> bubbleTextViewClass, ClassLoader classLoader) {
        Class<?> tempFolderIconClass = null;
        try {
            tempFolderIconClass = XposedHelpers.findClass("com.android.launcher3.folder.FolderIcon", classLoader);
        } catch (Throwable t) { }
        final Class<?> finalFolderIconClass = tempFolderIconClass;

        XC_MethodHook applyIconHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof TextView)) return;
                TextView view = (TextView) param.thisObject;
                Context context = view.getContext();
                if (context == null) return;

                boolean isHotseat = isHotseatView(view);
                
                if (isHotseat) {
                     int sizePercent = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100);
                     if (sizePercent > 0 && sizePercent != 100) {
                         view.setScaleX(1f);
                         view.setScaleY(1f);
                     }
                } else {
                    // Используем строгую проверку из GridSizeHomePageHook для рабочего стола
                    if (isSettingEnabled(context, KEY_HOME_ENABLE)) {
                        Object info = (param.args.length > 0) ? param.args[0] : null;
                        handleWorkspaceView(view, info);
                    }
                }
            }
        };

        for (Method method : bubbleTextViewClass.getDeclaredMethods()) {
            if (method.getName().equals("applyIconAndLabel")) {
                XposedBridge.hookMethod(method, applyIconHook);
            }
        }

        // 2. Hook FolderIcon.setFolder (Восстановлено из рабочего кода)
        if (finalFolderIconClass != null) {
            XposedBridge.hookAllMethods(finalFolderIconClass, "setFolder", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.thisObject instanceof View)) return;
                    View view = (View) param.thisObject;
                    if (isWorkspaceView(view)) {
                         applyWorkspaceIconSize(view, view.getContext());
                    }
                }
            });

            XposedHelpers.findAndHookMethod(android.widget.FrameLayout.class, "onLayout", boolean.class, int.class, int.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // Проверяем, что это FolderIcon (так как FolderIcon наследуется от FrameLayout)
                    if (finalFolderIconClass.isInstance(param.thisObject)) {
                        View view = (View) param.thisObject;
                        if (isWorkspaceView(view) && isSettingEnabled(view.getContext(), KEY_HOME_ENABLE)) {
                            updatePivot(view);
                        }
                    }
                }
            });
        }

        XposedHelpers.findAndHookMethod(TextView.class, "onLayout", boolean.class, int.class, int.class, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                // Проверяем, что это именно наш BubbleTextView
                if (!bubbleTextViewClass.isInstance(param.thisObject)) return;
                
                View view = (View) param.thisObject;
                // Применяем только к иконкам рабочего стола (не док)
                if (isWorkspaceView(view) && !isHotseatView(view) && isSettingEnabled(view.getContext(), KEY_HOME_ENABLE)) {
                    updatePivot(view);
                }
            }
        });

        XC_MethodHook scaleHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof View)) return;
                View view = (View) param.thisObject;
                
                boolean isBubble = bubbleTextViewClass.isInstance(view);
                boolean isFolder = (finalFolderIconClass != null && finalFolderIconClass.isInstance(view));
                if (!isBubble && !isFolder) return;

                Context context = view.getContext();
                if (context == null) return;

                float myScale = 1.0f;
                
                if (isHotseatView(view)) {
                    int size = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100);
                    if (size > 0 && size != 100) myScale = size / 100f;
                } else if (isWorkspaceView(view) && isSettingEnabled(context, KEY_HOME_ENABLE)) {
                    int size = getIntSetting(context, KEY_HOME_ICON_SIZE, 100);
                    if (size > 0 && size != 100) myScale = size / 100f;
                }

                if (myScale != 1.0f && param.args[0] instanceof Float) {
                    float incoming = (Float) param.args[0];
                    param.args[0] = incoming * myScale;
                }
            }
        };

        XposedHelpers.findAndHookMethod(View.class, "setScaleX", float.class, scaleHook);
        XposedHelpers.findAndHookMethod(View.class, "setScaleY", float.class, scaleHook);

        XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof View)) return;
                View view = (View) param.thisObject;
                
                boolean isBubble = bubbleTextViewClass.isInstance(view);
                boolean isFolder = (finalFolderIconClass != null && finalFolderIconClass.isInstance(view));
                if (!isBubble && !isFolder) return;

                if ((Integer) param.args[0] == View.VISIBLE) {
                    Context context = view.getContext();
                    if (context == null) return;
                    
                    if (isHotseatView(view)) {
                        int size = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100);
                        if (size > 0 && size != 100) { view.setScaleX(1f); view.setScaleY(1f); }
                    } else if (isWorkspaceView(view) && isSettingEnabled(context, KEY_HOME_ENABLE)) {
                        applyWorkspaceIconSize(view, context);
                    }
                }
            }
        });
    }

    private void hookFloatingIconView(ClassLoader classLoader) {
        try {
            Class<?> helperClass = XposedHelpers.findClass("com.android.quickstep.util.FloatingIconViewHelper", classLoader);
            Class<?> launcherClass = XposedHelpers.findClass("com.android.launcher3.uioverrides.QuickstepLauncher", classLoader);
            Class<?> asyncViewClass = XposedHelpers.findClass("com.android.launcher3.util.AsyncView", classLoader);
            Class<?> clipIconViewClass = XposedHelpers.findClass("com.android.launcher3.views.ClipIconView", classLoader);

            XposedHelpers.findAndHookMethod(helperClass, "getFloatingIconView",
                launcherClass, View.class, asyncViewClass, asyncViewClass, boolean.class, RectF.class, boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.args[1] instanceof View) || !(param.args[5] instanceof RectF)) return;
                        View originalView = (View) param.args[1];
                        RectF positionOut = (RectF) param.args[5];

                        View targetView = resolveBubbleTextView(originalView);
                        if (targetView == null) targetView = originalView;

                        Context context = targetView.getContext();
                        if (context == null) return;

                        float scale = 1.0f;
                        if (isHotseatView(targetView)) {
                            int size = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100);
                            if (size > 0 && size != 100) scale = size / 100f;
                        } else if (isWorkspaceView(targetView) && isSettingEnabled(context, KEY_HOME_ENABLE)) {
                            int size = getIntSetting(context, KEY_HOME_ICON_SIZE, 100);
                            if (size > 0 && size != 100) scale = size / 100f;
                        }

                        if (scale != 1.0f) {
                            float cx = positionOut.centerX();
                            float cy = positionOut.centerY();
                            float newW = positionOut.width() * scale;
                            float newH = positionOut.height() * scale;
                            positionOut.set(cx - newW / 2, cy - newH / 2, cx + newW / 2, cy + newH / 2);
                        }
                    }
                });

            XposedBridge.hookAllMethods(clipIconViewClass, "update", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length < 6 || !(param.args[5] instanceof View)) return;
                        View floatingView = (View) param.args[5];
                        
                        Object originalIconObj = XposedHelpers.getObjectField(floatingView, "mOriginalIcon");
                        if (!(originalIconObj instanceof View)) return;
                        View originalIcon = (View) originalIconObj;
                        
                        View targetView = resolveBubbleTextView(originalIcon);
                        if (targetView == null) targetView = originalIcon;
                        
                        Context context = targetView.getContext();
                        if (context == null) return;

                        float scale = 1.0f;
                        if (isHotseatView(targetView)) {
                             int size = getIntSetting(context, KEY_HOTSEAT_ICON_SIZE, 100);
                             if (size > 0 && size != 100) scale = size / 100f;
                        } else if (isWorkspaceView(targetView) && isSettingEnabled(context, KEY_HOME_ENABLE)) {
                             int size = getIntSetting(context, KEY_HOME_ICON_SIZE, 100);
                             if (size > 0 && size != 100) scale = size / 100f;
                        }

                        if (scale == 1.0f) return;

                        if (!(param.args[0] instanceof RectF)) return;
                        RectF rectF = (RectF) param.args[0];
                        
                        if (!(floatingView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
                        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) floatingView.getLayoutParams();
                        
                        float fMin = Math.min(lp.width, lp.height);

                        if (fMin > 0) {
                            float intendedScale = rectF.width() / fMin;
                            floatingView.setScaleX(intendedScale);
                            floatingView.setScaleY(intendedScale);

                            Rect outline = (Rect) XposedHelpers.getObjectField(param.thisObject, "mOutline");
                            Rect finalBounds = (Rect) XposedHelpers.getObjectField(param.thisObject, "mFinalDrawableBounds");

                            if (outline != null) outline.set(0, 0, lp.width, lp.height);
                            if (finalBounds != null) {
                                Drawable bg = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mBackground");
                                Drawable fg = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mForeground");
                                if (bg != null) bg.setBounds(finalBounds);
                                if (fg != null) fg.setBounds(finalBounds);
                            }
                        }

                        Object clipView = param.thisObject;
                        boolean isAdaptive = XposedHelpers.getBooleanField(clipView, "mIsAdaptiveIcon");
                        if (isAdaptive) {
                            Drawable foreground = (Drawable) XposedHelpers.getObjectField(clipView, "mForeground");
                            Drawable background = (Drawable) XposedHelpers.getObjectField(clipView, "mBackground");
                            if (foreground != null && background != null) foreground.setBounds(background.getBounds());
                        }
                    } catch (Exception e) {}
                }
            });
        } catch (Throwable e) { /* ignore */ }
    }
    
    private void hookDockAnimationCorrection(ClassLoader classLoader) {
        try {
            Class<?> launcherClass = XposedHelpers.findClass("com.android.launcher3.Launcher", classLoader);
            
            XposedHelpers.findAndHookMethod(
                "com.android.launcher3.views.FloatingIconView",
                classLoader,
                "getLocationBoundsForView",
                launcherClass,
                View.class,
                boolean.class,
                RectF.class,
                Rect.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.args[0] instanceof Activity)) return;
                        Activity launcher = (Activity) param.args[0];
                        
                        if (getIntSetting(launcher, KEY_DOCK_ENABLE, 0) != 1) return;
                        int paddingDock = getIntSetting(launcher, KEY_PADDING_DOCK, 0);
                        if (paddingDock == 0) return;

                        if (!(param.args[1] instanceof View)) return;
                        View view = (View) param.args[1];
                        
                        if ((Boolean) param.args[2]) return; // only close animation

                        ViewGroup hotseat = null;
                        try {
                             Object hotseatObj = XposedHelpers.getObjectField(launcher, "mHotseat");
                             if (hotseatObj instanceof ViewGroup) hotseat = (ViewGroup) hotseatObj;
                        } catch (Throwable t) {}
                        
                        if (hotseat == null) return;
                        if (!isDescendantOf(view, hotseat)) return;

                        View dockIconsView = findHotseatCellLayout(hotseat);
                        if (dockIconsView == null) return;
                        
                        float offsetY = dockIconsView.getTranslationY();
                        if (offsetY == 0f) return;

                        if (param.args[3] instanceof RectF) {
                            RectF rectF = (RectF) param.args[3];
                            rectF.offset(0f, offsetY);
                        }
                    }
                }
            );

            try {
                Class<?> stableViewInfoClass = XposedHelpers.findClass("com.android.launcher3.util.StableViewInfo", classLoader);
                XposedHelpers.findAndHookMethod(launcherClass, "getFirstHomeElementForAppClose", stableViewInfoClass, String.class, UserHandle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!(param.thisObject instanceof Activity)) return;
                            Activity activity = (Activity) param.thisObject;
                            
                            if (getIntSetting(activity, KEY_DOCK_ENABLE, 0) != 1) return;
                            if (!isSettingEnabled(activity, KEY_HIDE_DOCK)) return;

                            if (!(param.getResult() instanceof View)) return;
                            View resultView = (View) param.getResult();
                            
                            ViewGroup hotseat = null;
                            try {
                                 Object hotseatObj = XposedHelpers.getObjectField(activity, "mHotseat");
                                 if (hotseatObj instanceof ViewGroup) hotseat = (ViewGroup) hotseatObj;
                            } catch (Throwable t) {}
                            
                            if (hotseat != null && isDescendantOf(resultView, hotseat)) param.setResult(null);
                        }
                    }
                );
            } catch (Throwable t) {}

        } catch (Throwable e) {
            logError("Failed to hook Dock animation logic", e);
        }
    }

    // =========================================================================
    // SECTION 4: WORKSPACE (Padding, Dots, Gestures, TopWidget)
    // =========================================================================

    private void hookWorkspace(Class<?> workspaceClass, ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(workspaceClass, "setInsets", Rect.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                View workspace = (View) param.thisObject;
                Context context = workspace.getContext();

                if (isDotsOffsetEnabled(context)) {
                    applyDotsOffset(workspace, context);
                }
            }
        });

        XposedHelpers.findAndHookMethod(workspaceClass, "bindAndInitFirstWorkspaceScreen", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                ViewGroup workspace = (ViewGroup) param.thisObject;
                Context context = workspace.getContext();
                if (getIntSetting(context, KEY_TOP_WIDGET_ENABLE, 0) == 1) {
                    try {
                        int childCount = workspace.getChildCount();
                        XposedHelpers.callMethod(workspace, "insertNewWorkspaceScreen", 0, childCount);
                    } catch (Exception e) { /* ignore */ }
                    return null;
                } else {
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                }
            }
        });
        
        try {
            Class<?> pageIndicatorDotsClass = XposedHelpers.findClass("com.android.launcher3.pageindicators.PageIndicatorDots", classLoader);
            XC_MethodHook dotsVisibleHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    View view = (View) param.thisObject;
                    Context context = view.getContext();
                    if (isDotsOffsetEnabled(context)) {
                        if (view.getVisibility() != View.VISIBLE) view.setVisibility(View.VISIBLE);
                        if (view.getAlpha() != 1f) view.setAlpha(1f);
                    }
                }
            };
            XposedHelpers.findAndHookMethod(pageIndicatorDotsClass, "onMeasure", int.class, int.class, dotsVisibleHook);
            XposedHelpers.findAndHookMethod(pageIndicatorDotsClass, "onDraw", Canvas.class, dotsVisibleHook);
        } catch (Throwable e) { /* ignore */ }
    }


    private void applyDT2SListener(Activity activity) {
        if (!isSettingEnabled(activity, KEY_DT2S_ENABLED)) return;

        try {
            View workspace = (View) XposedHelpers.getObjectField(activity, "mWorkspace");
            if (workspace != null) {
                if (workspace.getTag(TAG_DT2S_LISTENER) != null) return; // Prevent double hook

                final View.OnTouchListener originalListener = getExistingOnTouchListener(workspace);
                final Context context = activity;
                
                final GestureDetector gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (isSettingEnabled(context, KEY_DT2S_ENABLED)) {
                            android.util.Log.d("SleepLauncher", "DT2S Listener: onDoubleTap FIRED!");
                            performSleep(context);
                            return true;
                        }
                        return false;
                    }
                });
                
                gestureDetector.setIsLongpressEnabled(false);
                try {
                    if (doubleTapTimeoutField != null) {
                        int timeout = getIntSetting(context, KEY_DT2S_TIMEOUT, 250);
                        doubleTapTimeoutField.setInt(gestureDetector, timeout);
                    }
                } catch (Exception e) {}

                workspace.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        if (gestureDetector.onTouchEvent(event)) {
                            return true;
                        }
                        if (originalListener != null) {
                            return originalListener.onTouch(v, event);
                        }
                        return false;
                    }
                });
                
                workspace.setTag(TAG_DT2S_LISTENER, true);
                android.util.Log.d("SleepLauncher", "DT2S Proxy Listener attached to Workspace");
            }
        } catch (Throwable t) {
            logError("Failed to attach DT2S listener", t);
        }
    }

    private View.OnTouchListener getExistingOnTouchListener(View view) {
        try {
            Object listenerInfo = XposedHelpers.getObjectField(view, "mListenerInfo");
            if (listenerInfo != null) {
                return (View.OnTouchListener) XposedHelpers.getObjectField(listenerInfo, "mOnTouchListener");
            }
        } catch (Exception e) {}
        return null;
    }

    // =========================================================================
    // SECTION 5: PERSISTENCE & MIGRATION
    // =========================================================================

    private void hookPersistenceLogic(ClassLoader classLoader) {
        try {
            Class<?> logicClass = XposedHelpers.findClass("com.android.launcher3.model.GridSizeMigrationLogic", classLoader);
            final Class<?> itemsToPlaceClass = XposedHelpers.findClass("com.android.launcher3.model.GridSizeMigrationLogic$WorkspaceItemsToPlace", classLoader);
            final Class<?> occupancyClass = XposedHelpers.findClass("com.android.launcher3.util.GridOccupancy", classLoader);
            Class<?> dbReaderClass = XposedHelpers.findClass("com.android.launcher3.model.DbReader", classLoader);
            Class<?> dbHelperClass = XposedHelpers.findClass("com.android.launcher3.model.DatabaseHelper", classLoader);

            XposedHelpers.findAndHookMethod(logicClass, "migrateHotseat", int.class, int.class, dbReaderClass, dbReaderClass, dbHelperClass, List.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = getCurrentApplication();
                    if (getIntSetting(context, KEY_HOTSEAT_ICONS, 0) > 0) return null;
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                }
            });

            XposedHelpers.findAndHookMethod(logicClass, "solveGridPlacement", int.class, int.class, int.class, List.class, List.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    int screenId = (Integer) param.args[0];
                    int trgX = (Integer) param.args[1];
                    int trgY = (Integer) param.args[2];
                    List<Object> remaining = (List<Object>) param.args[3];
                    List<Object> placed = (List<Object>) param.args[4];

                    Context context = getCurrentApplication();
                    boolean allowTopRow = (context != null && getIntSetting(context, KEY_TOP_WIDGET_ENABLE, 0) == 1);

                    if (screenId == 0 && allowTopRow) {
                        Constructor<?> solutionConstructor = itemsToPlaceClass.getConstructor(List.class, List.class);
                        ArrayList<Object> placementSolution = new ArrayList<>();
                        Object result = solutionConstructor.newInstance(remaining, placementSolution);
                        
                        Constructor<?> occupancyConstructor = occupancyClass.getConstructor(int.class, int.class);
                        Object gridOccupancy = occupancyConstructor.newInstance(trgX, trgY);
                        Point nextEmptyCell = new Point(0, 0);

                        if (placed != null) {
                            for (Object dbEntry : placed) {
                                XposedHelpers.callMethod(gridOccupancy, "markCells", true, 
                                    XposedHelpers.getIntField(dbEntry, "cellX"), 
                                    XposedHelpers.getIntField(dbEntry, "cellY"), 
                                    XposedHelpers.getIntField(dbEntry, "spanX"), 
                                    XposedHelpers.getIntField(dbEntry, "spanY"));
                            }
                        }
                        Iterator<Object> it = remaining.iterator();
                        while (it.hasNext()) {
                            Object dbEntry = it.next();
                            int minSpanX = XposedHelpers.getIntField(dbEntry, "minSpanX");
                            int minSpanY = XposedHelpers.getIntField(dbEntry, "minSpanY");
                            if (minSpanX > trgX || minSpanY > trgY) { it.remove(); continue; }
                            
                            int foundCellX = -1; int foundCellY = -1;
                             for (int y = nextEmptyCell.y; y < trgY; y++) {
                                int x = (y == nextEmptyCell.y) ? nextEmptyCell.x : 0;
                                for (; x < trgX; x++) {
                                    if ((Boolean) XposedHelpers.callMethod(gridOccupancy, "isRegionVacant", x, y, minSpanX, minSpanY)) {
                                        foundCellX = x; foundCellY = y; break;
                                    }
                                }
                                if (foundCellX != -1) break;
                            }

                            if (foundCellX != -1) {
                                XposedHelpers.setIntField(dbEntry, "screenId", screenId);
                                XposedHelpers.setIntField(dbEntry, "cellX", foundCellX);
                                XposedHelpers.setIntField(dbEntry, "cellY", foundCellY);
                                XposedHelpers.setIntField(dbEntry, "spanX", minSpanX);
                                XposedHelpers.setIntField(dbEntry, "spanY", minSpanY);
                                XposedHelpers.callMethod(gridOccupancy, "markCells", true, foundCellX, foundCellY, minSpanX, minSpanY);
                                nextEmptyCell.set(foundCellX + minSpanX, foundCellY);
                                placementSolution.add(dbEntry);
                                it.remove();
                            }
                        }
                        return result;
                    } else {
                        return XposedBridge.invokeOriginalMethod(param.method, null, param.args);
                    }
                }
            });

            hookLoaderCursor(classLoader);
            hookModelWriter(classLoader);
            XposedHelpers.findAndHookMethod(occupancyClass, "isRegionVacant", int.class, int.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {

                    int y = (Integer) param.args[1];
                    if (y > 0) return; 
                    
                    if ((Boolean) param.getResult()) return;

                    Context context = getCurrentApplication();
                    if (context == null || getIntSetting(context, KEY_TOP_WIDGET_ENABLE, 0) != 1) return;

                    try {
                        int x = (Integer) param.args[0];
                        int spanX = (Integer) param.args[2];
                        int spanY = (Integer) param.args[3];
                        Object gridOccupancy = param.thisObject;
                        
                        boolean[][] cells = (boolean[][]) XposedHelpers.getObjectField(gridOccupancy, "cells");
                        int countX = XposedHelpers.getIntField(gridOccupancy, "mCountX");
                        int countY = XposedHelpers.getIntField(gridOccupancy, "mCountY");
                        int endX = x + spanX - 1;
                        int endY = y + spanY - 1;

                        if (x < 0 || y < 0 || endX >= countX || endY >= countY) return;

                        boolean wouldBeVacant = true;
                        for (int cx = x; cx <= endX; cx++) {
                            for (int cy = y; cy <= endY; cy++) {
                                if (cy > 0 && cells[cx][cy]) {
                                    wouldBeVacant = false;
                                    break;
                                }
                            }
                            if (!wouldBeVacant) break;
                        }

                        if (wouldBeVacant) {
                            param.setResult(true);
                        }
                    } catch (Throwable t) {
                        param.setResult(true);
                    }
                }
            });

        } catch (Throwable e) {
            logError("Failed to hook Persistence Logic", e);
        }
    }

    private void hookLoaderCursor(ClassLoader classLoader) {
        try {
            Class<?> loaderCursorClass = XposedHelpers.findClass("com.android.launcher3.model.LoaderCursor", classLoader);
            Class<?> itemInfoClass = XposedHelpers.findClass("com.android.launcher3.model.data.ItemInfo", classLoader);
            Class<?> intSparseArrayMapClass = XposedHelpers.findClass("com.android.launcher3.util.IntSparseArrayMap", classLoader);
            Class<?> loaderMemoryLoggerClass = XposedHelpers.findClass("com.android.launcher3.model.LoaderMemoryLogger", classLoader);

            XposedHelpers.findAndHookMethod(loaderCursorClass, "checkAndAddItem",
                itemInfoClass, intSparseArrayMapClass, loaderMemoryLoggerClass,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object cursor = param.thisObject;
                            Object item = param.args[0];
                            Context context = (Context) XposedHelpers.getObjectField(cursor, "mContext");

                            if (getIntSetting(context, KEY_TOP_WIDGET_ENABLE, 0) != 1) return;

                            int container = XposedHelpers.getIntField(item, "container");
                            if (container != -100) return; // Desktop

                            int screenId = XposedHelpers.getIntField(item, "screenId");
                            if (screenId != 0) return;

                            int cellY = XposedHelpers.getIntField(item, "cellY");
                            if (cellY != 0) return;

                            Object mOccupied = XposedHelpers.getObjectField(cursor, "mOccupied");
                            Object gridOccupancy = XposedHelpers.callMethod(mOccupied, "get", screenId);

                            if (gridOccupancy != null) {
                                int cellX = XposedHelpers.getIntField(item, "cellX");
                                int spanX = XposedHelpers.getIntField(item, "spanX");
                                int spanY = XposedHelpers.getIntField(item, "spanY");

                                XposedHelpers.callMethod(gridOccupancy, "markCells", false, cellX, cellY, spanX, spanY);

                                param.setObjectExtra("gridOccupancy", gridOccupancy);
                                param.setObjectExtra("cellX", cellX);
                                param.setObjectExtra("cellY", cellY);
                                param.setObjectExtra("spanX", spanX);
                                param.setObjectExtra("spanY", spanY);
                            }
                        } catch (Throwable e) { }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object gridOccupancy = param.getObjectExtra("gridOccupancy");
                            if (gridOccupancy != null) {
                                int cellX = (Integer) param.getObjectExtra("cellX");
                                int cellY = (Integer) param.getObjectExtra("cellY");
                                int spanX = (Integer) param.getObjectExtra("spanX");
                                int spanY = (Integer) param.getObjectExtra("spanY");

                                XposedHelpers.callMethod(gridOccupancy, "markCells", true, cellX, cellY, spanX, spanY);
                            }
                        } catch (Throwable e) { }
                    }
                }
            );
        } catch (Throwable e) {
            logError("Failed to hook LoaderCursor", e);
        }
    }

    private void hookModelWriter(ClassLoader classLoader) {
        try {
             Class<?> modelWriterClass = XposedHelpers.findClass("com.android.launcher3.model.ModelWriter", classLoader);
             Class<?> itemInfoClass = XposedHelpers.findClass("com.android.launcher3.model.data.ItemInfo", classLoader);
             
             XposedHelpers.findAndHookMethod(modelWriterClass, "addItemToDatabase", itemInfoClass, int.class, int.class, int.class, int.class, new XC_MethodHook() {
                 @Override protected void afterHookedMethod(MethodHookParam param) { 
                     saveWorkspaceItem((Context) XposedHelpers.getObjectField(param.thisObject, "mContext"), param.args[0]); 
                 }
             });
             
             XposedHelpers.findAndHookMethod(modelWriterClass, "modifyItemInDatabase", itemInfoClass, int.class, int.class, int.class, int.class, int.class, int.class, new XC_MethodHook() {
                 @Override protected void afterHookedMethod(MethodHookParam param) {
                     saveWorkspaceItem((Context) XposedHelpers.getObjectField(param.thisObject, "mContext"), param.args[0]);
                 }
             });
        } catch (Throwable e) {}
    }

    // =========================================================================
    // HELPER LOGIC IMPLEMENTATIONS
    // =========================================================================

    private void applyHomepagePadding(Activity activity) {
        if (getIntSetting(activity, KEY_DOCK_ENABLE, 0) != 1) return;
        
        int paddingSetting = getIntSetting(activity, KEY_PADDING_HOMEPAGE, SETTINGS_DEFAULT_PADDING);
        if (lastAppliedPaddingHash == paddingSetting) return;

        try {
            Object deviceProfile = XposedHelpers.getObjectField(activity, "mDeviceProfile");
            View workspace = (View) XposedHelpers.getObjectField(activity, "mWorkspace");
            ViewGroup hotseat = (ViewGroup) XposedHelpers.getObjectField(activity, "mHotseat");

            Rect paddingObj = null;
            try { paddingObj = (Rect) XposedHelpers.getObjectField(deviceProfile, "workspacePadding"); } catch(Throwable t) {}
            if (paddingObj == null) {
                Object workspaceProfile = XposedHelpers.getObjectField(deviceProfile, "mWorkspaceProfile");
                try { paddingObj = (Rect) XposedHelpers.getObjectField(workspaceProfile, "workspacePadding"); } catch(Throwable t) {}
            }

            if (paddingObj != null) {
                int desiredBottomPx;
                if (paddingSetting == SETTINGS_DEFAULT_PADDING) {
                    desiredBottomPx = (lastAppliedPaddingHash == 0) ? paddingObj.bottom : toPx(activity, LAUNCHER_ORIGINAL_BOTTOM_DP);
                } else {
                    desiredBottomPx = toPx(activity, paddingSetting + 20);
                }

                if (paddingObj.bottom != desiredBottomPx) {
                    paddingObj.bottom = desiredBottomPx;
                    triggerNativeUpdate(workspace, deviceProfile);
                    if (hotseat != null) triggerNativeUpdate(hotseat, deviceProfile);
                }
                lastAppliedPaddingHash = paddingSetting;
            }
        } catch (Throwable e) { /* ignore */ }
    }

    private void applyDockSettings(Activity activity) {
        if (getIntSetting(activity, KEY_DOCK_ENABLE, 0) != 1) return;

        boolean hideSearch = isSettingEnabled(activity, KEY_HIDE_SEARCH);
        boolean hideDock = isSettingEnabled(activity, KEY_HIDE_DOCK);
        int paddingDock = getIntSetting(activity, KEY_PADDING_DOCK, 0);
        int paddingSearch = getIntSetting(activity, KEY_PADDING_SEARCH, 0);

        String hashString = hideSearch + "|" + hideDock + "|" + paddingDock + "|" + paddingSearch;
        int currentHash = hashString.hashCode();
        if (lastAppliedDockHash == currentHash) return;

        try {
            ViewGroup hotseat = (ViewGroup) XposedHelpers.getObjectField(activity, "mHotseat");
            if (hotseat != null) {
                View qsbView = findQsbView(hotseat, activity);
                View dockIconsView = findHotseatCellLayout(hotseat);

                if (qsbView != null) {
                    float tY = (paddingSearch != 0) ? -toPx(activity, paddingSearch) : 0f;
                    enforceViewProperties(qsbView, hideSearch ? View.GONE : View.VISIBLE, tY, hideSearch);
                }
                if (dockIconsView != null) {
                    float tY = (paddingDock != 0) ? -toPx(activity, paddingDock) : 0f;
                    enforceViewProperties(dockIconsView, hideDock ? View.GONE : View.VISIBLE, tY, hideDock);
                }
            }
            lastAppliedDockHash = currentHash;
        } catch (Throwable e) { /* ignore */ }
    }

    private void applyDotsOffset(View workspace, Context context) {
        if (getIntSetting(context, KEY_DOCK_ENABLE, 0) != 1) return;

        int paddingDots = getIntSetting(context, KEY_PADDING_DOTS, DEFAULT_PADDING_DOTS);
        int paddingDotsX = getIntSetting(context, KEY_PADDING_DOTS_X, 0);
        
        int diffDp = paddingDots - DEFAULT_PADDING_DOTS;
        int diffPx = toPx(context, diffDp);
        int diffPxX = toPx(context, paddingDotsX);

        try {
            View pageIndicator = (View) XposedHelpers.getObjectField(workspace, "mPageIndicator");
            if (pageIndicator != null && pageIndicator.getParent() instanceof View) {
                View parent = (View) pageIndicator.getParent();
                if (parent.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) parent.getLayoutParams();
                    params.bottomMargin += diffPx; 
                    parent.setLayoutParams(params);
                    if (parent.getTranslationX() != diffPxX) parent.setTranslationX(diffPxX);
                }
            }
        } catch (Throwable e) { /* ignore */ }
    }

    private void handleWorkspaceView(TextView view, Object itemInfo) {
        try {
            int mDisplay = XposedHelpers.getIntField(view, "mDisplay");
            if (mDisplay != DISPLAY_WORKSPACE && mDisplay != DISPLAY_FOLDER) return;
            
            if (itemInfo != null) {
                int container = XposedHelpers.getIntField(itemInfo, "container");
                if (container == CONTAINER_HOTSEAT || container == CONTAINER_HOTSEAT_PREDICTION || container == -104) return;
            }
            applyWorkspaceIconSize(view, view.getContext());
            applyWorkspaceTextMode(view, view.getContext());
        } catch (Exception e) { }
    }

    private void applyWorkspaceIconSize(final View view, Context context) {
        int sizePercent = getIntSetting(context, KEY_HOME_ICON_SIZE, 100);
        if (sizePercent <= 0) return;
        
        view.setScaleX(1f);
        view.setScaleY(1f);

        if (view.getWidth() > 0) {
            updatePivot(view);
        }

        if (!layoutListeners.containsKey(view)) {
            View.OnLayoutChangeListener listener = new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    updatePivot(v);
                }
            };
            view.addOnLayoutChangeListener(listener);
            layoutListeners.put(view, listener);
        }
        view.post(new Runnable() { @Override public void run() { updatePivot(view); } });
    }

    private void applyWorkspaceTextMode(TextView view, Context context) {
        int mode = getIntSetting(context, KEY_HOME_TEXT_MODE, 0);
        if ((view.getText() == null || view.getText().length() == 0) && mode != 3) return;
        
        switch (mode) {
            case 1: view.setMaxLines(2); view.setEllipsize(TextUtils.TruncateAt.END); break;
            case 2: 
                view.setSingleLine(true); 
                view.setEllipsize(TextUtils.TruncateAt.MARQUEE); 
                view.setMarqueeRepeatLimit(-1);
                view.post(new Runnable() { @Override public void run() { view.setSelected(true); } });
                break;
            case 3: view.setText(""); break;
        }
    }

    private void updatePivot(View view) {
        try {
            if (view.getWidth() <= 0) return;
            if (view.getClass().getName().contains("BubbleTextView")) {
                Rect iconBounds = new Rect();
                XposedHelpers.callMethod(view, "getIconBounds", iconBounds);
                if (!iconBounds.isEmpty()) {
                    view.setPivotX(view.getWidth() / 2f);
                    view.setPivotY(iconBounds.centerY());
                }
            } else if (view.getClass().getName().contains("FolderIcon")) {
                view.setPivotX(view.getWidth() / 2f);
                Rect previewBounds = new Rect();
                XposedHelpers.callMethod(view, "getPreviewBounds", previewBounds);
                if (!previewBounds.isEmpty()) view.setPivotY(previewBounds.centerY());
            }
        } catch (Throwable e) {}
    }

    private void performSleep(Context context) {
        android.util.Log.d("SleepLauncher", "performSleep called.");

        try {
            Class<?> smClass = XposedHelpers.findClass("android.os.ServiceManager", null);
            Object binder = XposedHelpers.callStaticMethod(smClass, "getService", Context.POWER_SERVICE);
            
            if (binder != null) {
                Class<?> iPmClass = XposedHelpers.findClass("android.os.IPowerManager$Stub", null);
                Object iPm = XposedHelpers.callStaticMethod(iPmClass, "asInterface", binder);
                XposedHelpers.callMethod(iPm, "goToSleep", SystemClock.uptimeMillis(), 0, 0);
                return; 
            }
        } catch (Throwable t) { 
             android.util.Log.e("SleepLauncher", "IPowerManager failed", t);
        }

        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                XposedHelpers.callMethod(pm, "goToSleep", SystemClock.uptimeMillis());
                return;
            }
        } catch (Throwable t) { 
             android.util.Log.e("SleepLauncher", "PowerManager failed", t);
        }

        try {
            Intent intent = new Intent("org.pixel.customparts.ACTION_SLEEP");
            intent.setComponent(new ComponentName("org.pixel.customparts", "org.pixel.customparts.services.SleepReceiver"));
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND | 32);
            context.sendBroadcast(intent);
        } catch (Throwable e) {
            logError("Sleep failed", e);
        }
    }


    private void hookModelWriterAndLoader(ClassLoader classLoader) {
        try {
             Class<?> modelWriterClass = XposedHelpers.findClass("com.android.launcher3.model.ModelWriter", classLoader);
             Class<?> itemInfoClass = XposedHelpers.findClass("com.android.launcher3.model.data.ItemInfo", classLoader);
             
             XposedHelpers.findAndHookMethod(modelWriterClass, "addItemToDatabase", itemInfoClass, int.class, int.class, int.class, int.class, new XC_MethodHook() {
                 @Override protected void afterHookedMethod(MethodHookParam param) { saveWorkspaceItem((Context) XposedHelpers.getObjectField(param.thisObject, "mContext"), param.args[0]); }
             });
        } catch (Throwable e) {}
    }
    
    private void saveWorkspaceItem(Context context, Object item) {
        try {
             if (getIntSetting(context, KEY_TOP_WIDGET_ENABLE, 0) != 1) return;
        } catch(Throwable e) {}
    }

    private boolean isDotsOffsetEnabled(Context context) {
        if (getIntSetting(context, KEY_DOCK_ENABLE, 0) != 1) return false;
        return getIntSetting(context, KEY_PADDING_DOTS, DEFAULT_PADDING_DOTS) != DEFAULT_PADDING_DOTS || 
               getIntSetting(context, KEY_PADDING_DOTS_X, 0) != 0;
    }

    private void forceUpdateDots(Activity activity) {
        try {
            int resId = activity.getResources().getIdentifier("page_indicator", "id", activity.getPackageName());
            if (resId != 0) {
                View pageIndicator = activity.findViewById(resId);
                if (pageIndicator != null) {
                    pageIndicator.requestLayout();
                    if (pageIndicator.getParent() instanceof View) ((View) pageIndicator.getParent()).requestLayout();
                }
            }
        } catch (Exception e) {}
    }

    private void enforceViewProperties(final View view, final int visibility, final float translationY, final boolean forceHeightZero) {
        applyViewProps(view, visibility, translationY, forceHeightZero);
        
        Object oldListenerObj = view.getTag(TAG_VIEW_LISTENER);
        if (oldListenerObj instanceof View.OnLayoutChangeListener) {
            view.removeOnLayoutChangeListener((View.OnLayoutChangeListener) oldListenerObj);
        }
        
        View.OnLayoutChangeListener newListener = new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                applyViewProps(v, visibility, translationY, forceHeightZero);
            }
        };
        view.addOnLayoutChangeListener(newListener);
        view.setTag(TAG_VIEW_LISTENER, newListener);
    }

    private void applyViewProps(View view, int visibility, float translationY, boolean forceHeightZero) {
        if (view.getVisibility() != visibility) view.setVisibility(visibility);
        if (view.getTranslationY() != translationY) view.setTranslationY(translationY);

        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null) {
            if (forceHeightZero && params.height != 0) { params.height = 0; view.setLayoutParams(params); }
            else if (visibility == View.VISIBLE && params.height == 0) { params.height = ViewGroup.LayoutParams.WRAP_CONTENT; view.setLayoutParams(params); }
        }
    }

    private void triggerNativeUpdate(View view, Object deviceProfile) {
        try {
            Method method = XposedHelpers.findMethodExact(view.getClass(), "onDeviceProfileChanged", deviceProfile.getClass());
            method.invoke(view, deviceProfile);
        } catch (Throwable e) {}
    }

    private boolean isWorkspaceView(View view) {
        if (view.getParent() instanceof View) {
            View curr = (View) view.getParent();
            while (curr != null) {
                String name = curr.getClass().getName();
                if (name.contains("Hotseat")) return false;
                if (name.contains("Workspace") || name.contains("Folder")) return true;
                if (curr.getParent() instanceof View) curr = (View) curr.getParent();
                else break;
            }
        }
        Object tag = view.getTag();
        if (tag == null) return false;
        try {
            int container = XposedHelpers.getIntField(tag, "container");
            return container != CONTAINER_HOTSEAT && container != CONTAINER_HOTSEAT_PREDICTION && container != -104;
        } catch (Throwable e) { return false; }
    }

    private boolean isHotseatView(View view) {
        if (view.getParent() instanceof View) {
            View curr = (View) view.getParent();
            while (curr != null) {
                if (curr.getClass().getName().contains("Hotseat")) return true;
                if (curr.getParent() instanceof View) curr = (View) curr.getParent(); else break;
            }
            return false;
        }
        Object tag = view.getTag();
        if (tag == null) return false;
        try {
            int container = XposedHelpers.getIntField(tag, "container");
            return container == CONTAINER_HOTSEAT || container == CONTAINER_HOTSEAT_PREDICTION;
        } catch (Throwable e) { return false; }
    }

    private TextView resolveBubbleTextView(View view) {
        if (view instanceof TextView && view.getClass().getName().contains("BubbleTextView")) return (TextView) view;
        if (view.getClass().getName().contains("BubbleTextHolder")) {
            try { return (TextView) XposedHelpers.callMethod(view, "getBubbleText"); } catch (Throwable e) { return null; }
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof TextView && child.getClass().getName().contains("BubbleTextView")) return (TextView) child;
            }
        }
        return null;
    }
    
    private View findQsbView(ViewGroup hotseat, Context context) {
        try {
            Object qsbObj = XposedHelpers.getObjectField(hotseat, "mQsb");
            if (qsbObj instanceof View) return (View) qsbObj;
        } catch (Throwable e) {}
        
        String[] ids = {"search_container_hotseat", "qsb_container"};
        for (String name : ids) {
            int resId = context.getResources().getIdentifier(name, "id", context.getPackageName());
            if (resId != 0) {
                View v = hotseat.findViewById(resId);
                if (v != null) return v;
            }
        }
        return null;
    }

    private View findHotseatCellLayout(ViewGroup hotseat) {
        try {
            int resId = hotseat.getContext().getResources().getIdentifier("layout", "id", hotseat.getContext().getPackageName());
            if (resId != 0) {
                View v = hotseat.findViewById(resId);
                if (v != null) return v;
            }
        } catch (Throwable e) {}

        for (int i = 0; i < hotseat.getChildCount(); i++) {
            View child = hotseat.getChildAt(i);
            if (child instanceof ViewGroup && child.getClass().getName().contains("CellLayout")) return child;
        }
        
        for (int i = 0; i < hotseat.getChildCount(); i++) {
            View child = hotseat.getChildAt(i);
            if (child instanceof ViewGroup) return child;
        }
        return null;
    }

    private Context getContextFromIDP(Object idp) {
        try {
            Object displayController = XposedHelpers.getObjectField(idp, "mDisplayController");
            if (displayController != null) {
                Object appContext = XposedHelpers.getObjectField(displayController, "mAppContext");
                if (appContext instanceof Context) return (Context) appContext;
            }
        } catch (Exception e) {}
        return getCurrentApplication();
    }

    private Context getCurrentApplication() {
        try {
            Class<?> atClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            return (Context) XposedHelpers.callStaticMethod(atClass, "currentApplication");
        } catch (Throwable t) { return null; }
    }

    private void updateWindowProfiles(Object idp, int numIcons) {
        try {
            List<?> supportedProfiles = (List<?>) XposedHelpers.getObjectField(idp, "supportedProfiles");
            if (supportedProfiles != null) {
                for (Object dp : supportedProfiles) {
                    if (dp != null) XposedHelpers.setIntField(dp, "numShownHotseatIcons", numIcons);
                }
            }
        } catch (Throwable e) {}
    }

    private int toPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
    
    @Override
    public void onActivityDestroyed(Activity activity) {

    }
    
    private boolean isDescendantOf(View child, View parent) {
        Object current = child.getParent();
        while (current instanceof View) {
            if (current == parent) return true;
            current = ((View) current).getParent();
        }
        return false;
    }
}