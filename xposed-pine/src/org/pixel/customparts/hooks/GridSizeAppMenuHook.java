package org.pixel.customparts.hooks;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.pixel.customparts.core.BaseHook;

public class GridSizeAppMenuHook extends BaseHook {

    private static final String CLASS_INVARIANT_DEVICE_PROFILE = "com.android.launcher3.InvariantDeviceProfile";
    private static final String CLASS_DEVICE_PROFILE_BUILDER = "com.android.launcher3.DeviceProfile$Builder";
    private static final String CLASS_BUBBLE_TEXT_VIEW = "com.android.launcher3.BubbleTextView";
    private static final String CLASS_ALPHABETICAL_APPS_LIST = "com.android.launcher3.allapps.AlphabeticalAppsList";
    private static final String CLASS_PREDICTION_ROW_VIEW = "com.android.launcher3.appprediction.PredictionRowView";

    private static final String KEY_MENU_ENABLE = "launcher_menupage_sizer";
    private static final String KEY_MENU_COLS = "launcher_menupage_h";
    private static final String KEY_MENU_ROW_HEIGHT = "launcher_menupage_row_height";
    private static final String KEY_MENU_ICON_SIZE = "launcher_menupage_icon_size";
    private static final String KEY_MENU_TEXT_MODE = "launcher_menupage_text_mode";

    private static final String KEY_SUGGESTION_ICON_SIZE = "launcher_suggestion_icon_size";
    private static final String KEY_SUGGESTION_TEXT_MODE = "launcher_suggestion_text_mode";
    private static final String KEY_SUGGESTION_DISABLE = "launcher_suggestion_disable";

    private static final String KEY_SEARCH_ICON_SIZE = "launcher_search_icon_size";
    private static final String KEY_SEARCH_TEXT_MODE = "launcher_search_text_mode";

    private static final int DISPLAY_ALL_APPS = 1;
    private static final int DISPLAY_SEARCH_RESULT = 6;
    private static final int DISPLAY_SEARCH_RESULT_SMALL = 7;
    private static final int DISPLAY_PREDICTION = 8;
    private static final int DISPLAY_SEARCH_RESULT_APP_ROW = 9;

    private static final String EXTRA_BASE_ICON_SIZE = "cpr_base_icon_size";

    @Override
    public String getHookId() {
        return "GridSizeAppMenuHook";
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public boolean isEnabled(Context context) {
        return isSettingEnabled(context, KEY_MENU_ENABLE);
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            hookInvariantDeviceProfile(classLoader);
            hookDeviceProfileBuilder(classLoader);
            hookAlphabeticalAppsList(classLoader);
            hookBubbleTextView(classLoader);
            hookPredictionRowView(classLoader);
            log("AppMenu grid hooks installed");
        } catch (Throwable e) {
            logError("Failed to initialize AppMenu grid hooks", e);
        }
    }

