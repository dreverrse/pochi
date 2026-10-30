package com.pochi.assistant

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PochiWidgets — bahasa papan widget hitam (Tahap 9, rombak total
 * mengikuti referensi pemilik): kartu hitam pekat bertepi garis
 * rambut, label mini abu-abu, nilai putih tegas, dan satu aksen
 * warna per widget (sparkline/ring). Dipakai di panel island dan
 * dashboard Home agar seluruh aplikasi terasa satu bahasa.
 */

/** Kartu widget dasar: permukaan hitam, tepi garis rambut, radius 16. */
@Composable
fun PochiWidget(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 10.dp,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    var m = modifier
        .clip(shape)
        .background(PochiDesign.WidgetSurface, shape)
        .border(1.dp, PochiDesign.WidgetBorder, shape)
    if (onClick != null) {
        m = m.clickable { onClick() }
    }
    Box(modifier = m.padding(contentPadding)) {
        content()
    }
}

/** Label mini khas papan: ikon kecil opsional + teks 9sp abu-abu. */
@Composable
fun WidgetLabel(
    text: String,
    icon: ImageVector? = null,
    tint: Color = PochiDesign.WidgetLabel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = text,
            color = tint,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        )
    }
}

/** Nilai utama widget: putih, tegas, ukuran bisa diatur. */
@Composable
fun WidgetValue(
    text: String,
    size: Int = 19,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = PochiDesign.TextPrimary,
        fontSize = size.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

/** Catatan kaki widget: 9sp abu-abu redup. */
@Composable
fun WidgetFootnote(
    text: String,
    color: Color = PochiDesign.WidgetLabel,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        modifier = modifier
    )
}

/**
 * Sparkline mulus khas papan widget: kurva Bézier tipis dengan isi
 * gradasi samar dan titik ujung kecil. Nilai dinormalisasi internal.
 */
@Composable
fun PochiSparkline(
    points: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = true
) {
    Canvas(modifier = modifier) {
        val series = if (points.size >= 2) points else listOf(0f, 0f)
        val minV = series.minOrNull() ?: 0f
        val maxV = series.maxOrNull() ?: 1f
        val range = (maxV - minV).takeIf { it > 0.0001f } ?: 1f
        val pad = 2.dp.toPx()
        val pts = series.mapIndexed { i, v ->
            val x = size.width * i / (series.size - 1).coerceAtLeast(1)
            val norm = (v - minV) / range
            val y = pad + (1f - norm) * (size.height - pad * 2)
            Offset(x, y)
        }
        val path = Path()
        path.moveTo(pts.first().x, pts.first().y)
        for (i in 0 until pts.size - 1) {
            val p0 = pts[i]
            val p1 = pts[i + 1]
            val midX = (p0.x + p1.x) / 2f
            path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
        if (filled) {
            val fillPath = Path()
            fillPath.addPath(path)
            fillPath.lineTo(size.width, size.height)
            fillPath.lineTo(0f, size.height)
            fillPath.close()
            drawPath(
                path = fillPath,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(color.copy(alpha = 0.28f), Color.Transparent)
                )
            )
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        drawCircle(color = color, radius = 2.dp.toPx(), center = pts.last())
    }
}

/** Ring mini tipis dengan teks tengah — versi papan dari ring besar. */
@Composable
fun WidgetRing(
    fraction: Float,
    color: Color,
    centerText: String,
    modifier: Modifier = Modifier,
    ringSize: Dp = 36.dp
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "widgetRing"
    )
    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = 3.dp.toPx()
            val inset = sw / 2f
            val arcSize = Size(size.width - sw, size.height - sw)
            drawArc(
                color = PochiDesign.WidgetBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round)
            )
            if (animated > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = centerText,
            color = PochiDesign.TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Susunan label + nilai + catatan dalam satu kolom widget. */
@Composable
fun WidgetTextStack(
    label: String,
    value: String,
    footnote: String,
    icon: ImageVector? = null,
    valueSize: Int = 19,
    footnoteColor: Color = PochiDesign.WidgetLabel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        WidgetLabel(text = label, icon = icon)
        WidgetValue(text = value, size = valueSize)
        WidgetFootnote(text = footnote, color = footnoteColor)
    }
}
