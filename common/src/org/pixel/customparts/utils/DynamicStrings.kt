package org.pixel.customparts.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object RemoteStringsManager {
    private const val PREFS_NAME = "remote_strings_cache"
    private const val KEY_JSON = "json_data"
    private const val BASE_URL_PATTERN = "https://raw.githubusercontent.com/leegarchat/PixelExtraParts/main/lang/strings_%s.json"
    private val overrides = mutableStateMapOf<Int, String>()
    private var isInitialized = false
    private fun getUrlForLocale(lang: String): String {
        return String.format(BASE_URL_PATTERN, lang)
    }

    suspend fun initialize(context: Context) {
        if (isInitialized) return
        loadFromCache(context)
        isInitialized = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastUpdate = prefs.getLong("last_update_time", 0L)
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastUpdate < 300000L) {
            return 
        }

        performUpdate(context)
    }

    suspend fun forceRefresh(context: Context): Boolean {
        return performUpdate(context)
    }

    private suspend fun performUpdate(context: Context): Boolean {
        val currentLanguage = Locale.getDefault().language
        var jsonString = fetchJson(getUrlForLocale(currentLanguage))

        if (!isValidJson(jsonString) && currentLanguage != "en") {
            jsonString = fetchJson(getUrlForLocale("en"))
        }

        return if (isValidJson(jsonString)) {
            parseAndApply(context, jsonString!!)
            saveToCache(context, jsonString)
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putLong("last_update_time", System.currentTimeMillis()).apply()
            true
        } else {
            false
        }
    }

    private fun isValidJson(json: String?): Boolean {
        if (json.isNullOrBlank()) return false
        return try {
            JSONObject(json)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadFromCache(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_JSON, null)
        if (isValidJson(jsonString)) {
            parseAndApply(context, jsonString!!)
        }
    }

    private fun saveToCache(context: Context, jsonString: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_JSON, jsonString).apply()
    }

    private fun parseAndApply(context: Context, jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val resources = context.resources
            val packageName = context.packageName

            val iterator = json.keys()
            while (iterator.hasNext()) {
                val keyName = iterator.next()
                val newValue = json.getString(keyName)
                val resId = resources.getIdentifier(keyName, "string", packageName)
                if (resId != 0) {
                    overrides[resId] = newValue
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun fetchJson(url: String): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as? HttpURLConnection ?: return@withContext null
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Android") 
            
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
    fun getString(context: Context, @StringRes id: Int): String {
        return overrides[id] ?: context.getString(id)
    }
    fun getOverride(id: Int): String? = overrides[id]
}

@Composable
fun dynamicStringResource(@StringRes id: Int): String {
    val override = RemoteStringsManager.getOverride(id)
    if (override != null) return override

    return safeStringResource(id)
}

@Composable
fun dynamicStringResource(@StringRes id: Int, vararg formatArgs: Any): String {
    val override = RemoteStringsManager.getOverride(id)
    return if (override != null) {
        try {
            String.format(override, *formatArgs)
        } catch (e: Exception) {
            safeStringResource(id, *formatArgs)
        }
    } else {
        safeStringResource(id, *formatArgs)
    }
}

@Composable
private fun safeStringResource(@StringRes id: Int, vararg args: Any): String {
    val context = LocalContext.current
    return try {
        if (args.isEmpty()) {
            context.getString(id)
        } else {
            context.getString(id, *args)
        }
    } catch (e: Exception) {
        val resName = try { 
            context.resources.getResourceEntryName(id) 
        } catch (ex: Exception) { 
            id.toString() 
        }
        "error: $resName"
    }
}