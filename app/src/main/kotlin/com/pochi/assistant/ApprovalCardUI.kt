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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun PochiApprovalCard(
    request: ApprovalRequest,
    isDark: Boolean = true,
    onDeny: () -> Unit
) {
    val context = LocalContext.current
    val lang by PochiPrefs.langFlow.collectAsState()
    val isId = lang == PochiLang.ID
    var remainingSeconds by remember(request.id) { mutableIntStateOf(request.expiresInSeconds) }

    LaunchedEffect(request.id) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
        ApprovalManager.confirmDeny("Timed out")
    }

    val cardBg = Color(0xFF141418)
    val borderCol = Color(0xFFF59E0B).copy(alpha = 0.55f)
    val titleCol = Color(0xFFF9FAFB)
    val subCol = Color(0xFFA1A1AA)

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
            PochiMascot(
                state = BotState.APPROVAL,
                emoteOverride = PochiEmote.SURPRISED,
                size = 34.dp,
                showBlackBacking = true
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isId) "${request.agentName} • Izin Perintah" else "${request.agentName} • Needs Approval",
                        color = Color(0xFFF59E0B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${remainingSeconds}s",
                        color = subCol,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = request.friendlyTitle,
                    color = titleCol,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (request.rawCommand.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0A0A0A))
                    .border(1.dp, Color(0xFF27272A), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "$ ${request.rawCommand}",
                    color = Color(0xFF38BDF8),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Reject (✕) button - comfortable touch target
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.18f))
                    .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable { onDeny() }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isId) "✕ Tolak" else "✕ Reject",
                    color = Color(0xFFF87171),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Approve (✓) button - comfortable touch target
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF10B981))
                    .clickable {
                        if (request.rawCommand.startsWith("pochi-agent ")) {
                            val intent = Intent(context, BiometricGateActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } else {
                            ApprovalManager.confirmAllow()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isId) "✓ Izinkan" else "✓ Approve",
                    color = Color(0xFF052E16),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
