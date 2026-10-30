package com.pochi.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {

    private val pickFileTrigger = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PochiPrefs.init(applicationContext)
        enableEdgeToEdge()
        checkPickFileIntent(intent)

        setContent {
            PochiLuxuryApp(
                pickFileSignal = pickFileTrigger.value,
                onPickFileConsumed = { pickFileTrigger.value = 0L }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkPickFileIntent(intent)
    }

    private fun checkPickFileIntent(intent: Intent?) {
        if (intent?.action == "com.pochi.assistant.ACTION_PICK_FILE") {
            pickFileTrigger.value = System.currentTimeMillis()
        }
    }
}

enum class PochiNavTab {
    WALLET,
    JARVIS,
    ACTIVITY,
    SETTINGS
}

data class UiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "pochi"
    val text: String,
    val richCard: PochiRichCard? = null,
    val emote: PochiEmote? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isUser: Boolean get() = sender == "user"
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
fun PochiLuxuryApp(
    pickFileSignal: Long = 0L,
    onPickFileConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val isOnboardingDone by PochiPrefs.onboardingDoneFlow.collectAsState()

    // Default to JARVIS center screen (matching the centerpiece of the reference design)
    var activeTab by remember { mutableStateOf(PochiNavTab.JARVIS) }

    // Shared universal file picker for both App and Notch
    val globalFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val info = PochiJarvisEngine.inspectUri(context, uri)
            PochiSignals.publishDroppedFile(info)
            activeTab = PochiNavTab.JARVIS
        }
    }

    LaunchedEffect(pickFileSignal) {
        if (pickFileSignal > 0L) {
            onPickFileConsumed()
            globalFileLauncher.launch("*/*")
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF4F4F7) // Crisp off-white/platinum base matching reference image
    ) {
        if (!isOnboardingDone) {
            PochiOnboardingScreen(
                onComplete = {
                    PochiPrefs.setOnboardingDone(context, true)
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                Crossfade(
                    targetState = activeTab,
                    animationSpec = tween(220),
                    label = "screenCrossfade"
                ) { tab ->
                    when (tab) {
                        PochiNavTab.WALLET -> WalletHubScreen(
                            onJumpToJarvis = { query ->
                                activeTab = PochiNavTab.JARVIS
                            },
                            onSeeAllActivity = {
                                activeTab = PochiNavTab.ACTIVITY
                            }
                        )
                        PochiNavTab.JARVIS -> JarvisCenterScreen(
                            onPickFile = { globalFileLauncher.launch("*/*") }
                        )
                        PochiNavTab.ACTIVITY -> ActivityAnalyticsScreen(
                            onJumpToJarvis = {
                                activeTab = PochiNavTab.JARVIS
                            }
                        )
                        PochiNavTab.SETTINGS -> SettingsAndMemoryScreen(
                            onPickFile = { globalFileLauncher.launch("*/*") }
                        )
                    }
                }

                // Signature Dual-Pill Floating Black Navigation Dock (Bottom Center)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 14.dp, start = 20.dp, end = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PochiReferenceFloatingDock(
                        activeTab = activeTab,
                        onSelectTab = { tab ->
                            activeTab = tab
                            PochiSignals.playTapHaptic(context)
                        },
                        onSparkleAction = {
                            PochiSignals.playTapHaptic(context)
                            if (activeTab != PochiNavTab.JARVIS) {
                                activeTab = PochiNavTab.JARVIS
                            } else {
                                toggleDynamicIsland(context, true)
                                PochiSignals.requestExpandIsland()
                            }
                        }
                    )
                }
            }
        }
    }
}

