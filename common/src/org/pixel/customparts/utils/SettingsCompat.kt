package org.pixel.customparts.utils

import android.content.Context
import android.provider.Settings
import org.pixel.customparts.AppConfig
import org.pixel.customparts.SettingsKeys












object SettingsCompat {
    private const val PINE_INJECT_SUFFIX = "_pine"
    private const val XPOSED_SUFFIX = "_xposed"

    
    private val SUFFIXED_KEY_BASES = setOf(
        "launcher_recents_modify_enable",
        "launcher_recents_disable_livetile",
        "launcher_recents_carousel_scale",
        "launcher_recents_carousel_spacing",
        "launcher_recents_carousel_alpha",
        "launcher_recents_carousel_blur_radius",
        "launcher_recents_carousel_blur_overflow",
        "launcher_recents_carousel_tint_color",
        "launcher_recents_carousel_tint_intensity",
        "launcher_recents_carousel_icon_offset_x",
        "launcher_recents_carousel_icon_offset_y",
        "launcher_recents_scale_enable",
        "launcher_recents_scale_percent",
        "launcher_homepage_sizer",
        "launcher_homepage_h",
        "launcher_homepage_v",
        "launcher_homepage_hide_text",
        "launcher_homepage_icon_size",
        "launcher_homepage_text_mode",
        "launcher_menupage_sizer",
        "launcher_menupage_h",
        "launcher_menupage_row_height",
        "launcher_menupage_hide_text",
        "launcher_menupage_icon_size",
        "launcher_menupage_text_mode",
        "launcher_suggestion_icon_size",
        "launcher_suggestion_text_mode",
        "launcher_search_icon_size",
        "launcher_search_text_mode",
        "launcher_suggestion_disable",
        "launcher_dock_enable",
        "launcher_hidden_search",
        "launcher_hidden_dock",
        "launcher_dock_padding",
        "launcher_hotseat_icons",
        "launcher_hotseat_hide_text",
        "launcher_hotseat_icon_size",
        "launcher_hotseat_text_mode",
        "launcher_disable_google_feed",
        "launcher_disable_top_widget",
        "launcher_clear_all",
        "launcher_clear_all_hide_actions_row",
        "launcher_replace_on_clear",
        "launcher_clear_all_bottom_margin",
        "launcher_debug_enable",
        "launcher_padding_homepage",
        "launcher_padding_dock",
        "launcher_padding_search",
        "launcher_padding_dots",
        "launcher_padding_dots_x",
        "doze_double_tap_hook",
        "launcher_dt2s_enabled",
        "overscroll_enabled",
        "pixelparts_battery_info_refresh_interval_ms",
        "pixelparts_battery_info_average_mode"
    )
    private fun isSuffixedKey(key: String): Boolean {
        return SUFFIXED_KEY_BASES.contains(stripRuntimeSuffix(key))
    }

    private fun stripRuntimeSuffix(key: String): String {
        return key.removeSuffix(PINE_INJECT_SUFFIX).removeSuffix(XPOSED_SUFFIX)
    }

    private fun activeRuntimeSuffix(): String {
        if (SettingsKeys.isPineOverride) return PINE_INJECT_SUFFIX
        return if (AppConfig.IS_XPOSED) XPOSED_SUFFIX else PINE_INJECT_SUFFIX
    }

    @JvmStatic
    fun key(base: String): String {
        val baseKey = stripRuntimeSuffix(base)
        return if (isSuffixedKey(baseKey)) {
            "$baseKey${activeRuntimeSuffix()}"
        } else {
            base
        }
    }


    @JvmStatic
    fun putInt(context: Context, key: String, value: Int) {
        Settings.Global.putInt(context.contentResolver, key(key), value)
    }

    @JvmStatic
    fun putFloat(context: Context, key: String, value: Float) {
        Settings.Global.putFloat(context.contentResolver, key(key), value)
    }

    @JvmStatic
    fun putString(context: Context, key: String, value: String?) {
        Settings.Global.putString(context.contentResolver, key(key), value)
    }

    @JvmStatic
    fun getInt(context: Context, key: String, defaultValue: Int): Int {
        return Settings.Global.getInt(context.contentResolver, key(key), defaultValue)
    }

    @JvmStatic
    fun getFloat(context: Context, key: String, defaultValue: Float): Float {
        return Settings.Global.getFloat(context.contentResolver, key(key), defaultValue)
    }

    @JvmStatic
    fun getString(context: Context, key: String, defaultValue: String?): String? {
        return Settings.Global.getString(context.contentResolver, key(key)) ?: defaultValue
    }

    @JvmStatic
    fun isEnabled(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return getInt(context, key, if (defaultValue) 1 else 0) != 0
    }
}
