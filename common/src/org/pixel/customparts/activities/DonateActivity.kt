package org.pixel.customparts.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.pixel.customparts.R
import org.pixel.customparts.dynamicDarkColorScheme
import org.pixel.customparts.dynamicLightColorScheme
import org.pixel.customparts.ui.REBOOT_BUBBLE_CONTENT_BOTTOM_PADDING
import org.pixel.customparts.ui.RebootBubble
import org.pixel.customparts.ui.TopBarBlurOverlay
import org.pixel.customparts.ui.recordLayer
import org.pixel.customparts.ui.rememberGraphicsLayerRecordingState
import org.pixel.customparts.utils.dynamicStringResource
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale

private const val TAG = "DonateActivity"

class DonateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkTheme = isSystemInDarkTheme()
            val context = LocalContext.current
            val colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DonateScreen(onBack = { finish() })
                }
            }
        }
    }
}


data class DonatePageData(
    val mainDonate: MainDonateInfo?,
    val urlsHeader: Map<String, String>?,
    val urls: List<SocialLink>,
    val progress: List<DonateCardConfig>
)

data class MainDonateInfo(
    val title: Map<String, String>,
    val desc: Map<String, String>,
    val iconKey: String,
    val buttons: List<DonateButton>
)

data class DonateButton(
    val text: Map<String, String>,
    val url: String,
    val colorType: String
)

data class SocialLink(
    val title: Map<String, String>,
    val subtitle: Map<String, String>,
    val url: String,
    val iconKey: String
)

data class DonateCardConfig(
    val title: String,
    val apiUrl: String,
    val webUrl: String,
    val desc: Map<String, String>
)

data class TargetData(val current: Double, val target: Double, val currency: String)

sealed class TargetState {
    data object Loading : TargetState()
    data class Success(val rub: TargetData, val usd: TargetData, val eur: TargetData) : TargetState()
    data class Error(val message: String) : TargetState()
}

object IconMapper {
    fun getIcon(key: String): ImageVector {
        return when (key) {
            "volunteer_activism" -> Icons.Rounded.VolunteerActivism
            "forum" -> Icons.Rounded.Forum
            "code" -> Icons.Rounded.Code
            "open_in_new" -> Icons.AutoMirrored.Rounded.OpenInNew
            "savings" -> Icons.Rounded.Savings
            "favorite" -> Icons.Rounded.Favorite
            "info" -> Icons.Rounded.Info
            else -> Icons.Rounded.Info
        }
    }
}

@Composable
fun getLocalizedText(map: Map<String, String>?): String {
    if (map == null) return ""
    val systemLanguage = Locale.getDefault().language
    return map[systemLanguage] ?: map["en"] ?: map.values.firstOrNull() ?: ""
}

