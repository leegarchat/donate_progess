package org.pixel.customparts.manager.xposed

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.pixel.customparts.core.BaseHook
import org.pixel.customparts.core.IHookEnvironment

import org.pixel.customparts.hooks.*

import org.pixel.customparts.hooks.recents.*

import org.pixel.customparts.hooks.systemui.DozeTapDozeHook
import org.pixel.customparts.hooks.systemui.DozeTapShadeHook
import org.pixel.customparts.hooks.systemui.KeyguardBatteryPowerHook
import org.pixel.customparts.hooks.systemui.ShadeCompactMediaHook
import org.pixel.customparts.hooks.systemui.ShadeUnifiedSurfaceHook

class XposedInit : IXposedHookLoadPackage {

    private val environment: IHookEnvironment = XposedEnvironment()
    private val TAG = "XposedInit"

    private companion object {
        const val PACKAGE_SYSTEMUI = "com.android.systemui"
        const val PACKAGE_SELF = "org.pixel.customparts.xposed"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == PACKAGE_SELF) {
            hookModuleStatus(lpparam)
        }

        
        if (lpparam.packageName != null) {
            initEdgeEffectHook(lpparam.classLoader)
            initGlobalHooks(lpparam.classLoader)
        }

        
        if (lpparam.packageName == "com.google.android.apps.nexuslauncher" || 
            lpparam.packageName == "com.google.android.apps.pixel.launcher" ||
            lpparam.packageName == "com.android.launcher3") {
            
            environment.log(TAG, "MATCHED Launcher package: ${lpparam.packageName}")
            
            val hooks: List<BaseHook> = listOf(
                GridSizeAppMenuHook(),
                UnifiedLauncherHook(),
                // OxygenRecentsIconStripHook(),
                RecentsUnifiedHook()
            ).sortedByDescending { it.priority }

            applyHooks(hooks, lpparam.classLoader)
        }

        
        if (lpparam.packageName == PACKAGE_SYSTEMUI) {
            environment.log(TAG, "MATCHED SystemUI package")

            val hooks: List<BaseHook> = listOf(
                DozeTapDozeHook(),
                DozeTapShadeHook(),
                KeyguardBatteryPowerHook(),
                ShadeUnifiedSurfaceHook(),
                ShadeCompactMediaHook()
            ).sortedByDescending { it.priority }

            applyHooks(hooks, lpparam.classLoader)
        }
    }

    



    private fun initEdgeEffectHook(classLoader: ClassLoader) {
        try {
            val wrapper = EdgeEffectHookWrapper().apply {
                keySuffix = "_xposed"
                useGlobalSettings = true
            }
            wrapper.setup(environment)
            wrapper.init(classLoader)
        } catch (t: Throwable) {
            
            environment.logError(TAG, "EdgeEffect hook failed", t)
        }
    }

    private fun initGlobalHooks(classLoader: ClassLoader) {
        val hooks: List<BaseHook> = listOf(
            MagnifierHook(),
            ActivityTransitionHook(),
            PredictiveBackDisableHook()
        )
        applyHooks(hooks, classLoader)
    }

    private fun applyHooks(hooks: List<BaseHook>, classLoader: ClassLoader) {
        hooks.forEach { hook ->
            try {
                hook.setup(environment)
                hook.init(classLoader)
                environment.log(TAG, "Hook ${hook.hookId} initialized")
            } catch (t: Throwable) {
                environment.logError(TAG, "Failed to load ${hook.hookId}", t)
            }
        }
    }

    


    private fun hookModuleStatus(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Check Settings.Global if we should override the hook
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
            val context = currentApplicationMethod.invoke(null) as? android.content.Context
            
            if (context != null) {
                 val value = android.provider.Settings.Global.getInt(
                    context.contentResolver, 
                    "pixelparts_xposed_to_pine", 
                    0
                )
                
                // If setting is enabled, let the internal check handle it
                if (value == 1) {
                    environment.log(TAG, "ModuleStatus hook skipped due to pixelparts_xposed_to_pine=1")
                    return
                }
            }

            val targetClass = "org.pixel.customparts.ui.ModuleStatus"

            XposedHelpers.findAndHookMethod(
                targetClass,
                lpparam.classLoader,
                "isModuleActive",
                object : XC_MethodReplacement() {
                    override fun replaceHookedMethod(param: MethodHookParam): Any {
                        return true
                    }
                }
            )

            environment.log(TAG, "ModuleStatus hook installed")
        } catch (t: Throwable) {
            environment.logError(TAG, "Failed to hook ModuleStatus self-check", t)
        }
    }
}