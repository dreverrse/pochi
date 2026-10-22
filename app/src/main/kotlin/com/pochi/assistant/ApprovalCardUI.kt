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
    isDark: Boolean = false,
    onDeny: () -> Unit
) {
    val context = LocalContext.current
    var remainingSeconds by remember(request.id) { mutableIntStateOf(request.expiresInSeconds) }
    var isTapGuardActive by remember(request.id) { mutableStateOf(true) }

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

    val cardBg = if (isDark) Color(0xFF18181B) else Color.White
    val borderCol = if (isDark) Color(0xFF27272A) else Color(0xFFE5E7EB)
    val titleCol = if (isDark) Color.White else Color(0xFF111827)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderCol, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PochiMascot(state = BotState.APPROVAL, emoteOverride = PochiEmote.SURPRISED, size = 38.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pochi Butuh Persetujuan",
                        color = Color(0xFFD97706),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${remainingSeconds}s",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = request.friendlyTitle,
                    color = titleCol,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF18181B))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = request.rawCommand,
                color = Color(0xFFE5E7EB),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF27272A) else Color(0xFFE5E7EB))
                    .clickable(enabled = !isTapGuardActive) { onDeny() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Deny",
                    color = if (isTapGuardActive) Color.Gray else titleCol,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isTapGuardActive) Color(0xFFF59E0B).copy(alpha = 0.4f)
                        else Color(0xFFFBBF24)
                    )
                    .clickable(enabled = !isTapGuardActive) {
                        val intent = Intent(context, BiometricGateActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔒 Allow (Biometrik)",
                    color = Color(0xFF111827),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
