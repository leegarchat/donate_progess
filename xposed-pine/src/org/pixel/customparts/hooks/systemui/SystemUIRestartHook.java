package org.pixel.customparts.hooks.systemui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Process;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import org.pixel.customparts.core.BaseHook;

public class SystemUIRestartHook extends BaseHook {

    private static final String ACTION_RESTART_SYSTEMUI = "org.pixel.customparts.action.RESTART_SYSTEMUI";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private boolean mReceiverRegistered = false;

    @Override
    public String getHookId() {
        return "systemui_restart_receiver";
    }

    @Override
    public void onInit(ClassLoader classLoader) {
        tryRegisterFromCurrentApplication();

        // We need a context to register the receiver.
        // Hooking into SystemUIApplication.onCreate provides the application context early on.
        try {
            Class<?> appClass = null;
            try {
                appClass = XposedHelpers.findClass("com.android.systemui.SystemUIApplication", classLoader);
            } catch (Throwable e) {
                 log("SystemUIRestartHook: SystemUIApplication class not found, skipping hook. This is expected on some ROMs.");
                 hookDependencyFallback(classLoader);
                 return;
            }
            
            XposedBridge.hookAllMethods(appClass, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mReceiverRegistered) return;
                    
                    Context context = (Context) param.thisObject;
                    registerReceiver(context);
                }
            });

            XposedBridge.hookAllMethods(appClass, "startServicesIfNeeded", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (mReceiverRegistered) return;
                    try {
                        Context context = (Context) param.thisObject;
                        registerReceiver(context);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) {
            logError("Failed to hook SystemUIApplication.onCreate", t);
            hookDependencyFallback(classLoader);
        }
    }

    private void hookDependencyFallback(ClassLoader classLoader) {
        // Fallback: Try hooking Dependency.initDependencies if SystemUIApplication is
        // not available/obfuscated differently.
        try {
            Class<?> dependencyClass = XposedHelpers.findClass("com.android.systemui.Dependency", classLoader);
            XposedBridge.hookAllMethods(dependencyClass, "initDependencies", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (mReceiverRegistered) return;
                    try {
                        Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                        registerReceiver(context);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    private void tryRegisterFromCurrentApplication() {
        if (mReceiverRegistered) return;
        try {
            Class<?> atClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object app = XposedHelpers.callStaticMethod(atClass, "currentApplication");
            if (app instanceof Context) {
                registerReceiver((Context) app);
            }
        } catch (Throwable ignored) {}
    }

    private void registerReceiver(Context context) {
        if (context == null || mReceiverRegistered) return;

        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        String packageName;
        try {
            packageName = appContext.getPackageName();
        } catch (Throwable t) {
            return;
        }
        if (!SYSTEMUI_PACKAGE.equals(packageName)) return;

        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_RESTART_SYSTEMUI.equals(intent.getAction())) {
                        log("SystemUIRestartHook: Restarting SystemUI via broadcast request");
                        Process.killProcess(Process.myPid());
                        System.exit(0);
                    }
                }
            };

            IntentFilter filter = new IntentFilter(ACTION_RESTART_SYSTEMUI);
            
            // Register with RECEIVER_EXPORTED (0x2) for Android 14+ if possible, reflectively
            try {
                java.lang.reflect.Method registerMethod = Context.class.getMethod("registerReceiver", BroadcastReceiver.class, IntentFilter.class, int.class);
                registerMethod.invoke(appContext, receiver, filter, 2); // 2 = RECEIVER_EXPORTED
            } catch (Throwable t) {
                // Fallback for older Android versions
                appContext.registerReceiver(receiver, filter);
            }

            mReceiverRegistered = true;
            log("SystemUIRestartHook: Receiver registered successfully");
        } catch (Throwable t) {
            logError("Failed to register SystemUI restart receiver", t);
        }
    }
}
