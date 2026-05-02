package org.pixel.customparts.manager.pine;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.pixel.customparts.core.IHookEnvironment;

public class PineEnvironment implements IHookEnvironment {

    private static final String TAG_PREFIX = "PineInject";
    private static final String SUFFIX = "_pine";
    private static final String XPOSED_SUFFIX = "_xposed";

    private String resolveKey(String key) {
        if (key.endsWith(SUFFIX)) return key;
        if (key.endsWith(XPOSED_SUFFIX)) {
            return key.substring(0, key.length() - XPOSED_SUFFIX.length()) + SUFFIX;
        }
        return key + SUFFIX;
    }

    @Override
    public boolean isEnabled(Context context, String key, boolean def) {
        if (context == null) return def;
        String finalKey = resolveKey(key);
        try {
            int defaultValue = def ? 1 : 0;
            return Settings.Global.getInt(context.getContentResolver(), finalKey, defaultValue) != 0;
        } catch (Throwable t) {
            logError("Env", "Failed to read boolean setting " + finalKey, t);
            return def;
        }
    }

    @Override
    public int getInt(Context context, String key, int def) {
        if (context == null) return def;
        String finalKey = resolveKey(key);
        try {
            return Settings.Global.getInt(context.getContentResolver(), finalKey, def);
        } catch (Throwable t) {
            logError("Env", "Failed to read int setting " + finalKey, t);
            return def;
        }
    }

    @Override
    public float getFloat(Context context, String key, float def) {
        if (context == null) return def;
        String finalKey = resolveKey(key);
        try {
            return Settings.Global.getFloat(context.getContentResolver(), finalKey, def);
        } catch (Throwable t) {
            // Фолбэк: пробуем прочитать как int и поделить на 100 (для совместимости со старыми твиками)
            try {
                int intValue = Settings.Global.getInt(
                        context.getContentResolver(),
                        finalKey,
                        (int) (def * 100)
                );
                return intValue / 100f;
            } catch (Throwable inner) {
                logError("Env", "Failed to read float setting " + finalKey, inner);
                return def;
            }
        }
    }

    @Override
    public String getString(Context context, String key, String def) {
        if (context == null) return def;
        String finalKey = resolveKey(key);
        try {
            String val = Settings.Global.getString(context.getContentResolver(), finalKey);
            return val != null ? val : def;
        } catch (Throwable t) {
            logError("Env", "Failed to read string setting " + finalKey, t);
            return def;
        }
    }

    @Override
    public void log(String tag, String message) {
        Log.d(TAG_PREFIX, "[" + tag + "] " + message);
    }

    @Override
    public void logError(String tag, String message, Throwable t) {
        Log.e(TAG_PREFIX, "[" + tag + "] ERROR: " + message, t);
    }
}