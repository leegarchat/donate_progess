package org.pixel.customparts.hooks.systemui;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.text.TextUtils;

import android.content.Intent;
import android.content.IntentFilter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.WeakHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import org.pixel.customparts.core.BaseHook;

public class KeyguardBatteryPowerHook extends BaseHook {

    private String mSuffix = "_xposed";

    private String getKey(String base) {
        return base + mSuffix;
    }

    private static final String KEY_BATTERY_INFO_ENABLE_BASE = "pixelparts_battery_info_enable";
    private static final String KEY_SHOW_WATTAGE_BASE = "pixelparts_battery_info_show_wattage";
    private static final String KEY_SHOW_VOLTAGE_BASE = "pixelparts_battery_info_show_voltage";
    private static final String KEY_SHOW_CURRENT_BASE = "pixelparts_battery_info_show_current";
    private static final String KEY_SHOW_TEMP_BASE = "pixelparts_battery_info_show_temp";
    private static final String KEY_SHOW_PERCENT_BASE = "pixelparts_battery_info_show_percent";
    private static final String KEY_SHOW_STANDARD_STRING_BASE = "pixelparts_battery_info_show_standard_string";
    private static final String KEY_SHOW_CUSTOM_SYMBOL_BASE = "pixelparts_battery_info_show_custom_symbol";
    private static final String KEY_CUSTOM_SYMBOL_BASE = "pixelparts_battery_info_custom_symbol";
    private static final String KEY_REFRESH_INTERVAL_MS_BASE = "pixelparts_battery_info_refresh_interval_ms";
    private static final String KEY_AVERAGE_MODE_BASE = "pixelparts_battery_info_average_mode";

    private static final String[] SYS_CURRENT_UA_PATHS = {
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/battery/current_avg",
        "/sys/class/power_supply/battery/batt_current_ua_now",
        "/sys/class/power_supply/battery/BatteryAverageCurrent"
    };
    private static final String[] SYS_VOLTAGE_UV_PATHS = {
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/battery/batt_volt",
        "/sys/class/power_supply/battery/voltage_ocv"
    };
    private static final String[] SYS_TEMP_PATHS = {
        "/sys/class/power_supply/battery/temp"
    };

    private static final long DEFAULT_REFRESH_INTERVAL_MS = 1000L;
    private static final long MIN_REFRESH_INTERVAL_MS = 100L;
    private static final long MAX_REFRESH_INTERVAL_MS = 5000L;
    private static final long INTERNAL_AVERAGE_SAMPLE_INTERVAL_MS = 100L;
    private static final long MIN_AVERAGE_OUTPUT_INTERVAL_MS = 2000L;

    private final WeakHashMap<Object, Runnable> mRefreshRunnables = new WeakHashMap<>();

    private volatile long mRefreshIntervalMs = DEFAULT_REFRESH_INTERVAL_MS;

    private volatile boolean mSettingsLoaded;
    private volatile boolean mMasterEnabled = true;
    private volatile boolean mShowWattage = true;
    private volatile boolean mShowVoltage = true;
    private volatile boolean mShowCurrent = true;
    private volatile boolean mShowTemp = true;
    private volatile boolean mShowPercent = false;
    private volatile boolean mShowStandardString = true;
    private volatile boolean mShowCustomSymbol = true;
    private volatile boolean mAverageModeEnabled = false;
    private volatile String mCustomSymbol = "⚡";

    private volatile String mCachedChargingWireless;
    private volatile String mCachedChargingFast;
    private volatile String mCachedChargingSlow;
    private volatile String mCachedChargingNormal;

    private volatile ChargeSnapshot mLatestSnapshot;
    private volatile long mLatestSnapshotUptime;

    private final Object mAveragingLock = new Object();
    private double mAvgCurrentMaSum;
    private double mAvgVoltageVSum;
    private double mAvgPowerWSum;
    private double mAvgTempCSum;
    private int mAvgSamples;
    private long mAvgWindowStartUptime;

