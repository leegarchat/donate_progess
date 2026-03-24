package org.pixel.customparts.ui.addons

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import org.pixel.customparts.R
import org.pixel.customparts.utils.dynamicStringResource

private const val TAG = "AddonManagerUI"
private const val ADDON_DIR = "/data/pixelparts/addons"
private const val SYSTEM_ADDON_DIR = "/system_ext/etc/pixelparts/addons"
private const val ADDON_PREFIX = "pixel_addon_"
private const val INJECT_PREFIX = "pixel_extra_parts_inject_package_"
private const val SAFE_MODE_SETTING = "pixel_addon_safe_mode"
private const val IGNORED_PACKAGE = "android" // system_server — can't hook reliably

// =====================================================================
// Data model
// =====================================================================

data class AddonUiModel(
    val id: String,
    val entryClass: String,
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val jarPath: String,
    val defaultTargets: Set<String>,
    val enabled: Boolean,
    val scopeMode: Int,          // 0=default, 1=custom, 2=merge
    val customTargets: Set<String>,
    val settings: List<AddonSettingDef> = emptyList(),
    val isSystem: Boolean = false,
    val iconBitmap: Bitmap? = null,
    val backgroundBitmap: Bitmap? = null,
    val backgroundMode: String = "gradient",  // "cover", "gradient"
    val backgroundAlpha: Int = 50,             // 0–100, intensity of background image
    val backgroundGradientSteps: List<Int> = listOf(0, 100), // gradient opacity stops (0–100 each)
    val backgroundBlur: Boolean = false,       // enable blur on background
    val backgroundBlurRadius: Int = 25,        // blur radius in dp
    val cardColor: String = "",                // custom card background color (hex, e.g. "#FF5722")
    val backgroundScope: String = "full"       // "full" = background extends with settings, "header" = header only
)

/** A single setting definition parsed from addon.json "settings" array */
data class AddonSettingDef(
    val key: String,
    val title: String,
    val description: String = "",
    val type: SettingType,
    val provider: SettingProvider = SettingProvider.GLOBAL,
    val defaultInt: Int = 0,
    val defaultFloat: Float = 0f,
    val defaultString: String = "",
    val defaultBool: Boolean = false,
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val unit: String = "",
    val options: List<SelectOption> = emptyList(),
    val mimeType: String = "*/*"
)

enum class SettingType { INT, FLOAT, STRING, SELECT, FILE, TOGGLE, SWITCH, CHECKBOX }
enum class SettingProvider { GLOBAL, SYSTEM, SECURE }

data class SelectOption(val value: String, val label: String)

/** Info about an installed app for the package picker */
data class AppInfoItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

// =====================================================================
// Settings helpers
// =====================================================================

private fun isSafeModeActive(context: Context): Boolean {
    return try {
        Settings.Global.getInt(context.contentResolver, SAFE_MODE_SETTING, 0) == 1
    } catch (_: Throwable) { false }
}

private fun exitAddonSafeMode(context: Context) {
    try {
        Settings.Global.putInt(context.contentResolver, SAFE_MODE_SETTING, 0)
        // Also delete the crash counter file
        val guardFile = File("/data/pixelparts/.boot_guard")
        if (guardFile.exists()) guardFile.delete()
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to exit safe mode", t)
    }
}

private fun readAddonEnabled(context: Context, id: String): Boolean {
    return try {
        Settings.Global.getInt(context.contentResolver, "${ADDON_PREFIX}${id}_enabled", 1) != 0
    } catch (_: Throwable) { true }
}

private fun writeAddonEnabled(context: Context, id: String, enabled: Boolean) {
    Settings.Global.putInt(context.contentResolver, "${ADDON_PREFIX}${id}_enabled", if (enabled) 1 else 0)
}

private fun readScopeMode(context: Context, id: String): Int {
    return try {
        Settings.Global.getInt(context.contentResolver, "${ADDON_PREFIX}${id}_scope_mode", 0)
    } catch (_: Throwable) { 0 }
}

private fun writeScopeMode(context: Context, id: String, mode: Int) {
    Settings.Global.putInt(context.contentResolver, "${ADDON_PREFIX}${id}_scope_mode", mode)
}

private fun readCustomTargets(context: Context, id: String): Set<String> {
    val raw = Settings.Global.getString(context.contentResolver, "${ADDON_PREFIX}${id}_packages")
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

private fun writeCustomTargets(context: Context, id: String, targets: Set<String>) {
    Settings.Global.putString(context.contentResolver, "${ADDON_PREFIX}${id}_packages", targets.joinToString(","))
}

private fun addToWhitelist(context: Context, pkg: String) {
    Settings.Global.putInt(context.contentResolver, "${INJECT_PREFIX}${pkg}", 1)
}

private fun removeFromWhitelist(context: Context, pkg: String) {
    Settings.Global.putInt(context.contentResolver, "${INJECT_PREFIX}${pkg}", 0)
}

private val BUILTIN_WHITELIST = setOf(
    "com.google.android.apps.nexuslauncher",
    "com.google.android.apps.pixel.launcher",
    "com.android.launcher3",
    "com.android.systemui"
)

// =====================================================================
// Settings provider read/write helpers
// =====================================================================

private fun readSettingString(context: Context, provider: SettingProvider, key: String): String? {
    return try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.getString(context.contentResolver, key)
            SettingProvider.SYSTEM -> Settings.System.getString(context.contentResolver, key)
            SettingProvider.SECURE -> Settings.Secure.getString(context.contentResolver, key)
        }
    } catch (_: Throwable) { null }
}

private fun writeSettingString(context: Context, provider: SettingProvider, key: String, value: String) {
    try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.putString(context.contentResolver, key, value)
            SettingProvider.SYSTEM -> Settings.System.putString(context.contentResolver, key, value)
            SettingProvider.SECURE -> Settings.Secure.putString(context.contentResolver, key, value)
        }
    } catch (t: Throwable) { Log.e(TAG, "writeSettingString($key) failed", t) }
}

private fun readSettingInt(context: Context, provider: SettingProvider, key: String, default: Int): Int {
    return try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.getInt(context.contentResolver, key, default)
            SettingProvider.SYSTEM -> Settings.System.getInt(context.contentResolver, key, default)
            SettingProvider.SECURE -> Settings.Secure.getInt(context.contentResolver, key, default)
        }
    } catch (_: Throwable) { default }
}

private fun writeSettingInt(context: Context, provider: SettingProvider, key: String, value: Int) {
    try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.putInt(context.contentResolver, key, value)
            SettingProvider.SYSTEM -> Settings.System.putInt(context.contentResolver, key, value)
            SettingProvider.SECURE -> Settings.Secure.putInt(context.contentResolver, key, value)
        }
    } catch (t: Throwable) { Log.e(TAG, "writeSettingInt($key) failed", t) }
}

