package org.pixel.customparts.hooks;

import android.content.Context;
import org.pixel.customparts.core.BaseHook;

// Предполагается, что EdgeEffectHook - это существующий Java/Kotlin класс.
// Если EdgeEffectHook.kt тоже есть и вызывает проблемы, его тоже нужно переписать,
// но пока переписываем Wrapper.

public class EdgeEffectHookWrapper extends BaseHook {

    private static final String KEY_ENABLED = "overscroll_enabled";

    // Свойства с дефолтными значениями
    private String keySuffix = "_xposed";
    private boolean useGlobalSettings = true;

    @Override
    public String getHookId() {
        return "EdgeEffectHook";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    // Сеттеры обязательны для конфигурации извне (как это делает Kotlin property)
    public void setKeySuffix(String keySuffix) {
        this.keySuffix = keySuffix;
    }

    public void setUseGlobalSettings(boolean useGlobalSettings) {
        this.useGlobalSettings = useGlobalSettings;
    }

    @Override
    public boolean isEnabled(Context context) {
        if (context == null) return true;
        return isSettingEnabled(context, KEY_ENABLED, true);
    }

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            // Вызов статических методов EdgeEffectHook.
            // Убедитесь, что EdgeEffectHook.java существует или EdgeEffectHook.kt доступен.
            // Если EdgeEffectHook - это объект (object) в Kotlin, то в Java к нему обращаются через EdgeEffectHook.INSTANCE
            // Но судя по вызову в исходнике, там @JvmStatic или companion object методы.
            
            EdgeEffectHook.configure(useGlobalSettings, keySuffix);
            EdgeEffectHook.initWithClassLoader(classLoader);
            
            log("EdgeEffect hook initialized (suffix=" + keySuffix + ", global=" + useGlobalSettings + ")");
        } catch (Throwable e) {
            logError("Failed to initialize EdgeEffect hook", e);
        }
    }
}