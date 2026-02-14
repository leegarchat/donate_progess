package org.pixel.customparts.pineinject;

import android.app.ActivityThread;
import android.app.Application;
import android.util.Log;
import org.pixel.customparts.manager.pine.HookEntry;
import top.canyie.pine.PineConfig;

public class ModEntry {
    private static final String TAG = "PineInject";

    public static void init() {
        PineConfig.debug = false;
        PineConfig.debuggable = false;

        try {
            try {
                System.load("/system/lib64/libpine.so");
            } catch (Throwable t) {
                System.load("/system/lib/libpine.so");
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load libpine.so", t);
            return;
        }

        Application app = ActivityThread.currentApplication();
        if (app == null) {
            return;
        }
        
        ClassLoader classLoader = app.getClassLoader();
        String packageName = app.getPackageName();

        try {
            HookEntry.init(app, classLoader, packageName);
        } catch (Throwable t) {
            Log.e(TAG, "HookEntry.init failed", t);
        }
    }
}