private fun readSettingFloat(context: Context, provider: SettingProvider, key: String, default: Float): Float {
    return try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.getFloat(context.contentResolver, key, default)
            SettingProvider.SYSTEM -> Settings.System.getFloat(context.contentResolver, key, default)
            SettingProvider.SECURE -> Settings.Secure.getFloat(context.contentResolver, key, default)
        }
    } catch (_: Throwable) { default }
}

private fun writeSettingFloat(context: Context, provider: SettingProvider, key: String, value: Float) {
    try {
        when (provider) {
            SettingProvider.GLOBAL -> Settings.Global.putFloat(context.contentResolver, key, value)
            SettingProvider.SYSTEM -> Settings.System.putFloat(context.contentResolver, key, value)
            SettingProvider.SECURE -> Settings.Secure.putFloat(context.contentResolver, key, value)
        }
    } catch (t: Throwable) { Log.e(TAG, "writeSettingFloat($key) failed", t) }
}

// =====================================================================
// Parse settings from addon.json
// =====================================================================

private fun parseSettings(json: JSONObject): List<AddonSettingDef> {
    val arr = json.optJSONArray("settings") ?: return emptyList()
    val result = mutableListOf<AddonSettingDef>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val key = obj.optString("key", "")
        if (key.isEmpty()) continue
        val typeStr = obj.optString("type", "").lowercase()
        val type = when (typeStr) {
            "int" -> SettingType.INT
            "float" -> SettingType.FLOAT
            "string", "str" -> SettingType.STRING
            "select", "arr" -> SettingType.SELECT
            "file" -> SettingType.FILE
            "toggle", "bool", "boolean" -> SettingType.TOGGLE
            "switch" -> SettingType.SWITCH
            "checkbox", "check" -> SettingType.CHECKBOX
            else -> continue
        }
        val providerStr = obj.optString("provider", "global").lowercase()
        val provider = when (providerStr) {
            "system" -> SettingProvider.SYSTEM
            "secure" -> SettingProvider.SECURE
            else -> SettingProvider.GLOBAL
        }
        val options = mutableListOf<SelectOption>()
        val optArr = obj.optJSONArray("options")
        if (optArr != null) {
            for (j in 0 until optArr.length()) {
                val optObj = optArr.optJSONObject(j)
                if (optObj != null) {
                    options.add(SelectOption(
                        value = optObj.optString("value", ""),
                        label = optObj.optString("label", optObj.optString("value", ""))
                    ))
                } else {
                    val s = optArr.optString(j)
                    if (s.isNotEmpty()) options.add(SelectOption(s, s))
                }
            }
        }
        result.add(AddonSettingDef(
            key = key,
            title = obj.optString("title", key),
            description = obj.optString("description", ""),
            type = type,
            provider = provider,
            defaultInt = obj.optInt("default", 0),
            defaultFloat = obj.optDouble("default", 0.0).toFloat(),
            defaultString = obj.optString("default", ""),
            defaultBool = obj.optBoolean("default", false),
            min = obj.optDouble("min", 0.0).toFloat(),
            max = obj.optDouble("max", 100.0).toFloat(),
            step = obj.optDouble("step", 1.0).toFloat(),
            unit = obj.optString("unit", ""),
            options = options,
            mimeType = obj.optString("mimeType", "*/*")
        ))
    }
    return result
}

// =====================================================================
// Scan addons
// =====================================================================

private fun scanAddons(context: Context): List<AddonUiModel> {
    val result = mutableListOf<AddonUiModel>()
    val dirs = listOf(SYSTEM_ADDON_DIR, ADDON_DIR)

    for (dirPath in dirs) {
        val isSystemDir = dirPath == SYSTEM_ADDON_DIR
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) continue
        val files = dir.listFiles() ?: continue

        for (file in files) {
            if (!file.name.endsWith(".jar")) continue
            try {
                val json = readDescriptor(file) ?: continue
                val entryClassStr = json.optString("entryClass", "")
                val id = json.optString("id", entryClassStr.ifEmpty { file.nameWithoutExtension })
                val name = json.optString("name", id)
                val author = json.optString("author", context.getString(R.string.addon_author_unknown))
                val description = json.optString("description", "")
                val version = json.optString("version", "1.0")

                val defaultTargets = mutableSetOf<String>()
                val arr = json.optJSONArray("targetPackages")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val pkg = arr.optString(i)
                        if (pkg.isNotEmpty()) defaultTargets.add(pkg)
                    }
                }

                // Extract icon and background from JAR
                val iconPath = json.optString("icon", "")
                val bgPath = json.optString("background", "")
                val bgMode = json.optString("backgroundMode", "gradient")
                val bgAlpha = json.optInt("backgroundAlpha", 50).coerceIn(0, 100)
                val bgGradientStepsArr = json.optJSONArray("backgroundGradientSteps")
                val bgGradientSteps = if (bgGradientStepsArr != null && bgGradientStepsArr.length() >= 2) {
                    (0 until bgGradientStepsArr.length()).map { bgGradientStepsArr.optInt(it, 0).coerceIn(0, 100) }
                } else listOf(0, 100)
                val bgBlur = json.optBoolean("backgroundBlur", false)
                val bgBlurRadius = json.optInt("backgroundBlurRadius", 25).coerceIn(0, 100)
                val cardColorStr = json.optString("cardColor", "")
                val bgScope = json.optString("backgroundScope", "full")
                val iconBitmap = extractBitmapFromJar(file, iconPath)
                val bgBitmap = extractBitmapFromJar(file, bgPath)

                result.add(
                    AddonUiModel(
                        id = id, entryClass = entryClassStr, name = name, author = author, description = description,
                        version = version, jarPath = file.absolutePath,
                        defaultTargets = defaultTargets,
                        enabled = readAddonEnabled(context, id),
                        scopeMode = readScopeMode(context, id),
                        customTargets = readCustomTargets(context, id),
                        settings = parseSettings(json),
                        isSystem = isSystemDir,
                        iconBitmap = iconBitmap,
                        backgroundBitmap = bgBitmap,
                        backgroundMode = bgMode,
                        backgroundAlpha = bgAlpha,
                        backgroundGradientSteps = bgGradientSteps,
                        backgroundBlur = bgBlur,
                        backgroundBlurRadius = bgBlurRadius,
                        cardColor = cardColorStr,
                        backgroundScope = bgScope
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read addon: ${file.name}", t)
            }
        }
    }
    return result
}

