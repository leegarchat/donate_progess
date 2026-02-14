package org.pixel.customparts.core;

import android.app.Activity;
import android.content.Context;

public abstract class BaseHook {

    protected IHookEnvironment env;
    protected ClassLoader hostClassLoader;

    public abstract String getHookId();

    public int getPriority() {
        return 0;
    }

    public void setup(IHookEnvironment environment) {
        this.env = environment;
    }

    public void init(ClassLoader classLoader) {
        this.hostClassLoader = classLoader;
        
        try {
            onInit(classLoader);
        } catch (Throwable t) {
            logError("Error during initialization", t);
        }
    }

    protected void onInit(ClassLoader classLoader) {
    }

    public boolean isEnabled(Context context) {
        return true;
    }


    public void onActivityCreated(Activity activity) {}
    public void onActivityResumed(Activity activity) {}
    public void onActivityPaused(Activity activity) {}
    public void onActivityDestroyed(Activity activity) {}


    protected void log(String message) {
        if (env != null) {
            env.log(getHookId(), message);
        }
    }

    protected void logError(String message, Throwable t) {
        if (env != null) {
            env.logError(getHookId(), message, t);
        }
    }

    protected boolean isSettingEnabled(Context context, String key, boolean defaultValue) {
        if (env != null) {
            return env.isEnabled(context, key, defaultValue);
        }
        return defaultValue;
    }

    protected boolean isSettingEnabled(Context context, String key) {
        return isSettingEnabled(context, key, false);
    }

    protected int getIntSetting(Context context, String key, int defaultValue) {
        if (env != null) {
            return env.getInt(context, key, defaultValue);
        }
        return defaultValue;
    }

    protected float getFloatSetting(Context context, String key, float defaultValue) {
        if (env != null) {
            return env.getFloat(context, key, defaultValue);
        }
        return defaultValue;
    }
}