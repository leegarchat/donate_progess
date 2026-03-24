package org.pixel.customparts.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import org.pixel.customparts.AppConfig

private const val TAG = "SystemUI_Restarter"
private const val SYSTEMUI_PACKAGE = "com.android.systemui"
const val ACTION_RESTART_SYSTEMUI = "org.pixel.customparts.action.RESTART_SYSTEMUI"

private fun isStrategyEnabled(context: Context, key: String): Boolean {
    return try {
        Settings.Global.getInt(context.contentResolver, key, 1) != 0
    } catch (t: Throwable) {
        true
    }
}

fun restartSystemUI(context: Context) {
    if (AppConfig.IS_XPOSED) {
        val sent = requestSystemUIRestart(context)
        if (!sent) {
            runRootCommand("killall $SYSTEMUI_PACKAGE")
        }
    } else {
        Log.d(TAG, "Trying strategy 4: broadcast")
        requestSystemUIRestart(context)

        Log.w(TAG, "All strategies exhausted")
    }
}


fun requestSystemUIRestart(context: Context): Boolean {
    return try {
        val intent = Intent(ACTION_RESTART_SYSTEMUI).apply {
            setPackage(SYSTEMUI_PACKAGE)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "SystemUI restart broadcast sent")
        true
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to send SystemUI restart broadcast", t)
        false
    }
}
