package org.pixel.customparts.activities

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.InfoDialog
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.utils.RootUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import org.pixel.customparts.utils.dynamicStringResource

import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment

class ImsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ImsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImsScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogText by remember { mutableStateOf<String?>(null) }

    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { Text(
                        dynamicStringResource(R.string.ims_category_title),
                        fontWeight = FontWeight.Bold
                    ) },
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
                    start = 16.dp,
                    top = 16.dp + innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            
            item {
                ExpandableWarningCard(
                    title = dynamicStringResource(R.string.dt2w_info_title),
                    text = dynamicStringResource(R.string.ims_footer_info),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.ims_sec_voice)) {
                    ImsSettingSwitch(
                        title = dynamicStringResource(R.string.ims_lbl_volte),
                        key = ImsManager.KEY_VOLTE,
                        infoText = dynamicStringResource(R.string.ims_desc_volte),
                        onInfoClick = { t, s -> infoDialogTitle = t; infoDialogText = s }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    ImsSettingSwitch(
                        title = dynamicStringResource(R.string.ims_lbl_vowifi),
                        key = ImsManager.KEY_WFC,
                        infoText = dynamicStringResource(R.string.ims_desc_vowifi),
                        onInfoClick = { t, s -> infoDialogTitle = t; infoDialogText = s }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ImsSettingSwitch(
                        title = dynamicStringResource(R.string.ims_lbl_vt),
                        key = ImsManager.KEY_VT,
                        infoText = dynamicStringResource(R.string.ims_desc_vt),
                        onInfoClick = { t, s -> infoDialogTitle = t; infoDialogText = s }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ImsSettingSwitch(
                        title = dynamicStringResource(R.string.ims_lbl_cross_sim),
                        key = ImsManager.KEY_CROSS_SIM,
                        infoText = dynamicStringResource(R.string.ims_desc_cross_sim),
                        onInfoClick = { t, s -> infoDialogTitle = t; infoDialogText = s }
                    )
                }
            }

            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.ims_sec_network)) {
                    ImsSettingSwitch(
                        title = dynamicStringResource(R.string.ims_lbl_vonr),
                        key = ImsManager.KEY_VONR,
                        infoText = dynamicStringResource(R.string.ims_desc_vonr),
                        onInfoClick = { t, s -> infoDialogTitle = t; infoDialogText = s }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ImsSettingSwitch(
                        title = dynamicStringResource(R.string.ims_lbl_5g),
                        key = ImsManager.KEY_5G,
                        infoText = dynamicStringResource(R.string.ims_desc_5g),
                        onInfoClick = { t, s -> infoDialogTitle = t; infoDialogText = s }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    ImsSettingSwitch(
                        title = dynamicStringResource(R.string.ims_lbl_5g_thresh),
                        key = ImsManager.KEY_5G_THRESH,
                        infoText = dynamicStringResource(R.string.ims_desc_5g_thresh),
                        onInfoClick = { t, s -> infoDialogTitle = t; infoDialogText = s }
                    )
                }
            }

            item {
                SettingsGroupCard(title = dynamicStringResource(R.string.ims_sec_advanced)) {
                    ImsSettingSwitch(
                        title = dynamicStringResource(R.string.ims_lbl_ut),
                        key = ImsManager.KEY_UT,
                        infoText = dynamicStringResource(R.string.ims_desc_ut),
                        onInfoClick = { t, s -> infoDialogTitle = t; infoDialogText = s }
                    )
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
            videoResName = null,
            onDismiss = { 
                infoDialogTitle = null
                infoDialogText = null
            }
        )
    }
}

@Composable
fun ImsSettingSwitch(
    title: String,
    key: String,
    infoText: String,
    onInfoClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isChecked by remember { 
        mutableStateOf(Settings.Secure.getInt(context.contentResolver, key, 0) == 1) 
    }

    GenericSwitchRow(
        title = title,
        checked = isChecked,
        onCheckedChange = { checked ->
            isChecked = checked
            scope.launch {
                withContext(Dispatchers.IO) {
                    Settings.Secure.putInt(context.contentResolver, key, if (checked) 1 else 0)
                    ImsManager.updateImsProfile(context)
                }
            }
        },
        summary = null,
        infoText = infoText,
        videoResName = null,
        onInfoClick = { t, s, _ -> onInfoClick(t, s) }
    )
}

object ImsManager {
    private const val TAG = "PixelPartsIMS"

    const val KEY_VOLTE = "pixel_ims_volte"
    const val KEY_WFC = "pixel_ims_wfc"
    const val KEY_VT = "pixel_ims_vt"
    const val KEY_VONR = "pixel_ims_vonr"
    const val KEY_CROSS_SIM = "pixel_ims_cross_sim"
    const val KEY_UT = "pixel_ims_ut"
    const val KEY_5G = "pixel_ims_5g"
    const val KEY_5G_THRESH = "pixel_ims_5g_thresh"

    fun onBoot(context: Context) {
        Log.d(TAG, "onBoot: Re-applying IMS config")
        updateImsProfile(context)
    }

