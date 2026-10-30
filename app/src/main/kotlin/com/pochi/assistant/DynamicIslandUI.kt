package com.pochi.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class IslandMode {
    COLLAPSED,
    EXPANDED
}

data class NotchChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val richCard: PochiRichCard? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val timeLabel: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Custom Notch Shape with flat top (`y = 0`), smooth top-left & top-right notch fillet ears,
 * and squircle bottom corners. Has NO top border so it fuses seamlessly with the phone's top bar.
 */
fun createSeamlessNotchShape(bottomCornerPx: Float, topEarPx: Float = 0f) = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    val r = bottomCornerPx.coerceIn(0f, minOf(w / 2f, h / 2f))
    val ear = topEarPx.coerceIn(0f, r * 0.45f)

    moveTo(0f, 0f)
    lineTo(w, 0f)
    // Right edge going down from top ear
    if (ear > 0f) {
        quadraticBezierTo(w - ear, 0f, w - ear, ear)
        lineTo(w - ear, h - r)
        quadraticBezierTo(w - ear, h, w - ear - r, h)
        lineTo(ear + r, h)
        quadraticBezierTo(ear, h, ear, h - r)
        lineTo(ear, ear)
        quadraticBezierTo(ear, 0f, 0f, 0f)
    } else {
        lineTo(w, h - r)
        quadraticBezierTo(w, h, w - r, h)
        lineTo(r, h)
        quadraticBezierTo(0f, h, 0f, h - r)
        lineTo(0f, 0f)
    }
    close()
}

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

    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Core states
    var mode by remember { mutableStateOf(IslandMode.COLLAPSED) }
    val isExpanded = mode == IslandMode.EXPANDED

    var lastActiveTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isIdleMini by remember { mutableStateOf(false) }

    // Live signals
    val screenApp by PochiSignals.screenApp.collectAsState()
    val mediaPlayback by PochiSignals.mediaPlayback.collectAsState()
    val latestScreenshot by PochiSignals.latestScreenshot.collectAsState()
    val droppedFile by PochiSignals.droppedFile.collectAsState()
    val expandSignal by PochiSignals.islandExpandRequest.collectAsState()
    val dismissSignal by ApprovalManager.dismissSignal.collectAsState()
    val activeApprovalReq by ApprovalManager.activeRequest.collectAsState()
    val selectedCity by PochiPrefs.cityFlow.collectAsState()

    // Expressive Pochi state (autonomous mood based on what Tuan Andre is doing)
    var customEmote by remember { mutableStateOf<PochiEmote?>(null) }
    var isThinking by remember { mutableStateOf(false) }
    var isListeningVoice by remember { mutableStateOf(false) }
    var pokeCount by remember { mutableIntStateOf(0) }

    // Chat state inside Notch
    val notchMessages = remember {
        mutableStateListOf(
            NotchChatMessage(
                isUser = false,
                text = "Jarvis aktif di Notch Tuan Andre. Ketik apa saja (misal: 'harga BTC', 'buat grafik', 'suhu sekarang', 'jadwal sholat') atau drop file ke sini!"
            )
        )
    }
    var chatInput by remember { mutableStateOf("") }
    var showDropDrawer by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Autonomous expression cycle when collapsed / working
    val autoEmote by produceState(initialValue = PochiEmote.SMILING, screenApp, mediaPlayback.isPlaying) {
        val emotions = listOf(
            PochiEmote.SMILING,
            PochiEmote.HAPPY,
            PochiEmote.WINK_LEFT,
            PochiEmote.FOCUS,
            PochiEmote.CUTE,
            PochiEmote.EXCITED,
            PochiEmote.CALM
        )
        var idx = 0
        while (true) {
            value = when {
                mediaPlayback.isPlaying -> PochiEmote.HAPPY
                screenApp.contains("Code", true) || screenApp.contains("Termux", true) -> PochiEmote.FOCUS
                screenApp.contains("YouTube", true) || screenApp.contains("TikTok", true) -> PochiEmote.EXCITED
                screenApp.contains("WhatsApp", true) || screenApp.contains("Telegram", true) -> PochiEmote.SMILING
                else -> emotions[idx % emotions.size]
            }
            idx++
            delay(5500)
        }
    }

    val effectiveEmote = when {
        isThinking -> PochiEmote.THINKING
        isListeningVoice -> PochiEmote.SURPRISED
        customEmote != null -> customEmote!!
        else -> autoEmote
    }

    // Auto-shrink into semi-transparent mini-notch when unused for 4.5s
    LaunchedEffect(isExpanded, lastActiveTimestamp, isThinking, isListeningVoice, activeApprovalReq) {
        if (isExpanded || isThinking || isListeningVoice || activeApprovalReq != null) {
            isIdleMini = false
        } else {
            isIdleMini = false
            delay(4500)
            isIdleMini = true
        }
    }

    // Outside tap collapses the expanded notch
    LaunchedEffect(dismissSignal) {
        if (dismissSignal > 0L && isExpanded) {
            mode = IslandMode.COLLAPSED
            showDropDrawer = false
            onExpandChange(false)
        }
    }

    // External expand trigger (e.g. from Main App purple AI button or file picker return)
    LaunchedEffect(expandSignal) {
        if (expandSignal > 0L && !isExpanded) {
            lastActiveTimestamp = System.currentTimeMillis()
            isIdleMini = false
            mode = IslandMode.EXPANDED
            onExpandChange(true)
        }
    }

    // Handle dropped or picked file automatically!
    fun submitToJarvis(promptText: String, fileToProcess: DroppedFileInfo? = null) {
        val clean = promptText.trim()
        if (clean.isBlank() && fileToProcess == null) return
        if (isThinking) return

        lastActiveTimestamp = System.currentTimeMillis()
        val userLabel = if (fileToProcess != null) {
            if (clean.isNotBlank()) "📎 ${fileToProcess.fileName}\n$clean" else "📎 Menjatuhkan file: ${fileToProcess.fileName}"
        } else {
            clean
        }

        notchMessages.add(NotchChatMessage(isUser = true, text = userLabel))
        PochiDb.getInstance(context).insertChatMessage("user", userLabel)
        chatInput = ""
        showDropDrawer = false
        isThinking = true
        customEmote = PochiEmote.THINKING

        coroutineScope.launch {
            runCatching { listState.animateScrollToItem((notchMessages.size - 1).coerceAtLeast(0)) }
            val reply = PochiJarvisEngine.processQuery(context, clean, fileToProcess)
            val fullText = if (!reply.actionFeedback.isNullOrBlank()) {
                "${reply.text}\n[${reply.actionFeedback}]"
            } else {
                reply.text
            }
            notchMessages.add(
                NotchChatMessage(
                    isUser = false,
                    text = fullText,
                    richCard = reply.richCard
                )
            )
            PochiDb.getInstance(context).insertChatMessage("pochi", fullText)
            isThinking = false
            customEmote = reply.emote
            PochiSignals.playSuccessHaptic(context)
            runCatching { listState.animateScrollToItem((notchMessages.size - 1).coerceAtLeast(0)) }
        }
    }

    LaunchedEffect(droppedFile) {
        val file = droppedFile ?: return@LaunchedEffect
        PochiSignals.consumeDroppedFile()
        lastActiveTimestamp = System.currentTimeMillis()
        isIdleMini = false
        if (!isExpanded) {
            mode = IslandMode.EXPANDED
            onExpandChange(true)
        }
        submitToJarvis(chatInput, file)
    }

    // Speech Recognizer inside Notch
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListeningVoice = true }
            override fun onBeginningOfSpeech() { isListeningVoice = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListeningVoice = false }
            override fun onError(error: Int) { isListeningVoice = false }
            override fun onResults(results: Bundle?) {
                isListeningVoice = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    submitToJarvis(spoken)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                    chatInput = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            runCatching { speechRecognizer?.destroy() }
        }
    }

    // Update service window height when expanded
    LaunchedEffect(isExpanded, showDropDrawer, activeApprovalReq) {
        if (isExpanded) {
            val targetDp = if (isLandscape) 310.dp else if (showDropDrawer) 470.dp else 430.dp
            with(density) {
                onExpandedHeightChange(targetDp.toPx().toInt())
            }
        }
    }

    // Edge Peek Bubble if user dragged the notch all the way to the screen edge
    if (isHidden && peekMode) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xCC0A0A12))
                .border(1.dp, Color(0xFF9D4EDD), CircleShape)
                .clickable {
                    lastActiveTimestamp = System.currentTimeMillis()
                    onRestoreFromHidden()
                },
            contentAlignment = Alignment.Center
        ) {
            PochiMascot(
                state = BotState.IDLE,
                emoteOverride = PochiEmote.WINK_LEFT,
                size = 24.dp,
                showBlackBacking = false
            )
        }
        return
    }

    // Animated Notch Dimensions & Transparency
    val targetWidthDp: Dp = when {
        isExpanded -> if (isLandscape) 460.dp else 364.dp
        isIdleMini -> 118.dp
        else -> 196.dp
    }
    val targetHeightDp: Dp = when {
        isExpanded -> if (isLandscape) 310.dp else if (showDropDrawer) 465.dp else 425.dp
        isIdleMini -> 24.dp
        else -> 34.dp
    }
    val targetAlpha: Float = when {
        isExpanded -> 1.0f
        isIdleMini -> 0.52f // Semi-transparent when shrunk & idle!
        else -> 0.96f
    }

    val animWidth by animateDpAsState(
        targetValue = targetWidthDp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "notchW"
    )
    val animHeight by animateDpAsState(
        targetValue = targetHeightDp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "notchH"
    )
    val animAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "notchAlpha"
    )

    val bottomRadiusDp by animateDpAsState(
        targetValue = if (isExpanded) 32.dp else if (isIdleMini) 15.dp else 20.dp,
        animationSpec = tween(260),
        label = "bottomRad"
    )

    val notchShape = remember(bottomRadiusDp, density) {
        with(density) {
            createSeamlessNotchShape(bottomCornerPx = bottomRadiusDp.toPx())
        }
    }

    // Infinite subtle purple aura animation
    val infTransition = rememberInfiniteTransition(label = "notchAura")
    val auraGlow by infTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "auraGlow"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .width(animWidth)
                .height(animHeight)
                .graphicsLayer {
                    alpha = animAlpha
                }
                .clip(notchShape)
                .background(
                    if (isExpanded) {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF050508),
                                Color(0xFF120924),
                                Color(0xFF08080E)
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF000000),
                                Color(0xFF0B0716)
                            )
                        )
                    }
                )
                // Draw border ONLY on Left, Bottom, and Right (NO TOP BORDER so it merges with the top bar!)
                .drawBehind {
                    val strokeW = if (isExpanded) 1.5.dp.toPx() else 1.dp.toPx()
                    val r = bottomRadiusDp.toPx().coerceIn(0f, minOf(size.width / 2f, size.height / 2f))
                    val w = size.width
                    val h = size.height
                    val borderPath = Path().apply {
                        // Start slightly below top-left (y = 2px), go down, around bottom, and end at top-right (y = 2px)
                        moveTo(strokeW / 2f, 2f)
                        lineTo(strokeW / 2f, h - r)
                        quadraticBezierTo(strokeW / 2f, h - strokeW / 2f, r, h - strokeW / 2f)
                        lineTo(w - r, h - strokeW / 2f)
                        quadraticBezierTo(w - strokeW / 2f, h - strokeW / 2f, w - strokeW / 2f, h - r)
                        lineTo(w - strokeW / 2f, 2f)
                    }
                    drawPath(
                        path = borderPath,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF9D4EDD).copy(alpha = auraGlow),
                                Color(0xFF00F5D4).copy(alpha = auraGlow * 0.8f),
                                Color(0xFF9D4EDD).copy(alpha = auraGlow)
                            )
                        ),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
                .pointerInput(isExpanded) {
                    if (!isExpanded) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var totalDx = 0f
                            var totalDy = 0f
                            var dragged = false
                            val touchSlop = viewConfiguration.touchSlop

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUp()) {
                                    change.consume()
                                    break
                                }
                                val delta = change.position - change.previousPosition
                                totalDx += delta.x
                                totalDy += delta.y
                                if (!dragged && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                                    dragged = true
                                }
                                if (dragged) {
                                    change.consume()
                                    onDragBubble(delta.x, delta.y)
                                }
                            }

                            if (dragged) {
                                onDragEnd()
                            } else {
                                lastActiveTimestamp = System.currentTimeMillis()
                                if (isIdleMini) {
                                    // Wake up and expand immediately on tap!
                                    isIdleMini = false
                                }
                                PochiSignals.playTapHaptic(context)
                                mode = IslandMode.EXPANDED
                                onExpandChange(true)
                            }
                        }
                    }
                }
        ) {
            if (!isExpanded) {
                // ============================================================
                // COLLAPSED NOTCH (MINI SEMI-TRANSPARENT OR ACTIVE NOTCH)
                // ============================================================
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isIdleMini) 10.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Expressive Pochi Mascot
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PochiMascot(
                            state = if (isThinking) BotState.WORKING else BotState.IDLE,
                            emoteOverride = effectiveEmote,
                            size = if (isIdleMini) 16.dp else 22.dp,
                            showBlackBacking = false
                        )

                        if (!isIdleMini) {
                            val label = when {
                                mediaPlayback.isPlaying -> mediaPlayback.title.take(14)
                                screenApp.isNotBlank() -> screenApp.take(14)
                                else -> "Jarvis AI"
                            }
                            Text(
                                text = label,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right: Glowing AI Sparkle / Status Dot
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isIdleMini) {
                            Text(
                                text = "✦",
                                color = Color(0xFFC77DFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F5D4))
                            )
                            Text(
                                text = "AI",
                                color = Color(0xFFE0AAFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            } else {
                // ============================================================
                // EXPANDED JARVIS AI CHAT NOTCH (CLEAN CHAT + RICH VISUAL CARDS)
                // ============================================================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. TOP NOTCH HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Interactive Pochi Mascot Orb
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(Color(0xFF7B2CBF).copy(alpha = 0.55f), Color(0xFF141422))
                                        )
                                    )
                                    .border(1.dp, Color(0xFFC77DFF).copy(alpha = 0.6f), CircleShape)
                                    .clickable {
                                        pokeCount++
                                        val (em, _, _) = PochiMood.onPokeReaction(pokeCount)
                                        customEmote = em
                                        PochiSignals.playTapHaptic(context)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                PochiMascot(
                                    state = if (isThinking) BotState.WORKING else BotState.IDLE,
                                    emoteOverride = effectiveEmote,
                                    size = 24.dp,
                                    showBlackBacking = false
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Pochi Jarvis",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Color(0xFF9D4EDD).copy(alpha = 0.25f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isThinking) "Thinking..." else "Online",
                                            color = Color(0xFFE0AAFF),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                val weatherSnap = remember(selectedCity) { WeatherHelper.getSnapshot(selectedCity) }
                                val contextLine = if (screenApp.isNotBlank()) {
                                    "Aktif: $screenApp • ${weatherSnap.tempC}°C"
                                } else {
                                    "Tuan Andre • $selectedCity ${weatherSnap.tempC}°C"
                                }
                                Text(
                                    text = contextLine,
                                    color = Color(0xFF9E9EB4),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Collapse Notch Button
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    PochiSignals.playTapHaptic(context)
                                    showDropDrawer = false
                                    mode = IslandMode.COLLAPSED
                                    onExpandChange(false)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Minimize",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 2. APPROVAL PROMPT (If a sensitive action awaits confirmation)
                    if (activeApprovalReq != null) {
                        val req = activeApprovalReq!!
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF261230))
                                .border(1.dp, Color(0xFFFF9F1C), RoundedCornerShape(16.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Konfirmasi: ${req.prompt}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF00F5D4))
                                        .clickable { ApprovalManager.respond(true) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text("Izinkan", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFF2A85))
                                        .clickable { ApprovalManager.respond(false) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text("Batal", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }

                    // 3. INLINE FILE DROP & ATTACH ZONE (When '+' is clicked)
                    AnimatedVisibility(visible = showDropDrawer) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF151226))
                                .border(
                                    1.dp,
                                    Brush.horizontalGradient(listOf(Color(0xFF9D4EDD), Color(0xFF00F5D4))),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📎 Drop atau Pilih File untuk Pochi",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Drag & Drop Aktif",
                                    color = Color(0xFF00F5D4),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Button 1: Open System File Picker via MainActivity
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.horizontalGradient(listOf(Color(0xFF7B2CBF), Color(0xFF5A189A))))
                                        .clickable {
                                            showDropDrawer = false
                                            val pickIntent = Intent(context, MainActivity::class.java).apply {
                                                action = "com.pochi.assistant.ACTION_PICK_FILE"
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(pickIntent)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "📂 Pilih File HP",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                // Button 2: Inspect Latest Screenshot or Current Screen
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF222038))
                                        .clickable {
                                            showDropDrawer = false
                                            val sc = latestScreenshot
                                            if (sc != null) {
                                                val info = PochiJarvisEngine.inspectUri(context, sc.uri)
                                                submitToJarvis("Analisis screenshot terbaru ini", info)
                                            } else {
                                                val txt = PochiSignals.screenText.value.ifBlank {
                                                    "Konteks layar aktif: ${PochiSignals.screenApp.value.ifBlank { "Beranda" }}"
                                                }
                                                submitToJarvis(
                                                    "Analisis konteks layar saya",
                                                    DroppedFileInfo(
                                                        uri = null,
                                                        fileName = "screen_context.txt",
                                                        mimeType = "text/plain",
                                                        sizeBytes = txt.length.toLong(),
                                                        textPreview = txt
                                                    )
                                                )
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (latestScreenshot != null) "📸 Screenshot" else "📱 Pindai Layar",
                                        color = Color(0xFF00F5D4),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 4. CHAT MESSAGES + RICH GENERATIVE VISUAL CARDS
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(notchMessages, key = { it.id }) { msg ->
                            NotchMessageBubble(msg = msg)
                        }
                    }

                    // 5. QUICK SUGGESTION CHIPS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickPrompts = listOf(
                            "📈 Harga BTC" to "Carikan harga Bitcoin sekarang",
                            "📊 Grafik" to "Tampilkan grafik pengeluaran",
                            "🌤️ Suhu" to "Suhu sekarang berapa",
                            "🕌 Sholat" to "Kapan waktu sholat berikutnya"
                        )
                        quickPrompts.forEach { (chipLabel, fullQuery) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                                    .clickable { submitToJarvis(fullQuery) }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = chipLabel,
                                    color = Color(0xFFD8D8E8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 6. CLEAN BOTTOM CHAT COMPOSER (+ Drop File | Text Input | Mic | Send)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF141320))
                            .border(1.dp, Color(0xFF2B2840), RoundedCornerShape(50))
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // '+' File Drop / Attach Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (showDropDrawer) Color(0xFF9D4EDD) else Color(0xFF222034))
                                .clickable {
                                    showDropDrawer = !showDropDrawer
                                    PochiSignals.playTapHaptic(context)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (showDropDrawer) Icons.Rounded.Close else Icons.Rounded.Add,
                                contentDescription = "Drop File",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Chat Input Field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = chatInput,
                                onValueChange = {
                                    chatInput = it
                                    lastActiveTimestamp = System.currentTimeMillis()
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = Color.White,
                                    fontSize = 12.sp
                                ),
                                cursorBrush = SolidColor(Color(0xFFC77DFF)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { submitToJarvis(chatInput) }),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { inner ->
                                    if (chatInput.isEmpty()) {
                                        Text(
                                            text = "Tanya Pochi atau drop file...",
                                            color = Color(0xFF8E8EA2),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    inner()
                                }
                            )
                        }

                        // Voice Mic Button
                        if (speechRecognizer != null) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isListeningVoice) Color(0xFFFF2A85) else Color(0xFF222034))
                                    .clickable {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            if (isListeningVoice) {
                                                speechRecognizer.stopListening()
                                            } else {
                                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
                                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                                }
                                                speechRecognizer.startListening(intent)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isListeningVoice) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                    contentDescription = "Voice",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Crisp White Circular Send Button (Paper Plane)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { submitToJarvis(chatInput) },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(14.dp)) {
                                val w = size.width
                                val h = size.height
                                val planePath = Path().apply {
                                    moveTo(w * 0.95f, h * 0.1f)
                                    lineTo(w * 0.08f, h * 0.46f)
                                    lineTo(w * 0.44f, h * 0.58f)
                                    lineTo(w * 0.58f, h * 0.92f)
                                    close()
                                }
                                drawPath(planePath, color = Color(0xFF050508))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotchMessageBubble(msg: NotchChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (msg.text.isNotBlank()) {
            Box(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (msg.isUser) 18.dp else 4.dp,
                            bottomEnd = if (msg.isUser) 4.dp else 18.dp
                        )
                    )
                    .background(
                        if (msg.isUser) {
                            Brush.horizontalGradient(listOf(Color(0xFF7B2CBF), Color(0xFF5A189A)))
                        } else {
                            Brush.horizontalGradient(listOf(Color(0xFF171624), Color(0xFF12111D)))
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (msg.isUser) Color(0xFFC77DFF).copy(alpha = 0.4f) else Color(0xFF28263C),
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (msg.isUser) 18.dp else 4.dp,
                            bottomEnd = if (msg.isUser) 4.dp else 18.dp
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = msg.text,
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = msg.timeLabel,
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }

        if (msg.richCard != null) {
            PochiRichCardView(
                card = msg.richCard,
                compact = true
            )
        }
    }
}