// ============================================================================
// SIGNATURE DUAL-PILL FLOATING DOCK (EXACT MATCH TO REFERENCE IMAGE)
// ============================================================================
@Composable
fun PochiReferenceFloatingDock(
    activeTab: PochiNavTab,
    onSelectTab: (PochiNavTab) -> Unit,
    onSparkleAction: () -> Unit
) {
    val isJarvisTab = activeTab == PochiNavTab.JARVIS

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Left Black Capsule with 4 Custom Vector Icons
        Row(
            modifier = Modifier
                .height(58.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF08080C))
                .border(1.dp, Color(0xFF1F1F2A), RoundedCornerShape(50))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PochiNavTab.entries.forEach { tab ->
                val isSelected = activeTab == tab
                val pillBg by animateColorAsState(
                    targetValue = if (isSelected) Color(0xFF23232E) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "navItemBg"
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(pillBg)
                        .clickable { onSelectTab(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        val w = size.width
                        val h = size.height
                        val iconColor = if (isSelected) Color.White else Color(0xFF8E8E9E)

                        when (tab) {
                            PochiNavTab.WALLET -> {
                                // Minimalist House Vector (matches reference leftmost icon)
                                val house = Path().apply {
                                    moveTo(w * 0.5f, h * 0.12f)
                                    lineTo(w * 0.12f, h * 0.46f)
                                    lineTo(w * 0.12f, h * 0.88f)
                                    lineTo(w * 0.38f, h * 0.88f)
                                    lineTo(w * 0.38f, h * 0.60f)
                                    lineTo(w * 0.62f, h * 0.60f)
                                    lineTo(w * 0.62f, h * 0.88f)
                                    lineTo(w * 0.88f, h * 0.88f)
                                    lineTo(w * 0.88f, h * 0.46f)
                                    close()
                                }
                                drawPath(house, color = iconColor)
                            }
                            PochiNavTab.JARVIS -> {
                                // Concentric Target / AI Ring Vector (matches reference 2nd icon)
                                drawCircle(
                                    color = iconColor,
                                    radius = w * 0.42f,
                                    center = center,
                                    style = Stroke(width = 1.8.dp.toPx())
                                )
                                drawCircle(
                                    color = iconColor,
                                    radius = w * 0.18f,
                                    center = center
                                )
                            }
                            PochiNavTab.ACTIVITY -> {
                                // Minimalist Person / Activity Silhouette (matches reference 3rd icon)
                                drawCircle(
                                    color = iconColor,
                                    radius = w * 0.20f,
                                    center = Offset(w * 0.5f, h * 0.28f)
                                )
                                val shoulders = Path().apply {
                                    moveTo(w * 0.18f, h * 0.86f)
                                    quadraticBezierTo(w * 0.5f, h * 0.52f, w * 0.82f, h * 0.86f)
                                    close()
                                }
                                drawPath(shoulders, color = iconColor)
                            }
                            PochiNavTab.SETTINGS -> {
                                // Three Vertical Dots (matches reference 4th icon)
                                val r = 1.8.dp.toPx()
                                drawCircle(color = iconColor, radius = r, center = Offset(w * 0.5f, h * 0.22f))
                                drawCircle(color = iconColor, radius = r, center = Offset(w * 0.5f, h * 0.50f))
                                drawCircle(color = iconColor, radius = r, center = Offset(w * 0.5f, h * 0.78f))
                            }
                        }
                    }
                }
            }
        }

        // Right Companion AI Sparkle / Action Pill (Matches reference image right dock button!)
        Box(
            modifier = Modifier
                .height(58.dp)
                .width(if (isJarvisTab) 72.dp else 54.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF08080C))
                .border(1.dp, Color(0xFF261838), RoundedCornerShape(50))
                .clickable { onSparkleAction() },
            contentAlignment = Alignment.Center
        ) {
            // Subtle purple radial aura inside the companion pill
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7B2CBF).copy(alpha = if (isJarvisTab) 0.48f else 0.22f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.6f
                    ),
                    radius = size.minDimension * 0.6f,
                    center = center
                )
            }

            // 8-Ray AI Starburst Vector
            Canvas(modifier = Modifier.size(20.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension * 0.44f
                for (i in 0 until 8) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val ex = cx + cos(angle).toFloat() * r
                    val ey = cy + sin(angle).toFloat() * r
                    drawLine(
                        color = Color(0xFFC77DFF),
                        start = Offset(cx, cy),
                        end = Offset(ex, ey),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(cx, cy))
            }
        }
    }
}

