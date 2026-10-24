package com.pochi.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// 36 Ekspresi Lengkap Pochi (1 - 36)
enum class PochiEmote(val number: Int, val title: String) {
    NORMAL(1, "Normal"),
    HAPPY(2, "Happy"),
    LAUGHING(3, "Laughing"),
    SMILING(4, "Smiling"),
    WINK_LEFT(5, "Wink"),
    EXCITED(6, "Excited"),
    LOVE(7, "Love"),
    BLUSH(8, "Blush"),
    CUTE(9, "Cute"),
    SURPRISED(10, "Surprised"),
    SHOCK(11, "Shock"),
    CONFUSED(12, "Confused"),
    THINKING(13, "Thinking"),
    HMM(14, "Hmm"),
    SUSPICIOUS(15, "Suspicious"),
    ANGRY(16, "Angry"),
    ANNOYED(17, "Annoyed"),
    TIRED(18, "Tired"),
    SLEEPY(19, "Sleepy"),
    CALM(20, "Calm"),
    HAPPY_TEARS(21, "Happy Tears"),
    CRYING(22, "Crying"),
    SCARED(23, "Scared"),
    EMBARRASSED(24, "Embarrassed"),
    OK(25, "OK"),
    NO(26, "No"),
    BYE(27, "Bye"),
    HI(28, "Hi"),
    GOOD_NIGHT(29, "Good Night"),
    GOOD_MORNING(30, "Good Morning"),
    WORKING(31, "Working"),
    SUCCESS(32, "Success"),
    ERROR(33, "Error"),
    LOADING(34, "Loading"),
    FOCUS(35, "Focus"),
    PET_POKE(36, "Pet / Poke")
}

enum class BotState(
    val accentColor: Color,
    val statusLabel: String,
    val collapsedLabel: String,
    val defaultEmote: PochiEmote
) {
    IDLE(Color(0xFF6366F1), "Pochi", "Pochi", PochiEmote.SMILING),
    WAITING(Color(0xFFF59E0B), "Listening...", "Pochi", PochiEmote.CALM),
    WORKING(Color(0xFF38BDF8), "Working...", "Working...", PochiEmote.WORKING),
    DONE(Color(0xFF10B981), "Done", "Done", PochiEmote.HAPPY),
    APPROVAL(Color(0xFFF59E0B), "Approval Needed", "Approve?", PochiEmote.SURPRISED),
    ANNOYED(Color(0xFFEF4444), "Attention", "Alert", PochiEmote.ANGRY)
}

/**
 * Interactive Pochi Mascot (`blobatar.dev` Organic Morphing Blob + Expressive White Eyes).
 *
 * - Built 100% with Jetpack Compose `Canvas` (0MB static bitmap/GIF overhead).
 * - Smooth closed Catmull-Rom -> Cubic Bézier spline interpolation across 8 radial control points.
 * - State-based animations:
 *   - Idle blinking + gentle organic breathing morph
 *   - Happy (`^ ^`) upward arc eyes
 *   - Automatic Sleepy (`- -`) mode after 11:00 PM (23:00 - 05:00)
 *   - Squishy spring physics and pupil eye-tracking reactive to touch, tap, and swipe gestures.
 */
