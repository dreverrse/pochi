package com.pochi.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
    TOOLS("Kontrol", "🛠️"),
    MUSIC("Musik", "🎵"),
    WEATHER("Cuaca", "🌤️"),
    NOTES("Notes", "📝"),
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
    var currentTab by remember { mutableStateOf(PochiTab.CHAT) }
    var selectedModel by remember { mutableStateOf("Flash") }

    var prompt by remember { mutableStateOf("") }
    var botReply by remember { mutableStateOf("Halo! Aku Pochi. Pilih tab di atas atau ketik pertanyaanmu.") }
    var pokeCount by remember { mutableIntStateOf(0) }

    // State Fitur Multifungsi
    var isTorchOn by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(PochiPrefs.getNotes(context)) }
    var cityInput by remember { mutableStateOf(PochiPrefs.getCity(context)) }
    var liveWeatherText by remember { mutableStateOf("Ketuk 'Cek Live' untuk cuaca terkini.") }

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

    // Animasi radius sudut dan warna tepi super mulus
    val animatedCorner by animateDpAsState(
        targetValue = if (isExpanded) 28.dp else 50.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "corner"
    )

    val borderColor by animateColorAsState(
        targetValue = if (botState == BotState.IDLE && !isExpanded) {
            Color(0xFF2C2C2E)
        } else {
            botState.accentColor.copy(alpha = 0.7f)
        },
        animationSpec = tween(250),
        label = "borderGlow"
    )

    // Fungsi bantu kirim prompt ke Gemini
    fun askGemini(queryText: String) {
        val apiKey = PochiPrefs.getApiKey(context)
        if (apiKey.isBlank()) {
            botReply = "Isi Gemini API Key dulu di aplikasi utama Pochi ya!"
            botState = BotState.ANNOYED
            return
        }
        botState = BotState.WORKING
        coroutineScope.launch {
            try {
                val modelName = if (selectedModel == "Pro") "gemini-2.5-pro" else "gemini-2.5-flash"
                val response = withContext(Dispatchers.IO) {
                    val model = GenerativeModel(modelName = modelName, apiKey = apiKey)
                    model.generateContent(queryText)
                }
                botReply = response.text?.trim() ?: "Tidak ada jawaban."
                botState = BotState.DONE
                delay(3500)
                if (botState == BotState.DONE) botState = BotState.IDLE
            } catch (e: Exception) {
                botReply = "Kendala koneksi AI: ${e.localizedMessage}"
                botState = BotState.ANNOYED
            }
        }
    }

    // Container Utama yang Dioptimasi dengan Hardware Layer
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .graphicsLayer { clip = false }
            .clip(RoundedCornerShape(animatedCorner))
            .background(Color(0xFF0A0A0C))
            .border(1.dp, borderColor, RoundedCornerShape(animatedCorner))
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = 260,
                    easing = FastOutSlowInEasing
                )
            )
            .clickable(
                enabled = !isExpanded,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
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
            // ================= TAMPILAN KAPSUL KECIL (COLLAPSED) =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(26.dp)
            ) {
                PochiMascot(state = botState, size = 22.dp)

                Text(
                    text = when {
                        isTorchOn -> "💡 Senter Aktif"
                        botState == BotState.IDLE -> "Pochi"
                        else -> botState.statusLabel
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isTorchOn) Color(0xFFFF9F0A) else botState.accentColor)
                )
            }
        } else {
            // ================= TAMPILAN NOTCH MULTIFUNGSI (EXPANDED) =================
            Column(
                modifier = Modifier.width(336.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Baris Navigasi Tab (Scrollable & Rapi) + Tombol Tutup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PochiTab.entries.forEach { tab ->
                            val isSelected = tab == currentTab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (isSelected) botState.accentColor.copy(alpha = 0.22f)
                                        else Color(0xFF18181B)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) botState.accentColor else Color(0xFF27272A),
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { currentTab = tab }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${tab.icon} ${tab.title}",
                                    color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1C1C1E))
                            .clickable {
                                isExpanded = false
                                botState = BotState.IDLE
                                onExpandChange(false)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 2. Panel Hero Pochi (Maskot + Balon Respons yang Bisa Di-scroll)
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
                        size = 42.dp,
                        onPoke = {
                            pokeCount++
                            if (pokeCount >= 3) {
                                botState = BotState.ANNOYED
                                botReply = "Aduh! Pochi pusing dicolek terus >_<"
                                coroutineScope.launch {
                                    delay(2200)
                                    pokeCount = 0
                                    botState = BotState.IDLE
                                }
                            } else {
                                botReply = "Tab aktif: ${currentTab.title}. Ketuk tombol di bawah!"
                            }
                        },
                        onPet = {
                            botState = BotState.DONE
                            botReply = "Hehe, makasih sudah elus Pochi! ^ ^"
                        }
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp, max = 96.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "${currentTab.icon} ${currentTab.title} • ${botState.statusLabel}",
                            color = botState.accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = botReply,
                            color = Color(0xFFE5E5EA),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                // 3. Kartu Approval Sidik Jari (Jika Ada Permintaan Aktif)
                activeApproval?.let { request ->
                    PochiApprovalCard(
                        request = request,
                        onDeny = { ApprovalManager.confirmDeny() }
                    )
                }

                // 4. Gelombang 9 Titik Saat Pochi Memproses AI
                if (botState == BotState.WORKING) {
                    NineDotsWaveform(color = botState.accentColor)
                }

                // 5. Area Modul Multifungsi (Crossfade Mulus & Tidak Bentrok)
                Crossfade(
                    targetState = currentTab,
                    animationSpec = tween(180),
                    label = "tabContent"
                ) { tab ->
                    when (tab) {
                        // ---------------- TAB 1: CHAT AI ----------------
                        PochiTab.CHAT -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF1C1C1E))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Pill pemilih Flash / Pro
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF2C2C2E))
                                        .clickable {
                                            selectedModel = if (selectedModel == "Flash") "Pro" else "Flash"
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = selectedModel,
                                        color = Color(0xFF0A84FF),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                BasicTextField(
                                    value = prompt,
                                    onValueChange = {
                                        prompt = it
                                        if (botState != BotState.WORKING) botState = BotState.WAITING
                                    },
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(botState.accentColor),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (prompt.isEmpty()) {
                                            Text(
                                                text = "Tanya Pochi $selectedModel...",
                                                color = Color.Gray,
                                                fontSize = 12.sp
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
                                                val q = prompt
                                                prompt = ""
                                                askGemini(q)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("↑", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        // ---------------- TAB 2: KONTROL CEPAT (SENTER, KAMERA, ON/OFF) ----------------
                        PochiTab.TOOLS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Tombol Senter (Flashlight)
                                    QuickToolTile(
                                        modifier = Modifier.weight(1f),
                                        icon = "🔦",
                                        label = if (isTorchOn) "Senter: ON" else "Senter: OFF",
                                        active = isTorchOn,
                                        activeColor = Color(0xFFFF9F0A)
                                    ) {
                                        try {
                                            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                                            val camId = camManager.cameraIdList.firstOrNull()
                                            if (camId != null) {
                                                isTorchOn = !isTorchOn
                                                camManager.setTorchMode(camId, isTorchOn)
                                                botReply = if (isTorchOn) "Senter belakang dinyalakan!" else "Senter dimatikan."
                                                botState = if (isTorchOn) BotState.DONE else BotState.IDLE
                                            }
                                        } catch (e: Exception) {
                                            botReply = "Tidak bisa mengakses senter: ${e.localizedMessage}"
                                            botState = BotState.ANNOYED
                                        }
                                    }

                                    // Tombol Buka Kamera
                                    QuickToolTile(
                                        modifier = Modifier.weight(1f),
                                        icon = "📷",
                                        label = "Kamera",
                                        active = false
                                    ) {
                                        try {
                                            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            isExpanded = false
                                            onExpandChange(false)
                                        } catch (e: Exception) {
                                            val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(fallback)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Tombol Setelan Wi-Fi / Koneksi Cepat
                                    QuickToolTile(
                                        modifier = Modifier.weight(1f),
                                        icon = "📡",
                                        label = "Panel Wi-Fi",
                                        active = false
                                    ) {
                                        try {
                                            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            isExpanded = false
                                            onExpandChange(false)
                                        } catch (_: Exception) {}
                                    }

                                    // Tombol On/Off Kapsul Pochi
                                    QuickToolTile(
                                        modifier = Modifier.weight(1f),
                                        icon = "⚡",
                                        label = "Matikan Pochi",
                                        active = false,
                                        textColor = Color(0xFFFF453A)
                                    ) {
                                        onPowerOffService()
                                    }
                                }
                            }
                        }

                        // ---------------- TAB 3: KONTROL MUSIK SISTEM ----------------
                        PochiTab.MUSIC -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF18181B))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MusicButton("⏮️", "Prev") {
                                        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                        botReply = "Memutar lagu sebelumnya."
                                    }
                                    MusicButton("⏯️", "Play / Pause") {
                                        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                        botReply = "Play / Pause musik dikirim ke pemutar aktif."
                                        botState = BotState.DONE
                                    }
                                    MusicButton("⏭️", "Next") {
                                        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                                        botReply = "Melompat ke lagu berikutnya."
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
                                            .background(Color(0xFF27272A))
                                            .clickable {
                                                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🔉 Vol -", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF27272A))
                                            .clickable {
                                                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                                            }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🔊 Vol +", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        // ---------------- TAB 4: CUACA REAL-TIME & TIPS AI ----------------
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
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF1C1C1E))
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        BasicTextField(
                                            value = cityInput,
                                            onValueChange = {
                                                cityInput = it
                                                PochiPrefs.saveCity(context, it)
                                            },
                                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                            cursorBrush = SolidColor(Color(0xFF0A84FF)),
                                            singleLine = true,
                                            decorationBox = { inner ->
                                                if (cityInput.isEmpty()) Text("Nama kota...", color = Color.Gray, fontSize = 12.sp)
                                                inner()
                                            }
                                        )
                                    }

                                    // Tombol Cek Cuaca Real-Time (wttr.in)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF0A84FF))
                                            .clickable {
                                                botState = BotState.WORKING
                                                coroutineScope.launch {
                                                    val result = fetchLiveWeather(cityInput.ifBlank { "Jakarta" })
                                                    liveWeatherText = result
                                                    botReply = "Cuaca $cityInput saat ini: $result"
                                                    botState = BotState.DONE
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text("🌤️ Cek Live", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Tombol Saran Outfit/Aktivitas via AI
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF27272A))
                                            .clickable {
                                                askGemini("Cuaca di $cityInput saat ini adalah $liveWeatherText. Berikan saran pakaian dan persiapan singkat dalam 2 kalimat santai.")
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text("✨ Tips AI", color = Color(0xFFFF9F0A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // ---------------- TAB 5: CATATAN CEPAT PERMANEN (NOTES) ----------------
                        PochiTab.NOTES -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1C1C1E))
                                        .padding(10.dp)
                                ) {
                                    BasicTextField(
                                        value = noteText,
                                        onValueChange = {
                                            noteText = it
                                            PochiPrefs.saveNotes(context, it)
                                        },
                                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                        cursorBrush = SolidColor(Color(0xFF30D158)),
                                        modifier = Modifier.fillMaxSize(),
                                        decorationBox = { inner ->
                                            if (noteText.isEmpty()) {
                                                Text("Tulis catatan cepat di sini (tersimpan otomatis)...", color = Color.Gray, fontSize = 12.sp)
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
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF27272A))
                                            .clickable {
                                                if (noteText.isNotBlank()) {
                                                    askGemini("Rapikan dan buat daftar to-do singkat dari catatan ini: $noteText")
                                                } else {
                                                    botReply = "Catatan masih kosong, tulis sesuatu dulu ya!"
                                                }
                                            }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✨ Rapikan dengan AI", color = Color(0xFF30D158), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF27272A))
                                            .clickable {
                                                noteText = ""
                                                PochiPrefs.saveNotes(context, "")
                                                botReply = "Catatan berhasil dikosongkan."
                                            }
                                            .padding(horizontal = 12.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🗑️ Hapus", color = Color(0xFFFF453A), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // ---------------- TAB 6: BRAINSTORMING & IDE INSTAN ----------------
                        PochiTab.IDEAS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IdeaChip(Modifier.weight(1f), "🎬 Ide Konten") {
                                        askGemini("Berikan 3 ide konten viral yang segar dan kreatif hari ini secara singkat dan padat.")
                                    }
                                    IdeaChip(Modifier.weight(1f), "🚀 Ide Bisnis") {
                                        askGemini("Berikan 3 ide bisnis digital modal kecil yang unik dan potensial secara singkat.")
                                    }
                                    IdeaChip(Modifier.weight(1f), "💻 Ide Coding") {
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
}

@Composable
fun QuickToolTile(
    modifier: Modifier = Modifier,
    icon: String,
    label: String,
    active: Boolean,
    activeColor: Color = Color(0xFF0A84FF),
    textColor: Color = Color.White,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) activeColor else Color(0xFF1C1C1E))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$icon $label",
            color = if (active) Color.Black else textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun MusicButton(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text = icon, fontSize = 18.sp)
        Text(text = label, color = Color.LightGray, fontSize = 10.sp)
    }
}

@Composable
fun IdeaChip(modifier: Modifier = Modifier, title: String, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1E))
            .border(1.dp, Color(0xFFFF9F0A).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, color = Color(0xFFFF9F0A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
