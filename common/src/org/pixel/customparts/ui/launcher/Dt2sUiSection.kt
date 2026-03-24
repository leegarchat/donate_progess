package org.pixel.customparts.ui.launcher

import android.content.Context
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.material3.HorizontalDivider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.activities.DoubleTapManager
import org.pixel.customparts.ui.*
import org.pixel.customparts.utils.dynamicStringResource

@Composable
fun Dt2sUiSection(
    context: Context,
    scope: CoroutineScope,
    showXposedDialog: () -> Unit,
    onInfoClick: (String, String, String?) -> Unit,
    onSettingChanged: () -> Unit = {}
) {
    var dt2sEnabled by remember { mutableStateOf(DoubleTapManager.isDt2sEnabled(context)) }
    var dt2sTimeout by remember { mutableIntStateOf(DoubleTapManager.getDt2sTimeout(context)) }
    var dt2sSlop by remember { mutableIntStateOf(DoubleTapManager.getDt2sSlop(context)) }
    
    val systemSlop = remember { android.view.ViewConfiguration.get(context).scaledDoubleTapSlop }

    LaunchedEffect(Unit) {
        if (dt2sSlop == 0) dt2sSlop = systemSlop
    }

    SettingsGroupCard(title = dynamicStringResource(R.string.dt_sec_sleep)) {
        val dt2sDesc = if (AppConfig.IS_XPOSED) dynamicStringResource(R.string.dt2s_desc_xposed) else dynamicStringResource(R.string.dt2s_desc_native)

        GenericSwitchRow(
            title = dynamicStringResource(R.string.dt2s_title),
            checked = dt2sEnabled,
            onCheckedChange = { checked ->
                if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive()) {
                    showXposedDialog()
                    dt2sEnabled = false 
                } else {
                    dt2sEnabled = checked
                    scope.launch { DoubleTapManager.setDt2sEnabled(context, checked) }
                    onSettingChanged()
                }
            },
            videoResName = "dt2s_hook",
            infoText = dt2sDesc,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SliderSetting(
            title = dynamicStringResource(R.string.dt2s_timeout_title),
            value = dt2sTimeout,
            range = 250..1000,
            unit = "ms",
            enabled = dt2sEnabled,
            onValueChange = { dt2sTimeout = it; scope.launch { DoubleTapManager.setDt2sTimeout(context, it) } },
            onDefault = {
                dt2sTimeout = 400
                scope.launch { DoubleTapManager.setDt2sTimeout(context, 400) }
                onSettingChanged()
            },
            onValueChangeFinished = { onSettingChanged() },
            infoText = dynamicStringResource(R.string.dt2w_timeout_desc),
            onInfoClick = onInfoClick
        )
    }
}

@Composable
fun Dt2wUiSection(
    context: Context,
    scope: CoroutineScope,
    showXposedDialog: () -> Unit,
    onInfoClick: (String, String, String?) -> Unit,
    onSettingChanged: () -> Unit = {}
) {
    var dt2wEnabled by remember { mutableStateOf(DoubleTapManager.isDt2wEnabled(context)) }
    var dt2wTimeout by remember { mutableIntStateOf(DoubleTapManager.getDt2wTimeout(context)) }

    SettingsGroupCard(title = dynamicStringResource(R.string.dt_sec_wake)) {
        val dt2wDesc = if (AppConfig.IS_XPOSED) {
            dynamicStringResource(R.string.dt2w_desc_xposed)
        } else {
            dynamicStringResource(R.string.dt2w_desc_native)
        }

        GenericSwitchRow(
            title = dynamicStringResource(R.string.dt2w_title),
            checked = dt2wEnabled,
            onCheckedChange = { checked ->
                if (checked && AppConfig.IS_XPOSED && !ModuleStatus.isModuleActive()) {
                    showXposedDialog()
                    dt2wEnabled = false
                } else {
                    dt2wEnabled = checked
                    scope.launch { DoubleTapManager.setDt2wEnabled(context, checked) }
                    onSettingChanged()
                }
            },
            videoResName = "dt2w_hook",
            infoText = dt2wDesc,
            onInfoClick = onInfoClick
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        SliderSetting(
            title = dynamicStringResource(R.string.dt2w_timeout_title),
            value = dt2wTimeout,
            range = 300..1000,
            unit = "ms",
            enabled = dt2wEnabled,
            onValueChange = { dt2wTimeout = it; scope.launch { DoubleTapManager.setDt2wTimeout(context, it) } },
            onDefault = {
                dt2wTimeout = 400
                scope.launch { DoubleTapManager.setDt2wTimeout(context, 400) }
                onSettingChanged()
            },
            onValueChangeFinished = { onSettingChanged() },
            infoText = dynamicStringResource(R.string.dt2w_timeout_desc),
            onInfoClick = onInfoClick
        )
    }
}
