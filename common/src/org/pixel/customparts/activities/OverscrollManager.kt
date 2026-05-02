package org.pixel.customparts.activities

import android.content.Context
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.pixel.customparts.AppConfig

data class SavedProfile(val name: String, val jsonData: JSONObject)
data class AppConfigItem(val pkg: String, var filter: Boolean, var scale: Float, var ignore: Boolean) {
    override fun toString(): String = "$pkg:${if(filter) 1 else 0}:$scale:${if(ignore) 1 else 0}"
}

object OverscrollManager {

    // Автоматический выбор суффикса среды
    private val SUFFIX: String
        get() = if (AppConfig.IS_XPOSED) "_xposed" else "_pine"

    const val KEY_SAVED_PROFILES = "overscroll_saved_profiles"
    const val KEY_ACTIVE_PROFILE = "overscroll_active_profile_name"

    val KEY_ENABLED get() = "overscroll_enabled$SUFFIX"
    val KEY_PACKAGES_CONFIG get() = "overscroll_packages_config$SUFFIX"
    val KEY_LOGGING get() = "overscroll_logging$SUFFIX"
    val KEY_COMPOSE_SCALE get() = "overscroll_compose_scale$SUFFIX"
    val KEY_INVERT_ANCHOR get() = "overscroll_invert_anchor$SUFFIX"
    val KEY_PULL_COEFF get() = "overscroll_pull$SUFFIX"
    val KEY_STIFFNESS get() = "overscroll_stiffness$SUFFIX"
    val KEY_DAMPING get() = "overscroll_damping$SUFFIX"
    val KEY_FLING get() = "overscroll_fling$SUFFIX"
    val KEY_RESISTANCE_EXPONENT get() = "overscroll_res_exponent$SUFFIX"
    
    // Scale Visuals
    val KEY_SCALE_MODE get() = "overscroll_scale_mode$SUFFIX"
    val KEY_SCALE_INTENSITY get() = "overscroll_scale_intensity$SUFFIX"
    val KEY_SCALE_INTENSITY_HORIZ get() = "overscroll_scale_intensity_horiz$SUFFIX"
    val KEY_SCALE_LIMIT_MIN get() = "overscroll_scale_limit_min$SUFFIX"
    val KEY_SCALE_ANCHOR_X get() = "overscroll_scale_anchor_x$SUFFIX"
    val KEY_SCALE_ANCHOR_Y get() = "overscroll_scale_anchor_y$SUFFIX"
    val KEY_SCALE_ANCHOR_X_HORIZ get() = "overscroll_scale_anchor_x_horiz$SUFFIX"
    val KEY_SCALE_ANCHOR_Y_HORIZ get() = "overscroll_scale_anchor_y_horiz$SUFFIX"
    
    // Zoom Visuals
    val KEY_ZOOM_MODE get() = "overscroll_zoom_mode$SUFFIX"
    val KEY_ZOOM_INTENSITY get() = "overscroll_zoom_intensity$SUFFIX"
    val KEY_ZOOM_INTENSITY_HORIZ get() = "overscroll_zoom_intensity_horiz$SUFFIX"
    val KEY_ZOOM_LIMIT_MIN get() = "overscroll_zoom_limit_min$SUFFIX"
    val KEY_ZOOM_ANCHOR_X get() = "overscroll_zoom_anchor_x$SUFFIX"
    val KEY_ZOOM_ANCHOR_Y get() = "overscroll_zoom_anchor_y$SUFFIX"
    val KEY_ZOOM_ANCHOR_X_HORIZ get() = "overscroll_zoom_anchor_x_horiz$SUFFIX"
    val KEY_ZOOM_ANCHOR_Y_HORIZ get() = "overscroll_zoom_anchor_y_horiz$SUFFIX"
    
    // Horizontal Scale Visuals
    val KEY_H_SCALE_MODE get() = "overscroll_h_scale_mode$SUFFIX"
    val KEY_H_SCALE_INTENSITY get() = "overscroll_h_scale_intensity$SUFFIX"
    val KEY_H_SCALE_INTENSITY_HORIZ get() = "overscroll_h_scale_intensity_horiz$SUFFIX"
    val KEY_H_SCALE_LIMIT_MIN get() = "overscroll_h_scale_limit_min$SUFFIX"
    val KEY_H_SCALE_ANCHOR_X get() = "overscroll_h_scale_anchor_x$SUFFIX"
    val KEY_H_SCALE_ANCHOR_Y get() = "overscroll_h_scale_anchor_y$SUFFIX"
    val KEY_H_SCALE_ANCHOR_X_HORIZ get() = "overscroll_h_scale_anchor_x_horiz$SUFFIX"
    val KEY_H_SCALE_ANCHOR_Y_HORIZ get() = "overscroll_h_scale_anchor_y_horiz$SUFFIX"
    
