package com.pochi.assistant

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

// 36 Ekspresi Lengkap Pochi (Mata Putih Bercahaya di Background Hitam ala Nochi/Tini)
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
    HMM(14, "Curious"),
    SUSPICIOUS(15, "Suspicious"),
    ANGRY(16, "Angry"),
    ANNOYED(17, "Annoyed"),
    TIRED(18, "Tired"),
    SLEEPY(19, "Sleepy"),
    CALM(20, "Calm"),
    HAPPY_TEARS(21, "Happy Tears"),
    CRYING(22, "Crying"),
    SCARED(23, "Scared"),
    EMBARRASSED(24, "Shy"),
    OK(25, "OK"),
    NO(26, "Dizzy"),
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
    IDLE(Color(0xFFFFFFFF), "Pochi", "Pochi", PochiEmote.NORMAL),
    WAITING(Color(0xFFF59E0B), "Listening...", "Pochi", PochiEmote.HMM),
    WORKING(Color(0xFF38BDF8), "Working...", "Working...", PochiEmote.WORKING),
    DONE(Color(0xFF10B981), "Done", "Done", PochiEmote.HAPPY),
    APPROVAL(Color(0xFFF59E0B), "Needs You", "Approve?", PochiEmote.SURPRISED),
    ANNOYED(Color(0xFFEF4444), "Alert", "Alert", PochiEmote.ANGRY)
}

/**
 * Pochi / Nochi Minimalist White Eyes on Pure Matte-Black Background.
 *
 * - Renders high-contrast glowing white (`#FFFFFF`) eyes over a `#0A0A0A` black squircle/notch.
 * - Supports 36 distinct expressions (`PochiEmote`):
 *   idle capsules (`• •`), happy carets (`^ ^`), laughing chevrons (`> <`), sleepy bars (`- -`),
 *   curious tilt (`• ●`), angry slants (`\ /`), suspicious flat-tops (`¬ ¬`), shocked rings (`O O`),
 *   crying tears (`; ;`), hearts (`♥ ♥`), stars (`✦ ✦`), dizzy (`X X`), and more.
 * - Reactive to touch, poke, long-press pet, and swipe gaze-tracking with 120fps spring physics.
 */
