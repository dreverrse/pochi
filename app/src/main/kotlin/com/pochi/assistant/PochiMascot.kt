package com.pochi.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class BotState(val accentColor: Color, val statusLabel: String) {
    IDLE(Color(0xFF8E8E93), "Pochi siap"),
    WAITING(Color(0xFFFF9F0A), "Menunggu perintah..."),
    WORKING(Color(0xFF0A84FF), "Pochi memproses..."),
    DONE(Color(0xFF30D158), "Berhasil!"),
    ANNOYED(Color(0xFFFF453A), "Jangan dicolek terus!")
}

@Composable
fun PochiMascot(
    state: BotState,
    size: Dp = 36.dp,
    onPoke: () -> Unit = {},
    onPet: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val jumpOffset by animateFloatAsState(
        targetValue = if (state == BotState.DONE) -5f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "jump"
    )

    val bodyColor by animateColorAsState(
        targetValue = when (state) {
            BotState.IDLE -> Color(0xFFF2F2F7)
            BotState.WAITING -> Color(0xFFFF9F0A)
            BotState.WORKING -> Color(0xFF0A84FF)
            BotState.DONE -> Color(0xFF30D158)
            BotState.ANNOYED -> Color(0xFFFF453A)
        },
        animationSpec = tween(250),
        label = "bodyColor"
    )

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((2500..4500).random().toLong())
            isBlinking = true
            delay(130)
            isBlinking = false
        }
    }

    Canvas(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = breathScale
                scaleY = 1f / breathScale
                translationY = jumpOffset
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onPoke() },
                    onLongPress = { onPet() }
                )
            }
    ) {
        val w = this.size.width
        val h = this.size.height

        drawRoundRect(
            color = bodyColor,
            size = Size(w, h),
            cornerRadius = CornerRadius(w * 0.36f, h * 0.36f)
        )

        val eyeColor = Color(0xFF1C1C1E)
        val leftEyeX = w * 0.33f
        val rightEyeX = w * 0.67f
        val eyeY = if (state == BotState.WORKING) h * 0.38f else h * 0.45f

        when {
            state == BotState.ANNOYED -> {
                val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(leftEyeX - 6f, eyeY - 5f)
                    lineTo(leftEyeX + 4f, eyeY)
                    lineTo(leftEyeX - 6f, eyeY + 5f)

                    moveTo(rightEyeX + 6f, eyeY - 5f)
                    lineTo(rightEyeX - 4f, eyeY)
                    lineTo(rightEyeX + 6f, eyeY + 5f)
                }
                drawPath(path, color = eyeColor, style = stroke)
            }
            state == BotState.DONE -> {
                val stroke = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
                drawArc(
                    color = eyeColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(leftEyeX - 6f, eyeY - 4f),
                    size = Size(12f, 10f),
                    style = stroke
                )
                drawArc(
                    color = eyeColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(rightEyeX - 6f, eyeY - 4f),
                    size = Size(12f, 10f),
                    style = stroke
                )
            }
            isBlinking -> {
                drawLine(
                    color = eyeColor,
                    start = Offset(leftEyeX - 5f, eyeY),
                    end = Offset(leftEyeX + 5f, eyeY),
                    strokeWidth = w * 0.06f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = eyeColor,
                    start = Offset(rightEyeX - 5f, eyeY),
                    end = Offset(rightEyeX + 5f, eyeY),
                    strokeWidth = w * 0.06f,
                    cap = StrokeCap.Round
                )
            }
            else -> {
                val eyeRadius = w * 0.085f
                drawCircle(color = eyeColor, radius = eyeRadius, center = Offset(leftEyeX, eyeY))
                drawCircle(color = eyeColor, radius = eyeRadius, center = Offset(rightEyeX, eyeY))

                drawCircle(
                    color = Color.White,
                    radius = eyeRadius * 0.35f,
                    center = Offset(leftEyeX + 2f, eyeY - 2f)
                )
                drawCircle(
                    color = Color.White,
                    radius = eyeRadius * 0.35f,
                    center = Offset(rightEyeX + 2f, eyeY - 2f)
                )
            }
        }
    }
}