@Composable
fun PochiMascot(
    state: BotState,
    emoteOverride: PochiEmote? = null,
    size: Dp = 44.dp,
    blobColorOverride: Color? = null,
    eyeTrackerOffset: Offset = Offset.Zero,
    onPoke: (() -> Unit)? = null,
    onPet: (() -> Unit)? = null,
    onSwipe: ((Offset) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val hourOfDay = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val isLateNightSleepy = hourOfDay >= 23 || hourOfDay < 5

    val activeEmote = when {
        emoteOverride != null -> emoteOverride
        state == BotState.IDLE && isLateNightSleepy -> PochiEmote.SLEEPY
        else -> state.defaultEmote
    }

    // Continuous phase clock for organic blobatar.dev radial morphing
    val infiniteTransition = rememberInfiniteTransition(label = "blobatarMorph")
    val morphPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    activeEmote in setOf(PochiEmote.SLEEPY, PochiEmote.TIRED, PochiEmote.GOOD_NIGHT) -> 5600
                    state == BotState.WORKING || activeEmote in setOf(PochiEmote.EXCITED, PochiEmote.WORKING, PochiEmote.LOADING) -> 2100
                    else -> 3600
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphPhase"
    )

    val secondaryPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondaryPhase"
    )

    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isLateNightSleepy) 2400 else 1500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    // Squishy spring physics reactive to touch, poke, pet, and swipe
    val squishX = remember { Animatable(1f) }
    val squishY = remember { Animatable(1f) }
    val touchGazeX = remember { Animatable(0f) }
    val touchGazeY = remember { Animatable(0f) }
    var isPressed by remember { mutableStateOf(false) }

    fun triggerSquish(targetX: Float = 1.22f, targetY: Float = 0.80f) {
        coroutineScope.launch {
            launch {
                squishX.snapTo(targetX)
                squishX.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                squishY.snapTo(targetY)
                squishY.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    LaunchedEffect(activeEmote, state) {
        if (activeEmote in setOf(PochiEmote.HAPPY, PochiEmote.EXCITED, PochiEmote.LAUGHING, PochiEmote.SUCCESS, PochiEmote.PET_POKE)) {
            triggerSquish(1.16f, 0.86f)
        }
    }

    val targetTilt = when (activeEmote) {
        PochiEmote.CONFUSED, PochiEmote.THINKING, PochiEmote.HMM -> -7f
        PochiEmote.SUSPICIOUS -> 5f
        PochiEmote.WINK_LEFT, PochiEmote.CUTE, PochiEmote.HI, PochiEmote.BYE -> 6f
        PochiEmote.SLEEPY, PochiEmote.TIRED, PochiEmote.GOOD_NIGHT -> -4f
        else -> 0f
    }
    val animatedTilt by animateFloatAsState(
        targetValue = targetTilt,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "blobTilt"
    )

    val targetBlobColor = blobColorOverride ?: when {
        activeEmote in setOf(PochiEmote.ANGRY, PochiEmote.ERROR, PochiEmote.NO) -> Color(0xFFEF4444)
        activeEmote in setOf(PochiEmote.LOVE, PochiEmote.BLUSH, PochiEmote.CUTE, PochiEmote.PET_POKE) -> Color(0xFFEC4899)
        activeEmote in setOf(PochiEmote.HAPPY, PochiEmote.SUCCESS, PochiEmote.EXCITED, PochiEmote.LAUGHING) -> Color(0xFF10B981)
        activeEmote in setOf(PochiEmote.SLEEPY, PochiEmote.TIRED, PochiEmote.GOOD_NIGHT) -> Color(0xFF4F46E5)
        activeEmote in setOf(PochiEmote.SURPRISED, PochiEmote.SHOCK, PochiEmote.FOCUS) -> Color(0xFFF59E0B)
        state == BotState.WORKING || activeEmote in setOf(PochiEmote.WORKING, PochiEmote.LOADING, PochiEmote.THINKING) -> Color(0xFF0EA5E9)
        else -> Color(0xFF6366F1)
    }

    val blobColor by animateColorAsState(
        targetValue = targetBlobColor,
        animationSpec = tween(320),
        label = "blobColor"
    )

    // Natural periodic blinking in idle/normal states
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((2400..4600).random().toLong())
            isBlinking = true
            delay(135)
            isBlinking = false
        }
    }

    val blobPath = remember { Path() }
    val highlightPath = remember { Path() }

    val interactiveModifier = if (onPoke != null || onPet != null || onSwipe != null) {
        Modifier
            .pointerInput(onPoke, onPet) {
                val halfSizePx = (size.toPx() / 2f).coerceAtLeast(1f)
                detectTapGestures(
                    onPress = { pressOffset ->
                        isPressed = true
                        val nx = ((pressOffset.x - halfSizePx) / halfSizePx).coerceIn(-1f, 1f)
                        val ny = ((pressOffset.y - halfSizePx) / halfSizePx).coerceIn(-1f, 1f)
                        coroutineScope.launch {
                            touchGazeX.animateTo(nx, spring(stiffness = Spring.StiffnessMedium))
                            touchGazeY.animateTo(ny, spring(stiffness = Spring.StiffnessMedium))
                        }
                        triggerSquish(1.18f, 0.82f)
                        tryAwaitRelease()
                        isPressed = false
                        coroutineScope.launch {
                            touchGazeX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            touchGazeY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                    },
                    onTap = {
                        triggerSquish(1.24f, 0.78f)
                        onPoke?.invoke()
                    },
                    onLongPress = {
                        triggerSquish(1.28f, 0.75f)
                        onPet?.invoke()
                    }
                )
            }
            .pointerInput(onSwipe) {
                if (onSwipe != null) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val nx = (dragAmount.x / 24f).coerceIn(-1f, 1f)
                            val ny = (dragAmount.y / 24f).coerceIn(-1f, 1f)
                            coroutineScope.launch {
                                touchGazeX.snapTo(nx)
                                touchGazeY.snapTo(ny)
                                squishX.snapTo((1f + kotlin.math.abs(nx) * 0.18f).coerceAtMost(1.26f))
                                squishY.snapTo((1f - kotlin.math.abs(nx) * 0.14f).coerceAtLeast(0.78f))
                            }
                            onSwipe(dragAmount)
                        },
                        onDragEnd = {
                            triggerSquish(1.12f, 0.90f)
                            coroutineScope.launch {
                                touchGazeX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                touchGazeY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        }
                    )
                }
            }
    } else {
        Modifier
    }

    Canvas(
        modifier = Modifier
            .size(size)
            .then(interactiveModifier)
    ) {
        val w = this.size.width
        val h = this.size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val baseRadius = w.coerceAtMost(h) * 0.42f

        val morphIntensity = when {
            activeEmote in setOf(PochiEmote.SLEEPY, PochiEmote.TIRED, PochiEmote.CALM, PochiEmote.GOOD_NIGHT) -> 0.045f
            activeEmote in setOf(PochiEmote.EXCITED, PochiEmote.LAUGHING, PochiEmote.WORKING, PochiEmote.ANGRY, PochiEmote.SHOCK) -> 0.105f
            else -> 0.072f
        }

        val combinedGazeX = (touchGazeX.value + eyeTrackerOffset.x).coerceIn(-1f, 1f)
        val combinedGazeY = (touchGazeY.value + eyeTrackerOffset.y).coerceIn(-1f, 1f)

        rotate(degrees = animatedTilt, pivot = Offset(cx, cy)) {
            scale(
                scaleX = breathScale * squishX.value,
                scaleY = (1f / breathScale) * squishY.value,
                pivot = Offset(cx, cy)
            ) {
                // 1. Build organic blobatar.dev closed spline across 8 radial control points
                buildBlobSplinePath(
                    outPath = blobPath,
                    cx = cx,
                    cy = cy,
                    baseRadius = baseRadius,
                    phase1 = morphPhase,
                    phase2 = secondaryPhase,
                    amplitude = morphIntensity,
                    pullX = combinedGazeX * baseRadius * 0.08f,
                    pullY = combinedGazeY * baseRadius * 0.08f
                )

                // Soft outer ambient glow on dark #0A0A0A island
                drawPath(
                    path = blobPath,
                    color = blobColor.copy(alpha = 0.24f),
                    style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Main organic squishy blob body with subtle radial gradient
                drawPath(
                    path = blobPath,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            lerp(blobColor, Color.White, 0.22f),
                            blobColor,
                            lerp(blobColor, Color.Black, 0.28f)
                        ),
                        center = Offset(cx - baseRadius * 0.25f, cy - baseRadius * 0.28f),
                        radius = baseRadius * 1.35f
                    ),
                    style = Fill
                )

                // Subtle top-left organic specular highlight blob
                buildBlobSplinePath(
                    outPath = highlightPath,
                    cx = cx - baseRadius * 0.22f,
                    cy = cy - baseRadius * 0.26f,
                    baseRadius = baseRadius * 0.34f,
                    phase1 = morphPhase + 0.8f,
                    phase2 = secondaryPhase,
                    amplitude = morphIntensity * 0.5f,
                    pullX = 0f,
                    pullY = 0f
                )
                drawPath(
                    path = highlightPath,
                    color = Color.White.copy(alpha = 0.16f),
                    style = Fill
                )

                // 2. Minimalist Expressive White Eyes (reactive to gaze + state)
                val eyeShiftX = combinedGazeX * (w * 0.075f)
                val eyeShiftY = combinedGazeY * (h * 0.065f)
                val leftEyeX = cx - w * 0.135f + eyeShiftX
                val rightEyeX = cx + w * 0.135f + eyeShiftX
                val eyeY = cy + eyeShiftY
                val strokeW = (w * 0.068f).coerceAtLeast(2.2f)

                val canAutoBlink = activeEmote in setOf(
                    PochiEmote.NORMAL,
                    PochiEmote.SMILING,
                    PochiEmote.HI,
                    PochiEmote.FOCUS,
                    PochiEmote.WORKING,
                    PochiEmote.LOADING,
                    PochiEmote.CALM
                )

                if (isBlinking && canAutoBlink) {
                    drawSleepyDashEye(leftEyeX, eyeY, w, Color.White, strokeW)
                    drawSleepyDashEye(rightEyeX, eyeY, w, Color.White, strokeW)
                } else {
                    drawBlobWhiteEyes(
                        emote = activeEmote,
                        leftX = leftEyeX,
                        rightX = rightEyeX,
                        eyeY = eyeY,
                        w = w,
                        h = h,
                        strokeW = strokeW
                    )
                }
            }
        }
    }
}

