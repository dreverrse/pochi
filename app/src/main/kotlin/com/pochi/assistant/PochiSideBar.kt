package com.pochi.assistant

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PochiSideBar — side bar tepi layar redesign (Tahap 3a).
 *
 * Menggantikan maskot mungil mode peek/hidden island dengan pill hitam
 * vertikal yang menempel di tepi layar, mengikuti referensi pemilik:
 * wajah Pochi di atas, tiga ring progres melingkar (Tugas, Fokus,
 * Baterai) dengan ikon di tengah ring dan persen di bawahnya, lalu
 * ikon roda gigi di dasar pill. Ketukan di mana pun pada pill tetap
 * memulihkan island (perilaku mode hidden yang sudah ada).
 */

/** Baca level baterai 0..1 dari sticky intent sistem; -1 bila tak terbaca. */
@Composable
fun rememberBatteryFraction(): Float {
    val context = LocalContext.current
    return remember {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) level.toFloat() / scale.toFloat() else -1f
    }
}

@Composable
private fun SideBarRing(
    icon: ImageVector,
    fraction: Float,
    accent: Color
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(40.dp)) {
                val sw = 3.5.dp.toPx()
                val inset = sw / 2f
                val arcSize = Size(size.width - sw, size.height - sw)
                drawArc(
                    color = PochiDesign.Hairline,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = sw, cap = StrokeCap.Round)
                )
                if (clamped > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * clamped,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = "${(clamped * 100).toInt()}%",
            color = PochiDesign.TextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PochiSideBar(
    side: String,
    tasksFraction: Float,
    focusFraction: Float,
    modifier: Modifier = Modifier
) {
    val battery = rememberBatteryFraction()
    val onRight = side != "left"
    // Pill memeluk tepi layar: sudut dalam besar, sudut luar kecil.
    val shape = if (onRight) {
        RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 10.dp, bottomEnd = 10.dp)
    } else {
        RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 26.dp, bottomEnd = 26.dp)
    }

    Column(
        modifier = modifier
            .width(58.dp)
            .fillMaxHeight()
            .clip(shape)
            .background(PochiDesign.IslandBlack)
            .border(1.dp, PochiDesign.Hairline, shape)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        PochiMascot(
            state = BotState.IDLE,
            size = 26.dp,
            showBlackBacking = false
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SideBarRing(
                icon = Icons.Rounded.CheckCircle,
                fraction = tasksFraction,
                accent = PochiDesign.StatusGreen
            )
            SideBarRing(
                icon = Icons.Rounded.Timer,
                fraction = focusFraction,
                accent = PochiDesign.StatusAmber
            )
            if (battery >= 0f) {
                SideBarRing(
                    icon = Icons.Rounded.BatteryFull,
                    fraction = battery,
                    accent = PochiDesign.AccentViolet
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = PochiDesign.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}
