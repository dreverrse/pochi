package com.pochi.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.MediaStore
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    var currentTab by remember { mutableStateOf("Chat AI") } // Pilihan: Chat, Tools, Weather, Notes, Ide
    var prompt by remember { mutableStateOf("") }
    var botReply by remember { mutableStateOf("Halo! Ketuk ikon menu di atas untuk akses cepat.") }
    var torchState by remember { mutableStateOf(false) }
    var pokeCount by remember { mutableIntStateOf(0) }

    val activeApproval by ApprovalManager.activeRequest.collectAsState()
    val lastDecision by ApprovalManager.lastDecision.collectAsState()
    val coroutineScope = rememberCoroutineScope()

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

    // Transisi warna border halus
    val borderColor by animateColorAsState(
        targetValue = if (botState == BotState.IDLE && !isExpanded) Color(0xFF2C2C2E) else botState.accentColor.copy(alpha = 0.7f),
        animationSpec = tween(300),
        label = "borderGlow"
    )

    val cornerRadius = if (isExpanded) 28.dp else 50.dp

    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF0A0A0C))
            .border(1.dp, borderColor, RoundedCornerShape(cornerRadius))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy, // Dibuat tanpa pantulan berlebih agar tidak patah-patah
                    stiffness = Spring.StiffnessMedium
                )
            )
            .clickable(enabled = !isExpanded) {
                isExpanded = true
                botState = BotState.WAITING
                onExpandChange(true)
            }
            .padding(horizontal = if (isExpanded) 14.dp else 12.dp, vertical = if (isExpanded) 12.dp else 6.dp)
    ) {
        if (!isExpanded) {
            // TAMPILAN KAPSUL KECIL (COLLAPSED)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(24.dp)
            ) {
                PochiMascot(state = botState, size = 20.dp)
                Text(
                    text = if (botState == BotState.IDLE) "Pochi Assistant" else botState.statusLabel,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(botState.accentColor)
                )
            }
        } else {
            // TAMPILAN EXPANDED (MULTIFUNGSI & CLEAN)
            Column(
                modifier = Modifier.width(330.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header: Tab Navigasi Multifungsi (Bisa digeser horizontal agar bersih)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf("Chat AI", "Tools", "Weather", "Notes", "Ide")
                    tabs.forEach { tab ->
                        val isSelected = tab == currentTab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSelected) Color(0xFF0A84FF).copy(alpha = 0.25f) else Color(0xFF1C1C1E))
                                .border(1.dp, if (isSelected) Color(0xFF0A84FF) else Color.Transparent, RoundedCornerShape(50))
                                .clickable { currentTab = tab }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

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
                            .padding(start = 6.dp)
                    )
                }

                // Area Maskot & Status Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141417))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PochiMascot(
                        state = botState,
                        size = 40.dp,
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
                                botReply = "Mode aktif: $currentTab. Ada yang bisa dibantu?"
                            }
                        },
                        onPet = {
                            botState = BotState.DONE
                            botReply = "Makasih sudah elus Pochi! ^ ^"
                        }
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mode: $currentTab",
                            color = botState.accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = botReply,
                            color = Color(0xFFE5E5EA),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Konten Berdasarkan Tab yang Dipilih (Clean & Bebas Bentrok)
                when (currentTab) {
                    "Chat AI" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF1C1C1E))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                BasicTextField(
                                    value = prompt,
                                    onValueChange = { prompt = it; if (botState != BotState.WORKING) botState = BotState.WAITING },
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(Color(0xFF0A84FF)),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (prompt.isEmpty()) Text("Tanya Gemini...", color = Color.Gray, fontSize = 12.sp)
                                        innerTextField()
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0A84FF))
                                        .clickable {
                                            if (prompt.isNotBlank() && botState != BotState.WORKING) {
                                                val query = prompt
                                                prompt = ""
                                                val apiKey = PochiPrefs.getApiKey(context)
                                                if (apiKey.isBlank()) {
                                                    botReply = "Masukkan Gemini API Key di aplikasi utama!"
                                                    botState = BotState.ANNOYED
                                                    return@clickable
                                                }
                                                botState = BotState.WORKING
                                                coroutineScope.launch {
                                                    try {
                                                        val model = GenerativeModel("gemini-2.5-flash", apiKey)
                                                        val res = model.generateContent(query)
                                                        botReply = res.text ?: "Tidak ada jawaban."
                                                        botState = BotState.DONE
                                                        delay(3000)
                                                        if (botState == BotState.DONE) botState = BotState.IDLE
                                                    } catch (e: Exception) {
                                                        botReply = "Error: ${e.localizedMessage}"
                                                        botState = BotState.ANNOYED
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("↑", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    "Tools" -> {
                        // Fitur Cepat: Senter, Kamera, On/Off Layar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tombol Senter (Flashlight)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (torchState) Color(0xFFFF9F0A) else Color(0xFF1C1C1E))
                                    .clickable {
                                        try {
                                            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                                            val cameraId = camManager.cameraIdList[0]
                                            torchState = !torchState
                                            camManager.setTorchMode(cameraId, torchState)
                                            botReply = if (torchState) "Senter dinyalakan!" else "Senter dimatikan."
                                        } catch (e: Exception) {
                                            botReply = "Gagal mengontrol senter."
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (torchState) "💡 Matikan Senter" else "💡 Nyalakan Senter",
                                    color = if (torchState) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Tombol Buka Kamera Cepat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1C1C1E))
                                    .clickable {
                                        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        botReply = "Membuka kamera..."
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "📷 Buka Kamera", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    "Weather" -> {
                        // Info Cuaca Kilat via AI
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1C1C1E))
                                .clickable {
                                    val apiKey = PochiPrefs.getApiKey(context)
                                    if (apiKey.isBlank()) {
                                        botReply = "Isi API Key dulu di aplikasi utama!"
                                        return@clickable
                                    }
                                    botState = BotState.WORKING
                                    coroutineScope.launch {
                                        try {
                                            val model = GenerativeModel("gemini-2.5-flash", apiKey)
                                            val res = model.generateContent("Berikan perkiraan cuaca umum dan tips pakaian hari ini secara singkat dalam bahasa Indonesia.")
                                            botReply = res.text ?: "Gagal memuat cuaca."
                                            botState = BotState.DONE
                                        } catch (e: Exception) {
                                            botReply = "Gagal terhubung ke AI."
                                            botState = BotState.ANNOYED
                                        }
                                    }
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🌤️ Ketuk untuk Cek Info Cuaca & Saran Hari Ini", color = Color(0xFF0A84FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    "Notes" -> {
                        // Catatan Cepat
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1C1C1E))
                                .clickable {
                                    botReply = "Catatan aktif: Gunakan fitur Chat AI di atas untuk merangkum teks atau ide cepat!"
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "📝 Catatan Singkat: Tersimpan otomatis di sesi aktif", color = Color(0xFF30D158), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    "Ide" -> {
                        // Brainstorming Ide Instan
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1C1C1E))
                                .clickable {
                                    val apiKey = PochiPrefs.getApiKey(context)
                                    if (apiKey.isBlank()) {
                                        botReply = "Isi API Key dulu di aplikasi utama!"
                                        return@clickable
                                    }
                                    botState = BotState.WORKING
                                    coroutineScope.launch {
                                        try {
                                            val model = GenerativeModel("gemini-2.5-flash", apiKey)
                                            val res = model.generateContent("Berikan 1 ide kreatif konten atau inovasi proyek teknologi yang unik dan segar hari ini dalam 2 kalimat.")
                                            botReply = res.text ?: "Tidak ada ide."
                                            botState = BotState.DONE
                                        } catch (e: Exception) {
                                            botReply = "Gagal menghasilkan ide."
                                            botState = BotState.ANNOYED
                                        }
                                    }
                                }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "💡 Ketuk untuk Meminta Ide Kreatif / Brainstorming", color = Color(0xFFFF9F0A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                activeApproval?.let { request ->
                    PochiApprovalCard(
                        request = request,
                        onDeny = { ApprovalManager.confirmDeny() }
                    )
                }
            }
        }
    }
}