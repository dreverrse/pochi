package com.pochi.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PochiPrefs.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PochiV2App()
        }
    }
}

/**
 * 4 Simple & Clean Navigation Tabs for Pochi v2 AI Agent.
 */
enum class PochiNavTab(
    val titleId: String,
    val titleEn: String,
    val icon: ImageVector,
    val accent: Color
) {
    HOME("Beranda", "Home", Icons.Rounded.Home, Color(0xFF34C759)),
    CHAT("Pochi", "Pochi", Icons.Rounded.AutoAwesome, Color(0xFF007AFF)),
    MEMORY("Memori", "Memory", Icons.Rounded.Psychology, Color(0xFFAF52DE)),
    SETTINGS("Pengaturan", "Settings", Icons.Rounded.Settings, Color(0xFFFF9500))
}

data class UiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "user" or "pochi"
    val text: String,
    val actionDetail: String? = null,
    val emote: PochiEmote? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isUser: Boolean get() = sender == "user"
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Composable
fun PochiV2App() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Authentic Dark-yet-Colorful Palette
    val bgDark = Color(0xFF0B0B0F)
    val cardDark = Color(0xFF16161F)
    val borderDark = Color(0xFF242432)
    val textWhite = Color(0xFFFFFFFF)
    val textMuted = Color(0xFF8E8E93)

    val isOnboardingDone by PochiPrefs.onboardingDoneFlow.collectAsState()
    val userName by PochiPrefs.userNameFlow.collectAsState()
    val lang by PochiPrefs.langFlow.collectAsState()
    val isId = lang == PochiLang.ID

    var activeTab by remember { mutableStateOf(PochiNavTab.HOME) }
    var currentEmote by remember { mutableStateOf(PochiEmote.SMILING) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgDark
    ) {
        if (!isOnboardingDone) {
            PochiOnboardingScreen(
                onComplete = {
                    PochiPrefs.setOnboardingDone(context, true)
                }
            )
        } else {
            Scaffold(
                containerColor = bgDark,
                bottomBar = {
                    PochiFloatingNavBar(
                        activeTab = activeTab,
                        onTabSelect = { tab ->
                            activeTab = tab
                            PochiSignals.playTapHaptic(context)
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Crossfade(
                        targetState = activeTab,
                        animationSpec = tween(durationMillis = 180),
                        label = "tabTransition"
                    ) { tab ->
                        when (tab) {
                            PochiNavTab.HOME -> HomeScreen(
                                onNavigateToChat = { activeTab = PochiNavTab.CHAT },
                                onNavigateToMemory = { activeTab = PochiNavTab.MEMORY },
                                onNavigateToSettings = { activeTab = PochiNavTab.SETTINGS }
                            )
                            PochiNavTab.CHAT -> ChatScreen()
                            PochiNavTab.MEMORY -> MemoryScreen()
                            PochiNavTab.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 1. FLOATING NAVIGATION BAR (4 CLEAN TABS)
// ============================================================================
@Composable
fun PochiFloatingNavBar(
    activeTab: PochiNavTab,
    onTabSelect: (PochiNavTab) -> Unit
) {
    val cardDark = Color(0xFF14141C)
    val borderDark = Color(0xFF262636)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(26.dp))
                .background(cardDark)
                .border(1.dp, borderDark, RoundedCornerShape(26.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PochiNavTab.entries.forEach { tab ->
                val isSelected = activeTab == tab
                val bgCol by animateColorAsState(
                    targetValue = if (isSelected) tab.accent.copy(alpha = 0.22f) else Color.Transparent,
                    animationSpec = tween(180),
                    label = "tabBg"
                )

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgCol)
                        .clickable { onTabSelect(tab) }
                        .padding(horizontal = if (isSelected) 14.dp else 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.titleId,
                        tint = if (isSelected) tab.accent else Color(0xFF8E8E93),
                        modifier = Modifier.size(20.dp)
                    )
                    AnimatedVisibility(visible = isSelected) {
                        Text(
                            text = tab.titleId,
                            color = tab.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. HOME SCREEN (HERO MASCOT, REAL-TIME STATUS, ACTIVITY RINGS)
// ============================================================================
@Composable
fun HomeScreen(
    onNavigateToChat: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userName by PochiPrefs.userNameFlow.collectAsState()
    val tasks by PochiPrefs.tasksFlow.collectAsState()
    val waterGlasses by PochiPrefs.waterGlassesFlow.collectAsState()
    val focusSessions by PochiPrefs.focusSessionsFlow.collectAsState()
    val selectedCity by PochiPrefs.cityFlow.collectAsState()
    val lang by PochiPrefs.langFlow.collectAsState()
    val isId = lang == PochiLang.ID

    val screenApp by PochiSignals.screenApp.collectAsState()
    val screenText by PochiSignals.screenText.collectAsState()
    val mediaPlayback by PochiSignals.mediaPlayback.collectAsState()

    var pokeCount by remember { mutableIntStateOf(0) }
    var mascotEmote by remember { mutableStateOf(PochiEmote.SMILING) }

    // Dynamic greeting based on time of day
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..10 -> if (isId) "Selamat Pagi" else "Good Morning"
            in 11..14 -> if (isId) "Selamat Siang" else "Good Afternoon"
            in 15..18 -> if (isId) "Selamat Sore" else "Good Evening"
            else -> if (isId) "Selamat Malam" else "Good Night"
        }
    }

    val nextPrayer = remember(selectedCity) {
        PrayerScheduleHelper.getNextPrayer(selectedCity)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- TOP HEADER ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "$greeting,",
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = userName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Quick language badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF16161F))
                        .border(1.dp, Color(0xFF242432), RoundedCornerShape(12.dp))
                        .clickable { PochiPrefs.toggleLanguage(context) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isId) "🇮🇩 ID" else "🇬🇧 EN",
                        color = Color(0xFF007AFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- HERO MASCOT CARD ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF191924), Color(0xFF111118))
                        )
                    )
                    .border(1.dp, Color(0xFF262638), RoundedCornerShape(28.dp))
                    .clickable {
                        pokeCount++
                        val (em, _, _) = PochiMood.onPokeReaction(pokeCount)
                        mascotEmote = em
                        PochiSignals.playTapHaptic(context)
                    }
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PochiMascot(
                        state = BotState.IDLE,
                        emoteOverride = mascotEmote,
                        size = 68.dp,
                        showBlackBacking = false
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Pochi AI Agent",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isId) "Siap mendampingi dan mengingat semua sesi Tuan Andre" else "Ready to assist and remember all sessions for Tuan Andre",
                            color = Color(0xFF8E8E93),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // --- REAL-TIME CONTEXT AWARENESS CARD ---
        item {
            val isAwarenessActive = screenApp.isNotBlank() || mediaPlayback.isPlaying
            val activeActivityLabel = when {
                mediaPlayback.isPlaying -> "Sedang memutar: ${mediaPlayback.title.take(24)}"
                screenApp.isNotBlank() -> "Sedang aktif: $screenApp"
                else -> if (isId) "Siap memantau aktivitas & notifikasi Tuan Andre" else "Monitoring active context"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF26263A), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAwarenessActive) Color(0xFF34C759) else Color(0xFFFF9500))
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isId) "Konteks Real-Time" else "Real-Time Context",
                            color = Color(0xFF8E8E93),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = activeActivityLabel,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = "Awareness",
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // --- ACTIVITY RINGS SECTION (APPLE HEALTH STYLE) ---
        item {
            val doneTasks = tasks.count { it.isDone }
            val totalTasks = tasks.size.coerceAtLeast(1)

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isId) "Aktivitas Hari Ini" else "Today's Activity",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Ring 1: Tugas
                    HomeMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Tugas 🎯",
                        value = "$doneTasks/$totalTasks",
                        accent = Color(0xFF34C759),
                        onClick = onNavigateToChat
                    )

                    // Ring 2: Air Minum
                    HomeMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Air 💧",
                        value = "${waterGlasses * 250}ml",
                        accent = Color(0xFF007AFF),
                        onClick = {
                            PochiPrefs.addWaterGlass(context)
                            PochiSignals.playSuccessHaptic(context)
                            mascotEmote = PochiEmote.HAPPY
                        }
                    )

                    // Ring 3: Fokus
                    HomeMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Fokus ⚡",
                        value = "${focusSessions * 25}m",
                        accent = Color(0xFFFF9500),
                        onClick = onNavigateToChat
                    )
                }
            }
        }

        // --- GLANCEABLE WEATHER & PRAYER CARD ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF26263A), RoundedCornerShape(22.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "☀️",
                            fontSize = 24.sp
                        )
                        Column {
                            Text(
                                text = "$selectedCity • 32°C",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "UV High • Cerah Berawan",
                                color = Color(0xFF8E8E93),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Next prayer badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFF9500).copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🕌 ${nextPrayer.prayer.nameId} ${nextPrayer.prayer.formatted}",
                            color = Color(0xFFFF9500),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- QUICK TALK TO POCHI BAR ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF007AFF), Color(0xFF5856D6))
                        )
                    )
                    .clickable { onNavigateToChat() }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubble,
                            contentDescription = "Chat",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isId) "Bicara dengan Pochi..." else "Talk to Pochi...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = "Open",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF161622))
            .border(1.dp, Color(0xFF26263A), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF8E8E93),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ============================================================================
