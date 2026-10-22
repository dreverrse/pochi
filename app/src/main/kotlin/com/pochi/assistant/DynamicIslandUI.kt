package com.pochi.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class PochiTab(val title: String, val icon: String) {
    CHAT("Chat AI", "💬"),
    TOOLS("Kontrol", "🔧"),
    MUSIC("Musik", "🎵"),
    WEATHER("Cuaca", "☁️"),
    NOTES("Notes", "📄"),
    IDEAS("Ide", "💡")
}

@Composable
fun PochiIslandBot(
    onExpandChange: (Boolean) -> Unit,
    onPowerOffService: () -> Unit = {}
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var botState by remember { mutableStateOf(BotState.IDLE) }
    var customEmote by remember { mutableStateOf<PochiEmote?>(null) }
    var currentTab by remember { mutableStateOf(PochiTab.CHAT) }
    var selectedModel by remember { mutableStateOf("Flash") }

    var prompt by remember { mutableStateOf("") }
    var botReply by remember { mutableStateOf("Halo!
Aku Pochi.
Ada yang bisa kubantu? ✨") }
    var pokeCount by remember { mutableIntStateOf(0) }

    var isTorchOn by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(PochiPrefs.getNotes(context)) }
    var cityInput by remember { mutableStateOf(PochiPrefs.getCity(context)) }
    var liveWeatherText by remember { mutableStateOf("Ketuk 'Cek Live' untuk cuaca terkini.") }

    val isDarkMode by PochiPrefs.darkModeFlow.collectAsState()
    val activeApproval by ApprovalManager.activeRequest.collectAsState()
    val lastDecision by ApprovalManager.lastDecision.collectAsState()
    val dismissSignal by ApprovalManager.dismissSignal.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    fun closeIsland() {
        if (isExpanded) {
            isExpanded = false
            customEmote = null
            if (activeApproval == null) botState = BotState.IDLE
            onExpandChange(false)
        }
    }

    LaunchedEffect(dismissSignal) {
        if (dismissSignal > 0L && isExpanded) {
            closeIsland()
        }
    }

    LaunchedEffect(activeApproval) {
        if (activeApproval != null) {
            botState = BotState.APPROVAL
            if (!isExpanded) {
                onExpandChange(true)
                isExpanded = true
            }
        }
    }

    LaunchedEffect(lastDecision) {
        lastDecision?.let { decision ->
            botReply = decision
            botState = if (decision.startsWith("Diizinkan")) BotState.DONE else BotState.IDLE
        }
    }

    val transition = updateTransition(targetState = isExpanded, label = "islandTransition")

    val islandWidth by transition.animateDp(
        transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
        label = "width"
    ) { expanded ->
        if (expanded) 352.dp else 172.dp
    }

    val cornerRadius by transition.animateDp(
        transitionSpec = { tween(durationMillis = 220, easing = FastOutSlowInEasing) },
        label = "corner"
    ) { expanded ->
        if (expanded) 30.dp else 50.dp
    }

    // Palet Warna Sesuai Style Guide Gambar (Tema Putih + Silver & Mode Gelap)
    val panelBg = if (!isExpanded) Color(0xFF09090B) else if (isDarkMode) Color(0xFF121215) else Color(0xFFFAFBFC)
    val surfaceBg = if (isDarkMode) Color(0xFF1E1E24) else Color(0xFFFFFFFF)
    val borderCol = if (!isExpanded) Color(0xFF27272A) else if (isDarkMode) Color(0xFF2E2E36) else Color(0xFFE5E7EB)
    val textPrimary = if (isDarkMode) Color(0xFFF9FAFB) else Color(0xFF111827)
    val textSecondary = Color(0xFF8E8E93)
    val primaryBlue = Color(0xFF0A84FF)

    fun askGemini(queryText: String) {
        val apiKey = PochiPrefs.getApiKey(context)
        if (apiKey.isBlank()) {
            botReply = "Isi Gemini API Key dulu di aplikasi utama Pochi ya!"
            botState = BotState.ANNOYED
            customEmote = PochiEmote.ERROR
            return
        }
        customEmote = PochiEmote.WORKING
        botState = BotState.WORKING
        coroutineScope.launch {
            try {
                val modelName = if (selectedModel == "Pro") "gemini-2.5-pro" else "gemini-2.5-flash"
                val response = withContext(Dispatchers.IO) {
                    val model = GenerativeModel(modelName = modelName, apiKey = apiKey)
                    model.generateContent(queryText)
                }
                botReply = response.text?.trim() ?: "Tidak ada jawaban."
                customEmote = PochiEmote.SUCCESS
                botState = BotState.DONE
                delay(3500)
                if (botState == BotState.DONE) {
                    botState = BotState.IDLE
                    customEmote = null
                }
            } catch (e: Exception) {
                botReply = "Kendala koneksi AI: ${e.localizedMessage}"
                customEmote = PochiEmote.ERROR
                botState = BotState.ANNOYED
            }
        }
    }

    // Latar luar penangkap ketukan untuk menutup otomatis
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = isExpanded,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                closeIsland()
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .width(islandWidth)
                .shadow(if (isExpanded) 12.dp else 0.dp, RoundedCornerShape(cornerRadius))
                .clip(RoundedCornerShape(cornerRadius))
                .background(panelBg)
                .border(1.dp, borderCol, RoundedCornerShape(cornerRadius))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (!isExpanded) {
                        onExpandChange(true)
                        isExpanded = true
                    }
                }
                .padding(
                    horizontal = if (isExpanded) 16.dp else 10.dp,
                    vertical = if (isExpanded) 14.dp else 6.dp
                )
        ) {
            if (!isExpanded) {
                // ================= UI - MINI ISLAND (COLLAPSED) SESUAI GAMBAR =================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PochiMascot(
                            state = botState,
                            emoteOverride = customEmote,
                            size = 24.dp
                        )

                        Text(
                            text = if (isTorchOn) "Senter Aktif" else botState.collapsedLabel,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Indikator Status Kanan Sesuai 6 State di Gambar
                    when (botState) {
                        BotState.WORKING -> {
                            ThreeBlueDotsMini()
                        }
                        BotState.APPROVAL -> {
                            Text("🔒", fontSize = 12.sp)
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isTorchOn) Color(0xFFFF9F0A) else botState.accentColor)
                            )
                        }
                    }
                }
            } else {
                // ================= UI - EXPANDED (MAIN PANEL PUTIH + SILVER) =================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Header Pill Hitam di Tengah + Tombol Close Bulat di Kanan
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF09090B))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PochiMascot(state = botState, emoteOverride = customEmote, size = 22.dp)
                            Text(
                                text = botState.statusLabel,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (botState == BotState.IDLE) Color(0xFF30D158)
                                        else botState.accentColor
                                    )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(surfaceBg)
                                .border(1.dp, borderCol, CircleShape)
                                .clickable { closeIsland() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 2. Baris 6 Ikon Tab (Chat AI, Kontrol, Musik, Cuaca, Notes, Ide)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PochiTab.entries.forEach { tab ->
                            val isSelected = tab == currentTab
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { currentTab = tab }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) primaryBlue.copy(alpha = 0.14f)
                                            else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = tab.icon, fontSize = 16.sp)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) primaryBlue else textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(22.dp)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (isSelected) primaryBlue else Color.Transparent)
                                )
                            }
                        }
                    }

                    // 3. Kartu Approval (Jika sedang aktif)
                    activeApproval?.let { request ->
                        PochiApprovalCard(
                            request = request,
                            isDark = isDarkMode,
                            onDeny = {
                                ApprovalManager.confirmDeny()
                                botState = BotState.IDLE
                            }
                        )
                    }

                    // 4. Konten Utama Berdasarkan Tab
                    when (currentTab) {
                        // ---------------- TAB 1: CHAT AI (SESUAI PANEL KIRI TENGAH GAMBAR) ----------------
                        PochiTab.CHAT -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Karakter Kucing Pochi Besar di Kiri + Balon Putih di Kanan
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PochiMascot(
                                        state = botState,
                                        emoteOverride = customEmote ?: PochiEmote.NORMAL,
                                        size = 76.dp,
                                        onPoke = {
                                            pokeCount++
                                            if (pokeCount >= 3) {
                                                botState = BotState.ANNOYED
                                                customEmote = PochiEmote.ANGRY
                                                botReply = "Jangan dicolek terus! Pochi lagi fokus >_<"
                                                coroutineScope.launch {
                                                    delay(2200)
                                                    pokeCount = 0
                                                    botState = BotState.IDLE
                                                    customEmote = null
                                                }
                                            } else {
                                                customEmote = PochiEmote.WINK_LEFT
                                                botReply = "Hei! Ketuk & tahan kepala Pochi kalau mau mengelus ya ✨"
                                            }
                                        },
                                        onPet = {
                                            botState = BotState.DONE
                                            customEmote = PochiEmote.PET_POKE
                                            botReply = "Hehe nyaman banget dielus! Makasih ya ❤️"
                                        }
                                    )

                                    // Balon Bicara Putih dengan Sudut Membulat
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(surfaceBg)
                                            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
                                            .padding(12.dp)
                                            .heightIn(min = 58.dp, max = 105.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = botReply,
                                            color = textPrimary,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Kolom Input Putih "✨ Tanya Pochi sesuatu..." + Tombol Kirim Biru
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(50))
                                        .background(surfaceBg)
                                        .border(1.dp, borderCol, RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("✨", fontSize = 14.sp)

                                    BasicTextField(
                                        value = prompt,
                                        onValueChange = {
                                            prompt = it
                                            if (botState != BotState.WORKING) botState = BotState.WAITING
                                        },
                                        textStyle = TextStyle(color = textPrimary, fontSize = 13.sp),
                                        cursorBrush = SolidColor(primaryBlue),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (prompt.isEmpty()) {
                                                Text(
                                                    text = "Tanya Pochi sesuatu...",
                                                    color = textSecondary,
                                                    fontSize = 13.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(primaryBlue)
                                            .clickable {
                                                if (prompt.isNotBlank() && botState != BotState.WORKING) {
                                                    val q = prompt
                                                    prompt = ""
                                                    askGemini(q)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("➤", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Baris Pill Bawah: [Flash] [Pro]  ...  [📎] [🎤] [🎛️]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("Flash", "Pro").forEach { model ->
                                            val active = selectedModel == model
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(
                                                        if (active) primaryBlue.copy(alpha = 0.15f)
                                                        else surfaceBg
                                                    )
                                                    .border(
                                                        1.dp,
                                                        if (active) primaryBlue.copy(alpha = 0.4f) else borderCol,
                                                        RoundedCornerShape(50)
                                                    )
                                                    .clickable { selectedModel = model }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = model,
                                                    color = if (active) primaryBlue else textSecondary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Tombol Lampiran / Catatan ke Chat
                                        RoundBottomAction(surfaceBg, borderCol, "📎") {
                                            val saved = PochiPrefs.getNotes(context)
                                            if (saved.isNotBlank()) {
                                                prompt = "Ringkas catatan ini: $saved"
                                            } else {
                                                botReply = "Belum ada teks di tab Notes untuk dilampirkan."
                                            }
                                        }
                                        // Tombol Suara / Voice Input Sistem
                                        RoundBottomAction(surfaceBg, borderCol, "🎤") {
                                            try {
                                                val intent = Intent(RecognizerIntent.ACTION_WEB_SEARCH).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                botReply = "Ketuk kolom teks lalu gunakan ikon mikrofon di keyboard HP-mu ya!"
                                            }
                                        }
                                        // Tombol Acak 36 Ekspresi Pochi
                                        RoundBottomAction(surfaceBg, borderCol, "🎭") {
                                            val next = PochiEmote.entries.random()
                                            customEmote = next
                                            botReply = "Ekspresi Pochi #${next.number}: ${next.title}!"
                                        }
                                    }
                                }
                            }
                        }

                        // ---------------- TAB 2: KONTROL 2x3 (SESUAI PANEL KANAN TENGAH GAMBAR) ----------------
                        PochiTab.TOOLS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Baris 1: Senter (Switch) & Kamera
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Kartu Senter dengan Switch
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(surfaceBg)
                                            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, primaryBlue, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🔦", fontSize = 15.sp)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Senter", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Switch(
                                                checked = isTorchOn,
                                                onCheckedChange = { checked ->
                                                    try {
                                                        val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                                                        val camId = camManager.cameraIdList.firstOrNull()
                                                        if (camId != null) {
                                                            isTorchOn = checked
                                                            camManager.setTorchMode(camId, isTorchOn)
                                                            botState = if (isTorchOn) BotState.DONE else BotState.IDLE
                                                        }
                                                    } catch (_: Exception) {}
                                                },
                                                modifier = Modifier.height(24.dp),
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = primaryBlue
                                                )
                                            )
                                        }
                                    }

                                    // Kartu Kamera
                                    ControlArrowCard(
                                        modifier = Modifier.weight(1f),
                                        surfaceBg = surfaceBg,
                                        borderCol = borderCol,
                                        textPrimary = textPrimary,
                                        icon = "📷",
                                        title = "Kamera"
                                    ) {
                                        try {
                                            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            closeIsland()
                                        } catch (e: Exception) {
                                            val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(fallback)
                                        }
                                    }
                                }

                                // Baris 2: Wi-Fi & Bluetooth
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ControlArrowCard(
                                        modifier = Modifier.weight(1f),
                                        surfaceBg = surfaceBg,
                                        borderCol = borderCol,
                                        textPrimary = textPrimary,
                                        icon = "📶",
                                        title = "Wi-Fi"
                                    ) {
                                        try {
                                            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            closeIsland()
                                        } catch (_: Exception) {}
                                    }

                                    ControlArrowCard(
                                        modifier = Modifier.weight(1f),
                                        surfaceBg = surfaceBg,
                                        borderCol = borderCol,
                                        textPrimary = textPrimary,
                                        icon = "ᛒ",
                                        title = "Bluetooth"
                                    ) {
                                        try {
                                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            closeIsland()
                                        } catch (_: Exception) {}
                                    }
                                }

                                // Baris 3: Mode Gelap (Switch) & Matikan Pochi
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(surfaceBg)
                                            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("🌙", fontSize = 16.sp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Mode Gelap", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Switch(
                                                checked = isDarkMode,
                                                onCheckedChange = { PochiPrefs.setDarkMode(context, it) },
                                                modifier = Modifier.height(24.dp),
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = primaryBlue
                                                )
                                            )
                                        }
                                    }

                                    ControlArrowCard(
                                        modifier = Modifier.weight(1f),
                                        surfaceBg = surfaceBg,
                                        borderCol = borderCol,
                                        textPrimary = textPrimary,
                                        icon = "⏻",
                                        title = "Matikan Pochi"
                                    ) {
                                        onPowerOffService()
                                    }
                                }
                            }
                        }

                        // ---------------- TAB 3: MUSIK ----------------
                        PochiTab.MUSIC -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(surfaceBg)
                                    .border(1.dp, borderCol, RoundedCornerShape(18.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MusicButton("⏮️", "Prev", textPrimary) {
                                        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                    }
                                    MusicButton("⏯️", "Play / Pause", textPrimary) {
                                        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                        botState = BotState.DONE
                                    }
                                    MusicButton("⏭️", "Next", textPrimary) {
                                        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
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
                                            .background(panelBg)
                                            .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                            .clickable {
                                                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🔉 Vol -", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(panelBg)
                                            .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                            .clickable {
                                                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🔊 Vol +", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // ---------------- TAB 4: CUACA ----------------
                        PochiTab.WEATHER -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(surfaceBg)
                                            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 10.dp, vertical = 9.dp)
                                    ) {
                                        BasicTextField(
                                            value = cityInput,
                                            onValueChange = {
                                                cityInput = it
                                                PochiPrefs.saveCity(context, it)
                                            },
                                            textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                            cursorBrush = SolidColor(primaryBlue),
                                            singleLine = true,
                                            decorationBox = { inner ->
                                                if (cityInput.isEmpty()) Text("Nama kota...", color = textSecondary, fontSize = 12.sp)
                                                inner()
                                            }
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(primaryBlue)
                                            .clickable {
                                                botState = BotState.WORKING
                                                coroutineScope.launch {
                                                    val result = fetchLiveWeather(cityInput.ifBlank { "Semarang" })
                                                    liveWeatherText = result
                                                    botReply = "Cuaca $cityInput: $result"
                                                    botState = BotState.DONE
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                    ) {
                                        Text("🌤️ Cek Live", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(surfaceBg)
                                            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                            .clickable {
                                                currentTab = PochiTab.CHAT
                                                askGemini("Cuaca di $cityInput saat ini adalah $liveWeatherText. Berikan saran pakaian dan persiapan singkat dalam 2 kalimat santai.")
                                            }
                                            .padding(horizontal = 10.dp, vertical = 9.dp)
                                    ) {
                                        Text("✨ Tips AI", color = primaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(surfaceBg)
                                        .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(text = liveWeatherText, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        // ---------------- TAB 5: NOTES ----------------
                        PochiTab.NOTES -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(surfaceBg)
                                        .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                                        .padding(10.dp)
                                ) {
                                    BasicTextField(
                                        value = noteText,
                                        onValueChange = {
                                            noteText = it
                                            PochiPrefs.saveNotes(context, it)
                                        },
                                        textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                        cursorBrush = SolidColor(primaryBlue),
                                        modifier = Modifier.fillMaxSize(),
                                        decorationBox = { inner ->
                                            if (noteText.isEmpty()) {
                                                Text("Tulis catatan cepat di sini (tersimpan otomatis)...", color = textSecondary, fontSize = 12.sp)
                                            }
                                            inner()
                                        }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(primaryBlue)
                                            .clickable {
                                                if (noteText.isNotBlank()) {
                                                    currentTab = PochiTab.CHAT
                                                    askGemini("Rapikan dan buat daftar to-do singkat dari catatan ini: $noteText")
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✨ Rapikan dengan AI", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(surfaceBg)
                                            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                            .clickable {
                                                noteText = ""
                                                PochiPrefs.saveNotes(context, "")
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🗑️ Hapus", color = Color(0xFFFF453A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // ---------------- TAB 6: IDE ----------------
                        PochiTab.IDEAS -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IdeaChip(Modifier.weight(1f), surfaceBg, borderCol, textPrimary, "🎬 Konten") {
                                    currentTab = PochiTab.CHAT
                                    askGemini("Berikan 3 ide konten viral yang segar dan kreatif hari ini secara singkat dan padat.")
                                }
                                IdeaChip(Modifier.weight(1f), surfaceBg, borderCol, textPrimary, "🚀 Bisnis") {
                                    currentTab = PochiTab.CHAT
                                    askGemini("Berikan 3 ide bisnis digital modal kecil yang unik dan potensial secara singkat.")
                                }
                                IdeaChip(Modifier.weight(1f), surfaceBg, borderCol, textPrimary, "💻 Coding") {
                                    currentTab = PochiTab.CHAT
                                    askGemini("Berikan 3 ide aplikasi atau proyek coding unik untuk portofolio secara singkat.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoundBottomAction(bg: Color, border: Color, icon: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = icon, fontSize = 13.sp)
    }
}

@Composable
fun ControlArrowCard(
    modifier: Modifier = Modifier,
    surfaceBg: Color,
    borderCol: Color,
    textPrimary: Color,
    icon: String,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceBg)
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 16.sp, color = textPrimary)
            Text(text = title, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text("›", color = Color(0xFF8E8E93), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MusicButton(icon: String, label: String, textColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text = icon, fontSize = 18.sp)
        Text(text = label, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun IdeaChip(
    modifier: Modifier = Modifier,
    bg: Color,
    border: Color,
    textColor: Color,
    title: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ThreeBlueDotsMini() {
    val infiniteTransition = rememberInfiniteTransition(label = "miniDots")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { idx ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, delayMillis = idx * 120),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$idx"
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A84FF).copy(alpha = alpha))
            )
        }
    }
}

fun dispatchMediaKey(context: Context, keyCode: Int) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val eventTime = SystemClock.uptimeMillis()
    val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
    val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
    audioManager.dispatchMediaKeyEvent(downEvent)
    audioManager.dispatchMediaKeyEvent(upEvent)
}

suspend fun fetchLiveWeather(city: String): String = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(city.trim(), "UTF-8")
        val url = URL("https://wttr.in/$encoded?format=%C+%t+(%h)")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "curl/7.68.0")
        }
        conn.inputStream.bufferedReader().use { it.readText().trim() }
    } catch (e: Exception) {
        "Gagal memuat cuaca langsung, periksa koneksi internet."
    }
}