    private final Object mSensorLoopLock = new Object();
    private HandlerThread mSensorThread;
    private Handler mSensorHandler;
    private Runnable mSensorSampler;
    private volatile boolean mSensorLoopActive;

    private volatile String mResolvedCurrentPath;
    private volatile String mResolvedVoltagePath;
    private volatile String mResolvedTempPath;

    @Override
    public String getHookId() {
        return "KeyguardBatteryPowerHook";
    }

    @Override
    public int getPriority() {
        return 65;
    }

    @Override
    public boolean isEnabled(Context context) {
        return true;
    }

    private boolean mReceiverRegistered = false;

    @Override
    protected void onInit(ClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method getMethod = clazz.getMethod("get", String.class, String.class);
            String value = (String) getMethod.invoke(null, "pixelparts_xposed_to_pine", "0");
            if ("1".equals(value)) {
                 mSuffix = "_pine";
                //  log("KeyguardBatteryPowerHook: Overriding suffix to _pine");
            }
        } catch (Throwable t) {}
        
        hookKeyguardIndicationController(classLoader);
        registerRestartReceiver(classLoader);
    }

    private void registerRestartReceiver(ClassLoader classLoader) {
        try {
            Class<?> dependencyClass = XposedHelpers.findClass("com.android.systemui.Dependency", classLoader);
            
             XposedBridge.hookAllMethods(dependencyClass, "initDependencies", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext"); // Dependency is static usually?
                         // Dependency.initDependencies(Context) usually.
                         // Actually, easiest way to get context in SystemUI is hooking SystemUIApplication.onCreate or similar. 
                         // But we are already in KeyguardBatteryPowerHook.
                         // We can register receiver when we have context in hookKeyguardIndicationController.
                    } catch (Throwable t) {
                    }
                }
            });
            
        } catch (Throwable t) {
            
        }
    }


    private void hookKeyguardIndicationController(ClassLoader classLoader) {
        try {
            Class<?> controllerClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.KeyguardIndicationController",
                    classLoader
            );

            // 1. Hook computePowerIndication to return our custom string directly
            XposedBridge.hookAllMethods(controllerClass, "computePowerIndication", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object controller = param.thisObject;
                        boolean pluggedIn = XposedHelpers.getBooleanField(controller, "mPowerPluggedIn");
                        
                        if (pluggedIn) {
                            Context context = (Context) XposedHelpers.getObjectField(controller, "mContext");
                            ensureSettingsLoaded(context);

                            if (!mMasterEnabled) {
                                return; // Don't override
                            }

                            ChargeSnapshot snapshot = mLatestSnapshot;
                            if (snapshot != null) {
                                String customIndication = formatChargingString(context, controller, snapshot);
                                param.setResult(customIndication);
                            } else {
                                startSensorLoop(context);
                            }
                        }
                    } catch (Throwable t) {
                        // logError("Error in computePowerIndication hook", t);
                    }
                }
            });

            // 2. Keep the periodic refresh to force updates, but call updateDeviceEntryIndication on the controller
            // We use the BaseKeyguardCallback just to get a reference to the controller initially if needed, 
            // OR we can hook the constructor of the Controller to get the reference and start the timer.
            // Let's stick to the callback hook for getting the controller reference for now, but also add logging.

            Class<?> callbackClass = XposedHelpers.findClass(
                    "com.android.systemui.statusbar.KeyguardIndicationController$BaseKeyguardCallback",
                    classLoader
            );

            XposedBridge.hookAllMethods(callbackClass, "onRefreshBatteryInfo", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                     // log("KeyguardBatteryPowerHook: onRefreshBatteryInfo called");
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object callback = param.thisObject;
                        Object controller = XposedHelpers.getObjectField(callback, "this$0");
                        if (controller == null) return;

                        boolean pluggedIn = XposedHelpers.getBooleanField(controller, "mPowerPluggedIn");
                        Context context = (Context) XposedHelpers.getObjectField(controller, "mContext");
                        ensureSettingsLoaded(context);
                        reloadDynamicSettings(context);
                        
                        if (pluggedIn && mMasterEnabled) {
                            startSensorLoop(context);
                            startPeriodicRefresh(controller, context);
                        } else {
                            stopPeriodicRefresh(controller);
                            stopSensorLoopIfUnused();
                        }
                    } catch (Throwable t) {
                        // logError("Error in onRefreshBatteryInfo hook", t);
                    }
                }
            });

            // log("KeyguardBatteryPowerHook: Hooks initialized");
        } catch (Throwable t) {
            // logError("Failed to hook KeyguardIndicationController", t);
        }
    }

    private void ensureSettingsLoaded(Context context) {
        if (mSettingsLoaded || context == null) return;
        synchronized (this) {
            if (mSettingsLoaded) return;

            mMasterEnabled = isSettingEnabled(context, KEY_BATTERY_INFO_ENABLE_BASE, true);
            mShowWattage = isSettingEnabled(context, KEY_SHOW_WATTAGE_BASE, true);
            mShowVoltage = isSettingEnabled(context, KEY_SHOW_VOLTAGE_BASE, true);
            mShowCurrent = isSettingEnabled(context, KEY_SHOW_CURRENT_BASE, true);
            mShowTemp = isSettingEnabled(context, KEY_SHOW_TEMP_BASE, true);
            mShowPercent = isSettingEnabled(context, KEY_SHOW_PERCENT_BASE, false);
            mShowStandardString = isSettingEnabled(context, KEY_SHOW_STANDARD_STRING_BASE, true);
            mShowCustomSymbol = isSettingEnabled(context, KEY_SHOW_CUSTOM_SYMBOL_BASE, true);
            mAverageModeEnabled = isSettingEnabled(context, KEY_AVERAGE_MODE_BASE, false);

            String symbol = getSystemString(context.getContentResolver(), getKey(KEY_CUSTOM_SYMBOL_BASE));
            if (TextUtils.isEmpty(symbol)) symbol = "⚡";
            mCustomSymbol = symbol;

                int intervalMs = getIntSetting(context, KEY_REFRESH_INTERVAL_MS_BASE, (int) DEFAULT_REFRESH_INTERVAL_MS);
            mRefreshIntervalMs = clamp(intervalMs, MIN_REFRESH_INTERVAL_MS, MAX_REFRESH_INTERVAL_MS);

            mCachedChargingWireless = getCleanString(context, "keyguard_plugged_in_wireless");
            mCachedChargingFast = getCleanString(context, "keyguard_plugged_in_charging_fast");
            mCachedChargingSlow = getCleanString(context, "keyguard_plugged_in_charging_slowly");
            mCachedChargingNormal = getCleanString(context, "keyguard_plugged_in");

            resetAveragingWindow();

            mSettingsLoaded = true;
        }
    }

    private void reloadDynamicSettings(Context context) {
        if (context == null) return;
        int intervalMs = getIntSetting(context, KEY_REFRESH_INTERVAL_MS_BASE, (int) DEFAULT_REFRESH_INTERVAL_MS);
        mRefreshIntervalMs = clamp(intervalMs, MIN_REFRESH_INTERVAL_MS, MAX_REFRESH_INTERVAL_MS);

        mAverageModeEnabled = isSettingEnabled(context, KEY_AVERAGE_MODE_BASE, mAverageModeEnabled);
    }

    private String formatChargingString(Context context, Object controller, ChargeSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();

        // 1. Localized Charging Type String (Only, no percentages)
        if (mShowStandardString) {
             try {
                String chargingTypeString = null;
                boolean wireless = XposedHelpers.getBooleanField(controller, "mPowerPluggedInWireless");
                
                if (wireless) {
                     chargingTypeString = mCachedChargingWireless;
                } else {
                    double power = snapshot.powerWRounded;
                    if (power > 15) {
                        chargingTypeString = mCachedChargingFast;
                    } else if (power < 7) {
                        chargingTypeString = mCachedChargingSlow;
                    } else {
                        // Regular charging (7-15W)
                        chargingTypeString = mCachedChargingNormal;
                    }
                }

                if (chargingTypeString != null && !chargingTypeString.isEmpty()) {
                     sb.append(chargingTypeString).append(" ");
                }
             } catch (Throwable t) {
                 // log("KeyguardBatteryPowerHook: Error determining charging string: " + t);
             }
        }

        // 2. Custom Symbol or Default Lightning
        if (mShowCustomSymbol) {
             sb.append(mCustomSymbol).append("\n");
        } else if (sb.length() > 0) {
            sb.append("\n");
        }

        // 3. Components
        boolean firstComponentAdded = false;

        if (mShowWattage) {
            sb.append(String.format(Locale.US, "%.2f W", snapshot.powerWRounded));
            firstComponentAdded = true;
        }

        if (mShowVoltage) {
            if (firstComponentAdded) sb.append(" • ");
            sb.append(String.format(Locale.US, "%.2f V", snapshot.voltageVRounded));
            firstComponentAdded = true;
        }

        if (mShowCurrent) {
            if (firstComponentAdded) sb.append(" • ");
            sb.append(String.format(Locale.US, "%.0f mA", snapshot.currentMaRounded));
            firstComponentAdded = true;
        }
        
        if (mShowTemp) {
             if (firstComponentAdded) sb.append(" • ");
            sb.append(String.format(Locale.US, "%.1f°C", snapshot.temperatureCelsius));
            firstComponentAdded = true;
        }
        
        if (mShowPercent) {
             if (firstComponentAdded) sb.append(" • ");
             
             // Get percentage from controller field mBatteryLevel or BatteryManager
             try {
                 BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                 int level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                 sb.append(level).append("%");
                 firstComponentAdded = true;
             } catch (Throwable t) {
             }
        }

        return sb.toString();
    }

    private String getCleanString(Context context, String name) {
        try {
            int resId = context.getResources().getIdentifier(name, "string", "com.android.systemui");
            if (resId == 0) resId = context.getResources().getIdentifier(name, "string", "android");
            
            if (resId != 0) {
                String raw = context.getString(resId);
                // Remove formatting placeholders like %s, %1$s, etc.
                // Usually these strings are like "Charging (%d%%)" or "Fast charging (%s)"
                // We want to keep just the text part.
                
                // Remove content in parenthesis if it contains %
                String cleaned = raw.replaceAll("\\(.*?%[a-zA-Z].*?\\)", "").trim();
                
                // Also remove standalone placeholders if any
                 cleaned = cleaned.replaceAll("%[0-9$]*[a-zA-Z%]", "").trim();
                 
                 // Remove leading non-letter characters like bullets, dots, etc.
                 // This handles cases like "• Wireless charging"
                 while (cleaned.length() > 0 && !Character.isLetterOrDigit(cleaned.charAt(0))) {
                     cleaned = cleaned.substring(1).trim();
                 }

                return cleaned;
            }
        } catch (Throwable t) {
        }
        return null;
    }


    private ChargeSnapshot readChargeSnapshotFromSys(Context context) {
        Long currentRaw = null;
        Long voltageRaw = null;
        float temperatureCelsius = 0f;

        final boolean needCurrent = mShowCurrent || mShowWattage || mShowStandardString;
        final boolean needVoltage = mShowVoltage || mShowWattage || mShowStandardString;
        final boolean needTemp = mShowTemp;

        // Try BatteryManager API first
        if (context != null && (needCurrent || needVoltage || needTemp)) {
            try {
                BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
                if (bm != null && needCurrent) {
                    long currentBm = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                    
                    if (currentBm != Long.MIN_VALUE && currentBm != Integer.MIN_VALUE) { 
                        currentRaw = currentBm;
                        // log("KeyguardBatteryPowerHook: Read current from BatteryManager property: " + currentBm);
                    }
                }
                
                // Fallback or primary for voltage: sticky intent
                if (needVoltage || needTemp) {
                    Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    if (intent != null) {
                        if (needVoltage) {
                            int voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                            if (voltageMv > 0) {
                                voltageRaw = (long) voltageMv * 1000L; // Convert mV to uV common base
                            }
                        }
                        if (needTemp) {
                            int tempInt = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                            if (tempInt > 0) {
                                temperatureCelsius = tempInt / 10.0f;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                // log("KeyguardBatteryPowerHook: Error reading from BatteryManager/Intent: " + t.getMessage());
            }
        }

        if (needCurrent && (currentRaw == null || currentRaw == 0)) {
             currentRaw = readFirstLongFromPaths(SYS_CURRENT_UA_PATHS);
             if (currentRaw == null) {
                // log("KeyguardBatteryPowerHook: Failed to find valid current path from options");
            }
        }
        
        if (needVoltage && (voltageRaw == null || voltageRaw == 0)) {
            voltageRaw = readFirstLongFromPaths(SYS_VOLTAGE_UV_PATHS);
            if (voltageRaw == null) {
                // log("KeyguardBatteryPowerHook: Failed to find valid voltage path from options");
            }
        }
        
        if (needTemp && temperatureCelsius <= 0.1f) {
            Long tempRaw = readFirstLongFromPaths(SYS_TEMP_PATHS);
            if (tempRaw != null) {
                temperatureCelsius = tempRaw / 10.0f;
                //  log("KeyguardBatteryPowerHook: Read temp from sysfs: " + tempRaw + " (" + temperatureCelsius + "C)");
            }
        }

        if ((needCurrent && currentRaw == null) || (needVoltage && voltageRaw == null)) {
            return null;
        }

        long currentUa = needCurrent ? normalizeCurrentToUa(currentRaw) : 0L;
        long voltageUv = needVoltage ? normalizeVoltageToUv(voltageRaw) : 0L;

        if ((needCurrent && currentUa <= 0) || (needVoltage && voltageUv <= 0)) {
            // log("KeyguardBatteryPowerHook: Found values <= 0. Current: " + currentUa + ", Voltage: " + voltageUv);
            return null;
        }

        // log("KeyguardBatteryPowerHook: Read currentRaw=" + currentRaw + " (norm=" + currentUa + "uA), voltageRaw=" + voltageRaw + " (norm=" + voltageUv + "uV)");

        double currentMa = needCurrent ? currentUa / 1000d : 0d;
        double voltageV = needVoltage ? voltageUv / 1_000_000d : 0d;
        double powerW = currentMa * voltageV / 1000d;

        double currentMaRounded = round2(currentMa);
        double voltageVRounded = round2(voltageV);
        double powerWRounded = round2(powerW);

        int microWatt = (int) Math.min(
                Math.round(powerWRounded * 1_000_000d),
                Integer.MAX_VALUE
        );

        return new ChargeSnapshot(
                (int) Math.min(currentUa, Integer.MAX_VALUE),
                (int) Math.min(voltageUv, Integer.MAX_VALUE),
                currentMaRounded,
                voltageVRounded,
                powerWRounded,
                microWatt,
                temperatureCelsius
        );
    }

    private Long readFirstLongFromPaths(String[] paths) {
        String resolvedPath = null;
        if (paths == SYS_CURRENT_UA_PATHS) resolvedPath = mResolvedCurrentPath;
        else if (paths == SYS_VOLTAGE_UV_PATHS) resolvedPath = mResolvedVoltagePath;
        else if (paths == SYS_TEMP_PATHS) resolvedPath = mResolvedTempPath;

        if (!TextUtils.isEmpty(resolvedPath)) {
            Long resolvedValue = readLongFromFile(resolvedPath);
            if (resolvedValue != null) return resolvedValue;
        }

        for (String path : paths) {
            Long value = readLongFromFile(path);
            if (value != null) {
                if (paths == SYS_CURRENT_UA_PATHS) mResolvedCurrentPath = path;
                else if (paths == SYS_VOLTAGE_UV_PATHS) mResolvedVoltagePath = path;
                else if (paths == SYS_TEMP_PATHS) mResolvedTempPath = path;
                return value;
            } else {
                // log("KeyguardBatteryPowerHook: Failed to read from " + path);
            }
        }
        return null;
    }

    private Long readLongFromFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            String value = line.trim();
            if (value.isEmpty()) {
                return null;
            }
            return Long.parseLong(value);
        } catch (Throwable t) {
            // log("KeyguardBatteryPowerHook: Error reading " + path + ": " + t.getMessage());
            return null;
        }
    }

    private long normalizeCurrentToUa(long rawValue) {
        long abs = Math.abs(rawValue);
        if (abs >= 1_000_000_000L) {
            return abs / 1000L;
        }
        if (abs >= 10_000L) {
            return abs;
        }
        return abs * 1000L;
    }

    private long normalizeVoltageToUv(long rawValue) {
        long abs = Math.abs(rawValue);
        if (abs >= 1_000_000L) {
            return abs;
        }
        if (abs >= 1_000L) {
            return abs * 1000L;
        }
        return abs * 1_000_000L;
    }

    private double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }


    private void startPeriodicRefresh(Object controller, Context context) {
        try {
            Handler handler = (Handler) XposedHelpers.getObjectField(controller, "mHandler");
            if (handler == null) {
                return;
            }

            synchronized (mRefreshRunnables) {
                Runnable existing = mRefreshRunnables.get(controller);
                if (existing != null) {
                    return;
                }

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            boolean pluggedIn = XposedHelpers.getBooleanField(controller,
                                    "mPowerPluggedIn");
                            if (!pluggedIn) {
                                stopPeriodicRefresh(controller);
                                return;
                            }
                            // Just poke the update method; the computePowerIndication hook will handle reading.
                            XposedHelpers.callMethod(controller, "updateDeviceEntryIndication", false);
                        } catch (Throwable ignored) {
                        }

                        synchronized (mRefreshRunnables) {
                            Runnable self = mRefreshRunnables.get(controller);
                            if (self != null) {
                                handler.postDelayed(self, getUiRefreshIntervalMs());
                            }
                        }
                    }
                };

                mRefreshRunnables.put(controller, runnable);
                handler.postDelayed(runnable, getUiRefreshIntervalMs());
            }
        } catch (Throwable ignored) {
        }
    }


    private void stopPeriodicRefresh(Object controller) {
        try {
            Handler handler = (Handler) XposedHelpers.getObjectField(controller, "mHandler");
            if (handler == null) {
                return;
            }

            synchronized (mRefreshRunnables) {
                Runnable runnable = mRefreshRunnables.remove(controller);
                if (runnable != null) {
                    handler.removeCallbacks(runnable);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void startSensorLoop(Context context) {
        if (context == null || !mMasterEnabled) return;
        synchronized (mSensorLoopLock) {
            if (mSensorThread == null) {
                mSensorThread = new HandlerThread("PP-BatterySampler");
                mSensorThread.start();
                mSensorHandler = new Handler(mSensorThread.getLooper());
            }
            if (mSensorLoopActive) return;

            mSensorLoopActive = true;
            if (mSensorSampler == null) {
                mSensorSampler = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ChargeSnapshot snapshot = readChargeSnapshotFromSys(context.getApplicationContext());
                            if (snapshot != null) {
                                onSensorSample(snapshot);
                            }
                        } catch (Throwable ignored) {
                        }

                        synchronized (mSensorLoopLock) {
                            if (mSensorLoopActive && mSensorHandler != null) {
                                mSensorHandler.postDelayed(this, getInternalSampleIntervalMs());
                            }
                        }
                    }
                };
            }

            mSensorHandler.removeCallbacks(mSensorSampler);
            mSensorHandler.post(mSensorSampler);
        }
    }

    private void stopSensorLoopIfUnused() {
        synchronized (mSensorLoopLock) {
            if (!mRefreshRunnables.isEmpty()) return;
            mSensorLoopActive = false;
            if (mSensorHandler != null && mSensorSampler != null) {
                mSensorHandler.removeCallbacks(mSensorSampler);
            }
        }
    }

    private long clamp(long value, long min, long max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private long getInternalSampleIntervalMs() {
        if (mAverageModeEnabled) return INTERNAL_AVERAGE_SAMPLE_INTERVAL_MS;
        return mRefreshIntervalMs;
    }

    private long getUiRefreshIntervalMs() {
        if (!mAverageModeEnabled) return mRefreshIntervalMs;
        return Math.max(MIN_AVERAGE_OUTPUT_INTERVAL_MS, Math.max(1000L, mRefreshIntervalMs));
    }

    private void onSensorSample(ChargeSnapshot sample) {
        if (!mAverageModeEnabled) {
            mLatestSnapshot = sample;
            mLatestSnapshotUptime = android.os.SystemClock.uptimeMillis();
            return;
        }

        long now = android.os.SystemClock.uptimeMillis();
        synchronized (mAveragingLock) {
            if (mAvgWindowStartUptime == 0L) {
                mAvgWindowStartUptime = now;
            }

            mAvgCurrentMaSum += sample.currentMaRounded;
            mAvgVoltageVSum += sample.voltageVRounded;
            mAvgPowerWSum += sample.powerWRounded;
            mAvgTempCSum += sample.temperatureCelsius;
            mAvgSamples++;

            long windowElapsed = now - mAvgWindowStartUptime;
            if (windowElapsed >= getUiRefreshIntervalMs() && mAvgSamples > 0) {
                double avgCurrentMa = mAvgCurrentMaSum / mAvgSamples;
                double avgVoltageV = mAvgVoltageVSum / mAvgSamples;
                double avgPowerW = mAvgPowerWSum / mAvgSamples;
                float avgTempC = (float) (mAvgTempCSum / mAvgSamples);

                long avgCurrentUa = Math.round(avgCurrentMa * 1000d);
                long avgVoltageUv = Math.round(avgVoltageV * 1_000_000d);
                int microWatt = (int) Math.min(Math.round(avgPowerW * 1_000_000d), Integer.MAX_VALUE);

                mLatestSnapshot = new ChargeSnapshot(
                        (int) Math.min(Math.abs(avgCurrentUa), Integer.MAX_VALUE),
                        (int) Math.min(Math.abs(avgVoltageUv), Integer.MAX_VALUE),
                        round2(avgCurrentMa),
                        round2(avgVoltageV),
                        round2(avgPowerW),
                        microWatt,
                        avgTempC
                );
                mLatestSnapshotUptime = now;
                resetAveragingWindowLocked(now);
            }
        }
    }

    private void resetAveragingWindow() {
        synchronized (mAveragingLock) {
            resetAveragingWindowLocked(0L);
        }
    }

    private void resetAveragingWindowLocked(long now) {
        mAvgCurrentMaSum = 0d;
        mAvgVoltageVSum = 0d;
        mAvgPowerWSum = 0d;
        mAvgTempCSum = 0d;
        mAvgSamples = 0;
        mAvgWindowStartUptime = now;
    }


    private static final class ChargeSnapshot {
        final int currentUa;
        final int voltageUv;
        final double currentMaRounded;
        final double voltageVRounded;
        final double powerWRounded;
        final int microWatt;
        final float temperatureCelsius; // Decimals

        ChargeSnapshot(int currentUa, int voltageUv, double currentMaRounded,
                double voltageVRounded, double powerWRounded, int microWatt, float temperatureCelsius) {
            this.currentUa = currentUa;
            this.voltageUv = voltageUv;
            this.currentMaRounded = currentMaRounded;
            this.voltageVRounded = voltageVRounded;
            this.powerWRounded = powerWRounded;
            this.microWatt = microWatt;
            this.temperatureCelsius = temperatureCelsius;
        }
    }

    private int getSystemInt(android.content.ContentResolver cr, String key, int def) {
        try {
            return Settings.System.getInt(cr, key);
        } catch (Settings.SettingNotFoundException e) {
            return Settings.Global.getInt(cr, key, def);
        }
    }

    private String getSystemString(android.content.ContentResolver cr, String key) {
        String val = Settings.System.getString(cr, key);
        if (val == null) {
            val = Settings.Global.getString(cr, key);
        }
        return val;
    }

}
