package com.pochi.assistant

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
    IDLE(Color(0xFF0A84FF), "Pochi siap ✨", "Pochi", PochiEmote.SMILING),
    WAITING(Color(0xFFFF9F0A), "Menunggu perintah...", "Menunggu perintah...", PochiEmote.CALM),
    WORKING(Color(0xFF0A84FF), "Memproses...", "Memproses...", PochiEmote.WORKING),
    DONE(Color(0xFF30D158), "Berhasil!", "Berhasil!", PochiEmote.SUCCESS),
    APPROVAL(Color(0xFFFF9F0A), "Perlu persetujuan", "Perlu persetujuan", PochiEmote.SURPRISED),
    ANNOYED(Color(0xFFFF453A), "Jangan dicolek terus!", "Jangan dicolek terus!", PochiEmote.ANGRY)
}

@Composable
fun PochiMascot(
    state: BotState,
    emoteOverride: PochiEmote? = null,
    size: Dp = 44.dp,
    onPoke: () -> Unit = {},
    onPet: () -> Unit = {}
) {
    val activeEmote = emoteOverride ?: state.defaultEmote

    val infiniteTransition = rememberInfiniteTransition(label = "pochiBreath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((2800..4500).random().toLong())
            isBlinking = true
            delay(130)
            isBlinking = false
        }
    }

    Canvas(
        modifier = Modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onPoke() },
                    onLongPress = { onPet() }
                )
            }
    ) {
        val w = this.size.width
        val h = this.size.height
        val lineColor = Color(0xFF111827)
        val strokeW = (w * 0.055f).coerceAtLeast(2.2f)

        scale(scaleX = breathScale, scaleY = 1f / breathScale) {
            val catPath = Path().apply {
                moveTo(w * 0.36f, h * 0.28f)
                lineTo(w * 0.20f, h * 0.12f)
                quadraticBezierTo(w * 0.16f, h * 0.10f, w * 0.16f, h * 0.17f)
                lineTo(w * 0.14f, h * 0.44f)
                cubicTo(
                    w * 0.10f, h * 0.74f,
                    w * 0.22f, h * 0.88f,
                    w * 0.50f, h * 0.88f
                )
                cubicTo(
                    w * 0.78f, h * 0.88f,
                    w * 0.90f, h * 0.74f,
                    w * 0.86f, h * 0.44f
                )
                lineTo(w * 0.84f, h * 0.17f)
                quadraticBezierTo(w * 0.84f, h * 0.10f, w * 0.80f, h * 0.12f)
                lineTo(w * 0.64f, h * 0.28f)
                quadraticBezierTo(w * 0.50f, h * 0.26f, w * 0.36f, h * 0.28f)
                close()
            }

            drawPath(path = catPath, color = Color.White, style = Fill)
            drawPath(
                path = catPath,
                color = lineColor,
                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val leftX = w * 0.35f
            val rightX = w * 0.65f
            val eyeY = h * 0.54f

            if (isBlinking && activeEmote == PochiEmote.NORMAL) {
                drawClosedLineEye(leftX, eyeY, w, lineColor, strokeW)
                drawClosedLineEye(rightX, eyeY, w, lineColor, strokeW)
            } else {
                drawPochiExpression(activeEmote, leftX, rightX, eyeY, w, h, lineColor, strokeW)
            }
        }
    }
}

private fun DrawScope.drawOvalEye(cx: Float, cy: Float, w: Float, color: Color, scale: Float = 1f) {
    val rx = w * 0.055f * scale
    val ry = w * 0.082f * scale
    drawOval(
        color = color,
        topLeft = Offset(cx - rx, cy - ry),
        size = Size(rx * 2, ry * 2)
    )
}

