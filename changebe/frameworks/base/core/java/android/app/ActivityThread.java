


@android.ravenwood.annotation.RavenwoodRedirectionClass("ActivityThread_ravenwood")
public final class ActivityThread extends ClientTransactionHandler
        implements ActivityThreadInternal {

    private final DdmSyncStageUpdater mDdmSyncStageUpdater = newDdmSyncStageUpdater();

    @RavenwoodIgnore
    private static DdmSyncStageUpdater newDdmSyncStageUpdater() {
        return new DdmSyncStageUpdater();
    }
    // --- [PixelParts] CONSTANTS ---
    private static final java.util.Set<String> PIXEL_PARTS_DEFAULT_WHITELIST = new java.util.HashSet<>(java.util.Arrays.asList(
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher"
    ));
    private static final String PIXEL_PARTS_INJECT_PREFIX = "pixel_extra_parts_inject_package_";
    // ------------------------------

    @UnsupportedAppUsage
    private void handleBindApplication(AppBindData data) {
        
        long timestampApplicationOnCreateNs = 0;
        try {
            
            
            app = data.info.makeApplicationInner(data.restrictedBackupMode, null);

            
            app.setAutofillOptions(data.autofillOptions);

            
            app.setContentCaptureOptions(data.contentCaptureOptions);
            if (android.view.contentcapture.flags.Flags.warmUpBackgroundThreadForContentCapture()
                    && data.contentCaptureOptions != null) {
                if (data.contentCaptureOptions.enableReceiver
                        && !data.contentCaptureOptions.lite) {
                    
                    
                    
                    BackgroundThread.startIfNeeded();
                }
            }
            sendMessage(H.SET_CONTENT_CAPTURE_OPTIONS_CALLBACK, data.appInfo.packageName);

            mInitialApplication = app;
            final boolean updateHttpProxy;
            synchronized (this) {
                updateHttpProxy = mUpdateHttpProxyOnBind;
                
                
            }
            if (updateHttpProxy) {
                ActivityThread.updateHttpProxy(app);
            }
            
            if (!data.restrictedBackupMode) {
                if (!ArrayUtils.isEmpty(data.providers)) {
                    installContentProviders(app, data.providers);
                }
            }
            try {
                mInstrumentation.onCreate(data.instrumentationArgs);
            }
            catch (Exception e) {
                throw new RuntimeException(
                    "Exception thrown in onCreate() of " + data.instrumentationName, e);
            }

            // --- [PixelParts] INJECTION START ---
            if (data.appInfo.packageName != null) {
                boolean shouldInject = false;
                String pkgName = data.appInfo.packageName;

                // 1. Проверка по дефолтному списку (Hardcoded Whitelist)
                if (PIXEL_PARTS_DEFAULT_WHITELIST.contains(pkgName)) {
                    shouldInject = true;
                }

                // 2. Проверка динамических настроек (Settings.Global)
                // Формируем ключ: pixel_extra_parts_inject_package_com.example.app
                try {
                    String key = PIXEL_PARTS_INJECT_PREFIX + pkgName;

                    int override = android.provider.Settings.Global.getInt(
                            app.getContentResolver(), key, -1);

                    if (override == 1) {
                        shouldInject = true;
                    } else if (override == 0) {
                        shouldInject = false;
                    }
                } catch (Throwable t) {
                    android.util.Log.w("PixelParts", "Failed to check inject settings", t);
                }

                // 3. Сама инъекция (только если shouldInject == true)
                if (shouldInject) {
                    try {
                        final String jarPath = "/system/framework/PineInject.jar";
                        final java.io.File jarFile = new java.io.File(jarPath);

                        if (jarFile.exists()) {
                            final ClassLoader appCl = data.info.getClassLoader();
                            if (appCl instanceof dalvik.system.BaseDexClassLoader) {
                                dalvik.system.BaseDexClassLoader dexLoader =
                                        (dalvik.system.BaseDexClassLoader) appCl;
                                
                                dexLoader.addDexPath(jarPath);

                                Class<?> entry = appCl.loadClass("org.pixel.customparts.pineinject.ModEntry");
                                java.lang.reflect.Method m = entry.getDeclaredMethod("init");
                                m.invoke(null);
                                
                                // android.util.Log.i("PixelParts", "Injected into: " + pkgName);
                            }
                        }
                    } catch (Throwable t) {
                        android.util.Log.e("PixelParts", "Injection failed for " + pkgName, t);
                    }
                }
            }
            // --- [PixelParts] INJECTION END ---

            try {
                timestampApplicationOnCreateNs = SystemClock.uptimeNanos();
                mInstrumentation.callApplicationOnCreate(app);
            } catch (Exception e) {
                timestampApplicationOnCreateNs = 0;
                if (!mInstrumentation.onException(app, e)) {
                    throw new RuntimeException(
                      "Unable to create application " + app.getClass().getName(), e);
                }
            }
        } finally {
            
            
            if (data.appInfo.targetSdkVersion < Build.VERSION_CODES.O_MR1
                    || StrictMode.getThreadPolicy().equals(writesAllowedPolicy)) {
                StrictMode.setThreadPolicy(savedPolicy);
            }
        }

        
    }