// ============================================================================
// SCREEN 2 (CENTERPIECE): POCHI JARVIS AI CENTER (1:1 REFERENCE CENTER SCREEN)
// ============================================================================
@Composable
fun JarvisCenterScreen(
    onPickFile: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val userName by PochiPrefs.userNameFlow.collectAsState()
    val focusSessions by PochiPrefs.focusSessionsFlow.collectAsState()
    val droppedFile by PochiSignals.droppedFile.collectAsState()
    val screenApp by PochiSignals.screenApp.collectAsState()

    val db = remember { PochiDb.getInstance(context) }
    var memoriesCount by remember { mutableIntStateOf(db.getAllMemories().size) }

    var islandEnabled by remember { mutableStateOf(DynamicIslandService.isRunning.value) }
    var contextAwarenessEnabled by remember { mutableStateOf(true) }
    var showMascotInsideOrb by remember { mutableStateOf(false) }

    var isThinking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var orbEmote by remember { mutableStateOf(PochiEmote.SMILING) }
    var inputText by remember { mutableStateOf("") }

    // Default initial conversation & rich card
    var activeQuestion by remember { mutableStateOf("Which subscriptions & live assets am I using?") }
    var activeTimestamp by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    var activeReplyText by remember {
        mutableStateOf("Siap Tuan Andre! Saya memantau pasar kripto live, grafik analitik, cuaca, jadwal sholat, dan siap menganalisis file apapun yang Tuan Andre drop ke sini.")
    }
    var activeRichCard by remember { mutableStateOf<PochiRichCard?>(null) }

    fun sendToJarvis(query: String, file: DroppedFileInfo? = null) {
        val clean = query.trim()
        if (clean.isBlank() && file == null) return
        if (isThinking) return

        activeQuestion = if (file != null) {
            if (clean.isNotBlank()) "📎 ${file.fileName} • $clean" else "📎 Analyze dropped file: ${file.fileName}"
        } else {
            clean
        }
        activeTimestamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        inputText = ""
        isThinking = true
        orbEmote = PochiEmote.THINKING

        coroutineScope.launch {
            db.insertChatMessage("user", activeQuestion)
            val bundle = PochiJarvisEngine.processQuery(context, clean, file)
            activeReplyText = if (!bundle.actionFeedback.isNullOrBlank()) {
                "${bundle.text}\n[${bundle.actionFeedback}]"
            } else {
                bundle.text
            }
            activeRichCard = bundle.richCard
            orbEmote = bundle.emote
            isThinking = false
            memoriesCount = db.getAllMemories().size
            db.insertChatMessage("pochi", activeReplyText)
            PochiSignals.playSuccessHaptic(context)
        }
    }

    // Consume dropped/picked file automatically
    LaunchedEffect(droppedFile) {
        val f = droppedFile ?: return@LaunchedEffect
        PochiSignals.consumeDroppedFile()
        sendToJarvis(inputText, f)
    }

    // Speech recognition setup
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() { isListening = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    sendToJarvis(spoken)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                    inputText = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            runCatching { speechRecognizer?.destroy() }
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer.startListening(intent)
        }
    }

    val inf = rememberInfiniteTransition(label = "auroraTransition")
    val starRotation by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (isThinking) 3000 else 16000, easing = LinearEasing)),
        label = "starRot"
    )
    val auroraWave by inf.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "auroraWave"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F4F7))
    ) {
        // Upper Deep-Black Container with Rounded Bottom Corners (Matches Reference Center Screen)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 84.dp)
                .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                .background(Color(0xFF050507))
        ) {
            // 1. TOP PURPLE AURORA NEBULA GLOW CANVAS (Exact match to center screen top glow!)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(310.dp)
            ) {
                val w = size.width
                val h = size.height

                // Outer violet sweep
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7B2CBF).copy(alpha = 0.72f),
                            Color(0xFF5A189A).copy(alpha = 0.42f),
                            Color(0xFF240046).copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.18f),
                        radius = w * 0.72f * auroraWave
                    ),
                    radius = w * 0.72f * auroraWave,
                    center = Offset(w * 0.5f, h * 0.18f)
                )

                // Bright lilac core beam
                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFC77DFF).copy(alpha = 0.58f),
                            Color(0xFF9D4EDD).copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.52f, h * 0.34f),
                        radius = w * 0.48f
                    ),
                    topLeft = Offset(w * 0.08f, h * 0.05f),
                    size = Size(w * 0.84f, h * 0.58f)
                )
            }

            // Main Scrollable Content inside Black Container
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- TOP CENTER ORB SECTION ("AI" + Starburst / PochiMascot + "Thinking...") ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(92.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Superscript "AI" label at top-right of the orb
                        Text(
                            text = "AI",
                            color = Color(0xFFE0AAFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 4.dp)
                        )

                        // Glowing Frosted Purple Orb
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF9D4EDD).copy(alpha = 0.48f),
                                            Color(0xFF3C096C).copy(alpha = 0.75f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.5.dp,
                                    brush = Brush.linearGradient(
                                        listOf(Color(0xFFE0AAFF), Color(0xFF7B2CBF))
                                    ),
                                    shape = CircleShape
                                )
                                .clickable {
                                    showMascotInsideOrb = !showMascotInsideOrb
                                    PochiSignals.playTapHaptic(context)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (showMascotInsideOrb) {
                                PochiMascot(
                                    state = if (isThinking) BotState.WORKING else BotState.IDLE,
                                    emoteOverride = orbEmote,
                                    size = 38.dp,
                                    showBlackBacking = false
                                )
                            } else {
                                // Rotating 10-ray AI Starburst + subtle Pochi eye wink
                                Canvas(modifier = Modifier.size(30.dp)) {
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val r = size.minDimension * 0.46f
                                    rotate(starRotation, pivot = Offset(cx, cy)) {
                                        for (i in 0 until 10) {
                                            val rad = Math.toRadians((i * 36).toDouble())
                                            val innerR = r * 0.22f
                                            drawLine(
                                                color = Color.White.copy(alpha = 0.92f),
                                                start = Offset(
                                                    cx + cos(rad).toFloat() * innerR,
                                                    cy + sin(rad).toFloat() * innerR
                                                ),
                                                end = Offset(
                                                    cx + cos(rad).toFloat() * r,
                                                    cy + sin(rad).toFloat() * r
                                                ),
                                                strokeWidth = 1.8.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = when {
                            isThinking -> "Thinking..."
                            isListening -> "Listening to $userName..."
                            screenApp.isNotBlank() -> "Jarvis Active • $screenApp"
                            else -> "Thinking... • Ready for $userName"
                        },
                        color = Color(0xFFD8D8E8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // --- MIDDLE: 2 SIDE-BY-SIDE BENTO TOGGLE CARDS + PROMPT CARD ---
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Bento Card ("Switch to Monthly / 10 hours / N icon + toggle")
                        JarvisBentoToggleCard(
                            modifier = Modifier.weight(1f),
                            subtitle = "Notch Jarvis Mode",
                            bigValue = "${(focusSessions.coerceAtLeast(2) * 5)}",
                            unitText = "hours",
                            badgeColor = Color(0xFFE50914),
                            badgeLetter = "N",
                            checked = islandEnabled,
                            onCheckedChange = { enabled ->
                                islandEnabled = enabled
                                toggleDynamicIsland(context, enabled)
                                PochiSignals.playTapHaptic(context)
                            }
                        )

                        // Right Bento Card ("Switch to Annual / $24 Save/year / Spotify icon + toggle")
                        JarvisBentoToggleCard(
                            modifier = Modifier.weight(1f),
                            subtitle = "Memory & Context",
                            bigValue = "$24",
                            unitText = "Save/ year",
                            badgeColor = Color.White,
                            badgeLetter = "≡",
                            checked = contextAwarenessEnabled,
                            onCheckedChange = { enabled ->
                                contextAwarenessEnabled = enabled
                                PochiSignals.playTapHaptic(context)
                            }
                        )
                    }

                    // --- QUICK ACTION CHIPS (Crypto, Chart, Weather, Prayer, Drop File) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val chips = listOf(
                            "📈 Crypto" to "Carikan harga Bitcoin sekarang",
                            "📊 Grafik" to "Tampilkan grafik pengeluaran",
                            "🌤️ Suhu" to "Suhu sekarang berapa",
                            "🕌 Sholat" to "Kapan waktu sholat berikutnya",
                            "📎 File" to "__PICK_FILE__"
                        )
                        chips.forEach { (label, cmd) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF111119))
                                    .border(1.dp, Color(0xFF222230), RoundedCornerShape(50))
                                    .clickable {
                                        if (cmd == "__PICK_FILE__") {
                                            onPickFile()
                                        } else {
                                            sendToJarvis(cmd)
                                        }
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = Color(0xFFD0D0E0),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // --- BOTTOM PROMPT & RICH GENERATIVE RESPONSE CARD ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF0D0D13))
                            .border(1.dp, Color(0xFF1F1F2C), RoundedCornerShape(28.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Question & Timestamp header (Matches "Which subscriptions am I using? / 14:30")
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = activeQuestion,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = activeTimestamp,
                                    color = Color(0xFF7C7C8E),
                                    fontSize = 11.sp
                                )
                            }

                            // Pochi Jarvis Answer Text
                            Text(
                                text = activeReplyText,
                                color = Color(0xFFC8C8DA),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )

                            // Rich Generative Visual Card (Crypto, Chart, Weather, Prayer, FileDrop, System)
                            if (activeRichCard != null) {
                                PochiRichCardView(
                                    card = activeRichCard!!,
                                    compact = false
                                )
                            }

                            // Composer Row (+ Button | Input / Model Label | White Send Circle)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // '+' File Drop / Attach Circular Button
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF181822))
                                            .border(1.dp, Color(0xFF282838), CircleShape)
                                            .clickable { onPickFile() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Drop File",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Clean Input Field ("ChatGPT50" / "Pochi Jarvis" placeholder)
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = inputText,
                                            onValueChange = { inputText = it },
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            cursorBrush = SolidColor(Color(0xFFC77DFF)),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(onSend = { sendToJarvis(inputText) }),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { inner ->
                                                if (inputText.isEmpty()) {
                                                    Text(
                                                        text = "PochiJarvis • Ketik perintah atau cari...",
                                                        color = Color(0xFF8E8EA0),
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                inner()
                                            }
                                        )
                                    }

                                    // Voice Button
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (isListening) Color(0xFFFF2A85) else Color(0xFF181822))
                                            .clickable {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    if (isListening) {
                                                        speechRecognizer?.stopListening()
                                                    } else {
                                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                                                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                                        }
                                                        speechRecognizer?.startListening(intent)
                                                    }
                                                } else {
                                                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                            contentDescription = "Mic",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Crisp White Circular Send Button with Black Paper-Plane Icon
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .clickable {
                                            if (inputText.isNotBlank()) {
                                                sendToJarvis(inputText)
                                            } else {
                                                sendToJarvis("Carikan harga Bitcoin sekarang")
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.size(16.dp)) {
                                        val w = size.width
                                        val h = size.height
                                        val plane = Path().apply {
                                            moveTo(w * 0.92f, h * 0.10f)
                                            lineTo(w * 0.08f, h * 0.46f)
                                            lineTo(w * 0.44f, h * 0.58f)
                                            lineTo(w * 0.56f, h * 0.92f)
                                            close()
                                        }
                                        drawPath(plane, color = Color(0xFF050508))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JarvisBentoToggleCard(
    modifier: Modifier = Modifier,
    subtitle: String,
    bigValue: String,
    unitText: String,
    badgeColor: Color,
    badgeLetter: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF0D0D13))
            .border(1.dp, Color(0xFF1F1F2C), RoundedCornerShape(26.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = subtitle,
                color = Color(0xFFD0D0DE),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = bigValue,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = unitText,
                    color = Color(0xFF8E8E9E),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand / Status Circle Badge
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeLetter,
                        color = if (badgeColor == Color.White) Color.Black else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Custom Reference Pill Toggle Switch
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (checked) Color(0xFF282838) else Color(0xFF14141C))
                        .border(1.dp, Color(0xFF3A3A4E), RoundedCornerShape(50))
                        .padding(horizontal = 3.dp),
                    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (checked) Color.White else Color(0xFF7E7E90))
                    )
                }
            }
        }
    }
}