    // Advanced
    val KEY_INPUT_SMOOTH_FACTOR get() = "overscroll_input_smooth$SUFFIX"
    val KEY_PHYSICS_MIN_VEL get() = "overscroll_physics_min_vel$SUFFIX" 
    val KEY_PHYSICS_MIN_VAL get() = "overscroll_physics_min_val$SUFFIX" 
    val KEY_ANIMATION_SPEED get() = "overscroll_anim_speed$SUFFIX"
    val KEY_LERP_MAIN_IDLE get() = "overscroll_lerp_main_idle$SUFFIX"
    val KEY_LERP_MAIN_RUN get() = "overscroll_lerp_main_run$SUFFIX"

    // Delta Normalization (Compose intelligent smoothing)
    val KEY_NORM_ENABLED get() = "overscroll_norm_enabled$SUFFIX"
    val KEY_NORM_REF_DELTA get() = "overscroll_norm_ref_delta$SUFFIX"
    val KEY_NORM_DETECT_MUL get() = "overscroll_norm_detect_mul$SUFFIX"
    val KEY_NORM_FACTOR get() = "overscroll_norm_factor$SUFFIX"
    val KEY_NORM_WINDOW get() = "overscroll_norm_window$SUFFIX"
    val KEY_NORM_RAMP get() = "overscroll_norm_ramp$SUFFIX"
    val KEY_NORM_DETECT_MODE get() = "overscroll_norm_detect_mode$SUFFIX"

    fun isMasterEnabled(context: Context) = Settings.Global.getInt(context.contentResolver, KEY_ENABLED, 1) == 1
    
