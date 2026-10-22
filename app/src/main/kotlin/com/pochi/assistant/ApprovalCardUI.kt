package com.pochi.assistant

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PochiApprovalCard(
    request: ApprovalRequest,
    onDeny: () -> Unit
) {
    val context = LocalContext.current
    var remainingSeconds by remember(request.id) { mutableIntStateOf(request.expiresInSeconds) }
    var isTapGuardActive by remember(request.id) { mutableStateOf(true) }
    var showRawCommand by remember(request.id) { mutableStateOf(false) }

    LaunchedEffect(request.id) {
        isTapGuardActive = true
        delay(600)
        isTapGuardActive = false
    }

    LaunchedEffect(request.id) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
        ApprovalManager.confirmDeny("Kedaluwarsa (120 dtk habis)")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1510))
            .border(1.dp, Color(0xFFFF9F0A).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⚡ Pochi Butuh Persetujuan",
                color = Color(0xFFFF9F0A),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${remainingSeconds}s",
                color = if (remainingSeconds <= 15) Color(0xFFFF453A) else Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = request.friendlyTitle,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0A0A0C))
                .clickable { showRawCommand = !showRawCommand }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (!showRawCommand) {
                Text(
                    text = "🔒 Ketuk untuk lihat perintah asli",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            } else {
                Text(
                    text = request.rawCommand,
                    color = Color(0xFF30D158),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2C2C2E))
                    .clickable(enabled = !isTapGuardActive) { onDeny() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Deny",
                    color = if (isTapGuardActive) Color.DarkGray else Color(0xFFFF453A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isTapGuardActive) Color(0xFFFF9F0A).copy(alpha = 0.3f)
                        else Color(0xFFFF9F0A)
                    )
                    .clickable(enabled = !isTapGuardActive) {
                        val intent = Intent(context, BiometricGateActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔒 Allow",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
