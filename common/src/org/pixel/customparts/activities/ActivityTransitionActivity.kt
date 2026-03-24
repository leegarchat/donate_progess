package org.pixel.customparts.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.AnticipateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pixel.customparts.R
import org.pixel.customparts.SettingsKeys
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.SettingsGroupCard
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.AnimThemeCompiler
import org.pixel.customparts.utils.RemoteStringsManager
import org.pixel.customparts.utils.dynamicStringResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

// ── Constants ───────────────────────────────────────────────────────

internal const val THEME_PACKAGE_PREFIX = "org.pixel.customparts.anim."

internal const val MODE_DISABLED = 0
internal const val MODE_CUSTOM = -1
internal const val MODE_NO_ANIMATION = -2

// ── Built-in animation catalog ──────────────────────────────────────

internal data class AnimMode(
    val modeId: Int,
    val groupNameRes: Int,
    val dirNameRes: Int? = null,
    val openEnterAnim: Int,
    val openExitAnim: Int,
    val closeEnterAnim: Int,
    val closeExitAnim: Int
)

internal val BUILTIN_MODES: List<AnimMode> by lazy {
    listOf(
        // Slide
        AnimMode(10, R.string.anim_group_slide, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.no_anim, R.anim.no_anim, R.anim.slide_out_right),
        AnimMode(11, R.string.anim_group_slide, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.no_anim, R.anim.no_anim, R.anim.train_exit),
        AnimMode(12, R.string.anim_group_slide, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.no_anim, R.anim.no_anim, R.anim.slide_out_top),
        AnimMode(13, R.string.anim_group_slide, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.no_anim, R.anim.no_anim, R.anim.slide_down_exit),
        // Card Stack
        AnimMode(20, R.string.anim_group_card_stack, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.card_stack_exit, R.anim.card_stack_enter, R.anim.slide_out_right),
        AnimMode(21, R.string.anim_group_card_stack, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.card_stack_exit_left, R.anim.card_stack_enter_left, R.anim.train_exit),
        AnimMode(22, R.string.anim_group_card_stack, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.card_stack_exit_top, R.anim.card_stack_enter_top, R.anim.slide_out_top),
        AnimMode(23, R.string.anim_group_card_stack, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.card_stack_exit_bottom, R.anim.card_stack_enter_bottom, R.anim.slide_down_exit),
        // Train
        AnimMode(30, R.string.anim_group_train, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.train_exit, R.anim.slide_in_left, R.anim.slide_out_right),
        AnimMode(31, R.string.anim_group_train, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.train_exit),
        AnimMode(32, R.string.anim_group_train, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.slide_down_exit, R.anim.slide_up_enter, R.anim.slide_out_top),
        AnimMode(33, R.string.anim_group_train, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.slide_out_top, R.anim.slide_in_top, R.anim.slide_down_exit),
        // iOS Parallax
        AnimMode(40, R.string.anim_group_ios, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.ios_open_exit, R.anim.ios_close_enter, R.anim.slide_out_right),
        AnimMode(41, R.string.anim_group_ios, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.ios_open_exit_left, R.anim.ios_close_enter_left, R.anim.train_exit),
        AnimMode(42, R.string.anim_group_ios, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.ios_open_exit_top, R.anim.ios_close_enter_top, R.anim.slide_out_top),
        AnimMode(43, R.string.anim_group_ios, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.ios_open_exit_bottom, R.anim.ios_close_enter_bottom, R.anim.slide_down_exit),
        // Fade
        AnimMode(50, R.string.anim_group_fade, null,
            R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out),
        // Zoom
        AnimMode(60, R.string.anim_group_zoom, null,
            R.anim.zoom_enter, R.anim.zoom_exit, R.anim.zoom_close_enter, R.anim.zoom_close_exit),
        // Modal
        AnimMode(70, R.string.anim_group_modal, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.slide_up_exit_right, R.anim.slide_up_close_enter_right, R.anim.slide_out_right),
        AnimMode(71, R.string.anim_group_modal, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.slide_up_exit_left, R.anim.slide_up_close_enter_left, R.anim.train_exit),
        AnimMode(72, R.string.anim_group_modal, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.slide_up_exit_top, R.anim.slide_up_close_enter_top, R.anim.slide_out_top),
        AnimMode(73, R.string.anim_group_modal, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.slide_up_exit, R.anim.slide_up_close_enter, R.anim.slide_down_exit),
        // Depth
        AnimMode(80, R.string.anim_group_depth, R.string.anim_dir_right,
            R.anim.slide_in_right, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.slide_out_right),
        AnimMode(81, R.string.anim_group_depth, R.string.anim_dir_left,
            R.anim.slide_in_left, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.train_exit),
        AnimMode(82, R.string.anim_group_depth, R.string.anim_dir_top,
            R.anim.slide_in_top, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.slide_out_top),
        AnimMode(83, R.string.anim_group_depth, R.string.anim_dir_bottom,
            R.anim.slide_up_enter, R.anim.depth_exit, R.anim.depth_close_enter, R.anim.slide_down_exit),
        // Pivot
        AnimMode(90, R.string.anim_group_pivot, R.string.anim_dir_right,
            R.anim.pivot_enter, R.anim.pivot_exit, R.anim.pivot_close_enter, R.anim.pivot_close_exit),
        AnimMode(91, R.string.anim_group_pivot, R.string.anim_dir_left,
            R.anim.pivot_enter_left, R.anim.pivot_exit_left, R.anim.pivot_close_enter_left, R.anim.pivot_close_exit_left),
        AnimMode(92, R.string.anim_group_pivot, R.string.anim_dir_top,
            R.anim.pivot_enter_top, R.anim.pivot_exit_top, R.anim.pivot_close_enter_top, R.anim.pivot_close_exit_top),
        AnimMode(93, R.string.anim_group_pivot, R.string.anim_dir_bottom,
            R.anim.pivot_enter_bottom, R.anim.pivot_exit_bottom, R.anim.pivot_close_enter_bottom, R.anim.pivot_close_exit_bottom)
    )
}

internal fun modeDisplayName(context: Context, modeId: Int): String {
    if (modeId == MODE_DISABLED) return context.getString(R.string.anim_builtin_disabled)
    if (modeId == MODE_CUSTOM) return context.getString(R.string.anim_builtin_custom)
    if (modeId == MODE_NO_ANIMATION) return context.getString(R.string.anim_no_animation_title)
    val mode = BUILTIN_MODES.find { it.modeId == modeId } ?: return "Mode $modeId"
    val group = context.getString(mode.groupNameRes)
    return if (mode.dirNameRes != null) "$group — ${context.getString(mode.dirNameRes)}" else group
}

// ── Data classes ────────────────────────────────────────────────────

internal data class InstalledTheme(
    val packageName: String,
    val styleName: String,
    val isActiveOpen: Boolean,
    val isActiveClose: Boolean
)

// ── Animation Constructor data ──────────────────────────────────────

