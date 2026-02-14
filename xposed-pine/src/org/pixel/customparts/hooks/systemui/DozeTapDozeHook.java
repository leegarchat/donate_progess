package org.pixel.customparts.hooks.systemui;

import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.pixel.customparts.core.BaseHook;

import java.lang.reflect.Method;
import java.util.Collection;

public class DozeTapDozeHook extends BaseHook {
    
    private int dozeTapReason = 9; // Default fallback

    @Override
    public String getHookId() {
        return "DozeTapDozeHook";
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
        resolveTapReason(classLoader);
        hookDozeTriggers(classLoader);
    }

    private void resolveTapReason(ClassLoader classLoader) {
        try {
            Class<?> dozeLogClass = XposedHelpers.findClass("com.android.systemui.doze.DozeLog", classLoader);
            dozeTapReason = XposedHelpers.getStaticIntField(dozeLogClass, "REASON_SENSOR_TAP");
        } catch (Throwable t) {
            log("DozeTapDozeHook: Using default REASON_SENSOR_TAP = " + dozeTapReason);
        }
    }

    private void hookDozeTriggers(ClassLoader classLoader) {
        try {
            Class<?> dozeTriggersClass = XposedHelpers.findClass(
                "com.android.systemui.doze.DozeTriggers",
                classLoader
            );

            XposedBridge.hookAllMethods(dozeTriggersClass, "onSensor", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        // Ищем первый аргумент типа Int (pulseReason)
                        int pulseReason = -1;
                        for (Object arg : param.args) {
                            if (arg instanceof Integer) {
                                pulseReason = (Integer) arg;
                                break;
                            }
                        }

                        if (pulseReason != dozeTapReason) return;

                        Object dozeTriggers = param.thisObject;
                        Context context = (Context) XposedHelpers.getObjectField(dozeTriggers, "mContext");
                        if (context == null) return;
                        
                        if (!isSettingEnabled(context, DozeTapManager.KEY_HOOK)) return;

                        // Ищем float аргументы (x, y)
                        float screenX = -1f;
                        float screenY = -1f;
                        int floatCount = 0;
                        
                        for (Object arg : param.args) {
                            if (arg instanceof Float) {
                                if (floatCount == 0) screenX = (Float) arg;
                                else if (floatCount == 1) screenY = (Float) arg;
                                floatCount++;
                            }
                        }

                        if (floatCount < 2) return;

                        int timeout = getIntSetting(context, DozeTapManager.KEY_TIMEOUT, DozeTapManager.DEFAULT_TIMEOUT);
                        
                        boolean consumed = DozeTapManager.processTap(
                            context,
                            screenX,
                            screenY,
                            true,
                            timeout,
                            new Runnable() {
                                @Override
                                public void run() {
                                    reregisterTapSensor(dozeTriggers);
                                }
                            }
                        );

                        if (consumed) {
                            param.setResult(null); // Блокируем выполнение оригинального метода
                        }
                    } catch (Throwable t) {
                        logError("DozeTapDozeHook: Error in onSensor", t);
                    }
                }
            });

            log("DozeTapDozeHook: Hook applied successfully");
        } catch (Throwable e) {
            logError("DozeTapDozeHook: Failed to apply hook", e);
        }
    }

    private void reregisterTapSensor(Object dozeTriggers) {
        try {
            Object dozeSensors = XposedHelpers.getObjectField(dozeTriggers, "mDozeSensors");
            if (dozeSensors == null) return;
            
            Object triggerSensors = XposedHelpers.getObjectField(dozeSensors, "mTriggerSensors");
            if (triggerSensors == null) return;

            if (triggerSensors instanceof Object[]) {
                for (Object sensor : (Object[]) triggerSensors) {
                    if (sensor != null) checkAndResetSensor(sensor);
                }
            } else if (triggerSensors instanceof Collection) {
                for (Object sensor : (Collection<?>) triggerSensors) {
                    if (sensor != null) checkAndResetSensor(sensor);
                }
            }
        } catch (Throwable t) {
             logError("DozeTapDozeHook: reregisterTapSensor failed", t);
        }
    }

    private void checkAndResetSensor(Object sensor) {
        try {
            int reason = XposedHelpers.getIntField(sensor, "mPulseReason");
            if (reason == dozeTapReason) {
                Method method = sensor.getClass().getDeclaredMethod("setListening", boolean.class);
                method.setAccessible(true);
                method.invoke(sensor, false);
                method.invoke(sensor, true);
                log("DozeTapDozeHook: Sensor re-registered OK");
            }
        } catch (Throwable e) {
            log("DozeTapDozeHook: checkAndResetSensor failed: " + e.getMessage());
            try {
                // Fallback method
                XposedHelpers.setBooleanField(sensor, "mRequested", false);
                XposedHelpers.callMethod(sensor, "updateListening");
                XposedHelpers.setBooleanField(sensor, "mRequested", true);
                XposedHelpers.callMethod(sensor, "updateListening");
                log("DozeTapDozeHook: Sensor re-registered via fallback OK");
            } catch (Throwable e2) {
                log("DozeTapDozeHook: Fallback re-register also failed: " + e2.getMessage());
            }
        }
    }
}