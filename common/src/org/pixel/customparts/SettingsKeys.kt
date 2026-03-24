package org.pixel.customparts

import org.pixel.customparts.AppConfig.IS_XPOSED

object SettingsKeys {

    val isPineOverride: Boolean
        get() {
            if (!IS_XPOSED) return false
            return try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
                val context = currentApplicationMethod.invoke(null) as? android.content.Context
                if (context != null) {
                    android.provider.Settings.Global.getInt(
                        context.contentResolver,
                        "pixelparts_xposed_to_pine",
                        0
                    ) == 1
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

    internal val suffix: String
        get() {
            if (isPineOverride) return "_pine"
            return if (IS_XPOSED) "_xposed" else "_pine"
        }
    
    val BATTERY_INFO_ENABLE: String
        get() = "pixelparts_battery_info_enable" + suffix
    val SHOW_WATTAGE: String
        get() = "pixelparts_battery_info_show_wattage" + suffix
    val SHOW_VOLTAGE: String
        get() = "pixelparts_battery_info_show_voltage" + suffix
    val SHOW_CURRENT: String
        get() = "pixelparts_battery_info_show_current" + suffix
    val SHOW_TEMP: String
        get() = "pixelparts_battery_info_show_temp" + suffix
    val SHOW_PERCENT: String
        get() = "pixelparts_battery_info_show_percent" + suffix
    val SHOW_STANDARD_STRING: String
        get() = "pixelparts_battery_info_show_standard_string" + suffix
    val SHOW_CUSTOM_SYMBOL: String
        get() = "pixelparts_battery_info_show_custom_symbol" + suffix
    val CUSTOM_SYMBOL: String
        get() = "pixelparts_battery_info_custom_symbol" + suffix
    val BATTERY_INFO_REFRESH_INTERVAL_MS: String
        get() = "pixelparts_battery_info_refresh_interval_ms" + suffix
    val BATTERY_INFO_AVERAGE_MODE: String
        get() = "pixelparts_battery_info_average_mode" + suffix

    val LAUNCHER_CLEAR_ALL_ENABLED: String
        get() = "launcher_clear_all" + suffix

    val LAUNCHER_CLEAR_ALL_HIDE_ACTIONS_ROW: String
        get() = "launcher_clear_all_hide_actions_row" + suffix

    val LAUNCHER_CLEAR_ALL_MODE: String
        get() = "launcher_replace_on_clear" + suffix

    val LAUNCHER_CLEAR_ALL_MARGIN: String
        get() = "launcher_clear_all_bottom_margin" + suffix

    val LAUNCHER_HOMEPAGE_SIZER: String
        get() = "launcher_homepage_sizer" + suffix

    val LAUNCHER_HOMEPAGE_COLS: String
        get() = "launcher_homepage_h" + suffix

    val LAUNCHER_HOMEPAGE_ROWS: String
        get() = "launcher_homepage_v" + suffix

    val LAUNCHER_HOMEPAGE_HIDE_TEXT: String
        get() = "launcher_homepage_hide_text" + suffix

    val LAUNCHER_HOMEPAGE_ICON_SIZE: String
        get() = "launcher_homepage_icon_size" + suffix

    val LAUNCHER_HOMEPAGE_TEXT_MODE: String
        get() = "launcher_homepage_text_mode" + suffix

    val LAUNCHER_MENUPAGE_SIZER: String
        get() = "launcher_menupage_sizer" + suffix

    val LAUNCHER_MENUPAGE_COLS: String
        get() = "launcher_menupage_h" + suffix

    val LAUNCHER_MENUPAGE_ROW_HEIGHT: String
        get() = "launcher_menupage_row_height" + suffix

    val LAUNCHER_MENUPAGE_HIDE_TEXT: String
        get() = "launcher_menupage_hide_text" + suffix

    val LAUNCHER_MENUPAGE_ICON_SIZE: String
        get() = "launcher_menupage_icon_size" + suffix

    val LAUNCHER_MENUPAGE_TEXT_MODE: String
        get() = "launcher_menupage_text_mode" + suffix
    
    val LAUNCHER_DOCK_ENABLE: String
        get() = "launcher_dock_enable" + suffix

    val LAUNCHER_HIDE_SEARCH: String
        get() = "launcher_hidden_search" + suffix

    val LAUNCHER_HIDE_DOCK: String
        get() = "launcher_hidden_dock" + suffix

    val LAUNCHER_DOCK_PADDING: String
        get() = "launcher_dock_padding" + suffix

    val LAUNCHER_HOTSEAT_ICONS: String
        get() = "launcher_hotseat_icons" + suffix

    val LAUNCHER_HOTSEAT_HIDE_TEXT: String
        get() = "launcher_hotseat_hide_text" + suffix

    val LAUNCHER_HOTSEAT_ICON_SIZE: String
        get() = "launcher_hotseat_icon_size" + suffix

    val LAUNCHER_HOTSEAT_TEXT_MODE: String
        get() = "launcher_hotseat_text_mode" + suffix

    val LAUNCHER_DISABLE_GOOGLE_FEED: String
        get() = "launcher_disable_google_feed" + suffix

    val LAUNCHER_DISABLE_TOP_WIDGET: String
        get() = "launcher_disable_top_widget" + suffix

    
    val LAUNCHER_DEBUG_ENABLE: String
        get() = "launcher_debug_enable" + suffix

    val LAUNCHER_RECENTS_MODIFY_ENABLE: String
        get() = "launcher_recents_modify_enable" + suffix

    val LAUNCHER_RECENTS_DISABLE_LIVETILE: String
        get() = "launcher_recents_disable_livetile" + suffix

    val LAUNCHER_RECENTS_SCALE_ENABLE: String
        get() = "launcher_recents_scale_enable" + suffix

    val LAUNCHER_RECENTS_SCALE_PERCENT: String
        get() = "launcher_recents_scale_percent" + suffix

    val LAUNCHER_RECENTS_CAROUSEL_SCALE: String
        get() = "launcher_recents_carousel_scale" + suffix

    val LAUNCHER_RECENTS_CAROUSEL_SPACING: String
        get() = "launcher_recents_carousel_spacing" + suffix

    val LAUNCHER_RECENTS_CAROUSEL_ALPHA: String
        get() = "launcher_recents_carousel_alpha" + suffix

    val LAUNCHER_RECENTS_CAROUSEL_BLUR_RADIUS: String
        get() = "launcher_recents_carousel_blur_radius" + suffix

    val LAUNCHER_RECENTS_CAROUSEL_BLUR_OVERFLOW: String
        get() = "launcher_recents_carousel_blur_overflow" + suffix

    val LAUNCHER_RECENTS_CAROUSEL_TINT_COLOR: String
        get() = "launcher_recents_carousel_tint_color" + suffix

    val LAUNCHER_RECENTS_CAROUSEL_TINT_INTENSITY: String
        get() = "launcher_recents_carousel_tint_intensity" + suffix

    val LAUNCHER_RECENTS_CAROUSEL_ICON_OFFSET_X: String
        get() = "launcher_recents_carousel_icon_offset_x" + suffix

    val LAUNCHER_RECENTS_CAROUSEL_ICON_OFFSET_Y: String
        get() = "launcher_recents_carousel_icon_offset_y" + suffix

    val LAUNCHER_PADDING_HOMEPAGE: String
        get() = "launcher_padding_homepage" + suffix

    val LAUNCHER_PADDING_DOCK: String
        get() = "launcher_padding_dock" + suffix

    val LAUNCHER_PADDING_SEARCH: String
        get() = "launcher_padding_search" + suffix

    val LAUNCHER_PADDING_DOTS: String
        get() = "launcher_padding_dots" + suffix

    val LAUNCHER_PADDING_DOTS_X: String
        get() = "launcher_padding_dots_x" + suffix


    val DOZE_DOUBLE_TAP_HOOK: String
        get() = "doze_double_tap_hook" + suffix

    val LAUNCHER_DT2S_ENABLED: String
        get() = "launcher_dt2s_enabled" + suffix

    // SystemUI / Shade
    val QS_COMPACT_PLAYER: String
        get() = "qs_compact_player" + suffix
    val QS_PLAYER_HIDE_EXPAND: String
        get() = "qs_player_hide_expand" + suffix
    val QS_PLAYER_HIDE_NOTIFY: String
        get() = "qs_player_hide_notify" + suffix
    val QS_PLAYER_HIDE_LOCKSCREEN: String
        get() = "qs_player_hide_lockscreen" + suffix
    val QS_PLAYER_ALPHA: String
        get() = "qs_player_alpha" + suffix

    /** Shade background/window blur intensity in percent (100 = stock). */
    val SHADE_BLUR_INTENSITY: String
        get() = "shade_blur_intensity" + suffix

    /** Shade zoom intensity in percent (100 = stock). */
    val SHADE_ZOOM_INTENSITY: String
        get() = "shade_zoom_intensity" + suffix

    /** Scale disable threshold in percent (default 40). */
    val SHADE_DISABLE_SCALE_THRESHOLD: String
        get() = "shade_disable_scale_threshold" + suffix

    /** Notification Scrim Alpha (0-100, -1 = default). */
    val SHADE_NOTIF_SCRIM_ALPHA: String
        get() = "shade_notif_scrim_alpha" + suffix

    /** Notification Scrim Tint (Color int, 0 = default). */
    val SHADE_NOTIF_SCRIM_TINT: String
        get() = "shade_notif_scrim_tint" + suffix

    /** Notification Scrim Tint enabled (0/1). */
    val SHADE_NOTIF_SCRIM_TINT_ENABLED: String
        get() = "shade_notif_scrim_tint_enabled" + suffix

    /** Main Scrim Alpha (0-100, -1 = default). */
    val SHADE_MAIN_SCRIM_ALPHA: String
        get() = "shade_main_scrim_alpha" + suffix

    /** Main Scrim Tint (Color int, 0 = default). */
    val SHADE_MAIN_SCRIM_TINT: String
        get() = "shade_main_scrim_tint" + suffix

    /** Main Scrim Tint enabled (0/1). */
    val SHADE_MAIN_SCRIM_TINT_ENABLED: String
        get() = "shade_main_scrim_tint_enabled" + suffix

    /** Minimum non-zero blur radius in px to avoid awkward tiny-blur transitions (0 = off). */
    val SHADE_BLUR_MIN_RADIUS_PX: String
        get() = "shade_blur_min_radius_px" + suffix

    const val DOZE_DOUBLE_TAP_TIMEOUT = "doze_double_tap_timeout"
    const val LAUNCHER_DT2S_TIMEOUT = "launcher_dt2s_timeout"
    const val LAUNCHER_DT2S_SLOP = "launcher_dt2s_slop"

    const val LAUNCHER_CURRENT_ICON_PACK = "launcher_current_icon_pack"

    
    const val PIXEL_LAUNCHER_NATIVE_SEARCH = "pixel_launcher_native_search"

    // Magnifier (text loupe)
    val MAGNIFIER_CUSTOM_ENABLED: String
        get() = "magnifier_custom_enabled" + suffix
    val MAGNIFIER_CUSTOM_ZOOM: String
        get() = "magnifier_custom_zoom" + suffix
    val MAGNIFIER_CUSTOM_SIZE: String
        get() = "magnifier_custom_size" + suffix
    val MAGNIFIER_CUSTOM_SHAPE: String
        get() = "magnifier_custom_shape" + suffix
    val MAGNIFIER_CUSTOM_OFFSET_Y: String
        get() = "magnifier_custom_offset_y" + suffix

    // Two-Shade (horizontal QS paging)
    /** Enable two-shade mode: QS becomes a separate horizontal page in shade. */
    val TWO_SHADE_HOOK: String
        get() = "two_shade_hook" + suffix

    // Activity transition animations
    /** Open transition mode ID (0=disabled, -1=custom, 10-93=built-in). */
    val ACTIVITY_OPEN_TRANSITION: String
        get() = "activity_open_transition" + suffix

    /** Close transition mode ID (0=disabled, -1=custom, 10-93=built-in). */
    val ACTIVITY_CLOSE_TRANSITION: String
        get() = "activity_close_transition" + suffix

    /** Package name of the custom animation theme APK. */
    val ACTIVITY_TRANSITION_CUSTOM_PACKAGE: String
        get() = "activity_transition_custom_package" + suffix

    /** Package name of the custom animation theme APK for open transitions. */
    val ACTIVITY_OPEN_CUSTOM_PACKAGE: String
        get() = "activity_open_custom_package" + suffix

    /** Package name of the custom animation theme APK for close transitions. */
    val ACTIVITY_CLOSE_CUSTOM_PACKAGE: String
        get() = "activity_close_custom_package" + suffix

    /** Disable predictive back gesture animation (0 = default, 1 = disable). */
    val DISABLE_PREDICTIVE_BACK_ANIM: String
        get() = "disable_predictive_back_anim" + suffix
}