// 3. CHAT SCREEN (AI AGENT CONVERSATION WITH TOOL RESULTS)
// ============================================================================
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userName by PochiPrefs.userNameFlow.collectAsState()
    val lang by PochiPrefs.langFlow.collectAsState()
    val isId = lang == PochiLang.ID

    val db = remember { PochiDb.getInstance(context) }
    var chatMessages by remember {
        mutableStateOf(
            db.getChatMessages(80).map {
                UiChatMessage(
                    id = it.id,
                    sender = it.sender,
                    text = it.text,
                    timestamp = it.timestamp
                )
            }.toMutableList()
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var agentEmote by remember { mutableStateOf(PochiEmote.SMILING) }

    val listState = rememberLazyListState()

    fun sendMessage(text: String) {
        val clean = text.trim()
        if (clean.isBlank() || isThinking) return

        val userMsg = UiChatMessage(sender = "user", text = clean)
        chatMessages.add(userMsg)
        db.insertChatMessage("user", clean)
        inputText = ""
        isThinking = true
        agentEmote = PochiEmote.THINKING

        coroutineScope.launch {
            listState.animateScrollToItem(chatMessages.size - 1)

            // 1. Try local device handler first (0ms latency)
            val localResult = PochiAgent.tryHandleLocal(context, clean)
            if (localResult != null) {
                val (replyText, action) = localResult
                var actionFeedback = ""
                if (action != null) {
                    actionFeedback = PochiAgent.execute(context, action)
                }
                val finalReply = if (actionFeedback.isNotBlank()) "$replyText\n$actionFeedback" else replyText
                val pochiMsg = UiChatMessage(
                    sender = "pochi",
                    text = finalReply,
                    actionDetail = action?.type,
                    emote = PochiEmote.HAPPY
                )
                chatMessages.add(pochiMsg)
                db.insertChatMessage("pochi", finalReply)
                isThinking = false
                agentEmote = PochiEmote.HAPPY
                listState.animateScrollToItem(chatMessages.size - 1)
                return@launch
            }

            // 2. Query generative AI with persistent memory injection
            val provider = PochiPrefs.getProvider(context)
            val apiKey = PochiPrefs.getApiKey(context, provider)
            val model = PochiPrefs.getModel(context, provider)

            if (apiKey.isBlank()) {
                val needKeyMsg = if (isId) {
                    "Halo $userName! Untuk mengaktifkan kecerdasan penuh Pochi, silakan masukkan API Key di menu Pengaturan. Sementara itu, Tuan Andre bisa mencoba perintah langsung seperti 'pasang timer 20 menit', 'jadwal sholat', 'catat: beli buku', atau 'ingat saya suka kopi'."
                } else {
                    "Hello $userName! Please configure your AI API Key in Settings for generative reasoning. Device commands like timers, notes, and prayer times work offline!"
                }
                val pochiMsg = UiChatMessage(sender = "pochi", text = needKeyMsg, emote = PochiEmote.WINK_LEFT)
                chatMessages.add(pochiMsg)
                db.insertChatMessage("pochi", needKeyMsg)
                isThinking = false
                agentEmote = PochiEmote.WINK_LEFT
                listState.animateScrollToItem(chatMessages.size - 1)
                return@launch
            }

            // Build rich memory + real-time context note
            val memoriesPrompt = db.getMemoriesPrompt()
            val screenApp = PochiSignals.screenApp.value
            val contextNote = buildString {
                if (memoriesPrompt.isNotBlank()) {
                    append("\nMemori Tersimpan tentang Tuan Andre:\n")
                    append(memoriesPrompt)
                }
                if (screenApp.isNotBlank()) {
                    append("\nAplikasi yang sedang dibuka Tuan Andre: $screenApp")
                }
            }

            val response = runCatching {
                AiClient.ask(
                    provider = provider,
                    apiKey = apiKey,
                    model = model,
                    prompt = clean,
                    contextNote = contextNote
                )
            }.getOrElse { "Maaf Tuan Andre, terjadi kendala koneksi AI: ${it.localizedMessage}" }

            val (replyWithoutAction, action) = PochiAgent.extract(response)
            val (cleanText, extractedEmote) = PochiAgent.extractEmote(replyWithoutAction, clean)

            var actionFeedback: String? = null
            if (action != null) {
                if (PochiAgent.requiresApproval(action)) {
                    ApprovalManager.requestAction(context, action)
                    actionFeedback = "Meminta persetujuan Tuan Andre untuk ${action.type}..."
                } else {
                    actionFeedback = PochiAgent.execute(context, action)
                }
            }

            val finalDisplay = if (actionFeedback != null) "$cleanText\n\n[$actionFeedback]" else cleanText
            val pochiMsg = UiChatMessage(
                sender = "pochi",
                text = finalDisplay,
                actionDetail = action?.type,
                emote = extractedEmote
            )
            chatMessages.add(pochiMsg)
            db.insertChatMessage("pochi", finalDisplay)
            isThinking = false
            agentEmote = extractedEmote
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // Speech recognition setup
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() { isListening = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) {
                    sendMessage(spoken)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) {
                    inputText = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer?.setRecognitionListener(listener)
        onDispose {
            runCatching { speechRecognizer?.destroy() }
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isId) "id-ID" else "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer.startListening(intent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- CHAT TOP HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161622))
                        .border(1.dp, Color(0xFF26263A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    PochiMascot(
                        state = if (isThinking) BotState.WORKING else BotState.IDLE,
                        emoteOverride = agentEmote,
                        size = 24.dp,
                        showBlackBacking = false
                    )
                }

                Column {
                    Text(
                        text = "Pochi Agent",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isThinking) "Berpikir..." else "Online • Menjawab Tuan Andre",
                        color = if (isThinking) Color(0xFFFF9500) else Color(0xFF34C759),
                        fontSize = 11.sp
                    )
                }
            }

            // Clear chat option
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF161622))
                    .clickable {
                        db.clearChatMessages()
                        chatMessages.clear()
                        PochiSignals.playTapHaptic(context)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Bersihkan",
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp
                )
            }
        }

        // --- MESSAGE LIST ---
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages, key = { it.id }) { msg ->
                ChatBubble(msg = msg)
            }
        }

        // --- QUICK CHIP SUGGESTIONS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf(
                "Rangkum notifikasi",
                "Fokus 25m",
                "Catat 25rb makan siang",
                "Ingat saya suka kopi"
            )
            suggestions.take(3).forEach { chip ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF161622))
                        .border(1.dp, Color(0xFF26263A), RoundedCornerShape(14.dp))
                        .clickable { sendMessage(chip) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = chip,
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // --- INPUT BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text Input Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF26263A), RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(Color(0xFF007AFF)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage(inputText) }),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Ketik pesan untuk Pochi...",
                                color = Color(0xFF8E8E93),
                                fontSize = 13.sp
                            )
                        }
                        inner()
                    }
                )
            }

            // Voice Mic Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isListening) Color(0xFFFF3B30) else Color(0xFF161622))
                    .border(1.dp, if (isListening) Color(0xFFFF3B30) else Color(0xFF26263A), CircleShape)
                    .clickable {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            if (isListening) {
                                speechRecognizer?.stopListening()
                            } else {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isId) "id-ID" else "en-US")
                                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                                }
                                speechRecognizer?.startListening(intent)
                            }
                        } else {
                            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                    contentDescription = "Mic",
                    tint = if (isListening) Color.White else Color(0xFF007AFF),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Send Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF))
                    .clickable { sendMessage(inputText) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(msg: UiChatMessage) {
    val bubbleColor = if (msg.isUser) Color(0xFF007AFF) else Color(0xFF161622)
    val align = if (msg.isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (msg.isUser) 18.dp else 4.dp,
                        bottomEnd = if (msg.isUser) 4.dp else 18.dp
                    )
                )
                .background(bubbleColor)
                .border(
                    width = if (msg.isUser) 0.dp else 1.dp,
                    color = Color(0xFF26263A),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = msg.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Text(
                    text = msg.formattedTime,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ============================================================================
// 4. MEMORY SCREEN (WHAT POCHI REMEMBERS ACROSS SESSIONS & LAPTOP SYNC)
// ============================================================================
@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { PochiDb.getInstance(context) }
    var memories by remember { mutableStateOf(db.getAllMemories()) }

    var newFactText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Preferensi") }
    var isAdding by remember { mutableStateOf(false) }

    fun refresh() {
        memories = db.getAllMemories()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- HEADER ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Memori Tuan Andre",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Multi-Device Sync Pill (HP <-> Laptop)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFAF52DE).copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "📱 HP • 💻 Laptop (Ready)",
                            color = Color(0xFFAF52DE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "Pochi mengingat preferensi, kebiasaan, dan fakta penting di semua sesi agar percakapan selalu bersambung.",
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }

        // --- ADD MEMORY CARD ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF26263A), RoundedCornerShape(22.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "+ Tambah Memori Baru",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Category chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Preferensi", "Fakta", "Kebiasaan", "Orang").forEach { cat ->
                            val isSel = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) Color(0xFFAF52DE) else Color(0xFF1E1E2C))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSel) Color.White else Color(0xFF8E8E93),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Input
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E2C))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = newFactText,
                            onValueChange = { newFactText = it },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(Color(0xFFAF52DE)),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (newFactText.isEmpty()) {
                                    Text(
                                        text = "Misal: Suka minum teh hangat tanpa gula...",
                                        color = Color(0xFF8E8E93),
                                        fontSize = 12.sp
                                    )
                                }
                                inner()
                            }
                        )
                    }

                    // Save Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFAF52DE))
                            .clickable {
                                if (newFactText.isNotBlank()) {
                                    db.saveMemory(selectedCategory, newFactText)
                                    newFactText = ""
                                    refresh()
                                    PochiSignals.playSuccessHaptic(context)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Simpan ke Memori",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- MEMORY ITEMS LIST ---
        items(memories, key = { it.id }) { mem ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF26263A), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFAF52DE).copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = mem.category,
                                    color = Color(0xFFAF52DE),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = mem.formattedDate,
                                color = Color(0xFF8E8E93),
                                fontSize = 10.sp
                            )
                        }
                        Text(
                            text = mem.fact,
                            color = Color.White,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            db.deleteMemory(mem.id)
                            refresh()
                            PochiSignals.playTapHaptic(context)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Hapus",
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. SETTINGS SCREEN (AI PROVIDER, API KEY, ISLAND TOGGLE, PERMISSIONS)
// ============================================================================
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userName by PochiPrefs.userNameFlow.collectAsState()
    val provider by PochiPrefs.providerFlow.collectAsState()
    val selectedCity by PochiPrefs.cityFlow.collectAsState()

    var editingUserName by remember { mutableStateOf(userName) }
    var currentApiKey by remember { mutableStateOf(PochiPrefs.getApiKey(context, provider)) }
    var showApiKey by remember { mutableStateOf(false) }
    var islandActive by remember { mutableStateOf(DynamicIslandService.isRunning.value) }

    fun saveProvider(p: AiProvider) {
        PochiPrefs.setProvider(context, p)
        currentApiKey = PochiPrefs.getApiKey(context, p)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Pengaturan",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // --- PROFIL USER ---
        item {
            SettingsCard(title = "Profil Pengguna") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Nama Panggilan", color = Color(0xFF8E8E93), fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E2C))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = editingUserName,
                            onValueChange = {
                                editingUserName = it
                                PochiPrefs.setUserName(context, it)
                            },
                            textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(Color(0xFFFF9500)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // --- AI BRAIN PROVIDER ---
        item {
            SettingsCard(title = "Otak AI Agent") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Pilih Penyedia AI", color = Color(0xFF8E8E93), fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiProvider.entries.forEach { p ->
                            val isSel = provider == p
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) Color(0xFFFF9500) else Color(0xFF1E1E2C))
                                    .clickable { saveProvider(p) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = p.label,
                                    color = if (isSel) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(text = "API Key (${provider.label})", color = Color(0xFF8E8E93), fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E2C))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            BasicTextField(
                                value = currentApiKey,
                                onValueChange = {
                                    currentApiKey = it
                                    PochiPrefs.saveApiKey(context, provider, it)
                                },
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(Color(0xFFFF9500)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle",
                                tint = Color(0xFF8E8E93)
                            )
                        }
                    }
                }
            }
        }

        // --- DYNAMIC ISLAND OVERLAY CONTROL ---
        item {
            SettingsCard(title = "Pochi Dynamic Island") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Tampilkan Dynamic Island", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Mengambang di status bar HP", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    }

                    Switch(
                        checked = islandActive,
                        onCheckedChange = { active ->
                            islandActive = active
                            toggleDynamicIsland(context, active)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759))
                    )
                }
            }
        }

        // --- PERMISSIONS STATUS ---
        item {
            SettingsCard(title = "Status Izin Sistem") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val hasOverlay = Settings.canDrawOverlays(context)
                    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

                    PermissionStatusRow(
                        name = "Izin Tampil di Atas Aplikasi",
                        isGranted = hasOverlay,
                        onClick = {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    )

                    PermissionStatusRow(
                        name = "Izin Mikrofon (Suara)",
                        isGranted = hasMic,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        }
                    )

                    PermissionStatusRow(
                        name = "Akses Notifikasi & Musik",
                        isGranted = true,
                        onClick = {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF161622))
            .border(1.dp, Color(0xFF26263A), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
fun PermissionStatusRow(
    name: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name, color = Color(0xFF8E8E93), fontSize = 12.sp)
        Text(
            text = if (isGranted) "✅ Aktif" else "⚠️ Buka Izin",
            color = if (isGranted) Color(0xFF34C759) else Color(0xFFFF9500),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun toggleDynamicIsland(context: Context, enable: Boolean) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "Berikan izin 'Tampilkan di atas aplikasi lain'", Toast.LENGTH_SHORT).show()
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return
    }

    val intent = Intent(context, DynamicIslandService::class.java)
    if (enable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } else {
        context.stopService(intent)
    }
}

// ============================================================================
// 6. ONBOARDING SCREEN
// ============================================================================
@Composable
fun PochiOnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("Tuan Andre") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PochiMascot(
                state = BotState.IDLE,
                emoteOverride = PochiEmote.HAPPY,
                size = 80.dp,
                showBlackBacking = false
            )

            Text(
                text = "Selamat Datang di Pochi",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "AI Agent pribadi cerdas yang setia mendampingi dan mengingat semua sesi Tuan Andre.",
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Bagaimana Pochi harus memanggil Anda?",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161622))
                    .border(1.dp, Color(0xFF26263A), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF007AFF)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(Color(0xFF007AFF))
                .clickable {
                    PochiPrefs.setUserName(context, nameInput)
                    onComplete()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Mulai Bersama Pochi ✦",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
