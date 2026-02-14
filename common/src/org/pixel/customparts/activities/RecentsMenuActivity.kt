package org.pixel.customparts.activities

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.*
import org.pixel.customparts.ui.launcher.ClearAllSection
import org.pixel.customparts.utils.RecentsScaleOverlay
import org.pixel.customparts.utils.SettingsCompat
import org.pixel.customparts.utils.dynamicStringResource

class RecentsMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RecentsMenuScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsMenuScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // val useDiscreteScale = !AppConfig.IS_XPOSED
    val useDiscreteScale = false
    
    
    val keyModifyEnable = SettingsCompat.key("launcher_recents_modify_enable")
    val keyScaleEnable = SettingsCompat.key("launcher_recents_scale_enable")
    val keyScalePercent = SettingsCompat.key("launcher_recents_scale_percent")
    val keyCarouselScale = SettingsCompat.key("launcher_recents_carousel_scale")
    val keyCarouselSpacing = SettingsCompat.key("launcher_recents_carousel_spacing")
    val keyCarouselAlpha = SettingsCompat.key("launcher_recents_carousel_alpha")
    val keyCarouselBlur = SettingsCompat.key("launcher_recents_carousel_blur_radius")
    val keyCarouselBlurOverflow = SettingsCompat.key("launcher_recents_carousel_blur_overflow")
    val keyCarouselIconOffsetX = SettingsCompat.key("launcher_recents_carousel_icon_offset_x")
    val keyCarouselIconOffsetY = SettingsCompat.key("launcher_recents_carousel_icon_offset_y")
    val keyCarouselTintColor = SettingsCompat.key("launcher_recents_carousel_tint_color")
    val keyCarouselTintIntensity = SettingsCompat.key("launcher_recents_carousel_tint_intensity")
    val keyDisableLiveTile = SettingsCompat.key("launcher_recents_disable_livetile")

    
    var needsRestart by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var infoDialogVideo by remember { mutableStateOf<String?>(null) }
    var showXposedWarning by remember { mutableStateOf(false) }

    fun snapScalePercent(value: Float): Float {
        val clamped = value.coerceIn(20f, 120f)
        val step = 10f
        val snapped = (clamped / step).roundToInt() * step
        return snapped.coerceIn(20f, 120f)
    }

    fun applyScaleOverlay(enabled: Boolean, percent: Float) {
        if (!useDiscreteScale) return
        RecentsScaleOverlay.apply(context, enabled, percent.toInt())
    }
    
    
    var modifyEnabled by remember {
        mutableStateOf(Settings.Global.getInt(context.contentResolver, keyModifyEnable, 0) == 1)
    }
    var liveTileDisable by remember {
        mutableStateOf(Settings.Global.getInt(context.contentResolver, keyDisableLiveTile, 0) == 1)
    }

    
    var scaleEnabled by remember {
        mutableStateOf(Settings.Global.getInt(context.contentResolver, keyScaleEnable, 0) == 1)
    }
    var scalePercent by remember { 
        val rawValue = Settings.Global.getInt(context.contentResolver, keyScalePercent, 100).toFloat()
        mutableFloatStateOf(if (useDiscreteScale) snapScalePercent(rawValue) else rawValue)
    }

    
    var carouselScale by remember { 
        mutableFloatStateOf(Settings.Global.getFloat(context.contentResolver, keyCarouselScale, 1.0f)) 
    }
    var carouselSpacing by remember { 
        mutableFloatStateOf(Settings.Global.getInt(context.contentResolver, keyCarouselSpacing, 0).toFloat()) 
    }
    var carouselAlpha by remember {
        mutableFloatStateOf(Settings.Global.getFloat(context.contentResolver, keyCarouselAlpha, 1.0f))
    }

    
    var carouselBlur by remember { 
        mutableFloatStateOf(Settings.Global.getInt(context.contentResolver, keyCarouselBlur, 0).toFloat()) 
    }
    var blurOverflow by remember {
        mutableStateOf(Settings.Global.getInt(context.contentResolver, keyCarouselBlurOverflow, 0) == 1)
    }

    
    var tintIntensity by remember { 
        mutableFloatStateOf(Settings.Global.getInt(context.contentResolver, keyCarouselTintIntensity, 0).toFloat()) 
    }
    var tintColor by remember { 
        mutableIntStateOf(Settings.Global.getInt(context.contentResolver, keyCarouselTintColor, android.graphics.Color.TRANSPARENT)) 
    }
    var showColorPicker by remember { mutableStateOf(false) }

    
    var iconOffsetX by remember { 
        mutableFloatStateOf(Settings.Global.getInt(context.contentResolver, keyCarouselIconOffsetX, 0).toFloat()) 
    }
    var iconOffsetY by remember { 
        mutableFloatStateOf(Settings.Global.getInt(context.contentResolver, keyCarouselIconOffsetY, 0).toFloat()) 
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.recents_menu_screen_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back)) }
                },
                actions = {
                     AnimatedVisibility(
                        visible = needsRestart,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Button(
                            onClick = { scope.launch { LauncherManager.restartLauncher(context); needsRestart = false } },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                             Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                             Spacer(Modifier.width(4.dp))
                             Text(dynamicStringResource(R.string.btn_restart))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            
            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.recents_group_general)) {
                     GenericSwitchRow(
                        title = dynamicStringResource(R.string.recents_enable_modding),
                        checked = modifyEnabled,
                        onCheckedChange = { 
                            modifyEnabled = it
                            Settings.Global.putInt(context.contentResolver, keyModifyEnable, if (it) 1 else 0)
                            needsRestart = true
                            applyScaleOverlay(modifyEnabled && scaleEnabled, scalePercent)
                        }
                    )
                    HorizontalDivider()
                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.recents_disable_live_tile),
                        summary = dynamicStringResource(R.string.recents_disable_live_tile_desc),
                        checked = liveTileDisable,
                        enabled = modifyEnabled,
                        onCheckedChange = { 
                            liveTileDisable = it
                            Settings.Global.putInt(context.contentResolver, keyDisableLiveTile, if (it) 1 else 0)
                            needsRestart = true
                        }
                    )
                }
            }

            
            item {
                ClearAllSection(
                    context = context,
                    scope = scope,
                    refreshKey = refreshKey,
                    onSettingChanged = {
                        refreshKey++
                        needsRestart = true
                    },
                    onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                    onShowXposedDialog = { showXposedWarning = true }
                )
            }
            
            
            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.recents_group_scaling)) {
                     GenericSwitchRow(
                        title = dynamicStringResource(R.string.recents_enable_static_scale),
                        checked = scaleEnabled,
                        enabled = modifyEnabled,
                        onCheckedChange = { 
                            scaleEnabled = it
                            Settings.Global.putInt(context.contentResolver, keyScaleEnable, if (it) 1 else 0)
                            needsRestart = true
                            applyScaleOverlay(modifyEnabled && scaleEnabled, scalePercent)
                        }
                    )
                    SliderSettingFloat(
                        title = dynamicStringResource(R.string.recents_scale_percent),
                        value = scalePercent,
                        range = 20f..120f,
                        unit = "%",
                        enabled = modifyEnabled && scaleEnabled,
                        onValueChange = { scalePercent = if (useDiscreteScale) snapScalePercent(it) else it },
                        onDefault = { 
                            scalePercent = if (useDiscreteScale) 100f else 100f
                            Settings.Global.putInt(context.contentResolver, keyScalePercent, scalePercent.toInt())
                            needsRestart = true
                            applyScaleOverlay(modifyEnabled && scaleEnabled, scalePercent)
                        },
                        onValueChangeFinished = {
                            Settings.Global.putInt(context.contentResolver, keyScalePercent, scalePercent.toInt())
                            needsRestart = true
                            applyScaleOverlay(modifyEnabled && scaleEnabled, scalePercent)
                        }
                    )
                }
            }
            
            
            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.recents_group_carousel_geometry)) {
                     
                     
                    SliderSettingFloat(
                        title = dynamicStringResource(R.string.recents_carousel_min_scale),
                        value = carouselScale,
                        range = 0.2f..1.2f,
                        unit = "x",
                        enabled = modifyEnabled,
                        onValueChange = { carouselScale = it },
                        onDefault = { 
                            carouselScale = 1.0f
                            Settings.Global.putFloat(context.contentResolver, keyCarouselScale, 1.0f)
                            needsRestart = true
                        },
                        onValueChangeFinished = {
                            Settings.Global.putFloat(context.contentResolver, keyCarouselScale, carouselScale)
                            needsRestart = true
                        }
                    )
                    SliderSetting(
                        title = dynamicStringResource(R.string.recents_carousel_spacing),
                        value = carouselSpacing.toInt(),
                        range = -400..500,
                        unit = "px",
                        enabled = modifyEnabled,
                        onValueChange = { carouselSpacing = it.toFloat() }, 
                        onDefault = {
                            carouselSpacing = 0f
                            Settings.Global.putInt(context.contentResolver, keyCarouselSpacing, 0)
                            needsRestart = true
                        },
                        onValueChangeFinished = {
                            Settings.Global.putInt(context.contentResolver, keyCarouselSpacing, carouselSpacing.toInt())
                            needsRestart = true
                        }
                    )
                    SliderSettingFloat(
                        title = dynamicStringResource(R.string.recents_carousel_min_alpha),
                        value = carouselAlpha,
                        range = 0f..1f,
                        unit = "",
                        valueText = String.format("%.2f", carouselAlpha),
                        enabled = modifyEnabled,
                        onValueChange = { carouselAlpha = it.coerceIn(0f, 1f) },
                        onDefault = {
                            carouselAlpha = 1.0f
                            Settings.Global.putFloat(context.contentResolver, keyCarouselAlpha, 1.0f)
                            needsRestart = true
                        },
                        onValueChangeFinished = {
                            Settings.Global.putFloat(context.contentResolver, keyCarouselAlpha, carouselAlpha)
                            needsRestart = true
                        }
                    )
                }
            }
            
            
            item {
                val blurAvailable = android.os.Build.VERSION.SDK_INT >= 31
                SettingsGroupCard(title = dynamicStringResource(R.string.recents_group_carousel_blur)) {
                    SliderSetting(
                        title = dynamicStringResource(R.string.recents_carousel_blur_radius),
                        value = carouselBlur.toInt(),
                        range = 0..300,
                        unit = "dp",
                        enabled = modifyEnabled && blurAvailable,
                        onValueChange = { carouselBlur = it.toFloat() }, 
                        onDefault = {
                            carouselBlur = 0f
                            Settings.Global.putInt(context.contentResolver, keyCarouselBlur, 0)
                            needsRestart = true
                        },
                        onValueChangeFinished = {
                            Settings.Global.putInt(context.contentResolver, keyCarouselBlur, carouselBlur.toInt())
                            needsRestart = true
                        }
                    )
                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.recents_blur_overflow),
                        summary = dynamicStringResource(R.string.recents_blur_overflow_desc),
                        checked = blurOverflow,
                        enabled = modifyEnabled && blurAvailable,
                        onCheckedChange = { 
                            blurOverflow = it
                            Settings.Global.putInt(context.contentResolver, keyCarouselBlurOverflow, if (it) 1 else 0)
                            needsRestart = true
                        }
                    )
                }
            }
            
            
            item {
                val tintAvailable = android.os.Build.VERSION.SDK_INT >= 31
                SettingsGroupCard(title = dynamicStringResource(R.string.recents_group_carousel_tint)) {
                     SliderSetting(
                        title = dynamicStringResource(R.string.recents_tint_intensity),
                        value = tintIntensity.toInt(),
                        range = 0..100,
                        unit = "%",
                        enabled = modifyEnabled && tintAvailable,
                        onValueChange = { tintIntensity = it.toFloat() }, 
                        onDefault = {
                            tintIntensity = 0f
                            Settings.Global.putInt(context.contentResolver, keyCarouselTintIntensity, 0)
                            needsRestart = true
                        },
                        onValueChangeFinished = {
                            Settings.Global.putInt(context.contentResolver, keyCarouselTintIntensity, tintIntensity.toInt())
                            needsRestart = true
                        }
                    )
                    Button(
                        onClick = { showColorPicker = true },
                        enabled = modifyEnabled && tintAvailable,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(dynamicStringResource(R.string.recents_select_tint_color))
                    }
                }
            }
            
            
            item {
                 SettingsGroupCard(title = dynamicStringResource(R.string.recents_group_icon_offset)) {
                     SliderSetting(
                            title = dynamicStringResource(R.string.recents_icon_offset_x),
                            value = iconOffsetX.toInt(),
                            range = -1500..2500,
                            unit = "dp",
                            enabled = modifyEnabled,
                            onValueChange = { iconOffsetX = it.toFloat() }, 
                            onDefault = {
                                iconOffsetX = 0f
                                Settings.Global.putInt(context.contentResolver, keyCarouselIconOffsetX, 0)
                                needsRestart = true
                            },
                            onValueChangeFinished = {
                                Settings.Global.putInt(context.contentResolver, keyCarouselIconOffsetX, iconOffsetX.toInt())
                                needsRestart = true
                            }
                        )
                    SliderSetting(
                            title = dynamicStringResource(R.string.recents_icon_offset_y),
                            value = iconOffsetY.toInt(),
                            range = -1500..2500,
                            unit = "dp",
                            enabled = modifyEnabled,
                            onValueChange = { iconOffsetY = it.toFloat() }, 
                            onDefault = {
                                iconOffsetY = 0f
                                Settings.Global.putInt(context.contentResolver, keyCarouselIconOffsetY, 0)
                                needsRestart = true
                            },
                            onValueChangeFinished = {
                                Settings.Global.putInt(context.contentResolver, keyCarouselIconOffsetY, iconOffsetY.toInt())
                                needsRestart = true
                            }
                        )
                 }
            }
            
            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    
    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = tintColor,
            onDismissRequest = { showColorPicker = false },
            onColorSelected = { color ->
                tintColor = color
                Settings.Global.putInt(context.contentResolver, keyCarouselTintColor, color)
                needsRestart = true
                showColorPicker = false
            }
        )
    }

    if (infoDialogTitle != null && infoDialogText != null) {
        InfoDialog(
            title = infoDialogTitle!!,
            text = infoDialogText!!,
            videoResName = infoDialogVideo,
            onDismiss = { infoDialogTitle = null; infoDialogText = null; infoDialogVideo = null }
        )
    }

    if (showXposedWarning) {
        AlertDialog(
            onDismissRequest = { showXposedWarning = false },
            title = { Text(dynamicStringResource(R.string.xposed_required_title)) },
            text = { Text(dynamicStringResource(R.string.xposed_required_message)) },
            confirmButton = {
                TextButton(onClick = { showXposedWarning = false }) {
                    Text(dynamicStringResource(R.string.btn_ok))
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        applyScaleOverlay(modifyEnabled && scaleEnabled, scalePercent)
    }
}
