package org.pixel.customparts.ui

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.utils.restartSystemUI
import org.pixel.customparts.utils.runRootCommand
import org.pixel.customparts.utils.dynamicStringResource

val REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING = 96.dp

private const val MORPH_DURATION = 300

@Composable
fun RebootBubble(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<RebootAction?>(null) }

    // Shape morph: circle (28dp on 56dp = circle) → rounded card (22dp)
    val cornerRadius by animateDpAsState(
        targetValue = if (expanded) 22.dp else 28.dp,
        animationSpec = tween(MORPH_DURATION, easing = FastOutSlowInEasing),
        label = "corner"
    )
    // Color morph: primary (FAB) → surfaceContainerHigh (card)
    val containerColor by animateColorAsState(
        targetValue = if (expanded)
            MaterialTheme.colorScheme.surfaceContainerHigh
        else
            MaterialTheme.colorScheme.primary,
        animationSpec = tween(MORPH_DURATION),
        label = "color"
    )
    // Elevation morph
    val elevation by animateDpAsState(
        targetValue = if (expanded) 16.dp else 6.dp,
        animationSpec = tween(MORPH_DURATION),
        label = "elevation"
    )

    // Dismiss expanded menu on system back
    BackHandler(enabled = expanded) { expanded = false }

    // Custom layout: always report 56dp to the parent Scaffold so the FAB
    // position stays fixed at bottom-end.  The expanded Surface overflows
    // upward / leftward from the anchored bottom-right corner.
    Box(
        modifier = modifier.layout { measurable, constraints ->
            // Let child measure with full screen so the scrim can fill it
            val placeable = measurable.measure(
                constraints.copy(minWidth = 0, minHeight = 0)
            )
            val fabSizePx = 56.dp.roundToPx()
            layout(fabSizePx, fabSizePx) {
                placeable.place(
                    x = fabSizePx - placeable.width,
                    y = fabSizePx - placeable.height
                )
            }
        },
        contentAlignment = Alignment.BottomEnd
    ) {
        // Dismiss-on-outside-tap scrim — lives inside the custom layout Box,
        // placed before the Surface so it sits behind it in z-order.
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expanded = false }
                    )
            )
        }

        Surface(
            onClick = { if (!expanded) expanded = true },
            shape = RoundedCornerShape(cornerRadius),
            color = containerColor,
            shadowElevation = elevation,
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    val contentIn = fadeIn(
                        tween(
                            durationMillis = if (targetState) MORPH_DURATION else 180,
                            delayMillis = if (targetState) 120 else 60
                        )
                    )
                    val contentOut = fadeOut(
                        tween(durationMillis = if (targetState) 120 else 100)
                    )
                    val sizeAnim = SizeTransform(clip = true) { _: IntSize, _: IntSize ->
                        tween(MORPH_DURATION, easing = FastOutSlowInEasing)
                    }
                    contentIn togetherWith contentOut using sizeAnim
                },
                contentAlignment = Alignment.BottomEnd,
                label = "bubble_morph"
            ) { isExpanded ->
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .width(IntrinsicSize.Max)
                    ) {
                        StaggeredMenuItem(
                            icon = Icons.Rounded.Home,
                            label = dynamicStringResource(R.string.reboot_launcher),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            delayMs = 40,
                            onClick = {
                                expanded = false
                                scope.launch(Dispatchers.IO) { performRebootLauncher(context) }
                            }
                        )
                        StaggeredMenuItem(
                            icon = Icons.Rounded.SettingsApplications,
                            label = dynamicStringResource(R.string.reboot_systemui),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            delayMs = 90,
                            onClick = {
                                expanded = false
                                confirmAction = RebootAction.SYSTEMUI
                            }
                        )
                        StaggeredMenuItem(
                            icon = Icons.Rounded.RestartAlt,
                            label = dynamicStringResource(R.string.reboot_system),
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            delayMs = 140,
                            onClick = {
                                expanded = false
                                confirmAction = RebootAction.SYSTEM
                            }
                        )
                        // Close button — left-aligned, bold icon + bold text
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 0.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { expanded = false }
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = dynamicStringResource(R.string.btn_close),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Collapsed: 56×56 circle with icon
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = "Reboot menu",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }

    // --- confirm dialog ---
    if (confirmAction != null) {
        val action = confirmAction!!
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            icon = {
                Icon(
                    imageVector = if (action == RebootAction.SYSTEM) Icons.Rounded.RestartAlt
                                  else Icons.Rounded.SettingsApplications,
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
                                RebootAction.SYSTEMUI -> restartSystemUI(context)
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


@Composable
private fun StaggeredMenuItem(
    icon: ImageVector,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    delayMs: Int,
    onClick: () -> Unit
) {
    val offsetY = remember { Animatable(28f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        launch { offsetY.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 320f)) }
        launch { alpha.animateTo(1f, tween(260)) }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = offsetY.value
            }
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

private enum class RebootAction { SYSTEM, SYSTEMUI }