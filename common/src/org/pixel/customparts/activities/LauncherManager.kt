package org.pixel.customparts.activities

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixel.customparts.utils.SettingsCompat
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.utils.runRootCommand

object LauncherManager {
    private const val KEY_NATIVE_SEARCH = SettingsKeys.PIXEL_LAUNCHER_NATIVE_SEARCH
    private fun compatKey(base: String): String = SettingsCompat.key(base)
    val KEY_DOCK_ENABLE: String
        get() = SettingsKeys.LAUNCHER_DOCK_ENABLE
    const val KEY_HIDE_SEARCH_BASE = "launcher_hidden_search"
    val KEY_HIDE_SEARCH: String
        get() = SettingsKeys.LAUNCHER_HIDE_SEARCH
    val KEY_HIDE_DOCK: String
        get() = SettingsKeys.LAUNCHER_HIDE_DOCK
    val KEY_PADDING_HOMEPAGE: String
        get() = SettingsKeys.LAUNCHER_PADDING_HOMEPAGE
    val KEY_PADDING_DOCK: String
        get() = SettingsKeys.LAUNCHER_PADDING_DOCK
    val KEY_PADDING_SEARCH: String
        get() = SettingsKeys.LAUNCHER_PADDING_SEARCH
    val KEY_PADDING_DOTS: String
        get() = SettingsKeys.LAUNCHER_PADDING_DOTS
    val KEY_PADDING_DOTS_X: String
        get() = SettingsKeys.LAUNCHER_PADDING_DOTS_X
    const val KEY_HOME_ENABLE_BASE = "launcher_homepage_sizer"
    val KEY_HOME_ENABLE: String
        get() = SettingsKeys.LAUNCHER_HOMEPAGE_SIZER
    val KEY_HOME_COLS: String
        get() = SettingsKeys.LAUNCHER_HOMEPAGE_COLS
    val KEY_HOME_ROWS: String
        get() = SettingsKeys.LAUNCHER_HOMEPAGE_ROWS
    val KEY_HOME_HIDE_TEXT: String
        get() = SettingsKeys.LAUNCHER_HOMEPAGE_HIDE_TEXT
    val KEY_HOME_ICON_SIZE: String
        get() = SettingsKeys.LAUNCHER_HOMEPAGE_ICON_SIZE
    val KEY_HOME_TEXT_MODE: String
        get() = SettingsKeys.LAUNCHER_HOMEPAGE_TEXT_MODE
    const val KEY_MENU_ENABLE_BASE = "launcher_menupage_sizer"
    val KEY_MENU_ENABLE: String
        get() = SettingsKeys.LAUNCHER_MENUPAGE_SIZER
    val KEY_MENU_COLS: String
        get() = SettingsKeys.LAUNCHER_MENUPAGE_COLS
    val KEY_MENU_HIDE_TEXT: String
        get() = SettingsKeys.LAUNCHER_MENUPAGE_HIDE_TEXT
    val KEY_MENU_ROW_HEIGHT: String
        get() = SettingsKeys.LAUNCHER_MENUPAGE_ROW_HEIGHT
    val KEY_MENU_ICON_SIZE: String
        get() = SettingsKeys.LAUNCHER_MENUPAGE_ICON_SIZE
    val KEY_MENU_TEXT_MODE: String
        get() = SettingsKeys.LAUNCHER_MENUPAGE_TEXT_MODE
    val KEY_DISABLE_TOP_WIDGET: String
        get() = SettingsKeys.LAUNCHER_DISABLE_TOP_WIDGET
    val KEY_DISABLE_GOOGLE_FEED: String
        get() = SettingsKeys.LAUNCHER_DISABLE_GOOGLE_FEED
    const val KEY_ICON_PACK = SettingsKeys.LAUNCHER_CURRENT_ICON_PACK
    val KEY_CLEAR_ALL_ENABLED: String
        get() = SettingsKeys.LAUNCHER_CLEAR_ALL_ENABLED
    val KEY_CLEAR_ALL_HIDE_ACTIONS_ROW: String
        get() = SettingsKeys.LAUNCHER_CLEAR_ALL_HIDE_ACTIONS_ROW
    val KEY_CLEAR_ALL_MODE: String
        get() = SettingsKeys.LAUNCHER_CLEAR_ALL_MODE
    val KEY_CLEAR_ALL_MARGIN: String
        get() = SettingsKeys.LAUNCHER_CLEAR_ALL_MARGIN
    val KEY_HOTSEAT_ICONS: String
        get() = SettingsKeys.LAUNCHER_HOTSEAT_ICONS
    val KEY_HOTSEAT_ICON_SIZE: String
        get() = SettingsKeys.LAUNCHER_HOTSEAT_ICON_SIZE
    fun isNativeSearchEnabled(context: Context): Boolean {
        return Settings.Global.getInt(context.contentResolver, KEY_NATIVE_SEARCH, 1) == 1
    }
    suspend fun setNativeSearchEnabled(context: Context, enabled: Boolean) = withContext(Dispatchers.IO) {
        Settings.Global.putInt(context.contentResolver, KEY_NATIVE_SEARCH, if (enabled) 1 else 0)
        val cmdValue = if (enabled) "true" else "false"
        val command = "cmd device_config override launcher enable_one_search $cmdValue"
        runRootCommand(command)
    }
    suspend fun restartLauncher(context: Context) = withContext(Dispatchers.IO) {
        runRootCommand("am force-stop com.google.android.apps.nexuslauncher")
    }
}

data class ClearAllMode(val id: Int, val labelRes: Int, val icon: ImageVector)