// ============================================================================
// SCREEN 1 (LEFT SCREEN OF REFERENCE IMAGE): YOUR WALLET & HUB
// ============================================================================
@Composable
fun WalletHubScreen(
    onJumpToJarvis: (String) -> Unit,
    onSeeAllActivity: () -> Unit
) {
    val context = LocalContext.current
    val expenses by PochiPrefs.expensesFlow.collectAsState()
    var isUsdMode by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F4F7))
    ) {
        // Upper Pitch-Black Container with 38.dp Rounded Bottom Corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.64f)
                .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                .background(Color(0xFF050507))
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Header: Crimson Avatar | "Your Wallet" | Bell Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Crimson-glowing Avatar of Tuan Andre
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFFFF4D4D), Color(0xFF800020))
                                )
                            )
                            .border(1.dp, Color(0xFFFF758F).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TA",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "Your Wallet",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Notification Bell with Red Dot
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF14141C))
                            .clickable {
                                PochiSignals.playTapHaptic(context)
                                onJumpToJarvis("Rangkum notifikasi")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 10.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF2A55))
                        )
                    }
                }

                // Currency Selector Pill ("⌄ USD" / "⌄ IDR")
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF14141C))
                        .border(1.dp, Color(0xFF232330), RoundedCornerShape(50))
                        .clickable {
                            isUsdMode = !isUsdMode
                            PochiSignals.playTapHaptic(context)
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = if (isUsdMode) "⌄  USD" else "⌄  IDR",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Hero Balance Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Balance",
                        color = Color(0xFF8E8E9E),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isUsdMode) "$136.500.00" else "Rp 2.218.125.000",
                        color = Color.White,
                        fontSize = if (isUsdMode) 36.sp else 28.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action Pills ("↑ | ↓" and "+")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF15151E))
                            .border(1.dp, Color(0xFF262634), RoundedCornerShape(50))
                            .clickable { onJumpToJarvis("Carikan harga Bitcoin sekarang") }
                            .padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "↑  |  ↓",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF15151E))
                            .border(1.dp, Color(0xFF262634), CircleShape)
                            .clickable { onJumpToJarvis("Tampilkan grafik pengeluaran") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Masked Card Digits
                Text(
                    text = "••••   ••••   ••••   4320",
                    color = Color(0xFFD0D0DE),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                // Mastercard Dual-Circle Pill + "+" Pill
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF14141C))
                            .border(1.dp, Color(0xFF252534), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(28.dp, 18.dp)) {
                            val r = size.height * 0.45f
                            drawCircle(
                                color = Color(0xFFEB001B),
                                radius = r,
                                center = Offset(size.width * 0.36f, size.height * 0.5f)
                            )
                            drawCircle(
                                color = Color(0xFFF79E1B).copy(alpha = 0.9f),
                                radius = r,
                                center = Offset(size.width * 0.64f, size.height * 0.5f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF14141C))
                            .border(1.dp, Color(0xFF252534), RoundedCornerShape(12.dp))
                            .clickable { onJumpToJarvis("Carikan harga Ethereum") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add Card",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // "Add Account" Underlined Link
                Text(
                    text = "Add Account",
                    color = Color(0xFFA0A0B2),
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onJumpToJarvis("Status sistem HP") }
                )
            }
        }

        // Lower Platinum/White Sheet Section ("Transaction" + Dark Pill Cards)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.36f)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Transaction",
                    color = Color(0xFF0A0A0E),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "See All",
                    color = Color(0xFF5A5A6A),
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onSeeAllActivity() }
                )
            }

            // Dark Pill Card 1: Figma Monthly Subscription (Matches Reference Left Screen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF08080C))
                    .clickable { onJumpToJarvis("Tampilkan grafik pengeluaran") }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FigmaLogoBadge(sizeDp = 36.dp)
                Text(
                    text = if (expenses.isNotEmpty()) {
                        "${expenses.first().title} • ${NaturalExpenseParser.formatRupiah(expenses.first().amountRp)}"
                    } else {
                        "You paid for Figma monthly Subscription"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Dark Pill Card 2: Transfer from Clave Vance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE2E2EA), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFF3B30), Color(0xFF600010)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CV", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "Transfer $9400.80 from Clave Vance",
                    color = Color(0xFF14141C),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================================
