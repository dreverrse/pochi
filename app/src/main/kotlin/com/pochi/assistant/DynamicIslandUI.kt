package com.pochi.assistant

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Creates a Notch shape that fuses seamlessly with the phone edge:
 * - Portrait ("NONE"): Flat top edge (`topStart = 0.dp, topEnd = 0.dp`), rounded bottom corners.
 * - Landscape Left ("LEFT"): Flat left edge (`topStart = 0.dp, bottomStart = 0.dp`), rounded right corners.
 * - Landscape Right ("RIGHT"): Flat right edge (`topEnd = 0.dp, bottomEnd = 0.dp`), rounded left corners.
 */
private fun createSeamlessNotchShape(cornerRadius: Dp, landscapeSide: String): RoundedCornerShape {
    return when (landscapeSide) {
        "LEFT" -> RoundedCornerShape(
            topStart = 0.dp,
            bottomStart = 0.dp,
            topEnd = cornerRadius,
            bottomEnd = cornerRadius
        )
        "RIGHT" -> RoundedCornerShape(
            topStart = cornerRadius,
            bottomStart = cornerRadius,
            topEnd = 0.dp,
            bottomEnd = 0.dp
        )
        else -> RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = cornerRadius,
            bottomEnd = cornerRadius
        )
    }
}

/**
 * Draws a subtle ambient bottom curve glow on the GPU without triggering recomposition.
 */