/**
 * Generates a smooth closed Catmull-Rom spline converted to Cubic Bézier segments (`blobatar.dev` style)
 * around 8 radial control points with multi-harmonic organic oscillation.
 */
private fun buildBlobSplinePath(
    outPath: Path,
    cx: Float,
    cy: Float,
    baseRadius: Float,
    phase1: Float,
    phase2: Float,
    amplitude: Float,
    pullX: Float,
    pullY: Float
) {
    outPath.reset()
    val count = 8
    val xs = FloatArray(count)
    val ys = FloatArray(count)
    val step = (2.0 * PI / count).toFloat()

    for (i in 0 until count) {
        val angle = i * step
        val wave1 = sin(phase1 + i * 1.75f)
        val wave2 = cos(phase2 - i * 2.35f)
        val wave3 = sin(phase1 * 2f + i * 0.95f)
        val r = baseRadius * (1f + amplitude * (0.55f * wave1 + 0.30f * wave2 + 0.15f * wave3))
        xs[i] = cx + pullX + r * cos(angle)
        ys[i] = cy + pullY + r * sin(angle)
    }

    // Closed Catmull-Rom to Cubic Bézier conversion (tension = 1.0)
    outPath.moveTo(xs[0], ys[0])
    for (i in 0 until count) {
        val p0x = xs[(i - 1 + count) % count]
        val p0y = ys[(i - 1 + count) % count]
        val p1x = xs[i]
        val p1y = ys[i]
        val p2x = xs[(i + 1) % count]
        val p2y = ys[(i + 1) % count]
        val p3x = xs[(i + 2) % count]
        val p3y = ys[(i + 2) % count]

        val cp1x = p1x + (p2x - p0x) / 6f
        val cp1y = p1y + (p2y - p0y) / 6f
        val cp2x = p2x - (p3x - p1x) / 6f
        val cp2y = p2y - (p3y - p1y) / 6f

        outPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y)
    }
    outPath.close()
}