    fun updateImsProfile(context: Context) {
        val subMgr = context.getSystemService(SubscriptionManager::class.java) ?: return
        val configMgr = context.getSystemService(CarrierConfigManager::class.java) ?: return
        
        val subIds = try {
            subMgr.activeSubscriptionInfoList?.map { it.subscriptionId }
        } catch (e: SecurityException) {
            Log.e(TAG, "No permission to get subs API.", e)
            null
        }

        val targetSubIds = if (subIds.isNullOrEmpty()) {
            if (RootUtils.hasRootAccess()) {
                Log.w(TAG, "Subs list empty/no perm. Fallback to Root with IDs [1, 2]")
                listOf(1, 2) 
            } else {
                Log.e(TAG, "No active subscriptions and No Root. Cannot apply config.")
                return
            }
        } else {
            subIds
        }

        val enableVoLTE = getInt(context, KEY_VOLTE)
        val enableWFC = getInt(context, KEY_WFC)
        val enableVT = getInt(context, KEY_VT)
        val enableVoNR = getInt(context, KEY_VONR)
        val enableCrossSim = getInt(context, KEY_CROSS_SIM)
        val enableUT = getInt(context, KEY_UT)
        val enable5G = getInt(context, KEY_5G)
        val enable5GThresh = getInt(context, KEY_5G_THRESH)

        for (subId in targetSubIds) {
            val bundle = android.os.PersistableBundle()
            val cmdBuilder = StringBuilder()

            if (!enableVoLTE && !enableWFC && !enableVT && !enableVoNR &&
                !enableCrossSim && !enableUT && !enable5G) {
                applyConfig(configMgr, subId, null, "reset-all")
                continue
            }

            if (enableVoLTE) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
                bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true)
                bundle.putBoolean(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false)
                bundle.putBoolean(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false)
                bundle.putBoolean(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true)
                
                cmdBuilder.append("carrier_volte_available_bool true ")
                cmdBuilder.append("editable_enhanced_4g_lte_bool true ")
                cmdBuilder.append("hide_enhanced_4g_lte_bool false ")
                cmdBuilder.append("enhanced_4g_lte_on_by_default_bool true ")
            }

            if (enableWFC) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true)
                bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true)
                bundle.putBoolean(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, true)
                bundle.putBoolean("show_wifi_calling_icon_in_status_bar_bool", true)
                
                cmdBuilder.append("carrier_wfc_ims_available_bool true ")
                cmdBuilder.append("carrier_wfc_supports_wifi_only_bool true ")
                cmdBuilder.append("editable_wfc_mode_bool true ")
                cmdBuilder.append("editable_wfc_roaming_mode_bool true ")
                cmdBuilder.append("show_wifi_calling_icon_in_status_bar_bool true ")
            }

            if (enableVT) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true)
                cmdBuilder.append("carrier_vt_available_bool true ")
            }

            if (enableUT) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL, true)
                cmdBuilder.append("carrier_supports_ss_over_ut_bool true ")
            }

            if (enableCrossSim) {
                bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL, true)
                bundle.putBoolean("enable_cross_sim_calling_on_opportunistic_data_bool", true)
                
                cmdBuilder.append("carrier_cross_sim_ims_available_bool true ")
                cmdBuilder.append("enable_cross_sim_calling_on_opportunistic_data_bool true ")
            }

            if (android.os.Build.VERSION.SDK_INT >= 34 && enableVoNR) {
                bundle.putBoolean(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true)
                bundle.putBoolean(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true)
                
                cmdBuilder.append("vonr_enabled_bool true ")
                cmdBuilder.append("vonr_setting_visibility_bool true ")
            }

            if (enable5G) {
                bundle.putIntArray(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY, intArrayOf(1, 2))
                if (enable5GThresh) {
                    bundle.putIntArray(CarrierConfigManager.KEY_5G_NR_SSRSRP_THRESHOLDS_INT_ARRAY, intArrayOf(-128, -118, -108, -98))
                }
            }

            applyConfig(configMgr, subId, bundle, cmdBuilder.toString())
        }
    }

    private fun applyConfig(
        mgr: CarrierConfigManager, 
        subId: Int, 
        bundle: android.os.PersistableBundle?, 
        shellCmdArgs: String
    ) {
        try {
            mgr.overrideConfig(subId, bundle)
            Log.d(TAG, "Applied Config via API for Sub $subId")
        } catch (e: SecurityException) {
            Log.w(TAG, "API call failed for Sub $subId. Trying Root...", e)
            
            if (RootUtils.hasRootAccess()) {
                if (bundle == null) {
                    runRootCmd("cmd phone cc clear-values -p $subId")
                } else {
                    if (shellCmdArgs.isNotEmpty()) {
                        runRootCmd("cmd phone cc set-value -p $subId $shellCmdArgs")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
        }
    }

    private fun runRootCmd(cmd: String) {
        try {
            Log.d(TAG, "Executing SU: $cmd")
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            
            val exitCode = p.waitFor()
            if (exitCode == 0) {
                Log.d(TAG, "SU Command Success (Exit: 0)")
            } else {
                val errorStream = BufferedReader(InputStreamReader(p.errorStream))
                val sb = StringBuilder()
                var line: String?
                while (errorStream.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                Log.e(TAG, "SU Command FAILED (Exit: $exitCode). Error: $sb")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SU execution failed", e)
        }
    }

    private fun getInt(c: Context, key: String): Boolean {
        return Settings.Secure.getInt(c.contentResolver, key, 0) == 1
    }
}