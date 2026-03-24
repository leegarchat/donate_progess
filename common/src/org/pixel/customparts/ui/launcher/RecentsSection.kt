package org.pixel.customparts.ui.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.pixel.customparts.R
import org.pixel.customparts.activities.RecentsMenuActivity
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.utils.dynamicStringResource

@Composable
fun RecentsSection(
    context: Context,
    scope: CoroutineScope,
    onShowBottomRestartChange: (Boolean) -> Unit,
    onInfoClick: ((String, String, String?) -> Unit)? = null
) {
    SettingsGroupCard(title = dynamicStringResource(R.string.launcher_group_recents_customization)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(Intent(context, RecentsMenuActivity::class.java))
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dynamicStringResource(R.string.launcher_recents_menu_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dynamicStringResource(R.string.launcher_recents_menu_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}