private suspend fun fetchCardState(config: DonateCardConfig): TargetState = withContext(Dispatchers.IO) {
    try {
        val rub = async { fetchBoostyTarget(config.apiUrl, "RUB") }
        val usd = async { fetchBoostyTarget(config.apiUrl, "USD") }
        val eur = async { fetchBoostyTarget(config.apiUrl, "EUR") }

        val r = rub.await()
        val u = usd.await()
        val e = eur.await()

        if (r != null && u != null && e != null) {
            TargetState.Success(r, u, e)
        } else {
            TargetState.Error("API Error")
        }
    } catch (e: Exception) {
        TargetState.Error(e.message ?: "Unknown Error")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val blurState = rememberGraphicsLayerRecordingState()
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val isScrolled by remember { derivedStateOf { lazyListState.canScrollBackward } }
    
    var pageData by remember { mutableStateOf<DonatePageData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) } 

    val targetStates = remember { mutableStateMapOf<String, TargetState>() }

    val refreshSingleCard: (DonateCardConfig) -> Unit = { config ->
        targetStates[config.apiUrl] = TargetState.Loading
        scope.launch {
            targetStates[config.apiUrl] = fetchCardState(config)
        }
    }

    LaunchedEffect(refreshKey) {
        val rawJsonUrl = "https://raw.githubusercontent.com/leegarchat/PixelExtraParts/main/donate_page.json?t=${System.currentTimeMillis()}"
        try {
            isLoading = true
            loadError = null
            
            
            val result = fetchDonatePageData(rawJsonUrl)
            
            if (result != null) {
                pageData = result
                isLoading = false
                
                
                result.progress.forEach { config ->
                    targetStates[config.apiUrl] = TargetState.Loading
                    val state = fetchCardState(config)
                    targetStates[config.apiUrl] = state
                }
                
                if (refreshKey > 0) {
                    Toast.makeText(context, "Страница обновлена", Toast.LENGTH_SHORT).show()
                }
            } else {
                loadError = "Failed to parse config"
                isLoading = false
                if (refreshKey > 0) {
                    Toast.makeText(context, "Ошибка получения данных", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            loadError = e.message
            isLoading = false
            if (refreshKey > 0) {
                Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        floatingActionButton = { RebootBubble() },
        topBar = {
            TopAppBar(
                title = { Text(dynamicStringResource(R.string.donate_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshKey++ }) {
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
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (loadError != null) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(text = loadError!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            } else {
                val data = pageData!!
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
                if (data.mainDonate != null) item { MainDonateCard(data.mainDonate) }
                if (!data.urlsHeader.isNullOrEmpty()) {
                    item {
                        Text(
                            text = getLocalizedText(data.urlsHeader),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    }
                }
                items(data.urls) { link -> LinkCard(link) }
                if (data.progress.isNotEmpty()) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(data.progress) { config ->
                        DonateTargetCard(
                            config = config,
                            state = targetStates[config.apiUrl] ?: TargetState.Loading,
                            onRefresh = { refreshSingleCard(config) }
                        )
                    }
                }
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
}

@Composable
fun DonateTargetCard(config: DonateCardConfig, state: TargetState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val description = getLocalizedText(config.desc)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Savings, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = config.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = dynamicStringResource(R.string.donate_progress_title), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (description.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = state,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "targetState"
            ) { targetState ->
                when (targetState) {
                    is TargetState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                    is TargetState.Error -> {
                        Column(modifier = Modifier.fillMaxWidth().height(120.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Text(text = targetState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = onRefresh) { Text(dynamicStringResource(R.string.donate_retry)) }
                        }
                    }
                    is TargetState.Success -> {
                        UnifiedProgressLayout(targetState.rub, targetState.usd, targetState.eur)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = state !is TargetState.Loading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }

                Button(
                    onClick = { openUrl(context, config.webUrl) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text(dynamicStringResource(R.string.donate_open_target))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun UnifiedProgressLayout(rub: TargetData, usd: TargetData, eur: TargetData) {
    val progress = if (rub.target > 0) (rub.current / rub.target).toFloat() else 0f
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${(progress * 100).toInt()}%", 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Black, 
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${formatNum(rub.current)} / ${formatNum(rub.target)} ₽", 
                style = MaterialTheme.typography.labelLarge, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            strokeCap = StrokeCap.Round
        )
        
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CurrencyMiniInfo(label = "USD", current = usd.current, target = usd.target, symbol = "$")
            CurrencyMiniInfo(label = "EUR", current = eur.current, target = eur.target, symbol = "€")
        }
    }
}

@Composable
fun CurrencyMiniInfo(label: String, current: Double, target: Double, symbol: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text = "${formatNum(current)} / ${formatNum(target)} $symbol", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MainDonateCard(info: MainDonateInfo) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(IconMapper.getIcon(info.iconKey), null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(16.dp))
            Text(text = getLocalizedText(info.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text = getLocalizedText(info.desc), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            Spacer(Modifier.height(24.dp))
            info.buttons.forEachIndexed { index, btn ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { openUrl(context, btn.url) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (btn.colorType == "primary") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) { Text(getLocalizedText(btn.text)) }
            }
        }
    }
}

@Composable
fun LinkCard(link: SocialLink) {
    val context = LocalContext.current
    Card(
        onClick = { openUrl(context, link.url) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(IconMapper.getIcon(link.iconKey), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = getLocalizedText(link.title), style = MaterialTheme.typography.titleMedium)
                val subtitle = getLocalizedText(link.subtitle)
                if (subtitle.isNotEmpty()) Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.rotate(180f).size(16.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}


private suspend fun fetchDonatePageData(jsonUrl: String): DonatePageData? = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = URL(jsonUrl).openConnection() as HttpURLConnection
        conn.apply { 
            connectTimeout = 10000
            readTimeout = 10000
            useCaches = false 
            setRequestProperty("User-Agent", "Android")
            setRequestProperty("Cache-Control", "no-cache") 
        }
        if (conn.responseCode == 200) {
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(response)
            
            val mdObj = root.optJSONObject("main_donate")
            val mainDonate = mdObj?.let {
                val btns = mutableListOf<DonateButton>()
                val btnsArr = it.optJSONArray("buttons")
                if (btnsArr != null) for (i in 0 until btnsArr.length()) {
                    val b = btnsArr.getJSONObject(i)
                    btns.add(DonateButton(parseMap(b.optJSONObject("text")), b.getString("url"), b.optString("color", "primary")))
                }
                MainDonateInfo(parseMap(it.optJSONObject("title")), parseMap(it.optJSONObject("desc")), it.optString("icon", "info"), btns)
            }

            val urlsList = mutableListOf<SocialLink>()
            val uArr = root.optJSONArray("urls")
            if (uArr != null) for (i in 0 until uArr.length()) {
                val u = uArr.getJSONObject(i)
                urlsList.add(SocialLink(parseMap(u.optJSONObject("title")), parseMap(u.optJSONObject("subtitle")), u.getString("url"), u.optString("icon", "info")))
            }

            val progList = mutableListOf<DonateCardConfig>()
            val pArr = root.optJSONArray("progress")
            if (pArr != null) for (i in 0 until pArr.length()) {
                val p = pArr.getJSONObject(i)
                progList.add(DonateCardConfig(p.getString("title"), p.getString("apiUrl"), p.getString("webUrl"), parseMap(p.optJSONObject("desc"))))
            }

            DonatePageData(mainDonate, parseMap(root.optJSONObject("urls_header")), urlsList, progList)
        } else null
    } catch (e: Exception) { null } finally { conn?.disconnect() }
}

private fun formatNum(num: Double): String {
    return if (num % 1.0 == 0.0) num.toInt().toString() else String.format("%.2f", num)
}

private suspend fun fetchBoostyTarget(apiUrl: String, currency: String): TargetData? = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = URL("$apiUrl?currency=$currency").openConnection() as HttpURLConnection
        conn.apply { connectTimeout = 8000; readTimeout = 8000; setRequestProperty("User-Agent", "Android") }
        if (conn.responseCode == 200) {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            TargetData(json.optDouble("currentSum", 0.0), json.optDouble("targetSum", 0.0), currency)
        } else null
    } catch (e: Exception) { null } finally { conn?.disconnect() }
}

private fun parseMap(json: JSONObject?): Map<String, String> {
    val map = mutableMapOf<String, String>()
    json?.keys()?.forEach { map[it] = json.getString(it) }
    return map
}

private fun openUrl(context: Context, url: String) {
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { }
}