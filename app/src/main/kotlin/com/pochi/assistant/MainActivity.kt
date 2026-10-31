package com.pochi.assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// BLUE-BLACK GRADIENT THEME PALETTE (NO PURPLE, NO EMOJI)
// ============================================================================
private val BgDeepBlack = Color(0xFF020409)
private val BgNavyDark = Color(0xFF060D1E)
private val CardSurface = Color(0xFF091224)
private val CardSurfaceElevated = Color(0xFF0E1B36)
private val BorderSubtleBlue = Color(0xFF1E3A8A)
private val PrimaryRoyalBlue = Color(0xFF1D4ED8)
private val AccentElectricBlue = Color(0xFF2563EB)
private val AccentSkyBlue = Color(0xFF38BDF8)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMutedBlue = Color(0xFF8DA2C0)

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_PICK_FILE = "com.pochi.assistant.ACTION_PICK_FILE"
        const val EXTRA_MIME_TYPE = "extra_mime_type"
    }

    private val pendingPickMime = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PochiPrefs.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handlePickIntent(intent)

        if (Settings.canDrawOverlays(this)) {
            DynamicIslandService.start(this)
        }

        setContent {
            val scheme = darkColorScheme(
                primary = PrimaryRoyalBlue,
                secondary = AccentSkyBlue,
                background = BgDeepBlack,
                surface = CardSurface,
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )
            MaterialTheme(colorScheme = scheme) {
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    val wasFromIsland = pendingPickMime.value != null
                    pendingPickMime.value = null
                    if (uri != null) {
                        val info = PochiJarvisEngine.inspectUri(this@MainActivity, uri)
                        PochiSignals.publishDroppedFile(info)
                        if (wasFromIsland) {
                            PochiSignals.requestExpandIsland()
                            moveTaskToBack(true)
                        }
                    }
                }

                val reqMime by pendingPickMime
                LaunchedEffect(reqMime) {
                    val m = reqMime
                    if (!m.isNullOrBlank()) {
                        runCatching { filePickerLauncher.launch(m) }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDeepBlack
                ) {
                    PochiSimpleCoolApp(
                        onLaunchFilePicker = { mime ->
                            runCatching { filePickerLauncher.launch(mime) }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePickIntent(intent)
    }

    private fun handlePickIntent(intent: Intent?) {
        if (intent?.action == ACTION_PICK_FILE) {
            pendingPickMime.value = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "*/*"
        }
    }
}

@Composable
fun PochiSimpleCoolApp(
    onLaunchFilePicker: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var onboarded by remember { mutableStateOf(PochiPrefs.onboardingDoneFlow.value) }
    var selectedTab by remember { mutableIntStateOf(0) }

    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibility by remember { mutableStateOf(PochiAccessibilityService.instance != null) }
    var hasNotification by remember {
        mutableStateOf(
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.contains(context.packageName) == true
        )
    }
    var batteryIgnored by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canOverlay = Settings.canDrawOverlays(context)
                hasAccessibility = PochiAccessibilityService.instance != null
                hasNotification = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                    ?.contains(context.packageName) == true
                batteryIgnored = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
                if (canOverlay && !DynamicIslandService.isRunningFlow.value) {
                    DynamicIslandService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!onboarded) {
        PochiOnboardingScreen(
            canOverlay = canOverlay,
            hasAccessibility = hasAccessibility,
            hasNotification = hasNotification,
            onFinish = {
                PochiPrefs.setOnboardingDone(context, true)
                onboarded = true
                if (Settings.canDrawOverlays(context)) {
                    DynamicIslandService.start(context)
                }
            }
        )
        return
    }

    // Continuous Ambient Blue-Black Aurora & Particle Field Motion (Read only in DrawScope!)
    val inf = rememberInfiniteTransition(label = "globalAurora")
    val auroraWaveState = inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "auroraWave"
    )
    val particleOrbitState = inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing)),
        label = "particleOrbit"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeepBlack)
    ) {
        // Live Animated Blue-Black Aurora & Floating Particle Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val auroraWave = auroraWaveState.value
            val particleOrbit = particleOrbitState.value

            // Top sweeping Royal-Sky Blue Aurora
            val topCx = w * (0.35f + 0.30f * auroraWave)
            val topCy = h * (0.10f + 0.04f * (1f - auroraWave))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentSkyBlue.copy(alpha = 0.24f),
                        PrimaryRoyalBlue.copy(alpha = 0.36f),
                        Color(0xFF0A2463).copy(alpha = 0.20f),
                        Color.Transparent
                    ),
                    center = Offset(topCx, topCy),
                    radius = w * 0.85f
                ),
                radius = w * 0.85f,
                center = Offset(topCx, topCy)
            )

            // Secondary deep blue ambient wave
            val midCx = w * (0.72f - 0.36f * auroraWave)
            val midCy = h * 0.34f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        PrimaryRoyalBlue.copy(alpha = 0.18f),
                        Color(0xFF07132B).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(midCx, midCy),
                    radius = w * 0.70f
                ),
                radius = w * 0.70f,
                center = Offset(midCx, midCy)
            )

            // Subtle drifting starlight particles
            for (i in 0 until 10) {
                val rad = Math.toRadians(((particleOrbit + i * 36f) % 360f).toDouble())
                val px = (w * (0.12f + (i * 0.085f) % 0.78f)) + cos(rad).toFloat() * 18f
                val py = (h * (0.08f + (i * 0.07f) % 0.55f)) + sin(rad).toFloat() * 14f
                val pAlpha = (0.15f + 0.25f * ((sin(rad).toFloat() + 1f) / 2f))
                drawCircle(
                    color = AccentSkyBlue.copy(alpha = pAlpha),
                    radius = (if (i % 2 == 0) 2.2.dp else 1.4.dp).toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Smooth Animated Tab Transitions
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (fadeIn(tween(260)) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { dir * (it / 8) })
                    .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -dir * (it / 10) })
            },
            label = "tabTransition"
        ) { tab ->
            when (tab) {
                0 -> PochiHomeChatTab(
                    canOverlay = canOverlay,
                    onLaunchFilePicker = onLaunchFilePicker,
                    onOpenSettings = { selectedTab = 2 }
                )
                1 -> PochiMemoryTab()
                else -> PochiSettingsTab(
                    canOverlay = canOverlay,
                    hasAccessibility = hasAccessibility,
                    hasNotification = hasNotification,
                    batteryIgnored = batteryIgnored
                )
            }
        }

        // Floating Split Bottom Navigation Bar + Quick Pochi Notch Orb
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            PochiSplitBottomNav(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onTriggerNotch = {
                    if (Settings.canDrawOverlays(context)) {
                        DynamicIslandService.start(context)
                        PochiSignals.requestExpandIsland()
                    } else {
                        selectedTab = 2
                    }
                }
            )
        }
    }
}

