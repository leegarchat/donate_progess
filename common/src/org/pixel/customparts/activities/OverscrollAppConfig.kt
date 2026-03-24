package org.pixel.customparts.activities

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.draw.alpha
import java.util.Locale
import org.pixel.customparts.R
import androidx.compose.ui.res.stringResource
import org.pixel.customparts.utils.dynamicStringResource

data class AppItem(val name: String, val pkg: String, val icon: Drawable)

@Composable
fun AppConfigCard(
    context: Context,
    item: AppConfigItem,
    onConfigChange: (AppConfigItem) -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true 
) {
    var appName by remember { mutableStateOf(item.pkg) }
    var appIcon by remember { mutableStateOf<Drawable?>(null) }
    
    var sliderValue by remember(item.scale) { mutableFloatStateOf(item.scale) }
    
    val contentAlpha = if (enabled) 1f else 0.4f

    LaunchedEffect(item.pkg) {
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(item.pkg, 0)
                appName = pm.getApplicationLabel(info).toString()
                appIcon = pm.getApplicationIcon(info)
            } catch (e: Exception) {  }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .alpha(contentAlpha) 
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon!!.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(modifier = Modifier.size(40.dp).background(Color.Gray, CircleShape))
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dynamicStringResource(R.string.os_app_filter), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = item.filter,
                    enabled = enabled, 
                    onCheckedChange = { 
                        onConfigChange(item.copy(filter = it)) 
                    },
                    modifier = Modifier.scale(0.8f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dynamicStringResource(R.string.os_app_ignore), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = item.ignore,
                    enabled = enabled,
                    onCheckedChange = { 
                        onConfigChange(item.copy(ignore = it)) 
                    },
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dynamicStringResource(R.string.os_app_scale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(
                String.format(Locale.US, "%.1f", sliderValue), 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(40.dp)
            )
            
            Slider(
                value = sliderValue,
                enabled = enabled,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { 
                    onConfigChange(item.copy(scale = sliderValue)) 
                },
                valueRange = 0f..6f,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun AppSelectorDialog(
    context: Context,
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var appsList by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val items = installed.map { 
                AppItem(
                    name = it.loadLabel(pm).toString(),
                    pkg = it.packageName,
                    icon = it.loadIcon(pm)
                )
            }.sortedBy { it.name.lowercase() }
            
            withContext(Dispatchers.Main) {
                appsList = items
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, appsList) {
        if (searchQuery.isBlank()) appsList
        else appsList.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || 
            it.pkg.contains(searchQuery, ignoreCase = true) 
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(dynamicStringResource(R.string.os_app_select_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(dynamicStringResource(R.string.os_app_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
                Spacer(Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAppSelected(app.pkg) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.pkg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.btn_cancel)) }
                }
            }
        }
    }
}