private fun readDescriptor(jarFile: File): org.json.JSONObject? {
    val ext = File(jarFile.absolutePath + ".json")
    if (ext.exists()) {
        try { return org.json.JSONObject(ext.readText(Charsets.UTF_8)) } catch (_: Throwable) {}
    }
    try {
        java.util.zip.ZipFile(jarFile).use { zip ->
            val entry = zip.getEntry("META-INF/addon.json") ?: return null
            val text = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText()
            return org.json.JSONObject(text)
        }
    } catch (_: Throwable) { return null }
}

/** Extract a bitmap image from inside a JAR file at the given entry path */
private fun extractBitmapFromJar(jarFile: File, entryPath: String): Bitmap? {
    if (entryPath.isEmpty()) return null
    return try {
        java.util.zip.ZipFile(jarFile).use { zip ->
            val entry = zip.getEntry(entryPath) ?: return null
            zip.getInputStream(entry).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    } catch (_: Throwable) { null }
}

// =====================================================================
// Whitelist sync
// =====================================================================

private fun syncWhitelist(context: Context, addons: List<AddonUiModel>) {
    for (addon in addons) {
        if (!addon.enabled) continue
        for (pkg in getEffectiveTargets(addon)) {
            if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
        }
    }
}

private fun getEffectiveTargets(addon: AddonUiModel): Set<String> {
    val raw = when (addon.scopeMode) {
        0 -> addon.defaultTargets
        1 -> addon.customTargets
        2 -> addon.defaultTargets + addon.customTargets
        else -> addon.defaultTargets
    }
    return raw - IGNORED_PACKAGE
}

/** Build a map of packageName → list of enabled addons targeting that package */
private fun buildActiveAppsMap(addons: List<AddonUiModel>): Map<String, List<AddonUiModel>> {
    val map = mutableMapOf<String, MutableList<AddonUiModel>>()
    for (addon in addons) {
        if (!addon.enabled) continue
        for (pkg in getEffectiveTargets(addon)) {
            map.getOrPut(pkg) { mutableListOf() }.add(addon)
        }
    }
    return map
}

// =====================================================================
// Import / Delete
// =====================================================================

private fun importAddonJar(context: Context, uri: Uri): Boolean {
    return try {
        val dir = File(ADDON_DIR)

        // --- Step 1: ensure directory exists ---
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Cannot create addon dir: ${dir.absolutePath}")
            return false
        }

        // --- Step 2: determine file name ---
        var fileName = "addon_${System.currentTimeMillis()}.jar"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                val displayName = cursor.getString(nameIndex)
                if (displayName.endsWith(".jar")) fileName = displayName
            }
        }

        val target = File(dir, fileName)

        // --- Step 3: copy file (system UID — direct I/O) ---
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
        if (!target.exists() || target.length() == 0L) {
            Log.e(TAG, "Copy failed: file empty or missing")
            return false
        }
        target.setReadable(true, false)

        // --- Step 4: validate addon.json ---
        val desc = readDescriptor(target)
        if (desc == null || !desc.has("entryClass")) {
            target.delete()
            Log.e(TAG, "Imported JAR has no valid addon.json")
            return false
        }

        // --- Step 5: register targets in whitelist ---
        val arr = desc.optJSONArray("targetPackages")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val pkg = arr.optString(i)
                if (pkg.isNotEmpty() && pkg !in BUILTIN_WHITELIST) {
                    addToWhitelist(context, pkg)
                }
            }
        }

        Log.d(TAG, "Imported addon: $fileName -> ${target.absolutePath}")
        true
    } catch (t: Throwable) {
        Log.e(TAG, "Import failed", t)
        false
    }
}

private fun deleteAddon(context: Context, addon: AddonUiModel, allAddons: List<AddonUiModel>) {
    try {
        val jarFile = File(addon.jarPath)
        val descFile = File(addon.jarPath + ".json")

        if (jarFile.exists()) jarFile.delete()
        if (descFile.exists()) descFile.delete()

        // Also clean addon data directory
        val dataDir = if (addon.isSystem) {
            File("/data/pixelparts/system_addons_data", addon.id)
        } else {
            File(addon.jarPath.removeSuffix(".jar") + "_data")
        }
        if (dataDir.exists()) dataDir.deleteRecursively()

        // Clean whitelist — only remove packages not needed by other addons
        val otherAddonsTargets = allAddons
            .filter { it.id != addon.id && it.enabled }
            .flatMap { getEffectiveTargets(it) }
            .toSet()
        for (pkg in getEffectiveTargets(addon)) {
            if (pkg !in BUILTIN_WHITELIST && pkg !in otherAddonsTargets) {
                removeFromWhitelist(context, pkg)
            }
        }

        // Clean settings
        try {
            Settings.Global.putString(context.contentResolver, "${ADDON_PREFIX}${addon.id}_enabled", null)
            Settings.Global.putString(context.contentResolver, "${ADDON_PREFIX}${addon.id}_packages", null)
            Settings.Global.putString(context.contentResolver, "${ADDON_PREFIX}${addon.id}_scope_mode", null)
        } catch (_: Throwable) {}

        Log.d(TAG, "Deleted addon: ${addon.id}")
    } catch (t: Throwable) {
        Log.e(TAG, "Delete failed", t)
    }
}

// =====================================================================
// Load installed apps for package picker
// =====================================================================

private fun loadInstalledApps(pm: PackageManager): List<AppInfoItem> {
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    return apps.filter { it.packageName != IGNORED_PACKAGE }.map { info ->
        AppInfoItem(
            packageName = info.packageName,
            label = try { pm.getApplicationLabel(info).toString() } catch (_: Throwable) { info.packageName },
            icon = try { pm.getApplicationIcon(info) } catch (_: Throwable) { null },
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        )
    }.sortedBy { it.label.lowercase() }
}

