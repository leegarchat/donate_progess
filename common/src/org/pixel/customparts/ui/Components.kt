package org.pixel.customparts.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import org.pixel.customparts.R
import org.pixel.customparts.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.net.URL
import androidx.compose.material.icons.rounded.Refresh
import org.pixel.customparts.utils.dynamicStringResource

@Composable
fun ExpandableWarningCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    dividerAlpha: Float = 0.2f
) {
    var expanded by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "rot",
        animationSpec = tween(300)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = contentColor)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.rotate(rotation), tint = contentColor)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = contentColor.copy(alpha = dividerAlpha), modifier = Modifier.padding(bottom = 12.dp))
                    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = contentColor)
                }
            }
        }
    }
}

@Composable
fun GenericSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    infoText: String? = null,
    videoResName: String? = null,
    enabled: Boolean = true,
    onInfoClick: ((String, String, String?) -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.4f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).alpha(contentAlpha)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (onInfoClick != null && infoText != null) {
            IconButton(
                onClick = { onInfoClick(title, infoText, videoResName) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.alpha(contentAlpha)
        )
    }
}

@Composable
fun SliderSetting(
    title: String,
    value: Int,
    range: IntRange,
    unit: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onDefault: () -> Unit,
    valueText: String? = null,
    infoText: String? = null,
    videoResName: String? = null,
    onInfoClick: ((String, String, String?) -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    showDefaultButton: Boolean = true,
    inputRange: IntRange? = null
) {
    SliderSettingFloat(
        title = title,
        value = value.toFloat(),
        range = range.first.toFloat()..range.last.toFloat(),
        unit = unit,
        enabled = enabled,
        onValueChange = { onValueChange(it.toInt()) },
        onDefault = onDefault,
        valueText = valueText,
        isInteger = true,
        infoText = infoText,
        videoResName = videoResName,
        onInfoClick = onInfoClick,
        onValueChangeFinished = onValueChangeFinished,
        showDefaultButton = showDefaultButton,
        inputRange = inputRange?.let { it.first.toFloat()..it.last.toFloat() }
    )
}

@Composable
fun SliderSettingFloat(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onDefault: () -> Unit,
    valueText: String? = null,
    isInteger: Boolean = false,
    infoText: String? = null,
    videoResName: String? = null,
    onInfoClick: ((String, String, String?) -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    showDefaultButton: Boolean = true,
    inputRange: ClosedFloatingPointRange<Float>? = null
) {
    var showManualInput by remember { mutableStateOf(false) }
    val contentAlpha = if (enabled) 1f else 0.4f

    val formattedValue = valueText ?: if (isInteger) {
        "${value.toInt()} $unit"
    } else {
        String.format("%.2f %s", value, unit)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { showManualInput = true }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().alpha(contentAlpha)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            
            Text(
                text = formattedValue,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }
        
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showDefaultButton) {
                IconButton(
                    onClick = onDefault,
                    enabled = enabled,
                    modifier = Modifier.size(32.dp).alpha(contentAlpha)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Reset to default",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))
            }

            Slider(
                value = value.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                valueRange = range,
                enabled = enabled,
                modifier = Modifier.weight(1f).alpha(contentAlpha),
                onValueChangeFinished = onValueChangeFinished
            )

            if (onInfoClick != null && infoText != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onInfoClick(title, infoText, videoResName) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    if (showManualInput) {
        val dialogRange = inputRange ?: range
        FloatInputDialog(
            title = title,
            initialValue = value,
            rangeStart = dialogRange.start,
            rangeEnd = dialogRange.endInclusive,
            unit = unit,
            isInteger = isInteger,
            onDismiss = { showManualInput = false },
            onConfirm = { 
                onValueChange(it)
                onValueChangeFinished?.invoke()
                showManualInput = false 
            },
            onDefault = {
                onDefault()
                showManualInput = false
            }
        )
    }
}

@Composable
fun RadioSelectionGroup(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
    infoText: String? = null,
    videoResName: String? = null,
    onInfoClick: ((String, String, String?) -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.4f

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).alpha(contentAlpha)
            )
            
            if (onInfoClick != null && infoText != null) {
                IconButton(
                    onClick = { onInfoClick(title, infoText, videoResName) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val targetContainerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            val backgroundColor by animateColorAsState(targetContainerColor, label = "bg")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .clickable(enabled = enabled) { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                AnimatedVisibility(
                    visible = isSelected,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsGroupCard(
    title: String,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).alpha(if (enabled) 1f else 0.5f)
            )
            content()
        }
    }
}

@Composable
fun RestartLauncherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(dynamicStringResource(R.string.btn_restart_launcher))
    }
}

@Composable
fun FloatInputDialog(
    title: String,
    initialValue: Float,
    rangeStart: Float,
    rangeEnd: Float,
    unit: String,
    isInteger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
    onDefault: (() -> Unit)? = null
) {
    val initText = if (isInteger) initialValue.toInt().toString() else initialValue.toString()
    var text by remember { mutableStateOf(initText) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = dynamicStringResource(R.string.common_range_format, rangeStart.toInt(), rangeEnd.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        val num = it.toFloatOrNull()
                        isError = num == null || num < rangeStart || num > rangeEnd
                    },
                    label = { Text(dynamicStringResource(R.string.common_input_label)) },
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    suffix = { Text(unit) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row {
                if (onDefault != null) {
                    TextButton(onClick = onDefault) { Text(dynamicStringResource(R.string.btn_default)) }
                }
                Button(
                    onClick = {
                        val num = text.toFloatOrNull()
                        if (num != null && num >= rangeStart && num <= rangeEnd) {
                            onConfirm(num)
                        } else {
                            isError = true
                        }
                    }
                ) { Text(dynamicStringResource(R.string.btn_apply)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dynamicStringResource(R.string.btn_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun InfoDialog(
    title: String,
    text: String,
    videoResName: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp)
                .verticalScroll(scrollState)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss 
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(enabled = false) {}
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.btn_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    if (videoResName != null) {
                        var videoRatio by remember { mutableFloatStateOf(16f / 9f) }
                        var currentUri by remember { mutableStateOf<Uri?>(null) }
                        var isLoading by remember { mutableStateOf(true) }
                        var isError by remember { mutableStateOf(false) }
                        
                        var reloadKey by remember { mutableIntStateOf(0) }
                        var isNetworkSource by remember { mutableStateOf(false) }

                        LaunchedEffect(videoResName, reloadKey) {
                            val TAG = "CustomPartsVideo"

                            if (videoResName == "test_row") {
                                android.util.Log.d(TAG, "Skipping logic for 'test_row'")
                                isLoading = false
                                return@LaunchedEffect
                            }

                            android.util.Log.d(TAG, ">>> START LaunchedEffect for: $videoResName | ReloadKey: $reloadKey")
                            isLoading = true
                            isError = false
                            
                            val webUrl = "https://raw.githubusercontent.com/leegarchat/PixelExtraParts/main/VideoSample/$videoResName.mp4"
                            
                            
                            val resId = context.resources.getIdentifier(videoResName, "raw", context.packageName)
                            if (resId != 0) {
                                currentUri = Uri.parse("android.resource://${context.packageName}/$resId")
                                isNetworkSource = false
                                isLoading = false
                                return@LaunchedEffect
                            }

                            isNetworkSource = true
                            
                            val targetDir = if (!AppConfig.IS_XPOSED) {
                                val extCache = context.externalCacheDir
                                extCache ?: context.cacheDir
                            } else {
                                context.cacheDir
                            }
                            
                            val cacheFile = File(targetDir, "$videoResName.mp4")

                            try {
                                withContext(Dispatchers.IO) {
                                    val fileExists = cacheFile.exists()
                                    val fileLength = cacheFile.length()

                                    if (!fileExists || fileLength == 0L || reloadKey > 0) {
                                        if (fileExists) cacheFile.delete()

                                        val url = java.net.URL(webUrl)
                                        val connection = url.openConnection() as java.net.HttpURLConnection
                                        connection.connectTimeout = 15000
                                        connection.readTimeout = 15000
                                        connection.instanceFollowRedirects = true
                                        connection.connect()

                                        if (connection.responseCode != 200) {
                                            throw Exception("HTTP Failed: ${connection.responseCode}")
                                        }

                                        connection.inputStream.use { input ->
                                            cacheFile.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        
                                        if (!AppConfig.IS_XPOSED) {
                                            cacheFile.setReadable(true, false)
                                        }
                                    }
                                    
                                    if (cacheFile.exists() && cacheFile.length() > 0) {
                                        currentUri = Uri.fromFile(cacheFile)
                                    } else {
                                        throw Exception("Verification failed")
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isError = true 
                            } finally {
                                isLoading = false
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(videoRatio)
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (isLoading || isError || currentUri == null) Color.Black else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (videoResName == "test_row") {
                                Text(dynamicStringResource(R.string.common_video_placeholder), color = Color.White)
                            } else if (currentUri != null && !isError) {
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                            clipToOutline = true
                                            outlineProvider = object : android.view.ViewOutlineProvider() {
                                                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                                    outline.setRoundRect(0, 0, view.width, view.height, 24.dp.value * ctx.resources.displayMetrics.density)
                                                }
                                            }

                                            setOnPreparedListener { mp ->
                                                mp.isLooping = true
                                                mp.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                                                
                                                if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                                    videoRatio = mp.videoWidth.toFloat() / mp.videoHeight.toFloat()
                                                }
                                                start()
                                            }
                                            setOnErrorListener { _, _, _ ->
                                                isError = true
                                                true
                                            }
                                        }
                                    },
                                    update = { videoView ->
                                        if (currentUri != null && !videoView.isPlaying) {
                                                videoView.setVideoURI(currentUri)
                                        }
                                    },
                                    modifier = Modifier.matchParentSize()
                                )
                            }
                            
                            if (isError) {
                                Text(dynamicStringResource(R.string.status_error), color = Color.Red)
                            }

                            if (isLoading) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }

                            if (isNetworkSource && !isLoading) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                ) {
                                    Surface(
                                        onClick = { reloadKey++ },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                                contentDescription = "Refresh",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Text(
                        text = title, 
                        style = MaterialTheme.typography.titleLarge, 
                        fontSize = 18.sp, 
                        textAlign = TextAlign.Start, 
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = text, 
                        style = MaterialTheme.typography.bodyMedium, 
                        textAlign = TextAlign.Start, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant, 
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun WeakDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = modifier.padding(horizontal = 20.dp)
    )
}

@Composable
fun StrongDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun ExpandableSettingsGroupCard(
    title: String,
    enabled: Boolean = true,
    expanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "arrow_rotation",
        animationSpec = tween(300)
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.6f)
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandChange(!expanded) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
                )

                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation),
                    tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

object ModuleStatus {
    fun isModuleActive(): Boolean {
        try {
            // Check Settings.Global directly via reflection to avoid context issues or hidden structure
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = activityThreadClass.getMethod("currentApplication")
            val context = currentApplicationMethod.invoke(null) as? android.content.Context
            
            if (context != null) {
                val value = android.provider.Settings.Global.getInt(
                    context.contentResolver, 
                    "pixelparts_xposed_to_pine", 
                    0
                )
                if (value == 1) return true
            }
        } catch (e: Exception) {
            // ignore
        }
        return false
    }
}