private fun DrawScope.drawHappyArcEye(cx: Float, cy: Float, w: Float, color: Color, strokeW: Float) {
    val r = w * 0.065f
    drawArc(
        color = color,
        startAngle = 190f,
        sweepAngle = 160f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r * 0.6f),
        size = Size(r * 2, r * 1.5f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawSleepyArcEye(cx: Float, cy: Float, w: Float, color: Color, strokeW: Float) {
    val r = w * 0.065f
    drawArc(
        color = color,
        startAngle = 10f,
        sweepAngle = 160f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r * 0.8f),
        size = Size(r * 2, r * 1.4f),
        style = Stroke(width = strokeW, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawClosedLineEye(cx: Float, cy: Float, w: Float, color: Color, strokeW: Float) {
    val r = w * 0.065f
    drawLine(
        color = color,
        start = Offset(cx - r, cy),
        end = Offset(cx + r, cy),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawChevronEye(cx: Float, cy: Float, w: Float, isLeft: Boolean, color: Color, strokeW: Float) {
    val r = w * 0.06f
    val path = Path().apply {
        if (isLeft) {
            moveTo(cx - r, cy - r * 0.7f)
            lineTo(cx + r * 0.8f, cy)
            lineTo(cx - r, cy + r * 0.7f)
        } else {
            moveTo(cx + r, cy - r * 0.7f)
            lineTo(cx - r * 0.8f, cy)
            lineTo(cx + r, cy + r * 0.7f)
        }
    }
    drawPath(path, color = color, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawSmallSmile(cx: Float, cy: Float, w: Float, color: Color, strokeW: Float) {
    val r = w * 0.045f
    drawArc(
        color = color,
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(cx - r, cy - r * 0.5f),
        size = Size(r * 2, r * 1.2f),
        style = Stroke(width = strokeW * 0.85f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawBlushCheeks(leftX: Float, rightX: Float, eyeY: Float, w: Float) {
    val blushColor = Color(0xFFFF8A8A).copy(alpha = 0.55f)
    val rx = w * 0.065f
    val ry = w * 0.032f
    drawOval(blushColor, topLeft = Offset(leftX - rx * 1.4f, eyeY + w * 0.08f), size = Size(rx * 2, ry * 2))
    drawOval(blushColor, topLeft = Offset(rightX - rx * 0.6f, eyeY + w * 0.08f), size = Size(rx * 2, ry * 2))
}

private fun DrawScope.drawThreeTopDots(w: Float, h: Float, color: Color) {
    val dotR = w * 0.025f
    drawCircle(color, radius = dotR, center = Offset(w * 0.43f, h * 0.17f))
    drawCircle(color, radius = dotR, center = Offset(w * 0.50f, h * 0.17f))
    drawCircle(color, radius = dotR, center = Offset(w * 0.57f, h * 0.17f))
}

private fun DrawScope.drawPochiExpression(
    emote: PochiEmote,
    leftX: Float,
    rightX: Float,
    eyeY: Float,
    w: Float,
    h: Float,
    lineColor: Color,
    strokeW: Float
) {
    val mouthY = h * 0.64f
    when (emote) {
        PochiEmote.NORMAL, PochiEmote.HI -> {
            drawOvalEye(leftX, eyeY, w, lineColor)
            drawOvalEye(rightX, eyeY, w, lineColor)
            if (emote == PochiEmote.HI) drawSmallSmile(w * 0.5f, mouthY, w, lineColor, strokeW)
        }
        PochiEmote.HAPPY, PochiEmote.SMILING, PochiEmote.CUTE, PochiEmote.BYE, PochiEmote.GOOD_MORNING, PochiEmote.SUCCESS -> {
            drawHappyArcEye(leftX, eyeY, w, lineColor, strokeW)
            drawHappyArcEye(rightX, eyeY, w, lineColor, strokeW)
            drawSmallSmile(w * 0.5f, mouthY, w, lineColor, strokeW)
            if (emote == PochiEmote.CUTE) drawBlushCheeks(leftX, rightX, eyeY, w)
            if (emote == PochiEmote.SUCCESS) {
                drawCircle(Color(0xFF30D158), radius = w * 0.07f, center = Offset(w * 0.86f, h * 0.24f))
            }
            if (emote == PochiEmote.GOOD_MORNING) {
                drawCircle(Color(0xFFFF9F0A), radius = w * 0.08f, center = Offset(w * 0.85f, h * 0.22f))
            }
        }
        PochiEmote.LAUGHING -> {
            drawChevronEye(leftX, eyeY, w, true, lineColor, strokeW)
            drawChevronEye(rightX, eyeY, w, false, lineColor, strokeW)
            drawArc(
                color = Color(0xFFFF6B6B),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(w * 0.43f, mouthY - w * 0.02f),
                size = Size(w * 0.14f, w * 0.11f)
            )
        }
        PochiEmote.WINK_LEFT, PochiEmote.OK -> {
            drawOvalEye(leftX, eyeY, w, lineColor)
            drawChevronEye(rightX, eyeY, w, false, lineColor, strokeW)
            drawSmallSmile(w * 0.5f, mouthY, w, lineColor, strokeW)
        }
        PochiEmote.EXCITED -> {
            drawOvalEye(leftX, eyeY, w, lineColor, 1.15f)
            drawOvalEye(rightX, eyeY, w, lineColor, 1.15f)
            drawBlushCheeks(leftX, rightX, eyeY, w)
            drawSmallSmile(w * 0.5f, mouthY, w, lineColor, strokeW)
        }
        PochiEmote.LOVE -> {
            val heartColor = Color(0xFFFF453A)
            drawCircle(heartColor, radius = w * 0.075f, center = Offset(leftX, eyeY))
            drawCircle(heartColor, radius = w * 0.075f, center = Offset(rightX, eyeY))
            drawSmallSmile(w * 0.5f, mouthY, w, lineColor, strokeW)
        }
        PochiEmote.BLUSH, PochiEmote.EMBARRASSED -> {
            drawSleepyArcEye(leftX, eyeY, w, lineColor, strokeW)
            drawSleepyArcEye(rightX, eyeY, w, lineColor, strokeW)
            drawBlushCheeks(leftX, rightX, eyeY, w)
        }
        PochiEmote.SURPRISED, PochiEmote.SHOCK, PochiEmote.SCARED -> {
            drawOvalEye(leftX, eyeY, w, lineColor, 0.9f)
            drawOvalEye(rightX, eyeY, w, lineColor, 0.9f)
            drawCircle(lineColor, radius = w * 0.032f, center = Offset(w * 0.5f, mouthY + w * 0.02f))
            if (emote == PochiEmote.SURPRISED) {
                drawCircle(Color(0xFFFF453A), radius = w * 0.04f, center = Offset(w * 0.88f, h * 0.22f))
            }
        }
        PochiEmote.CONFUSED, PochiEmote.THINKING, PochiEmote.HMM -> {
            drawHappyArcEye(leftX, eyeY, w, lineColor, strokeW)
            drawOvalEye(rightX, eyeY, w, lineColor)
            drawClosedLineEye(w * 0.5f, mouthY + w * 0.02f, w * 0.5f, lineColor, strokeW * 0.8f)
            if (emote == PochiEmote.HMM) drawThreeTopDots(w, h, lineColor)
        }
        PochiEmote.SUSPICIOUS, PochiEmote.ANNOYED, PochiEmote.TIRED -> {
            drawClosedLineEye(leftX, eyeY, w, lineColor, strokeW)
            drawClosedLineEye(rightX, eyeY, w, lineColor, strokeW)
            drawClosedLineEye(w * 0.5f, mouthY + w * 0.03f, w * 0.45f, lineColor, strokeW * 0.8f)
        }
        PochiEmote.ANGRY, PochiEmote.FOCUS, PochiEmote.ERROR -> {
            drawLine(lineColor, Offset(leftX - w * 0.06f, eyeY - w * 0.08f), Offset(leftX + w * 0.06f, eyeY - w * 0.03f), strokeW, StrokeCap.Round)
            drawLine(lineColor, Offset(rightX + w * 0.06f, eyeY - w * 0.08f), Offset(rightX - w * 0.06f, eyeY - w * 0.03f), strokeW, StrokeCap.Round)
            drawOvalEye(leftX, eyeY + w * 0.02f, w, lineColor, 0.85f)
            drawOvalEye(rightX, eyeY + w * 0.02f, w, lineColor, 0.85f)
            if (emote == PochiEmote.ANGRY || emote == PochiEmote.ERROR) {
                drawCircle(Color(0xFFFF453A), radius = w * 0.055f, center = Offset(w * 0.84f, h * 0.24f))
            }
        }
        PochiEmote.SLEEPY, PochiEmote.CALM, PochiEmote.GOOD_NIGHT, PochiEmote.PET_POKE -> {
            drawSleepyArcEye(leftX, eyeY, w, lineColor, strokeW)
            drawSleepyArcEye(rightX, eyeY, w, lineColor, strokeW)
            if (emote == PochiEmote.PET_POKE) drawBlushCheeks(leftX, rightX, eyeY, w)
            if (emote == PochiEmote.GOOD_NIGHT) {
                drawCircle(Color(0xFF0A84FF), radius = w * 0.07f, center = Offset(w * 0.84f, h * 0.22f))
            }
        }
        PochiEmote.HAPPY_TEARS, PochiEmote.CRYING -> {
            if (emote == PochiEmote.HAPPY_TEARS) {
                drawHappyArcEye(leftX, eyeY, w, lineColor, strokeW)
                drawHappyArcEye(rightX, eyeY, w, lineColor, strokeW)
            } else {
                drawOvalEye(leftX, eyeY, w, lineColor)
                drawOvalEye(rightX, eyeY, w, lineColor)
            }
            val tearColor = Color(0xFF38BDF8)
            drawCircle(tearColor, radius = w * 0.04f, center = Offset(leftX - w * 0.06f, eyeY + w * 0.09f))
            drawCircle(tearColor, radius = w * 0.04f, center = Offset(rightX + w * 0.06f, eyeY + w * 0.09f))
        }
        PochiEmote.NO -> {
            val r = w * 0.05f
            drawLine(lineColor, Offset(leftX - r, eyeY - r), Offset(leftX + r, eyeY + r), strokeW, StrokeCap.Round)
            drawLine(lineColor, Offset(leftX + r, eyeY - r), Offset(leftX - r, eyeY + r), strokeW, StrokeCap.Round)
            drawLine(lineColor, Offset(rightX - r, eyeY - r), Offset(rightX + r, eyeY + r), strokeW, StrokeCap.Round)
            drawLine(lineColor, Offset(rightX + r, eyeY - r), Offset(rightX - r, eyeY + r), strokeW, StrokeCap.Round)
        }
        PochiEmote.WORKING, PochiEmote.LOADING -> {
            drawOvalEye(leftX, eyeY, w, lineColor)
            drawOvalEye(rightX, eyeY, w, lineColor)
            drawThreeTopDots(w, h, Color(0xFF0A84FF))
        }
    }
}
