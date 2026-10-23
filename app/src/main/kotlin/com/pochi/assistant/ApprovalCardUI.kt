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
        delay(500)
        isTapGuardActive = false
    }

    LaunchedEffect(request.id) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
        ApprovalManager.confirmDeny("Waktu habis")
    }

    val cardBg = if (isDark) Color(0xFF1C1C22) else Color(0xFFFFFBEB)
    val borderCol = if (isDark) Color(0xFF3F3F46) else Color(0xFFFDE68A)
    val titleCol = if (isDark) Color(0xFFF9FAFB) else Color(0xFF111827)
    val subCol = if (isDark) Color(0xFFA1A1AA) else Color(0xFF6B7280)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PochiMascot(state = BotState.APPROVAL, emoteOverride = PochiEmote.SURPRISED, size = 34.dp)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Konfirmasi Tindakan",
                        color = Color(0xFFD97706),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${remainingSeconds}d",
                        color = subCol,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = request.friendlyTitle,
                    color = titleCol,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (!request.rawCommand.startsWith("pochi-agent ")) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF18181B))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = request.rawCommand,
                    color = Color(0xFFE5E7EB),
                    fontSize = 10.sp,
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF27272A) else Color(0xFFE5E7EB))
                    .clickable(enabled = !isTapGuardActive) { onDeny() }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Batal",
                    color = if (isTapGuardActive) Color.Gray else titleCol,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isTapGuardActive) Color(0xFFF59E0B).copy(alpha = 0.4f)
                        else Color(0xFFF59E0B)
                    )
                    .clickable(enabled = !isTapGuardActive) {
                        val intent = Intent(context, BiometricGateActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lanjutkan",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
