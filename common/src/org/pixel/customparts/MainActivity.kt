package org.pixel.customparts

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.activities.*
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.utils.RootUtils
import org.pixel.customparts.utils.RemoteStringsManager
import org.pixel.customparts.utils.dynamicStringResource

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            
            LaunchedEffect(Unit) {
                try {
                    RemoteStringsManager.initialize(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            var rootState by remember { mutableStateOf(if (AppConfig.NEEDS_ROOT_ACCESS) 0 else 1) }

            LaunchedEffect(Unit) {
                if (AppConfig.NEEDS_ROOT_ACCESS) {
                    withContext(Dispatchers.IO) {
                        try {
                            if (RootUtils.hasRootAccess()) {
                                RootUtils.grantPermissions(context)
                                rootState = 1
                            } else {
                                rootState = 2
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            rootState = 2
                        }
                    }
                }
            }

            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (rootState) {
                        0 -> LoadingScreen() 
                        1 -> MainDashboard()
                        2 -> NoRootDialog { finishAffinity() }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun NoRootDialog(onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
        title = { Text(text = dynamicStringResource(R.string.main_root_title)) },
        text = { Text(dynamicStringResource(R.string.main_root_desc)) },
        confirmButton = {
            TextButton(onClick = onExit) { Text(dynamicStringResource(R.string.btn_exit)) }
        },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    val showTestThings = remember {
        try {
            Settings.Global.getInt(context.contentResolver, "pixelparts_test_things", 0) == 1
        } catch (_: Throwable) { false }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.navigationBars, // Only respect nav bars for Scaffold layout, let content handle status bar
        floatingActionButton = { RebootBubble() },
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text(dynamicStringResource(R.string.main_title), fontWeight = FontWeight.Bold)

                        Text(
                            dynamicStringResource(R.string.main_desc),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.btn_exit))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val success = RemoteStringsManager.forceRefresh(context)

                            val message = if (success) RemoteStringsManager.getString(context, R.string.refresh_strings) else RemoteStringsManager.getString(context, R.string.error_network)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

                            if (success) {
                                activity?.recreate()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = dynamicStringResource(R.string.menu_refresh)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .recordLayer(blurState)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentPadding = PaddingValues(
                    start = 16.dp, 
                    top = innerPadding.calculateTopPadding() + 16.dp, 
                    end = 16.dp, 
                    bottom = innerPadding.calculateBottomPadding() + REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.donate_title)) {
                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.donate_title),
                            subtitle = dynamicStringResource(R.string.donate_desc_short),
                            icon = Icons.Rounded.Favorite,
                            iconContainerColor = MaterialTheme.colorScheme.primary,
                            iconContentColor = MaterialTheme.colorScheme.onPrimary,
                            onClick = { context.startActivity(Intent(context, DonateActivity::class.java)) }
                        )
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.main_header_gesture)) {
                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.dt_title_activity),
                            subtitle = dynamicStringResource(R.string.dt_desc_activity),
                            icon = Icons.Rounded.TouchApp,
                            iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { context.startActivity(Intent(context, DoubleTapActivity::class.java)) }
                        )

                        HorizontalDivider()

                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.os_title_activity),
                            subtitle = dynamicStringResource(R.string.os_desc_activity),
                            icon = Icons.Rounded.Animation,
                            iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { context.startActivity(Intent(context, OverscrollActivity::class.java)) }
                        )
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.main_header_system)) {
                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.sysui_settings_title),
                            subtitle = "Configure SystemUI components",
                            icon = Icons.Rounded.SettingsSystemDaydream,
                            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { context.startActivity(Intent(context, SystemUISettingsActivity::class.java)) }
                        )

                        HorizontalDivider()

                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.launcher_title_activity),
                            subtitle = dynamicStringResource(R.string.launcher_desc_activity),
                            icon = Icons.Rounded.Home,
                            iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            onClick = { context.startActivity(Intent(context, LauncherActivity::class.java)) }
                        )

                        if (AppConfig.ENABLE_THERMALS) {
                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = dynamicStringResource(R.string.thermal_title_activity),
                                subtitle = dynamicStringResource(R.string.thermal_desc_activity),
                                icon = Icons.Rounded.Thermostat,
                                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                                iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = { context.startActivity(Intent(context, ThermalActivity::class.java)) }
                            )
                        }

                        if (!AppConfig.IS_XPOSED || showTestThings) {
                            HorizontalDivider()

                            MainMenuNavigationRow(
                                title = dynamicStringResource(R.string.addon_title),
                                subtitle = dynamicStringResource(R.string.addon_desc),
                                icon = Icons.Rounded.Extension,
                                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { context.startActivity(Intent(context, AddonManagerActivity::class.java)) }
                            )
                        }
                    }
                }

                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.main_header_network)) {
                        MainMenuNavigationRow(
                            title = dynamicStringResource(R.string.ims_title_activity),
                            subtitle = dynamicStringResource(R.string.ims_desc_activity),
                            icon = Icons.Rounded.NetworkCell,
                            iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { context.startActivity(Intent(context, ImsActivity::class.java)) }
                        )
                    }
                }

                // Test Things — visible only when pixelparts_test_things == 1
                if (showTestThings) {
                    item {
                        SettingsGroupCard(title = dynamicStringResource(R.string.test_things_title)) {
                            MainMenuNavigationRow(
                                title = dynamicStringResource(R.string.test_things_title),
                                subtitle = dynamicStringResource(R.string.test_things_desc),
                                icon = Icons.Rounded.Science,
                                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                                iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = { context.startActivity(Intent(context, TestActivity::class.java)) }
                            )
                        }
                    }
                }
            }

            // Fixed at collapsed top-bar height (64dp + status bar).
            // When scrolled the large bar is already collapsed to this size;
            // when at top the overlay is invisible (isScrolled = false, alpha = 0).
            TopBarBlurOverlay(
                modifier = Modifier.fillMaxWidth(),
                topBarHeight = 64.dp + WindowInsets.statusBars
                    .asPaddingValues().calculateTopPadding(),
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }
}

@Composable
fun MainMenuNavigationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconContainerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconContentColor
            )
        }

        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}