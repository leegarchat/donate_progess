package org.pixel.customparts.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.RadioSelectionGroup
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.SettingsCompat
import org.pixel.customparts.utils.dynamicStringResource

class MagnifierSettingsActivity : ComponentActivity() {
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
                    MagnifierSettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnifierSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = {
                    Text(dynamicStringResource(R.string.magnifier_section_title), fontWeight = FontWeight.Bold)
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
                    top = 16.dp + innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Xposed warning
                if (AppConfig.IS_XPOSED) {
                    item {
                        ExpandableWarningCard(
                            title = dynamicStringResource(R.string.magnifier_xposed_warning_title),
                            text = dynamicStringResource(R.string.magnifier_xposed_warning_desc),
                            modifier = Modifier.padding(bottom = 0.dp)
                        )
                    }
                }

                item {
                    MagnifierSection()
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
}

@Composable
private fun MagnifierSection() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var enabled by remember {
        mutableStateOf(
            SettingsCompat.getInt(context, SettingsKeys.MAGNIFIER_CUSTOM_ENABLED, 0) == 1
        )
    }
    var zoom by remember {
        mutableFloatStateOf(
            SettingsCompat.getFloat(context, SettingsKeys.MAGNIFIER_CUSTOM_ZOOM, 1.25f)
        )
    }
    var sizeScale by remember {
        mutableFloatStateOf(
            SettingsCompat.getFloat(context, SettingsKeys.MAGNIFIER_CUSTOM_SIZE, 1.0f)
        )
    }
    var shape by remember {
        mutableIntStateOf(
            SettingsCompat.getInt(context, SettingsKeys.MAGNIFIER_CUSTOM_SHAPE, 0)
        )
    }
    var offsetY by remember {
        mutableIntStateOf(
            SettingsCompat.getInt(context, SettingsKeys.MAGNIFIER_CUSTOM_OFFSET_Y, 0)
        )
    }

    SettingsGroupCard(title = dynamicStringResource(R.string.magnifier_section_title)) {
        GenericSwitchRow(
            title = dynamicStringResource(R.string.magnifier_enable_title),
            summary = dynamicStringResource(R.string.magnifier_enable_summary),
            checked = enabled,
            onCheckedChange = { newValue ->
                enabled = newValue
                SettingsCompat.putInt(
                    context,
                    SettingsKeys.MAGNIFIER_CUSTOM_ENABLED,
                    if (newValue) 1 else 0
                )
            }
        )

        HorizontalDivider()

        SliderSettingFloat(
            title = dynamicStringResource(R.string.magnifier_zoom_title),
            value = zoom,
            range = 0.5f..4.0f,
            unit = "x",
            enabled = enabled,
            onValueChange = { newValue ->
                zoom = newValue
                SettingsCompat.putFloat(context, SettingsKeys.MAGNIFIER_CUSTOM_ZOOM, newValue)
            },
            onDefault = {
                zoom = 1.25f
                SettingsCompat.putFloat(context, SettingsKeys.MAGNIFIER_CUSTOM_ZOOM, 1.25f)
            },
            valueText = String.format("%.2fx", zoom)
        )

        HorizontalDivider()

        SliderSettingFloat(
            title = dynamicStringResource(R.string.magnifier_size_title),
            value = sizeScale,
            range = 0.5f..3.0f,
            unit = "x",
            enabled = enabled,
            onValueChange = { newValue ->
                sizeScale = newValue
                SettingsCompat.putFloat(context, SettingsKeys.MAGNIFIER_CUSTOM_SIZE, newValue)
            },
            onDefault = {
                sizeScale = 1.0f
                SettingsCompat.putFloat(context, SettingsKeys.MAGNIFIER_CUSTOM_SIZE, 1.0f)
            },
            valueText = String.format("%.2fx", sizeScale)
        )

        HorizontalDivider()

        RadioSelectionGroup(
            title = dynamicStringResource(R.string.magnifier_shape_title),
            options = listOf(
                dynamicStringResource(R.string.magnifier_shape_default),
                dynamicStringResource(R.string.magnifier_shape_square),
                dynamicStringResource(R.string.magnifier_shape_circle)
            ),
            selectedIndex = shape,
            enabled = enabled,
            onSelect = { newValue ->
                shape = newValue
                SettingsCompat.putInt(context, SettingsKeys.MAGNIFIER_CUSTOM_SHAPE, newValue)
            }
        )

        HorizontalDivider()

        SliderSetting(
            title = dynamicStringResource(R.string.magnifier_offset_y_title),
            value = offsetY,
            range = -200..200,
            unit = "dp",
            enabled = enabled,
            onValueChange = { newValue ->
                offsetY = newValue
                SettingsCompat.putInt(context, SettingsKeys.MAGNIFIER_CUSTOM_OFFSET_Y, newValue)
            },
            onDefault = {
                offsetY = 0
                SettingsCompat.putInt(context, SettingsKeys.MAGNIFIER_CUSTOM_OFFSET_Y, 0)
            },
            valueText = "${offsetY}dp"
        )
    }
}
