package com.pochi.assistant

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PochiDataCard — kartu data mewah ala referensi pemilik (Tahap 8):
 * kaca biru laut dalam dengan cahaya tepi elektrik, kurva mulus
 * bercahaya dengan titik puncak menyala, dan angka pahlawan besar.
 *
 * Dipakai untuk grafik hidrasi di island dan untuk menampilkan data
 * pada balon chat Pochi (lihat ChatBubble di MainActivity).
 */
object PochiDataStyle {
    val DeepTop = Color(0xFF0A1B3D)
    val DeepMid = Color(0xFF0D2A5F)
    val DeepBottom = Color(0xFF12408C)
    val LineBlue = Color(0xFF7FB2FF)
    val GlowBlue = Color(0xFF2E6BFF)
    val TextDim = Color(0xFFB9C8E8)
    val TextFaint = Color(0xFF8FA8D8)
    val ChipSurface = Color(0x1FFFFFFF)
}

@Composable
fun PochiDataCard(
    title: String,
    chipText: String,
    heroLabel: String,
    heroValue: String,
    footer: String,
    points: List<Float>,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(26.dp)

    // Animasi gambar kurva: garis "ditulis" dari kiri ke kanan.
    var revealTarget by remember { mutableStateOf(0f) }
    LaunchedEffect(points) {
        revealTarget = 0f
        revealTarget = 1f
    }
    val reveal by animateFloatAsState(
        targetValue = revealTarget,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow),
        label = "chartReveal"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        PochiDataStyle.DeepTop,
                        PochiDataStyle.DeepMid,
                        PochiDataStyle.DeepBottom
                    )
                ),
                shape
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF3D6BDB).copy(alpha = 0.85f),
                        Color(0xFF3D6BDB).copy(alpha = 0.12f)
                    )
                ),
                shape
            )
    ) {
        // Cahaya elektrik di tepi bawah kartu (seperti referensi)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            PochiDataStyle.GlowBlue.copy(alpha = 0.38f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = PochiDataStyle.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PochiDataStyle.ChipSurface)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = chipText,
                        color = PochiDataStyle.TextDim,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // --- Kurva mulus bercahaya ---
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                val w = size.width
                val h = size.height
                val topPad = 8.dp.toPx()
                val bottomPad = 4.dp.toPx()

                // Grid samar
                val gridColor = Color.White.copy(alpha = 0.055f)
                for (i in 1..3) {
                    val y = h * i / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                for (i in 1..5) {
                    val x = w * i / 6f
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                }

                val series = if (points.size >= 2) points else listOf(0f, 0f)
                val minV = series.minOrNull() ?: 0f
                val maxV = series.maxOrNull() ?: 1f
                val range = (maxV - minV).takeIf { it > 0.0001f } ?: 1f
                val pts = series.mapIndexed { i, v ->
                    val x = w * i / (series.size - 1).coerceAtLeast(1)
                    val norm = (v - minV) / range
                    val y = topPad + (1f - norm) * (h - topPad - bottomPad)
                    Offset(x, y)
                }

                // Jalur mulus: kurva kubik melewati titik tengah antar titik
                val path = Path()
                path.moveTo(pts.first().x, pts.first().y)
                for (i in 0 until pts.size - 1) {
                    val p0 = pts[i]
                    val p1 = pts[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }

                val measure = PathMeasure()
                measure.setPath(path, false)
                val length = measure.length
                val segment = Path()
                measure.getSegment(0f, (length * reveal).coerceAtLeast(0.01f), segment, true)

                // Isi gradasi di bawah kurva (setelah garis hampir penuh)
                if (reveal > 0.92f) {
                    val fill = Path()
                    fill.addPath(path)
                    fill.lineTo(w, h)
                    fill.lineTo(0f, h)
                    fill.close()
                    drawPath(
                        path = fill,
                        brush = Brush.verticalGradient(
                            listOf(
                                PochiDataStyle.LineBlue.copy(alpha = 0.30f),
                                Color.Transparent
                            )
                        )
                    )
                }

                // Garis utama
                drawPath(
                    path = segment,
                    color = PochiDataStyle.LineBlue,
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Titik ujung menyala (mengikuti ujung gambar)
                val head = measure.getPosition((length * reveal).coerceAtLeast(0.01f))
                drawCircle(
                    color = PochiDataStyle.LineBlue.copy(alpha = 0.30f),
                    radius = 9.dp.toPx(),
                    center = head
                )
                drawCircle(
                    color = PochiDataStyle.LineBlue.copy(alpha = 0.55f),
                    radius = 5.5.dp.toPx(),
                    center = head
                )
                drawCircle(
                    color = Color(0xFFEAF3FF),
                    radius = 3.dp.toPx(),
                    center = head
                )

                // Titik puncak tertinggi ikut menyala bila sudah tergambar
                val peakIdx = series.indices.maxByOrNull { series[it] } ?: 0
                val peakPt = pts[peakIdx]
                if (reveal > (peakIdx.toFloat() / (series.size - 1).coerceAtLeast(1)) + 0.05f) {
                    drawCircle(
                        color = PochiDataStyle.LineBlue.copy(alpha = 0.30f),
                        radius = 8.dp.toPx(),
                        center = peakPt
                    )
                    drawCircle(
                        color = Color(0xFFEAF3FF),
                        radius = 2.6.dp.toPx(),
                        center = peakPt
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = heroLabel,
                    color = PochiDataStyle.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = heroValue,
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = footer,
                    color = PochiDataStyle.TextFaint,
                    fontSize = 9.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
