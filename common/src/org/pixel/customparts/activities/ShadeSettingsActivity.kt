package org.pixel.customparts.activities

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.ColorPickerDialog
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RadioSelectionGroup
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.utils.dynamicStringResource
import org.pixel.customparts.utils.restartSystemUI

import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState

class ShadeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = androidx.compose.ui.platform.LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShadeSettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun ColorPickerRow(
    title: String,
    color: Int,
    enabled: Boolean,
    onColorClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit = {},
    showSwitch: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
             Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color(color), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onColorClick)
            )
            
            if (showSwitch) {
                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadeSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var needsRestart by remember { mutableStateOf(false) }
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    var compactMode by remember { mutableIntStateOf(0) }
    var bgAlphaPercent by remember { mutableIntStateOf(100) }
    var hideQs by remember { mutableStateOf(false) }
    var hideNotify by remember { mutableStateOf(false) }
    var hideLockscreen by remember { mutableStateOf(false) }

    // New settings
    var blurIntensity by remember { mutableIntStateOf(100) }
    var zoomIntensity by remember { mutableIntStateOf(100) }
    var disableScaleThreshold by remember { mutableIntStateOf(40) }

    var notifScrimAlpha by remember { mutableIntStateOf(-1) }
    var mainScrimAlpha by remember { mutableIntStateOf(-1) }
    var notifScrimTint by remember { mutableIntStateOf(0) }
    var mainScrimTint by remember { mutableIntStateOf(0) }
    var notifTintEnabled by remember { mutableStateOf(false) }
    var mainTintEnabled by remember { mutableStateOf(false) }
    
    var showColorPickerTarget by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(Unit) {
        compactMode = Settings.Global.getInt(context.contentResolver, SettingsKeys.QS_COMPACT_PLAYER, 0).coerceIn(0, 3)

        val rawAlpha = Settings.Global.getFloat(context.contentResolver, SettingsKeys.QS_PLAYER_ALPHA, 1f)
        val normalizedAlpha = if (rawAlpha > 1.01f) (rawAlpha / 100f) else rawAlpha
        bgAlphaPercent = (normalizedAlpha.coerceIn(0f, 1f) * 100f).toInt()

        hideQs = Settings.Global.getInt(context.contentResolver, SettingsKeys.QS_PLAYER_HIDE_EXPAND, 0) != 0
        hideNotify = Settings.Global.getInt(context.contentResolver, SettingsKeys.QS_PLAYER_HIDE_NOTIFY, 0) != 0
        hideLockscreen = Settings.Global.getInt(context.contentResolver, SettingsKeys.QS_PLAYER_HIDE_LOCKSCREEN, 0) != 0

        blurIntensity = Settings.Global.getInt(context.contentResolver, SettingsKeys.SHADE_BLUR_INTENSITY, 100)
        zoomIntensity = Settings.Global.getInt(context.contentResolver, SettingsKeys.SHADE_ZOOM_INTENSITY, 100)
        disableScaleThreshold = Settings.Global.getInt(context.contentResolver, SettingsKeys.SHADE_DISABLE_SCALE_THRESHOLD, 40)
        
        notifScrimAlpha = Settings.Global.getInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_ALPHA, -1)
        mainScrimAlpha = Settings.Global.getInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_ALPHA, -1)
        if (notifScrimAlpha != -1) notifScrimAlpha = notifScrimAlpha.coerceIn(0, 100)
        if (mainScrimAlpha != -1) mainScrimAlpha = mainScrimAlpha.coerceIn(0, 100)
        notifScrimTint = Settings.Global.getInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_TINT, 0)
        mainScrimTint = Settings.Global.getInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_TINT, 0)
        notifTintEnabled = Settings.Global.getInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_TINT_ENABLED, 0) != 0
        mainTintEnabled = Settings.Global.getInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_TINT_ENABLED, 0) != 0
    }

    if (showColorPickerTarget != null) {
        val isNotif = showColorPickerTarget == "notif"
        val initialColor = if (isNotif) notifScrimTint else mainScrimTint
        // If 0, default to Black with alpha or just Black for picker
        val safeInitial = if (initialColor == 0) Color.Black.toArgb() else initialColor

        ColorPickerDialog(
            initialColor = safeInitial,
            onColorSelected = { color ->
                if (isNotif) {
                    notifScrimTint = color
                    Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_TINT, color)
                } else {
                    mainScrimTint = color
                    Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_TINT, color)
                }
                needsRestart = true
                showColorPickerTarget = null
            },
            onDismissRequest = { showColorPickerTarget = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.material3.Text(
                        dynamicStringResource(R.string.sysui_shade_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                actions = {
                    AnimatedVisibility(
                        visible = needsRestart,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Button(
                            onClick = {
                                restartSystemUI(context)
                                needsRestart = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(dynamicStringResource(R.string.btn_restart))
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(blurState)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp + innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.sysui_media_player_title)) {
                    SliderSetting(
                        title = dynamicStringResource(R.string.sysui_media_bg_alpha_title),
                        value = bgAlphaPercent,
                        range = 0..100,
                        unit = "%",
                        enabled = true,
                        onValueChange = { v ->
                            bgAlphaPercent = v.coerceIn(0, 100)
                            Settings.Global.putFloat(
                                context.contentResolver,
                                SettingsKeys.QS_PLAYER_ALPHA,
                                (bgAlphaPercent / 100f)
                            )
                            needsRestart = true
                        },
                        onDefault = {
                            bgAlphaPercent = 100
                            Settings.Global.putFloat(context.contentResolver, SettingsKeys.QS_PLAYER_ALPHA, 1f)
                            needsRestart = true
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    RadioSelectionGroup(
                        title = dynamicStringResource(R.string.sysui_media_compact_mode_title),
                        options = listOf(
                            dynamicStringResource(R.string.sysui_media_compact_mode_off),
                            dynamicStringResource(R.string.sysui_media_compact_mode_small),
                            dynamicStringResource(R.string.sysui_media_compact_mode_header),
                            dynamicStringResource(R.string.sysui_media_compact_mode_very_small)
                        ),
                        selectedIndex = compactMode,
                        onSelect = { newMode ->
                            compactMode = newMode
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.QS_COMPACT_PLAYER, newMode)
                            needsRestart = true
                        }
                    )
                }
            }

            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.sysui_media_hide_title)) {
                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.sysui_media_hide_qs),
                        checked = hideQs,
                        onCheckedChange = { checked ->
                            hideQs = checked
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.QS_PLAYER_HIDE_EXPAND, if (checked) 1 else 0)
                            needsRestart = true
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.sysui_media_hide_notifications),
                        checked = hideNotify,
                        onCheckedChange = { checked ->
                            hideNotify = checked
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.QS_PLAYER_HIDE_NOTIFY, if (checked) 1 else 0)
                            needsRestart = true
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.sysui_media_hide_lockscreen),
                        checked = hideLockscreen,
                        onCheckedChange = { checked ->
                            hideLockscreen = checked
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.QS_PLAYER_HIDE_LOCKSCREEN, if (checked) 1 else 0)
                            needsRestart = true
                        }
                    )
                }
            }

            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.sysui_shade_surface_title)) {
                    SliderSetting(
                        title = dynamicStringResource(R.string.sysui_shade_blur_title),
                        value = blurIntensity,
                        range = 0..400,
                        unit = "%",
                        enabled = true,
                        onValueChange = { v ->
                            blurIntensity = v
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_BLUR_INTENSITY, v)
                            needsRestart = true
                        },
                        onDefault = {
                            blurIntensity = 100
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_BLUR_INTENSITY, 100)
                            needsRestart = true
                        },
                        inputRange = 0..999999
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SliderSetting(
                        title = dynamicStringResource(R.string.sysui_shade_zoom_title),
                        value = zoomIntensity,
                        range = -200..400,
                        unit = "%",
                        enabled = true,
                        onValueChange = { v ->
                            zoomIntensity = v
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_ZOOM_INTENSITY, v)
                            needsRestart = true
                        },
                        onDefault = {
                            zoomIntensity = 100
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_ZOOM_INTENSITY, 100)
                            needsRestart = true
                        },
                        inputRange = -999999..999999
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                     SliderSetting(
                        title = dynamicStringResource(R.string.sysui_shade_disable_scale_threshold_title),
                        value = disableScaleThreshold,
                        range = 0..400,
                        unit = "%",
                        enabled = true,
                        onValueChange = { v ->
                            disableScaleThreshold = v
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_DISABLE_SCALE_THRESHOLD, v)
                            needsRestart = true
                        },
                        onDefault = {
                            disableScaleThreshold = 40
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_DISABLE_SCALE_THRESHOLD, 40)
                            needsRestart = true
                        }
                    )
                }
            }
            
            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.sysui_shade_scrim_title)) {
                    // Notification Scrim Alpha
                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.sysui_shade_notif_scrim_alpha_title),
                        checked = notifScrimAlpha != -1,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                notifScrimAlpha = 100
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_ALPHA, 100)
                            } else {
                                notifScrimAlpha = -1
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_ALPHA, -1)
                            }
                            needsRestart = true
                        }
                    )
                    
                    if (notifScrimAlpha != -1) {
                         SliderSetting(
                            title = dynamicStringResource(R.string.sysui_shade_override_enable),
                            value = notifScrimAlpha,
                            range = 0..100,
                            unit = "%",
                            enabled = true,
                            onValueChange = { v ->
                                notifScrimAlpha = v
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_ALPHA, v)
                                needsRestart = true
                            },
                             onDefault = {
                                notifScrimAlpha = 100
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_ALPHA, 100)
                                needsRestart = true
                            },
                            showDefaultButton = false
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Main Scrim Alpha
                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.sysui_shade_main_scrim_alpha_title),
                        checked = mainScrimAlpha != -1,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                mainScrimAlpha = 100
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_ALPHA, 100)
                            } else {
                                mainScrimAlpha = -1
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_ALPHA, -1)
                            }
                            needsRestart = true
                        }
                    )
                     if (mainScrimAlpha != -1) {
                         SliderSetting(
                            title = dynamicStringResource(R.string.sysui_shade_override_enable),
                            value = mainScrimAlpha,
                            range = 0..100,
                            unit = "%",
                            enabled = true,
                            onValueChange = { v ->
                                mainScrimAlpha = v
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_ALPHA, v)
                                needsRestart = true
                            },
                             onDefault = {
                                mainScrimAlpha = 100
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_ALPHA, 100)
                                needsRestart = true
                            },
                            showDefaultButton = false
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Notification Scrim Tint
                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.sysui_shade_notif_scrim_tint_title),
                        checked = notifTintEnabled,
                        onCheckedChange = { enabled ->
                            notifTintEnabled = enabled
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_TINT_ENABLED, if (enabled) 1 else 0)
                            // Set default color on first enable if no color was picked yet
                            if (enabled && notifScrimTint == 0) {
                                notifScrimTint = Color.Black.toArgb()
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_NOTIF_SCRIM_TINT, notifScrimTint)
                            }
                            needsRestart = true
                        }
                    )

                    if (notifTintEnabled) {
                        ColorPickerRow(
                            title = dynamicStringResource(R.string.sysui_shade_notif_scrim_tint_color),
                            color = if (notifScrimTint == 0) Color.Black.toArgb() else notifScrimTint,
                            enabled = true,
                            onColorClick = { showColorPickerTarget = "notif" },
                            showSwitch = false
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Main Scrim Tint
                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.sysui_shade_main_scrim_tint_title),
                        checked = mainTintEnabled,
                        onCheckedChange = { enabled ->
                            mainTintEnabled = enabled
                            Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_TINT_ENABLED, if (enabled) 1 else 0)
                            if (enabled && mainScrimTint == 0) {
                                mainScrimTint = Color.Black.toArgb()
                                Settings.Global.putInt(context.contentResolver, SettingsKeys.SHADE_MAIN_SCRIM_TINT, mainScrimTint)
                            }
                            needsRestart = true
                        }
                    )

                    if (mainTintEnabled) {
                        ColorPickerRow(
                            title = dynamicStringResource(R.string.sysui_shade_main_scrim_tint_color),
                            color = if (mainScrimTint == 0) Color.Black.toArgb() else mainScrimTint,
                            enabled = true,
                            onColorClick = { showColorPickerTarget = "main" },
                            showSwitch = false
                        )
                    }
                }
            }
        }
            
        TopBarBlurOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            blurState = blurState,
            topBarHeight = innerPadding.calculateTopPadding(),
            isScrolled = isScrolled
        )
    }
    }
}