// SCREEN 3 (RIGHT SCREEN OF REFERENCE IMAGE): ACTIVITY & ANALYTICS
// ============================================================================
@Composable
fun ActivityAnalyticsScreen(
    onJumpToJarvis: () -> Unit
) {
    val context = LocalContext.current
    var filterLabel by remember { mutableStateOf("All") }

    val inf = rememberInfiniteTransition(label = "orbitAnim")
    val orbitAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "orbitRot"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F4F7))
    ) {
        // Upper Pitch-Black Container with 38.dp Rounded Bottom Corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.72f)
                .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                .background(Color(0xFF050507))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Header: Centered "Activity" + Right Search Circle
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Activity",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF14141C))
                            .clickable { onJumpToJarvis() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Hero Summary Card ("October spent / Total Balance USD / $2,650.32" + Orbital Graphic)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF101016))
                        .border(1.dp, Color(0xFF222230), RoundedCornerShape(26.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "October spent",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Total Balance",
                                    color = Color(0xFF8E8E9E),
                                    fontSize = 11.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF1F1F2C))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("USD", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$2,650.32",
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Right Orbital Crystalline Graphic + "+4,920 / Money in"
                        Box(
                            modifier = Modifier.size(110.dp, 92.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width * 0.48f
                                val cy = size.height * 0.44f
                                val r = size.minDimension * 0.30f

                                // Glowing Crystalline Sphere
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White,
                                            Color(0xFF90E0EF),
                                            Color(0xFF7B2CBF),
                                            Color.Transparent
                                        ),
                                        center = Offset(cx, cy),
                                        radius = r * 1.4f
                                    ),
                                    radius = r * 1.3f,
                                    center = Offset(cx, cy)
                                )

                                // Rotating Orbital Rings
                                rotate(orbitAngle, pivot = Offset(cx, cy)) {
                                    drawOval(
                                        color = Color.White.copy(alpha = 0.55f),
                                        topLeft = Offset(cx - r * 1.6f, cy - r * 0.55f),
                                        size = Size(r * 3.2f, r * 1.1f),
                                        style = Stroke(width = 1.2.dp.toPx())
                                    )
                                }
                                rotate(-orbitAngle * 0.7f, pivot = Offset(cx, cy)) {
                                    drawOval(
                                        color = Color(0xFFC77DFF).copy(alpha = 0.5f),
                                        topLeft = Offset(cx - r * 1.4f, cy - r * 0.7f),
                                        size = Size(r * 2.8f, r * 1.4f),
                                        style = Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                horizontalAlignment = Alignment.End
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF252532).copy(alpha = 0.9f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text("▲ +4,920", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    text = "Money in",
                                    color = Color(0xFFA0A0B2),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                // Centered "All ⌄" Filter Pill
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF14141C))
                            .border(1.dp, Color(0xFF242432), RoundedCornerShape(50))
                            .clickable {
                                filterLabel = if (filterLabel == "All") "Crypto" else "All"
                                PochiSignals.playTapHaptic(context)
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "$filterLabel ⌄",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // "Today" & "3 transactions" Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Today",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "3 transactions",
                        color = Color(0xFF9E9EB0),
                        fontSize = 12.sp,
                        textDecoration = TextDecoration.Underline
                    )
                }

                // 2-Column Square Bento Cards ("Figma" & "Aster Market")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: Figma
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF111117))
                            .border(1.dp, Color(0xFF20202C), RoundedCornerShape(24.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FigmaLogoBadge(sizeDp = 30.dp)
                                Text(
                                    text = "Figma",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "-$15.00",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Card 2: Aster Market
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF111117))
                            .border(1.dp, Color(0xFF20202C), RoundedCornerShape(24.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF1B1B26)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🛍️", fontSize = 13.sp)
                                }
                                Text(
                                    text = "Aster Market",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "-$15.00",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Full-Width Card ("Northline Coffee" + Latte Art Canvas)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF111117))
                        .border(1.dp, Color(0xFF20202C), RoundedCornerShape(24.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF1B1B26)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("☕", fontSize = 13.sp)
                                }
                                Text(
                                    text = "Northline Coffee",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "-$15.00",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Custom Latte Art Illustration Canvas
                        Canvas(modifier = Modifier.size(64.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val r = size.minDimension * 0.44f
                            // Roasted coffee scatter halo
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFF6F4E37).copy(alpha = 0.55f), Color.Transparent),
                                    center = Offset(cx, cy),
                                    radius = r * 1.25f
                                ),
                                radius = r * 1.25f,
                                center = Offset(cx, cy)
                            )
                            // Cup rim
                            drawCircle(
                                color = Color(0xFFD7CCC8),
                                radius = r,
                                center = Offset(cx, cy)
                            )
                            // Crema
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFFD4A373), Color(0xFF8D5524)),
                                    center = Offset(cx, cy),
                                    radius = r * 0.88f
                                ),
                                radius = r * 0.88f,
                                center = Offset(cx, cy)
                            )
                            // Latte leaf heart
                            drawCircle(
                                color = Color(0xFFFFF8F0),
                                radius = r * 0.36f,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Light Sheet Section ("Yesterday" + "Sent to Jamie -$125.00")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.28f)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Yesterday",
                    color = Color(0xFF0A0A0E),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "2 transactions",
                    color = Color(0xFF5A5A6A),
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF08080C))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A24)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↗", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(
                            text = "Sent to Jamie",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Transfer • Yesterday",
                            color = Color(0xFF8E8E9E),
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = "-$125.00",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FigmaLogoBadge(sizeDp: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1A1A24)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(sizeDp * 0.55f)) {
            val r = size.width * 0.24f
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            drawCircle(color = Color(0xFFF24E1E), radius = r, center = Offset(cx - r * 0.8f, cy - r * 1.4f))
            drawCircle(color = Color(0xFFFF7262), radius = r, center = Offset(cx + r * 0.8f, cy - r * 1.4f))
            drawCircle(color = Color(0xFFA259FF), radius = r, center = Offset(cx - r * 0.8f, cy))
            drawCircle(color = Color(0xFF1ABCFE), radius = r, center = Offset(cx + r * 0.8f, cy))
            drawCircle(color = Color(0xFF0ACF83), radius = r, center = Offset(cx - r * 0.8f, cy + r * 1.4f))
        }
    }
}

