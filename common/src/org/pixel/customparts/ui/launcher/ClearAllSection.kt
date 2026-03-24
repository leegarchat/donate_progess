package org.pixel.customparts.ui.launcher

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.activities.ClearAllMode
import org.pixel.customparts.activities.LauncherManager
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.ModuleStatus
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSettingFloat
import org.pixel.customparts.utils.dynamicStringResource
import org.pixel.customparts.utils.SettingsCompat

@Composable
fun ClearAllSection(
    context: Context,
    scope: CoroutineScope,
    refreshKey: Int,
    onSettingChanged: () -> Unit,
    onInfo: (String, String, String?) -> Unit,
    onShowXposedDialog: () -> Unit
) {
    val keyEnabled = LauncherManager.KEY_CLEAR_ALL_ENABLED
    val keyHideActionsRow = LauncherManager.KEY_CLEAR_ALL_HIDE_ACTIONS_ROW
    val keyMode = LauncherManager.KEY_CLEAR_ALL_MODE
    val keyMargin = LauncherManager.KEY_CLEAR_ALL_MARGIN
    
    var enabled by remember(refreshKey) { mutableStateOf(SettingsCompat.getInt(context, keyEnabled, 0) == 1) }
    var hideActionsRow by remember(refreshKey) { mutableStateOf(SettingsCompat.getInt(context, keyHideActionsRow, 0) == 1) }
    var mode by remember(refreshKey) { mutableIntStateOf(SettingsCompat.getInt(context, keyMode, 0)) }
    var margin by remember(refreshKey) { mutableFloatStateOf(SettingsCompat.getFloat(context, keyMargin, 3.0f)) }

    SettingsGroupCard(title = dynamicStringResource(R.string.launcher_clear_all_title)) {
        GenericSwitchRow(
            title = dynamicStringResource(R.string.launcher_clear_all_enable_title),
            summary = if (enabled) dynamicStringResource(R.string.os_status_active) else dynamicStringResource(R.string.os_status_disabled),
            checked = enabled,
            onCheckedChange = { checked ->
                if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive()) {
                    onShowXposedDialog()
                    enabled = false
                } else {
                    enabled = checked
                    scope.launch(Dispatchers.IO) {
                        SettingsCompat.putInt(context, keyEnabled, if (checked) 1 else 0)
                        launch(Dispatchers.Main) { onSettingChanged() }
                    }
                }
            },
            videoResName = "launcher_clear_all",
            infoText = dynamicStringResource(R.string.launcher_clear_all_desc),
            onInfoClick = onInfo
        )

        GenericSwitchRow(
            title = dynamicStringResource(R.string.launcher_ca_hide_actions_row_title),
            summary = if (hideActionsRow) dynamicStringResource(R.string.os_status_active) else dynamicStringResource(R.string.os_status_disabled),
            checked = hideActionsRow,
            enabled = enabled,
            onCheckedChange = { checked ->
                hideActionsRow = checked
                scope.launch(Dispatchers.IO) {
                    SettingsCompat.putInt(context, keyHideActionsRow, if (checked) 1 else 0)
                    launch(Dispatchers.Main) { onSettingChanged() }
                }
            },
            infoText = dynamicStringResource(R.string.launcher_ca_hide_actions_row_desc),
            onInfoClick = onInfo
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        val modes = listOf(
            ClearAllMode(0, R.string.launcher_ca_mode_float, Icons.Rounded.CleaningServices),
            ClearAllMode(1, R.string.launcher_ca_mode_screenshot, Icons.Rounded.PhotoCamera),
            ClearAllMode(2, R.string.launcher_ca_mode_select, Icons.Rounded.SelectAll)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            modes.forEach { item ->
                val isSelected = item.id == mode
                val isRowEnabled = enabled
                
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "rowBg"
                )
                
                val contentAlpha = if (isRowEnabled) 1f else 0.4f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(backgroundColor)
                        .clickable(enabled = isRowEnabled) {
                            if (mode != item.id) {
                                mode = item.id
                                scope.launch(Dispatchers.IO) {
                                    SettingsCompat.putInt(context, keyMode, item.id)
                                    launch(Dispatchers.Main) { onSettingChanged() }
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp)
                        .alpha(contentAlpha),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                item.icon,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = dynamicStringResource(item.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    AnimatedVisibility(
                        visible = isSelected,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        val isSliderActive = enabled && (mode == 0)
        
        SliderSettingFloat(
            title = dynamicStringResource(R.string.launcher_ca_margin),
            value = margin,
            range = 0.1f..16.0f,
            unit = "x",
            enabled = isSliderActive,
            onValueChange = { 
                margin = it
                scope.launch(Dispatchers.IO) {
                    SettingsCompat.putFloat(context, keyMargin, it)
                }
            },
            onDefault = {
                val def = 3.0f
                margin = def
                scope.launch(Dispatchers.IO) {
                    SettingsCompat.putFloat(context, keyMargin, def)
                    launch(Dispatchers.Main) { onSettingChanged() }
                }
            },
            onValueChangeFinished = {
                onSettingChanged()
            },
            infoText = dynamicStringResource(R.string.launcher_ca_margin_desc),
            onInfoClick = onInfo
        )
    }
}