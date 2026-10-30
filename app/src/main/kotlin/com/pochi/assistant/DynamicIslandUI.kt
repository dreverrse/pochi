package com.pochi.assistant

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * Public Enums & Models to maintain seamless backward compatibility across the app.
 */
enum class PochiTab(
    val titleEn: String,
    val titleId: String,
    val icon: ImageVector,
    val accent: Color
) {
    GLANCE("Glance", "Sekilas", Icons.Rounded.Dashboard, Color(0xFF7C5CFF)),
    TODAY("Activity", "Aktivitas", Icons.Rounded.DonutLarge, Color(0xFF34C759)),
    CHAT("Call", "Panggilan", Icons.Rounded.Call, Color(0xFF007AFF)),
    LIFE("Weather", "Cuaca", Icons.Rounded.WbSunny, Color(0xFFFF9500)),
    WALLET("Media", "Musik", Icons.Rounded.MusicNote, Color(0xFFAF52DE)),
    FOCUS("Timer", "Timer", Icons.Rounded.Timer, Color(0xFFFF9500)),
    DECK("Actions", "Aksi", Icons.Rounded.Widgets, Color(0xFF5856D6))
}

data class PochiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class QuickPhoneComposerMode { NONE, CALL, SMS, WHATSAPP, OPEN_APP }
enum class TaskFilter { ALL, ACTIVE, DONE }
enum class PomodoroPhase(val defaultMinutes: Int) {
    WORK(25), SHORT_BREAK(5), LONG_BREAK(15)
}

data class LaunchableAppInfo(
    val label: String,
    val packageName: String
)

private enum class IslandMode {
    COLLAPSED,
    EXPANDED,
    EYES,
    MINI
}

/**
 * Pochi Dynamic Island - Pure iOS Live Activity Aesthetic.
 *
 * Modeled directly on official Apple Dynamic Island live activities:
 * 1. Podcasts / Spotify (Media Player with Album Art, Progress Bar, Large Play/Pause)
 * 2. Telegram / FaceTime (Pochi Live Call with expressive eyes avatar & Large Red/Gray Buttons)
 * 3. Apple Health Activity Rings (Hydration, Tasks, Focus, Budget)
 * 4. Weather & UV Rainbow Equalizer Bars & Prayer Schedule
 * 5. Yandex Go / Wash Timer with animated route progress & big buttons
 */