// =====================================================================
// Composable: Full Addon Manager Section
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AddonManagerSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var addons by remember { mutableStateOf<List<AddonUiModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedAddon by remember { mutableStateOf<AddonUiModel?>(null) }
    var showDeleteDialog by remember { mutableStateOf<AddonUiModel?>(null) }
    var safeModeActive by remember { mutableStateOf(isSafeModeActive(context)) }
    var highlightedAddonId by remember { mutableStateOf<String?>(null) }
    val bringIntoViewRequesters = remember { mutableMapOf<String, BringIntoViewRequester>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            addons = scanAddons(context)
            syncWhitelist(context, addons)
        }
        isLoading = false
    }

    fun refreshAddons() {
        scope.launch {
            isLoading = true
            withContext(Dispatchers.IO) {
                addons = scanAddons(context)
                syncWhitelist(context, addons)
            }
            isLoading = false
        }
    }

    val importSuccessMsg = dynamicStringResource(R.string.addon_import_success)
    val importFailedMsg = dynamicStringResource(R.string.addon_import_failed)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val success = withContext(Dispatchers.IO) { importAddonJar(context, uri) }
                Toast.makeText(
                    context,
                    if (success) importSuccessMsg else importFailedMsg,
                    Toast.LENGTH_SHORT
                ).show()
                if (success) refreshAddons()
            }
        }
    }

    val systemAddons = addons.filter { it.isSystem }
    val userAddons = addons.filter { !it.isSystem }
    val activeCount = addons.count { it.enabled }

    Column(modifier = modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dynamicStringResource(R.string.addon_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    dynamicStringResource(R.string.addon_count, userAddons.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    dynamicStringResource(R.string.addon_active_count, activeCount, addons.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { refreshAddons() }) {
                Icon(Icons.Rounded.Refresh, dynamicStringResource(R.string.menu_refresh), tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Import button card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { filePickerLauncher.launch("application/java-archive") }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Add, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dynamicStringResource(R.string.addon_import),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        dynamicStringResource(R.string.addon_import_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    Icons.Rounded.FileOpen, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // === Active apps section ===
        if (!isLoading) {
            val activeAppsMap = remember(addons) { buildActiveAppsMap(addons) }
            if (activeAppsMap.isNotEmpty()) {
                ActiveAppsSection(
                    activeAppsMap = activeAppsMap,
                    onAddonClick = { addonId ->
                        scope.launch {
                            highlightedAddonId = addonId
                            bringIntoViewRequesters[addonId]?.bringIntoView()
                            delay(1500)
                            highlightedAddonId = null
                        }
                    },
                    onDisableAddon = { addonId ->
                        val addon = addons.find { it.id == addonId } ?: return@ActiveAppsSection
                        writeAddonEnabled(context, addonId, false)
                        val updatedList = addons.map { if (it.id == addonId) it.copy(enabled = false) else it }
                        scope.launch(Dispatchers.IO) {
                            val otherTargets = updatedList
                                .filter { it.id != addonId && it.enabled }
                                .flatMap { getEffectiveTargets(it) }
                                .toSet()
                            for (pkg in getEffectiveTargets(addon)) {
                                if (pkg !in BUILTIN_WHITELIST && pkg !in otherTargets) {
                                    removeFromWhitelist(context, pkg)
                                }
                            }
                        }
                        addons = updatedList
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }

        if (!isLoading) {
            // === Safe mode banner ===
            if (safeModeActive) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Warning, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                dynamicStringResource(R.string.addon_safe_mode_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            dynamicStringResource(R.string.addon_safe_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                exitAddonSafeMode(context)
                                safeModeActive = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(dynamicStringResource(R.string.addon_safe_mode_exit))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // === System addons section ===
            SectionHeader(dynamicStringResource(R.string.addon_section_system))

            if (systemAddons.isEmpty()) {
                EmptySectionCard(
                    title = dynamicStringResource(R.string.addon_system_empty_title),
                    description = dynamicStringResource(R.string.addon_system_empty_desc)
                )
            } else {
                for (addon in systemAddons) {
                    val requester = remember { BringIntoViewRequester() }
                    bringIntoViewRequesters[addon.id] = requester
                    AddonCard(
                        addon = addon,
                        isSystem = true,
                        isHighlighted = highlightedAddonId == addon.id,
                        bringIntoViewRequester = requester,
                        onToggle = { enabled ->
                            writeAddonEnabled(context, addon.id, enabled)
                            val updatedList = addons.map { if (it.id == addon.id) it.copy(enabled = enabled) else it }
                            scope.launch(Dispatchers.IO) {
                                if (enabled) {
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
                                    }
                                } else {
                                    val otherTargets = updatedList
                                        .filter { it.id != addon.id && it.enabled }
                                        .flatMap { getEffectiveTargets(it) }
                                        .toSet()
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST && pkg !in otherTargets) {
                                            removeFromWhitelist(context, pkg)
                                        }
                                    }
                                }
                            }
                            addons = updatedList
                        },
                        onClick = { selectedAddon = addon },
                        onDelete = {}
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // === User addons section ===
            SectionHeader(dynamicStringResource(R.string.addon_section_user))

            if (userAddons.isEmpty()) {
                EmptySectionCard(
                    title = dynamicStringResource(R.string.addon_user_empty_title),
                    description = dynamicStringResource(R.string.addon_user_empty_desc)
                )
            } else {
                for (addon in userAddons) {
                    val requester = remember { BringIntoViewRequester() }
                    bringIntoViewRequesters[addon.id] = requester
                    AddonCard(
                        addon = addon,
                        isSystem = false,
                        isHighlighted = highlightedAddonId == addon.id,
                        bringIntoViewRequester = requester,
                        onToggle = { enabled ->
                            writeAddonEnabled(context, addon.id, enabled)
                            val updatedList = addons.map { if (it.id == addon.id) it.copy(enabled = enabled) else it }
                            scope.launch(Dispatchers.IO) {
                                if (enabled) {
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
                                    }
                                } else {
                                    val otherTargets = updatedList
                                        .filter { it.id != addon.id && it.enabled }
                                        .flatMap { getEffectiveTargets(it) }
                                        .toSet()
                                    for (pkg in getEffectiveTargets(addon)) {
                                        if (pkg !in BUILTIN_WHITELIST && pkg !in otherTargets) {
                                            removeFromWhitelist(context, pkg)
                                        }
                                    }
                                }
                            }
                            addons = updatedList
                        },
                        onClick = { selectedAddon = addon },
                        onDelete = { showDeleteDialog = addon }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    // Detail dialog
    selectedAddon?.let { addon ->
        AddonDetailDialog(
            addon = addon,
            onDismiss = { selectedAddon = null },
            onSave = { updatedAddon ->
                val oldTargets = getEffectiveTargets(addon)
                val newTargets = getEffectiveTargets(updatedAddon)

                writeAddonEnabled(context, updatedAddon.id, updatedAddon.enabled)
                writeScopeMode(context, updatedAddon.id, updatedAddon.scopeMode)
                writeCustomTargets(context, updatedAddon.id, updatedAddon.customTargets)

                val updatedAddons = addons.map { if (it.id == updatedAddon.id) updatedAddon else it }

                scope.launch(Dispatchers.IO) {
                    // Add new targets to whitelist
                    for (pkg in newTargets) {
                        if (pkg !in BUILTIN_WHITELIST) addToWhitelist(context, pkg)
                    }
                    // Remove targets that were dropped — but only if no other addon uses them
                    val removedTargets = oldTargets - newTargets
                    if (removedTargets.isNotEmpty()) {
                        val otherAddonsTargets = updatedAddons
                            .filter { it.id != updatedAddon.id && it.enabled }
                            .flatMap { getEffectiveTargets(it) }
                            .toSet()
                        for (pkg in removedTargets) {
                            if (pkg !in BUILTIN_WHITELIST && pkg !in otherAddonsTargets) {
                                removeFromWhitelist(context, pkg)
                            }
                        }
                    }
                }
                addons = updatedAddons
                selectedAddon = null
            }
        )
    }

    // Delete dialog
    showDeleteDialog?.let { addon ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(dynamicStringResource(R.string.addon_delete_title)) },
            text = {
                Text(dynamicStringResource(R.string.addon_delete_confirm, addon.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { deleteAddon(context, addon, addons) }
                            showDeleteDialog = null
                            refreshAddons()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text(dynamicStringResource(R.string.addon_btn_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(dynamicStringResource(R.string.addon_btn_cancel)) }
            }
        )
    }
}

// =====================================================================
// Addon Card — with expandable settings panel
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AddonCard(
    addon: AddonUiModel,
    isSystem: Boolean = false,
    isHighlighted: Boolean = false,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val effectiveTargets = getEffectiveTargets(addon)
    val contentAlpha = if (addon.enabled) 1f else 0.5f
    var settingsExpanded by remember { mutableStateOf(false) }

    // Pulsing white highlight like Android system settings
    var highlightVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            highlightVisible = true
        } else {
            highlightVisible = false
        }
    }
    val infiniteTransition = rememberInfiniteTransition(label = "highlightPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Parse custom card color
    val cardContainerColor = remember(addon.cardColor) {
        if (addon.cardColor.isNotEmpty()) {
            try { Color(android.graphics.Color.parseColor(addon.cardColor)) }
            catch (_: Throwable) { null }
        } else null
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor ?: MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (bringIntoViewRequester != null) Modifier.bringIntoViewRequester(bringIntoViewRequester) else Modifier)
            .clickable(onClick = onClick)
    ) {
        // Composable to render background layers
        @Composable
        fun BackgroundLayers(modifier: Modifier = Modifier) {
            if (addon.backgroundBitmap != null) {
                val bgImageBitmap = remember(addon.id) { addon.backgroundBitmap.asImageBitmap() }
                val bgAlphaFloat = (addon.backgroundAlpha / 100f).coerceIn(0f, 1f) *
                    if (addon.enabled) 1f else 0.4f
                Image(
                    bitmap = bgImageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .alpha(bgAlphaFloat)
                        .then(
                            if (addon.backgroundBlur && addon.backgroundBlurRadius > 0)
                                Modifier.blur(addon.backgroundBlurRadius.dp)
                            else Modifier
                        )
                )
                if (addon.backgroundMode == "gradient" && addon.backgroundGradientSteps.size >= 2) {
                    val surfaceColor = cardContainerColor ?: MaterialTheme.colorScheme.surface
                    val gradientColors = addon.backgroundGradientSteps.map { opacity ->
                        surfaceColor.copy(alpha = (opacity / 100f).coerceIn(0f, 1f))
                    }
                    Box(modifier = modifier.background(Brush.verticalGradient(colors = gradientColors)))
                }
            }
        }

        if (addon.backgroundScope == "header") {
            // Background covers only the header, not the expandable settings
            Box {
                Column {
                    Box {
                        BackgroundLayers(Modifier.matchParentSize())
                        Column(modifier = Modifier.padding(16.dp)) {
                            AddonCardHeader(addon, contentAlpha, effectiveTargets, settingsExpanded,
                                onToggle, onClick, onDelete, isSystem,
                                onSettingsToggle = { settingsExpanded = !settingsExpanded })
                        }
                    }
                    // Settings panel outside background
                    AddonCardSettings(addon, settingsExpanded)
                }
                // Highlight overlay on top of everything
                if (highlightVisible) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = pulseAlpha))
                    )
                }
            }
        } else {
            // "full" — background extends to the entire card including settings
            Box {
                BackgroundLayers(Modifier.matchParentSize())
                Column(modifier = Modifier.padding(16.dp)) {
                    AddonCardHeader(addon, contentAlpha, effectiveTargets, settingsExpanded,
                        onToggle, onClick, onDelete, isSystem,
                        onSettingsToggle = { settingsExpanded = !settingsExpanded })
                    AddonCardSettings(addon, settingsExpanded)
                }
                // Highlight overlay on top of everything
                if (highlightVisible) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = pulseAlpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun AddonCardHeader(
    addon: AddonUiModel,
    contentAlpha: Float,
    effectiveTargets: Set<String>,
    settingsExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isSystem: Boolean,
    onSettingsToggle: () -> Unit
) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Custom icon or default Extension icon
                if (addon.iconBitmap != null) {
                    val iconImageBitmap = remember(addon.id) { addon.iconBitmap.asImageBitmap() }
                    Image(
                        bitmap = iconImageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (addon.enabled) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Extension, null,
                        modifier = Modifier.size(20.dp),
                        tint = if (addon.enabled) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(contentAlpha)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            addon.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "v${addon.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        addon.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        addon.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = addon.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            if (addon.description.isNotEmpty()) {
                Text(
                    addon.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .alpha(contentAlpha)
                )
            }

            if (effectiveTargets.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(contentAlpha),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Outlined.FilterAlt, null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        effectiveTargets.joinToString(", ") { it.substringAfterLast(".") },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val scopeLabel = when (addon.scopeMode) {
                    0 -> dynamicStringResource(R.string.addon_scope_label_default)
                    1 -> dynamicStringResource(R.string.addon_scope_label_custom)
                    2 -> dynamicStringResource(R.string.addon_scope_label_merge)
                    else -> dynamicStringResource(R.string.addon_scope_label_default)
                }

                SuggestionChip(
                    onClick = onClick,
                    label = { Text(scopeLabel, style = MaterialTheme.typography.labelSmall) },
                    icon = { Icon(Icons.Outlined.Tune, null, modifier = Modifier.size(14.dp)) },
                    modifier = Modifier.height(26.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Gear icon — only if addon has settings
                    if (addon.settings.isNotEmpty()) {
                        IconButton(
                            onClick = onSettingsToggle,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (settingsExpanded) Icons.Filled.Settings
                                else Icons.Outlined.Settings,
                                dynamicStringResource(R.string.addon_settings_title),
                                tint = MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (settingsExpanded) 1f else 0.7f
                                ),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (!isSystem) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Outlined.Delete, dynamicStringResource(R.string.addon_btn_remove),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
}

@Composable
private fun AddonCardSettings(
    addon: AddonUiModel,
    settingsExpanded: Boolean
) {
            // ---- Expandable settings panel ----
            AnimatedVisibility(
                visible = settingsExpanded && addon.settings.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier
                    .padding(top = 8.dp, start = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                             end = if (addon.backgroundScope == "header") 16.dp else 0.dp,
                             bottom = if (addon.backgroundScope == "header") 16.dp else 0.dp)
                    .clickable(onClick = {}) // consume clicks — prevent Card onClick (detail dialog)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        dynamicStringResource(R.string.addon_settings_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    for (setting in addon.settings) {
                        AddonSettingControl(
                            setting = setting,
                            addonId = addon.id,
                            addonJarPath = addon.jarPath,
                            isSystemAddon = addon.isSystem,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
}

// =====================================================================
// Single setting control — dispatches by type
// =====================================================================

@Composable
private fun AddonSettingControl(
    setting: AddonSettingDef,
    addonId: String,
    addonJarPath: String,
    isSystemAddon: Boolean,
    modifier: Modifier = Modifier
) {
    when (setting.type) {
        SettingType.INT -> IntSliderSettingControl(setting, modifier)
        SettingType.FLOAT -> FloatSliderSettingControl(setting, modifier)
        SettingType.STRING -> StringSettingControl(setting, modifier)
        SettingType.SELECT -> SelectSettingControl(setting, modifier)
        SettingType.FILE -> FileSettingControl(setting, addonId, addonJarPath, isSystemAddon, modifier)
        SettingType.TOGGLE, SettingType.SWITCH -> SwitchSettingControl(setting, modifier)
        SettingType.CHECKBOX -> CheckboxSettingControl(setting, modifier)
    }
}

// ---------- TOGGLE / SWITCH ----------

@Composable
private fun SwitchSettingControl(setting: AddonSettingDef, modifier: Modifier) {
    val context = LocalContext.current
    val defaultVal = if (setting.defaultBool) 1 else setting.defaultInt
    var checked by remember {
        mutableStateOf(readSettingInt(context, setting.provider, setting.key, defaultVal) != 0)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                checked = !checked
                writeSettingInt(context, setting.provider, setting.key, if (checked) 1 else 0)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (setting.description.isNotEmpty()) {
                Text(
                    setting.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                writeSettingInt(context, setting.provider, setting.key, if (it) 1 else 0)
            }
        )
    }
}

// ---------- CHECKBOX ----------

@Composable
private fun CheckboxSettingControl(setting: AddonSettingDef, modifier: Modifier) {
    val context = LocalContext.current
    val defaultVal = if (setting.defaultBool) 1 else setting.defaultInt
    var checked by remember {
        mutableStateOf(readSettingInt(context, setting.provider, setting.key, defaultVal) != 0)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                checked = !checked
                writeSettingInt(context, setting.provider, setting.key, if (checked) 1 else 0)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = {
                checked = it
                writeSettingInt(context, setting.provider, setting.key, if (it) 1 else 0)
            }
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (setting.description.isNotEmpty()) {
                Text(
                    setting.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------- INT SLIDER ----------

@Composable
private fun IntSliderSettingControl(setting: AddonSettingDef, modifier: Modifier) {
    val context = LocalContext.current
    var value by remember {
        mutableIntStateOf(readSettingInt(context, setting.provider, setting.key, setting.defaultInt))
    }
    var showManualInput by remember { mutableStateOf(false) }

    Column(modifier = modifier
        .fillMaxWidth()
        .clickable { showManualInput = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (setting.description.isNotEmpty()) {
                    Text(
                        setting.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(onClick = { showManualInput = true }) {
                Text(
                    "$value${if (setting.unit.isNotEmpty()) " ${setting.unit}" else ""}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    value = setting.defaultInt
                    writeSettingInt(context, setting.provider, setting.key, value)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Rounded.Refresh, dynamicStringResource(R.string.btn_default), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(4.dp))
            Slider(
                value = value.toFloat(),
                onValueChange = { value = it.toInt() },
                valueRange = setting.min..setting.max,
                steps = if (setting.step > 1f) ((setting.max - setting.min) / setting.step).toInt() - 1 else 0,
                onValueChangeFinished = { writeSettingInt(context, setting.provider, setting.key, value) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showManualInput) {
        ManualIntInputDialog(
            title = setting.title,
            currentValue = value,
            min = setting.min.toInt(),
            max = setting.max.toInt(),
            unit = setting.unit,
            defaultValue = setting.defaultInt,
            onDismiss = { showManualInput = false },
            onConfirm = { newVal ->
                value = newVal
                writeSettingInt(context, setting.provider, setting.key, newVal)
                showManualInput = false
            }
        )
    }
}

// ---------- FLOAT SLIDER ----------

@Composable
private fun FloatSliderSettingControl(setting: AddonSettingDef, modifier: Modifier) {
    val context = LocalContext.current
    var value by remember {
        mutableFloatStateOf(readSettingFloat(context, setting.provider, setting.key, setting.defaultFloat))
    }
    var showManualInput by remember { mutableStateOf(false) }

    Column(modifier = modifier
        .fillMaxWidth()
        .clickable { showManualInput = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (setting.description.isNotEmpty()) {
                    Text(
                        setting.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            TextButton(onClick = { showManualInput = true }) {
                Text(
                    String.format("%.2f%s", value, if (setting.unit.isNotEmpty()) " ${setting.unit}" else ""),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    value = setting.defaultFloat
                    writeSettingFloat(context, setting.provider, setting.key, value)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Rounded.Refresh, dynamicStringResource(R.string.btn_default), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(4.dp))
            Slider(
                value = value,
                onValueChange = { value = it },
                valueRange = setting.min..setting.max,
                onValueChangeFinished = { writeSettingFloat(context, setting.provider, setting.key, value) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showManualInput) {
        ManualFloatInputDialog(
            title = setting.title,
            currentValue = value,
            min = setting.min,
            max = setting.max,
            unit = setting.unit,
            defaultValue = setting.defaultFloat,
            onDismiss = { showManualInput = false },
            onConfirm = { newVal ->
                value = newVal
                writeSettingFloat(context, setting.provider, setting.key, newVal)
                showManualInput = false
            }
        )
    }
}

// ---------- STRING INPUT ----------

@Composable
private fun StringSettingControl(setting: AddonSettingDef, modifier: Modifier) {
    val context = LocalContext.current
    var value by remember {
        mutableStateOf(readSettingString(context, setting.provider, setting.key) ?: setting.defaultString)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (setting.description.isNotEmpty()) {
            Text(
                setting.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                writeSettingString(context, setting.provider, setting.key, it)
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = {
                if (value != setting.defaultString) {
                    IconButton(onClick = {
                        value = setting.defaultString
                        writeSettingString(context, setting.provider, setting.key, setting.defaultString)
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Refresh, dynamicStringResource(R.string.btn_default), Modifier.size(16.dp))
                    }
                }
            }
        )
    }
}

// ---------- SELECT (dropdown) ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectSettingControl(setting: AddonSettingDef, modifier: Modifier) {
    val context = LocalContext.current
    val currentValue = readSettingString(context, setting.provider, setting.key) ?: setting.defaultString
    var expanded by remember { mutableStateOf(false) }
    var selectedValue by remember { mutableStateOf(currentValue) }
    val selectedLabel = setting.options.find { it.value == selectedValue }?.label ?: selectedValue

    Column(modifier = modifier.fillMaxWidth()) {
        Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (setting.description.isNotEmpty()) {
            Text(
                setting.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                setting.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            selectedValue = option.value
                            writeSettingString(context, setting.provider, setting.key, option.value)
                            expanded = false
                        },
                        trailingIcon = {
                            if (option.value == selectedValue) {
                                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }
        }
    }
}

// ---------- FILE PICKER ----------

@Composable
private fun FileSettingControl(
    setting: AddonSettingDef,
    addonId: String,
    addonJarPath: String,
    isSystemAddon: Boolean,
    modifier: Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPath = readSettingString(context, setting.provider, setting.key) ?: ""
    var filePath by remember { mutableStateOf(currentPath) }
    val notSetLabel = dynamicStringResource(R.string.addon_file_not_set)
    val fileSavedMsg = dynamicStringResource(R.string.addon_file_saved)
    val fileCopyFailedMsg = dynamicStringResource(R.string.addon_file_copy_failed)
    val fileName = if (filePath.isNotEmpty()) File(filePath).name else notSetLabel

    // System addons: writable data in /data/pixelparts/system_addons_data/{id}/
    // User addons: data next to JAR  e.g. /data/pixelparts/addons/my_addon_data/
    val dataDir = if (isSystemAddon) {
        File("/data/pixelparts/system_addons_data", addonId)
    } else {
        File(File(addonJarPath).parent, File(addonJarPath).nameWithoutExtension + "_data")
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val destPath = withContext(Dispatchers.IO) {
                    copyFileToAddonData(context, uri, dataDir, setting.key)
                }
                if (destPath != null) {
                    filePath = destPath
                    writeSettingString(context, setting.provider, setting.key, destPath)
                    Toast.makeText(context, fileSavedMsg, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, fileCopyFailedMsg, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(setting.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (setting.description.isNotEmpty()) {
            Text(
                setting.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.InsertDriveFile, null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        fileName,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (filePath.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = { filePicker.launch(setting.mimeType) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(20.dp))
            }
            if (filePath.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        filePath = ""
                        writeSettingString(context, setting.provider, setting.key, "")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.Close, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** Copy a picker URI into the addon's data directory. Returns absolute path or null. */
private fun copyFileToAddonData(context: Context, uri: Uri, dataDir: File, settingKey: String): String? {
    return try {
        if (!dataDir.exists()) dataDir.mkdirs()

        // Determine file name
        var fileName = "${settingKey}_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }

        val target = File(dataDir, fileName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        if (!target.exists() || target.length() == 0L) {
            Log.e(TAG, "copyFileToAddonData: copy failed")
            return null
        }
        target.setReadable(true, false)

        target.absolutePath
    } catch (t: Throwable) {
        Log.e(TAG, "copyFileToAddonData failed", t)
        null
    }
}

// ---------- Manual input dialogs ----------

@Composable
private fun ManualIntInputDialog(
    title: String,
    currentValue: Int,
    min: Int,
    max: Int,
    unit: String,
    defaultValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentValue.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(dynamicStringResource(R.string.addon_range_format, min.toString(), max.toString()), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        val n = it.toIntOrNull()
                        isError = n == null || n < min || n > max
                    },
                    isError = isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = { if (unit.isNotEmpty()) Text(unit) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onConfirm(defaultValue)
                }) { Text(dynamicStringResource(R.string.btn_default)) }
                Button(onClick = {
                    val n = text.toIntOrNull()
                    if (n != null && n in min..max) onConfirm(n)
                    else isError = true
                }) { Text(dynamicStringResource(R.string.btn_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.addon_btn_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun ManualFloatInputDialog(
    title: String,
    currentValue: Float,
    min: Float,
    max: Float,
    unit: String,
    defaultValue: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var text by remember { mutableStateOf(currentValue.toString()) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(dynamicStringResource(R.string.addon_range_format, min.toString(), max.toString()), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        val n = it.toFloatOrNull()
                        isError = n == null || n < min || n > max
                    },
                    isError = isError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { if (unit.isNotEmpty()) Text(unit) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onConfirm(defaultValue)
                }) { Text(dynamicStringResource(R.string.btn_default)) }
                Button(onClick = {
                    val n = text.toFloatOrNull()
                    if (n != null && n >= min && n <= max) onConfirm(n)
                    else isError = true
                }) { Text(dynamicStringResource(R.string.btn_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.addon_btn_cancel)) } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

// =====================================================================
// Active Apps Section — expandable per-app module lists
// =====================================================================

@Composable
private fun ActiveAppsSection(
    activeAppsMap: Map<String, List<AddonUiModel>>,
    onAddonClick: (String) -> Unit,
    onDisableAddon: (String) -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Apps, null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dynamicStringResource(R.string.addon_active_apps_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        dynamicStringResource(R.string.addon_active_apps_desc, activeAppsMap.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    for ((pkg, addons) in activeAppsMap.entries.sortedBy { it.key }) {
                        ActiveAppRow(
                            packageName = pkg,
                            pm = pm,
                            addons = addons,
                            onAddonClick = onAddonClick,
                            onDisableAddon = onDisableAddon
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveAppRow(
    packageName: String,
    pm: PackageManager,
    addons: List<AddonUiModel>,
    onAddonClick: (String) -> Unit,
    onDisableAddon: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val appLabel = remember(packageName) {
        try {
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Throwable) { packageName }
    }
    val appIcon = remember(packageName) {
        try {
            pm.getApplicationIcon(packageName)
        } catch (_: Throwable) { null }
    }

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            if (appIcon != null) {
                val bitmap = remember(packageName) {
                    try { appIcon.toBitmap(width = 64, height = 64).asImageBitmap() }
                    catch (_: Throwable) { null }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    AppPlaceholderIcon()
                }
            } else {
                AppPlaceholderIcon()
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    appLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    dynamicStringResource(R.string.addon_active_apps_modules, addons.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(start = 38.dp, bottom = 4.dp)) {
                for (addon in addons) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onAddonClick(addon.id) }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Extension, null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            addon.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "v${addon.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { onDisableAddon(addon.id) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Close, null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPlaceholderIcon() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Android, null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================
// Empty state
// =====================================================================

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmptySectionCard(title: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Extension, null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// =====================================================================
// Detail Dialog — Addon settings + App Picker
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddonDetailDialog(
    addon: AddonUiModel,
    onDismiss: () -> Unit,
    onSave: (AddonUiModel) -> Unit
) {
    var enabled by remember { mutableStateOf(addon.enabled) }
    var scopeMode by remember { mutableIntStateOf(addon.scopeMode) }
    var customTargets by remember { mutableStateOf(addon.customTargets) }
    var showAppPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (addon.iconBitmap != null) {
                        val iconImageBitmap = remember(addon.id) { addon.iconBitmap.asImageBitmap() }
                        Image(
                            bitmap = iconImageBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Extension, null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            addon.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${addon.author} • v${addon.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // ID and entryClass info
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ID: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            addon.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (addon.entryClass.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Class: ",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                addon.entryClass,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (addon.description.isNotEmpty()) {
                    Text(
                        addon.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                // Scope mode
                Text(
                    dynamicStringResource(R.string.addon_target_scope),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                val scopeLabels = listOf(
                    dynamicStringResource(R.string.addon_scope_default),
                    dynamicStringResource(R.string.addon_scope_custom),
                    dynamicStringResource(R.string.addon_scope_merge)
                )
                scopeLabels.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scopeMode = index }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = scopeMode == index,
                            onClick = { scopeMode = index }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Default targets (read-only)
                if (addon.defaultTargets.isNotEmpty() && (scopeMode == 0 || scopeMode == 2)) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        dynamicStringResource(R.string.addon_targets_default),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    for (pkg in addon.defaultTargets) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Apps, null,
                                Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                pkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            if (pkg in BUILTIN_WHITELIST) {
                                Text(
                                    dynamicStringResource(R.string.addon_builtin_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Custom targets — "Select apps" button + chips
                if (scopeMode == 1 || scopeMode == 2) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        dynamicStringResource(R.string.addon_targets_custom),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (customTargets.isNotEmpty()) {
                        Text(
                            dynamicStringResource(R.string.addon_apps_selected, customTargets.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Checklist, null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(dynamicStringResource(R.string.addon_select_apps))
                    }

                    // Chips for selected custom targets
                    if (customTargets.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (pkg in customTargets.sorted()) {
                                InputChip(
                                    selected = true,
                                    onClick = { customTargets = customTargets - pkg },
                                    label = {
                                        Text(
                                            pkg.substringAfterLast("."),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Rounded.Close, null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.addon_btn_cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(
                            addon.copy(
                                enabled = enabled,
                                scopeMode = scopeMode,
                                customTargets = customTargets
                            )
                        )
                    }) { Text(dynamicStringResource(R.string.addon_btn_save)) }
                }
            }
        }
    }

    // Full-screen app picker dialog (LSPosed-style)
    if (showAppPicker) {
        AppPickerDialog(
            defaultTargets = addon.defaultTargets,
            selectedPackages = customTargets,
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                customTargets = selected
                showAppPicker = false
            }
        )
    }
}

// =====================================================================
// Full-screen App Picker Dialog (LSPosed-style)
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerDialog(
    defaultTargets: Set<String>,
    selectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val context = LocalContext.current

    var allApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(selectedPackages) }
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Load apps in background
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            allApps = loadInstalledApps(context.packageManager)
        }
        isLoading = false
    }

    // Filter and sort: selected/default on top, then alphabetical
    val filteredApps = remember(allApps, searchQuery, showSystemApps, selected, defaultTargets) {
        val query = searchQuery.trim().lowercase()
        allApps
            .filter { app ->
                // Filter system apps unless option enabled or app is selected/default
                if (!showSystemApps && app.isSystem &&
                    app.packageName !in selected &&
                    app.packageName !in defaultTargets
                ) return@filter false
                // Filter by search query
                if (query.isNotEmpty()) {
                    val matchesLabel = app.label.lowercase().contains(query)
                    val matchesPackage = app.packageName.lowercase().contains(query)
                    return@filter matchesLabel || matchesPackage
                }
                true
            }
            .sortedWith(
                compareByDescending<AppInfoItem> {
                    it.packageName in selected || it.packageName in defaultTargets
                }.thenBy { it.label.lowercase() }
            )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                if (isSearchActive) {
                    // Search toolbar
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = {
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back))
                            }
                        },
                        title = {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(dynamicStringResource(R.string.addon_search_apps)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Filled.Close, null)
                                        }
                                    }
                                }
                            )
                        }
                    )
                } else {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.btn_close))
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    dynamicStringResource(R.string.addon_select_apps),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    dynamicStringResource(R.string.addon_selected_count, selected.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Filled.Search, null)
                            }
                            IconButton(onClick = { showSystemApps = !showSystemApps }) {
                                Icon(
                                    if (showSystemApps) Icons.Filled.VisibilityOff
                                    else Icons.Filled.Visibility,
                                    if (showSystemApps) dynamicStringResource(R.string.addon_hide_system)
                                    else dynamicStringResource(R.string.addon_show_system)
                                )
                            }
                        }
                    )
                }
            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.addon_btn_cancel)) }
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = { onConfirm(selected) }) {
                            Text(dynamicStringResource(R.string.addon_confirm_count, selected.size))
                        }
                    }
                }
            }
        ) { padding ->
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        val isDefault = app.packageName in defaultTargets
                        val isChecked = app.packageName in selected

                        AppPickerItem(
                            app = app,
                            isChecked = isChecked,
                            isDefault = isDefault,
                            onToggle = { checked ->
                                selected = if (checked) selected + app.packageName
                                else selected - app.packageName
                            }
                        )
                    }

                    if (filteredApps.isEmpty() && !isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (searchQuery.isNotEmpty()) dynamicStringResource(R.string.addon_no_apps_match, searchQuery)
                                    else dynamicStringResource(R.string.addon_no_apps_available),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// Single app row in the picker (LSPosed-style)
// =====================================================================

@Composable
private fun AppPickerItem(
    app: AppInfoItem,
    isChecked: Boolean,
    isDefault: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val bgColor = if (isChecked || isDefault) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { onToggle(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon
        if (app.icon != null) {
            val bitmap = remember(app.packageName) {
                try {
                    app.icon.toBitmap(width = 80, height = 80).asImageBitmap()
                } catch (_: Throwable) { null }
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                DefaultAppIcon()
            }
        } else {
            DefaultAppIcon()
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isDefault) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            dynamicStringResource(R.string.addon_default_target_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Checkbox(
            checked = isChecked || isDefault,
            onCheckedChange = { onToggle(it) },
            enabled = !isDefault  // Default targets are always checked, not removable
        )
    }
}

@Composable
private fun DefaultAppIcon() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.Android, null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
