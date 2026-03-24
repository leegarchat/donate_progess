package org.pixel.customparts.manager.xposed

import android.content.Context
import android.provider.Settings
import de.robv.android.xposed.XposedBridge
import org.pixel.customparts.core.IHookEnvironment






class XposedEnvironment : IHookEnvironment {

    companion object {
        private const val SUFFIX = "_xposed"
    }

    



    private fun resolveKey(key: String): String {
        val baseKey = key.removeSuffix("_xposed").removeSuffix("_pine")
        return "$baseKey$SUFFIX"
    }

    override fun isEnabled(context: Context?, key: String, default: Boolean): Boolean {
        if (context == null) return default
        val finalKey = resolveKey(key)
        return try {
            Settings.Global.getInt(context.contentResolver, finalKey, if (default) 1 else 0) != 0
        } catch (t: Throwable) {
            logError("Env", "Failed to read boolean setting $finalKey", t)
            default
        }
    }

    override fun getInt(context: Context?, key: String, default: Int): Int {
        if (context == null) return default
        val finalKey = resolveKey(key)
        return try {
            Settings.Global.getInt(context.contentResolver, finalKey, default)
        } catch (t: Throwable) {
            logError("Env", "Failed to read int setting $finalKey", t)
            default
        }
    }

    override fun getFloat(context: Context?, key: String, default: Float): Float {
        if (context == null) return default
        val finalKey = resolveKey(key)
        return try {
            Settings.Global.getFloat(context.contentResolver, finalKey, default)
        } catch (t: Throwable) {
            try {
                val intValue = Settings.Global.getInt(
                    context.contentResolver,
                    finalKey,
                    (default * 100).toInt()
                )
                intValue / 100f
            } catch (inner: Throwable) {
                logError("Env", "Failed to read float setting $finalKey", inner)
                default
            }
        }
    }

    override fun getString(context: Context?, key: String, default: String?): String? {
        if (context == null) return default
        val finalKey = resolveKey(key)
        return try {
            Settings.Global.getString(context.contentResolver, finalKey) ?: default
        } catch (t: Throwable) {
            logError("Env", "Failed to read string setting $finalKey", t)
            default
        }
    }

    override fun log(tag: String, message: String) {
        XposedBridge.log("[$tag] $message")
    }

    override fun logError(tag: String, message: String, t: Throwable?) {
        if (t != null) {
            XposedBridge.log("[$tag] ERROR: $message - ${t.message}")
            XposedBridge.log(t)
        } else {
            XposedBridge.log("[$tag] ERROR: $message")
        }
    }
}