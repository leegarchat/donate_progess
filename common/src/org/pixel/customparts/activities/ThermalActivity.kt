package org.pixel.customparts.activities

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import java.io.BufferedReader
import java.io.InputStreamReader
import org.pixel.customparts.utils.dynamicStringResource
import org.pixel.customparts.utils.RemoteStringsManager

class ThermalActivity : ComponentActivity() {
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
                    ThermalScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }
    var batteryProfile by remember { mutableIntStateOf(0) }
    var socProfile by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var showRebootDialog by remember { mutableStateOf(false) }
    var isRiskyConfig by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val (batIndex, socIndex) = ThermalManager.getCurrentProfiles(context)
        batteryProfile = batIndex
        socProfile = socIndex
        isLoading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { 
                    Text(dynamicStringResource(R.string.thermal_title),
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
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            
            item {
                ExpandableWarningCard(
                    title = dynamicStringResource(R.string.thermal_info_title),
                    text = dynamicStringResource(R.string.thermal_info_summary)
                )
            }

            item {
                ThermalSection(
                    title = dynamicStringResource(R.string.thermal_sec_battery),
                    icon = Icons.Rounded.BatteryStd,
                    currentMode = batteryProfile,
                    modes = ThermalManager.getModes(),
                    isLoading = isLoading,
                    onModeSelect = { newModeIndex ->
                        batteryProfile = newModeIndex
                        scope.launch {
                            ThermalManager.setBatteryProfile(context, newModeIndex)
                        }
                        isRiskyConfig = newModeIndex >= 3
                        showRebootDialog = true
                    },
                    getDescription = { mode -> ThermalManager.getBatteryDescription(mode) }
                )
            }

            item {
                ThermalSection(
                    title = dynamicStringResource(R.string.thermal_sec_soc),
                    icon = Icons.Rounded.Memory,
                    currentMode = socProfile,
                    modes = ThermalManager.getModes(),
                    isLoading = isLoading,
                    onModeSelect = { newModeIndex ->
                        socProfile = newModeIndex
                        scope.launch {
                            ThermalManager.setSocProfile(context, newModeIndex)
                        }
                        isRiskyConfig = newModeIndex >= 3
                        showRebootDialog = true
                    },
                    getDescription = { mode -> ThermalManager.getSocDescription(mode) }
                )
            }
        }

            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                topBarHeight = innerPadding.calculateTopPadding(),
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }

    if (showRebootDialog) {
        val msg = if (isRiskyConfig) {
            dynamicStringResource(R.string.thermal_dialog_risk_msg)
        } else {
            dynamicStringResource(R.string.thermal_dialog_safe_msg)
        }

        Dialog(onDismissRequest = { showRebootDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        text = dynamicStringResource(R.string.thermal_dialog_reboot_title),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showRebootDialog = false }) {
                            Text(dynamicStringResource(R.string.btn_ok))
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { 
                                showRebootDialog = false
                                rebootDevice(context)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(dynamicStringResource(R.string.btn_reboot))
                        }
                    }
                }
            }
        }
    }
}

private fun rebootDevice(context: Context) {
    try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.reboot(null)
    } catch (e: Exception) {
        Log.e("ThermalActivity", "Failed to reboot via PowerManager", e)
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "svc power reboot"))
        } catch (ex: Exception) {
            Log.e("ThermalActivity", "Shell reboot failed", ex)
        }
    }
}

