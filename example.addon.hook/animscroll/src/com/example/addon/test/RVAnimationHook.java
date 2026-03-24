package com.example.addon.test;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

import org.pixel.customparts.core.IAddonHook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class RVAnimationHook implements IAddonHook {

    private static final String TAG = "PineInject";
    
    private boolean hasAttemptedHook = false; 
    private final Set<String> hookedAdapters = new HashSet<>();
    
    // Кэш для быстрого доступа к полям без рефлексии в каждом кадре (спасает от лагов)
    private final Map<Class<?>, Field> itemViewFieldsCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Field> rvFieldsCache = new ConcurrentHashMap<>();
    private Method getScrollStateMethodCache = null;

    @Override
    public String getId() {
        return "rv_scroll_animation";
    }

    @Override
    public String getName() {
        return "RecyclerView Scroll Animation";
    }

    @Override
    public String getAuthor() {
        return "liuqiang (Port)";
    }

    @Override
    public String getDescription() {
        return "Добавляет пружинящую анимацию появления списков";
    }

    @Override
    public String getVersion() {
        return "2.0-Bulletproof";
    }

    @Override
    public Set<String> getTargetPackages() {
        return null; 
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public boolean isEnabled(Context context) {
        return true;
    }

    @Override
    public void handleLoadPackage(Context context, ClassLoader classLoader, String packageName) {
        Log.d(TAG, "[RVAnim] === Инициализация аддона для: " + packageName + " ===");

        int durationTmp = 333;
        float scaleTmp = 0.2f;
        try {
            durationTmp = Settings.Global.getInt(context.getContentResolver(), "rv_anim_duration", 333);
            scaleTmp = Settings.Global.getFloat(context.getContentResolver(), "rv_anim_start_scale", 0.2f);
        } catch (Throwable t) {
            Log.w(TAG, "[RVAnim] Ошибка чтения настроек, используем дефолтные");
        }
        
        final int animDuration = durationTmp;
        final float startScale = scaleTmp;

        // Главный хук анимации (срабатывает при каждом появлении элемента)
        final XC_MethodHook bindViewHolderHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Object viewHolder = param.args[0];
                    if (viewHolder == null) return;

                    Class<?> vhClass = viewHolder.getClass();
                    Field itemViewField = getItemViewField(vhClass);
                    Field rvField = getRecyclerViewField(vhClass);

                    if (itemViewField != null && rvField != null) {
                        View itemView = (View) itemViewField.get(viewHolder);
                        Object rv = rvField.get(viewHolder);

                        if (itemView != null && rv != null) {
                            itemView.clearAnimation();

                            if (getScrollStateMethodCache == null) {
                                getScrollStateMethodCache = XposedHelpers.findMethodExactIfExists(rv.getClass(), "getScrollState");
                            }

                            int state = 0;
                            if (getScrollStateMethodCache != null) {
                                state = (int) getScrollStateMethodCache.invoke(rv);
                            } else {
                                state = (int) XposedHelpers.callMethod(rv, "getScrollState");
                            }

                            // 0 = SCROLL_STATE_IDLE
                            if (state != 0) {
                                ScaleAnimation anim = new ScaleAnimation(
                                        startScale, 1f, 
                                        startScale, 1f, 
                                        Animation.RELATIVE_TO_SELF, 0.5f, 
                                        Animation.RELATIVE_TO_SELF, 0.5f
                                );
                                anim.setDuration(animDuration);
                                itemView.startAnimation(anim);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Молча подавляем ошибки
                }
            }
        };

        // Откладываем поиск до момента создания первой Activity
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (hasAttemptedHook) return;
                hasAttemptedHook = true; 

                Activity activity = (Activity) param.thisObject;
                ClassLoader finalCL = activity.getClassLoader();

                try {
                    // 1. Ищем RecyclerView
                    Class<?> recyclerViewClass = XposedHelpers.findClassIfExists("androidx.recyclerview.widget.RecyclerView", finalCL);
                    if (recyclerViewClass == null) {
                        recyclerViewClass = XposedHelpers.findClassIfExists("android.support.v7.widget.RecyclerView", finalCL);
                    }
                    
                    if (recyclerViewClass == null) {
                        Log.d(TAG, "[RVAnim] ПРОПУСК: RecyclerView не используется в " + packageName);
                        return;
                    }
                    Log.d(TAG, "[RVAnim] Шаг 1: RecyclerView найден!");

                    // 2. Хукаем публичный метод setAdapter
                    // Это обходит любую обфускацию R8, так как метод setAdapter является публичным API
                    XC_MethodHook setAdapterHook = new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object adapter = param.args[0];
                            if (adapter == null) return;
                            
                            Class<?> adapterClazz = adapter.getClass();
                            if (hookedAdapters.contains(adapterClazz.getName())) return;
                            hookedAdapters.add(adapterClazz.getName());
                            
                            Log.d(TAG, "[RVAnim] Шаг 2: Перехвачен Адаптер: " + adapterClazz.getName());
                            
                            // 3. Динамически ищем и хукаем методы bindViewHolder в адаптере
                            Class<?> current = adapterClazz;
                            boolean hookedAny = false;
                            
                            while (current != null && !current.getName().equals("java.lang.Object")) {
                                for (Method m : current.getDeclaredMethods()) {
                                    Class<?>[] pTypes = m.getParameterTypes();
                                    // Ищем методы: void bindViewHolder(ViewHolder, int, ...)
                                    if (m.getReturnType() == void.class && pTypes.length >= 2 && pTypes[1] == int.class) {
                                        // Проверяем, что первый аргумент не примитив и не базовая Java строка
                                        if (!pTypes[0].isPrimitive() && !pTypes[0].isArray() && !pTypes[0].getName().startsWith("java.")) {
                                            XposedBridge.hookMethod(m, bindViewHolderHook);
                                            hookedAny = true;
                                        }
                                    }
                                }
                                current = current.getSuperclass();
                            }
                            
                            if (hookedAny) {
                                Log.d(TAG, "[RVAnim] === УСПЕХ: Анимация прикреплена к адаптеру! ===");
                            }
                        }
                    };

                    // Ставим ловушки на методы привязки адаптеров
                    XposedBridge.hookAllMethods(recyclerViewClass, "setAdapter", setAdapterHook);
                    XposedBridge.hookAllMethods(recyclerViewClass, "swapAdapter", setAdapterHook);
                    
                    Log.d(TAG, "[RVAnim] Ожидание инициализации списков...");

                } catch (Throwable t) {
                    Log.e(TAG, "[RVAnim] Ошибка инициализации в " + packageName, t);
                }
            }
        });
    }

    // --- Вспомогательные методы с кэшированием ---

    private Field getItemViewField(Class<?> clazz) {
        if (itemViewFieldsCache.containsKey(clazz)) return itemViewFieldsCache.get(clazz);
        
        Class<?> current = clazz;
        while (current != null && !current.getName().equals("java.lang.Object")) {
            for (Field f : current.getDeclaredFields()) {
                if (f.getType() == View.class) {
                    f.setAccessible(true);
                    itemViewFieldsCache.put(clazz, f);
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        return null; 
    }

    private Field getRecyclerViewField(Class<?> clazz) {
        if (rvFieldsCache.containsKey(clazz)) return rvFieldsCache.get(clazz);
        
        Class<?> current = clazz;
        while (current != null && !current.getName().equals("java.lang.Object")) {
            for (Field f : current.getDeclaredFields()) {
                if (f.getType().getName().contains("RecyclerView")) {
                    f.setAccessible(true);
                    rvFieldsCache.put(clazz, f);
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        return null; 
    }
}