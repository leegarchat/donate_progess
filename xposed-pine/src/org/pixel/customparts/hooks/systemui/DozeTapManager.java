package org.pixel.customparts.hooks.systemui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewConfiguration;

public class DozeTapManager {
    
    // Константы (Keys)
    public static final String KEY_HOOK = "doze_double_tap_hook";
    public static final String KEY_TIMEOUT = "doze_double_tap_timeout";
    public static final int DEFAULT_TIMEOUT = 400;

    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static boolean doubleTapPending = false;
    private static float lastTapX = -1f;
    private static float lastTapY = -1f;
    private static int doubleTapSlop = -1;

    private static final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            resetState();
        }
    };

    private static void resetState() {
        doubleTapPending = false;
        lastTapX = -1f;
        lastTapY = -1f;
    }

    public static boolean processTap(
            Context context,
            float x,
            float y,
            boolean isEnabled,
            int timeoutMs,
            Runnable resetSensorAction
    ) {
        if (!isEnabled) return false;

        if (doubleTapSlop < 0) {
            doubleTapSlop = ViewConfiguration.get(context).getScaledDoubleTapSlop();
        }

        if (!doubleTapPending) {
            doubleTapPending = true;
            lastTapX = x;
            lastTapY = y;

            handler.removeCallbacks(timeoutRunnable);
            handler.postDelayed(timeoutRunnable, timeoutMs);

            if (resetSensorAction != null) {
                resetSensorAction.run();
            }
            return true;
        }

        float dx = Math.abs(x - lastTapX);
        float dy = Math.abs(y - lastTapY);
        
        // Если координаты невалидны (например, -1), считаем что тапы рядом
        boolean invalidCoords = (x <= 0 && lastTapX <= 0);
        boolean isClose = invalidCoords || (dx < doubleTapSlop && dy < doubleTapSlop);

        if (isClose) {
            // Второй тап произошел быстро и рядом -> это двойной тап!
            // Мы возвращаем false, чтобы НЕ поглощать событие, 
            // позволяя системе обработать его как пробуждение.
            resetState();
            handler.removeCallbacks(timeoutRunnable);
            return false;
        } else {
            // Тап далеко от предыдущего -> начинаем ожидание заново
            lastTapX = x;
            lastTapY = y;

            handler.removeCallbacks(timeoutRunnable);
            handler.postDelayed(timeoutRunnable, timeoutMs);

            if (resetSensorAction != null) {
                resetSensorAction.run();
            }
            return true;
        }
    }
}