@Composable
fun ThermalSection(
    title: String,
    icon: ImageVector,
    currentMode: Int,
    modes: List<ThermalMode>,
    isLoading: Boolean,
    onModeSelect: (Int) -> Unit,
    getDescription: @Composable (Int) -> String
) {
    val isDangerous = currentMode >= 3 
    val targetContainerColor = if (isDangerous) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    
    val containerColor by animateColorAsState(targetContainerColor, label = "containerColor")

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                
                if (isLoading) {
                    Spacer(Modifier.weight(1f))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), 
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            modes.forEach { mode ->
                val isSelected = mode.id == currentMode && !isLoading
                
                val selectedColor = if (isDangerous) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }

                val rowBackgroundColor by animateColorAsState(
                    targetValue = if (isSelected) selectedColor else Color.Transparent,
                    label = "rowBg"
                )

                val weightVal by animateIntAsState(
                    targetValue = if (isSelected) 700 else 400,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "weight"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowBackgroundColor)
                        .clickable { onModeSelect(mode.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = dynamicStringResource(mode.labelRes),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight(weightVal)
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    AnimatedVisibility(
                        visible = isSelected,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle, 
                            null, 
                            tint = if (isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f), 
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = getDescription(currentMode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

data class ThermalMode(val id: Int, val labelRes: Int)

object ThermalManager {
    private const val TAG = "PixelPartsThermal"

    private const val KEY_THERMAL_BATTERY = "thermal_battery_mode"
    private const val KEY_THERMAL_SOC = "thermal_soc_mode"

    private const val PROP_BATTERY = "persist.sys.pixelparts.battery"
    private const val PROP_SOC = "persist.sys.pixelparts.soc"
    private const val PROP_CONFIG_TARGET = "persist.sys.pixelparts.thermal_config"

    private const val MODE_STOCK = 0
    private const val MODE_SOFT = 1
    private const val MODE_MEDIUM = 2
    private const val MODE_HARD = 3
    private const val MODE_OFF = 4

    private val MODE_STRINGS = listOf("stock", "soft", "medium", "hard", "off")

    fun getModes(): List<ThermalMode> = listOf(
        ThermalMode(MODE_STOCK, R.string.thermal_mode_stock),
        ThermalMode(MODE_SOFT, R.string.thermal_mode_soft),
        ThermalMode(MODE_MEDIUM, R.string.thermal_mode_medium),
        ThermalMode(MODE_HARD, R.string.thermal_mode_hard),
        ThermalMode(MODE_OFF, R.string.thermal_mode_off)
    )

    @Composable
    fun getBatteryDescription(mode: Int): String {
        val resId = when(mode) {
            MODE_STOCK -> R.string.thermal_desc_bat_stock
            MODE_SOFT -> R.string.thermal_desc_bat_soft
            MODE_MEDIUM -> R.string.thermal_desc_bat_medium
            MODE_HARD -> R.string.thermal_desc_bat_hard
            MODE_OFF -> R.string.thermal_desc_bat_off
            else -> R.string.thermal_desc_bat_stock
        }
        return dynamicStringResource(resId)
    }

    @Composable
    fun getSocDescription(mode: Int): String {
        val resId = when(mode) {
            MODE_STOCK -> R.string.thermal_desc_soc_stock
            MODE_SOFT -> R.string.thermal_desc_soc_soft
            MODE_MEDIUM -> R.string.thermal_desc_soc_medium
            MODE_HARD -> R.string.thermal_desc_soc_hard
            MODE_OFF -> R.string.thermal_desc_soc_off
            else -> R.string.thermal_desc_soc_stock
        }
        return dynamicStringResource(resId)
    }

    fun onBoot(context: Context) {
        Log.d(TAG, "onBoot: Re-applying Thermal config")
        updateThermalProps(context)
    }

    suspend fun getCurrentProfiles(context: Context): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val batStr = prefs.getString(KEY_THERMAL_BATTERY, "stock") ?: "stock"
        val socStr = prefs.getString(KEY_THERMAL_SOC, "stock") ?: "stock"

        val batIndex = MODE_STRINGS.indexOf(batStr).takeIf { it >= 0 } ?: MODE_STOCK
        val socIndex = MODE_STRINGS.indexOf(socStr).takeIf { it >= 0 } ?: MODE_STOCK

        return@withContext Pair(batIndex, socIndex)
    }

    suspend fun setBatteryProfile(context: Context, modeIndex: Int) {
        val modeStr = MODE_STRINGS.getOrElse(modeIndex) { "stock" }
        withContext(Dispatchers.IO) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putString(KEY_THERMAL_BATTERY, modeStr).apply()
            updateThermalProps(context)
        }
    }

    suspend fun setSocProfile(context: Context, modeIndex: Int) {
        val modeStr = MODE_STRINGS.getOrElse(modeIndex) { "stock" }
        withContext(Dispatchers.IO) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit().putString(KEY_THERMAL_SOC, modeStr).apply()
            updateThermalProps(context)
        }
    }

    private fun updateThermalProps(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val batteryMode = prefs.getString(KEY_THERMAL_BATTERY, "stock") ?: "stock"
        val socMode = prefs.getString(KEY_THERMAL_SOC, "stock") ?: "stock"

        Log.d(TAG, "Updating thermal props. Battery: $batteryMode, SoC: $socMode")

        setSystemProperty(PROP_BATTERY, batteryMode)
        setSystemProperty(PROP_SOC, socMode)

        val socAddName = if (socMode != "stock") "_soc_$socMode" else ""
        val batteryAddName = if (batteryMode != "stock") "_battery_$batteryMode" else ""
        val configFileName = "thermal_info_config$socAddName$batteryAddName.json"

        setSystemProperty(PROP_CONFIG_TARGET, configFileName)
    }

    @SuppressLint("PrivateApi")
    private fun setSystemProperty(key: String, value: String) {
        try {
            val c = Class.forName("android.os.SystemProperties")
            val set = c.getMethod("set", String::class.java, String::class.java)
            set.invoke(null, key, value)
            return
        } catch (e: Exception) {
            Log.w(TAG, "Reflection set failed ($key), trying root...", e)
        }

        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop $key $value")).waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Root set failed for $key", e)
        }
    }
}