package com.pochi.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PochiIslandBot(
    onExpandChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var botState by remember { mutableStateOf(BotState.IDLE) }
    var selectedAgent by remember { mutableStateOf("Pochi Flash") }
    var prompt by remember { mutableStateOf("") }
    var botReply by remember { mutableStateOf("Halo! Aku Pochi. Ketuk aku atau ketik sesuatu di bawah.") }
    var pokeCount by remember { mutableIntStateOf(0) }

    val activeApproval by ApprovalManager.activeRequest.collectAsState()
    val lastDecision by ApprovalManager.lastDecision.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val agents = listOf("Pochi Flash", "Pochi Pro", "Action Guard")

    LaunchedEffect(activeApproval) {
        if (activeApproval != null) {
            isExpanded = true
            botState = BotState.WAITING
            onExpandChange(true)
        }
    }

    LaunchedEffect(lastDecision) {
        lastDecision?.let { decision ->
            botReply = decision
            botState = if (decision.startsWith("Diizinkan")) BotState.DONE else BotState.IDLE
        }
    }

    val borderColor by animateColorAsState(
        targetValue = if (botState == BotState.IDLE && !isExpanded) {
            Color(0xFF2C2C2E)
        } else {
            botState.accentColor.copy(alpha = 0.65f)
        },
        label = "borderGlow"
    )

    val cornerRadius = if (isExpanded) 26.dp else 50.dp

    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF0A0A0C))
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .clickable(enabled = !isExpanded) {
                isExpanded = true
                botState = BotState.WAITING
                onExpandChange(true)
            }
            .padding(
                horizontal = if (isExpanded) 14.dp else 12.dp,
                vertical = if (isExpanded) 12.dp else 6.dp
            )
    ) {
        if (!isExpanded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(26.dp)
            ) {
                PochiMascot(state = botState, size = 22.dp)

                Text(
                    text = if (botState == BotState.IDLE) "Pochi" else botState.statusLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 130.dp)
                )

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(botState.accentColor)
                )
            }
        } else {
            Column(
                modifier = Modifier.width(330.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        agents.forEach { agent ->
                            val isSelected = agent == selectedAgent
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isSelected) botState.accentColor.copy(alpha = 0.2f)
                                        else Color(0xFF1C1C1E)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) botState.accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { selectedAgent = agent }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = agent,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Text(
                        text = "✕",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable {
                                isExpanded = false
                                botState = BotState.IDLE
                                onExpandChange(false)
                            }
                            .padding(4.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF141417))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PochiMascot(
                        state = botState,
                        size = 46.dp,
                        onPoke = {
                            pokeCount++
                            if (pokeCount >= 3) {
                                botState = BotState.ANNOYED
                                botReply = "Aduh! Pochi pusing dicolek terus >_<"
                                coroutineScope.launch {
                                    delay(2500)
                                    pokeCount = 0
                                    botState = BotState.IDLE
                                }
                            } else {
                                botReply = "Hei! Pochi di sini, ada yang bisa dibantu?"
                            }
                        },
                        onPet = {
                            botState = BotState.DONE
                            botReply = "Hehe, makasih sudah elus Pochi! ^ ^"
                        }
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = botState.statusLabel,
                            color = botState.accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = botReply,
                            color = Color(0xFFE5E5EA),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.heightIn(max = 110.dp)
                        )
                    }
                }

                activeApproval?.let { request ->
                    PochiApprovalCard(
                        request = request,
                        onDeny = { ApprovalManager.confirmDeny() }
                    )
                }

                if (botState == BotState.WORKING) {
                    NineDotsWaveform(color = botState.accentColor)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1C1C1E))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BasicTextField(
                        value = prompt,
                        onValueChange = {
                            prompt = it
                            if (botState != BotState.WORKING) botState = BotState.WAITING
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(botState.accentColor),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (prompt.isEmpty()) {
                                Text(
                                    text = "Tanya $selectedAgent...",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(botState.accentColor)
                            .clickable {
                                if (prompt.isNotBlank() && botState != BotState.WORKING) {
                                    val query = prompt
                                    prompt = ""
                                    val apiKey = PochiPrefs.getApiKey(context)
                                    if (apiKey.isBlank()) {
                                        botReply = "Isi Gemini API Key dulu di aplikasi utama Pochi ya!"
                                        botState = BotState.ANNOYED
                                        return@clickable
                                    }

                                    botState = BotState.WORKING
                                    coroutineScope.launch {
                                        try {
                                            val modelName = if (selectedAgent == "Pochi Pro") "gemini-2.5-pro" else "gemini-2.5-flash"
                                            val generativeModel = GenerativeModel(
                                                modelName = modelName,
                                                apiKey = apiKey
                                            )
                                            val response = generativeModel.generateContent(query)
                                            botReply = response.text ?: "Tidak ada jawaban."
                                            botState = BotState.DONE
                                            delay(4000)
                                            if (botState == BotState.DONE) botState = BotState.IDLE
                                        } catch (e: Exception) {
                                            botReply = "Gagal memproses: ${e.localizedMessage}"
                                            botState = BotState.ANNOYED
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("↑", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun NineDotsWaveform(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(9) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 60, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(5.dp * scale)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