@Composable
fun PochiIslandBot(
    onExpandChange: (Boolean) -> Unit,
    onExpandedHeightChange: (Int) -> Unit = {},
    isHidden: Boolean,
    peekMode: Boolean,
    peekSide: String = "right",
    onRestoreFromHidden: () -> Unit,
    onDragBubble: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onPowerOffService: () -> Unit,
    onMiniChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Palet redesign (Tahap 1): nilai tunggal kini hidup di PochiDesign,
    // nama lokal dipertahankan agar sisa file tidak berubah.
    val islandBlack = PochiDesign.IslandBlack
    val cardSurface = PochiDesign.WidgetSurface
    val borderHairline = PochiDesign.WidgetBorder
    val textPrimary = PochiDesign.TextPrimary
    val textSecondary = PochiDesign.TextSecondary
    val accentViolet = PochiDesign.AccentViolet

    // Vibrant iOS System Colors (large & colorful buttons)
    val iosGreen = Color(0xFF34C759)
    val iosRed = Color(0xFFFF3B30)
    val iosBlue = Color(0xFF007AFF)
    val iosPurple = Color(0xFFAF52DE)
    val iosAmber = Color(0xFFFF9500)
    val iosGray = Color(0xFF2C2C2E)
    val spotifyGreen = Color(0xFF1DB954)

    val lang by PochiPrefs.langFlow.collectAsState()
    val isId = lang == PochiLang.ID

    var islandMode by remember { mutableStateOf(IslandMode.COLLAPSED) }
    var activeTab by remember { mutableStateOf(PochiTab.GLANCE) }
    var botState by remember { mutableStateOf(BotState.IDLE) }
    var customEmote by remember { mutableStateOf<PochiEmote?>(null) }
    var idleEmote by remember { mutableStateOf<PochiEmote?>(PochiEmote.SMILING) }
    var pokeCount by remember { mutableIntStateOf(0) }
    var eyeGazeOffset by remember { mutableStateOf(Offset.Zero) }

    // Signals from system & preferences
    val tasks by PochiPrefs.tasksFlow.collectAsState()
    val waterGlasses by PochiPrefs.waterGlassesFlow.collectAsState()
    val dailyBudgetRp by PochiPrefs.dailyBudgetFlow.collectAsState()
    val completedFocusSessions by PochiPrefs.focusSessionsFlow.collectAsState()
    val selectedCity by PochiPrefs.cityFlow.collectAsState()
    val activeApproval by ApprovalManager.activeRequest.collectAsState()
    val dismissSignal by ApprovalManager.dismissSignal.collectAsState()
    val mediaPlayback by PochiSignals.mediaPlayback.collectAsState()
    val systemPeek by PochiSignals.systemPeek.collectAsState()
    val waterLog by PochiPrefs.waterLogFlow.collectAsState()

    // Tahap 6 (ala Nusa): pelacak diam untuk siklus mata pendamping
    // dan jendela tampil intipan peristiwa sistem.
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var idleTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var peekTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(islandMode) {
        while (islandMode != IslandMode.EXPANDED) {
            idleTickMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(systemPeek) {
        peekTickMs = System.currentTimeMillis()
        while (systemPeek != null &&
            System.currentTimeMillis() - (systemPeek?.atMs ?: 0L) < 3400L
        ) {
            delay(400)
            peekTickMs = System.currentTimeMillis()
        }
    }
    val activeDownload by PochiSignals.activeDownload.collectAsState()
    val latestScreenshot by PochiSignals.latestScreenshot.collectAsState()

    // Focus session timer state
    var isFocusRunning by remember { mutableStateOf(false) }
    var isFocusPaused by remember { mutableStateOf(false) }
    var focusRemainingSeconds by remember { mutableIntStateOf(25 * 60) }
    val focusTotalSeconds by remember { mutableIntStateOf(25 * 60) }

    // Voice & Call state
    var isListeningVoice by remember { mutableStateOf(false) }
    var voicePartialText by remember { mutableStateOf("") }
    var callSeconds by remember { mutableIntStateOf(56) }
    var speechRecognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Weather state
    var liveTemp by remember { mutableStateOf(32) }
    var isRefreshingWeather by remember { mutableStateOf(false) }

    // Jam header kini hidup di composable kecil PochiClockText (lihat
    // bawah file) agar tik 1 detik tidak merekomposisi seluruh island
    // — perbaikan responsivitas Tahap 4.

    // Lirikan kembali netral setelah sesaat (Tahap 7)
    LaunchedEffect(eyeGazeOffset) {
        if (eyeGazeOffset != Offset.Zero) {
            delay(1500)
            eyeGazeOffset = Offset.Zero
        }
    }

    // Call duration ticker
    LaunchedEffect(islandMode, activeTab) {
        while (islandMode == IslandMode.EXPANDED && activeTab == PochiTab.CHAT) {
            delay(1000)
            callSeconds++
        }
    }

    // Focus countdown ticker
    LaunchedEffect(isFocusRunning, isFocusPaused) {
        if (isFocusRunning && !isFocusPaused) {
            while (focusRemainingSeconds > 0 && isFocusRunning && !isFocusPaused) {
                delay(1000)
                focusRemainingSeconds--
            }
            if (focusRemainingSeconds <= 0 && isFocusRunning) {
                isFocusRunning = false
                PochiPrefs.recordFocusSession(context)
                customEmote = PochiEmote.HAPPY
                PochiSignals.playSuccessHaptic(context)
            }
        }
    }

    // React to system events
    LaunchedEffect(dismissSignal) {
        if (dismissSignal > 0L && islandMode == IslandMode.EXPANDED) {
            islandMode = IslandMode.COLLAPSED
            onExpandChange(false)
        }
    }

    LaunchedEffect(activeApproval) {
        if (activeApproval != null && islandMode == IslandMode.COLLAPSED) {
            customEmote = PochiEmote.SURPRISED
            islandMode = IslandMode.EXPANDED
            onExpandChange(true)
        }
    }

    LaunchedEffect(mediaPlayback.isPlaying) {
        if (mediaPlayback.isPlaying && islandMode == IslandMode.EXPANDED) {
            activeTab = PochiTab.WALLET
        }
    }

    // Idle Pochi emotion cycle
    LaunchedEffect(islandMode, botState) {
        val expressivePool = listOf(
            PochiEmote.SMILING,
            PochiEmote.HAPPY,
            PochiEmote.WINK_LEFT,
            PochiEmote.HMM,
            PochiEmote.CUTE,
            PochiEmote.CALM,
            PochiEmote.FOCUS
        )
        while (true) {
            delay((3500..6000).random().toLong())
            if (customEmote == null && botState == BotState.IDLE) {
                idleEmote = expressivePool.random()
            }
        }
    }

    fun setMode(mode: IslandMode) {
        lastInteractionMs = System.currentTimeMillis()
        islandMode = mode
        onMiniChange(mode == IslandMode.MINI)
        onExpandChange(mode == IslandMode.EXPANDED)
        if (mode == IslandMode.COLLAPSED) {
            if (isListeningVoice) {
                isListeningVoice = false
                runCatching { speechRecognizerRef?.stopListening() }
            }
        }
    }

    // Tahap 8: satu pintu navigasi halaman agar dock, titik, geser,
    // dan kartu selalu konsisten urutan & rasanya.
    fun goToPage(tab: PochiTab) {
        if (activeTab != tab) {
            activeTab = tab
            PochiSignals.playTapHaptic(context)
        }
        lastInteractionMs = System.currentTimeMillis()
    }

    fun toggleVoiceListening() {
        if (isListeningVoice) {
            isListeningVoice = false
            customEmote = PochiEmote.SMILING
            runCatching { speechRecognizerRef?.stopListening() }
            return
        }
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            Toast.makeText(context, if (isId) "Izin mikrofon diperlukan" else "Microphone permission required", Toast.LENGTH_SHORT).show()
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }

        runCatching {
            if (speechRecognizerRef == null) {
                speechRecognizerRef = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            }
            val recognizer = speechRecognizerRef ?: return
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningVoice = true
                    voicePartialText = if (isId) "Mendengarkan suaramu..." else "Listening..."
                    customEmote = PochiEmote.HMM
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListeningVoice = false
                }
                override fun onError(error: Int) {
                    isListeningVoice = false
                    customEmote = PochiEmote.CONFUSED
                    voicePartialText = ""
                }
                override fun onResults(results: Bundle?) {
                    isListeningVoice = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (spoken.isNotBlank()) {
                        voicePartialText = spoken
                        customEmote = PochiEmote.HAPPY
                        PochiSignals.playSuccessHaptic(context)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (partial.isNotBlank()) {
                        voicePartialText = partial
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isId) "id-ID" else "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.startListening(intent)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                speechRecognizerRef?.destroy()
                speechRecognizerRef = null
            }
        }
    }

    // Dynamic Sizing with 120fps Spring Transitions
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = configuration.screenWidthDp.dp
    val collapsedWidth = if (mediaPlayback.isPlaying) 216.dp else 196.dp
    val collapsedHeight = 36.dp
    val expandedWidth = if (isLandscape) {
        (screenWidth - 32.dp).coerceIn(320.dp, 480.dp)
    } else {
        (screenWidth - 20.dp).coerceIn(336.dp, 380.dp)
    }
    val expandedHeight = 288.dp

    val transition = updateTransition(targetState = islandMode, label = "islandTransition")

    // Organic breathing animation when collapsed
    val ambientTransition = rememberInfiniteTransition(label = "ambient")
    val breathScale by ambientTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val islandWidth by transition.animateDp(
        transitionSpec = {
            if (targetState == IslandMode.EXPANDED) {
                spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
            } else {
                spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
            }
        },
        label = "islandWidth"
    ) { mode ->
        when (mode) {
            IslandMode.COLLAPSED -> collapsedWidth
            IslandMode.EXPANDED -> expandedWidth
            IslandMode.EYES -> 68.dp
            IslandMode.MINI -> 48.dp
        }
    }

    val islandHeightAnim by transition.animateDp(
        transitionSpec = {
            if (targetState == IslandMode.EXPANDED) {
                spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow)
            } else {
                spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
            }
        },
        label = "islandHeight"
    ) { mode ->
        when (mode) {
            IslandMode.COLLAPSED -> collapsedHeight
            IslandMode.EXPANDED -> expandedHeight
            IslandMode.EYES -> 34.dp
            IslandMode.MINI -> 22.dp
        }
    }

    // Authentic Apple Squircle Corner Radii: 18dp for capsule pill, 32dp for expanded card
    val islandCornerRadius by transition.animateDp(
        transitionSpec = {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "islandCornerRadius"
    ) { mode ->
        when (mode) {
            IslandMode.COLLAPSED -> 18.dp
            IslandMode.EXPANDED -> 32.dp
            IslandMode.EYES -> 17.dp
            IslandMode.MINI -> 11.dp
        }
    }

    val expandedContentAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 160) },
        label = "expandedAlpha"
    ) { mode -> if (mode == IslandMode.EXPANDED) 1f else 0f }

    val eyesContentAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 140) },
        label = "eyesAlpha"
    ) { mode -> if (mode == IslandMode.EYES) 1f else 0f }

    val miniContentAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 160) },
        label = "miniAlpha"
    ) { mode -> if (mode == IslandMode.MINI) 1f else 0f }

    val collapsedContentAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 140) },
        label = "collapsedAlpha"
    ) { mode -> if (mode == IslandMode.COLLAPSED) 1f else 0f }

    // Tahap 6 (ala Nusa): urutan halaman panel untuk geser & titik.
    val pageOrder = listOf(
        PochiTab.GLANCE, PochiTab.WALLET, PochiTab.TODAY,
        PochiTab.LIFE, PochiTab.FOCUS, PochiTab.CHAT
    )

    // Tahap 7: pulsa squash & stretch setiap island berubah bentuk —
    // island melebar sesaat sambil memipih, lalu memantul pulih.
    val squashPulse = remember { Animatable(0f) }
    LaunchedEffect(islandMode) {
        squashPulse.snapTo(1f)
        squashPulse.animateTo(
            0f,
            spring(dampingRatio = 0.42f, stiffness = 260f)
        )
    }

    val activeEmote = customEmote ?: idleEmote ?: PochiEmote.SMILING

    // Tahap 6 (ala Nusa): siklus pendamping saat island mengecil —
    // 0 = jam & status, 1 = jam memudar diganti mata (>=15 dtk diam),
    // 2 = mata tertidur (>=75 dtk diam). Musik yang sedang diputar
    // menahan siklus agar info lagu tetap terlihat.
    val idleSeconds = ((idleTickMs - lastInteractionMs) / 1000L).coerceAtLeast(0L)
    val idleStage = when {
        islandMode == IslandMode.EXPANDED -> 0
        mediaPlayback.isPlaying -> 0
        idleSeconds >= 75 -> 2
        idleSeconds >= 15 -> 1
        else -> 0
    }
    val displayEmote = if (idleStage == 2) PochiEmote.SLEEPY else activeEmote

    // Intipan peristiwa sistem tampil maksimal ~3,4 detik.
    val peekActive = systemPeek != null &&
        (peekTickMs - (systemPeek?.atMs ?: 0L)) in 0..3400L

    // Tahap 8: bersembunyi otomatis — 10 detik tanpa interaksi,
    // panel menutup sendiri lalu island menyusut jadi MINI (sangat
    // kecil & tembus pandang) agar tidak menghalangi layar. Musik,
    // timer fokus, unduhan, dan kartu persetujuan menahan penyusutan.
    LaunchedEffect(islandMode, mediaPlayback.isPlaying, isFocusRunning, activeApproval, activeDownload, systemPeek) {
        while (true) {
            delay(500)
            val idleMs = System.currentTimeMillis() - lastInteractionMs
            if (idleMs < 10_000L) continue
            when (islandMode) {
                IslandMode.EXPANDED -> {
                    if (activeApproval == null && !isListeningVoice) {
                        setMode(IslandMode.COLLAPSED)
                    }
                }
                IslandMode.COLLAPSED, IslandMode.EYES -> {
                    if (!mediaPlayback.isPlaying && !isFocusRunning &&
                        activeDownload == null && !peekActive
                    ) {
                        setMode(IslandMode.MINI)
                    }
                }
                IslandMode.MINI -> Unit
            }
        }
    }

    // Saat MINI: musik mulai / peristiwa sistem -> tampil lagi sebentar
    LaunchedEffect(mediaPlayback.isPlaying) {
        if (mediaPlayback.isPlaying && islandMode == IslandMode.MINI) {
            setMode(IslandMode.COLLAPSED)
        }
    }
    LaunchedEffect(systemPeek) {
        if (systemPeek != null && islandMode == IslandMode.MINI) {
            setMode(IslandMode.COLLAPSED)
        }
    }


    // Edge Peek Mode
    if (isHidden) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onRestoreFromHidden() },
            contentAlignment = if (peekSide == "left") Alignment.CenterStart else Alignment.CenterEnd
        ) {
            // Redesign Tahap 3a: mode tepi kini menampilkan side bar
            // vertikal ber-ring progres, bukan maskot mungil saja.
            val doneCount = tasks.count { it.isDone }
            val tasksFraction = if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size.toFloat()
            val focusFraction = if (isFocusRunning && focusTotalSeconds > 0) {
                1f - (focusRemainingSeconds.toFloat() / focusTotalSeconds.toFloat())
            } else {
                (completedFocusSessions / 4f).coerceIn(0f, 1f)
            }
            PochiSideBar(
                side = peekSide,
                tasksFraction = tasksFraction,
                focusFraction = focusFraction
            )
        }
        return
    }

    val islandShape = RoundedCornerShape(islandCornerRadius)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Pure Matte-Black (#000000) 120fps Hardware-Accelerated Container
        Box(
            modifier = Modifier
                .width(islandWidth)
                .height(islandHeightAnim)
                .graphicsLayer {
                    clip = true
                    shape = islandShape
                    val squash = squashPulse.value
                    var sx = 1f + squash * 0.05f
                    var sy = 1f - squash * 0.055f
                    if (islandMode == IslandMode.COLLAPSED) {
                        sx *= breathScale
                        sy *= (2f - breathScale)
                    }
                    scaleX = sx
                    scaleY = sy
                }
                .background(
                    Brush.verticalGradient(listOf(PochiDesign.Void, islandBlack)),
                    islandShape
                )
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(PochiDesign.FaceRim.copy(alpha = 0.85f), borderHairline)
                    ),
                    islandShape
                )
        ) {
            // Cahaya violet lembut di balik konten (Tahap 4) — memberi
            // kedalaman premium tanpa mengorbankan keterbacaan.
            if (expandedContentAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .graphicsLayer { alpha = expandedContentAlpha }
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    accentViolet.copy(alpha = 0.20f + squashPulse.value * 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
            // ================= 1. COLLAPSED DYNAMIC ISLAND CAPSULE =================
            if (collapsedContentAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = collapsedContentAlpha }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Tahap 7: mata melirik ke titik yang disentuh.
                                eyeGazeOffset = Offset(
                                    ((down.position.x / size.width.coerceAtLeast(1)) - 0.5f) * 7f,
                                    ((down.position.y / size.height.coerceAtLeast(1)) - 0.5f) * 5f
                                )
                                val touchSlop = viewConfiguration.touchSlop
                                var accumulatedX = 0f
                                var accumulatedY = 0f
                                var isDragging = false
                                var upSeen = false
                                var longPressFired = false

                                // Fase A (Tahap 6): bedakan ketuk / seret /
                                // tahan. Tekan lama kini MENYALA SAAT MASIH
                                // DITAHAN (450 md) — tidak menunggu jari
                                // diangkat seperti Tahap 5 yang mudah gagal.
                                try {
                                    withTimeout(450L) {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (change.changedToUp()) {
                                                upSeen = true
                                                break
                                            }
                                            if (change.isConsumed) break
                                            accumulatedX += change.position.x - change.previousPosition.x
                                            accumulatedY += change.position.y - change.previousPosition.y
                                            if (kotlin.math.hypot(accumulatedX, accumulatedY) > touchSlop) {
                                                isDragging = true
                                                break
                                            }
                                        }
                                    }
                                } catch (e: TimeoutCancellationException) {
                                    if (!isDragging && !upSeen) longPressFired = true
                                }

                                when {
                                    longPressFired -> {
                                        customEmote = PochiEmote.CALM
                                        setMode(IslandMode.EYES)
                                        waitForUpOrCancellation()
                                    }
                                    upSeen -> {
                                        // Bangun dari tidur -> sapa gembira (ala Nusa)
                                        customEmote = PochiEmote.HAPPY
                                        setMode(IslandMode.EXPANDED)
                                    }
                                    isDragging -> {
                                        onDragBubble(accumulatedX, accumulatedY)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (change.changedToUp()) {
                                                onDragEnd()
                                                break
                                            }
                                            change.consume()
                                            onDragBubble(
                                                change.position.x - change.previousPosition.x,
                                                change.position.y - change.previousPosition.y
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Pochi Mascot's glowing white eyes on the left
                    if (idleStage > 0) {
                        // Tahap 6 (ala Nusa): jam memudar, tinggal mata pendamping.
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            PochiMascot(
                                state = botState,
                                emoteOverride = displayEmote,
                                size = 26.dp,
                                eyeTrackerOffset = eyeGazeOffset,
                                showBlackBacking = false
                            )
                            if (idleStage == 2) {
                                SleepZzz(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(x = 17.dp, y = (-3).dp)
                                )
                            }
                        }
                    } else {
                    PochiMascot(
                        state = botState,
                        emoteOverride = activeEmote,
                        size = 24.dp,
                        eyeTrackerOffset = eyeGazeOffset,
                        showBlackBacking = false
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Glanceable status on the right with vibrant color
                    when {
                        peekActive -> {
                            val peek = systemPeek!!
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = when (peek.kind) {
                                        "charging" -> Icons.Rounded.Bolt
                                        "audio" -> Icons.Rounded.Headphones
                                        else -> Icons.Rounded.Power
                                    },
                                    contentDescription = null,
                                    tint = if (peek.kind == "charging") iosGreen else textPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (peek.percent >= 0) "${peek.label} • ${peek.percent}%" else peek.label,
                                    color = textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        mediaPlayback.isPlaying -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                LiveEqualizer(
                                    color = spotifyGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = mediaPlayback.title.take(13),
                                    color = textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        isFocusRunning -> {
                            val mins = focusRemainingSeconds / 60
                            val secs = focusRemainingSeconds % 60
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(iosAmber)
                                )
                                Text(
                                    text = String.format(Locale.US, "%02d:%02d", mins, secs),
                                    color = iosAmber,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        activeDownload != null -> {
                            val pct = ((activeDownload?.fraction ?: 0f) * 100).toInt()
                            Text(
                                text = "↓ $pct%",
                                color = iosBlue,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        else -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(accentViolet)
                                )
                                Text(
                                    text = "Pochi ✦",
                                    color = textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    }
                }
            }

            // ================= 1B. EYES-ONLY (Tahap 5) =================
            if (eyesContentAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = eyesContentAlpha }
                        .pochiPress { setMode(IslandMode.COLLAPSED) },
                    contentAlignment = Alignment.Center
                ) {
                    PochiMascot(
                        state = botState,
                        emoteOverride = displayEmote,
                        size = 21.dp,
                        eyeTrackerOffset = eyeGazeOffset,
                        showBlackBacking = false
                    )
                    if (displayEmote == PochiEmote.SLEEPY) {
                        SleepZzz(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 7.dp, top = 1.dp)
                        )
                    }
                }
            }

            // ================= 1C. MINI (Tahap 8: sembunyi otomatis) =================
            if (miniContentAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = miniContentAlpha * 0.72f }
                        .pochiPress {
                            customEmote = PochiEmote.HAPPY
                            setMode(IslandMode.COLLAPSED)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    PochiMascot(
                        state = botState,
                        emoteOverride = displayEmote,
                        size = 12.dp,
                        eyeTrackerOffset = eyeGazeOffset,
                        showBlackBacking = false
                    )
                }
            }

            // ================= 2. EXPANDED LIVE ACTIVITY CARD =================
            if (expandedContentAlpha > 0.01f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandedContentAlpha }
                        .pointerInput(activeTab) {
                            var totalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onDragEnd = {
                                    val idx = pageOrder.indexOf(activeTab)
                                    if (idx >= 0) {
                                        val next = when {
                                            totalDrag < -44f && idx < pageOrder.lastIndex -> pageOrder[idx + 1]
                                            totalDrag > 44f && idx > 0 -> pageOrder[idx - 1]
                                            else -> activeTab
                                        }
                                        if (next != activeTab) {
                                            goToPage(next)
                                        }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    totalDrag += dragAmount
                                    if (kotlin.math.abs(totalDrag) > viewConfiguration.touchSlop) {
                                        change.consume()
                                    }
                                }
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // --- HEADER PREMIUM (Tahap 4): wajah + identitas + jam + tutup ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        customEmote = PochiEmote.CALM
                                        setMode(IslandMode.EYES)
                                    }
                                )
                            }
                        ) {
                            PochiFaceMascot(
                                face = PochiFace.forState(botState),
                                size = 30.dp,
                                showWaves = true
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "POCHI",
                                        color = textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 2.sp
                                    )
                                    Text(
                                        text = "✦",
                                        color = PochiDesign.AccentGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = botState.statusLabel,
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PochiClockText()
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(PochiDesign.SurfaceGlass)
                                    .border(1.dp, borderHairline, CircleShape)
                                    .pochiPress { setMode(IslandMode.COLLAPSED) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // --- ACTIVE CARD BODY (Tahap 7: transisi halaman hidup) ---
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            val dir = if (pageOrder.indexOf(targetState) >= pageOrder.indexOf(initialState)) 1 else -1
                            (slideInHorizontally(
                                animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMedium)
                            ) { w -> dir * w / 3 } + fadeIn(tween(190)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
                                    ) { w -> -dir * w / 3 } + fadeOut(tween(130))
                                )
                                .using(SizeTransform(clip = false))
                        },
                        label = "pageTransition",
                        modifier = Modifier.fillMaxWidth()
                    ) { page ->
                    when {
                        // System Approval Card
                        activeApproval != null -> {
                            val req = activeApproval!!
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = req.prompt,
                                    color = textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(iosRed)
                                            .clickable { ApprovalManager.respond(false) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = if (isId) "Tolak" else "Reject", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(iosGreen)
                                            .clickable { ApprovalManager.respond(true) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = if (isId) "Setuju" else "Allow", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Screenshot Preview Card
                        latestScreenshot != null -> {
                            val sc = latestScreenshot!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isId) "Tangkapan Layar Baru" else "New Screenshot",
                                    color = textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(iosBlue)
                                        .clickable {
                                            runCatching {
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(sc.uri, "image/*")
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(intent)
                                            }
                                        }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = if (isId) "Buka" else "Open", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // CARD 0: GLANCE ala notch Mac (Tahap 4)
                        page == PochiTab.GLANCE -> {
                            GlancePage(
                                context = context,
                                isId = isId,
                                selectedCity = selectedCity,
                                tasks = tasks,
                                mediaPlayback = mediaPlayback,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                borderHairline = borderHairline,
                                accentViolet = accentViolet,
                                onOpenTab = { goToPage(it) }
                            )
                        }


                        // CARD 1: SPOTIFY & PODCAST NOW PLAYING
                        page == PochiTab.WALLET -> {
                            MusicPage(
                                context = context,
                                mediaPlayback = mediaPlayback,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                spotifyGreen = spotifyGreen
                            )
                        }


                        // CARD 2: FACETIME / TELEGRAM POCHI CALL
                        page == PochiTab.CHAT -> {
                            CallPage(
                                context = context,
                                isId = isId,
                                callSeconds = callSeconds,
                                isListeningVoice = isListeningVoice,
                                voicePartialText = voicePartialText,
                                botState = botState,
                                activeEmote = activeEmote,
                                cardSurface = cardSurface,
                                borderHairline = borderHairline,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                iosRed = iosRed,
                                iosGreen = iosGreen,
                                iosBlue = iosBlue,
                                iosGray = iosGray,
                                onPoke = {
                                    pokeCount++
                                    val (em, _, _) = PochiMood.onPokeReaction(pokeCount)
                                    customEmote = em
                                    PochiSignals.playTapHaptic(context)
                                },
                                onToggleVoice = { toggleVoiceListening() },
                                onQuickAi = {
                                    customEmote = PochiEmote.EXCITED
                                    voicePartialText = if (isId) "Pochi siap melayani Tuan Andre!" else "Pochi is ready for Tuan Andre!"
                                    PochiSignals.playTapHaptic(context)
                                },
                                onEndCall = {
                                    customEmote = PochiEmote.BYE
                                    setMode(IslandMode.COLLAPSED)
                                    PochiSignals.playTapHaptic(context)
                                }
                            )
                        }


                        // CARD 3: APPLE HEALTH ACTIVITY RINGS (Direct match for Image 3)
                        page == PochiTab.TODAY -> {
                            val doneTasks = tasks.count { it.isDone }
                            val totalTasks = tasks.size.coerceAtLeast(1)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Grid widget 2x2 (Tahap 9, papan widget)
                                val doneTasksW = tasks.count { it.isDone }
                                val waterPctW = (waterGlasses * 100 / 8).coerceIn(0, 100)
                                val focusFracW = (completedFocusSessions / 4f).coerceIn(0f, 1f)
                                val sparkBuckets = (0 until 6).map { b ->
                                    waterLog.count { ts ->
                                        val h = Calendar.getInstance()
                                            .apply { timeInMillis = ts }
                                            .get(Calendar.HOUR_OF_DAY)
                                        h >= 6 + b * 3 && h < 9 + b * 3
                                    }.toFloat()
                                }
                                var sparkRun = 0f
                                val sparkCumulative = sparkBuckets.map { v ->
                                    sparkRun += v; sparkRun
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PochiWidget(modifier = Modifier.weight(1f)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    WidgetLabel(
                                                        text = if (isId) "AIR MINUM" else "WATER",
                                                        icon = Icons.Rounded.WaterDrop,
                                                        tint = PochiDesign.BoardBlue
                                                    )
                                                    WidgetValue(text = "${waterGlasses * 250} ml", size = 16)
                                                    WidgetFootnote(
                                                        text = if (isId) "$waterGlasses/8 gelas" else "$waterGlasses/8 glasses"
                                                    )
                                                }
                                                WidgetRing(
                                                    fraction = waterGlasses / 8f,
                                                    color = PochiDesign.BoardBlue,
                                                    centerText = "$waterPctW%"
                                                )
                                            }
                                            PochiSparkline(
                                                points = sparkCumulative,
                                                color = PochiDesign.BoardBlue,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(16.dp)
                                            )
                                        }
                                    }
                                    PochiWidget(modifier = Modifier.weight(1f)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    WidgetLabel(
                                                        text = if (isId) "TUGAS" else "TASKS",
                                                        icon = Icons.Rounded.CheckCircle,
                                                        tint = PochiDesign.BoardGreen
                                                    )
                                                    WidgetValue(
                                                        text = "$doneTasksW/${tasks.size}",
                                                        size = 16
                                                    )
                                                    WidgetFootnote(
                                                        text = if (isId) "${tasks.size - doneTasksW} tersisa" else "${tasks.size - doneTasksW} left"
                                                    )
                                                }
                                                WidgetRing(
                                                    fraction = if (tasks.isEmpty()) 0f else doneTasksW.toFloat() / tasks.size,
                                                    color = PochiDesign.BoardGreen,
                                                    centerText = if (tasks.isEmpty()) "0%" else "${doneTasksW * 100 / tasks.size}%"
                                                )
                                            }
                                            WidgetFootnote(
                                                text = tasks.firstOrNull { !it.isDone }?.title
                                                    ?: (if (isId) "Semua beres" else "All done")
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PochiWidget(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                WidgetLabel(
                                                    text = if (isId) "FOKUS" else "FOCUS",
                                                    icon = Icons.Rounded.Timer,
                                                    tint = PochiDesign.BoardOrange
                                                )
                                                WidgetValue(
                                                    text = "${completedFocusSessions * 25} mnt",
                                                    size = 16
                                                )
                                                WidgetFootnote(
                                                    text = if (isId) "$completedFocusSessions/4 sesi" else "$completedFocusSessions/4 sessions"
                                                )
                                            }
                                            WidgetRing(
                                                fraction = focusFracW,
                                                color = PochiDesign.BoardOrange,
                                                centerText = "${(focusFracW * 100).toInt()}%"
                                            )
                                        }
                                    }
                                    PochiWidget(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                WidgetLabel(
                                                    text = if (isId) "BUDGET" else "BUDGET",
                                                    icon = Icons.Rounded.AccountBalanceWallet,
                                                    tint = PochiDesign.AccentVioletSoft
                                                )
                                                WidgetValue(
                                                    text = "Rp ${dailyBudgetRp / 1000}rb",
                                                    size = 16
                                                )
                                                WidgetFootnote(
                                                    text = if (isId) "anggaran harian" else "daily budget"
                                                )
                                            }
                                            WidgetRing(
                                                fraction = 0.65f,
                                                color = PochiDesign.AccentVioletSoft,
                                                centerText = "65%"
                                            )
                                        }
                                    }
                                }
                                // Grafik minum per 3 jam (Tahap 6, ala Nusa)
                                val bucketLabels = listOf("06", "09", "12", "15", "18", "21")
                                val bucketMl = (0 until 6).map { b ->
                                    waterLog.count { ts ->
                                        val h = java.util.Calendar.getInstance()
                                            .apply { timeInMillis = ts }
                                            .get(java.util.Calendar.HOUR_OF_DAY)
                                        h >= 6 + b * 3 && h < 9 + b * 3
                                    } * 250
                                }
                                val bucketMax = (bucketMl.maxOrNull() ?: 0).coerceAtLeast(250)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    bucketMl.forEachIndexed { i, ml ->
                                        val barHeight by animateDpAsState(
                                            targetValue = (6 + 18 * ml / bucketMax).dp,
                                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 210f),
                                            label = "waterBar"
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(20.dp)
                                                    .height(barHeight)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (ml > 0) iosBlue else borderHairline
                                                    )
                                            )
                                            Text(
                                                text = bucketLabels[i],
                                                color = textSecondary,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                }

                                // Large Tactile Buttons: Log Water & Start Focus
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Big Blue Water Button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(iosBlue)
                                            .pochiPress {
                                                PochiPrefs.addWaterGlass(context)
                                                customEmote = PochiEmote.HAPPY
                                                PochiSignals.playSuccessHaptic(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.WaterDrop,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "+ Minum 250ml",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }

                                    // Big Green Focus Button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(iosGreen)
                                            .pochiPress {
                                                isFocusRunning = true
                                                isFocusPaused = false
                                                focusRemainingSeconds = 25 * 60
                                                goToPage(PochiTab.FOCUS)
                                                customEmote = PochiEmote.FOCUS
                                                PochiSignals.playSuccessHaptic(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Fokus 25m",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // CARD 4: WEATHER & UV RAINBOW EQUALIZER (Direct match for Image 3)
                        page == PochiTab.LIFE -> {
                            val nextPrayer = PrayerScheduleHelper.getNextPrayer(selectedCity)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Widget cuaca besar (Tahap 9, ala papan):
                                // suhu raksasa + strip suhu seminggu.
                                val lifeCal = Calendar.getInstance()
                                val lifeMondayOffset =
                                    (lifeCal.get(Calendar.DAY_OF_WEEK) + 5) % 7
                                val lifeMonday = (lifeCal.clone() as Calendar).apply {
                                    add(Calendar.DAY_OF_MONTH, -lifeMondayOffset)
                                }
                                val lifeTemps = listOf(31, 32, 33, 32, 31, 30, 29)
                                val lifeLetters = listOf("S", "S", "R", "K", "J", "S", "M")
                                PochiWidget(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            WidgetLabel(
                                                text = selectedCity.uppercase(),
                                                icon = Icons.Rounded.WbSunny,
                                                tint = PochiDesign.BoardOrange
                                            )
                                            WidgetValue(text = "$liveTemp°", size = 32)
                                            WidgetFootnote(
                                                text = if (isId) "Cerah berawan • UV High 8" else "Partly cloudy • UV High 8",
                                                color = PochiDesign.BoardOrange
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            lifeTemps.forEachIndexed { i, t ->
                                                val isToday = i == lifeMondayOffset
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = lifeLetters[i],
                                                        color = if (isToday) PochiDesign.BoardBlue else textSecondary,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "$t°",
                                                        color = if (isToday) textPrimary else textSecondary,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                // Kartu data mewah (Tahap 8.1): grafik suhu
                                // per jam — satu-satunya tempat kartu ini.
                                val hourlyTemps = listOf(
                                    27f, 28f, 30f, 32f, 33f, 32.5f, 31f, 29f, 28f, 27f
                                )
                                PochiDataCard(
                                    title = selectedCity,
                                    chipText = if (isId) "Per jam" else "Hourly",
                                    heroLabel = if (isId) "Suhu sekarang • Cerah berawan" else "Now • Partly cloudy",
                                    heroValue = "$liveTemp°C",
                                    footer = if (isId) {
                                        "Tertinggi ${(hourlyTemps.maxOrNull() ?: 0f).toInt()}°C • Terendah ${(hourlyTemps.minOrNull() ?: 0f).toInt()}°C • UV High indeks 8"
                                    } else {
                                        "High ${(hourlyTemps.maxOrNull() ?: 0f).toInt()}°C • Low ${(hourlyTemps.minOrNull() ?: 0f).toInt()}°C • UV High index 8"
                                    },
                                    points = hourlyTemps,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                // Large Buttons: Prayer Schedule & Weather Refresh
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(iosAmber)
                                            .clickable {
                                                PochiSignals.playTapHaptic(context)
                                                customEmote = PochiEmote.HAPPY
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Mosque,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "${nextPrayer.prayer.nameId} ${nextPrayer.prayer.formatted}",
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(cardSurface)
                                            .border(1.dp, borderHairline, RoundedCornerShape(23.dp))
                                            .clickable {
                                                isRefreshingWeather = true
                                                customEmote = PochiEmote.CALM
                                                PochiSignals.playTapHaptic(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                tint = textPrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Update Cuaca",
                                                color = textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // CARD 5: ACTIVE TIMER & RIDE TRACKER (Direct match for Image 1 Yandex Go & Image 3 Wash Timer)
                        else -> {
                            val mins = focusRemainingSeconds / 60
                            val secs = focusRemainingSeconds % 60
                            val timeStr = String.format(Locale.US, "%02d:%02d", mins, secs)
                            val progress = 1f - (focusRemainingSeconds.toFloat() / focusTotalSeconds)

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    WidgetLabel(
                                        text = if (isId) "SESI FOKUS" else "FOCUS SESSION",
                                        icon = Icons.Rounded.Timer,
                                        tint = PochiDesign.BoardOrange
                                    )
                                    Text(
                                        text = timeStr,
                                        color = textPrimary,
                                        fontSize = 26.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // Route Tracker Bar with Animated Moving Marker
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(PochiDesign.BoardOrange, PochiDesign.BoardRed)
                                                )
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress.coerceIn(0.05f, 0.95f)),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                                .border(2.dp, PochiDesign.BoardOrange, CircleShape)
                                        )
                                    }
                                }

                                // 3 Large Buttons: Pause/Resume, Stop, +5m
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(iosBlue)
                                            .clickable {
                                                isFocusPaused = !isFocusPaused
                                                PochiSignals.playTapHaptic(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFocusPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = if (isFocusPaused) "Lanjut" else "Jeda",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(iosRed)
                                            .clickable {
                                                isFocusRunning = false
                                                focusRemainingSeconds = 25 * 60
                                                goToPage(PochiTab.TODAY)
                                                PochiSignals.playTapHaptic(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Stop,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "Selesai",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(iosGray)
                                            .clickable {
                                                focusRemainingSeconds += 5 * 60
                                                PochiSignals.playTapHaptic(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+5 Min",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }

                    // --- TITIK HALAMAN (Tahap 6, ala Nusa) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            pageOrder.forEach { tab ->
                                val isSel = activeTab == tab
                                val dotWidth by animateDpAsState(
                                    targetValue = if (isSel) 16.dp else 6.dp,
                                    animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
                                    label = "dotWidth"
                                )
                                val dotColorMix by animateFloatAsState(
                                    targetValue = if (isSel) 1f else 0f,
                                    animationSpec = tween(200),
                                    label = "dotColor"
                                )
                                val dotColor = lerp(borderHairline, PochiDesign.BoardBlue, dotColorMix)
                                Box(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .width(dotWidth + 10.dp)
                                        .pochiPress { goToPage(tab) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(6.dp)
                                            .width(dotWidth)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                }
                            }
                        }
                    }

                    // --- DOCK KACA (Tahap 4): pengganti switcher lama ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(PochiDesign.WidgetSurface)
                                .border(1.dp, borderHairline, RoundedCornerShape(20.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            // Tahap 8: dock memakai urutan halaman yang
                            // sama dengan titik & geser (konsisten).
                            pageOrder.forEach { tab ->
                                val isSel = activeTab == tab
                                val selBg = if (isSel) {
                                    Brush.linearGradient(listOf(PochiDesign.BoardBlue, PochiDesign.BoardBlue))
                                } else {
                                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(selBg)
                                        .pochiPress { goToPage(tab) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.titleEn,
                                        tint = if (isSel) Color.White else textSecondary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Minimalist Apple Activity Ring Widget (Canvas Arc)
 */
@Composable
private fun ActivityRingItem(
    color: Color,
    progress: Float,
    label: String,
    sublabel: String
) {
    // Tahap 7: nilai ring dianimasikan pegas agar perubahan terasa cair.
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 90f),
        label = "ringProgress"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 5.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                // Background track ring
                drawCircle(
                    color = color.copy(alpha = 0.2f),
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeWidth)
                )

                // Progress active arc
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = sublabel,
            color = Color(0xFF8E8E93),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Jam kecil mandiri untuk header island (Tahap 4): state tik 1 detik
 * terkurung di sini sehingga hanya teks jam yang direkomposisi tiap
 * detik, bukan seluruh panel island.
 */
@Composable
fun PochiClockText(
    color: Color = PochiDesign.TextSecondary,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    var now by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            now = sdf.format(Date())
            delay(1000)
        }
    }
    Text(
        text = now.ifBlank { "--:--" },
        color = color,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * Dispatches hardware media key events (Play/Pause, Next, Prev) to the active media player.
 */
private fun dispatchMediaKey(context: Context, keyCode: Int) {
    runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventTime = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
    }
}

@Composable
private fun GlancePage(
    context: Context,
    isId: Boolean,
    selectedCity: String,
    tasks: List<TodayTask>,
    mediaPlayback: MediaPlaybackInfo,
    textPrimary: Color,
    textSecondary: Color,
    borderHairline: Color,
    accentViolet: Color,
    onOpenTab: (PochiTab) -> Unit
) {
    // Tahap 9: Glance menjadi grid papan widget — musik di kiri,
    // strip kalender + agenda ringkas di kanan.
    val nextPrayer = PrayerScheduleHelper.getNextPrayer(selectedCity)
    val remaining = tasks.count { !it.isDone }

    val todayCal = Calendar.getInstance()
    val dow = todayCal.get(Calendar.DAY_OF_WEEK)
    val mondayOffset = (dow + 5) % 7
    val monday = (todayCal.clone() as Calendar).apply {
        add(Calendar.DAY_OF_MONTH, -mondayOffset)
    }
    val weekDays = (0..6).map { i ->
        (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
    }
    val dayLetters = listOf("S", "S", "R", "K", "J", "S", "M")
    val monthName = SimpleDateFormat("MMMM yyyy", Locale("id", "ID")).format(todayCal.time)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Widget musik (kiri)
        PochiWidget(
            modifier = Modifier.weight(1.08f),
            onClick = { onOpenTab(PochiTab.WALLET) }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                WidgetLabel(
                    text = if (isId) "MUSIK" else "MUSIC",
                    icon = Icons.Rounded.MusicNote,
                    tint = PochiDesign.BoardBlue
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaPlayback.title.ifBlank { if (isId) "Tidak ada media" else "No media" },
                            color = textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mediaPlayback.artist.ifBlank { if (isId) "Ketuk untuk musik" else "Tap for music" },
                            color = textSecondary,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                PochiSignals.playTapHaptic(context)
                            }
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                PochiSignals.playTapHaptic(context)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mediaPlayback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                                PochiSignals.playTapHaptic(context)
                            }
                    )
                }
                if (mediaPlayback.isPlaying) {
                    LiveEqualizer(
                        color = PochiDesign.SpotifyGreen,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // Kolom kanan: kalender + agenda
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PochiWidget(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenTab(PochiTab.TODAY) }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    WidgetLabel(
                        text = monthName.uppercase(),
                        icon = Icons.Rounded.CalendarMonth,
                        tint = PochiDesign.BoardBlue
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        weekDays.forEachIndexed { i, dayCal ->
                            val dateNum = dayCal.get(Calendar.DAY_OF_MONTH)
                            val isToday = i == mondayOffset
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = dayLetters[i],
                                    color = textSecondary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(21.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(
                                            if (isToday) PochiDesign.BoardBlue else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dateNum",
                                        color = if (isToday) Color.White else textPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            PochiWidget(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onOpenTab(PochiTab.TODAY) }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mosque,
                            contentDescription = null,
                            tint = PochiDesign.AccentGold,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${nextPrayer.prayer.nameId} ${nextPrayer.prayer.formatted}",
                            color = textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = PochiDesign.BoardGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isId) "$remaining tugas tersisa • $selectedCity" else "$remaining tasks left • $selectedCity",
                            color = textSecondary,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPage(
    context: Context,
    mediaPlayback: MediaPlaybackInfo,
    textPrimary: Color,
    textSecondary: Color,
    spotifyGreen: Color
) {
    val title = mediaPlayback.title.ifBlank { "Cruel Summer" }
    val artist = mediaPlayback.artist.ifBlank { "Taylor Swift • Lover" }
    val isPlaying = mediaPlayback.isPlaying

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PochiDesign.WidgetSurface)
            .border(1.dp, PochiDesign.WidgetBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Middle row: Album Art Squircle + Song Title + Green Checkmark
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Album Artwork Squircle (52dp)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Track Info & Verification
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = title,
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Verified",
                        tint = spotifyGreen,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = artist,
                    color = textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Timeline Slider
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF2C2C2E))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .fillMaxHeight()
                        .background(spotifyGreen)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "1:20", color = textSecondary, fontSize = 10.sp)
                Text(text = "-2:16", color = textSecondary, fontSize = 10.sp)
            }
        }

        // Controls: Shuffle, Prev, Big Center White Play/Pause Button (50dp), Next, Repeat
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                tint = textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { PochiSignals.playTapHaptic(context) }
            )
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clickable {
                        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                        PochiSignals.playTapHaptic(context)
                    }
            )
            // Big Central Circular White Play/Pause Button (50dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pochiPress {
                        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                        PochiSignals.playTapHaptic(context)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(28.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clickable {
                        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                        PochiSignals.playTapHaptic(context)
                    }
            )
            Icon(
                imageVector = Icons.Rounded.Repeat,
                contentDescription = "Repeat",
                tint = textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { PochiSignals.playTapHaptic(context) }
            )
        }
    }
}

@Composable
private fun CallPage(
    context: Context,
    isId: Boolean,
    callSeconds: Int,
    isListeningVoice: Boolean,
    voicePartialText: String,
    botState: BotState,
    activeEmote: PochiEmote,
    cardSurface: Color,
    borderHairline: Color,
    textPrimary: Color,
    textSecondary: Color,
    iosRed: Color,
    iosGreen: Color,
    iosBlue: Color,
    iosGray: Color,
    onPoke: () -> Unit,
    onToggleVoice: () -> Unit,
    onQuickAi: () -> Unit,
    onEndCall: () -> Unit
) {
    val mins = callSeconds / 60
    val secs = callSeconds % 60
    val callTimeFormatted = String.format(Locale.US, "%02d:%02d", mins, secs)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Middle row: Big Squircle Video Frame with Pochi Mascot + Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar Squircle (54dp) containing animated Pochi Mascot eyes
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardSurface)
                    .border(1.dp, borderHairline, RoundedCornerShape(18.dp))
                    .clickable {
                        onPoke()
                    },
                contentAlignment = Alignment.Center
            ) {
                PochiMascot(
                    state = botState,
                    emoteOverride = activeEmote,
                    size = 36.dp,
                    showBlackBacking = false
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Pochi Assistant",
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isListeningVoice) iosRed else iosGreen)
                    )
                    Text(
                        text = if (isListeningVoice) (if (isId) "Mendengarkan..." else "Listening...") else "Live • $callTimeFormatted",
                        color = textSecondary,
                        fontSize = 12.sp
                    )
                }
                if (voicePartialText.isNotBlank()) {
                    Text(
                        text = "\"$voicePartialText\"",
                        color = iosBlue,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3 Large Round Buttons (FaceTime / Telegram Call style: Mic, Tanya AI, End Call)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Button 1: Mic / Mute
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isListeningVoice) iosRed else iosGray)
                    .clickable {
                        onToggleVoice()
                        PochiSignals.playTapHaptic(context)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isListeningVoice) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        contentDescription = "Mic",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (isListeningVoice) "Stop" else "Bicara",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Button 2: Quick AI Prompt
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(iosBlue)
                    .clickable {
                        onQuickAi()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "AI",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tanya AI",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Button 3: End / Dismiss Call
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(iosRed)
                    .clickable {
                        onEndCall()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tutup",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Tahap 7: umpan balik fisik saat ditekan — elemen mengecil ke 0,92
 * lalu memantul pulih dengan pegas longgar. Menggantikan clickable
 * polos pada tombol-tombol utama island agar terasa hidup.
 */
@Composable
private fun Modifier.pochiPress(onTap: () -> Unit): Modifier {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    scope.launch { scale.animateTo(0.92f, tween(80)) }
                    tryAwaitRelease()
                    scope.launch {
                        scale.animateTo(
                            1f,
                            spring(dampingRatio = 0.45f, stiffness = 400f)
                        )
                    }
                },
                onTap = { onTap() }
            )
        }
}

/**
 * Tahap 7: equalizer batang hidup untuk status media — empat batang
 * menari dengan tempo berbeda selama musik diputar, menggantikan
 * ikon statis.
 */
@Composable
private fun LiveEqualizer(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveEq")
    val phases = listOf(340, 470, 390, 520).mapIndexed { i, duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eqBar$i"
        )
    }
    Canvas(modifier = modifier) {
        val barWidth = size.width / 7f
        phases.forEachIndexed { i, phase ->
            val barHeight = size.height * phase.value
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = i * barWidth * 1.8f,
                    y = size.height - barHeight
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * Tahap 7: partikel "z/Z" yang melayang naik dan memudar saat Pochi
 * tertidur — penanda tidur yang lembut ala pendamping Nusa.
 */
@Composable
private fun SleepZzz(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sleepZzz")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "zzzDrift"
    )
    Box(modifier = modifier) {
        val driftB = (drift + 0.5f) % 1f
        Text(
            text = "z",
            color = PochiDesign.AccentVioletSoft.copy(alpha = 0.75f - drift * 0.6f),
            fontSize = (8 + drift * 3).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = (drift * 5).dp, y = (-drift * 7).dp)
        )
        Text(
            text = "Z",
            color = PochiDesign.AccentVioletSoft.copy(alpha = 0.75f - driftB * 0.6f),
            fontSize = (10 + driftB * 4).sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = (6 + driftB * 6).dp, y = (-4 - driftB * 8).dp)
        )
    }
}