@Composable
private fun PochiSplitBottomNav(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onTriggerNotch: () -> Unit
) {
    val tabs = listOf("Pochi AI", "Memori", "Pengaturan")
    val inf = rememberInfiniteTransition(label = "navOrbSpin")
    val orbAngleState = inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "orbAngle"
    )
    val orbPulseState = inf.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbPulse"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left 3-Tab Pill Container
        Row(
            modifier = Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF050B18).copy(alpha = 0.96f))
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            PrimaryRoyalBlue.copy(alpha = 0.55f),
                            AccentSkyBlue.copy(alpha = 0.35f),
                            PrimaryRoyalBlue.copy(alpha = 0.55f)
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { idx, title ->
                val selected = selectedTab == idx
                val tabScale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.94f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "tabScale$idx"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .scale(tabScale)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(
                                    listOf(PrimaryRoyalBlue, Color(0xFF0A2463))
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.Transparent)
                                )
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelectTab(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (selected) TextWhite else TextMutedBlue,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Right Circular Animated Pochi AI / Notch Trigger Orb
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            PrimaryRoyalBlue.copy(alpha = 0.78f),
                            Color(0xFF08142C),
                            BgDeepBlack
                        )
                    )
                )
                .border(
                    1.2.dp,
                    Brush.sweepGradient(
                        listOf(AccentSkyBlue, PrimaryRoyalBlue, AccentSkyBlue)
                    ),
                    CircleShape
                )
                .clickable { onTriggerNotch() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(26.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension * 0.42f * orbPulseState.value
                rotate(orbAngleState.value, pivot = Offset(cx, cy)) {
                    for (i in 0 until 8) {
                        val rad = Math.toRadians((i * 45).toDouble())
                        drawLine(
                            color = AccentSkyBlue.copy(alpha = 0.90f),
                            start = Offset(cx, cy),
                            end = Offset(cx + cos(rad).toFloat() * r, cy + sin(rad).toFloat() * r),
                            strokeWidth = 1.8.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                drawCircle(
                    color = Color.White,
                    radius = 2.8.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

// ============================================================================
// TAB 1: POCHI AI HOME & LIVE CHAT (NO SCROLLBAR, FRAMELESS CANVAS & RICH MOTION)
// ============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PochiHomeChatTab(
    canOverlay: Boolean,
    onLaunchFilePicker: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val islandRunning by DynamicIslandService.isRunningFlow.collectAsState()
    val droppedFile by PochiSignals.droppedFile.collectAsState()
    val screenApp by PochiSignals.screenApp.collectAsState()

    var isThinking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var promptInput by remember { mutableStateOf("") }
    var lastUserMsg by remember { mutableStateOf("") }
    var pochiReply by remember {
        mutableStateOf("Halo Tuan Andre! Ketik pesan di bawah untuk mencari harga kripto live, membuat grafik interaktif, cek suhu cuaca, jadwal sholat, atau tekan + untuk melampirkan file.")
    }
    var activeCard by remember { mutableStateOf<PochiRichCard?>(null) }
    var currentEmote by remember { mutableStateOf(PochiEmote.SMILING) }

    val currentProvider = remember { PochiPrefs.getProvider(context) }
    val currentModel = remember(currentProvider) { PochiPrefs.getModel(context, currentProvider) }
    val hasApiKey = remember(currentProvider) {
        PochiPrefs.getApiKey(context, currentProvider).isNotBlank() || currentProvider == AiProvider.NINEROUTER
    }

    val chatScroll = rememberScrollState()

    // Autonomous lively expressions on home screen
    LaunchedEffect(isThinking, isListening) {
        while (!isThinking && !isListening) {
            delay(4200L)
            currentEmote = listOf(
                PochiEmote.SMILING,
                PochiEmote.HAPPY,
                PochiEmote.WINK_LEFT,
                PochiEmote.HMM,
                PochiEmote.CALM,
                PochiEmote.EXCITED
            ).random()
        }
    }

    // Automatically inspect dropped file if triggered
    LaunchedEffect(droppedFile) {
        val df = droppedFile ?: return@LaunchedEffect
        isThinking = true
        lastUserMsg = "File: ${df.fileName} (${df.formattedSize})"
        val res = PochiJarvisEngine.processQuery(context, promptInput, df)
        pochiReply = res.text
        activeCard = res.richCard
        currentEmote = res.emote
        PochiSignals.consumeDroppedFile()
        isThinking = false
        delay(80)
        chatScroll.animateScrollTo(chatScroll.maxValue)
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { speechRecognizer?.destroy() } }
    }

    fun sendQuery(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        promptInput = ""
        lastUserMsg = clean
        isThinking = true
        pochiReply = "Memproses permintaan Tuan Andre..."
        scope.launch {
            val res = PochiJarvisEngine.processQuery(context, clean, null)
            pochiReply = res.text
            activeCard = res.richCard
            currentEmote = res.emote
            isThinking = false
            delay(80)
            chatScroll.animateScrollTo(chatScroll.maxValue)
        }
    }

    fun startVoice() {
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() { isListening = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val txt = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (txt.isNotBlank()) sendQuery(txt)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        runCatching { recognizer.startListening(intent) }.onFailure { isListening = false }
    }

    val inf = rememberInfiniteTransition(label = "pochiCoreMotion")
    val auraPulseState = inf.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "auraPulse"
    )
    val ringSpinOuterState = inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing)),
        label = "ringSpinOuter"
    )
    val ringSpinInnerState = inf.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing)),
        label = "ringSpinInner"
    )
    val wavePhaseState = inf.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "wavePhase"
    )
    val sendBtnScale by animateFloatAsState(
        targetValue = if (promptInput.isNotBlank()) 1.07f else 0.96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "sendScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 78.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Sleek Top Header Bar: Brand + Provider Badge + Notch Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.clickable { onOpenSettings() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = "POCHI AI",
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .graphicsLayer {
                                val s = auraPulseState.value
                                scaleX = s
                                scaleY = s
                            }
                            .clip(CircleShape)
                            .background(AccentSkyBlue)
                    )
                }
                Text(
                    text = if (hasApiKey) "${currentProvider.label} • $currentModel" else "Mode Live API • Ketuk untuk atur API Key",
                    color = AccentSkyBlue,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Buka Notch Quick Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CardSurfaceElevated)
                        .border(1.dp, BorderSubtleBlue.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .clickable {
                            if (canOverlay) {
                                DynamicIslandService.start(context)
                                PochiSignals.requestExpandIsland()
                            } else {
                                onOpenSettings()
                            }
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Buka Notch",
                        color = AccentSkyBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Dynamic Notch ON/OFF Pill Switch
                val notchBg by animateColorAsState(
                    targetValue = if (islandRunning) PrimaryRoyalBlue else CardSurface,
                    animationSpec = tween(250),
                    label = "notchSwitchBg"
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(notchBg)
                        .border(
                            1.dp,
                            if (islandRunning) AccentSkyBlue else BorderSubtleBlue,
                            RoundedCornerShape(50)
                        )
                        .clickable {
                            if (!Settings.canDrawOverlays(context)) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } else if (islandRunning) {
                                DynamicIslandService.stop(context)
                            } else {
                                DynamicIslandService.start(context)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .graphicsLayer {
                                val s = if (islandRunning) auraPulseState.value else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .clip(CircleShape)
                            .background(if (islandRunning) AccentSkyBlue else TextMutedBlue)
                    )
                    Text(
                        text = if (islandRunning) "ON" else "OFF",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 2. Interactive Orbital Pochi AI Core (With Multi-Ring Motion & Squishy Touch Physics)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier.size(112.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Multi-ring Counter-Rotating Orbital Energy Canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val maxR = size.minDimension / 2f
                        val pulse = auraPulseState.value

                        // Breathing Radial Blue Aura
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    PrimaryRoyalBlue.copy(alpha = 0.42f * pulse),
                                    AccentSkyBlue.copy(alpha = 0.16f * pulse),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = maxR * pulse
                            ),
                            radius = maxR * pulse,
                            center = Offset(cx, cy)
                        )

                        // Outer Rotating Segmented Ring
                        rotate(ringSpinOuterState.value, pivot = Offset(cx, cy)) {
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        PrimaryRoyalBlue.copy(alpha = 0.75f),
                                        AccentSkyBlue.copy(alpha = 0.90f),
                                        Color.Transparent,
                                        PrimaryRoyalBlue.copy(alpha = 0.55f),
                                        Color.Transparent
                                    )
                                ),
                                radius = maxR * 0.88f,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // Inner Counter-Rotating Orbital Ring
                        rotate(ringSpinInnerState.value, pivot = Offset(cx, cy)) {
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        AccentSkyBlue.copy(alpha = 0.65f),
                                        Color.Transparent,
                                        PrimaryRoyalBlue.copy(alpha = 0.70f),
                                        Color.Transparent
                                    )
                                ),
                                radius = maxR * 0.74f,
                                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Core Matte-Black Sphere Hosting Pochi's Living Eyes
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(0xFF0A1936),
                                        Color(0xFF040814),
                                        BgDeepBlack
                                    )
                                )
                            )
                            .border(1.5.dp, AccentSkyBlue.copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        PochiMascot(
                            state = if (isThinking) BotState.WORKING else BotState.IDLE,
                            emoteOverride = if (isThinking) PochiEmote.THINKING else if (isListening) PochiEmote.SURPRISED else currentEmote,
                            size = 46.dp,
                            showBlackBacking = false,
                            onPoke = {
                                currentEmote = listOf(
                                    PochiEmote.HAPPY,
                                    PochiEmote.EXCITED,
                                    PochiEmote.LOVE,
                                    PochiEmote.WINK_LEFT,
                                    PochiEmote.LAUGHING
                                ).random()
                            },
                            onPet = {
                                currentEmote = PochiEmote.LOVE
                            }
                        )
                    }
                }

                // Live Animated Status Subtitle under Pochi Orb
                val coreStatusText = when {
                    isThinking -> "Thinking & menganalisis data untuk Tuan Andre..."
                    isListening -> "Mendengarkan suara Tuan Andre..."
                    screenApp.isNotBlank() -> "Terhubung • Konteks layar: $screenApp"
                    else -> "Ketuk mata Pochi untuk berinteraksi • Siap menerima perintah"
                }
                AnimatedContent(
                    targetState = coreStatusText,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 2 })
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "coreStatusAnim"
                ) { label ->
                    Text(
                        text = label,
                        color = if (isThinking || isListening) AccentSkyBlue else TextMutedBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Frameless, Scrollbar-Free Chat & Generative Visual Card Stream
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(chatScroll)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(
                    visible = lastUserMsg.isNotBlank(),
                    enter = fadeIn(tween(240)) + slideInVertically { it / 2 } + scaleIn(initialScale = 0.92f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(PrimaryRoyalBlue, Color(0xFF0A2463))
                                    )
                                )
                                .border(
                                    1.dp,
                                    AccentSkyBlue.copy(alpha = 0.35f),
                                    RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                                )
                                .padding(horizontal = 15.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = lastUserMsg,
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Pochi Reply Bubble with Smooth Content Transition & Live Sine Wave When Thinking
                AnimatedContent(
                    targetState = pochiReply,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInVertically { it / 3 } + scaleIn(initialScale = 0.96f))
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "pochiReplyBubble"
                ) { reply ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        CardSurfaceElevated,
                                        CardSurface
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(PrimaryRoyalBlue.copy(alpha = 0.45f), AccentSkyBlue.copy(alpha = 0.25f))
                                ),
                                RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                            )
                            .padding(15.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .graphicsLayer {
                                            val s = auraPulseState.value
                                            scaleX = s
                                            scaleY = s
                                        }
                                        .clip(CircleShape)
                                        .background(AccentSkyBlue)
                                )
                                Text(
                                    text = "POCHI",
                                    color = AccentSkyBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Text(
                                text = reply,
                                color = Color(0xFFEFF6FF),
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )

                            if (isThinking || isListening) {
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(18.dp)
                                ) {
                                    val w = size.width
                                    val cy = size.height / 2f
                                    val wp = wavePhaseState.value
                                    val path = Path().apply {
                                        moveTo(0f, cy)
                                        val steps = 36
                                        for (i in 1..steps) {
                                            val x = (w / steps) * i
                                            val y = cy + sin((i * 0.35f + wp).toDouble()).toFloat() * (size.height * 0.38f)
                                            lineTo(x, y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        brush = Brush.horizontalGradient(
                                            listOf(Color.Transparent, AccentSkyBlue, PrimaryRoyalBlue, Color.Transparent)
                                        ),
                                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                            }
                        }
                    }
                }

                // Generative Visual Card (Crypto / Chart / Weather / Prayer / FileDrop / System / Knowledge)
                AnimatedVisibility(
                    visible = activeCard != null,
                    enter = fadeIn(tween(280)) + expandVertically() + scaleIn(initialScale = 0.94f),
                    exit = fadeOut(tween(180)) + shrinkVertically()
                ) {
                    activeCard?.let { card ->
                        PochiRichCardView(
                            card = card,
                            compact = false
                        )
                    }
                }
            }
        }

        // 4. Pinned Bottom Chat Input Bar with Animated Border & Spring Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF060D1D).copy(alpha = 0.96f))
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            PrimaryRoyalBlue.copy(alpha = 0.65f),
                            AccentSkyBlue.copy(alpha = 0.50f),
                            PrimaryRoyalBlue.copy(alpha = 0.65f)
                        )
                    ),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "+" File Drop Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CardSurfaceElevated)
                    .border(1.dp, BorderSubtleBlue, CircleShape)
                    .clickable { onLaunchFilePicker("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Lampirkan File",
                    tint = TextWhite,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (promptInput.isEmpty()) {
                    Text(
                        text = "Ketik pesan ke Pochi...",
                        color = TextMutedBlue,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    textStyle = TextStyle(
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(AccentSkyBlue),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendQuery(promptInput) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Mic Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        val s = if (isListening) auraPulseState.value else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .background(if (isListening) AccentSkyBlue.copy(alpha = 0.20f) else Color.Transparent)
                    .clickable { startVoice() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                    contentDescription = "Suara",
                    tint = if (isListening) AccentSkyBlue else TextMutedBlue,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Send Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .scale(sendBtnScale)
                    .clip(CircleShape)
                    .background(
                        if (promptInput.isNotBlank()) {
                            Brush.linearGradient(listOf(TextWhite, Color(0xFFBAE6FD)))
                        } else {
                            Brush.linearGradient(listOf(TextWhite, TextWhite))
                        }
                    )
                    .clickable { sendQuery(promptInput) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(15.dp)) {
                    val w = size.width
                    val h = size.height
                    val p = Path().apply {
                        moveTo(w * 0.12f, h * 0.48f)
                        lineTo(w * 0.88f, h * 0.14f)
                        lineTo(w * 0.58f, h * 0.88f)
                        lineTo(w * 0.44f, h * 0.56f)
                        close()
                    }
                    drawPath(
                        path = p,
                        color = BgDeepBlack,
                        style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

// ============================================================================
// TAB 2: POCHI MEMORY & CONTEXT (NO SCROLLBAR, ANIMATED TELEMETRY & PILLS)
// ============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PochiMemoryTab() {
    val context = LocalContext.current
    val db = remember { PochiDb.getInstance(context) }
    var memories by remember { mutableStateOf(db.getAllMemories().take(40)) }
    var newMemoryText by remember { mutableStateOf("") }

    val selectedCity by PochiPrefs.cityFlow.collectAsState()
    val screenApp by PochiSignals.screenApp.collectAsState()

    val bm = remember { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    val batteryPct = remember { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(1, 100) ?: 88 }

    val am = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
    val ramUsedGb = remember {
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val used = ((info.totalMem - info.availMem).coerceAtLeast(1024L * 1024L * 1024L)) / (1024.0 * 1024.0 * 1024.0)
        String.format(Locale.US, "%.1f GB", used)
    }

    val scroll = rememberScrollState()

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 82.dp)
                .verticalScroll(scroll)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Memori & Konteks Pochi",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // 1. Telemetry & Context Summary Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SimpleStatCard(
                    label = "BATERAI & RAM",
                    value = "$batteryPct% • $ramUsedGb",
                    modifier = Modifier.weight(1f)
                )
                SimpleStatCard(
                    label = "LAYAR AKTIF",
                    value = screenApp.ifBlank { "Pochi Home" },
                    modifier = Modifier.weight(1f)
                )
            }

            // 2. City Selector Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface.copy(alpha = 0.92f))
                    .border(1.dp, BorderSubtleBlue.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Kota Jadwal Sholat & Cuaca Live",
                    color = AccentSkyBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrayerScheduleHelper.supportedCities.take(4).forEach { city ->
                        val active = city.equals(selectedCity, ignoreCase = true)
                        val pillScale by animateFloatAsState(
                            targetValue = if (active) 1.03f else 0.97f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "cityScale$city"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(pillScale)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue))
                                    else Brush.horizontalGradient(listOf(CardSurfaceElevated, CardSurfaceElevated))
                                )
                                .clickable { PochiPrefs.saveCity(context, city) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = city,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrayerScheduleHelper.supportedCities.drop(4).take(4).forEach { city ->
                        val active = city.equals(selectedCity, ignoreCase = true)
                        val pillScale by animateFloatAsState(
                            targetValue = if (active) 1.03f else 0.97f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "cityScale2$city"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(pillScale)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue))
                                    else Brush.horizontalGradient(listOf(CardSurfaceElevated, CardSurfaceElevated))
                                )
                                .clickable { PochiPrefs.saveCity(context, city) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = city,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 3. Add Permanent Memory Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface.copy(alpha = 0.92f))
                    .border(1.dp, BorderSubtleBlue.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Tambah Ingatan Baru untuk Tuan Andre",
                    color = AccentSkyBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDeepBlack)
                            .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (newMemoryText.isEmpty()) {
                            Text(
                                text = "Misal: Saya suka kopi americano...",
                                color = TextMutedBlue,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = newMemoryText,
                            onValueChange = { newMemoryText = it },
                            textStyle = TextStyle(color = TextWhite, fontSize = 12.sp),
                            cursorBrush = SolidColor(AccentSkyBlue),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue)))
                            .clickable {
                                if (newMemoryText.isNotBlank()) {
                                    db.saveMemory("preferensi", newMemoryText.trim())
                                    newMemoryText = ""
                                    memories = db.getAllMemories().take(40)
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Simpan",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Saved Memories List
            Text(
                text = "Daftar Memori Tersimpan (${memories.size})",
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardSurface.copy(alpha = 0.92f))
                        .border(1.dp, BorderSubtleBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Pochi selalu mengingat panggilan kehormatan 'Tuan Andre' secara permanen.",
                        color = TextMutedBlue,
                        fontSize = 12.sp
                    )
                }
            } else {
                memories.forEach { mem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardSurface.copy(alpha = 0.92f))
                            .border(1.dp, BorderSubtleBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mem.fact,
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = mem.category.uppercase(Locale.US),
                                color = AccentSkyBlue,
                                fontSize = 10.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Hapus",
                            tint = TextMutedBlue,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    db.deleteMemory(mem.id)
                                    memories = db.getAllMemories().take(40)
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface.copy(alpha = 0.92f))
            .border(1.dp, BorderSubtleBlue.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = AccentSkyBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = TextWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// TAB 3: POCHI SETTINGS (9ROUTER, GEMINI, OPENROUTER, OPENCODE, INCEPTIONLABS)
// ============================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PochiSettingsTab(
    canOverlay: Boolean,
    hasAccessibility: Boolean,
    hasNotification: Boolean,
    batteryIgnored: Boolean
) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf(PochiPrefs.getProvider(context)) }
    var apiKeyInput by remember(selectedProvider) {
        mutableStateOf(PochiPrefs.getApiKey(context, selectedProvider))
    }
    var modelInput by remember(selectedProvider) {
        mutableStateOf(PochiPrefs.getModel(context, selectedProvider))
    }
    var nineRouterUrlInput by remember {
        mutableStateOf(PochiPrefs.getNineRouterUrl(context))
    }
    var showApiKey by remember { mutableStateOf(false) }
    var savedBanner by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()

    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 82.dp)
                .verticalScroll(scroll)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Pengaturan Pochi",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )

            // 1. AI Brain & API Key Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface.copy(alpha = 0.92f))
                    .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(16.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Otak AI & Kunci API",
                    color = AccentSkyBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // Row 1 of Providers (Gemini, 9Router, OpenRouter)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiProvider.entries.take(3).forEach { prov ->
                        val active = prov == selectedProvider
                        val pillScale by animateFloatAsState(
                            targetValue = if (active) 1.03f else 0.97f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "provScale1_${prov.name}"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(pillScale)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue))
                                    else Brush.horizontalGradient(listOf(CardSurfaceElevated, CardSurfaceElevated))
                                )
                                .clickable {
                                    selectedProvider = prov
                                    PochiPrefs.setProvider(context, prov)
                                    savedBanner = false
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = prov.label,
                                color = if (active) TextWhite else TextMutedBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Row 2 of Providers (OpenCode, InceptionLabs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiProvider.entries.drop(3).forEach { prov ->
                        val active = prov == selectedProvider
                        val pillScale by animateFloatAsState(
                            targetValue = if (active) 1.03f else 0.97f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "provScale2_${prov.name}"
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .scale(pillScale)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue))
                                    else Brush.horizontalGradient(listOf(CardSurfaceElevated, CardSurfaceElevated))
                                )
                                .clickable {
                                    selectedProvider = prov
                                    PochiPrefs.setProvider(context, prov)
                                    savedBanner = false
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = prov.label,
                                color = if (active) TextWhite else TextMutedBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 9Router Base URL field when 9Router is selected
                AnimatedVisibility(
                    visible = selectedProvider == AiProvider.NINEROUTER,
                    enter = fadeIn(tween(200)) + expandVertically(),
                    exit = fadeOut(tween(150)) + shrinkVertically()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Base URL 9Router (Default: http://localhost:20128/v1)",
                            color = TextMutedBlue,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgDeepBlack)
                                .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                        ) {
                            BasicTextField(
                                value = nineRouterUrlInput,
                                onValueChange = {
                                    nineRouterUrlInput = it
                                    savedBanner = false
                                },
                                textStyle = TextStyle(
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                cursorBrush = SolidColor(AccentSkyBlue),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "API Key (${selectedProvider.label})",
                        color = TextMutedBlue,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDeepBlack)
                            .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (apiKeyInput.isEmpty()) {
                                Text(
                                    text = selectedProvider.keyHint,
                                    color = TextMutedBlue.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            BasicTextField(
                                value = apiKeyInput,
                                onValueChange = {
                                    apiKeyInput = it
                                    savedBanner = false
                                },
                                textStyle = TextStyle(
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                cursorBrush = SolidColor(AccentSkyBlue),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Icon(
                            imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Tampilkan Key",
                            tint = TextMutedBlue,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { showApiKey = !showApiKey }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Model (${selectedProvider.defaultModel})",
                        color = TextMutedBlue,
                        fontSize = 11.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgDeepBlack)
                            .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                    ) {
                        BasicTextField(
                            value = modelInput,
                            onValueChange = {
                                modelInput = it
                                savedBanner = false
                            },
                            textStyle = TextStyle(color = TextWhite, fontSize = 13.sp),
                            cursorBrush = SolidColor(AccentSkyBlue),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue)))
                        .clickable {
                            PochiPrefs.setProvider(context, selectedProvider)
                            PochiPrefs.setApiKey(context, selectedProvider, apiKeyInput.trim())
                            PochiPrefs.saveModel(context, selectedProvider, modelInput.trim().ifBlank { selectedProvider.defaultModel })
                            if (selectedProvider == AiProvider.NINEROUTER) {
                                PochiPrefs.saveNineRouterUrl(context, nineRouterUrlInput)
                            }
                            savedBanner = true
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (savedBanner) "Tersimpan untuk Tuan Andre" else "Simpan API Key & Model",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 2. System Permissions Checklist
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface.copy(alpha = 0.92f))
                    .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Izin Sistem Android",
                    color = AccentSkyBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                DarkPermissionRow(
                    title = "Overlay Dynamic Notch",
                    subtitle = "Menampilkan Notch Pochi di atas layar",
                    granted = canOverlay
                ) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }

                DarkPermissionRow(
                    title = "Aksesibilitas Konteks Layar",
                    subtitle = "Mendeteksi aktivitas layar secara real-time",
                    granted = hasAccessibility
                ) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }

                DarkPermissionRow(
                    title = "Pendengar Media & Notifikasi",
                    subtitle = "Sinkronisasi musik & pesan masuk",
                    granted = hasNotification
                ) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }

                DarkPermissionRow(
                    title = "Bebas Optimasi Baterai",
                    subtitle = "Menjaga Pochi tetap aktif di latar belakang",
                    granted = batteryIgnored
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DarkPermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextMutedBlue,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (granted) PrimaryRoyalBlue.copy(alpha = 0.28f) else Color(0xFFEF4444).copy(alpha = 0.20f))
                .border(
                    1.dp,
                    if (granted) AccentSkyBlue.copy(alpha = 0.6f) else Color(0xFFEF4444).copy(alpha = 0.6f),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (granted) "AKTIF" else "IZINKAN",
                color = if (granted) AccentSkyBlue else Color(0xFFF87171),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ============================================================================
// ONBOARDING SCREEN FOR TUAN ANDRE
// ============================================================================
@Composable
private fun PochiOnboardingScreen(
    canOverlay: Boolean,
    hasAccessibility: Boolean,
    hasNotification: Boolean,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeepBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(PrimaryRoyalBlue, BgNavyDark, BgDeepBlack)))
                    .border(1.5.dp, AccentSkyBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                PochiMascot(
                    state = BotState.IDLE,
                    emoteOverride = PochiEmote.EXCITED,
                    size = 64.dp,
                    showBlackBacking = false
                )
            }

            Text(
                text = "Selamat Datang, Tuan Andre",
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Pochi siap menjadi AI Agent di tengah atas layar HP Tuan Andre (dan otomatis menyesuaikan di pinggir layar saat miring).",
                color = TextMutedBlue,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            DarkPermissionRow(
                title = "1. Izin Overlay Notch (Wajib)",
                subtitle = "Agar Notch Pochi tampil di atas layar",
                granted = canOverlay
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }

            DarkPermissionRow(
                title = "2. Aksesibilitas Konteks Layar",
                subtitle = "Agar mata Pochi bereaksi terhadap aktivitas layar",
                granted = hasAccessibility
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            DarkPermissionRow(
                title = "3. Pendengar Media & Notifikasi",
                subtitle = "Deteksi musik & pesan secara real-time",
                granted = hasNotification
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue)))
                    .clickable { onFinish() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Mulai Pochi Sekarang",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