    suspend fun setMasterEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, KEY_ENABLED, if (enabled) 1 else 0)
    }

    fun getSavedProfiles(context: Context): List<SavedProfile> {
        val raw = Settings.Global.getString(context.contentResolver, KEY_SAVED_PROFILES) ?: return emptyList()
        val list = mutableListOf<SavedProfile>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(SavedProfile(obj.getString("name"), obj.getJSONObject("data")))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
    }

    fun getActiveProfileName(context: Context): String? {
        return Settings.Global.getString(context.contentResolver, KEY_ACTIVE_PROFILE)
    }

    fun clearActiveProfile(context: Context) {
        if (Settings.Global.getString(context.contentResolver, KEY_ACTIVE_PROFILE) != null) {
            Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, null)
        }
    }

    suspend fun saveProfile(context: Context, name: String) = withContext(Dispatchers.IO) {
        val currentJson = collectCurrentSettingsJson(context)
        val profiles = getSavedProfiles(context).toMutableList()
        profiles.removeAll { it.name == name }
        profiles.add(SavedProfile(name, currentJson))
        saveProfilesToGlobal(context, profiles)
        Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, name)
    }

    suspend fun deleteProfile(context: Context, profile: SavedProfile) = withContext(Dispatchers.IO) {
        val profiles = getSavedProfiles(context).toMutableList()
        profiles.removeIf { it.name == profile.name }
        saveProfilesToGlobal(context, profiles)
        
        val active = getActiveProfileName(context)
        if (active == profile.name) {
            Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, null)
        }
    }

    suspend fun loadProfile(context: Context, profile: SavedProfile) = withContext(Dispatchers.IO) {
        applySettingsFromJson(context, profile.jsonData)
        Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, profile.name)
    }

    private fun saveProfilesToGlobal(context: Context, profiles: List<SavedProfile>) {
        val arr = JSONArray()
        profiles.forEach { 
            val obj = JSONObject()
            obj.put("name", it.name)
            obj.put("data", it.jsonData)
            arr.put(obj)
        }
        Settings.Global.putString(context.contentResolver, KEY_SAVED_PROFILES, arr.toString())
    }

    /** Убирает платформенный суффикс _pine/_xposed из ключа для кросс-платформенного JSON */
    private fun stripSuffix(key: String): String = key.removeSuffix("_pine").removeSuffix("_xposed")

    fun getAppConfigs(context: Context): List<AppConfigItem> {
        val raw = Settings.Global.getString(context.contentResolver, KEY_PACKAGES_CONFIG) ?: return emptyList()
        val list = mutableListOf<AppConfigItem>()
        if (raw.isBlank()) return list
        
        raw.split(" ").forEach {
            try {
                val parts = it.split(":")
                if (parts.size >= 3) {
                    list.add(AppConfigItem(parts[0], parts[1] == "1", parts[2].toFloat(), if (parts.size >= 4) parts[3] == "1" else false))
                }
            } catch (_: Exception) {}
        }
        return list
    }

    suspend fun saveAppConfig(context: Context, list: List<AppConfigItem>) = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        list.forEachIndexed { index, item ->
            sb.append(item.toString())
            if (index < list.size - 1) sb.append(" ")
        }
        Settings.Global.putString(context.contentResolver, KEY_PACKAGES_CONFIG, sb.toString())
    }

    private fun collectCurrentSettingsJson(context: Context): JSONObject {
        val json = JSONObject()
        // Ключи в JSON хранятся БЕЗ суффикса _pine/_xposed — кросс-платформенный формат.
        // Для чтения из Settings.Global используем полный ключ (с суффиксом),
        // а для записи в JSON — stripSuffix(key).
        json.put(stripSuffix(KEY_ENABLED), Settings.Global.getInt(context.contentResolver, KEY_ENABLED, 1))
        json.put(stripSuffix(KEY_LOGGING), Settings.Global.getInt(context.contentResolver, KEY_LOGGING, 0))
        json.put(stripSuffix(KEY_INVERT_ANCHOR), Settings.Global.getInt(context.contentResolver, KEY_INVERT_ANCHOR, 1))
        json.put(stripSuffix(KEY_PACKAGES_CONFIG), Settings.Global.getString(context.contentResolver, KEY_PACKAGES_CONFIG) ?: "")

        val floatKeysDefaults = mapOf(
            KEY_PULL_COEFF to 1.5141f, KEY_STIFFNESS to 148.6191f, KEY_DAMPING to 0.9976f,
            KEY_FLING to 1.3679f, KEY_RESISTANCE_EXPONENT to 4.0f,
            KEY_PHYSICS_MIN_VEL to 8.0f, KEY_PHYSICS_MIN_VAL to 0.6f,
            KEY_ANIMATION_SPEED to 168.5232f, KEY_INPUT_SMOOTH_FACTOR to 0.5f,
            KEY_LERP_MAIN_IDLE to 0.4f, KEY_LERP_MAIN_RUN to 0.6999f, KEY_COMPOSE_SCALE to 3.3299f,
            KEY_NORM_REF_DELTA to 9.9999f, KEY_NORM_DETECT_MUL to 0f, KEY_NORM_FACTOR to 0.33f
        )
        for ((k, def) in floatKeysDefaults) json.put(stripSuffix(k), Settings.Global.getFloat(context.contentResolver, k, def).toDouble())

        val modeKeys = listOf(KEY_SCALE_MODE, KEY_ZOOM_MODE, KEY_H_SCALE_MODE)
        for (k in modeKeys) json.put(stripSuffix(k), Settings.Global.getInt(context.contentResolver, k, 0))

        // Delta normalization int keys (normEnabled, normDetectMode are true ints)
        json.put(stripSuffix(KEY_NORM_ENABLED), Settings.Global.getInt(context.contentResolver, KEY_NORM_ENABLED, 1))
        json.put(stripSuffix(KEY_NORM_DETECT_MODE), Settings.Global.getInt(context.contentResolver, KEY_NORM_DETECT_MODE, 1))
        // normWindow and normRamp stored as float (UI slider), hook reads via getFloatSetting
        json.put(stripSuffix(KEY_NORM_WINDOW), Settings.Global.getFloat(context.contentResolver, KEY_NORM_WINDOW, 2f).toDouble())
        json.put(stripSuffix(KEY_NORM_RAMP), Settings.Global.getFloat(context.contentResolver, KEY_NORM_RAMP, 1f).toDouble())

        val scaleFloatDefaults = mapOf(
            KEY_SCALE_INTENSITY to 0.31f, KEY_SCALE_INTENSITY_HORIZ to 0.3786f, KEY_SCALE_LIMIT_MIN to 0.1f,
            KEY_SCALE_ANCHOR_X to 0.5f, KEY_SCALE_ANCHOR_Y to 0.5f, KEY_SCALE_ANCHOR_X_HORIZ to 0.5f, KEY_SCALE_ANCHOR_Y_HORIZ to 0.5f,
            KEY_ZOOM_INTENSITY to 0.2f, KEY_ZOOM_INTENSITY_HORIZ to 0.2f, KEY_ZOOM_LIMIT_MIN to 0.1f,
            KEY_ZOOM_ANCHOR_X to 0.5f, KEY_ZOOM_ANCHOR_Y to 0.5f, KEY_ZOOM_ANCHOR_X_HORIZ to 0.5f, KEY_ZOOM_ANCHOR_Y_HORIZ to 0.5f,
            KEY_H_SCALE_INTENSITY to 0.2f, KEY_H_SCALE_INTENSITY_HORIZ to 0.0f, KEY_H_SCALE_LIMIT_MIN to 0.1f,
            KEY_H_SCALE_ANCHOR_X to 0.5f, KEY_H_SCALE_ANCHOR_Y to 0.5f, KEY_H_SCALE_ANCHOR_X_HORIZ to 0.5f, KEY_H_SCALE_ANCHOR_Y_HORIZ to 0.5f
        )
        for ((k, def) in scaleFloatDefaults) json.put(stripSuffix(k), Settings.Global.getFloat(context.contentResolver, k, def).toDouble())
        
        return json
    }

    private fun applySettingsFromJson(context: Context, json: JSONObject) {
        val iter = json.keys()
        while(iter.hasNext()) {
            val originalKey = iter.next()
            if (originalKey == KEY_ACTIVE_PROFILE) continue 
            if (originalKey == "name") continue // Игнорируем мета-поле имени, если оно есть

            var baseKey = originalKey.removeSuffix("_pine").removeSuffix("_xposed")
            
            if (baseKey.endsWith("_v2")) {
                baseKey = baseKey.removeSuffix("_v2")
            }

            val key = if (baseKey.startsWith("overscroll_")) baseKey + SUFFIX else baseKey

            val valObj = json.get(originalKey)
            
            try {
                if (valObj is Number) {
                    val isInt = key.endsWith("_mode") || 
                               key.contains("enabled") || 
                               key.contains("logging") || 
                               key.contains("invert_anchor") ||
                               key.contains("norm_detect_mode")
                    
                    if (isInt) {
                        Settings.Global.putInt(context.contentResolver, key, valObj.toInt())
                    } else {
                        Settings.Global.putFloat(context.contentResolver, key, valObj.toFloat())
                    }
                } else if (valObj is String) {
                    Settings.Global.putString(context.contentResolver, key, valObj)
                } else if (valObj is Boolean) {
                    // Обработка Boolean как Int (1/0), так как Settings.Global не хранит Boolean напрямую
                     Settings.Global.putInt(context.contentResolver, key, if (valObj) 1 else 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun resetAll(context: Context) = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        Settings.Global.putInt(cr, KEY_ENABLED, 1)
        Settings.Global.putInt(cr, KEY_LOGGING, 0)
        Settings.Global.putString(cr, KEY_ACTIVE_PROFILE, null)
        Settings.Global.putInt(cr, KEY_INVERT_ANCHOR, 1)
        Settings.Global.putFloat(cr, KEY_PULL_COEFF, 1.5141f)
        Settings.Global.putFloat(cr, KEY_STIFFNESS, 148.6191f)
        Settings.Global.putFloat(cr, KEY_DAMPING, 0.9976f)
        Settings.Global.putFloat(cr, KEY_FLING, 1.3679f)
        Settings.Global.putFloat(cr, KEY_RESISTANCE_EXPONENT, 4.0f)
        Settings.Global.putFloat(cr, KEY_PHYSICS_MIN_VEL, 8.0f)
        Settings.Global.putFloat(cr, KEY_PHYSICS_MIN_VAL, 0.6f)
        Settings.Global.putFloat(cr, KEY_ANIMATION_SPEED, 168.5232f)
        Settings.Global.putFloat(cr, KEY_INPUT_SMOOTH_FACTOR, 0.5f)
        Settings.Global.putFloat(cr, KEY_LERP_MAIN_IDLE, 0.4f)
        Settings.Global.putFloat(cr, KEY_LERP_MAIN_RUN, 0.6999f)
        Settings.Global.putFloat(cr, KEY_COMPOSE_SCALE, 3.3299f)
        Settings.Global.putInt(cr, KEY_SCALE_MODE, 0)
        Settings.Global.putFloat(cr, KEY_SCALE_INTENSITY, 0.31f)
        Settings.Global.putFloat(cr, KEY_SCALE_INTENSITY_HORIZ, 0.3786f)
        Settings.Global.putFloat(cr, KEY_SCALE_LIMIT_MIN, 0.1f)
        Settings.Global.putFloat(cr, KEY_SCALE_ANCHOR_X, 0.5f)
        Settings.Global.putFloat(cr, KEY_SCALE_ANCHOR_Y, 0.5f)
        Settings.Global.putFloat(cr, KEY_SCALE_ANCHOR_X_HORIZ, 0.5f)
        Settings.Global.putFloat(cr, KEY_SCALE_ANCHOR_Y_HORIZ, 0.5f)
        Settings.Global.putInt(cr, KEY_ZOOM_MODE, 0)
        Settings.Global.putFloat(cr, KEY_ZOOM_INTENSITY, 0.2f)
        Settings.Global.putFloat(cr, KEY_ZOOM_INTENSITY_HORIZ, 0.2f)
        Settings.Global.putFloat(cr, KEY_ZOOM_LIMIT_MIN, 0.1f)
        Settings.Global.putFloat(cr, KEY_ZOOM_ANCHOR_X, 0.5f)
        Settings.Global.putFloat(cr, KEY_ZOOM_ANCHOR_Y, 0.5f)
        Settings.Global.putFloat(cr, KEY_ZOOM_ANCHOR_X_HORIZ, 0.5f)
        Settings.Global.putFloat(cr, KEY_ZOOM_ANCHOR_Y_HORIZ, 0.5f)
        Settings.Global.putInt(cr, KEY_H_SCALE_MODE, 0)
        Settings.Global.putFloat(cr, KEY_H_SCALE_INTENSITY, 0.2f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_INTENSITY_HORIZ, 0.0f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_LIMIT_MIN, 0.1f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_ANCHOR_X, 0.5f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_ANCHOR_Y, 0.5f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_ANCHOR_X_HORIZ, 0.5f)
        Settings.Global.putFloat(cr, KEY_H_SCALE_ANCHOR_Y_HORIZ, 0.5f)
        // Delta normalization
        Settings.Global.putInt(cr, KEY_NORM_ENABLED, 1)
        Settings.Global.putFloat(cr, KEY_NORM_REF_DELTA, 9.9999f)
        Settings.Global.putFloat(cr, KEY_NORM_DETECT_MUL, 0f)
        Settings.Global.putFloat(cr, KEY_NORM_FACTOR, 0.33f)
        Settings.Global.putFloat(cr, KEY_NORM_WINDOW, 2f)
        Settings.Global.putFloat(cr, KEY_NORM_RAMP, 1f)
        Settings.Global.putInt(cr, KEY_NORM_DETECT_MODE, 1)
    }

    suspend fun exportSettings(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        try {
            val json = collectCurrentSettingsJson(context)
            context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { it.write(json.toString(4).toByteArray()) }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    suspend fun importSettings(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val sb = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) { sb.append(line); line = reader.readLine() }
                }
            }
            applySettingsFromJson(context, JSONObject(sb.toString()))
            Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, null)
            true
        } catch (e: Exception) { 
            e.printStackTrace()
            false 
        }
    }

    // ── Network configs from GitHub ──

    private const val GITHUB_API_URL = "https://api.github.com/repos/leegarchat/PixelExtraParts/contents/overscroll.configs"
    
    data class NetworkConfig(val name: String, val downloadUrl: String)

    /**
     * Fetches the list of .json files from the GitHub repo directory.
     * Returns list of NetworkConfig with display name (filename without .json) and raw download URL.
     */
    suspend fun fetchNetworkConfigs(): Result<List<NetworkConfig>> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(GITHUB_API_URL)
            conn = url.openConnection() as? HttpURLConnection
                ?: return@withContext Result.failure(Exception("Unsupported connection type"))
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode != 200) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode}"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }

            val arr = JSONArray(body)
            val configs = mutableListOf<NetworkConfig>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fileName = obj.getString("name")
                if (fileName.endsWith(".json", ignoreCase = true)) {
                    val displayName = fileName.removeSuffix(".json").removeSuffix(".JSON")
                    val rawUrl = obj.getString("download_url")
                    configs.add(NetworkConfig(displayName, rawUrl))
                }
            }
            Result.success(configs)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Downloads a config JSON from URL, applies it, and saves as a profile.
     */
    suspend fun applyNetworkConfig(context: Context, config: NetworkConfig): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(config.downloadUrl)
            conn = url.openConnection() as? HttpURLConnection ?: return@withContext false
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            val body = conn.inputStream.bufferedReader().use { it.readText() }

            val json = JSONObject(body)
            applySettingsFromJson(context, json)

            // Save as profile
            val profiles = getSavedProfiles(context).toMutableList()
            profiles.removeAll { it.name == config.name }
            profiles.add(SavedProfile(config.name, json))
            saveProfilesToGlobal(context, profiles)
            Settings.Global.putString(context.contentResolver, KEY_ACTIVE_PROFILE, config.name)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            conn?.disconnect()
        }
    }
}