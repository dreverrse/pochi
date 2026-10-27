package com.pochi.assistant

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    EXPANDED
}

/**
 * Pochi Dynamic Island - Pure iOS Live Activity Aesthetic.
 *
 * Modeled directly on official Apple Dynamic Island live activities:
 * 1. Podcasts / Spotify (Media Player with Album Art, Progress Bar, Large Play/Pause)
 * 2. Telegram / FaceTime (Pochi Live Call with expressive eyes avatar & Large Red/Gray Buttons)
 * 3. Apple Health Activity Rings (Hydration 💧, Tasks 🎯, Focus ⚡, Budget 💰)
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
    onPowerOffService: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Authentic iOS Deep OLED Black Palette
    val islandBlack = Color(0xFF000000)
    val cardSurface = Color(0xFF141416)
    val borderHairline = Color(0xFF26262B)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF8E8E93)

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
    var activeTab by remember { mutableStateOf(PochiTab.CHAT) }
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

    // Time ticker for header clock
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(1000)
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
        islandMode = mode
        onExpandChange(mode == IslandMode.EXPANDED)
        if (mode == IslandMode.COLLAPSED) {
            if (isListeningVoice) {
                isListeningVoice = false
                runCatching { speechRecognizerRef?.stopListening() }
            }
        }
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
    val expandedHeight = 224.dp

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
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "islandWidth"
    ) { mode ->
        if (mode == IslandMode.COLLAPSED) collapsedWidth else expandedWidth
    }

    val islandHeightAnim by transition.animateDp(
        transitionSpec = {
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "islandHeight"
    ) { mode ->
        if (mode == IslandMode.COLLAPSED) collapsedHeight else expandedHeight
    }

    // Authentic Apple Squircle Corner Radii: 18dp for capsule pill, 32dp for expanded card
    val islandCornerRadius by transition.animateDp(
        transitionSpec = {
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "islandCornerRadius"
    ) { mode ->
        if (mode == IslandMode.COLLAPSED) 18.dp else 32.dp
    }

    val expandedContentAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 160) },
        label = "expandedAlpha"
    ) { mode -> if (mode == IslandMode.COLLAPSED) 0f else 1f }

    val collapsedContentAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 140) },
        label = "collapsedAlpha"
    ) { mode -> if (mode == IslandMode.COLLAPSED) 1f else 0f }

    val activeEmote = customEmote ?: idleEmote ?: PochiEmote.SMILING

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
            PochiMascot(
                state = botState,
                emoteOverride = activeEmote,
                size = 36.dp,
                showBlackBacking = true
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
                    if (islandMode == IslandMode.COLLAPSED) {
                        scaleX = breathScale
                        scaleY = 2f - breathScale
                    }
                }
                .background(islandBlack, islandShape)
                .border(1.dp, borderHairline, islandShape)
        ) {
            // ================= 1. COLLAPSED DYNAMIC ISLAND CAPSULE =================
            if (collapsedContentAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = collapsedContentAlpha }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var accumulatedX = 0f
                                var accumulatedY = 0f
                                var isDragging = false
                                val touchSlop = viewConfiguration.touchSlop

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUp()) {
                                        if (!isDragging) {
                                            customEmote = PochiEmote.HAPPY
                                            setMode(IslandMode.EXPANDED)
                                        } else {
                                            onDragEnd()
                                        }
                                        break
                                    }
                                    if (change.isConsumed) break

                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    accumulatedX += dx
                                    accumulatedY += dy

                                    if (!isDragging) {
                                        if (kotlin.math.hypot(accumulatedX, accumulatedY) > touchSlop) {
                                            isDragging = true
                                            change.consume()
                                            onDragBubble(accumulatedX, accumulatedY)
                                        }
                                    } else {
                                        change.consume()
                                        onDragBubble(dx, dy)
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Pochi Mascot's glowing white eyes on the left
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
                        mediaPlayback.isPlaying -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = spotifyGreen,
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
                                        .background(iosGreen)
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

            // ================= 2. EXPANDED LIVE ACTIVITY CARD =================
            if (expandedContentAlpha > 0.01f) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expandedContentAlpha }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // --- TOP BAR: Sleek Apple Live Activity Pill Switcher & Dismiss ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Card switcher pills: Media, Call, Activity, Weather, Timer
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardSurface)
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            val availableTabs = listOf(
                                PochiTab.CHAT,
                                PochiTab.WALLET,
                                PochiTab.TODAY,
                                PochiTab.LIFE
                            )
                            availableTabs.forEach { tab ->
                                val isSel = activeTab == tab
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(if (isSel) tab.accent.copy(alpha = 0.25f) else Color.Transparent)
                                        .clickable {
                                            activeTab = tab
                                            PochiSignals.playTapHaptic(context)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.titleEn,
                                        tint = if (isSel) tab.accent else textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        // Right: Clock + Minimal Close Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = currentTimeString.ifBlank { "21:31" },
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(cardSurface)
                                    .clickable { setMode(IslandMode.COLLAPSED) },
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

                    // --- ACTIVE CARD BODY ---
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
                                                    setDataAndType(android.net.Uri.parse(sc.uri), "image/*")
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

                        // CARD 1: SPOTIFY & PODCAST NOW PLAYING (Direct match for Image 1)
                        activeTab == PochiTab.WALLET -> {
                            val title = mediaPlayback.title.ifBlank { "Cruel Summer" }
                            val artist = mediaPlayback.artist.ifBlank { "Taylor Swift • Lover" }
                            val isPlaying = mediaPlayback.isPlaying

                            Column(
                                modifier = Modifier.fillMaxWidth(),
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
                                            .clickable {
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

                        // CARD 2: FACETIME / TELEGRAM POCHI CALL (Direct match for Image 1)
                        activeTab == PochiTab.CHAT -> {
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
                                                pokeCount++
                                                val (em, _, _) = PochiMood.onPokeReaction(pokeCount)
                                                customEmote = em
                                                PochiSignals.playTapHaptic(context)
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
                                                toggleVoiceListening()
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
                                                customEmote = PochiEmote.EXCITED
                                                voicePartialText = if (isId) "Pochi siap membantumu!" else "Pochi is ready to help!"
                                                PochiSignals.playTapHaptic(context)
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
                                                customEmote = PochiEmote.BYE
                                                setMode(IslandMode.COLLAPSED)
                                                PochiSignals.playTapHaptic(context)
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

                        // CARD 3: APPLE HEALTH ACTIVITY RINGS (Direct match for Image 3)
                        activeTab == PochiTab.TODAY -> {
                            val doneTasks = tasks.count { it.isDone }
                            val totalTasks = tasks.size.coerceAtLeast(1)

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 4 Rings Preview Row: Water 💧, Tasks 🎯, Focus ⚡, Budget 💰
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Ring 1: Water
                                    ActivityRingItem(
                                        color = iosBlue,
                                        progress = (waterGlasses / 8f).coerceIn(0f, 1f),
                                        label = "${(waterGlasses * 0.25f)}L",
                                        sublabel = "Air 💧"
                                    )
                                    // Ring 2: Tasks
                                    ActivityRingItem(
                                        color = iosGreen,
                                        progress = (doneTasks.toFloat() / totalTasks).coerceIn(0f, 1f),
                                        label = "$doneTasks/$totalTasks",
                                        sublabel = "Tugas 🎯"
                                    )
                                    // Ring 3: Focus
                                    ActivityRingItem(
                                        color = iosAmber,
                                        progress = (completedFocusSessions / 4f).coerceIn(0f, 1f),
                                        label = "${completedFocusSessions * 25}m",
                                        sublabel = "Fokus ⚡"
                                    )
                                    // Ring 4: Budget
                                    ActivityRingItem(
                                        color = iosPurple,
                                        progress = 0.65f,
                                        label = "${dailyBudgetRp / 1000}k",
                                        sublabel = "Budget 💰"
                                    )
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
                                            .clickable {
                                                PochiPrefs.addWaterGlass(context)
                                                customEmote = PochiEmote.HAPPY
                                                PochiSignals.playSuccessHaptic(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+ 💧 Minum 250ml",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }

                                    // Big Green Focus Button
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(23.dp))
                                            .background(iosGreen)
                                            .clickable {
                                                isFocusRunning = true
                                                isFocusPaused = false
                                                focusRemainingSeconds = 25 * 60
                                                activeTab = PochiTab.FOCUS
                                                customEmote = PochiEmote.FOCUS
                                                PochiSignals.playSuccessHaptic(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "▶ Fokus 25m",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        // CARD 4: WEATHER & UV RAINBOW EQUALIZER (Direct match for Image 3)
                        activeTab == PochiTab.LIFE -> {
                            val nextPrayer = PrayerScheduleHelper.getNextPrayer(selectedCity)

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Header: City & UV
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$selectedCity • 32°C ☀️",
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "UV High • Indeks 8",
                                        color = iosAmber,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                // Rainbow Gradient Equalizer Bars (Yellow -> Orange -> Magenta -> Purple as in Image 3!)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    val barColors = listOf(
                                        Color(0xFF34C759),
                                        Color(0xFF30D158),
                                        Color(0xFFFFD60A),
                                        Color(0xFFFF9F0A),
                                        Color(0xFFFF453A),
                                        Color(0xFFFF375F),
                                        Color(0xFFBF5AF2),
                                        Color(0xFF5E5CE6),
                                        Color(0xFF0A84FF),
                                        Color(0xFF64D2FF)
                                    )
                                    val heights = listOf(0.3f, 0.45f, 0.65f, 0.9f, 1f, 0.85f, 0.7f, 0.5f, 0.4f, 0.35f)
                                    heights.forEachIndexed { idx, frac ->
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .fillMaxHeight(frac)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(barColors[idx % barColors.size])
                                        )
                                    }
                                }

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
                                        Text(
                                            text = "🕌 ${nextPrayer.prayer.nameId} ${nextPrayer.prayer.formatted}",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
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
                                        Text(
                                            text = "🔄 Update Cuaca",
                                            color = textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
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
                                    Text(
                                        text = if (isId) "Sesi Fokus Produktif" else "Focus Session",
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = timeStr,
                                        color = iosAmber,
                                        fontSize = 16.sp,
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
                                                    listOf(iosGreen, iosAmber, iosRed)
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
                                                .border(2.dp, iosAmber, CircleShape)
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
                                        Text(
                                            text = if (isFocusPaused) "▶ Lanjut" else "⏸ Jeda",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
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
                                                activeTab = PochiTab.TODAY
                                                PochiSignals.playTapHaptic(context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "⏹ Selesai",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
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
                    sweepAngle = 360f * progress,
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

/**
 * Toggles camera torch / flashlight.
 */
private fun setTorchState(context: Context, enabled: Boolean): Boolean {
    return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull() ?: return false
        cm.setTorchMode(id, enabled)
        true
    } catch (_: Exception) {
        false
    }
}