private fun Modifier.drawNotchEdgeBorder(
    cornerRadius: Dp,
    landscapeSide: String,
    glowPhaseProvider: () -> Float,
    strokeWidth: Dp = 1.dp
): Modifier = this.drawBehind {
    val r = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
    val sw = strokeWidth.toPx()
    val w = size.width
    val h = size.height
    val phase = glowPhaseProvider()

    val borderBrush = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF1D4ED8).copy(alpha = 0.35f + 0.35f * phase),
            Color(0xFF38BDF8).copy(alpha = 0.45f + 0.40f * phase),
            Color(0xFF1D4ED8).copy(alpha = 0.35f + 0.35f * phase)
        )
    )

    val path = Path().apply {
        when (landscapeSide) {
            "LEFT" -> {
                moveTo(0f, sw / 2f)
                lineTo(w - r, sw / 2f)
                arcTo(
                    rect = Rect(w - 2f * r, sw / 2f, w - sw / 2f, 2f * r),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(w - sw / 2f, h - r)
                arcTo(
                    rect = Rect(w - 2f * r, h - 2f * r, w - sw / 2f, h - sw / 2f),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                lineTo(0f, h - sw / 2f)
            }
            "RIGHT" -> {
                moveTo(w, sw / 2f)
                lineTo(r, sw / 2f)
                arcTo(
                    rect = Rect(sw / 2f, sw / 2f, 2f * r, 2f * r),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(sw / 2f, h - r)
                arcTo(
                    rect = Rect(sw / 2f, h - 2f * r, 2f * r, h - sw / 2f),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(w, h - sw / 2f)
            }
            else -> {
                moveTo(sw / 2f, h - r)
                arcTo(
                    rect = Rect(sw / 2f, h - 2f * r, 2f * r, h - sw / 2f),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
                lineTo(w - r, h - sw / 2f)
                arcTo(
                    rect = Rect(w - 2f * r, h - 2f * r, w - sw / 2f, h - sw / 2f),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = -90f,
                    forceMoveTo = false
                )
            }
        }
    }

    drawPath(
        path = path,
        brush = borderBrush,
        style = Stroke(width = sw, cap = StrokeCap.Round)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PochiIslandBot(
    landscapeSide: String = "NONE",
    onExpandChange: (Boolean) -> Unit,
    onExpandedHeightChange: (Int) -> Unit = {},
    onPowerOffService: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var showDropDrawer by remember { mutableStateOf(false) }

    var inputText by remember { mutableStateOf("") }
    var lastSentMessage by remember { mutableStateOf("") }
    var replyText by remember {
        mutableStateOf("Halo Tuan Andre, ada yang ingin dicari atau dianalisis hari ini?")
    }
    var activeRichCard by remember { mutableStateOf<PochiRichCard?>(null) }
    var manualEmote by remember { mutableStateOf<PochiEmote?>(null) }
    var autonomousEmote by remember { mutableStateOf(PochiEmote.NORMAL) }

    // Idle auto-shrink & semi-transparency timer
    var lastActivityMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isIdleMini by remember { mutableStateOf(false) }

    fun touchActivity() {
        lastActivityMs = System.currentTimeMillis()
        isIdleMini = false
    }

    val pendingApproval by ApprovalManager.activeRequest.collectAsState()
    val mediaPlayback by PochiSignals.mediaPlayback.collectAsState()
    val screenApp by PochiSignals.screenApp.collectAsState()
    val recentScreenshot by PochiSignals.latestScreenshot.collectAsState()
    val droppedFile by PochiSignals.droppedFile.collectAsState()
    val expandRequest by PochiSignals.islandExpandRequest.collectAsState()

    // GPU-only entrance animation when expanded changes (zero WindowManager relayout IPC thrashing!)
    val expandBloom = remember { Animatable(1f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            expandBloom.snapTo(0.86f)
            expandBloom.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        } else {
            expandBloom.snapTo(1f)
        }
    }

    // Expand when requested by file picker or app
    LaunchedEffect(expandRequest) {
        if (expandRequest > 0L && !expanded) {
            expanded = true
            onExpandChange(true)
            touchActivity()
        }
    }

    // Automatically process dropped/picked file in the Notch
    LaunchedEffect(droppedFile) {
        val df = droppedFile ?: return@LaunchedEffect
        touchActivity()
        if (!expanded) {
            expanded = true
            onExpandChange(true)
        }
        isThinking = true
        lastSentMessage = "File: ${df.fileName} (${df.formattedSize})"
        val bundle = PochiJarvisEngine.processQuery(context, inputText, df)
        replyText = bundle.text
        activeRichCard = bundle.richCard
        manualEmote = bundle.emote
        isThinking = false
    }

    // Expand if biometric approval is pending
    LaunchedEffect(pendingApproval) {
        if (pendingApproval != null && !expanded) {
            expanded = true
            onExpandChange(true)
            touchActivity()
        }
    }

    // Idle detector: shrinks and becomes semi-transparent after 4.5s of inactivity when collapsed
    LaunchedEffect(expanded, lastActivityMs, isThinking, isListening, mediaPlayback.isPlaying) {
        if (expanded || isThinking || isListening) {
            isIdleMini = false
            return@LaunchedEffect
        }
        delay(4500L)
        isIdleMini = true
    }

    // Autonomous Real-Time Expressions based on what Tuan Andre is doing
    LaunchedEffect(screenApp, mediaPlayback.isPlaying, expanded, isThinking, isListening) {
        while (true) {
            if (!isThinking && !isListening && manualEmote == null) {
                val appLower = screenApp.lowercase(Locale.US)
                autonomousEmote = when {
                    mediaPlayback.isPlaying -> listOf(PochiEmote.HAPPY, PochiEmote.EXCITED, PochiEmote.SMILING).random()
                    appLower.contains("code") || appLower.contains("studio") || appLower.contains("termux") || appLower.contains("git") ->
                        listOf(PochiEmote.FOCUS, PochiEmote.THINKING, PochiEmote.WINK_LEFT).random()
                    appLower.contains("whatsapp") || appLower.contains("telegram") || appLower.contains("instagram") || appLower.contains("tiktok") ->
                        listOf(PochiEmote.SMILING, PochiEmote.HMM, PochiEmote.SUSPICIOUS, PochiEmote.WINK_LEFT).random()
                    appLower.contains("game") || appLower.contains("mobilelegends") || appLower.contains("pubg") || appLower.contains("genshin") ->
                        listOf(PochiEmote.EXCITED, PochiEmote.FOCUS, PochiEmote.SURPRISED).random()
                    appLower.contains("youtube") || appLower.contains("netflix") || appLower.contains("spotify") ->
                        listOf(PochiEmote.CALM, PochiEmote.HAPPY).random()
                    else -> listOf(
                        PochiEmote.NORMAL,
                        PochiEmote.SMILING,
                        PochiEmote.HMM,
                        PochiEmote.SUSPICIOUS,
                        PochiEmote.CALM,
                        PochiEmote.WINK_LEFT
                    ).random()
                }
            }
            delay(3400L)
        }
    }

    // Reset manual emote after 6 seconds
    LaunchedEffect(manualEmote) {
        if (manualEmote != null) {
            delay(6000L)
            manualEmote = null
        }
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { speechRecognizer?.destroy() }
        }
    }

    val chatScrollState = rememberScrollState()

    fun submitQuery(query: String, attachedFile: DroppedFileInfo? = droppedFile) {
        val q = query.trim()
        if (q.isEmpty() && attachedFile == null) return
        touchActivity()
        if (q.isNotEmpty()) {
            lastSentMessage = q
        }
        inputText = ""
        showDropDrawer = false
        isThinking = true
        replyText = "Memproses permintaan Tuan Andre..."
        scope.launch {
            val bundle = PochiJarvisEngine.processQuery(context, q, attachedFile)
            replyText = bundle.text
            activeRichCard = bundle.richCard
            manualEmote = bundle.emote
            if (attachedFile != null) {
                PochiSignals.consumeDroppedFile()
            }
            isThinking = false
            delay(60)
            chatScrollState.animateScrollTo(chatScrollState.maxValue)
        }
    }

    fun startVoiceInput() {
        touchActivity()
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
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
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) {
                    submitQuery(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) inputText = text
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        runCatching { recognizer.startListening(intent) }.onFailure { isListening = false }
    }

    val currentEmote: PochiEmote = when {
        isThinking -> PochiEmote.THINKING
        isListening -> PochiEmote.SURPRISED
        manualEmote != null -> manualEmote!!
        else -> autonomousEmote
    }

    // Continuous animations read ONLY inside Draw/GraphicsLayer lambdas (zero recomposition jank!)
    val inf = rememberInfiniteTransition(label = "notchMotion")
    val glowPhaseState = inf.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPhase"
    )
    val orbitalSpinState = inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "orbitalSpin"
    )
    val eqWaveState = inf.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "eqWave"
    )

    val isLandscape = landscapeSide != "NONE"
    val collapsedTargetWidth = when {
        isLandscape && isIdleMini -> 108.dp
        isLandscape -> 172.dp
        isIdleMini -> 116.dp
        else -> 196.dp
    }
    val collapsedWidth by animateDpAsState(
        targetValue = collapsedTargetWidth,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "notchW"
    )
    val collapsedHeight by animateDpAsState(
        targetValue = if (isIdleMini) 24.dp else 35.dp,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "notchH"
    )
    val notchAlpha by animateFloatAsState(
        targetValue = when {
            expanded -> 1f
            isIdleMini -> 0.52f
            else -> 0.97f
        },
        animationSpec = tween(280),
        label = "notchAlpha"
    )
    val attachBtnRot by animateFloatAsState(
        targetValue = if (showDropDrawer) 45f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "attachBtnRot"
    )

    val cornerRad = when {
        expanded -> 28.dp
        isIdleMini -> 14.dp
        else -> 20.dp
    }
    val notchShape = remember(cornerRad, landscapeSide) {
        createSeamlessNotchShape(cornerRad, landscapeSide)
    }

    val boxAlignment = when (landscapeSide) {
        "LEFT" -> Alignment.CenterStart
        "RIGHT" -> Alignment.CenterEnd
        else -> Alignment.TopCenter
    }

    val pivotOrigin = when (landscapeSide) {
        "LEFT" -> TransformOrigin(0f, 0.5f)
        "RIGHT" -> TransformOrigin(1f, 0.5f)
        else -> TransformOrigin(0.5f, 0f)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = boxAlignment
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (expanded) {
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    } else {
                        Modifier.size(width = collapsedWidth, height = collapsedHeight)
                    }
                )
                .graphicsLayer {
                    val b = expandBloom.value
                    scaleX = b
                    scaleY = b
                    alpha = notchAlpha * b.coerceIn(0.6f, 1f)
                    transformOrigin = pivotOrigin
                }
                .clip(notchShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (expanded) {
                            listOf(
                                Color(0xFF020408),
                                Color(0xFF060C1A),
                                Color(0xFF091428)
                            )
                        } else {
                            listOf(
                                Color(0xFF020408),
                                Color(0xFF071022)
                            )
                        }
                    )
                )
                .drawBehind {
                    // GPU-only ambient Blue-Black Aurora glow inside the Notch
                    val phase = glowPhaseState.value
                    val cx = size.width * 0.5f
                    val cy = if (expanded) size.height * 0.16f else size.height * 0.5f
                    val rad = (size.width * 0.65f).coerceAtLeast(60f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1D4ED8).copy(alpha = if (expanded) 0.24f * phase else 0.18f * phase),
                                Color(0xFF38BDF8).copy(alpha = if (expanded) 0.10f * phase else 0.06f * phase),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = rad
                        ),
                        radius = rad,
                        center = Offset(cx, cy)
                    )
                }
                .drawNotchEdgeBorder(
                    cornerRadius = cornerRad,
                    landscapeSide = landscapeSide,
                    glowPhaseProvider = { glowPhaseState.value },
                    strokeWidth = if (expanded) 1.2.dp else 1.dp
                )
                .then(
                    if (!expanded) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            touchActivity()
                            expanded = true
                            onExpandChange(true)
                        }
                    } else Modifier
                )
        ) {
            if (!expanded) {
                // ============================================================
                // COLLAPSED / MINI-TRANSPARENT NOTCH MODE (LOCKED 120FPS)
                // ============================================================
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (isIdleMini) 8.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!isIdleMini) {
                                Canvas(modifier = Modifier.size(25.dp)) {
                                    rotate(orbitalSpinState.value) {
                                        drawCircle(
                                            brush = Brush.sweepGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color(0xFF38BDF8).copy(alpha = 0.65f),
                                                    Color(0xFF1D4ED8).copy(alpha = 0.85f),
                                                    Color.Transparent
                                                )
                                            ),
                                            radius = size.minDimension / 2f,
                                            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                }
                            }
                            PochiMascot(
                                state = if (isThinking) BotState.WORKING else BotState.IDLE,
                                emoteOverride = currentEmote,
                                size = if (isIdleMini) 16.dp else 21.dp,
                                showBlackBacking = false
                            )
                        }
                        if (!isIdleMini) {
                            val label = when {
                                isThinking -> "Thinking..."
                                isListening -> "Listening..."
                                mediaPlayback.isPlaying && mediaPlayback.title.isNotBlank() -> mediaPlayback.title.take(12)
                                screenApp.isNotBlank() -> screenApp.take(11)
                                else -> "Pochi"
                            }
                            Text(
                                text = label,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right GPU-Drawn Orbital / Pulse Indicator
                    Canvas(
                        modifier = Modifier.size(if (isIdleMini) 10.dp else 16.dp)
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val spin = orbitalSpinState.value
                        val wave = eqWaveState.value
                        val phase = glowPhaseState.value
                        if (mediaPlayback.isPlaying || isListening || isThinking) {
                            rotate(spin * 1.4f, pivot = Offset(cx, cy)) {
                                for (i in 0 until 6) {
                                    val angle = Math.toRadians((i * 60).toDouble())
                                    val r = (size.minDimension * 0.44f) * (0.6f + 0.4f * wave)
                                    drawLine(
                                        color = Color(0xFF38BDF8),
                                        start = Offset(cx, cy),
                                        end = Offset(
                                            cx + cos(angle).toFloat() * r,
                                            cy + sin(angle).toFloat() * r
                                        ),
                                        strokeWidth = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        } else {
                            drawCircle(
                                color = Color(0xFF1D4ED8).copy(alpha = 0.38f * phase),
                                radius = (size.minDimension * 0.48f) * (0.8f + 0.25f * phase),
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = Color(0xFF38BDF8).copy(alpha = 0.65f + 0.35f * phase),
                                radius = size.minDimension * 0.24f,
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }
            } else {
                // ============================================================
                // EXPANDED POCHI CHAT NOTCH (NO SCROLLBAR, LOCKED 120FPS)
                // ============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Notch Header: Orbital Pochi Mascot + Live Status + Collapse
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF0A1D3B), Color(0xFF040814))
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        Color(0xFF1D4ED8).copy(alpha = 0.65f),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(34.dp)) {
                                    rotate(orbitalSpinState.value) {
                                        drawCircle(
                                            brush = Brush.sweepGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color(0xFF38BDF8).copy(alpha = 0.55f),
                                                    Color.Transparent
                                                )
                                            ),
                                            radius = size.minDimension * 0.46f,
                                            style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                    }
                                }
                                PochiMascot(
                                    state = if (isThinking) BotState.WORKING else BotState.IDLE,
                                    emoteOverride = currentEmote,
                                    size = 24.dp,
                                    showBlackBacking = false,
                                    onPoke = {
                                        touchActivity()
                                        manualEmote = listOf(PochiEmote.HAPPY, PochiEmote.WINK_LEFT, PochiEmote.EXCITED, PochiEmote.LOVE).random()
                                    }
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "POCHI",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.8.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .graphicsLayer {
                                                val s = 0.85f + 0.3f * glowPhaseState.value
                                                scaleX = s
                                                scaleY = s
                                            }
                                            .clip(CircleShape)
                                            .background(Color(0xFF38BDF8))
                                    )
                                }
                                Text(
                                    text = when {
                                        isThinking -> "Memproses permintaan Tuan Andre..."
                                        isListening -> "Mendengarkan suara Tuan Andre..."
                                        screenApp.isNotBlank() -> "Konteks aktif: $screenApp"
                                        else -> "Siap membantu Tuan Andre"
                                    },
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Collapse Notch Button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.25f), CircleShape)
                                .clickable {
                                    expanded = false
                                    showDropDrawer = false
                                    onExpandChange(false)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Tutup",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 2. Biometric Approval Card if pending
                    if (pendingApproval != null) {
                        PochiApprovalCard(
                            request = pendingApproval!!,
                            onDeny = { ApprovalManager.confirmDeny("Ditolak dari island") }
                        )
                    }

                    // 3. Frameless, Scrollbar-Free Chat Canvas (No animateContentSize IPC jank)
                    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp, max = if (isLandscape) 150.dp else 215.dp)
                                .verticalScroll(chatScrollState),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (lastSentMessage.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF1D4ED8), Color(0xFF0A2463))
                                                )
                                            )
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = lastSentMessage,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF0A1830),
                                                Color(0xFF050A14)
                                            )
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF1D4ED8).copy(alpha = 0.45f),
                                                Color(0xFF38BDF8).copy(alpha = 0.25f)
                                            )
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = replyText,
                                        color = Color(0xFFEFF6FF),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                    if (isThinking) {
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp)
                                        ) {
                                            val w = size.width
                                            val cy = size.height / 2f
                                            val spin = orbitalSpinState.value
                                            val path = Path().apply {
                                                moveTo(0f, cy)
                                                val steps = 28
                                                for (i in 1..steps) {
                                                    val x = (w / steps) * i
                                                    val phase = (i * 0.4f) + (spin * 0.08f)
                                                    val y = cy + sin(phase.toDouble()).toFloat() * (size.height * 0.36f)
                                                    lineTo(x, y)
                                                }
                                            }
                                            drawPath(
                                                path = path,
                                                brush = Brush.horizontalGradient(
                                                    listOf(Color.Transparent, Color(0xFF38BDF8), Color(0xFF1D4ED8), Color.Transparent)
                                                ),
                                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                        }
                                    }
                                }
                            }

                            if (activeRichCard != null) {
                                PochiRichCardView(
                                    card = activeRichCard!!,
                                    compact = true
                                )
                            }
                        }
                    }

                    // 4. Optional File Drop / Attach Drawer when "+" is clicked
                    AnimatedVisibility(
                        visible = showDropDrawer,
                        enter = fadeIn(tween(180)) + expandVertically(),
                        exit = fadeOut(tween(140)) + shrinkVertically()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF081226))
                                .border(1.dp, Color(0xFF1D4ED8).copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DropActionChip(
                                label = "Drop File",
                                modifier = Modifier.weight(1f)
                            ) {
                                showDropDrawer = false
                                val pickIntent = Intent(context, MainActivity::class.java).apply {
                                    action = MainActivity.ACTION_PICK_FILE
                                    putExtra(MainActivity.EXTRA_MIME_TYPE, "*/*")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(pickIntent) }
                            }

                            DropActionChip(
                                label = "Foto / PDF",
                                modifier = Modifier.weight(1f)
                            ) {
                                showDropDrawer = false
                                val pickIntent = Intent(context, MainActivity::class.java).apply {
                                    action = MainActivity.ACTION_PICK_FILE
                                    putExtra(MainActivity.EXTRA_MIME_TYPE, "image/*")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                runCatching { context.startActivity(pickIntent) }
                            }

                            DropActionChip(
                                label = "Layar Aktif",
                                modifier = Modifier.weight(1f)
                            ) {
                                showDropDrawer = false
                                val sc = recentScreenshot
                                if (sc != null) {
                                    val info = PochiJarvisEngine.inspectUri(context, sc.uri)
                                    PochiSignals.publishDroppedFile(info)
                                } else {
                                    val snapText = PochiSignals.screenText.value.ifBlank {
                                        "Konteks Aplikasi: ${screenApp.ifBlank { "Layar Utama" }}"
                                    }
                                    PochiSignals.publishDroppedFile(
                                        DroppedFileInfo(
                                            uri = null,
                                            fileName = "screen_context.txt",
                                            mimeType = "text/plain",
                                            sizeBytes = snapText.length.toLong(),
                                            textPreview = snapText
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Active Dropped File Pill Preview
                    if (droppedFile != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1D4ED8).copy(alpha = 0.22f))
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "FILE: ${droppedFile!!.fileName} (${droppedFile!!.formattedSize})",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Hapus lampiran",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { PochiSignals.consumeDroppedFile() }
                            )
                        }
                    }

                    // 5. Pinned Bottom Chat Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF070E1C))
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF1D4ED8).copy(alpha = 0.65f),
                                        Color(0xFF38BDF8).copy(alpha = 0.45f)
                                    )
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val attachBg by animateColorAsState(
                            targetValue = if (showDropDrawer) Color(0xFF1D4ED8) else Color(0xFF0F1D36),
                            animationSpec = tween(200),
                            label = "attachBg"
                        )
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(attachBg)
                                .clickable {
                                    touchActivity()
                                    showDropDrawer = !showDropDrawer
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Drop File",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = attachBtnRot }
                            )
                        }

                        // Chat Input Field (Always Visible & Focusable)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Ketik pesan ke Pochi...",
                                    color = Color(0xFF7A8BA8),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = {
                                    inputText = it
                                    touchActivity()
                                },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(Color(0xFF38BDF8)),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = { submitQuery(inputText) }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Voice Mic Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isListening) Color(0xFF0EA5E9).copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .clickable { startVoiceInput() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                contentDescription = "Suara",
                                tint = if (isListening) Color(0xFF38BDF8) else Color(0xFF8DA2C0),
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Send Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { submitQuery(inputText) },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(14.dp)) {
                                val w = size.width
                                val h = size.height
                                val plane = Path().apply {
                                    moveTo(w * 0.12f, h * 0.48f)
                                    lineTo(w * 0.88f, h * 0.14f)
                                    lineTo(w * 0.58f, h * 0.88f)
                                    lineTo(w * 0.44f, h * 0.56f)
                                    close()
                                }
                                drawPath(
                                    path = plane,
                                    color = Color(0xFF040814),
                                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DropActionChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Color(0xFF0D1B35))
            .border(1.dp, Color(0xFF1D4ED8).copy(alpha = 0.35f), RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
