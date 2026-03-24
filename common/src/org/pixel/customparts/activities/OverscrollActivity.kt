package org.pixel.customparts.activities

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.customparts.AppConfig
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.ui.InfoDialog
import org.pixel.customparts.ui.RadioSelectionGroup
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.ModuleStatus
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.dynamicStringResource
import org.pixel.customparts.utils.RemoteStringsManager

class OverscrollActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OverscrollScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverscrollScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        snapAnimationSpec = null,
        flingAnimationSpec = null
    )
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }
    var showXposedInactiveDialog by remember { mutableStateOf(false) }
    var infoDialogVideo by remember { mutableStateOf<String?>(null) }
    var isMasterEnabled by remember { mutableStateOf(OverscrollManager.isMasterEnabled(context)) }
    var profiles by remember { mutableStateOf(OverscrollManager.getSavedProfiles(context)) }
    var appConfigs by remember { mutableStateOf(OverscrollManager.getAppConfigs(context)) }
    var showAddAppDialog by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val expandedStates = remember { 
        mutableStateMapOf(
            "physics" to true, 
            "vis_vert" to false,
            "vis_zoom" to false,
            "vis_horz" to false,
            "advanced" to false,
            "norm" to false,
            "apps" to false
        ) 
    }


    val onSettingChanged: () -> Unit = { 
        scope.launch(Dispatchers.IO) {
            OverscrollManager.clearActiveProfile(context)
        }
        refreshKey++
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { OverscrollManager.exportSettings(context, uri) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch { 
                if (OverscrollManager.importSettings(context, uri)) {
                    isMasterEnabled = OverscrollManager.isMasterEnabled(context)
                    refreshKey++
                    appConfigs = OverscrollManager.getAppConfigs(context)
                    val message = RemoteStringsManager.getString(context, R.string.os_msg_import_success)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    if (showXposedInactiveDialog) {
        AlertDialog(
            onDismissRequest = { showXposedInactiveDialog = false },
            title = { Text(dynamicStringResource(R.string.os_dialog_xposed_title)) },
            text = { Text(dynamicStringResource(R.string.os_dialog_xposed_msg)) },
            confirmButton = {
                TextButton(onClick = { showXposedInactiveDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { 
                    Text(dynamicStringResource(R.string.os_title_activity),
                    fontWeight = FontWeight.Bold) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            if (AppConfig.IS_XPOSED) {
                item {
                    ExpandableWarningCard(
                        title = dynamicStringResource(R.string.overscroll_xposed_warning_title),
                        text = dynamicStringResource(R.string.overscroll_xposed_warning_desc)
                    )
                }
            }
            item(key = "master_switch", contentType = "switch_card") {
                val titleStr = dynamicStringResource(
                    if (AppConfig.IS_XPOSED) R.string.os_label_master_xposed else R.string.os_label_master_native
                )
                val descStr = dynamicStringResource(
                    if (AppConfig.IS_XPOSED) R.string.os_desc_master_xposed else R.string.os_desc_master_native
                )


                MasterSwitchCard(
                    title = titleStr,
                    isChecked = isMasterEnabled,
                    onCheckedChange = { checked ->
                        if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive()) {
                            showXposedInactiveDialog = true
                            isMasterEnabled = false 
                        } else {
                            isMasterEnabled = checked
                            scope.launch { OverscrollManager.setMasterEnabled(context, checked) }
                            onSettingChanged()
                        }
                    },
                    onInfoClick = { 
                        infoDialogTitle = titleStr
                        infoDialogText = descStr
                        infoDialogVideo = "overscroll_master" 
                    }
                )
            }


            item(key = "playground", contentType = "horizontal_list") {
                Text(
                    dynamicStringResource(R.string.os_title_playground),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(5, key = { "card_$it" }) { index -> 
                        Box(Modifier.width(160.dp)) { StaticTestCard("Card ${index + 1}") } 
                    }
                }
            }


            item(key = "profiles_section", contentType = "complex_section") {
                Box(modifier = Modifier.alpha(if (isMasterEnabled) 1f else 0.5f)) { 
                    ProfilesSection(
                        context = context,
                        scope = scope,
                        profiles = profiles,
                        onProfilesChanged = { profiles = it },
                        onProfileLoaded = {  },
                        exportLauncher = exportLauncher,
                        importLauncher = importLauncher,
                        refreshKey = refreshKey
                    )
                    if (!isMasterEnabled) {
                        Box(modifier = Modifier.matchParentSize().clickable(enabled = false, onClick = {}))
                    }
                }
            }

            item(key = "settings_physics", contentType = "settings_group") {
                val expanded = expandedStates["physics"] ?: true
                ExpandableSettingsGroupCard(
                    title = dynamicStringResource(R.string.os_group_physics),
                    enabled = isMasterEnabled,
                    expanded = expanded,
                    onExpandChange = { expandedStates["physics"] = it }
                ) {
                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_physics_pull),
                        key = OverscrollManager.KEY_PULL_COEFF,
                        range = 0.1f..3.0f,
                        defVal = 1.5141f,
                        infoText = dynamicStringResource(R.string.os_desc_physics_pull),
                        video = "overscroll_pull",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )


                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_physics_stiffness),
                        key = OverscrollManager.KEY_STIFFNESS,
                        range = 10f..1000f,
                        defVal = 148.6191f,
                        infoText = dynamicStringResource(R.string.os_desc_physics_stiffness),
                        video = "overscroll_stiffness",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )


                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_physics_damping),
                        key = OverscrollManager.KEY_DAMPING,
                        range = 0.1f..2.0f,
                        defVal = 0.9976f,
                        infoText = dynamicStringResource(R.string.os_desc_physics_damping),
                        video = "overscroll_damping",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )


                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_physics_fling),
                        key = OverscrollManager.KEY_FLING,
                        range = 0.1f..3.0f,
                        defVal = 1.3679f,
                        infoText = dynamicStringResource(R.string.os_desc_physics_fling),
                        video = "overscroll_fling",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_physics_res_exp),
                        key = OverscrollManager.KEY_RESISTANCE_EXPONENT,
                        range = 1f..8f,
                        defVal = 4.0f,
                        infoText = dynamicStringResource(R.string.os_desc_physics_res_exp),
                        video = "overscroll_res_exponent",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_anim_speed),
                        key = OverscrollManager.KEY_ANIMATION_SPEED,
                        range = 1f..300f,
                        defVal = 168.5232f,
                        infoText = dynamicStringResource(R.string.os_desc_anim_speed),
                        video = "overscroll_anim_speed",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )
                }
            }


            item(key = "settings_visual_scales", contentType = "settings_group") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScaleGroup(
                        context = context, 
                        title = dynamicStringResource(R.string.os_group_visual_vert),
                        prefix = "overscroll_scale",
                        desc = dynamicStringResource(R.string.os_desc_visual_vert),
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        isMasterEnabled = isMasterEnabled,
                        expanded = expandedStates["vis_vert"] ?: false,
                        onExpandChange = { expandedStates["vis_vert"] = it },
                        onChange = onSettingChanged,
                        defIntensity = 0.31f,
                        defIntensityHoriz = 0.3786f,
                        defLimitMin = 0.1f
                    )

                    ScaleGroup(
                        context = context, 
                        title = dynamicStringResource(R.string.os_group_visual_zoom),
                        prefix = "overscroll_zoom",
                        desc = dynamicStringResource(R.string.os_desc_visual_zoom),
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v }, 
                        refreshKey = refreshKey,
                        isMasterEnabled = isMasterEnabled,
                        expanded = expandedStates["vis_zoom"] ?: false,
                        onExpandChange = { expandedStates["vis_zoom"] = it },
                        onChange = onSettingChanged,
                        defIntensity = 0.2f,
                        defIntensityHoriz = 0.2f,
                        defLimitMin = 0.1f
                    )

                    ScaleGroup(
                        context = context, 
                        title = dynamicStringResource(R.string.os_group_visual_horz), 
                        prefix = "overscroll_h_scale", 
                        desc = dynamicStringResource(R.string.os_desc_visual_horz),
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v }, 
                        refreshKey = refreshKey, 
                        isMasterEnabled = isMasterEnabled,
                        expanded = expandedStates["vis_horz"] ?: false,
                        onExpandChange = { expandedStates["vis_horz"] = it },
                        onChange = onSettingChanged,
                        defIntensity = 0.2f,
                        defIntensityHoriz = 0.0f,
                        defLimitMin = 0.1f
                    )
                }
            }


            item(key = "settings_advanced", contentType = "settings_advanced") {
                val expanded = expandedStates["advanced"] ?: false
                ExpandableSettingsGroupCard(
                    title = dynamicStringResource(R.string.os_group_advanced),
                    enabled = isMasterEnabled,
                    expanded = expanded,
                    onExpandChange = { expandedStates["advanced"] = it },
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isMasterEnabled) 0.2f else 0.1f),
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_input_smooth),
                        key = OverscrollManager.KEY_INPUT_SMOOTH_FACTOR,
                        range = 0f..0.95f,
                        defVal = 0.5f,
                        infoText = dynamicStringResource(R.string.os_desc_input_smooth),
                        video = "overscroll_input_smooth",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_min_vel),
                        key = OverscrollManager.KEY_PHYSICS_MIN_VEL,
                        range = 0f..400f,
                        defVal = 8.0f,
                        infoText = dynamicStringResource(R.string.os_desc_min_vel),
                        video = "overscroll_physics_min_vel",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_min_val),
                        key = OverscrollManager.KEY_PHYSICS_MIN_VAL,
                        range = 0f..20f,
                        defVal = 0.6f,
                        infoText = dynamicStringResource(R.string.os_desc_min_val),
                        video = "overscroll_physics_min_val",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_lerp_idle),
                        key = OverscrollManager.KEY_LERP_MAIN_IDLE,
                        range = 0f..1f,
                        defVal = 0.4f,
                        infoText = dynamicStringResource(R.string.os_desc_lerp_idle),
                        video = "overscroll_lerp_main_idle",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_lerp_run),
                        key = OverscrollManager.KEY_LERP_MAIN_RUN,
                        range = 0f..1f,
                        defVal = 0.6999f,
                        infoText = dynamicStringResource(R.string.os_desc_lerp_run),
                        video = "overscroll_lerp_main_run",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_compose_scale),
                        key = OverscrollManager.KEY_COMPOSE_SCALE,
                        range = 0.01f..10.0f,
                        defVal = 3.3299f,
                        infoText = dynamicStringResource(R.string.os_desc_compose_scale),
                        video = "overscroll_compose_scale",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = isMasterEnabled,
                        onChange = onSettingChanged
                    )

                    val invertKey = OverscrollManager.KEY_INVERT_ANCHOR
                    var invert by remember(refreshKey) { mutableStateOf(Settings.Global.getInt(context.contentResolver, invertKey, 1) == 1) }
                    
                    val invertTitle = dynamicStringResource(R.string.os_lbl_invert_anchor)
                    val invertDesc = dynamicStringResource(R.string.os_desc_invert_anchor)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = invertTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { 
                            infoDialogTitle = invertTitle
                            infoDialogText = invertDesc
                            infoDialogVideo = "overscroll_invert_anchor"
                        }) {
                            Icon(Icons.Outlined.Info, "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = invert,
                            onCheckedChange = { 
                                invert = it
                                scope.launch(Dispatchers.IO) { 
                                    Settings.Global.putInt(context.contentResolver, invertKey, if (it) 1 else 0) 
                                    launch(Dispatchers.Main) { onSettingChanged() }
                                }
                            }
                        )
                    }
                }
            }

            item(key = "settings_norm", contentType = "settings_norm") {
                val expanded = expandedStates["norm"] ?: false
                ExpandableSettingsGroupCard(
                    title = dynamicStringResource(R.string.os_group_norm),
                    enabled = isMasterEnabled,
                    expanded = expanded,
                    onExpandChange = { expandedStates["norm"] = it },
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isMasterEnabled) 0.2f else 0.1f),
                    contentColor = MaterialTheme.colorScheme.error
                ) {
                    // Master switch — styled card (mini version of MasterSwitchCard)
                    val normEnabledKey = OverscrollManager.KEY_NORM_ENABLED
                    var normEnabled by remember(refreshKey) { mutableStateOf(Settings.Global.getInt(context.contentResolver, normEnabledKey, 1) == 1) }
                    val normEnabledTitle = dynamicStringResource(R.string.os_lbl_norm_enabled)
                    val normEnabledDesc = dynamicStringResource(R.string.os_desc_norm_enabled)

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = normEnabledTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (normEnabled) dynamicStringResource(R.string.os_status_active) else dynamicStringResource(R.string.os_status_disabled),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(onClick = {
                                infoDialogTitle = normEnabledTitle
                                infoDialogText = normEnabledDesc
                                infoDialogVideo = null
                            }) {
                                Icon(Icons.Outlined.Info, "Info", tint = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f))
                            }
                            Switch(
                                checked = normEnabled,
                                onCheckedChange = {
                                    normEnabled = it
                                    scope.launch(Dispatchers.IO) {
                                        Settings.Global.putInt(context.contentResolver, normEnabledKey, if (it) 1 else 0)
                                        launch(Dispatchers.Main) { onSettingChanged() }
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Detection mode radio group
                    val detectModeKey = OverscrollManager.KEY_NORM_DETECT_MODE
                    var detectMode by remember(refreshKey) { mutableIntStateOf(Settings.Global.getInt(context.contentResolver, detectModeKey, 1)) }
                    val detectModes = listOf(
                        dynamicStringResource(R.string.os_norm_mode_behavior),
                        dynamicStringResource(R.string.os_norm_mode_hybrid),
                        dynamicStringResource(R.string.os_norm_mode_stacktrace)
                    )

                    RadioSelectionGroup(
                        title = dynamicStringResource(R.string.os_lbl_norm_detect_mode),
                        options = detectModes,
                        selectedIndex = detectMode,
                        enabled = isMasterEnabled && normEnabled,
                        onSelect = {
                            detectMode = it
                            scope.launch(Dispatchers.IO) {
                                Settings.Global.putInt(context.contentResolver, detectModeKey, it)
                                launch(Dispatchers.Main) { onSettingChanged() }
                            }
                        },
                        infoText = dynamicStringResource(R.string.os_desc_norm_detect_mode),
                        videoResName = null,
                        onInfoClick = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v }
                    )

                    val normSlidersEnabled = isMasterEnabled && normEnabled

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_norm_ref_delta),
                        key = OverscrollManager.KEY_NORM_REF_DELTA,
                        range = 0.000001f..100f,
                        defVal = 9.9999f,
                        infoText = dynamicStringResource(R.string.os_desc_norm_ref_delta),
                        video = "overscroll_norm_ref_delta",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_norm_detect_mul),
                        key = OverscrollManager.KEY_NORM_DETECT_MUL,
                        range = 0f..10.0f,
                        defVal = 0f,
                        infoText = dynamicStringResource(R.string.os_desc_norm_detect_mul),
                        video = "overscroll_norm_detect_mul",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_norm_factor),
                        key = OverscrollManager.KEY_NORM_FACTOR,
                        range = 0.01f..1.0f,
                        defVal = 0.33f,
                        infoText = dynamicStringResource(R.string.os_desc_norm_factor),
                        video = "overscroll_norm_factor",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_norm_window),
                        key = OverscrollManager.KEY_NORM_WINDOW,
                        range = 2f..64f,
                        defVal = 2f,
                        infoText = dynamicStringResource(R.string.os_desc_norm_window),
                        video = "overscroll_norm_window",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onSettingChanged
                    )

                    OverscrollFloatSlider(
                        context = context,
                        title = dynamicStringResource(R.string.os_lbl_norm_ramp),
                        key = OverscrollManager.KEY_NORM_RAMP,
                        range = 1f..20f,
                        defVal = 1f,
                        infoText = dynamicStringResource(R.string.os_desc_norm_ramp),
                        video = "overscroll_norm_ramp",
                        onInfo = { t, s, v -> infoDialogTitle = t; infoDialogText = s; infoDialogVideo = v },
                        refreshKey = refreshKey,
                        enabled = normSlidersEnabled,
                        onChange = onSettingChanged
                    )
                }
            }

            item(key = "app_configs_header", contentType = "settings_group") {
                val expanded = expandedStates["apps"] ?: false
                
                val infoTitle = dynamicStringResource(R.string.os_group_apps)
                val infoText = dynamicStringResource(R.string.os_group_apps)

                ExpandableSettingsGroupCard(
                    title = dynamicStringResource(R.string.os_group_apps),
                    enabled = isMasterEnabled,
                    expanded = expanded,
                    onExpandChange = { expandedStates["apps"] = it }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showAddAppDialog = true },
                            enabled = isMasterEnabled,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(dynamicStringResource(R.string.os_btn_add_app))
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                infoDialogTitle = infoTitle
                                infoDialogText = infoText
                                infoDialogVideo = "app_config"
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.alpha(if (isMasterEnabled) 1f else 0.5f)) {
                        appConfigs.forEachIndexed { index, app ->
                            key(app.pkg) {
                                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                                AppConfigCard(
                                    context = context,
                                    item = app,
                                    onConfigChange = { newItem ->
                                        val newList = appConfigs.toMutableList()
                                        newList[index] = newItem
                                        appConfigs = newList
                                        scope.launch { OverscrollManager.saveAppConfig(context, appConfigs) }
                                    },
                                    onDelete = {
                                        val newList = appConfigs.toMutableList().apply { removeAt(index) }
                                        appConfigs = newList
                                        scope.launch { OverscrollManager.saveAppConfig(context, newList) }
                                    },
                                    enabled = isMasterEnabled
                                )
                            }
                        }
                    }
                }
            }
            
            item(key = "reset_button", contentType = "button") {
                Button(
                    onClick = {
                        scope.launch {
                            OverscrollManager.resetAll(context)
                            isMasterEnabled = true 
                            refreshKey++
                            appConfigs = OverscrollManager.getAppConfigs(context)
                            val message = RemoteStringsManager.getString(context, R.string.os_msg_reset_done)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = isMasterEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (isMasterEnabled) 1f else 0.5f)
                ) {
                    Text(dynamicStringResource(R.string.os_btn_reset))
                }
            }
        }

        TopBarBlurOverlay(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            topBarHeight = innerPadding.calculateTopPadding(),
            blurState = blurState,
            isScrolled = isScrolled
        )
    }
    }

    if (infoDialogTitle != null && infoDialogText != null) {
        InfoDialog(title = infoDialogTitle!!, text = infoDialogText!!, videoResName = infoDialogVideo, onDismiss = { infoDialogTitle = null; infoDialogText = null; infoDialogVideo = null })
    }

    if (showAddAppDialog) {
        AppSelectorDialog(
            context = context,
            onDismiss = { showAddAppDialog = false },
            onAppSelected = { pkg ->
                if (appConfigs.none { it.pkg == pkg }) {
                    val newList = appConfigs.toMutableList()
                    newList.add(AppConfigItem(pkg, false, 1.0f, false))
                    appConfigs = newList
                    scope.launch { OverscrollManager.saveAppConfig(context, newList) }
                    showAddAppDialog = false
                } else {
                    val message = RemoteStringsManager.getString(context, R.string.os_msg_app_exists)
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}


@Composable
private fun ExpandableSettingsGroupCard(
    title: String,
    enabled: Boolean = true,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_rotation",
        animationSpec = tween(300)
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!expanded) } 
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun MasterSwitchCard(
    title: String, 
    isChecked: Boolean, 
    onCheckedChange: (Boolean) -> Unit, 
    onInfoClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (isChecked) dynamicStringResource(R.string.os_status_active) else dynamicStringResource(R.string.os_status_disabled), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onInfoClick, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Outlined.Info, "Info", tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Switch(checked = isChecked, onCheckedChange = onCheckedChange)
            
        }
    }
}