// ============================================================================
// SCREEN 4 (⋮ TAB): SETTINGS, API KEYS, NOTCH CONTROLS & MEMORY VAULT
// ============================================================================
@Composable
fun SettingsAndMemoryScreen(
    onPickFile: () -> Unit
) {
    val context = LocalContext.current
    val provider by PochiPrefs.providerFlow.collectAsState()
    val userName by PochiPrefs.userNameFlow.collectAsState()

    val db = remember { PochiDb.getInstance(context) }
    var memories by remember { mutableStateOf(db.getAllMemories()) }
    var newMemoryText by remember { mutableStateOf("") }

    var editingName by remember { mutableStateOf(userName) }
    var currentApiKey by remember { mutableStateOf(PochiPrefs.getApiKey(context, provider)) }
    var showApiKey by remember { mutableStateOf(false) }
    var islandRunning by remember { mutableStateOf(DynamicIslandService.isRunning.value) }

    fun selectProvider(p: AiProvider) {
        PochiPrefs.setProvider(context, p)
        currentApiKey = PochiPrefs.getApiKey(context, p)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F4F7))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 84.dp)
                .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                .background(Color(0xFF050507))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Jarvis Core & API Key",
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Konfigurasi AI, Notch Dynamic Island & Memori $userName",
                                color = Color(0xFF8E8E9E),
                                fontSize = 11.sp
                            )
                        }

                        PochiMascot(
                            state = BotState.IDLE,
                            emoteOverride = PochiEmote.WINK_LEFT,
                            size = 34.dp,
                            showBlackBacking = false
                        )
                    }
                }

                // 1. NOTCH DYNAMIC ISLAND CONTROL CARD
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF101018))
                            .border(1.dp, Color(0xFF252238), RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Notch Dynamic Island (Jarvis)",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Menyatu tanpa border atas • Mengecil & transparan saat idle",
                                        color = Color(0xFF9E9EB0),
                                        fontSize = 11.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (islandRunning) Color(0xFF9D4EDD) else Color(0xFF222230))
                                        .clickable {
                                            val next = !islandRunning
                                            islandRunning = next
                                            toggleDynamicIsland(context, next)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (islandRunning) "Aktif" else "Aktifkan",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1A1828))
                                        .clickable {
                                            islandRunning = true
                                            toggleDynamicIsland(context, true)
                                            PochiSignals.requestExpandIsland()
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✦ Buka Chat Notch", color = Color(0xFFE0AAFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1A1828))
                                        .clickable {
                                            PochiPrefs.saveOffsets(context, 0, 0)
                                            Toast.makeText(context, "Posisi Notch di-reset ke tengah atas", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("↺ Reset Posisi Atas", color = Color(0xFF00F5D4), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 2. AI PROVIDER & API KEY CONFIGURATION
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF101018))
                            .border(1.dp, Color(0xFF222230), RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Penyedia AI & API Key",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AiProvider.entries.forEach { p ->
                                    val isSel = provider == p
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSel) Color(0xFF7B2CBF) else Color(0xFF1A1A26))
                                            .clickable { selectProvider(p) }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = p.label,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF181824))
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = currentApiKey,
                                    onValueChange = {
                                        currentApiKey = it
                                        PochiPrefs.saveApiKey(context, provider, it)
                                    },
                                    singleLine = true,
                                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(Color(0xFFC77DFF)),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (currentApiKey.isEmpty()) {
                                            Text(
                                                text = "Tempel API Key ${provider.label} (${provider.keyHint})",
                                                color = Color(0xFF7E7E90),
                                                fontSize = 12.sp
                                            )
                                        }
                                        inner()
                                    }
                                )

                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle Key",
                                        tint = Color(0xFF9E9EB0),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. USER NAME & SYSTEM PERMISSIONS
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF101018))
                            .border(1.dp, Color(0xFF222230), RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Panggilan Kehormatan & Izin Sistem",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF181824))
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                BasicTextField(
                                    value = editingName,
                                    onValueChange = {
                                        editingName = it
                                        PochiPrefs.setUserName(context, it)
                                    },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                    cursorBrush = SolidColor(Color(0xFFC77DFF)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            val hasOverlay = Settings.canDrawOverlays(context)
                            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

                            PermissionRowItem(
                                label = "Izin Notch Overlay (Tampil di Atas Aplikasi)",
                                granted = hasOverlay,
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                    )
                                }
                            )
                            PermissionRowItem(
                                label = "Izin Suara & Mikrofon Jarvis",
                                granted = hasMic,
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    )
                                }
                            )
                            PermissionRowItem(
                                label = "Akses Konteks Layar (Accessibility Service)",
                                granted = PochiAccessibilityService.instance != null,
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            )
                        }
                    }
                }

                // 4. PERSISTENT MEMORY VAULT
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF101018))
                            .border(1.dp, Color(0xFF222230), RoundedCornerShape(24.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "🧠 Memori Jangka Panjang Tuan Andre (${memories.size})",
                                color = Color.White,
                                fontSize = 14.sp,
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
                                        .background(Color(0xFF181824))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    BasicTextField(
                                        value = newMemoryText,
                                        onValueChange = { newMemoryText = it },
                                        singleLine = true,
                                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                        cursorBrush = SolidColor(Color(0xFFC77DFF)),
                                        modifier = Modifier.fillMaxWidth(),
                                        decorationBox = { inner ->
                                            if (newMemoryText.isEmpty()) {
                                                Text("Tambah fakta tentang Tuan Andre...", color = Color(0xFF7E7E90), fontSize = 12.sp)
                                            }
                                            inner()
                                        }
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF7B2CBF))
                                        .clickable {
                                            if (newMemoryText.isNotBlank()) {
                                                db.saveMemory("Preferensi", newMemoryText)
                                                newMemoryText = ""
                                                memories = db.getAllMemories()
                                                PochiSignals.playSuccessHaptic(context)
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text("Simpan", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                items(memories, key = { it.id }) { mem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF101018))
                            .border(1.dp, Color(0xFF1E1E2A), RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${mem.category} • ${mem.formattedDate}",
                                color = Color(0xFFC77DFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = mem.fact,
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                db.deleteMemory(mem.id)
                                memories = db.getAllMemories()
                                PochiSignals.playTapHaptic(context)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFFF2A55),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRowItem(
    label: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFFC0C0D0),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (granted) "✓ Aktif" else "Buka Izin →",
            color = if (granted) Color(0xFF00F5D4) else Color(0xFFFF9F1C),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun toggleDynamicIsland(context: Context, enable: Boolean) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "Berikan izin 'Tampilkan di atas aplikasi lain' untuk Pochi Notch", Toast.LENGTH_SHORT).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return
    }

    val intent = Intent(context, DynamicIslandService::class.java)
    if (enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } else {
        context.stopService(intent)
    }
}

// ============================================================================
// ONBOARDING SCREEN (FIRST RUN)
// ============================================================================
@Composable
fun PochiOnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("Tuan Andre") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050507))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PochiMascot(
                state = BotState.IDLE,
                emoteOverride = PochiEmote.HAPPY,
                size = 84.dp,
                showBlackBacking = false
            )
            Text(
                text = "Pochi Jarvis v2",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "Notch AI Agent pribadi untuk Tuan Andre. Mendukung pencarian harga kripto live, grafik interaktif, cuaca, waktu sholat, dan drop file instan.",
                color = Color(0xFF9E9EB0),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Nama Panggilan Anda",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF14141E))
                    .border(1.dp, Color(0xFF28283A), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFFC77DFF)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Color(0xFF9D4EDD), Color(0xFF5A189A))))
                .clickable {
                    PochiPrefs.setUserName(context, nameInput)
                    onComplete()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aktifkan Pochi Jarvis ✦",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
