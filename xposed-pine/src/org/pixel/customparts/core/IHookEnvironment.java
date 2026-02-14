package org.pixel.customparts.core;

import android.content.Context;

public interface IHookEnvironment {
    boolean isEnabled(Context context, String key, boolean defaultValue);

    int getInt(Context context, String key, int defaultValue);

    float getFloat(Context context, String key, float defaultValue);

    void log(String tag, String message);

    void logError(String tag, String message, Throwable t);
}