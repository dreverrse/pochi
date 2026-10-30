package com.pochi.assistant

import android.content.Intent
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
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
 * Draws the subtle blue-black border only on the exposed sides of the Notch,
 * leaving the docked screen edge completely borderless.
 */
private fun Modifier.drawNotchEdgeBorder(
    cornerRadius: Dp,
    landscapeSide: String,
    borderBrush: Brush,
    strokeWidth: Dp = 1.dp
): Modifier = this.drawBehind {
    val r = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
    val sw = strokeWidth.toPx()
    val w = size.width
    val h = size.height

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
                // Portrait: NO top border; only left side, bottom corners, bottom edge, right side
                moveTo(sw / 2f, 0f)
                lineTo(sw / 2f, h - r)
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
                lineTo(w - sw / 2f, 0f)
            }
        }
    }

    drawPath(
        path = path,
        brush = borderBrush,
        style = Stroke(width = sw, cap = StrokeCap.Round)
    )
}

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
    var replyText by remember {
        mutableStateOf("Halo Tuan Andre, ada yang ingin dicari atau dianalisis hari ini?")
    }
    var activeRichCard by remember { mutableStateOf<PochiRichCard?>(null) }
    var manualEmote by remember { mutableStateOf<PochiEmote?>(null) }
    var autonomousEmote by remember { mutableStateOf(PochiEmote.NEUTRAL) }

    // Idle auto-shrink & semi-transparency timer
    var lastActivityMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isIdleMini by remember { mutableStateOf(false) }

    fun touchActivity() {
        lastActivityMs = System.currentTimeMillis()
        isIdleMini = false
    }

    val pendingApproval by ApprovalManager.pendingAction.collectAsState()
    val outsideDismissTrigger by ApprovalManager.outsideDismissTrigger.collectAsState()
    val mediaPlayback by PochiSignals.mediaPlayback.collectAsState()
    val screenApp by PochiSignals.screenApp.collectAsState()
    val recentScreenshot by PochiSignals.recentScreenshot.collectAsState()
    val droppedFile by PochiSignals.droppedFile.collectAsState()
    val expandRequest by PochiSignals.islandExpandRequest.collectAsState()

    // Collapse when user taps outside the expanded Notch
    LaunchedEffect(outsideDismissTrigger) {
        if (outsideDismissTrigger > 0L && expanded && pendingApproval == null) {
            expanded = false
            showDropDrawer = false
            onExpandChange(false)
            touchActivity()
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

    // Idle detector: shrinks and becomes semi-transparent after 4.5s of inactivity
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
                        listOf(PochiEmote.SMILING, PochiEmote.LOOK_LEFT, PochiEmote.LOOK_RIGHT, PochiEmote.WINK_RIGHT).random()
                    appLower.contains("game") || appLower.contains("mobilelegends") || appLower.contains("pubg") || appLower.contains("genshin") ->
                        listOf(PochiEmote.EXCITED, PochiEmote.FOCUS, PochiEmote.SURPRISED).random()
                    appLower.contains("youtube") || appLower.contains("netflix") || appLower.contains("spotify") ->
                        listOf(PochiEmote.CALM, PochiEmote.HAPPY).random()
                    else -> listOf(
                        PochiEmote.NEUTRAL,
                        PochiEmote.SMILING,
                        PochiEmote.LOOK_LEFT,
                        PochiEmote.LOOK_RIGHT,
                        PochiEmote.CALM,
                        PochiEmote.WINK_LEFT
                    ).random()
                }
            }
            delay(3600L)
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

    fun submitQuery(query: String, attachedFile: DroppedFileInfo? = droppedFile) {
        val q = query.trim()
        if (q.isEmpty() && attachedFile == null) return
        touchActivity()
        inputText = ""
        showDropDrawer = false
        isThinking = true
        scope.launch {
            val bundle = PochiJarvisEngine.processQuery(context, q, attachedFile)
            replyText = bundle.text
            activeRichCard = bundle.richCard
            manualEmote = bundle.emote
            if (attachedFile != null) {
                PochiSignals.consumeDroppedFile()
            }
            isThinking = false
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
        isListening -> PochiEmote.LISTENING
        manualEmote != null -> manualEmote!!
        else -> autonomousEmote
    }

    // Blue-Black Gradient aura animation
    val inf = rememberInfiniteTransition(label = "notchBlueGlow")
    val glowPhase by inf.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowPhase"
    )
    val eqWave by inf.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "eqWave"
    )

    // Smoothly animate Notch width, corner radius, and transparency
    val isLandscape = landscapeSide != "NONE"
    val collapsedTargetWidth = when {
        isLandscape && isIdleMini -> 108.dp
        isLandscape -> 168.dp
        isIdleMini -> 116.dp
        else -> 192.dp
    }
    val collapsedWidth by animateDpAsState(
        targetValue = collapsedTargetWidth,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "notchW"
    )
    val collapsedHeight by animateDpAsState(
        targetValue = if (isIdleMini) 24.dp else 34.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "notchH"
    )
    val notchAlpha by animateFloatAsState(
        targetValue = when {
            expanded -> 1f
            isIdleMini -> 0.50f
            else -> 0.96f
        },
        animationSpec = tween(420),
        label = "notchAlpha"
    )
    val cornerRad by animateDpAsState(
        targetValue = when {
            expanded -> 28.dp
            isIdleMini -> 14.dp
            else -> 20.dp
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "notchCorner"
    )

    val notchShape = createSeamlessNotchShape(cornerRad, landscapeSide)

    // Blue-Black Gradient Border (No Purple)
    val borderBrush = Brush.horizontalGradient(
        colors = if (expanded || isThinking || isListening) {
            listOf(
                Color(0xFF1D4ED8).copy(alpha = glowPhase),
                Color(0xFF38BDF8).copy(alpha = glowPhase * 0.9f),
                Color(0xFF0A2463).copy(alpha = glowPhase)
            )
        } else {
            listOf(
                Color(0xFF1E3A8A).copy(alpha = if (isIdleMini) 0.20f else 0.45f),
                Color(0xFF38BDF8).copy(alpha = if (isIdleMini) 0.25f else 0.50f),
                Color(0xFF1E3A8A).copy(alpha = if (isIdleMini) 0.20f else 0.45f)
            )
        }
    )

    val boxAlignment = when (landscapeSide) {
        "LEFT" -> Alignment.CenterStart
        "RIGHT" -> Alignment.CenterEnd
        else -> Alignment.TopCenter
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
                            .onSizeChanged { size ->
                                if (size.height > 0) onExpandedHeightChange(size.height)
                            }
                    } else {
                        Modifier.size(width = collapsedWidth, height = collapsedHeight)
                    }
                )
                .alpha(notchAlpha)
                .clip(notchShape)
                .background(
                    Brush.verticalGradient(
                        colors = if (expanded) {
                            listOf(
                                Color(0xFF03050A),
                                Color(0xFF060C1A),
                                Color(0xFF091326)
                            )
                        } else {
                            listOf(
                                Color(0xFF020408),
                                Color(0xFF070F20)
                            )
                        }
                    )
                )
                .drawNotchEdgeBorder(
                    cornerRadius = cornerRad,
                    landscapeSide = landscapeSide,
                    borderBrush = borderBrush,
                    strokeWidth = if (expanded) 1.3.dp else 1.dp
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
                // COLLAPSED / MINI-TRANSPARENT NOTCH MODE (FIXED POSITION)
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PochiMascot(
                            emote = currentEmote,
                            isThinking = isThinking,
                            isListening = isListening,
                            eyeWidth = if (isIdleMini) 5.dp else 6.5.dp,
                            eyeHeight = if (isIdleMini) 8.dp else 10.dp,
                            eyeGap = if (isIdleMini) 3.dp else 4.dp,
                            accentColor = Color(0xFF38BDF8)
                        )
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
                                color = Color.White.copy(alpha = 0.90f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Right Blue Activity Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (mediaPlayback.isPlaying || isListening || isThinking) {
                            val bars = if (isIdleMini) 2 else 3
                            for (b in 0 until bars) {
                                val hRatio = ((eqWave + b * 0.28f) % 1f).coerceIn(0.3f, 1f)
                                Box(
                                    modifier = Modifier
                                        .width(2.5.dp)
                                        .height((if (isIdleMini) 9.dp else 13.dp) * hRatio)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8))
                                            )
                                        )
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(if (isIdleMini) 5.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8).copy(alpha = glowPhase))
                            )
                        }
                    }
                }
            } else {
                // ============================================================
                // EXPANDED POCHI CHAT NOTCH (CLEAN, NO SHORTCUT CHIPS, NO EMOJI)
                // ============================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Minimal Notch Header: Pochi Mascot + "POCHI" + Collapse
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
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF0A1931), Color(0xFF040814))
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        Color(0xFF1D4ED8).copy(alpha = 0.55f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PochiMascot(
                                    emote = currentEmote,
                                    isThinking = isThinking,
                                    isListening = isListening,
                                    eyeWidth = 7.dp,
                                    eyeHeight = 11.dp,
                                    eyeGap = 4.5.dp,
                                    accentColor = Color(0xFF38BDF8)
                                )
                            }

                            Column {
                                Text(
                                    text = "POCHI",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
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
                        ApprovalCardUI(action = pendingApproval!!)
                    }

                    // 3. Scrollable AI Reply & Generative Visual Card Area
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 54.dp, max = 250.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
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
                                    Color(0xFF1D4ED8).copy(alpha = 0.30f),
                                    RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = replyText,
                                color = Color(0xFFEFF6FF),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }

                        if (activeRichCard != null) {
                            PochiRichCardView(
                                card = activeRichCard!!,
                                compact = true
                            )
                        }
                    }

                    // 4. Optional File Drop / Attach Drawer when "+" is clicked (No Emojis)
                    AnimatedVisibility(
                        visible = showDropDrawer,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
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

                    // 5. Bottom Chat Input Bar: "+" File Drop Button + Text Input + Mic + Send Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF070E1C))
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF1D4ED8).copy(alpha = 0.55f),
                                        Color(0xFF38BDF8).copy(alpha = 0.35f)
                                    )
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // "+" File Drop / Attachment Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (showDropDrawer) Color(0xFF1D4ED8) else Color(0xFF0F1D36)
                                )
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
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        // Chat Input Field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = "Tanya Pochi...",
                                    color = Color(0xFF7A8BA8),
                                    fontSize = 12.sp,
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
                                    fontSize = 12.sp,
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
                                .size(30.dp)
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
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Send Button (White Circular Paper-Plane Icon)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
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
