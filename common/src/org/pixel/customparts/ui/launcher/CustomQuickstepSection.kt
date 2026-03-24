package org.pixel.customparts.ui.launcher

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import org.pixel.customparts.R
import org.pixel.customparts.ui.ExpandableWarningCard
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.utils.dynamicStringResource




@Composable
fun CustomQuickstepSection(
    context: Context,
    scope: CoroutineScope,
    onShowBottomRestartChange: (Boolean) -> Unit,
    onInfoClick: ((String, String, String?) -> Unit)? = null
) {
    var isEnabled by remember { mutableStateOf(isEnabled(context, KEY_ENABLE)) }
    var selectedStyle by remember { mutableIntStateOf(getInt(context, KEY_STYLE, STYLE_STOCK)) }

    Column {
        
        ExpandableWarningCard(
            title = dynamicStringResource(R.string.quickstep_warning_title),
            text = dynamicStringResource(R.string.quickstep_warning_text),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        
        GenericSwitchRow(
            title = dynamicStringResource(R.string.quickstep_engine_title),
            checked = isEnabled,
            onCheckedChange = { 
                isEnabled = it
                setEnabled(context, KEY_ENABLE, it)
                onShowBottomRestartChange(true)
            },
            summary = dynamicStringResource(R.string.quickstep_engine_summary),
            infoText = dynamicStringResource(R.string.quickstep_engine_info),
            onInfoClick = onInfoClick
        )

        
        AnimatedVisibility(
            visible = isEnabled,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = dynamicStringResource(R.string.quickstep_style_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                StyleOption(
                    title = dynamicStringResource(R.string.quickstep_style_stock),
                    description = dynamicStringResource(R.string.quickstep_style_stock_desc),
                    icon = Icons.Rounded.Dashboard,
                    isSelected = selectedStyle == STYLE_STOCK,
                    onClick = {
                        selectedStyle = STYLE_STOCK
                        setInt(context, KEY_STYLE, STYLE_STOCK)
                        onShowBottomRestartChange(true)
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                StyleOption(
                    title = dynamicStringResource(R.string.quickstep_style_ios),
                    description = dynamicStringResource(R.string.quickstep_style_ios_desc),
                    icon = Icons.Rounded.PhoneAndroid,
                    isSelected = selectedStyle == STYLE_IOS,
                    onClick = {
                        selectedStyle = STYLE_IOS
                        setInt(context, KEY_STYLE, STYLE_IOS)
                        onShowBottomRestartChange(true)
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                StyleOption(
                    title = dynamicStringResource(R.string.quickstep_style_oneplus),
                    description = dynamicStringResource(R.string.quickstep_style_oneplus_desc),
                    icon = Icons.Rounded.ViewCarousel,
                    isSelected = selectedStyle == STYLE_ONEPLUS,
                    onClick = {
                        selectedStyle = STYLE_ONEPLUS
                        setInt(context, KEY_STYLE, STYLE_ONEPLUS)
                        onShowBottomRestartChange(true)
                    }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                StyleOption(
                    title = dynamicStringResource(R.string.quickstep_style_minimal),
                    description = dynamicStringResource(R.string.quickstep_style_minimal_desc),
                    icon = Icons.Rounded.Widgets,
                    isSelected = selectedStyle == STYLE_MINIMAL,
                    onClick = {
                        selectedStyle = STYLE_MINIMAL
                        setInt(context, KEY_STYLE, STYLE_MINIMAL)
                        onShowBottomRestartChange(true)
                    }
                )
            }
        }
        
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
    }
}

@Composable
private fun StyleOption(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = dynamicStringResource(R.string.content_desc_selected),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                RadioButton(
                    selected = false,
                    onClick = onClick
                )
            }
        }
    }
}


private const val KEY_ENABLE = "launcher_custom_quickstep_enable"
private const val KEY_STYLE = "launcher_custom_quickstep_style"

private const val STYLE_STOCK = 0
private const val STYLE_IOS = 1
private const val STYLE_ONEPLUS = 2
private const val STYLE_MINIMAL = 3


private fun isEnabled(context: Context, key: String): Boolean {
    return Settings.Global.getInt(context.contentResolver, key, 0) == 1
}

private fun setEnabled(context: Context, key: String, enabled: Boolean) {
    Settings.Global.putInt(context.contentResolver, key, if (enabled) 1 else 0)
}

private fun getInt(context: Context, key: String, default: Int): Int {
    return Settings.Global.getInt(context.contentResolver, key, default)
}

private fun setInt(context: Context, key: String, value: Int) {
    Settings.Global.putInt(context.contentResolver, key, value)
}
