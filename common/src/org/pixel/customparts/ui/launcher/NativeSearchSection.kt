package org.pixel.customparts.ui.launcher

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.customparts.R
import org.pixel.customparts.activities.LauncherManager
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.WeakDivider
import org.pixel.customparts.utils.dynamicStringResource
import org.pixel.customparts.utils.SettingsCompat

@Composable
fun NativeSearchSection(
    context: Context,
    scope: CoroutineScope,
    nativeSearchEnabled: Boolean,
    onNativeSearchChanged: (Boolean) -> Unit,
    onInfoClick: (String, String, String?) -> Unit,
    onSettingChanged: () -> Unit
) {
    var feedDisabled by remember { 
        mutableStateOf(SettingsCompat.getInt(context, LauncherManager.KEY_DISABLE_GOOGLE_FEED, 0) == 1) 
    }
    var topWidgetDisabled by remember { 
        mutableStateOf(SettingsCompat.getInt(context, LauncherManager.KEY_DISABLE_TOP_WIDGET, 0) == 1) 
    }
    SettingsGroupCard(title = dynamicStringResource(R.string.misc_group_title)) {
        GenericSwitchRow(
            title = dynamicStringResource(R.string.launcher_search_title),
            checked = nativeSearchEnabled,
            onCheckedChange = onNativeSearchChanged,
            summary = null,
            infoText = dynamicStringResource(R.string.launcher_search_desc),
            videoResName = "search_fix",
            onInfoClick = onInfoClick
        )

        WeakDivider()
        GenericSwitchRow(
            title = dynamicStringResource(R.string.launcher_feed_title),
            checked = feedDisabled,
            onCheckedChange = { checked ->
                feedDisabled = checked
                scope.launch(Dispatchers.IO) {
                    SettingsCompat.putInt(
                        context, 
                        LauncherManager.KEY_DISABLE_GOOGLE_FEED, 
                        if (checked) 1 else 0
                    )
                }
                onSettingChanged()
            },
            summary = null,
            infoText = dynamicStringResource(R.string.launcher_feed_desc),
            videoResName = null,
            onInfoClick = onInfoClick
        )

        WeakDivider()
        GenericSwitchRow(
            title = dynamicStringResource(R.string.launcher_top_widget_title),
            checked = topWidgetDisabled,
            onCheckedChange = { checked ->
                topWidgetDisabled = checked
                scope.launch(Dispatchers.IO) {
                    SettingsCompat.putInt(
                        context, 
                        LauncherManager.KEY_DISABLE_TOP_WIDGET, 
                        if (checked) 1 else 0
                    )
                }
                onSettingChanged()
            },
            summary = null,
            infoText = dynamicStringResource(R.string.launcher_top_widget_desc),
            videoResName = null,
            onInfoClick = onInfoClick
        )
    }
}