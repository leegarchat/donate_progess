package org.pixel.customparts.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.ModuleStatus
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.InfoDialog

import androidx.compose.foundation.text.KeyboardOptions
import org.pixel.customparts.utils.SettingsCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.fillMaxWidth
import org.pixel.customparts.utils.restartSystemUI
import org.pixel.customparts.utils.dynamicStringResource

import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box



@Composable
fun TextFieldSetting(
    title: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    
    OutlinedTextField(
        value = text,
        onValueChange = { 
            text = it
            onValueChange(it)
        },
        label = { Text(title) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
    )
}

class LockscreenSettingsActivity : ComponentActivity() {
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
                    LockscreenSettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockscreenSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var needsRestart by remember { mutableStateOf(false) }

    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var infoDialogVideo by remember { mutableStateOf<String?>(null) }

    // DT2W State
    var dt2wEnabled by remember { mutableStateOf(DoubleTapManager.isDt2wEnabled(context)) }
    var dt2wTimeout by remember { mutableIntStateOf(DoubleTapManager.getDt2wTimeout(context)) }
    
    // Battery Info State
    var batteryInfoEnabled by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.BATTERY_INFO_ENABLE, false)) }
    var showWattage by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.SHOW_WATTAGE, true)) }
    var showVoltage by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.SHOW_VOLTAGE, true)) }
    var showCurrent by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.SHOW_CURRENT, true)) }
    var showTemp by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.SHOW_TEMP, true)) }
    var showPercent by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.SHOW_PERCENT, false)) }
    var showStandardString by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.SHOW_STANDARD_STRING, true)) }
    var showCustomSymbol by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.SHOW_CUSTOM_SYMBOL, true)) }
    var customSymbol by remember { mutableStateOf(SettingsCompat.getString(context, SettingsKeys.CUSTOM_SYMBOL, "⚡") ?: "⚡") }
    var refreshIntervalMs by remember { mutableIntStateOf(SettingsCompat.getInt(context, SettingsKeys.BATTERY_INFO_REFRESH_INTERVAL_MS, 1000).coerceIn(100, 5000)) }
    var averageModeEnabled by remember { mutableStateOf(SettingsCompat.isEnabled(context, SettingsKeys.BATTERY_INFO_AVERAGE_MODE, false)) }

    var showXposedInactiveDialog by remember { mutableStateOf(false) }

    if (showXposedInactiveDialog) {
        AlertDialog(
            onDismissRequest = { showXposedInactiveDialog = false },
            title = { Text(dynamicStringResource(R.string.os_dialog_xposed_title)) },
            text = { Text(dynamicStringResource(R.string.os_dialog_xposed_msg)) },
            confirmButton = {
                TextButton(onClick = { showXposedInactiveDialog = false }) {
                    Text(dynamicStringResource(R.string.btn_ok))
                }
            }
        )
    }

    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = {
                    Text(dynamicStringResource(R.string.sysui_lockscreen_title), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
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
            // DT2W Section (Duplicated)
            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.dt_sec_wake)) {
                    ExpandableWarningCard(
                        title = dynamicStringResource(R.string.dt2w_info_title),
                        text = dynamicStringResource(R.string.dt2w_info_desc),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    val dt2wDesc = if (AppConfig.IS_XPOSED) {
                        dynamicStringResource(R.string.dt2w_desc_xposed)
                    } else {
                        dynamicStringResource(R.string.dt2w_desc_native)
                    }

                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.dt2w_title),
                        checked = dt2wEnabled,
                        onCheckedChange = { checked ->
                            if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive() && !SettingsKeys.isPineOverride) {
                                showXposedInactiveDialog = true
                                dt2wEnabled = false
                            } else {
                                dt2wEnabled = checked
                                scope.launch { DoubleTapManager.setDt2wEnabled(context, checked) }
                                needsRestart = true
                            }
                        },
                        videoResName = "dt2w_hook",
                        infoText = dt2wDesc,
                        onInfoClick = { t, s, v ->
                            infoDialogTitle = t
                            infoDialogText = s
                            infoDialogVideo = v
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SliderSetting(
                        title = dynamicStringResource(R.string.dt2w_timeout_title),
                        value = dt2wTimeout,
                        range = 300..1000,
                        unit = "ms",
                        enabled = dt2wEnabled,
                        onValueChange = { dt2wTimeout = it; scope.launch { DoubleTapManager.setDt2wTimeout(context, it) }; needsRestart = true },
                        onDefault = { dt2wTimeout = 400; scope.launch { DoubleTapManager.setDt2wTimeout(context, 400) }; needsRestart = true },
                        infoText = dynamicStringResource(R.string.dt2w_timeout_desc),
                        onInfoClick = { t, s, v ->
                            infoDialogTitle = t
                            infoDialogText = s
                            infoDialogVideo = v
                        }
                    )
                }
            }

            // Battery Info Section
            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.sysui_charging_info_title)) {
                    GenericSwitchRow(
                        title = dynamicStringResource(R.string.sysui_charging_info_enable),
                        checked = batteryInfoEnabled,
                        onCheckedChange = { checked ->
                             if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive() && !SettingsKeys.isPineOverride) {
                                showXposedInactiveDialog = true
                                batteryInfoEnabled = false
                            } else {
                                batteryInfoEnabled = checked
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.BATTERY_INFO_ENABLE, if (checked) 1 else 0) }
                                needsRestart = true
                            }
                        }
                    )
                    
                    if (batteryInfoEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        SliderSetting(
                            title = dynamicStringResource(R.string.sysui_sensor_interval_title),
                            value = refreshIntervalMs,
                            range = 100..5000,
                            unit = "ms",
                            enabled = batteryInfoEnabled,
                            onValueChange = { value ->
                                refreshIntervalMs = value
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.BATTERY_INFO_REFRESH_INTERVAL_MS, value) }
                                needsRestart = true
                            },
                            onDefault = {
                                refreshIntervalMs = 1000
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.BATTERY_INFO_REFRESH_INTERVAL_MS, 1000) }
                                needsRestart = true
                            }
                        )

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_average_mode_title),
                            checked = averageModeEnabled,
                            onCheckedChange = { checked ->
                                averageModeEnabled = checked
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.BATTERY_INFO_AVERAGE_MODE, if (checked) 1 else 0) }
                                needsRestart = true
                            }
                        )
                        
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_show_standard),
                            checked = showStandardString,
                            onCheckedChange = { checked ->
                                showStandardString = checked
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.SHOW_STANDARD_STRING, if (checked) 1 else 0) }
                                needsRestart = true
                            }
                        )

                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_show_custom_symbol),
                            checked = showCustomSymbol,
                            onCheckedChange = { checked ->
                                showCustomSymbol = checked
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.SHOW_CUSTOM_SYMBOL, if (checked) 1 else 0) }
                                needsRestart = true
                            }
                        )

                        if (showCustomSymbol) {
                            TextFieldSetting(
                               title = dynamicStringResource(R.string.sysui_custom_symbol),
                               value = customSymbol,
                               onValueChange = { newVal ->
                                   customSymbol = newVal
                                   scope.launch { SettingsCompat.putString(context, SettingsKeys.CUSTOM_SYMBOL, newVal) }
                                   needsRestart = true
                               }
                            )
                        }
                        
                        GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_show_wattage),
                            checked = showWattage,
                            onCheckedChange = { checked ->
                                showWattage = checked
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.SHOW_WATTAGE, if (checked) 1 else 0) }
                                needsRestart = true
                            }
                        )
                         GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_show_voltage),
                            checked = showVoltage,
                            onCheckedChange = { checked ->
                                showVoltage = checked
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.SHOW_VOLTAGE, if (checked) 1 else 0) }
                                needsRestart = true
                            }
                        )
                         GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_show_current),
                            checked = showCurrent,
                            onCheckedChange = { checked ->
                                showCurrent = checked
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.SHOW_CURRENT, if (checked) 1 else 0) }
                                needsRestart = true
                            }
                        )
                         GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_show_temp),
                            checked = showTemp,
                            onCheckedChange = { checked ->
                                showTemp = checked
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.SHOW_TEMP, if (checked) 1 else 0) }
                                needsRestart = true
                            }
                        )
                         GenericSwitchRow(
                            title = dynamicStringResource(R.string.sysui_show_percent),
                            checked = showPercent,
                            onCheckedChange = { checked ->
                                showPercent = checked
                                scope.launch { SettingsCompat.putInt(context, SettingsKeys.SHOW_PERCENT, if (checked) 1 else 0) }
                                needsRestart = true
                            }
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

    if (infoDialogTitle != null && infoDialogText != null) {
        InfoDialog(
            title = infoDialogTitle!!,
            text = infoDialogText!!,
            videoResName = infoDialogVideo,
            onDismiss = {
                infoDialogTitle = null
                infoDialogText = null
                infoDialogVideo = null
            }
        )
    }
}
