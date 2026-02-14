package org.pixel.customparts.hooks.systemui;

import android.content.Context;
import android.view.MotionEvent;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.pixel.customparts.core.BaseHook;

public class DozeTapShadeHook extends BaseHook {

    private boolean loggedHook = false;
    private int tapLogCount = 0;

    @Override
    public String getHookId() {
        return "DozeTapShadeHook";
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public boolean isEnabled(Context context) {
        return true;
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        hookPulsingGestureListener(classLoader);
    }

    private void hookPulsingGestureListener(ClassLoader classLoader) {
        try {
            Class<?> listenerClass = XposedHelpers.findClass(
                "com.android.systemui.shade.PulsingGestureListener",
                classLoader
            );
            
            XposedBridge.hookAllMethods(listenerClass, "onSingleTapUp", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!loggedHook) {
                            loggedHook = true;
                            log("DozeTapShadeHook: onSingleTapUp hooked (all overloads)");
                        }
                        
                        if (tapLogCount < 10) {
                            tapLogCount++;
                            log("DozeTapShadeHook: onSingleTapUp invoked (#" + tapLogCount + ")");
                        }

                        Object controller = XposedHelpers.getObjectField(param.thisObject, "statusBarStateController");
                        Boolean isDozingObj = (Boolean) XposedHelpers.callMethod(controller, "isDozing");
                        boolean isDozing = isDozingObj != null && isDozingObj;

                        if (tapLogCount <= 10) {
                            log("DozeTapShadeHook: isDozing=" + isDozing);
                        }

                        if (!isDozing) {
                            return;
                        }

                        Context context = resolveAppContext(param.thisObject.getClass().getClassLoader());
                        if (context == null) {
                            if (tapLogCount <= 10) {
                                log("DozeTapShadeHook: app context not available");
                            }
                            return;
                        }

                        boolean enabled = isSettingEnabled(context, DozeTapManager.KEY_HOOK);
                        if (tapLogCount <= 10) {
                            log("DozeTapShadeHook: enabled=" + enabled);
                        }
                        if (!enabled) {
                            return;
                        }

                        int timeout = getIntSetting(context, DozeTapManager.KEY_TIMEOUT, DozeTapManager.DEFAULT_TIMEOUT);
                        boolean consumed = false;

                        if (param.args.length == 1 && param.args[0] instanceof MotionEvent) {
                            MotionEvent event = (MotionEvent) param.args[0];
                            consumed = DozeTapManager.processTap(
                                context,
                                event.getX(),
                                event.getY(),
                                true,
                                timeout,
                                null
                            );
                        } else if (param.args.length >= 2 && param.args[0] instanceof Float && param.args[1] instanceof Float) {
                            consumed = DozeTapManager.processTap(
                                context,
                                (Float) param.args[0],
                                (Float) param.args[1],
                                true,
                                timeout,
                                null
                            );
                        } else {
                            if (tapLogCount <= 10) {
                                log("DozeTapShadeHook: unsupported args size=" + param.args.length);
                            }
                            return;
                        }

                        if (tapLogCount <= 10) {
                            log("DozeTapShadeHook: consumed=" + consumed);
                        }
                        
                        if (consumed) {
                            log("DozeTapShadeHook: Pulsing tap consumed");
                            param.setResult(true);
                        }

                    } catch (Throwable t) {
                        log("DozeTapShadeHook: Error in PulsingGestureListener: " + t.getMessage());
                    }
                }
            });

            log("DozeTapShadeHook: Hook applied successfully");
        } catch (Throwable e) {
            log("DozeTapShadeHook: Failed to apply hook: " + e.getMessage());
        }
    }

    private Context resolveAppContext(ClassLoader classLoader) {
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", classLoader);
            return (Context) XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication");
        } catch (Throwable t) {
            return null;
        }
    }
}