@Composable
fun PochiMascot(
    state: BotState,
    emoteOverride: PochiEmote? = null,
    size: Dp = 44.dp,
    showBlackBacking: Boolean = true,
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

    val infiniteTransition = rememberInfiniteTransition(label = "nochiEyes")
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

    val idleLookDrift by infiniteTransition.animateFloat(
        initialValue = -0.22f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleLookDrift"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.48f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Squishy spring physics reactive to touch, tap, and swipe
    val squishX = remember { Animatable(1f) }
    val squishY = remember { Animatable(1f) }
    val touchGazeX = remember { Animatable(0f) }
    val touchGazeY = remember { Animatable(0f) }

    fun triggerSquish(targetX: Float = 1.22f, targetY: Float = 0.78f) {
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
            triggerSquish(1.16f, 0.85f)
        }
    }

    val targetTilt = when (activeEmote) {
        PochiEmote.CONFUSED, PochiEmote.THINKING, PochiEmote.HMM -> -6f
        PochiEmote.SUSPICIOUS -> 4f
        PochiEmote.WINK_LEFT, PochiEmote.CUTE, PochiEmote.HI, PochiEmote.BYE -> 5f
        else -> 0f
    }
    val animatedTilt by animateFloatAsState(
        targetValue = targetTilt,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "eyeTilt"
    )

    // Natural periodic blinking
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((2200..4500).random().toLong())
            isBlinking = true
            delay(130)
            isBlinking = false
        }
    }

    val interactiveModifier = if (onPoke != null || onPet != null || onSwipe != null) {
        Modifier
            .pointerInput(onPoke, onPet) {
                val halfSizePx = (size.toPx() / 2f).coerceAtLeast(1f)
                detectTapGestures(
                    onPress = { pressOffset ->
                        val nx = ((pressOffset.x - halfSizePx) / halfSizePx).coerceIn(-1f, 1f)
                        val ny = ((pressOffset.y - halfSizePx) / halfSizePx).coerceIn(-1f, 1f)
                        coroutineScope.launch {
                            touchGazeX.animateTo(nx, spring(stiffness = Spring.StiffnessMedium))
                            touchGazeY.animateTo(ny, spring(stiffness = Spring.StiffnessMedium))
                        }
                        triggerSquish(1.18f, 0.82f)
                        tryAwaitRelease()
                        coroutineScope.launch {
                            touchGazeX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            touchGazeY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                    },
                    onTap = {
                        triggerSquish(1.25f, 0.76f)
                        onPoke?.invoke()
                    },
                    onLongPress = {
                        triggerSquish(1.28f, 0.74f)
                        onPet?.invoke()
                    }
                )
            }
            .pointerInput(onSwipe) {
                if (onSwipe != null) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val nx = (dragAmount.x / 22f).coerceIn(-1f, 1f)
                            val ny = (dragAmount.y / 22f).coerceIn(-1f, 1f)
                            coroutineScope.launch {
                                touchGazeX.snapTo(nx)
                                touchGazeY.snapTo(ny)
                            }
                            onSwipe(dragAmount)
                        },
                        onDragEnd = {
                            triggerSquish(1.14f, 0.88f)
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

    val boxWidth = if (showBlackBacking) size * 1.42f else size
    Canvas(
        modifier = Modifier
            .size(width = boxWidth, height = size)
            .then(interactiveModifier)
    ) {
        val w = this.size.width
        val h = this.size.height
        val s = minOf(w, h)
        val cx = w * 0.5f
        val cy = h * 0.5f

        val combinedGazeX = (touchGazeX.value + eyeTrackerOffset.x +
            if (activeEmote == PochiEmote.HMM || activeEmote == PochiEmote.WORKING) idleLookDrift else 0f)
            .coerceIn(-1f, 1f)
        val combinedGazeY = (touchGazeY.value + eyeTrackerOffset.y).coerceIn(-1f, 1f)

        rotate(degrees = animatedTilt, pivot = Offset(cx, cy)) {
            scale(
                scaleX = breathScale * squishX.value,
                scaleY = (1f / breathScale) * squishY.value,
                pivot = Offset(cx, cy)
            ) {
                // Pure Matte-Black (#0A0A0A) sleek rounded-rectangle backing (Frame 2s / 35s style, not a circle)
                if (showBlackBacking) {
                    val pad = s * 0.04f
                    val corner = (h * 0.22f).coerceAtLeast(6f)
                    drawRoundRect(
                        color = Color(0xFF0A0A0A),
                        topLeft = Offset(pad, pad),
                        size = Size(w - pad * 2f, h - pad * 2f),
                        cornerRadius = CornerRadius(corner, corner)
                    )
                    drawRoundRect(
                        color = Color(0xFF26262E),
                        topLeft = Offset(pad, pad),
                        size = Size(w - pad * 2f, h - pad * 2f),
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = (s * 0.025f).coerceAtLeast(1f))
                    )
                }

                val eyeShiftX = combinedGazeX * (s * 0.095f)
                val eyeShiftY = combinedGazeY * (s * 0.075f)
                val leftEyeX = cx - s * 0.175f + eyeShiftX
                val rightEyeX = cx + s * 0.175f + eyeShiftX
                val eyeY = cy + eyeShiftY
                val strokeW = (s * 0.088f).coerceAtLeast(2.4f)

                val canAutoBlink = activeEmote in setOf(
                    PochiEmote.NORMAL,
                    PochiEmote.SMILING,
                    PochiEmote.HI,
                    PochiEmote.FOCUS,
                    PochiEmote.WORKING,
                    PochiEmote.LOADING,
                    PochiEmote.HMM
                )

                if (isBlinking && canAutoBlink) {
                    drawGlowingDashEye(leftEyeX, eyeY, s, strokeW, pulseAlpha)
                    drawGlowingDashEye(rightEyeX, eyeY, s, strokeW, pulseAlpha)
                } else {
                    drawNochiWhiteExpression(
                        emote = activeEmote,
                        leftX = leftEyeX,
                        rightX = rightEyeX,
                        eyeY = eyeY,
                        w = s,
                        h = s,
                        strokeW = strokeW,
                        glowAlpha = pulseAlpha
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawGlowingPillEye(
    cx: Float,
    cy: Float,
    w: Float,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    glowAlpha: Float = 0.32f
) {
    val ew = w * 0.145f * scaleX
    val eh = w * 0.255f * scaleY
    // Soft white halo glow around eye (like frames 2s & 35s)
    drawRoundRect(
        color = Color.White.copy(alpha = glowAlpha * 0.55f),
        topLeft = Offset(cx - ew * 0.72f, cy - eh * 0.65f),
        size = Size(ew * 1.44f, eh * 1.30f),
        cornerRadius = CornerRadius(ew, ew)
    )
    // Crisp pure white eye core
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(cx - ew * 0.5f, cy - eh * 0.5f),
        size = Size(ew, eh),
        cornerRadius = CornerRadius(ew * 0.5f, ew * 0.5f)
    )
}

private fun DrawScope.drawGlowingCaretEye(
    cx: Float,
    cy: Float,
    w: Float,
    strokeW: Float,
    glowAlpha: Float
) {
    val halfW = w * 0.088f
    val halfH = w * 0.062f
    val path = Path().apply {
        moveTo(cx - halfW, cy + halfH * 0.55f)
        quadraticBezierTo(cx, cy - halfH * 1.25f, cx + halfW, cy + halfH * 0.55f)
    }
    drawPath(
        path = path,
        color = Color.White.copy(alpha = glowAlpha * 0.55f),
        style = Stroke(width = strokeW * 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = path,
        color = Color.White,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawGlowingDashEye(
    cx: Float,
    cy: Float,
    w: Float,
    strokeW: Float,
    glowAlpha: Float
) {
    val halfW = w * 0.085f
    drawLine(
        color = Color.White.copy(alpha = glowAlpha * 0.5f),
        start = Offset(cx - halfW, cy),
        end = Offset(cx + halfW, cy),
        strokeWidth = strokeW * 1.6f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = Offset(cx - halfW, cy),
        end = Offset(cx + halfW, cy),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawChevronSquintEye(
    cx: Float,
    cy: Float,
    w: Float,
    isLeft: Boolean,
    strokeW: Float
) {
    val r = w * 0.075f
    val path = Path().apply {
        if (isLeft) {
            moveTo(cx - r, cy - r * 0.7f)
            lineTo(cx + r * 0.75f, cy)
            lineTo(cx - r, cy + r * 0.7f)
        } else {
            moveTo(cx + r, cy - r * 0.7f)
            lineTo(cx - r * 0.75f, cy)
            lineTo(cx + r, cy + r * 0.7f)
        }
    }
    drawPath(
        path = path,
        color = Color.White,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawStarEye(cx: Float, cy: Float, w: Float, strokeW: Float) {
    val r = w * 0.095f
    drawLine(Color.White, Offset(cx - r, cy), Offset(cx + r, cy), strokeW * 0.85f, StrokeCap.Round)
    drawLine(Color.White, Offset(cx, cy - r), Offset(cx, cy + r), strokeW * 0.85f, StrokeCap.Round)
    drawCircle(Color.White, radius = w * 0.045f, center = Offset(cx, cy))
}

private fun DrawScope.drawHeartEye(cx: Float, cy: Float, w: Float) {
    val r = w * 0.09f
    val path = Path().apply {
        moveTo(cx, cy + r * 0.85f)
        cubicTo(cx - r * 1.4f, cy + r * 0.1f, cx - r * 0.9f, cy - r * 0.95f, cx, cy - r * 0.3f)
        cubicTo(cx + r * 0.9f, cy - r * 0.95f, cx + r * 1.4f, cy + r * 0.1f, cx, cy + r * 0.85f)
        close()
    }
    drawPath(path, color = Color.White, style = Fill)
}

private fun DrawScope.drawNochiWhiteExpression(
    emote: PochiEmote,
    leftX: Float,
    rightX: Float,
    eyeY: Float,
    w: Float,
    h: Float,
    strokeW: Float,
    glowAlpha: Float
) {
    when (emote) {
        // 1. Normal / Hi / Working / Loading (`• •` classic glowing white pill eyes like 2s, 4s)
        PochiEmote.NORMAL,
        PochiEmote.HI,
        PochiEmote.WORKING,
        PochiEmote.LOADING -> {
            drawGlowingPillEye(leftX, eyeY, w, glowAlpha = glowAlpha)
            drawGlowingPillEye(rightX, eyeY, w, glowAlpha = glowAlpha)
        }

        // 2. Curious / Hmm / Confused (`• ●` asymmetric curious white eyes like 35s)
        PochiEmote.HMM,
        PochiEmote.CONFUSED -> {
            drawGlowingPillEye(leftX, eyeY + h * 0.01f, w, scaleX = 1.05f, scaleY = 0.78f, glowAlpha = glowAlpha)
            drawGlowingPillEye(rightX, eyeY - h * 0.01f, w, scaleX = 1.18f, scaleY = 0.95f, glowAlpha = glowAlpha)
        }

        // 3. Thinking (`- •` one flat eye, one raised tall eye)
        PochiEmote.THINKING -> {
            drawGlowingDashEye(leftX, eyeY, w, strokeW, glowAlpha)
            drawGlowingPillEye(rightX, eyeY - h * 0.02f, w, scaleX = 1.08f, scaleY = 1.08f, glowAlpha = glowAlpha)
        }

        // 4. Happy / Smiling / Cute / Good Morning / Success / Pet (`^ ^` like 20s)
        PochiEmote.HAPPY,
        PochiEmote.SMILING,
        PochiEmote.CUTE,
        PochiEmote.GOOD_MORNING,
        PochiEmote.SUCCESS,
        PochiEmote.PET_POKE,
        PochiEmote.BLUSH -> {
            drawGlowingCaretEye(leftX, eyeY, w, strokeW, glowAlpha)
            drawGlowingCaretEye(rightX, eyeY, w, strokeW, glowAlpha)
        }

        // 5. Laughing (`> <` happy squinting chevrons)
        PochiEmote.LAUGHING -> {
            drawChevronSquintEye(leftX, eyeY, w, isLeft = true, strokeW = strokeW)
            drawChevronSquintEye(rightX, eyeY, w, isLeft = false, strokeW = strokeW)
        }

        // 6. Wink / OK / Bye (`^ •`)
        PochiEmote.WINK_LEFT,
        PochiEmote.OK,
        PochiEmote.BYE -> {
            drawGlowingCaretEye(leftX, eyeY, w, strokeW, glowAlpha)
            drawGlowingPillEye(rightX, eyeY, w, glowAlpha = glowAlpha)
        }

        // 7. Excited (`✦ ✦` sparkling white star eyes)
        PochiEmote.EXCITED -> {
            drawStarEye(leftX, eyeY, w, strokeW)
            drawStarEye(rightX, eyeY, w, strokeW)
        }

        // 8. Love (`♥ ♥` glowing white heart eyes)
        PochiEmote.LOVE -> {
            drawHeartEye(leftX, eyeY, w)
            drawHeartEye(rightX, eyeY, w)
        }

        // 9. Sleepy / Good Night / Calm (`- -` horizontal glowing white bars)
        PochiEmote.SLEEPY,
        PochiEmote.GOOD_NIGHT,
        PochiEmote.CALM -> {
            drawGlowingDashEye(leftX, eyeY, w, strokeW, glowAlpha)
            drawGlowingDashEye(rightX, eyeY, w, strokeW, glowAlpha)
        }

        // 10. Tired / Embarrassed (`u u` droopy downward arcs)
        PochiEmote.TIRED,
        PochiEmote.EMBARRASSED -> {
            val halfW = w * 0.082f
            val halfH = w * 0.048f
            for (cx in listOf(leftX, rightX)) {
                val p = Path().apply {
                    moveTo(cx - halfW, eyeY - halfH * 0.4f)
                    quadraticBezierTo(cx, eyeY + halfH * 1.1f, cx + halfW, eyeY - halfH * 0.4f)
                }
                drawPath(p, Color.White, style = Stroke(width = strokeW, cap = StrokeCap.Round))
            }
        }

        // 11. Surprised / Shock (`O O` wide open hollow white rings)
        PochiEmote.SURPRISED,
        PochiEmote.SHOCK -> {
            val r = w * 0.095f
            drawCircle(Color.White.copy(alpha = glowAlpha * 0.45f), radius = r * 1.35f, center = Offset(leftX, eyeY))
            drawCircle(Color.White.copy(alpha = glowAlpha * 0.45f), radius = r * 1.35f, center = Offset(rightX, eyeY))
            drawCircle(Color.White, radius = r, center = Offset(leftX, eyeY), style = Stroke(width = strokeW * 0.9f))
            drawCircle(Color.White, radius = r, center = Offset(rightX, eyeY), style = Stroke(width = strokeW * 0.9f))
        }

        // 12. Scared (`• •` tiny trembling white dots)
        PochiEmote.SCARED -> {
            drawGlowingPillEye(leftX, eyeY, w, scaleX = 0.65f, scaleY = 0.55f, glowAlpha = glowAlpha)
            drawGlowingPillEye(rightX, eyeY, w, scaleX = 0.65f, scaleY = 0.55f, glowAlpha = glowAlpha)
        }

        // 13. Suspicious / Annoyed (`¬ ¬` narrow flat-top white eyes)
        PochiEmote.SUSPICIOUS,
        PochiEmote.ANNOYED -> {
            drawGlowingPillEye(leftX, eyeY, w, scaleX = 1.15f, scaleY = 0.42f, glowAlpha = glowAlpha)
            drawGlowingPillEye(rightX, eyeY, w, scaleX = 1.15f, scaleY = 0.42f, glowAlpha = glowAlpha)
        }

        // 14. Angry / Focus (`\ /` fierce angled white eyes)
        PochiEmote.ANGRY,
        PochiEmote.FOCUS -> {
            val r = w * 0.085f
            drawLine(
                color = Color.White,
                start = Offset(leftX - r, eyeY - r * 0.48f),
                end = Offset(leftX + r, eyeY + r * 0.38f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(rightX - r, eyeY + r * 0.38f),
                end = Offset(rightX + r, eyeY - r * 0.48f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }

        // 15. Crying / Happy Tears (`; ;` white eyes with glowing white teardrops)
        PochiEmote.CRYING,
        PochiEmote.HAPPY_TEARS -> {
            if (emote == PochiEmote.HAPPY_TEARS) {
                drawGlowingCaretEye(leftX, eyeY, w, strokeW, glowAlpha)
                drawGlowingCaretEye(rightX, eyeY, w, strokeW, glowAlpha)
            } else {
                drawGlowingDashEye(leftX, eyeY, w, strokeW, glowAlpha)
                drawGlowingDashEye(rightX, eyeY, w, strokeW, glowAlpha)
            }
            drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.036f, center = Offset(leftX - w * 0.05f, eyeY + h * 0.14f))
            drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.036f, center = Offset(rightX + w * 0.05f, eyeY + h * 0.14f))
        }

        // 16. Error / No (`X X` cross eyes)
        PochiEmote.ERROR,
        PochiEmote.NO -> {
            val r = w * 0.072f
            for (cx in listOf(leftX, rightX)) {
                drawLine(Color.White, Offset(cx - r, eyeY - r), Offset(cx + r, eyeY + r), strokeW * 0.85f, StrokeCap.Round)
                drawLine(Color.White, Offset(cx + r, eyeY - r), Offset(cx - r, eyeY + r), strokeW * 0.85f, StrokeCap.Round)
            }
        }
    }
}

object PochiMood {
    /**
     * Cycles across all rich white-eye expressions so Pochi feels alive like Nochi.
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
                PochiEmote.HMM,
                PochiEmote.SUSPICIOUS,
                PochiEmote.WINK_LEFT,
                PochiEmote.SURPRISED
            ).random()
        }
        if (idleMillis > 120_000L) {
            return listOf(
                PochiEmote.HMM,
                PochiEmote.SLEEPY,
                PochiEmote.TIRED,
                PochiEmote.THINKING,
                PochiEmote.SUSPICIOUS
            ).random()
        }
        return listOf(
            PochiEmote.NORMAL,
            PochiEmote.NORMAL,
            PochiEmote.HMM,
            PochiEmote.SMILING,
            PochiEmote.HAPPY,
            PochiEmote.WINK_LEFT,
            PochiEmote.THINKING,
            PochiEmote.CALM,
            PochiEmote.EXCITED
        ).random()
    }

    fun onPokeReaction(pokeCount: Int): Triple<PochiEmote, BotState, String> {
        val isId = PochiPrefs.langFlow.value == PochiLang.ID
        return when (pokeCount) {
            1 -> Triple(
                PochiEmote.HMM,
                BotState.IDLE,
                if (isId) "Penasaran ya? Ketuk untuk buka pulau atau tahan untuk perintah cepat."
                else "Curious? Tap to open island or hold for quick command."
            )
            2 -> Triple(
                PochiEmote.HAPPY,
                BotState.IDLE,
                if (isId) "Hehe (`^ ^`) siap bantu!"
                else "Hehe (`^ ^`) ready to help!"
            )
            3 -> Triple(
                PochiEmote.SUSPICIOUS,
                BotState.ANNOYED,
                if (isId) "Hmm... kok dicolek terus dari tadi? (`¬ ¬`)"
                else "Hmm... poking me a lot today? (`¬ ¬`)"
            )
            4 -> Triple(
                PochiEmote.ANGRY,
                BotState.ANNOYED,
                if (isId) "Jangan dicolek terus dong! Tahan buat ngelus."
                else "Easy on the pokes! Hold to pet me instead."
            )
            else -> Triple(
                PochiEmote.NO,
                BotState.ANNOYED,
                if (isId) "Pusing dicolek mulu (`X X`)... tahan sebentar buat nenangin."
                else "Dizzy from all the pokes (`X X`)... hold to calm me down."
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
                if (isId) "Nyaman banget dielus (`♥ ♥`)!"
                else "Purr... feeling energized (`♥ ♥`)!"
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