private fun DrawScope.drawIdleCapsuleEye(
    cx: Float,
    cy: Float,
    w: Float,
    color: Color,
    scaleY: Float = 1f,
    scaleX: Float = 1f
) {
    val ew = w * 0.105f * scaleX
    val eh = w * 0.175f * scaleY
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - ew * 0.5f, cy - eh * 0.5f),
        size = Size(ew, eh),
        cornerRadius = CornerRadius(ew * 0.5f, ew * 0.5f)
    )
}

/**
 * Happy (`^ ^`) upward chevron/arc eye in minimalist white.
 */
private fun DrawScope.drawHappyCaretEye(
    cx: Float,
    cy: Float,
    w: Float,
    color: Color,
    strokeW: Float
) {
    val halfW = w * 0.068f
    val halfH = w * 0.048f
    val path = Path().apply {
        moveTo(cx - halfW, cy + halfH * 0.55f)
        quadraticBezierTo(cx, cy - halfH * 1.15f, cx + halfW, cy + halfH * 0.55f)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * Sleepy (`- -`) horizontal dash eye (automatically used after 11 PM or during rest).
 */
private fun DrawScope.drawSleepyDashEye(
    cx: Float,
    cy: Float,
    w: Float,
    color: Color,
    strokeW: Float
) {
    val halfW = w * 0.068f
    drawLine(
        color = color,
        start = Offset(cx - halfW, cy),
        end = Offset(cx + halfW, cy),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawBlobWhiteEyes(
    emote: PochiEmote,
    leftX: Float,
    rightX: Float,
    eyeY: Float,
    w: Float,
    h: Float,
    strokeW: Float
) {
    val white = Color.White
    when (emote) {
        // Happy (`^ ^`) expressions
        PochiEmote.HAPPY,
        PochiEmote.LAUGHING,
        PochiEmote.SMILING,
        PochiEmote.CUTE,
        PochiEmote.EXCITED,
        PochiEmote.GOOD_MORNING,
        PochiEmote.SUCCESS,
        PochiEmote.PET_POKE,
        PochiEmote.HAPPY_TEARS -> {
            drawHappyCaretEye(leftX, eyeY, w, white, strokeW)
            drawHappyCaretEye(rightX, eyeY, w, white, strokeW)
            if (emote in setOf(PochiEmote.BLUSH, PochiEmote.CUTE, PochiEmote.PET_POKE)) {
                val blush = Color(0xFFFFB4C8).copy(alpha = 0.65f)
                drawCircle(blush, radius = w * 0.045f, center = Offset(leftX - w * 0.06f, eyeY + h * 0.09f))
                drawCircle(blush, radius = w * 0.045f, center = Offset(rightX + w * 0.06f, eyeY + h * 0.09f))
            }
        }

        // Sleepy (`- -`) expressions (active after 11 PM or when tired/calm)
        PochiEmote.SLEEPY,
        PochiEmote.TIRED,
        PochiEmote.GOOD_NIGHT,
        PochiEmote.CALM,
        PochiEmote.BLUSH,
        PochiEmote.EMBARRASSED -> {
            drawSleepyDashEye(leftX, eyeY, w, white, strokeW)
            drawSleepyDashEye(rightX, eyeY, w, white, strokeW)
            if (emote in setOf(PochiEmote.BLUSH, PochiEmote.EMBARRASSED)) {
                val blush = Color(0xFFFFB4C8).copy(alpha = 0.65f)
                drawCircle(blush, radius = w * 0.045f, center = Offset(leftX - w * 0.05f, eyeY + h * 0.08f))
                drawCircle(blush, radius = w * 0.045f, center = Offset(rightX + w * 0.05f, eyeY + h * 0.08f))
            }
        }

        // Wink (`^ •`)
        PochiEmote.WINK_LEFT,
        PochiEmote.OK,
        PochiEmote.BYE -> {
            drawHappyCaretEye(leftX, eyeY, w, white, strokeW)
            drawIdleCapsuleEye(rightX, eyeY, w, white)
        }

        // Curious / Thinking / Confused (asymmetric expressive capsule eyes)
        PochiEmote.THINKING,
        PochiEmote.HMM,
        PochiEmote.CONFUSED,
        PochiEmote.SUSPICIOUS -> {
            drawSleepyDashEye(leftX, eyeY - h * 0.01f, w, white, strokeW)
            drawIdleCapsuleEye(rightX, eyeY, w, white, scaleY = 1.08f)
        }

        // Surprised / Shock / Scared (wide circular white eyes)
        PochiEmote.SURPRISED,
        PochiEmote.SHOCK,
        PochiEmote.SCARED -> {
            drawIdleCapsuleEye(leftX, eyeY, w, white, scaleY = 1.15f, scaleX = 1.18f)
            drawIdleCapsuleEye(rightX, eyeY, w, white, scaleY = 1.15f, scaleX = 1.18f)
        }

        // Focused / Angry / Annoyed / Error (angled white eyes)
        PochiEmote.ANGRY,
        PochiEmote.ANNOYED,
        PochiEmote.ERROR,
        PochiEmote.NO,
        PochiEmote.FOCUS -> {
            val r = w * 0.065f
            drawLine(
                color = white,
                start = Offset(leftX - r, eyeY - r * 0.45f),
                end = Offset(leftX + r, eyeY + r * 0.35f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = white,
                start = Offset(rightX - r, eyeY + r * 0.35f),
                end = Offset(rightX + r, eyeY - r * 0.45f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }

        // Love
        PochiEmote.LOVE -> {
            drawHappyCaretEye(leftX, eyeY, w, white, strokeW)
            drawHappyCaretEye(rightX, eyeY, w, white, strokeW)
            drawCircle(Color(0xFFFFB4C8), radius = w * 0.048f, center = Offset(leftX - w * 0.06f, eyeY + h * 0.09f))
            drawCircle(Color(0xFFFFB4C8), radius = w * 0.048f, center = Offset(rightX + w * 0.06f, eyeY + h * 0.09f))
        }

        // Crying
        PochiEmote.CRYING -> {
            drawSleepyDashEye(leftX, eyeY, w, white, strokeW)
            drawSleepyDashEye(rightX, eyeY, w, white, strokeW)
            val tear = Color(0xFF7DD3FC)
            drawCircle(tear, radius = w * 0.036f, center = Offset(leftX - w * 0.04f, eyeY + h * 0.09f))
            drawCircle(tear, radius = w * 0.036f, center = Offset(rightX + w * 0.04f, eyeY + h * 0.09f))
        }

        // Default Idle / Working (`• •`)
        else -> {
            drawIdleCapsuleEye(leftX, eyeY, w, white)
            drawIdleCapsuleEye(rightX, eyeY, w, white)
        }
    }
}

object PochiMood {
    /**
     * Autonomous mood generator with automatic after-11PM Sleepy (`- -`) state.
     */
    fun nextAutonomousMood(idleMillis: Long, isPeeking: Boolean): PochiEmote {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour >= 23 || hour < 5) {
            return listOf(
                PochiEmote.SLEEPY,
                PochiEmote.GOOD_NIGHT,
                PochiEmote.TIRED,
                PochiEmote.CALM
            ).random()
        }
        if (isPeeking) {
            return listOf(
                PochiEmote.SUSPICIOUS,
                PochiEmote.WINK_LEFT,
                PochiEmote.CUTE,
                PochiEmote.SURPRISED,
                PochiEmote.HMM,
                PochiEmote.SLEEPY,
                PochiEmote.HI
            ).random()
        }
        if (idleMillis > 120_000L) {
            return listOf(
                PochiEmote.SLEEPY,
                PochiEmote.TIRED,
                PochiEmote.HMM,
                PochiEmote.CALM
            ).random()
        }
        return when (hour) {
            in 5..9 -> listOf(
                PochiEmote.GOOD_MORNING,
                PochiEmote.HI,
                PochiEmote.HAPPY,
                PochiEmote.SMILING,
                PochiEmote.EXCITED
            ).random()
            in 10..17 -> listOf(
                PochiEmote.NORMAL,
                PochiEmote.SMILING,
                PochiEmote.HAPPY,
                PochiEmote.WINK_LEFT,
                PochiEmote.FOCUS,
                PochiEmote.THINKING
            ).random()
            else -> listOf(
                PochiEmote.CALM,
                PochiEmote.SMILING,
                PochiEmote.HAPPY,
                PochiEmote.BLUSH,
                PochiEmote.SLEEPY
            ).random()
        }
    }

    fun onPokeReaction(pokeCount: Int): Triple<PochiEmote, BotState, String> {
        val isId = PochiPrefs.langFlow.value == PochiLang.ID
        return when (pokeCount) {
            1 -> Triple(
                PochiEmote.HAPPY,
                BotState.IDLE,
                if (isId) "Halo! Ketuk untuk buka Dynamic Island atau tahan untuk Perintah Cepat."
                else "Hey! Tap to open your Dynamic Island or hold for Quick Command."
            )
            2 -> Triple(
                PochiEmote.WINK_LEFT,
                BotState.IDLE,
                if (isId) "Hehe, mode blob kenyal aktif! (`^ ^`)"
                else "Hehe, squishy blob mode active! (`^ ^`)"
            )
            3 -> Triple(
                PochiEmote.SUSPICIOUS,
                BotState.ANNOYED,
                if (isId) "Hmm... kok dicolek terus hari ini?"
                else "Hmm... poking me a lot today?"
            )
            4 -> Triple(
                PochiEmote.ANGRY,
                BotState.ANNOYED,
                if (isId) "Jangan dicolek terus dong! Tahan di kepala buat ngelus."
                else "Hey, easy on the pokes! Hold to pet me instead."
            )
            else -> Triple(
                PochiEmote.CRYING,
                BotState.ANNOYED,
                if (isId) "Pusing dicolek mulu... tahan sebentar buat nenangin Pochi."
                else "Dizzy from all the pokes... hold on my head to calm me down."
            )
        }
    }

    fun onPetReaction(wasUpset: Boolean): Triple<PochiEmote, BotState, String> {
        val isId = PochiPrefs.langFlow.value == PochiLang.ID
        return if (wasUpset) {
            Triple(
                PochiEmote.HAPPY_TEARS,
                BotState.DONE,
                if (isId) "Oke dimaafin! Siap bantu lagi (`^ ^`)."
                else "All forgiven! Ready to help (`^ ^`)."
            )
        } else {
            Triple(
                PochiEmote.LOVE,
                BotState.DONE,
                if (isId) "Nyaman banget dielus, makin semangat (`^ ^`)!"
                else "Purr... feeling energized (`^ ^`)!"
            )
        }
    }

    fun inferEmoteFromText(userText: String, replyText: String): PochiEmote {
        val combined = "$userText $replyText".lowercase(Locale.getDefault())
        return when {
            combined.contains("selamat pagi") || combined.contains("good morning") -> PochiEmote.GOOD_MORNING
            combined.contains("selamat malam") || combined.contains("good night") || combined.contains("sleep") -> PochiEmote.SLEEPY
            combined.contains("terima kasih") || combined.contains("makasih") || combined.contains("thanks") -> PochiEmote.HAPPY
            combined.contains("error") || combined.contains("failed") || combined.contains("gagal") -> PochiEmote.ERROR
            combined.contains("beres") || combined.contains("done") || combined.contains("scheduled") || combined.contains("started") -> PochiEmote.HAPPY
            else -> PochiEmote.HAPPY
        }
    }
}