@Composable
private fun StaticTestCard(label: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.height(140.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                dynamicStringResource(R.string.os_card_example_text),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun OverscrollFloatSlider(
    context: Context,
    title: String,
    key: String,
    range: ClosedFloatingPointRange<Float>,
    defVal: Float,
    infoText: String,
    video: String,
    onInfo: (String, String, String?) -> Unit,
    refreshKey: Int,
    enabled: Boolean = true,
    onChange: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var value by remember(refreshKey) { mutableFloatStateOf(Settings.Global.getFloat(context.contentResolver, key, defVal)) }

    SliderSettingFloat(
        title = title,
        value = value,
        range = range,
        unit = "",
        enabled = enabled,
        onValueChange = { 
            value = it
            scope.launch(Dispatchers.IO) { 
                Settings.Global.putFloat(context.contentResolver, key, it) 
                launch(Dispatchers.Main) { onChange() }
            }
        },
        onDefault = {
            value = defVal
            scope.launch(Dispatchers.IO) { 
                Settings.Global.putFloat(context.contentResolver, key, defVal)
                launch(Dispatchers.Main) { onChange() }
            }
        },
        infoText = infoText,
        videoResName = video,
        onInfoClick = onInfo
    )
}

@Composable
private fun ScaleGroup(
    context: Context,
    title: String,
    prefix: String,
    desc: String,
    onInfo: (String, String, String?) -> Unit,
    refreshKey: Int,
    isMasterEnabled: Boolean,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onChange: () -> Unit,
    defIntensity: Float = 0f,
    defIntensityHoriz: Float = 0f,
    defLimitMin: Float = 0.1f
) {
    ExpandableSettingsGroupCard(
        title = title,
        enabled = isMasterEnabled,
        expanded = expanded,
        onExpandChange = onExpandChange
    ) {
        val scope = rememberCoroutineScope()
        // IMPORTANT: Calculate suffix here to ensure UI matches current environment (Pine/Xposed)
        val suffix = if (AppConfig.IS_XPOSED) "_xposed" else "_pine"
        
        val modeKey = "${prefix}_mode$suffix"
        var mode by remember(refreshKey) { 
            mutableIntStateOf(Settings.Global.getInt(context.contentResolver, modeKey, 0))
        }
        val modes = listOf(
            dynamicStringResource(R.string.os_mode_off),
            dynamicStringResource(R.string.os_mode_shrink),
            dynamicStringResource(R.string.os_mode_grow)
            )
        
        val areSlidersActive = isMasterEnabled && (mode != 0)

        RadioSelectionGroup(
            title = dynamicStringResource(R.string.os_lbl_scale_mode),
            options = modes,
            selectedIndex = mode,
            enabled = isMasterEnabled,
            onSelect = { 
                mode = it 
                scope.launch(Dispatchers.IO) { 
                    Settings.Global.putInt(context.contentResolver, modeKey, it) 
                    launch(Dispatchers.Main) { onChange() }
                }
            },
            infoText = desc,
            videoResName = "${prefix}_mode",
            onInfoClick = onInfo
        )

        OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_scale_int), "${prefix}_intensity$suffix", 0f..10f, defIntensity, 
            infoText = dynamicStringResource(R.string.os_desc_scale_intensity), video = "${prefix}_intensity", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
        
        OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_scale_int_horiz), "${prefix}_intensity_horiz$suffix", 0f..10f, defIntensityHoriz, 
            infoText = dynamicStringResource(R.string.os_desc_scale_int_horiz), video = "${prefix}_intensity_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)

        OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_scale_limit), "${prefix}_limit_min$suffix", 0.1f..10f, defLimitMin, 
            infoText = dynamicStringResource(R.string.os_desc_scale_limit), video = "${prefix}_intensity_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
        
        val anchorTitle = dynamicStringResource(R.string.os_desc_anchor)
        
        when (prefix) {
            "overscroll_scale" -> {
                OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_anchor_y), "overscroll_scale_anchor_y$suffix", 0f..1f, 0.5f, 
                    infoText = anchorTitle, video = "overscroll_scale_anchor_y", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
                OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_anchor_x_horiz), "overscroll_scale_anchor_x_horiz$suffix", 0f..1f, 0.5f, 
                    infoText = dynamicStringResource(R.string.os_desc_anchor_x_horiz), video = "overscroll_scale_anchor_x_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
            }
            "overscroll_h_scale" -> {
                OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_anchor_x), "overscroll_h_scale_anchor_x$suffix", 0f..1f, 0.5f, 
                    infoText = anchorTitle, video = "overscroll_h_scale_anchor_x", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
                OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_anchor_y_horiz), "overscroll_h_scale_anchor_y_horiz$suffix", 0f..1f, 0.5f, 
                    infoText = dynamicStringResource(R.string.os_desc_anchor_y_horiz), video = "overscroll_h_scale_anchor_y_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
            }
            "overscroll_zoom" -> {
                OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_anchor_x), "overscroll_zoom_anchor_x$suffix", 0f..1f, 0.5f, 
                    infoText = anchorTitle, video = "overscroll_zoom_anchor_x", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
                OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_anchor_y), "overscroll_zoom_anchor_y$suffix", 0f..1f, 0.5f, 
                    infoText = anchorTitle, video = "overscroll_zoom_anchor_y", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
                
                OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_anchor_x_horiz), "overscroll_zoom_anchor_x_horiz$suffix", 0f..1f, 0.5f, 
                    infoText = dynamicStringResource(R.string.os_desc_anchor_x_horiz), video = "overscroll_zoom_anchor_x_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
                OverscrollFloatSlider(context, dynamicStringResource(R.string.os_lbl_anchor_y_horiz), "overscroll_zoom_anchor_y_horiz$suffix", 0f..1f, 0.5f, 
                    infoText = dynamicStringResource(R.string.os_desc_anchor_y_horiz), video = "overscroll_zoom_anchor_y_horiz", onInfo = onInfo, refreshKey = refreshKey, enabled = areSlidersActive, onChange = onChange)
            }
        }
    }
}