package org.pixel.customparts.manager.pine;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.pixel.customparts.core.IHookEnvironment;

public class PineEnvironment implements IHookEnvironment {

    private static final String TAG_PREFIX = "PineInject";
    private static final String SUFFIX = "_pine";

    private String resolveKey(String key) {
        // Логика: очищаем от старых суффиксов, если они есть, и добавляем _pine
        String baseKey = key.replace("_xposed", "").replace("_pine", "");
        return baseKey + SUFFIX;
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
    public void log(String tag, String message) {
        Log.d(TAG_PREFIX, "[" + tag + "] " + message);
    }

    @Override
    public void logError(String tag, String message, Throwable t) {
        Log.e(TAG_PREFIX, "[" + tag + "] ERROR: " + message, t);
    }
}