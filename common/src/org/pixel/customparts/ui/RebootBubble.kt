package org.pixel.customparts.ui

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.utils.dynamicStringResource
import java.io.DataOutputStream

@Composable
fun RebootBubble(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<RebootAction?>(null) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "fab_rotation"
    )
    val density = LocalDensity.current
    val fabSize = 56.dp
    val fabPx = with(density) { fabSize.roundToPx() }

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        
        AnimatedVisibility(
            visible = expanded,
            enter = expandIn(
                expandFrom = Alignment.BottomEnd,
                initialSize = { IntSize(fabPx, fabPx) },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(120)),
            exit = shrinkOut(
                shrinkTowards = Alignment.BottomEnd,
                targetSize = { IntSize(fabPx, fabPx) },
                animationSpec = tween(180, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(90))
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .padding(bottom = 64.dp)
                    .width(IntrinsicSize.Max)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    AnimatedRebootMenuItem(visible = expanded, delayMs = 30) {
                        RebootMenuItem(
                            icon = Icons.Rounded.Home,
                            label = dynamicStringResource(R.string.reboot_launcher),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {
                                expanded = false
                                scope.launch(Dispatchers.IO) {
                                    performRebootLauncher(context)
                                }
                            }
                        )
                    }
                    AnimatedRebootMenuItem(visible = expanded, delayMs = 80) {
                        RebootMenuItem(
                            icon = Icons.Rounded.SettingsApplications,
                            label = dynamicStringResource(R.string.reboot_systemui),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                expanded = false
                                confirmAction = RebootAction.SYSTEMUI
                            }
                        )
                    }
                    AnimatedRebootMenuItem(visible = expanded, delayMs = 130) {
                        RebootMenuItem(
                            icon = Icons.Rounded.RestartAlt,
                            label = dynamicStringResource(R.string.reboot_system),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            onClick = {
                                expanded = false
                                confirmAction = RebootAction.SYSTEM
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = Icons.Rounded.Build,
                contentDescription = "Reboot menu",
                modifier = Modifier.rotate(rotation)
            )
        }
    }

    if (confirmAction != null) {
        val action = confirmAction!!
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            icon = {
                Icon(
                    imageVector = if (action == RebootAction.SYSTEM) Icons.Rounded.RestartAlt else Icons.Rounded.SettingsApplications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    dynamicStringResource(
                        if (action == RebootAction.SYSTEM) R.string.reboot_confirm_system_title
                        else R.string.reboot_confirm_sysui_title
                    )
                )
            },
            text = {
                Text(
                    dynamicStringResource(
                        if (action == RebootAction.SYSTEM) R.string.reboot_confirm_system_msg
                        else R.string.reboot_confirm_sysui_msg
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            when (action) {
                                RebootAction.SYSTEM -> performRebootSystem(context)
                                RebootAction.SYSTEMUI -> performRebootSystemUI(context)
                            }
                        }
                        confirmAction = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(dynamicStringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text(dynamicStringResource(R.string.btn_cancel))
                }
            }
        )
    }
}


private fun performRebootLauncher(context: Context) {
    if (AppConfig.IS_XPOSED) {
        runRootCommand("am force-stop com.google.android.apps.nexuslauncher")
    } else {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.forceStopPackage("com.google.android.apps.nexuslauncher")
            am.forceStopPackage("com.android.launcher3")
            am.forceStopPackage("com.google.android.apps.pixel.launcher")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun performRebootSystemUI(context: Context) {
    if (AppConfig.IS_XPOSED) {
        runRootCommand("killall com.android.systemui")
    } else {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.forceStopPackage("com.android.systemui")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

private fun performRebootSystem(context: Context) {
    if (AppConfig.IS_XPOSED) {
        runRootCommand("svc power reboot")
    } else {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.reboot(null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
private fun AnimatedRebootMenuItem(
    visible: Boolean,
    delayMs: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(160, delayMs)) +
            slideInVertically(animationSpec = tween(200, delayMs)) { height -> -height / 2 },
        exit = fadeOut(animationSpec = tween(120)) +
            slideOutVertically(animationSpec = tween(120)) { height -> height / 2 }
    ) {
        content()
    }
}

@Composable
private fun RebootMenuItem(
    icon: ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

private enum class RebootAction { SYSTEM, SYSTEMUI }

private fun runRootCommand(command: String) {
    try {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("$command\n")
        os.writeBytes("exit\n")
        os.flush()
        process.waitFor()
        os.close()
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}