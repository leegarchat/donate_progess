package org.pixel.customparts.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider
import org.pixel.customparts.AppConfig
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.GenericSwitchRow
import org.pixel.customparts.ui.InfoDialog
import org.pixel.customparts.ui.ModuleStatus
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.SliderSetting
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.ui.launcher.Dt2sUiSection
import org.pixel.customparts.utils.dynamicStringResource

class DoubleTapActivity : ComponentActivity() {
	@androidx.compose.material3.ExperimentalMaterial3Api
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			val darkTheme = isSystemInDarkTheme()
			val context = androidx.compose.ui.platform.LocalContext.current
			val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

			MaterialTheme(colorScheme = colorScheme) {
				Surface(
					modifier = Modifier.fillMaxSize(),
					color = MaterialTheme.colorScheme.background
				) {
					DoubleTapScreen(onBack = { finish() })
				}
			}
		}
	}
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun DoubleTapScreen(onBack: () -> Unit) {
	val context = androidx.compose.ui.platform.LocalContext.current
	val scope = rememberCoroutineScope()
	val blurState = rememberGraphicsLayerRecordingState()
	val lazyListState = rememberLazyListState()
	val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

	var infoDialogTitle by remember { mutableStateOf<String?>(null) }
	var infoDialogText by remember { mutableStateOf<String?>(null) }
	var showXposedInactiveDialog by remember { mutableStateOf(false) }
	var infoDialogVideo by remember { mutableStateOf<String?>(null) }
	var dt2wEnabled by remember { mutableStateOf(DoubleTapManager.isDt2wEnabled(context)) }
	var dt2wTimeout by remember { mutableIntStateOf(DoubleTapManager.getDt2wTimeout(context)) }

	if (showXposedInactiveDialog) {
		AlertDialog(
			onDismissRequest = { showXposedInactiveDialog = false },
			title = { Text(dynamicStringResource(R.string.os_dialog_xposed_title)) },
			text = { Text(dynamicStringResource(R.string.os_dialog_xposed_msg)) },
			confirmButton = {
				TextButton(onClick = { showXposedInactiveDialog = false }) {
					Text(dynamicStringResource(R.string.btn_ok))
				}
			}
		)
	}

	Scaffold(
		containerColor = MaterialTheme.colorScheme.surfaceContainer,
		floatingActionButton = { RebootBubble() },
		topBar = {
			TopAppBar(
				title = {
					Text(dynamicStringResource(R.string.dt_title_activity), fontWeight = FontWeight.Bold)
				},
				navigationIcon = {
					IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
				},
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = Color.Transparent,
					scrolledContainerColor = Color.Transparent
				)
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
								showXposedInactiveDialog = true
								dt2wEnabled = false
							} else {
								dt2wEnabled = checked
								scope.launch { DoubleTapManager.setDt2wEnabled(context, checked) }
							}
						},
						videoResName = "dt2w_hook",
						infoText = dt2wDesc,
						onInfoClick = { t, s, v ->
							infoDialogTitle = t
							infoDialogText = s
							infoDialogVideo = v
						}
					)

					HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

					SliderSetting(
						title = dynamicStringResource(R.string.dt2w_timeout_title),
						value = dt2wTimeout,
						range = 300..1000,
						unit = "ms",
						enabled = dt2wEnabled,
						onValueChange = { dt2wTimeout = it; scope.launch { DoubleTapManager.setDt2wTimeout(context, it) } },
						onDefault = { dt2wTimeout = 400; scope.launch { DoubleTapManager.setDt2wTimeout(context, 400) } },
						infoText = dynamicStringResource(R.string.dt2w_timeout_desc),
						onInfoClick = { t, s, v ->
							infoDialogTitle = t
							infoDialogText = s
							infoDialogVideo = v
						}
					)
				}
			}

			item {
				Dt2sUiSection(
					context = context,
					scope = scope,
					showXposedDialog = { showXposedInactiveDialog = true },
					onInfoClick = { t, s, v ->
						infoDialogTitle = t
						infoDialogText = s
						infoDialogVideo = v
					}
				)
			}
		}

			TopBarBlurOverlay(
				modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
				topBarHeight = innerPadding.calculateTopPadding(),
				blurState = blurState,
				isScrolled = isScrolled
			)
		}
	}

	if (infoDialogTitle != null && infoDialogText != null) {
		InfoDialog(
			title = infoDialogTitle!!,
			text = infoDialogText!!,
			videoResName = infoDialogVideo,
			onDismiss = {
				infoDialogTitle = null
				infoDialogText = null
				infoDialogVideo = null
			}
		)
	}
}