    private void hookInvariantDeviceProfile(ClassLoader classLoader) {
        try {
            Class<?> idpClass = XposedHelpers.findClass(CLASS_INVARIANT_DEVICE_PROFILE, classLoader);

            XposedBridge.hookAllMethods(
                idpClass,
                "initGrid",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object idp = param.thisObject;
                        Context context = null;

                        // Ищем Context среди аргументов
                        for (Object arg : param.args) {
                            if (arg instanceof Context) {
                                context = (Context) arg;
                                break;
                            }
                        }

                        if (context == null) {
                            context = getContextFromIDP(idp);
                        }

                        if (context != null) {
                            applyMenuGridSettingsToIDP(idp, context);
                        }
                    }
                }
            );
        } catch (Throwable e) {
            logError("Failed to hook InvariantDeviceProfile", e);
        }
    }

    private void hookDeviceProfileBuilder(ClassLoader classLoader) {
        try {
            Class<?> builderClass = XposedHelpers.findClass(CLASS_DEVICE_PROFILE_BUILDER, classLoader);
            XposedHelpers.findAndHookMethod(
                builderClass,
                "build",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object deviceProfile = param.getResult();
                        if (deviceProfile == null) return;
                        
                        Context context = getContextFromDP(deviceProfile);
                        if (context == null) return;
                        
                        applyAllAppsSettings(deviceProfile, context);
                    }
                }
            );
        } catch (Throwable e) {
            logError("Failed to hook DeviceProfile.Builder", e);
        }
    }

    private void hookAlphabeticalAppsList(ClassLoader classLoader) {
        try {
            Class<?> appsListClass = XposedHelpers.findClass(CLASS_ALPHABETICAL_APPS_LIST, classLoader);
            XposedBridge.hookAllConstructors(appsListClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object appsList = param.thisObject;
                    Context context = getContextFromAppsList(appsList);
                    if (context == null) return;
                    
                    if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return;
                    
                    int cols = getIntSetting(context, KEY_MENU_COLS, 0);
                    if (cols > 0) {
                        XposedHelpers.setIntField(appsList, "mNumAppsPerRowAllApps", cols);
                        log("AlphabeticalAppsList columns set to " + cols);
                    }
                }
            });
        } catch (Throwable e) {
            logError("Failed to hook AlphabeticalAppsList", e);
        }
    }

    private void hookBubbleTextView(ClassLoader classLoader) {
        try {
            Class<?> bubbleTextViewClass = XposedHelpers.findClass(CLASS_BUBBLE_TEXT_VIEW, classLoader);

            for (Method method : bubbleTextViewClass.getDeclaredMethods()) {
                if (method.getName().equals("applyIconAndLabel")) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!(param.thisObject instanceof TextView)) return;
                            TextView view = (TextView) param.thisObject;
                            Context context = view.getContext();
                            if (context != null) {
                                handleAllAppsView(view, context);
                            }
                        }
                    });
                }
            }
        } catch (Throwable e) {
            logError("Failed to hook BubbleTextView", e);
        }
    }

    private void hookPredictionRowView(ClassLoader classLoader) {
        try {
            Class<?> predictionRowClass = XposedHelpers.findClass(CLASS_PREDICTION_ROW_VIEW, classLoader);
            XposedHelpers.findAndHookMethod(
                predictionRowClass,
                "setPredictedApps",
                List.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!(param.thisObject instanceof View)) return;
                        View view = (View) param.thisObject;
                        Context context = view.getContext();
                        if (context == null) return;
                        
                        if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return;
                        if (getIntSetting(context, KEY_SUGGESTION_DISABLE, 0) == 1) {
                            param.args[0] = Collections.emptyList();
                        }
                    }
                }
            );
        } catch (Throwable e) {
            logError("Failed to hook PredictionRowView", e);
        }
    }

    private void applyMenuGridSettingsToIDP(Object idp, Context context) {
        if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return;
        int menuCols = getIntSetting(context, KEY_MENU_COLS, 0);
        if (menuCols > 0) {
            XposedHelpers.setIntField(idp, "numAllAppsColumns", menuCols);
            XposedHelpers.setIntField(idp, "numDatabaseAllAppsColumns", menuCols);
            log("AllApps columns in IDP set to " + menuCols);
        }
    }

    private void applyAllAppsSettings(Object deviceProfile, Context context) {
        if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return;
        
        int menuCols = getIntSetting(context, KEY_MENU_COLS, 0);
        int rowHeightRaw = getIntSetting(context, KEY_MENU_ROW_HEIGHT, 100);
        
        if (menuCols > 0) {
            XposedHelpers.setIntField(deviceProfile, "numShownAllAppsColumns", menuCols);
        }
        
        if (rowHeightRaw != 100 && rowHeightRaw > 0) {
            float scale = rowHeightRaw / 100f;
            Object allAppsProfile = XposedHelpers.getObjectField(deviceProfile, "mAllAppsProfile");
            if (allAppsProfile != null) {
                int currentHeight = XposedHelpers.getIntField(allAppsProfile, "cellHeightPx");
                if (currentHeight > 0) {
                    int newHeight = (int) (currentHeight * scale);
                    XposedHelpers.setIntField(allAppsProfile, "cellHeightPx", newHeight);
                    log("AllApps cell height scaled to " + newHeight + " (" + rowHeightRaw + "%)");
                }
            }
        }
    }

    private void handleAllAppsView(TextView view, Context context) {
        try {
            if (!isSettingEnabled(context, KEY_MENU_ENABLE)) return;
            
            int mDisplay = XposedHelpers.getIntField(view, "mDisplay");
            
            String keyIconSize = null;
            String keyTextMode = null;
            
            switch (mDisplay) {
                case DISPLAY_ALL_APPS:
                    keyIconSize = KEY_MENU_ICON_SIZE;
                    keyTextMode = KEY_MENU_TEXT_MODE;
                    break;
                case DISPLAY_PREDICTION:
                    keyIconSize = KEY_SUGGESTION_ICON_SIZE;
                    keyTextMode = KEY_SUGGESTION_TEXT_MODE;
                    break;
                case DISPLAY_SEARCH_RESULT:
                case DISPLAY_SEARCH_RESULT_SMALL:
                case DISPLAY_SEARCH_RESULT_APP_ROW:
                    keyIconSize = KEY_SEARCH_ICON_SIZE;
                    keyTextMode = KEY_SEARCH_TEXT_MODE;
                    break;
                default:
                    return;
            }

            if (keyIconSize != null) {
                applyIconSize(view, context, keyIconSize);
            }

            if (keyTextMode != null) {
                applyTextMode(view, context, keyTextMode);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void applyIconSize(TextView view, Context context, String keySize) {
        try {
            int sizePercent = getIntSetting(context, keySize, 100);
            int currentSize = XposedHelpers.getIntField(view, "mIconSize");
            
            Object storedBaseSizeObj = XposedHelpers.getAdditionalInstanceField(view, EXTRA_BASE_ICON_SIZE);
            Integer storedBaseSize = (storedBaseSizeObj instanceof Integer) ? (Integer) storedBaseSizeObj : null;
            
            int baseSize = (storedBaseSize != null) ? storedBaseSize : currentSize;
            
            if (storedBaseSize == null) {
                XposedHelpers.setAdditionalInstanceField(view, EXTRA_BASE_ICON_SIZE, baseSize);
            }

            int newSize;
            if (sizePercent == 100 || sizePercent <= 0) {
                newSize = baseSize;
            } else {
                newSize = Math.max(1, (int) (baseSize * sizePercent / 100f));
            }

            XposedHelpers.setIntField(view, "mIconSize", newSize);

            Drawable[] drawables = view.getCompoundDrawables();
            Drawable topDrawable = drawables[1];
            if (topDrawable != null) {
                topDrawable.setBounds(0, 0, newSize, newSize);
                view.setCompoundDrawables(drawables[0], topDrawable, drawables[2], drawables[3]);
            }
        } catch (Throwable e) {
            // ignore
        }
    }

    private void applyTextMode(final TextView view, Context context, String keyMode) {
        int mode = getIntSetting(context, keyMode, 0);
        
        if ((view.getText() == null || view.getText().length() == 0) && mode != 3) return;

        switch (mode) {
            case 0: // Default
                break;
            case 1: // 2 lines
                view.setMaxLines(2);
                view.setEllipsize(TextUtils.TruncateAt.END);
                break;
            case 2: // Marquee
                view.setSingleLine(true);
                view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                view.setMarqueeRepeatLimit(-1);
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            view.setSelected(true);
                        } catch (Throwable t) {}
                    }
                });
                break;
            case 3: // Hidden
                view.setText("");
                break;
        }
    }

    private Context getContextFromIDP(Object idp) {
        try {
            Object displayController = XposedHelpers.getObjectField(idp, "mDisplayController");
            if (displayController != null) {
                Object appContext = XposedHelpers.getObjectField(displayController, "mAppContext");
                if (appContext instanceof Context) return (Context) appContext;
            }
            return getCurrentApplication();
        } catch (Exception e) {
            return getCurrentApplication();
        }
    }

    private Context getContextFromDP(Object dp) {
        try {
            Object info = XposedHelpers.getObjectField(dp, "mInfo");
            if (info != null) {
                Object context = XposedHelpers.getObjectField(info, "context");
                if (context instanceof Context) return (Context) context;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Context getContextFromAppsList(Object appsList) {
        try {
            Object activityContext = XposedHelpers.getObjectField(appsList, "mActivityContext");
            if (activityContext != null) {
                Method asContext = activityContext.getClass().getMethod("asContext");
                return (Context) asContext.invoke(activityContext);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Context getCurrentApplication() {
        try {
            Class<?> atClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            return (Context) XposedHelpers.callStaticMethod(atClass, "currentApplication");
        } catch (Throwable t) {
            return null;
        }
    }
}