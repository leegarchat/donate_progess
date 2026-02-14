package org.pixel.customparts.ui.launcher

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.activities.LauncherManager
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.ModuleStatus
import org.pixel.customparts.utils.SettingsCompat
import org.pixel.customparts.utils.dynamicStringResource

@Composable
fun SearchWidgetSection(
    context: Context,
    scope: CoroutineScope,
    showXposedDialog: () -> Unit,
    onInfoClick: (String, String, String?) -> Unit,
    onSettingChanged: () -> Unit = {}
) {
    val keyDockEnable = LauncherManager.KEY_DOCK_ENABLE
    val keyHideSearch = LauncherManager.KEY_HIDE_SEARCH
    val keyHideDock = LauncherManager.KEY_HIDE_DOCK
    val keyPaddingHomepage = LauncherManager.KEY_PADDING_HOMEPAGE
    val keyPaddingDock = LauncherManager.KEY_PADDING_DOCK
    val keyPaddingSearch = LauncherManager.KEY_PADDING_SEARCH
    val keyPaddingDots = LauncherManager.KEY_PADDING_DOTS
    val keyPaddingDotsX = LauncherManager.KEY_PADDING_DOTS_X
    val videoKeyHideSearch = LauncherManager.KEY_HIDE_SEARCH_BASE
    val videoKeyHideDock = "launcher_hidden_dock"
    
    var dockCustomizationEnabled by remember { mutableStateOf(SettingsCompat.getInt(context, keyDockEnable, 0) == 1) }
    var hideSearchEnabled by remember { mutableStateOf(SettingsCompat.getInt(context, keyHideSearch, 0) == 1) }
    var hideDockEnabled by remember { mutableStateOf(SettingsCompat.getInt(context, keyHideDock, 0) == 1) }
    
    val initPaddingHomepage = remember { SettingsCompat.getInt(context, keyPaddingHomepage, 200) }
    var paddingHomepage by remember { mutableIntStateOf(initPaddingHomepage) }

    val initPaddingDock = remember { SettingsCompat.getInt(context, keyPaddingDock, 0) }
    var paddingDock by remember { mutableIntStateOf(initPaddingDock) }

    val initPaddingSearch = remember { SettingsCompat.getInt(context, keyPaddingSearch, 0) }
    var paddingSearch by remember { mutableIntStateOf(initPaddingSearch) }

    val initPaddingDots = remember { SettingsCompat.getInt(context, keyPaddingDots, 0) }
    var paddingDots by remember { mutableIntStateOf(initPaddingDots) }

    val initPaddingDotsX = remember { SettingsCompat.getInt(context, keyPaddingDotsX, 0) }
    var paddingDotsX by remember { mutableIntStateOf(initPaddingDotsX) }

    SettingsGroupCard(title = dynamicStringResource(R.string.search_widget_group_title)) {
        GenericSwitchRow(
            title = dynamicStringResource(R.string.search_widget_enable_dock),
            checked = dockCustomizationEnabled,
            onCheckedChange = { checked ->
                if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive()) {
                    showXposedDialog()
                    dockCustomizationEnabled = false
                } else {
                    dockCustomizationEnabled = checked
                    scope.launch(Dispatchers.IO) {
                        SettingsCompat.putInt(context, keyDockEnable, if (checked) 1 else 0)
                    }
                    onSettingChanged()
                }
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_dock),
            videoResName = null,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        GenericSwitchRow(
            title = dynamicStringResource(R.string.search_widget_hide_search),
            checked = hideSearchEnabled,
            enabled = dockCustomizationEnabled,
            onCheckedChange = { checked ->
                hideSearchEnabled = checked
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyHideSearch, if (checked) 1 else 0)
                }
                onSettingChanged()
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_hide_search),
            videoResName = videoKeyHideSearch,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        GenericSwitchRow(
            title = dynamicStringResource(R.string.search_widget_hide_dock),
            checked = hideDockEnabled,
            enabled = dockCustomizationEnabled,
            onCheckedChange = { checked ->
                hideDockEnabled = checked
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyHideDock, if (checked) 1 else 0)
                }
                onSettingChanged()
            },
            infoText = dynamicStringResource(R.string.search_widget_desc_hide_dock),
            videoResName = videoKeyHideDock,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        SliderSetting(
            title = dynamicStringResource(R.string.search_widget_padding_home),
            value = paddingHomepage,
            range = -100..600,
            unit = "px",
            enabled = dockCustomizationEnabled,
            onValueChange = {
                paddingHomepage = it
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyPaddingHomepage, it)
                }
            },
            onDefault = {
                paddingHomepage = 200
                scope.launch(Dispatchers.IO) {
                    SettingsCompat.putInt(context, keyPaddingHomepage, 200)
                }
                onSettingChanged()
            },
            onValueChangeFinished = { onSettingChanged() },
            infoText = dynamicStringResource(R.string.search_widget_desc_padding_home),
            videoResName = keyPaddingHomepage,
            onInfoClick = onInfoClick
        )
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        SliderSetting(
            title = dynamicStringResource(R.string.search_widget_padding_dock),
            value = paddingDock,
            range = -120..60,
            unit = "px",
            enabled = dockCustomizationEnabled && !hideDockEnabled,
            onValueChange = {
                paddingDock = it
                scope.launch(Dispatchers.IO) {
                    SettingsCompat.putInt(context, keyPaddingDock, it)
                }
            },
            onDefault = {
                paddingDock = 0
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyPaddingDock, 0)
                }
                onSettingChanged()
            },
            onValueChangeFinished = { onSettingChanged() },
            infoText = dynamicStringResource(R.string.search_widget_desc_padding_dock),
            videoResName = keyPaddingDock,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        SliderSetting(
            title = dynamicStringResource(R.string.search_widget_padding_search),
            value = paddingSearch,
            range = -260..100,
            unit = "px",
            enabled = dockCustomizationEnabled && !hideSearchEnabled,
            onValueChange = {
                paddingSearch = it
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyPaddingSearch, it)
                }
            },
            onDefault = {
                paddingSearch = 0
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyPaddingSearch, 0)
                }
                onSettingChanged()
            },
            onValueChangeFinished = { onSettingChanged() },
            infoText = dynamicStringResource(R.string.search_widget_desc_padding_search),
            videoResName = keyPaddingSearch,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        
        SliderSetting(
            title = dynamicStringResource(R.string.search_widget_padding_dots),
            value = paddingDots,
            range = -300..900,
            unit = "px",
            enabled = dockCustomizationEnabled,
            onValueChange = { 
                paddingDots = it
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyPaddingDots, it)
                }
            },
            onDefault = {
                paddingDots = -50
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyPaddingDots, -50)
                }
                onSettingChanged()
            },
            onValueChangeFinished = { onSettingChanged() },
            infoText = dynamicStringResource(R.string.search_widget_desc_padding_dots),
            videoResName = keyPaddingDots,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SliderSetting(
            title = dynamicStringResource(R.string.search_widget_padding_dots_x),
            value = paddingDotsX,
            range = -300..300,
            unit = "px",
            enabled = dockCustomizationEnabled,
            onValueChange = { 
                paddingDotsX = it
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyPaddingDotsX, it)
                }
            },
            onDefault = {
                paddingDotsX = 0
                scope.launch(Dispatchers.IO) { 
                    SettingsCompat.putInt(context, keyPaddingDotsX, 0)
                }
                onSettingChanged()
            },
            onValueChangeFinished = { onSettingChanged() },
            infoText = dynamicStringResource(R.string.search_widget_desc_padding_dots_x),
            videoResName = keyPaddingDotsX,
            onInfoClick = onInfoClick
        )
    }
}