internal data class AnimParams(
    val translateFromX: Float = 0f,   // % parent width
    val translateToX: Float = 0f,
    val translateFromY: Float = 0f,   // % parent height
    val translateToY: Float = 0f,
    val scaleFromX: Float = 1f,
    val scaleToX: Float = 1f,
    val scaleFromY: Float = 1f,
    val scaleToY: Float = 1f,
    val alphaFrom: Float = 1f,
    val alphaTo: Float = 1f,
    val rotateFrom: Float = 0f,
    val rotateTo: Float = 0f,
    val pivotX: Float = 50f,          // % of view
    val pivotY: Float = 50f,
    val duration: Long = 350,
    val startOffset: Long = 0,
    val interpolatorIndex: Int = 0
)

internal val INTERPOLATOR_NAMES = listOf(
    "Linear", "Accelerate", "Decelerate", "AccelerateDecelerate",
    "Overshoot", "Bounce", "Anticipate", "AnticipateOvershoot"
)

internal fun resolveInterpolator(index: Int): Interpolator = when (index) {
    0 -> LinearInterpolator()
    1 -> AccelerateInterpolator()
    2 -> DecelerateInterpolator()
    3 -> AccelerateDecelerateInterpolator()
    4 -> OvershootInterpolator()
    5 -> BounceInterpolator()
    6 -> AnticipateInterpolator()
    7 -> AnticipateOvershootInterpolator()
    else -> LinearInterpolator()
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

// ── Activity ────────────────────────────────────────────────────────

class ActivityTransitionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context)
                              else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ActivityTransitionScreen(onBack = { finish() })
                }
            }
        }
    }
}

// ── Main Screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTransitionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }

    // ── settings state ──
    var currentOpenMode by remember {
        mutableIntStateOf(
            try { Settings.Global.getInt(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_TRANSITION) }
            catch (_: Exception) { 0 }
        )
    }
    var currentCloseMode by remember {
        mutableIntStateOf(
            try { Settings.Global.getInt(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_TRANSITION) }
            catch (_: Exception) { 0 }
        )
    }

    // ── predictive back ──
    var predictiveBackDisabled by remember {
        mutableStateOf(
            try { Settings.Global.getInt(context.contentResolver, SettingsKeys.DISABLE_PREDICTIVE_BACK_ANIM) != 0 }
            catch (_: Exception) { false }
        )
    }

    fun applyOpen(mode: Int) {
        Settings.Global.putInt(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_TRANSITION, mode)
        currentOpenMode = mode
    }
    fun applyClose(mode: Int) {
        Settings.Global.putInt(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_TRANSITION, mode)
        currentCloseMode = mode
        // Auto-enable predictive back disable when close animation is set
        if (mode != MODE_DISABLED) {
            predictiveBackDisabled = true
            Settings.Global.putInt(context.contentResolver, SettingsKeys.DISABLE_PREDICTIVE_BACK_ANIM, 1)
        }
    }

    // ── theme creator state ──
    var styleName by remember { mutableStateOf("") }
    var useXmlInput by remember { mutableStateOf(false) }
    var openEnterUri by remember { mutableStateOf<Uri?>(null) }
    var openExitUri by remember { mutableStateOf<Uri?>(null) }
    var closeEnterUri by remember { mutableStateOf<Uri?>(null) }
    var closeExitUri by remember { mutableStateOf<Uri?>(null) }
    var openEnterXml by remember { mutableStateOf("") }
    var openExitXml by remember { mutableStateOf("") }
    var closeEnterXml by remember { mutableStateOf("") }
    var closeExitXml by remember { mutableStateOf("") }
    var compileLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var isCompiling by remember { mutableStateOf(false) }
    var compileError by remember { mutableStateOf<String?>(null) }

    // ── constructor state ──
    var constructorTab by remember { mutableIntStateOf(0) } // 0=OpenEnter,1=OpenExit,2=CloseEnter,3=CloseExit
    var cOpenEnter by remember { mutableStateOf(AnimParams(translateFromX = 100f, translateToX = 0f)) }
    var cOpenExit by remember { mutableStateOf(AnimParams(alphaFrom = 1f, alphaTo = 0.7f)) }
    var cCloseEnter by remember { mutableStateOf(AnimParams(alphaFrom = 0.7f, alphaTo = 1f)) }
    var cCloseExit by remember { mutableStateOf(AnimParams(translateFromX = 0f, translateToX = 100f)) }
    var constructorPreviewKey by remember { mutableIntStateOf(0) }

    // ── installed themes ──
    var installedThemes by remember { mutableStateOf<List<InstalledTheme>>(emptyList()) }

    fun refreshThemes() {
        val activeOpenPkg = try {
            Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE)
                ?: Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)
        } catch (_: Exception) { null }
        val activeClosePkg = try {
            Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE)
                ?: Settings.Global.getString(context.contentResolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE)
        } catch (_: Exception) { null }

        installedThemes = context.packageManager.getInstalledPackages(0)
            .filter { it.packageName.startsWith(THEME_PACKAGE_PREFIX) }
            .map { pi ->
                InstalledTheme(
                    packageName = pi.packageName,
                    styleName = pi.packageName.removePrefix(THEME_PACKAGE_PREFIX),
                    isActiveOpen = currentOpenMode == MODE_CUSTOM && pi.packageName == activeOpenPkg,
                    isActiveClose = currentCloseMode == MODE_CUSTOM && pi.packageName == activeClosePkg
                )
            }
            .sortedBy { it.styleName }
    }

    LaunchedEffect(Unit) { refreshThemes() }
    LaunchedEffect(currentOpenMode, currentCloseMode) { refreshThemes() }

    // ── file pickers ──
    val openEnterPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) openEnterUri = uri }
    val openExitPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) openExitUri = uri }
    val closeEnterPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) closeEnterUri = uri }
    val closeExitPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) closeExitUri = uri }

    // ── compile ──
    fun doCompile() {
        if (styleName.isBlank()) return
        if (!useXmlInput && (openEnterUri == null || openExitUri == null)) return
        if (useXmlInput && (openEnterXml.isBlank() || openExitXml.isBlank())) return
        isCompiling = true
        compileError = null
        compileLog = emptyList()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (useXmlInput) {
                    AnimThemeCompiler.compileFromXml(
                        context = context,
                        styleName = styleName,
                        openEnterXml = openEnterXml,
                        openExitXml = openExitXml,
                        closeEnterXml = closeEnterXml.ifBlank { null },
                        closeExitXml = closeExitXml.ifBlank { null }
                    ) { line -> scope.launch(Dispatchers.Main) { compileLog = compileLog + line } }
                } else {
                    AnimThemeCompiler.compile(
                        context = context,
                        styleName = styleName,
                        openEnterUri = openEnterUri!!,
                        openExitUri = openExitUri!!,
                        closeEnterUri = closeEnterUri,
                        closeExitUri = closeExitUri
                    ) { line -> scope.launch(Dispatchers.Main) { compileLog = compileLog + line } }
                }
            }
            isCompiling = false
            
            // Здесь мы используем result.packageName и передаем его в install
            if (result.success && result.apkPath != null && result.packageName != null) {
                compileLog = compileLog + RemoteStringsManager.getString(context, R.string.anim_installing)
                val installed = withContext(Dispatchers.IO) {
                    AnimThemeCompiler.install(context, result.apkPath, result.packageName) { line ->
                        scope.launch(Dispatchers.Main) { compileLog = compileLog + line }
                    }
                }
                if (installed) {
                    compileLog = compileLog + RemoteStringsManager.getString(context, R.string.anim_install_success)
                    refreshThemes()
                } else {
                    compileError = RemoteStringsManager.getString(context, R.string.anim_install_failed)
                    compileLog = compileLog + RemoteStringsManager.getString(context, R.string.anim_install_failed_log)
                }
            } else {
                compileError = result.error ?: RemoteStringsManager.getString(context, R.string.anim_compile_failed)
            }
        }
    }

    fun activateTheme(packageName: String) {
        // Legacy key for backward compatibility
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE, packageName)
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE, packageName)
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE, packageName)
        refreshThemes()
    }

    fun activateOpenTheme(packageName: String) {
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE, packageName)
        refreshThemes()
    }

    fun activateCloseTheme(packageName: String) {
        Settings.Global.putString(context.contentResolver, SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE, packageName)
        refreshThemes()
    }

    fun uninstallTheme(packageName: String) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { AnimThemeCompiler.uninstall(context, packageName) }
            if (ok) {
                // Clear all package references if this theme was active
                listOf(
                    SettingsKeys.ACTIVITY_TRANSITION_CUSTOM_PACKAGE,
                    SettingsKeys.ACTIVITY_OPEN_CUSTOM_PACKAGE,
                    SettingsKeys.ACTIVITY_CLOSE_CUSTOM_PACKAGE
                ).forEach { key ->
                    try {
                        val current = Settings.Global.getString(context.contentResolver, key)
                        if (packageName == current) {
                            Settings.Global.putString(context.contentResolver, key, null)
                        }
                    } catch (_: Exception) {}
                }
                refreshThemes()
            }
        }
    }

    val canCompile = !isCompiling && styleName.isNotBlank() && if (useXmlInput) {
        openEnterXml.isNotBlank() && openExitXml.isNotBlank()
    } else {
        openEnterUri != null && openExitUri != null
    }

    // ── UI ──
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.anim_transition_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, dynamicStringResource(R.string.nav_back)) }
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
                    start = 16.dp, top = 16.dp + innerPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING + innerPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ═══════════════════════════════════════════════════════
                // Section: Predictive Back toggle
                // ═══════════════════════════════════════════════════════
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dynamicStringResource(R.string.anim_predictive_back_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = dynamicStringResource(R.string.anim_predictive_back_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = predictiveBackDisabled,
                                onCheckedChange = {
                                    predictiveBackDisabled = it
                                    Settings.Global.putInt(
                                        context.contentResolver,
                                        SettingsKeys.DISABLE_PREDICTIVE_BACK_ANIM,
                                        if (it) 1 else 0
                                    )
                                }
                            )
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════
                // Section: Current status
                // ═══════════════════════════════════════════════════════
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = String.format(
                                    dynamicStringResource(R.string.anim_current_open),
                                    modeDisplayName(context, currentOpenMode)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = String.format(
                                    dynamicStringResource(R.string.anim_current_close),
                                    modeDisplayName(context, currentCloseMode)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (currentOpenMode != MODE_DISABLED || currentCloseMode != MODE_DISABLED) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        applyOpen(MODE_DISABLED)
                                        applyClose(MODE_DISABLED)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Text(dynamicStringResource(R.string.anim_builtin_disabled))
                                }
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════
                // Section: Built-in Animations
                // ═══════════════════════════════════════════════════════
                item {
                    Text(
                        text = dynamicStringResource(R.string.anim_builtin_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                // No-animation mode
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentOpenMode == MODE_NO_ANIMATION || currentCloseMode == MODE_NO_ANIMATION)
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dynamicStringResource(R.string.anim_no_animation_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = dynamicStringResource(R.string.anim_no_animation_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                    val noAnimLabels = buildList {
                                        if (currentOpenMode == MODE_NO_ANIMATION) add(dynamicStringResource(R.string.anim_preview_open))
                                        if (currentCloseMode == MODE_NO_ANIMATION) add(dynamicStringResource(R.string.anim_preview_close))
                                    }
                                    if (noAnimLabels.isNotEmpty()) {
                                        Text(
                                            noAnimLabels.joinToString(" + "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { applyOpen(MODE_NO_ANIMATION); applyClose(MODE_NO_ANIMATION) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        dynamicStringResource(R.string.anim_apply_both),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = { applyOpen(MODE_NO_ANIMATION) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        dynamicStringResource(R.string.anim_apply_open),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = { applyClose(MODE_NO_ANIMATION) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        dynamicStringResource(R.string.anim_apply_close),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                items(BUILTIN_MODES, key = { it.modeId }) { mode ->
                    BuiltinAnimItem(
                        mode = mode,
                        isActiveOpen = mode.modeId == currentOpenMode,
                        isActiveClose = mode.modeId == currentCloseMode,
                        onApplyBoth = { applyOpen(mode.modeId); applyClose(mode.modeId) },
                        onApplyOpen = { applyOpen(mode.modeId) },
                        onApplyClose = { applyClose(mode.modeId) }
                    )
                }

                // ═══════════════════════════════════════════════════════
                // Section: Custom Themes (Addons)
                // ═══════════════════════════════════════════════════════
                item {
                    Text(
                        text = dynamicStringResource(R.string.anim_theme_installed_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (installedThemes.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                text = dynamicStringResource(R.string.anim_theme_none_installed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                            )
                        }
                    }
                }

                items(installedThemes, key = { it.packageName }) { theme ->
                    ThemeItem(
                        theme = theme,
                        onApplyOpen = {
                            activateOpenTheme(theme.packageName)
                            applyOpen(MODE_CUSTOM)
                        },
                        onApplyClose = {
                            activateCloseTheme(theme.packageName)
                            applyClose(MODE_CUSTOM)
                        },
                        onApplyBoth = {
                            activateTheme(theme.packageName)
                            applyOpen(MODE_CUSTOM)
                            applyClose(MODE_CUSTOM)
                        },
                        onUninstall = { uninstallTheme(theme.packageName) }
                    )
                }

                // ═══════════════════════════════════════════════════════
                // Section: Create Theme
                // ═══════════════════════════════════════════════════════
                item {
                    SettingsGroupCard(title = dynamicStringResource(R.string.anim_theme_create_title)) {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            // Style name
                            OutlinedTextField(
                                value = styleName,
                                onValueChange = { styleName = it },
                                label = { Text(dynamicStringResource(R.string.anim_theme_style_name)) },
                                placeholder = { Text("e.g. bouncy_slide") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(12.dp))

                            // Input mode toggle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FilterChip(
                                    selected = !useXmlInput,
                                    onClick = { useXmlInput = false },
                                    label = { Text(dynamicStringResource(R.string.anim_theme_input_mode_file)) }
                                )
                                Spacer(Modifier.width(8.dp))
                                FilterChip(
                                    selected = useXmlInput,
                                    onClick = { useXmlInput = true },
                                    label = { Text(dynamicStringResource(R.string.anim_theme_input_mode_xml)) }
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            if (useXmlInput) {
                                // XML text inputs
                                XmlInputField(
                                    label = dynamicStringResource(R.string.anim_theme_open_enter),
                                    value = openEnterXml,
                                    onValueChange = { openEnterXml = it },
                                    required = true
                                )
                                XmlInputField(
                                    label = dynamicStringResource(R.string.anim_theme_open_exit),
                                    value = openExitXml,
                                    onValueChange = { openExitXml = it },
                                    required = true
                                )
                                XmlInputField(
                                    label = dynamicStringResource(R.string.anim_theme_close_enter),
                                    value = closeEnterXml,
                                    onValueChange = { closeEnterXml = it },
                                    required = false
                                )
                                XmlInputField(
                                    label = dynamicStringResource(R.string.anim_theme_close_exit),
                                    value = closeExitXml,
                                    onValueChange = { closeExitXml = it },
                                    required = false
                                )
                            } else {
                                // File pickers
                                FilePickerRow(
                                    label = dynamicStringResource(R.string.anim_theme_open_enter),
                                    uri = openEnterUri, required = true,
                                    onClick = { openEnterPicker.launch("text/xml") }
                                )
                                FilePickerRow(
                                    label = dynamicStringResource(R.string.anim_theme_open_exit),
                                    uri = openExitUri, required = true,
                                    onClick = { openExitPicker.launch("text/xml") }
                                )
                                FilePickerRow(
                                    label = dynamicStringResource(R.string.anim_theme_close_enter),
                                    uri = closeEnterUri, required = false,
                                    onClick = { closeEnterPicker.launch("text/xml") }
                                )
                                FilePickerRow(
                                    label = dynamicStringResource(R.string.anim_theme_close_exit),
                                    uri = closeExitUri, required = false,
                                    onClick = { closeExitPicker.launch("text/xml") }
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Compile button
                            Button(
                                onClick = { doCompile() },
                                enabled = canCompile,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isCompiling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Icon(Icons.Rounded.Build, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(dynamicStringResource(R.string.anim_theme_compile_btn))
                            }

                            if (compileError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = compileError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // ── Compile Log ──
                if (compileLog.isNotEmpty()) {
                    item {
                        SettingsGroupCard(title = dynamicStringResource(R.string.anim_theme_log_title)) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                                    .fillMaxWidth()
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .heightIn(max = 200.dp)
                                    ) {
                                        compileLog.forEach { line ->
                                            Text(
                                                text = line,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp, lineHeight = 15.sp,
                                                color = when {
                                                    line.startsWith("ERROR") || line.startsWith("✗") ->
                                                        MaterialTheme.colorScheme.error
                                                    line.startsWith("✓") ->
                                                        MaterialTheme.colorScheme.primary
                                                    else ->
                                                        MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ═══════════════════════════════════════════════════════
                // Section: Animation Constructor
                // ═══════════════════════════════════════════════════════
                item {
                    Text(
                        text = dynamicStringResource(R.string.anim_constructor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                item {
                    AnimConstructorBlock(
                        selectedTab = constructorTab,
                        onTabSelected = { constructorTab = it },
                        openEnter = cOpenEnter,
                        openExit = cOpenExit,
                        closeEnter = cCloseEnter,
                        closeExit = cCloseExit,
                        onOpenEnterChange = { cOpenEnter = it; constructorPreviewKey++ },
                        onOpenExitChange = { cOpenExit = it; constructorPreviewKey++ },
                        onCloseEnterChange = { cCloseEnter = it; constructorPreviewKey++ },
                        onCloseExitChange = { cCloseExit = it; constructorPreviewKey++ }
                    )
                }

                item {
                    ConstructorPreview(
                        openEnter = cOpenEnter,
                        openExit = cOpenExit,
                        closeEnter = cCloseEnter,
                        closeExit = cCloseExit,
                        previewKey = constructorPreviewKey
                    )
                }

                item {
                    ConstructorExportButton(
                        openEnter = cOpenEnter,
                        openExit = cOpenExit,
                        closeEnter = cCloseEnter,
                        closeExit = cCloseExit,
                        onBuildTheme = { name ->
                            styleName = name
                            useXmlInput = true
                            openEnterXml = animParamsToXml(cOpenEnter)
                            openExitXml = animParamsToXml(cOpenExit)
                            closeEnterXml = animParamsToXml(cCloseEnter)
                            closeExitXml = animParamsToXml(cCloseExit)
                            doCompile()
                        }
                    )
                }
            }

            TopBarBlurOverlay(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                topBarHeight = innerPadding.calculateTopPadding(),
                blurState = blurState,
                isScrolled = isScrolled
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Components
// ═══════════════════════════════════════════════════════════════════

// ── Built-in Animation Item ────────────────────────────────────────

@Composable
private fun BuiltinAnimItem(
    mode: AnimMode,
    isActiveOpen: Boolean,
    isActiveClose: Boolean,
    onApplyBoth: () -> Unit,
    onApplyOpen: () -> Unit,
    onApplyClose: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val displayName = remember(mode) {
        val group = context.getString(mode.groupNameRes)
        if (mode.dirNameRes != null) "$group — ${context.getString(mode.dirNameRes)}" else group
    }

    val isAnyActive = isActiveOpen || isActiveClose

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAnyActive)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isActiveOpen && isActiveClose) {
                        Text(dynamicStringResource(R.string.anim_active_open_close), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else if (isActiveOpen) {
                        Text(dynamicStringResource(R.string.anim_preview_open), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else if (isActiveClose) {
                        Text(dynamicStringResource(R.string.anim_preview_close), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess
                        else Icons.Rounded.ExpandMore,
                        contentDescription = dynamicStringResource(R.string.anim_constructor_preview)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Two preview cards side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LoopingAnimPreview(
                            label = dynamicStringResource(R.string.anim_preview_open),
                            enterAnimRes = mode.openEnterAnim,
                            exitAnimRes = mode.openExitAnim,
                            isOpen = true
                        )
                        LoopingAnimPreview(
                            label = dynamicStringResource(R.string.anim_preview_close),
                            enterAnimRes = mode.closeEnterAnim,
                            exitAnimRes = mode.closeExitAnim,
                            isOpen = false
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Apply buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onApplyBoth,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                dynamicStringResource(R.string.anim_apply_both),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = onApplyOpen,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                dynamicStringResource(R.string.anim_apply_open),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = onApplyClose,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                dynamicStringResource(R.string.anim_apply_close),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Looping Animation Preview (built-in) ───────────────────────────

@Composable
private fun LoopingAnimPreview(
    label: String,
    enterAnimRes: Int,
    exitAnimRes: Int,
    isOpen: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.size(width = 110.dp, height = 190.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        createLoopingBuiltinPreview(ctx, enterAnimRes, exitAnimRes, isOpen)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ── Theme Item (Custom Addons) ──────────────────────────────────────

@Composable
private fun ThemeItem(
    theme: InstalledTheme,
    onApplyOpen: () -> Unit,
    onApplyClose: () -> Unit,
    onApplyBoth: () -> Unit,
    onUninstall: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val isActive = theme.isActiveOpen || theme.isActiveClose

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = theme.styleName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = theme.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    if (theme.isActiveOpen && theme.isActiveClose) {
                        Text(dynamicStringResource(R.string.anim_active_open_close), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else if (theme.isActiveOpen) {
                        Text(dynamicStringResource(R.string.anim_preview_open), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    } else if (theme.isActiveClose) {
                        Text(dynamicStringResource(R.string.anim_preview_close), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                    }
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess
                        else Icons.Rounded.ExpandMore,
                        contentDescription = dynamicStringResource(R.string.anim_constructor_preview)
                    )
                }

                if (!showConfirmDelete) {
                    IconButton(onClick = { showConfirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, dynamicStringResource(R.string.anim_cd_delete),
                            tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Row {
                        IconButton(onClick = {
                            showConfirmDelete = false; onUninstall()
                        }) {
                            Icon(Icons.Rounded.Check, dynamicStringResource(R.string.anim_cd_confirm_delete),
                                tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { showConfirmDelete = false }) {
                            Icon(Icons.Rounded.Close, dynamicStringResource(R.string.anim_dialog_cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Expandable animation preview
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ThemePreviewPanel(
                    packageName = theme.packageName,
                    onApplyBoth = onApplyBoth,
                    onApplyOpen = onApplyOpen,
                    onApplyClose = onApplyClose
                )
            }
        }
    }
}

// ── Theme Preview Panel ─────────────────────────────────────────────

@Composable
private fun ThemePreviewPanel(
    packageName: String,
    onApplyBoth: () -> Unit,
    onApplyOpen: () -> Unit,
    onApplyClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LoopingThemePreview(
                label = dynamicStringResource(R.string.anim_preview_open),
                packageName = packageName,
                enterAnimName = "custom_open_enter",
                exitAnimName = "custom_open_exit",
                isOpen = true
            )
            LoopingThemePreview(
                label = dynamicStringResource(R.string.anim_preview_close),
                packageName = packageName,
                enterAnimName = "custom_close_enter",
                exitAnimName = "custom_close_exit",
                isOpen = false
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onApplyBoth,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(dynamicStringResource(R.string.anim_apply_both),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            OutlinedButton(
                onClick = onApplyOpen,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(dynamicStringResource(R.string.anim_apply_open),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            OutlinedButton(
                onClick = onApplyClose,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(dynamicStringResource(R.string.anim_apply_close),
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

// ── Looping Theme Preview (custom APK) ──────────────────────────────

@Composable
private fun LoopingThemePreview(
    label: String,
    packageName: String,
    enterAnimName: String,
    exitAnimName: String,
    isOpen: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.size(width = 110.dp, height = 190.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        createLoopingThemePreview(
                            ctx, packageName, enterAnimName, exitAnimName, isOpen
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ── File Picker Row ─────────────────────────────────────────────────

@Composable
private fun FilePickerRow(
    label: String,
    uri: Uri?,
    required: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (uri != null) Icons.Rounded.CheckCircle else Icons.Rounded.FileOpen,
            contentDescription = null,
            tint = if (uri != null) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(text = label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                if (required) {
                    Text(" *", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (uri != null) {
                Text(
                    text = uri.lastPathSegment ?: uri.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            } else {
                Text(
                    text = dynamicStringResource(R.string.anim_theme_tap_to_select),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── XML Input Field ─────────────────────────────────────────────────

@Composable
private fun XmlInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    required: Boolean
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(text = label, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            if (required) {
                Text(" *", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(dynamicStringResource(R.string.anim_theme_xml_hint),
                style = MaterialTheme.typography.bodySmall) },
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 160.dp),
            maxLines = 12
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// Preview engine: View-layer helpers
// ═══════════════════════════════════════════════════════════════════

private val handler = Handler(Looper.getMainLooper())

private fun createPreviewScreen(
    ctx: Context,
    label: String,
    bgColor: Int,
    fgColor: Int
): LinearLayout {
    val dp = ctx.resources.displayMetrics.density
    return LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(bgColor)
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        // Fake top bar
        addView(View(ctx).apply {
            setBackgroundColor(fgColor and 0x00FFFFFF or (0x33 shl 24))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (18 * dp).toInt()
            )
        })
        addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        })
        addView(TextView(ctx).apply {
            text = label; textSize = 28f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(fgColor); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
        addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.6f
            )
        })
        repeat(2) { i ->
            addView(View(ctx).apply {
                setBackgroundColor(fgColor and 0x00FFFFFF or (0x22 shl 24))
                val m = (10 * dp).toInt()
                val w = if (i == 1) (36 * dp).toInt() else LinearLayout.LayoutParams.MATCH_PARENT
                layoutParams = LinearLayout.LayoutParams(w, (4 * dp).toInt()).apply {
                    setMargins(m, (3 * dp).toInt(), m, 0)
                }
            })
        }
        addView(View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (8 * dp).toInt()
            )
        })
    }
}

/**
 * Create a looping preview for a built-in animation.
 * Uses the app's own R.anim resources.
 */
private fun createLoopingBuiltinPreview(
    viewCtx: Context,
    enterAnimRes: Int,
    exitAnimRes: Int,
    isOpen: Boolean
): FrameLayout {
    val frame = FrameLayout(viewCtx).apply {
        clipChildren = true; clipToPadding = true
    }
    val screenA = createPreviewScreen(viewCtx, "A", 0xFFF3EDF7.toInt(), 0xFF49454F.toInt())
    val screenB = createPreviewScreen(viewCtx, "B", 0xFF6750A4.toInt(), 0xFFFFFFFF.toInt())
    frame.addView(screenA)
    frame.addView(screenB)

    if (isOpen) {
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
    }

    frame.post {
        loopBuiltinAnim(viewCtx, frame, screenA, screenB, enterAnimRes, exitAnimRes, isOpen)
    }
    return frame
}

private fun loopBuiltinAnim(
    ctx: Context,
    frame: FrameLayout,
    screenA: View,
    screenB: View,
    enterRes: Int,
    exitRes: Int,
    isOpen: Boolean
) {
    if (!frame.isAttachedToWindow) return

    // Reset to initial state
    screenA.clearAnimation()
    screenB.clearAnimation()
    screenA.alpha = 1f; screenA.translationX = 0f; screenA.translationY = 0f
    screenA.scaleX = 1f; screenA.scaleY = 1f; screenA.rotation = 0f
    screenB.alpha = 1f; screenB.translationX = 0f; screenB.translationY = 0f
    screenB.scaleX = 1f; screenB.scaleY = 1f; screenB.rotation = 0f

    if (isOpen) {
        screenA.visibility = View.VISIBLE
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
        screenB.visibility = View.VISIBLE
    }

    handler.postDelayed({
        if (!frame.isAttachedToWindow) return@postDelayed

        var duration = 350L
        if (isOpen) {
            screenB.visibility = View.VISIBLE
            if (enterRes != 0) {
                val anim = AnimationUtils.loadAnimation(ctx, enterRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenB.startAnimation(anim)
            }
            if (exitRes != 0) {
                val anim = AnimationUtils.loadAnimation(ctx, exitRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenA.startAnimation(anim)
            }
        } else {
            screenA.visibility = View.VISIBLE
            if (enterRes != 0) {
                val anim = AnimationUtils.loadAnimation(ctx, enterRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenA.startAnimation(anim)
            }
            if (exitRes != 0) {
                val anim = AnimationUtils.loadAnimation(ctx, exitRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenB.startAnimation(anim)
            }
        }

        handler.postDelayed({
            loopBuiltinAnim(ctx, frame, screenA, screenB, enterRes, exitRes, isOpen)
        }, duration + 1200)
    }, 500)
}

/**
 * Create a looping preview for a custom theme APK animation.
 * Loads animations from the theme package via createPackageContext.
 */
private fun createLoopingThemePreview(
    viewCtx: Context,
    packageName: String,
    enterAnimName: String,
    exitAnimName: String,
    isOpen: Boolean
): FrameLayout {
    val frame = FrameLayout(viewCtx).apply {
        clipChildren = true; clipToPadding = true
    }
    val screenA = createPreviewScreen(viewCtx, "A", 0xFFF3EDF7.toInt(), 0xFF49454F.toInt())
    val screenB = createPreviewScreen(viewCtx, "B", 0xFF6750A4.toInt(), 0xFFFFFFFF.toInt())
    frame.addView(screenA)
    frame.addView(screenB)

    if (isOpen) {
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
    }

    frame.post {
        try {
            val themeCtx = viewCtx.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            val res = themeCtx.resources
            val enterId = res.getIdentifier(enterAnimName, "anim", packageName)
            val exitId = res.getIdentifier(exitAnimName, "anim", packageName)
            loopThemeAnim(themeCtx, frame, screenA, screenB, enterId, exitId, isOpen)
        } catch (_: Exception) {
            screenB.visibility = View.VISIBLE
        }
    }
    return frame
}

private fun loopThemeAnim(
    themeCtx: Context,
    frame: FrameLayout,
    screenA: View,
    screenB: View,
    enterRes: Int,
    exitRes: Int,
    isOpen: Boolean
) {
    if (!frame.isAttachedToWindow) return

    screenA.clearAnimation()
    screenB.clearAnimation()
    screenA.alpha = 1f; screenA.translationX = 0f; screenA.translationY = 0f
    screenA.scaleX = 1f; screenA.scaleY = 1f; screenA.rotation = 0f
    screenB.alpha = 1f; screenB.translationX = 0f; screenB.translationY = 0f
    screenB.scaleX = 1f; screenB.scaleY = 1f; screenB.rotation = 0f

    if (isOpen) {
        screenA.visibility = View.VISIBLE
        screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE
        screenB.visibility = View.VISIBLE
    }

    handler.postDelayed({
        if (!frame.isAttachedToWindow) return@postDelayed

        var duration = 350L
        if (isOpen) {
            screenB.visibility = View.VISIBLE
            if (enterRes != 0) {
                val anim = AnimationUtils.loadAnimation(themeCtx, enterRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenB.startAnimation(anim)
            }
            if (exitRes != 0) {
                val anim = AnimationUtils.loadAnimation(themeCtx, exitRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenA.startAnimation(anim)
            }
        } else {
            screenA.visibility = View.VISIBLE
            if (enterRes != 0) {
                val anim = AnimationUtils.loadAnimation(themeCtx, enterRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenA.startAnimation(anim)
            }
            if (exitRes != 0) {
                val anim = AnimationUtils.loadAnimation(themeCtx, exitRes).apply { fillAfter = true }
                duration = maxOf(duration, anim.duration + anim.startOffset)
                screenB.startAnimation(anim)
            }
        }

        handler.postDelayed({
            loopThemeAnim(themeCtx, frame, screenA, screenB, enterRes, exitRes, isOpen)
        }, duration + 1200)
    }, 500)
}

// ═══════════════════════════════════════════════════════════════════
// Animation Constructor
// ═══════════════════════════════════════════════════════════════════

private val CONSTRUCTOR_TAB_LABELS = listOf(R.string.anim_theme_open_enter, R.string.anim_theme_open_exit, R.string.anim_theme_close_enter, R.string.anim_theme_close_exit)

@Composable
private fun AnimConstructorBlock(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    openEnter: AnimParams,
    openExit: AnimParams,
    closeEnter: AnimParams,
    closeExit: AnimParams,
    onOpenEnterChange: (AnimParams) -> Unit,
    onOpenExitChange: (AnimParams) -> Unit,
    onCloseEnterChange: (AnimParams) -> Unit,
    onCloseExitChange: (AnimParams) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Tab row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                CONSTRUCTOR_TAB_LABELS.forEachIndexed { i, labelRes ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { onTabSelected(i) },
                        text = { Text(dynamicStringResource(labelRes), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Selected tab params editor
            val currentParams = when (selectedTab) {
                0 -> openEnter; 1 -> openExit; 2 -> closeEnter; else -> closeExit
            }
            val onParamsChange: (AnimParams) -> Unit = when (selectedTab) {
                0 -> onOpenEnterChange; 1 -> onOpenExitChange
                2 -> onCloseEnterChange; else -> onCloseExitChange
            }

            ParamEditor(params = currentParams, onParamsChange = onParamsChange)
        }
    }
}

@Composable
private fun ParamEditor(
    params: AnimParams,
    onParamsChange: (AnimParams) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // ── Translate ──
        Text(dynamicStringResource(R.string.anim_param_translate), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("fromX %", params.translateFromX, -100f, 100f, 0f) {
            onParamsChange(params.copy(translateFromX = it))
        }
        ParamSlider("toX %", params.translateToX, -100f, 100f, 0f) {
            onParamsChange(params.copy(translateToX = it))
        }
        ParamSlider("fromY %", params.translateFromY, -100f, 100f, 0f) {
            onParamsChange(params.copy(translateFromY = it))
        }
        ParamSlider("toY %", params.translateToY, -100f, 100f, 0f) {
            onParamsChange(params.copy(translateToY = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Scale ──
        Text(dynamicStringResource(R.string.anim_param_scale), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("fromX", params.scaleFromX, 0f, 3f, 1f) {
            onParamsChange(params.copy(scaleFromX = it))
        }
        ParamSlider("toX", params.scaleToX, 0f, 3f, 1f) {
            onParamsChange(params.copy(scaleToX = it))
        }
        ParamSlider("fromY", params.scaleFromY, 0f, 3f, 1f) {
            onParamsChange(params.copy(scaleFromY = it))
        }
        ParamSlider("toY", params.scaleToY, 0f, 3f, 1f) {
            onParamsChange(params.copy(scaleToY = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Alpha ──
        Text(dynamicStringResource(R.string.anim_param_alpha), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("from", params.alphaFrom, 0f, 1f, 1f) {
            onParamsChange(params.copy(alphaFrom = it))
        }
        ParamSlider("to", params.alphaTo, 0f, 1f, 1f) {
            onParamsChange(params.copy(alphaTo = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Rotate ──
        Text(dynamicStringResource(R.string.anim_param_rotate), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("from °", params.rotateFrom, -360f, 360f, 0f) {
            onParamsChange(params.copy(rotateFrom = it))
        }
        ParamSlider("to °", params.rotateTo, -360f, 360f, 0f) {
            onParamsChange(params.copy(rotateTo = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Pivot ──
        Text(dynamicStringResource(R.string.anim_param_pivot), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("X %", params.pivotX, 0f, 100f, 50f) {
            onParamsChange(params.copy(pivotX = it))
        }
        ParamSlider("Y %", params.pivotY, 0f, 100f, 50f) {
            onParamsChange(params.copy(pivotY = it))
        }

        Spacer(Modifier.height(4.dp))

        // ── Timing ──
        Text(dynamicStringResource(R.string.anim_param_timing), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        ParamSlider("duration ms", params.duration.toFloat(), 50f, 2000f, 350f) {
            onParamsChange(params.copy(duration = it.toLong()))
        }
        ParamSlider("offset ms", params.startOffset.toFloat(), 0f, 1000f, 0f) {
            onParamsChange(params.copy(startOffset = it.toLong()))
        }

        Spacer(Modifier.height(4.dp))

        // ── Interpolator ──
        Text(dynamicStringResource(R.string.anim_param_interpolator), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        InterpolatorSelector(
            selectedIndex = params.interpolatorIndex,
            onSelected = { onParamsChange(params.copy(interpolatorIndex = it)) }
        )
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    defaultValue: Float = 0f,
    onValueChange: (Float) -> Unit
) {
    var showInputDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .width(72.dp)
                .clickable { showInputDialog = true },
            maxLines = 1
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (max <= 3f) String.format("%.2f", value)
                   else String.format("%.0f", value),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(48.dp),
            maxLines = 1
        )
        if (value != defaultValue) {
            IconButton(
                onClick = { onValueChange(defaultValue) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Rounded.Refresh, dynamicStringResource(R.string.anim_cd_reset),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.width(28.dp))
        }
    }

    if (showInputDialog) {
        var inputText by remember {
            mutableStateOf(
                if (max <= 3f) String.format("%.2f", value) else String.format("%.0f", value)
            )
        }
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    inputText.replace(',', '.').toFloatOrNull()?.let { v ->
                        onValueChange(v.coerceIn(min, max))
                    }
                    showInputDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) { Text(dynamicStringResource(R.string.anim_dialog_cancel)) }
            }
        )
    }
}

@Composable
private fun InterpolatorSelector(
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(INTERPOLATOR_NAMES.getOrElse(selectedIndex) { "Linear" },
                style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ArrowDropDown, null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            INTERPOLATOR_NAMES.forEachIndexed { i, name ->
                DropdownMenuItem(
                    text = { Text(name, style = MaterialTheme.typography.bodyMedium) },
                    onClick = { onSelected(i); expanded = false },
                    leadingIcon = {
                        if (i == selectedIndex) Icon(Icons.Rounded.Check, null,
                            modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

// ── Constructor Preview ─────────────────────────────────────────────

@Composable
private fun ConstructorPreview(
    openEnter: AnimParams,
    openExit: AnimParams,
    closeEnter: AnimParams,
    closeExit: AnimParams,
    previewKey: Int
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dynamicStringResource(R.string.anim_constructor_preview),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Open preview
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dynamicStringResource(R.string.anim_preview_open), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.size(width = 110.dp, height = 190.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                            key(previewKey) {
                                AndroidView(
                                    factory = { ctx ->
                                        createConstructorPreview(ctx, openEnter, openExit, true)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Close preview
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dynamicStringResource(R.string.anim_preview_close), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.size(width = 110.dp, height = 190.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))) {
                            key(previewKey) {
                                AndroidView(
                                    factory = { ctx ->
                                        createConstructorPreview(ctx, closeEnter, closeExit, false)
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Export XML button ───────────────────────────────────────────────

private fun animParamsToXml(params: AnimParams): String {
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
    sb.appendLine("""<set xmlns:android="http://schemas.android.com/apk/res/android">""")

    // Translate
    if (params.translateFromX != 0f || params.translateToX != 0f ||
        params.translateFromY != 0f || params.translateToY != 0f) {
        sb.appendLine("    <translate")
        sb.appendLine("        android:fromXDelta=\"${params.translateFromX}%p\"")
        sb.appendLine("        android:toXDelta=\"${params.translateToX}%p\"")
        sb.appendLine("        android:fromYDelta=\"${params.translateFromY}%p\"")
        sb.appendLine("        android:toYDelta=\"${params.translateToY}%p\"")
        sb.appendLine("        android:duration=\"${params.duration}\"")
        sb.appendLine("        android:startOffset=\"${params.startOffset}\"")
        sb.appendLine("        android:interpolator=\"@android:anim/${interpolatorXmlName(params.interpolatorIndex)}\"")
        sb.appendLine("        android:fillAfter=\"true\" />")
    }
    // Scale
    if (params.scaleFromX != 1f || params.scaleToX != 1f ||
        params.scaleFromY != 1f || params.scaleToY != 1f) {
        sb.appendLine("    <scale")
        sb.appendLine("        android:fromXScale=\"${params.scaleFromX}\"")
        sb.appendLine("        android:toXScale=\"${params.scaleToX}\"")
        sb.appendLine("        android:fromYScale=\"${params.scaleFromY}\"")
        sb.appendLine("        android:toYScale=\"${params.scaleToY}\"")
        sb.appendLine("        android:pivotX=\"${params.pivotX}%\"")
        sb.appendLine("        android:pivotY=\"${params.pivotY}%\"")
        sb.appendLine("        android:duration=\"${params.duration}\"")
        sb.appendLine("        android:startOffset=\"${params.startOffset}\"")
        sb.appendLine("        android:interpolator=\"@android:anim/${interpolatorXmlName(params.interpolatorIndex)}\"")
        sb.appendLine("        android:fillAfter=\"true\" />")
    }
    // Alpha
    if (params.alphaFrom != 1f || params.alphaTo != 1f) {
        sb.appendLine("    <alpha")
        sb.appendLine("        android:fromAlpha=\"${params.alphaFrom}\"")
        sb.appendLine("        android:toAlpha=\"${params.alphaTo}\"")
        sb.appendLine("        android:duration=\"${params.duration}\"")
        sb.appendLine("        android:startOffset=\"${params.startOffset}\"")
        sb.appendLine("        android:interpolator=\"@android:anim/${interpolatorXmlName(params.interpolatorIndex)}\"")
        sb.appendLine("        android:fillAfter=\"true\" />")
    }
    // Rotate
    if (params.rotateFrom != 0f || params.rotateTo != 0f) {
        sb.appendLine("    <rotate")
        sb.appendLine("        android:fromDegrees=\"${params.rotateFrom}\"")
        sb.appendLine("        android:toDegrees=\"${params.rotateTo}\"")
        sb.appendLine("        android:pivotX=\"${params.pivotX}%\"")
        sb.appendLine("        android:pivotY=\"${params.pivotY}%\"")
        sb.appendLine("        android:duration=\"${params.duration}\"")
        sb.appendLine("        android:startOffset=\"${params.startOffset}\"")
        sb.appendLine("        android:interpolator=\"@android:anim/${interpolatorXmlName(params.interpolatorIndex)}\"")
        sb.appendLine("        android:fillAfter=\"true\" />")
    }

    sb.appendLine("</set>")
    return sb.toString()
}

private fun interpolatorXmlName(index: Int): String = when (index) {
    0 -> "linear_interpolator"
    1 -> "accelerate_interpolator"
    2 -> "decelerate_interpolator"
    3 -> "accelerate_decelerate_interpolator"
    4 -> "overshoot_interpolator"
    5 -> "bounce_interpolator"
    6 -> "anticipate_interpolator"
    7 -> "anticipate_overshoot_interpolator"
    else -> "linear_interpolator"
}

@Composable
private fun ConstructorExportButton(
    openEnter: AnimParams,
    openExit: AnimParams,
    closeEnter: AnimParams,
    closeExit: AnimParams,
    onBuildTheme: (String) -> Unit
) {
    var showXml by remember { mutableStateOf(false) }
    var showBuildInput by remember { mutableStateOf(false) }
    var buildThemeName by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Build theme ──
            AnimatedVisibility(
                visible = showBuildInput,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = buildThemeName,
                        onValueChange = { buildThemeName = it },
                        label = { Text(dynamicStringResource(R.string.anim_constructor_style_name)) },
                        placeholder = { Text("e.g. my_animation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (buildThemeName.isNotBlank()) {
                                onBuildTheme(buildThemeName)
                                showBuildInput = false
                            }
                        },
                        enabled = buildThemeName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Build, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(dynamicStringResource(R.string.anim_constructor_build_theme))
                    }
                }
            }

            FilledTonalButton(
                onClick = { showBuildInput = !showBuildInput },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Build, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (showBuildInput) dynamicStringResource(R.string.anim_constructor_hide) else dynamicStringResource(R.string.anim_constructor_build_theme))
            }

            Spacer(Modifier.height(8.dp))

            // ── Show XML ──
            FilledTonalButton(
                onClick = { showXml = !showXml },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Code, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (showXml) dynamicStringResource(R.string.anim_constructor_hide_xml) else dynamicStringResource(R.string.anim_constructor_show_xml))
            }

            AnimatedVisibility(
                visible = showXml,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    listOf(
                        "open_enter" to openEnter,
                        "open_exit" to openExit,
                        "close_enter" to closeEnter,
                        "close_exit" to closeExit
                    ).forEach { (name, p) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("<!-- $name -->",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace)
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(animParamsToXml(p)))
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ContentCopy, dynamicStringResource(R.string.anim_cd_copy),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = animParamsToXml(p),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp
                                ),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Custom preview engine (ValueAnimator-based, no XML resources)
// ═══════════════════════════════════════════════════════════════════

/**
 * Create a looping preview that animates using raw AnimParams
 * instead of loading anim XML resources.
 */
private fun createConstructorPreview(
    ctx: Context,
    enterParams: AnimParams,
    exitParams: AnimParams,
    isOpen: Boolean
): FrameLayout {
    val frame = FrameLayout(ctx).apply {
        clipChildren = true; clipToPadding = true
    }
    val screenA = createPreviewScreen(ctx, "A", 0xFFF3EDF7.toInt(), 0xFF49454F.toInt())
    val screenB = createPreviewScreen(ctx, "B", 0xFF6750A4.toInt(), 0xFFFFFFFF.toInt())
    frame.addView(screenA)
    frame.addView(screenB)

    if (isOpen) screenB.visibility = View.INVISIBLE
    else screenA.visibility = View.INVISIBLE

    frame.post {
        loopConstructorAnim(ctx, frame, screenA, screenB, enterParams, exitParams, isOpen)
    }
    return frame
}

private fun loopConstructorAnim(
    ctx: Context,
    frame: FrameLayout,
    screenA: View,
    screenB: View,
    enterParams: AnimParams,
    exitParams: AnimParams,
    isOpen: Boolean
) {
    if (!frame.isAttachedToWindow) return

    // Reset
    resetViewTransform(screenA)
    resetViewTransform(screenB)
    if (isOpen) {
        screenA.visibility = View.VISIBLE; screenB.visibility = View.INVISIBLE
    } else {
        screenA.visibility = View.INVISIBLE; screenB.visibility = View.VISIBLE
    }

    handler.postDelayed({
        if (!frame.isAttachedToWindow) return@postDelayed

        val enterDur = enterParams.duration + enterParams.startOffset
        val exitDur = exitParams.duration + exitParams.startOffset
        val maxDur = maxOf(enterDur, exitDur, 350L)

        if (isOpen) {
            // Open: B enters, A exits
            screenB.visibility = View.VISIBLE
            driveAnimParams(screenB, enterParams)
            driveAnimParams(screenA, exitParams)
        } else {
            // Close: A enters, B exits
            screenA.visibility = View.VISIBLE
            driveAnimParams(screenA, enterParams)
            driveAnimParams(screenB, exitParams)
        }

        handler.postDelayed({
            loopConstructorAnim(ctx, frame, screenA, screenB, enterParams, exitParams, isOpen)
        }, maxDur + 1200)
    }, 500)
}

private fun driveAnimParams(view: View, params: AnimParams) {
    val interpolator = resolveInterpolator(params.interpolatorIndex)
    ValueAnimator.ofFloat(0f, 1f).apply {
        duration = params.duration
        startDelay = params.startOffset
        this.interpolator = interpolator
        addUpdateListener { anim ->
            val t = anim.animatedValue as Float
            val parent = view.parent as? View ?: return@addUpdateListener
            val pw = parent.width.toFloat()
            val ph = parent.height.toFloat()

            view.translationX = lerp(params.translateFromX / 100f * pw, params.translateToX / 100f * pw, t)
            view.translationY = lerp(params.translateFromY / 100f * ph, params.translateToY / 100f * ph, t)
            view.scaleX = lerp(params.scaleFromX, params.scaleToX, t)
            view.scaleY = lerp(params.scaleFromY, params.scaleToY, t)
            view.alpha = lerp(params.alphaFrom, params.alphaTo, t)
            view.rotation = lerp(params.rotateFrom, params.rotateTo, t)
            view.pivotX = params.pivotX / 100f * view.width
            view.pivotY = params.pivotY / 100f * view.height
        }
        start()
    }
}

private fun resetViewTransform(v: View) {
    v.clearAnimation()
    v.translationX = 0f; v.translationY = 0f
    v.scaleX = 1f; v.scaleY = 1f
    v.alpha = 1f; v.rotation = 0f
}
