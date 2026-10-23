package com.pochi.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class PochiTab(val title: String, val icon: ImageVector) {
    CHAT("Utama", Icons.Rounded.FlashOn),
    NOTES("Catatan", Icons.Rounded.CheckCircleOutline),
    TOOLS("Kontrol", Icons.Rounded.Tune),
    MUSIC("Media", Icons.Rounded.MusicNote),
    WEATHER("Cuaca", Icons.Rounded.CloudQueue)
}

private fun naturalGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 4..10 -> "Pagi, Dre! Mau cek jadwal, hitung cepat, atau buka sesuatu?"
        in 11..14 -> "Siang, Dre! Ketik aja kalau mau catat sesuatu atau pasang pengingat."
        in 15..18 -> "Sore, Dre! Ada yang perlu diberesin sore ini?"
        else -> "Malam, Dre! Butuh pasang alarm besok atau catat sesuatu?"
    }
}

private fun shortTimeGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) {
        in 4..10 -> "Selamat pagi"
        in 11..14 -> "Selamat siang"
        in 15..18 -> "Selamat sore"
        else -> "Selamat malam"
    }
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    return "$greet • $clock"
}

@Composable
fun PochiIslandBot(
    onExpandChange: (Boolean) -> Unit,
    onExpandedHeightChange: (Int) -> Unit = {},
    isHidden: Boolean = false,
    onRestoreFromHidden: () -> Unit = {},
    onDragBubble: (Float, Float) -> Unit = { _, _ -> },
    onDragEnd: () -> Unit = {},
    onPowerOffService: () -> Unit = {},
    peekMode: Boolean = false,
    peekSide: String = "right"
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val expandedWidth = (configuration.screenWidthDp - 24).coerceIn(308, 384).dp
    val collapsedSize = 54.dp
    var expandedHeightPx by remember { mutableIntStateOf(0) }
    val expandedHeight = with(density) {
        if (expandedHeightPx > 0) expandedHeightPx.toDp() else 270.dp
    }

    var isExpanded by remember { mutableStateOf(false) }
    var botState by remember { mutableStateOf(BotState.IDLE) }
    var customEmote by remember { mutableStateOf<PochiEmote?>(null) }
    var idleEmote by remember { mutableStateOf<PochiEmote?>(PochiEmote.SMILING) }
    var currentTab by remember { mutableStateOf(PochiTab.CHAT) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var lastActiveTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var prompt by remember { mutableStateOf("") }
    var botReply by remember { mutableStateOf(naturalGreeting()) }
    var pokeCount by remember { mutableIntStateOf(0) }
    var screenCardDismissed by remember { mutableStateOf(false) }

    var isTorchOn by remember { mutableStateOf(false) }
    val savedNotes by PochiPrefs.notesFlow.collectAsState()
    var newNoteItem by remember { mutableStateOf("") }
    var isRawNoteEdit by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf(PochiPrefs.getCity(context)) }
    var liveWeatherText by remember { mutableStateOf("Ketuk Cek untuk memuat cuaca terkini.") }

    val isDarkMode by PochiPrefs.darkModeFlow.collectAsState()
    val activeApproval by ApprovalManager.activeRequest.collectAsState()
    val lastDecision by ApprovalManager.lastDecision.collectAsState()
    val dismissSignal by ApprovalManager.dismissSignal.collectAsState()
    val screenAwarenessEnabled by PochiPrefs.screenAwarenessFlow.collectAsState()
    val screenApp by PochiSignals.screenApp.collectAsState()
    val screenText by PochiSignals.screenText.collectAsState()
    val notifications by PochiSignals.notifications.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(screenApp) {
        screenCardDismissed = false
    }

    fun bump() {
        interactionTick++
        lastActiveTime = System.currentTimeMillis()
    }

    fun expand() {
        if (!isExpanded) {
            bump()
            isExpanded = true
            customEmote = PochiEmote.HAPPY
            onExpandChange(true)
            coroutineScope.launch {
                delay(1600)
                if (customEmote == PochiEmote.HAPPY && botState == BotState.IDLE) {
                    customEmote = null
                }
            }
        }
    }

    fun collapse() {
        if (isExpanded) {
            isExpanded = false
            customEmote = null
            if (activeApproval == null) botState = BotState.IDLE
            onExpandChange(false)
        }
    }

    LaunchedEffect(dismissSignal) {
        if (dismissSignal > 0L && isExpanded) collapse()
    }

    LaunchedEffect(activeApproval) {
        if (activeApproval != null) {
            botState = BotState.APPROVAL
            customEmote = PochiEmote.SURPRISED
            expand()
        }
    }

    LaunchedEffect(lastDecision) {
        lastDecision?.let { decision ->
            botReply = decision
            val approved = decision.startsWith("Diizinkan")
            botState = if (approved) BotState.DONE else BotState.IDLE
            customEmote = if (approved) PochiEmote.SUCCESS else PochiEmote.NO
        }
    }

    // Auto-minimize setelah tidak ada interaksi selama 12 detik
    LaunchedEffect(isExpanded, interactionTick, activeApproval, botState, prompt, newNoteItem) {
        if (isExpanded && activeApproval == null && botState != BotState.WORKING &&
            prompt.isBlank() && newNoteItem.isBlank()
        ) {
            delay(12_000)
            collapse()
        }
    }

    // Mood otonom Pochi (berganti-ganti dari 36 ekspresi sesuai situasi, waktu, & lama dianggurin)
    LaunchedEffect(botState, isHidden, peekMode) {
        if (botState == BotState.IDLE) {
            while (true) {
                delay((4200..7500).random().toLong())
                val idleDuration = System.currentTimeMillis() - lastActiveTime
                idleEmote = PochiMood.nextAutonomousMood(
                    idleMillis = idleDuration,
                    isPeeking = isHidden && peekMode
                )
            }
        }
    }

    // Siklus ekspresi berpikir saat sedang memproses jawaban
    LaunchedEffect(botState) {
        if (botState == BotState.WORKING) {
            val thinkingCycle = listOf(
                PochiEmote.THINKING,
                PochiEmote.WORKING,
                PochiEmote.HMM,
                PochiEmote.LOADING,
                PochiEmote.FOCUS
            )
            var idx = 0
            while (botState == BotState.WORKING) {
                customEmote = thinkingCycle[idx % thinkingCycle.size]
                idx++
                delay(1100)
            }
        }
    }

    fun handleAction(action: AgentAction) {
        if (PochiAgent.requiresApproval(action)) {
            ApprovalManager.requestAction(context, action)
        } else {
            val status = PochiAgent.execute(context, action)
            if (action.type == "TORCH") {
                isTorchOn = !action.argument.equals("OFF", ignoreCase = true)
            }
            if (action.type in setOf("OPEN_APP", "MAPS", "SEARCH_WEB", "YOUTUBE", "CALENDAR", "SETTINGS")) {
                collapse()
            } else if (botReply.isBlank()) {
                botReply = status
            }
        }
    }

    fun submitInput(queryText: String, forceAi: Boolean = false) {
        val trimmed = queryText.trim()
        if (trimmed.isEmpty()) return
        bump()

        // 1. Coba eksekusi lokal secara instan (0ms, tanpa perlu menunggu internet/API)
        if (!forceAi) {
            val localMatch = PochiAgent.tryHandleLocal(context, trimmed)
            if (localMatch != null) {
                val (replyText, localAction) = localMatch
                botReply = replyText
                val moodEmote = PochiMood.inferEmoteFromText(trimmed, replyText)
                customEmote = moodEmote
                botState = if (moodEmote in setOf(PochiEmote.ANGRY, PochiEmote.CRYING, PochiEmote.ANNOYED)) {
                    BotState.ANNOYED
                } else {
                    BotState.DONE
                }
                if (localAction != null) {
                    handleAction(localAction)
                }
                coroutineScope.launch {
                    delay(3500)
                    if (botState != BotState.WORKING) {
                        botState = BotState.IDLE
                        customEmote = null
                    }
                }
                return
            }
        }

        // 2. Gunakan koneksi asisten pintar dengan konteks lengkap
        val currentProvider = PochiPrefs.getProvider(context)
        val apiKey = PochiPrefs.getApiKey(context, currentProvider)
        if (apiKey.isBlank()) {
            botReply = "Untuk ngobrol bebas, isi kunci ${currentProvider.label} di aplikasi Pochi dulu ya. (Perintah seperti 'timer 5 menit', '45rb * 3', 'catat ...', 'buka wa' tetap bisa langsung dipakai!)"
            botState = BotState.ANNOYED
            customEmote = PochiEmote.CONFUSED
            return
        }

        val model = PochiPrefs.getModel(context, currentProvider)
        val contextBuilder = buildString {
            if (screenAwarenessEnabled && screenApp.isNotBlank()) {
                append("Aplikasi aktif: $screenApp. ")
                if (screenText.isNotBlank()) {
                    append("Teks di layar: ${screenText.take(350)}. ")
                }
            }
            if (notifications.isNotEmpty()) {
                val latestNotifs = notifications.take(4)
                    .joinToString("; ") { "${it.app} (${it.title}: ${it.text.take(60)})" }
                append("Notifikasi terbaru: $latestNotifs. ")
            }
            if (savedNotes.isNotBlank()) {
                append("Catatan tersimpan: ${savedNotes.take(200)}.")
            }
        }

        customEmote = PochiEmote.THINKING
        botState = BotState.WORKING
        coroutineScope.launch {
            try {
                val rawResponse = AiClient.ask(
                    provider = currentProvider,
                    apiKey = apiKey,
                    model = model,
                    prompt = trimmed,
                    contextNote = contextBuilder
                )
                val (replyAfterAction, action) = PochiAgent.extract(rawResponse)
                val (cleanReply, chosenEmote) = PochiAgent.extractEmote(replyAfterAction, trimmed)
                botReply = cleanReply
                if (action != null) {
                    handleAction(action)
                }
                customEmote = chosenEmote
                botState = when (chosenEmote) {
                    PochiEmote.ANGRY, PochiEmote.CRYING, PochiEmote.ANNOYED,
                    PochiEmote.SUSPICIOUS, PochiEmote.SCARED, PochiEmote.ERROR -> BotState.ANNOYED
                    else -> BotState.DONE
                }
                delay(4500)
                if (botState != BotState.WORKING) {
                    botState = BotState.IDLE
                    customEmote = null
                }
            } catch (e: Exception) {
                botReply = "Aduh, koneksiku lagi gangguan nih: ${e.localizedMessage?.take(100)}"
                customEmote = PochiEmote.ERROR
                botState = BotState.ANNOYED
            }
        }
    }

    val transition = updateTransition(targetState = isExpanded, label = "islandMorph")

    val islandWidth by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "islandWidth"
    ) { expanded -> if (expanded) expandedWidth else collapsedSize }

    val islandHeight by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "islandHeight"
    ) { expanded -> if (expanded) expandedHeight else collapsedSize }

    val cornerRadius by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "cornerRadius"
    ) { expanded -> if (expanded) 28.dp else collapsedSize / 2f }

    val panelAlpha by transition.animateFloat(
        transitionSpec = { tween(150, delayMillis = if (targetState) 35 else 0) },
        label = "panelAlpha"
    ) { expanded -> if (expanded) 1f else 0f }

    val mascotAlpha by transition.animateFloat(
        transitionSpec = { tween(110) },
        label = "mascotAlpha"
    ) { expanded -> if (expanded) 0f else 1f }

    val panelBg by animateColorAsState(
        targetValue = if (!isExpanded) Color.Transparent
        else if (isDarkMode) Color(0xFF111114)
        else Color(0xFFF8FAFC),
        animationSpec = tween(180),
        label = "panelBg"
    )

    val surfaceBg = if (isDarkMode) Color(0xFF1C1C22) else Color(0xFFFFFFFF)
    val subtleBg = if (isDarkMode) Color(0xFF25252D) else Color(0xFFF1F5F9)
    val borderCol by animateColorAsState(
        targetValue = if (!isExpanded) Color.Transparent
        else if (isDarkMode) Color(0xFF2B2B35) else Color(0xFFE2E8F0),
        animationSpec = tween(200),
        label = "borderCol"
    )
    val textPrimary = if (isDarkMode) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val textSecondary = if (isDarkMode) Color(0xFF94A3B8) else Color(0xFF64748B)
    val primaryAccent = Color(0xFF0A84FF)
    val collapsedEmote = customEmote ?: idleEmote ?: botState.defaultEmote

    if (isHidden) {
        val restShift = with(density) { (if (peekSide == "left") (-14).dp else 14.dp).toPx() }
        val peekShift = with(density) { (if (peekSide == "left") (-6).dp else 6.dp).toPx() }
        val peekOffset = remember(peekMode, peekSide) {
            Animatable(restShift)
        }
        LaunchedEffect(peekMode, peekSide) {
            if (peekMode) {
                while (true) {
                    peekOffset.animateTo(
                        targetValue = peekShift,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f)
                    )
                    delay(1200)
                    peekOffset.animateTo(
                        targetValue = restShift,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 220f)
                    )
                    delay(4200)
                }
            } else {
                peekOffset.snapTo(restShift)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    onRestoreFromHidden()
                },
            contentAlignment = if (peekSide == "left") Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = peekOffset.value
                        rotationZ = if (peekSide == "left") 16f else -16f
                    },
                contentAlignment = Alignment.Center
            ) {
                PochiMascot(
                    state = botState,
                    emoteOverride = collapsedEmote,
                    size = 50.dp
                )
            }
        }
        return
    }

    val headerSubtext = remember(screenAwarenessEnabled, screenApp, notifications.size, isExpanded) {
        when {
            screenAwarenessEnabled && screenApp.isNotBlank() -> "Layar aktif: $screenApp"
            notifications.isNotEmpty() -> "${notifications.size} notifikasi masuk"
            else -> shortTimeGreeting()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .width(islandWidth)
                .height(islandHeight)
                .clip(RoundedCornerShape(cornerRadius))
                .background(panelBg)
                .border(1.dp, borderCol, RoundedCornerShape(cornerRadius))
        ) {
            // ================= EXPANDED PANEL =================
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .requiredWidth(expandedWidth - 20.dp)
                    .wrapContentHeight(unbounded = true)
                    .onSizeChanged { size ->
                        if (size.height > 0 && size.height != expandedHeightPx) {
                            expandedHeightPx = size.height
                            onExpandedHeightChange(size.height)
                        }
                    }
                    .alpha(panelAlpha)
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PochiMascot(
                            state = botState,
                            emoteOverride = collapsedEmote,
                            size = 26.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pochi • ${collapsedEmote.title}",
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = headerSubtext,
                                color = textSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(subtleBg)
                                .clickable { collapse() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Tutup",
                                tint = textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Pill Navigation Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(subtleBg)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PochiTab.entries.forEach { tab ->
                            val selected = tab == currentTab
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(if (selected) surfaceBg else Color.Transparent)
                                    .clickable {
                                        bump()
                                        currentTab = tab
                                        customEmote = when (tab) {
                                            PochiTab.MUSIC -> PochiEmote.EXCITED
                                            PochiTab.NOTES -> PochiEmote.FOCUS
                                            PochiTab.WEATHER -> PochiEmote.HMM
                                            PochiTab.CONTROLS -> PochiEmote.WINK_LEFT
                                            PochiTab.CHAT -> null
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (selected) primaryAccent else textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = tab.title,
                                    color = if (selected) textPrimary else textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Approval Card (jika ada tindakan sensitif yang butuh konfirmasi)
                    activeApproval?.let { request ->
                        PochiApprovalCard(
                            request = request,
                            isDark = isDarkMode,
                            onDeny = {
                                bump()
                                ApprovalManager.confirmDeny()
                                botState = BotState.IDLE
                            }
                        )
                    }

                    // Konten per Tab
                    Crossfade(
                        targetState = currentTab,
                        animationSpec = tween(150),
                        label = "tabCrossfade"
                    ) { tab ->
                        when (tab) {
                            PochiTab.CHAT -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Notifikasi Masuk (jika ada)
                                    if (notifications.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(surfaceBg)
                                                .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.NotificationsNone,
                                                contentDescription = null,
                                                tint = primaryAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = notifications.first().let { "${it.app}: ${it.title}" },
                                                    color = textPrimary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = notifications.first().text,
                                                    color = textSecondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            QuickActionBadge("Intisari", primaryAccent) {
                                                val summary = notifications.take(8)
                                                    .joinToString("; ") { "${it.app} - ${it.title}: ${it.text.take(80)}" }
                                                submitInput("Rangkum singkat notifikasi yang masuk ini dan beri tahu mana yang paling penting: $summary", forceAi = true)
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Bersihkan",
                                                tint = textSecondary,
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { PochiSignals.clearNotifications() }
                                            )
                                        }
                                    }

                                    // Balon Pesan + Maskot Interaktif
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PochiMascot(
                                            state = botState,
                                            emoteOverride = customEmote ?: idleEmote ?: botState.defaultEmote,
                                            size = 54.dp,
                                            onPoke = {
                                                bump()
                                                pokeCount++
                                                val (emote, state, text) = PochiMood.onPokeReaction(pokeCount)
                                                botState = state
                                                customEmote = emote
                                                botReply = text
                                                coroutineScope.launch {
                                                    val currentPoke = pokeCount
                                                    delay(3800)
                                                    if (pokeCount == currentPoke && botState != BotState.WORKING) {
                                                        if (pokeCount >= 5) pokeCount = 0
                                                        botState = BotState.IDLE
                                                        customEmote = null
                                                    }
                                                }
                                            },
                                            onPet = {
                                                bump()
                                                val wasUpset = pokeCount >= 3 || botState == BotState.ANNOYED ||
                                                    customEmote in setOf(
                                                        PochiEmote.ANGRY,
                                                        PochiEmote.CRYING,
                                                        PochiEmote.ANNOYED,
                                                        PochiEmote.SCARED
                                                    )
                                                pokeCount = 0
                                                val (emote, state, text) = PochiMood.onPetReaction(wasUpset)
                                                botState = state
                                                customEmote = emote
                                                botReply = text
                                                coroutineScope.launch {
                                                    delay(3800)
                                                    if (botState == BotState.DONE) {
                                                        botState = BotState.IDLE
                                                        customEmote = null
                                                    }
                                                }
                                            }
                                        )

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(surfaceBg)
                                                .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 38.dp, max = 92.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                Text(
                                                    text = botReply,
                                                    color = textPrimary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            // Tombol Aksi Cepat pada Respons (Salin & Simpan ke Catatan)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(subtleBg)
                                                        .clickable {
                                                            bump()
                                                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cb.setPrimaryClip(ClipData.newPlainText("Pochi", botReply))
                                                            Toast.makeText(context, "Teks disalin", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Rounded.ContentCopy, null, tint = textSecondary, modifier = Modifier.size(11.dp))
                                                    Text("Salin", color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(subtleBg)
                                                        .clickable {
                                                            bump()
                                                            PochiAgent.execute(context, AgentAction("SAVE_NOTE", botReply))
                                                            Toast.makeText(context, "Disimpan ke catatan", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Rounded.BookmarkBorder, null, tint = textSecondary, modifier = Modifier.size(11.dp))
                                                    Text("+ Catatan", color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }

                                    // Konteks Layar Aktif (Screen Awareness)
                                    if (screenAwarenessEnabled && screenApp.isNotBlank() &&
                                        screenText.isNotBlank() && !screenCardDismissed
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(surfaceBg)
                                                .border(1.dp, primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                                                .padding(horizontal = 10.dp, vertical = 7.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Konteks layar: $screenApp",
                                                    color = textPrimary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Tutup",
                                                    tint = textSecondary,
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clickable { screenCardDismissed = true }
                                                )
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                QuickActionBadge("Ringkas layar", primaryAccent) {
                                                    submitInput("Ringkas isi layar $screenApp ini jadi 2 kalimat jelas: ${screenText.take(450)}", forceAi = true)
                                                }
                                                QuickActionBadge("Bantu balas", primaryAccent) {
                                                    submitInput("Buatkan draf balasan singkat yang natural dan sopan untuk percakapan di layar $screenApp ini, lalu salin ke clipboard dengan [ACTION:CLIPBOARD:isi_balasan]. Isi layar: ${screenText.take(450)}", forceAi = true)
                                                }
                                                QuickActionBadge("Terjemahkan", primaryAccent) {
                                                    submitInput("Terjemahkan teks di layar ini ke bahasa Indonesia yang natural: ${screenText.take(450)}", forceAi = true)
                                                }
                                            }
                                        }
                                    } else {
                                        // Deretan Chip Pintasan Harian
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ShortcutChip("⏱️ Timer 5m", subtleBg, textPrimary) {
                                                submitInput("timer 5 menit")
                                            }
                                            ShortcutChip("📋 Cek Clipboard", subtleBg, textPrimary) {
                                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clipText = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                                if (clipText.isBlank()) {
                                                    botReply = "Clipboard masih kosong."
                                                } else {
                                                    submitInput("Ini isi clipboard-ku: \"$clipText\". Jelaskan singkat atau rapikan isinya.", forceAi = true)
                                                }
                                            }
                                            ShortcutChip("💬 Buka WA", subtleBg, textPrimary) {
                                                submitInput("buka whatsapp")
                                            }
                                            ShortcutChip("🔦 Senter", subtleBg, textPrimary) {
                                                submitInput(if (isTorchOn) "matikan senter" else "nyalakan senter")
                                            }
                                            ShortcutChip("📝 Catatan", subtleBg, textPrimary) {
                                                bump()
                                                currentTab = PochiTab.NOTES
                                            }
                                        }
                                    }

                                    // Kolom Input Perintah / Hitung / Catat / Tanya
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
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = null,
                                            tint = textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )

                                        BasicTextField(
                                            value = prompt,
                                            onValueChange = {
                                                prompt = it
                                                bump()
                                                if (botState != BotState.WORKING) {
                                                    botState = if (it.isBlank()) BotState.IDLE else BotState.WAITING
                                                    customEmote = when {
                                                        it.isBlank() -> null
                                                        it.endsWith("?") -> PochiEmote.HMM
                                                        it.endsWith("!") -> PochiEmote.SURPRISED
                                                        else -> PochiEmote.FOCUS
                                                    }
                                                }
                                            },
                                            textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                            cursorBrush = SolidColor(primaryAccent),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(
                                                onSend = {
                                                    if (prompt.isNotBlank() && botState != BotState.WORKING) {
                                                        val q = prompt
                                                        prompt = ""
                                                        submitInput(q)
                                                    }
                                                }
                                            ),
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { innerTextField ->
                                                if (prompt.isEmpty()) {
                                                    Text(
                                                        text = "Ketik perintah, 45rb*3, catat..., atau tanya",
                                                        color = textSecondary,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(if (prompt.isNotBlank()) primaryAccent else subtleBg)
                                                .clickable {
                                                    if (prompt.isNotBlank() && botState != BotState.WORKING) {
                                                        val q = prompt
                                                        prompt = ""
                                                        submitInput(q)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowUpward,
                                                contentDescription = "Jalankan",
                                                tint = if (prompt.isNotBlank()) Color.White else textSecondary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            PochiTab.NOTES -> {
                                val noteLines = remember(savedNotes) {
                                    savedNotes.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Bar tambah item cepat
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(surfaceBg)
                                            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BasicTextField(
                                            value = newNoteItem,
                                            onValueChange = {
                                                newNoteItem = it
                                                bump()
                                            },
                                            textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                            cursorBrush = SolidColor(primaryAccent),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                if (newNoteItem.isEmpty()) {
                                                    Text("Tambah tugas atau catatan...", color = textSecondary, fontSize = 11.sp)
                                                }
                                                inner()
                                            }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(primaryAccent)
                                                .clickable {
                                                    if (newNoteItem.isNotBlank()) {
                                                        bump()
                                                        PochiAgent.execute(context, AgentAction("SAVE_NOTE", newNoteItem))
                                                        newNoteItem = ""
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text("+ Tambah", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (isRawNoteEdit) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(90.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(surfaceBg)
                                                .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                                                .padding(10.dp)
                                        ) {
                                            BasicTextField(
                                                value = savedNotes,
                                                onValueChange = {
                                                    bump()
                                                    PochiPrefs.saveNotes(context, it)
                                                },
                                                textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                                cursorBrush = SolidColor(primaryAccent),
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 56.dp, max = 115.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(surfaceBg)
                                                .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                                                .padding(8.dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            if (noteLines.isEmpty()) {
                                                Text(
                                                    text = "Belum ada catatan. Ketik di atas atau gunakan perintah 'catat ...'",
                                                    color = textSecondary,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(4.dp)
                                                )
                                            } else {
                                                noteLines.forEachIndexed { idx, line ->
                                                    val isDone = line.startsWith("[x] ")
                                                    val display = line
                                                        .removePrefix("[x] ")
                                                        .removePrefix("• ")
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                bump()
                                                                val updated = noteLines.toMutableList()
                                                                updated[idx] = if (isDone) "• $display" else "[x] $display"
                                                                PochiPrefs.saveNotes(context, updated.joinToString("\n"))
                                                            }
                                                            .padding(horizontal = 4.dp, vertical = 3.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                                            contentDescription = null,
                                                            tint = if (isDone) Color(0xFF30D158) else textSecondary,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Text(
                                                            text = display,
                                                            color = if (isDone) textSecondary else textPrimary,
                                                            fontSize = 11.sp,
                                                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                                                            modifier = Modifier.weight(1f),
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ActionChipButton(
                                            modifier = Modifier.weight(1f),
                                            label = if (isRawNoteEdit) "Mode Daftar" else "Edit Teks",
                                            bg = subtleBg,
                                            textColor = textPrimary
                                        ) {
                                            bump()
                                            isRawNoteEdit = !isRawNoteEdit
                                        }
                                        ActionChipButton(
                                            modifier = Modifier.weight(1f),
                                            label = "Rapikan",
                                            bg = primaryAccent.copy(alpha = 0.14f),
                                            textColor = primaryAccent
                                        ) {
                                            if (savedNotes.isNotBlank()) {
                                                bump()
                                                currentTab = PochiTab.CHAT
                                                submitInput("Rapikan catatan ini menjadi urutan prioritas yang singkat dan jelas: $savedNotes", forceAi = true)
                                            }
                                        }
                                        ActionChipButton(
                                            modifier = Modifier.weight(0.8f),
                                            label = "Bersihkan",
                                            bg = subtleBg,
                                            textColor = Color(0xFFFF453A)
                                        ) {
                                            bump()
                                            PochiPrefs.saveNotes(context, "")
                                        }
                                    }
                                }
                            }

                            PochiTab.TOOLS -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TorchTile(
                                            modifier = Modifier.weight(1f),
                                            surfaceBg = surfaceBg,
                                            borderCol = borderCol,
                                            textPrimary = textPrimary,
                                            primaryBlue = primaryAccent,
                                            isTorchOn = isTorchOn,
                                            onToggle = { checked ->
                                                bump()
                                                try {
                                                    val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                                                    val camId = camManager.cameraIdList.firstOrNull()
                                                    if (camId != null) {
                                                        isTorchOn = checked
                                                        camManager.setTorchMode(camId, isTorchOn)
                                                        botState = if (isTorchOn) BotState.DONE else BotState.IDLE
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        )

                                        DarkModeTile(
                                            modifier = Modifier.weight(1f),
                                            surfaceBg = surfaceBg,
                                            borderCol = borderCol,
                                            textPrimary = textPrimary,
                                            primaryBlue = primaryAccent,
                                            isDarkMode = isDarkMode,
                                            onToggle = {
                                                bump()
                                                PochiPrefs.setDarkMode(context, it)
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ControlTile(
                                            modifier = Modifier.weight(1f),
                                            surfaceBg = surfaceBg,
                                            borderCol = borderCol,
                                            textPrimary = textPrimary,
                                            icon = Icons.Rounded.PhotoCamera,
                                            title = "Kamera"
                                        ) {
                                            bump()
                                            try {
                                                val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                                collapse()
                                            } catch (_: Exception) {
                                                val fallback = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(fallback)
                                            }
                                        }

                                        ControlTile(
                                            modifier = Modifier.weight(1f),
                                            surfaceBg = surfaceBg,
                                            borderCol = borderCol,
                                            textPrimary = textPrimary,
                                            icon = Icons.Rounded.Wifi,
                                            title = "Wi-Fi"
                                        ) {
                                            bump()
                                            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            collapse()
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ControlTile(
                                            modifier = Modifier.weight(1f),
                                            surfaceBg = surfaceBg,
                                            borderCol = borderCol,
                                            textPrimary = textPrimary,
                                            icon = Icons.Rounded.Alarm,
                                            title = "Jam & Alarm"
                                        ) {
                                            bump()
                                            runCatching {
                                                val intent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                                collapse()
                                            }
                                        }

                                        ControlTile(
                                            modifier = Modifier.weight(1f),
                                            surfaceBg = surfaceBg,
                                            borderCol = borderCol,
                                            textPrimary = Color(0xFFFF453A),
                                            icon = Icons.Rounded.PowerSettingsNew,
                                            title = "Matikan"
                                        ) {
                                            onPowerOffService()
                                        }
                                    }
                                }
                            }

                            PochiTab.MUSIC -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(surfaceBg)
                                        .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MusicButton(Icons.Rounded.SkipPrevious, "Sebelumnya", textPrimary) {
                                            bump()
                                            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                        }
                                        MusicButton(Icons.Rounded.PlayArrow, "Putar/Jeda", primaryAccent) {
                                            bump()
                                            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                            botState = BotState.DONE
                                        }
                                        MusicButton(Icons.Rounded.SkipNext, "Berikutnya", textPrimary) {
                                            bump()
                                            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ActionChipButton(
                                            modifier = Modifier.weight(1f),
                                            label = "Vol -",
                                            bg = subtleBg,
                                            textColor = textPrimary
                                        ) {
                                            bump()
                                            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                                        }
                                        ActionChipButton(
                                            modifier = Modifier.weight(1f),
                                            label = "Vol +",
                                            bg = subtleBg,
                                            textColor = textPrimary
                                        ) {
                                            bump()
                                            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                                        }
                                        ActionChipButton(
                                            modifier = Modifier.weight(1f),
                                            label = "Spotify",
                                            bg = primaryAccent.copy(alpha = 0.14f),
                                            textColor = primaryAccent
                                        ) {
                                            bump()
                                            PochiAgent.execute(context, AgentAction("OPEN_APP", "spotify"))
                                            collapse()
                                        }
                                    }
                                }
                            }

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
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            BasicTextField(
                                                value = cityInput,
                                                onValueChange = {
                                                    cityInput = it
                                                    bump()
                                                    PochiPrefs.saveCity(context, it)
                                                },
                                                textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                                cursorBrush = SolidColor(primaryAccent),
                                                singleLine = true,
                                                decorationBox = { inner ->
                                                    if (cityInput.isEmpty()) {
                                                        Text("Nama kota...", color = textSecondary, fontSize = 12.sp)
                                                    }
                                                    inner()
                                                }
                                            )
                                        }

                                        ActionChipButton(
                                            modifier = Modifier.width(68.dp),
                                            label = "Cek",
                                            bg = primaryAccent,
                                            textColor = Color.White
                                        ) {
                                            bump()
                                            val city = cityInput.ifBlank { "Jakarta" }
                                            botState = BotState.WORKING
                                            coroutineScope.launch {
                                                val result = fetchLiveWeather(city)
                                                liveWeatherText = result
                                                botReply = "Cuaca $city: $result"
                                                botState = BotState.DONE
                                            }
                                        }

                                        ActionChipButton(
                                            modifier = Modifier.width(72.dp),
                                            label = "Tips",
                                            bg = subtleBg,
                                            textColor = primaryAccent
                                        ) {
                                            bump()
                                            currentTab = PochiTab.CHAT
                                            submitInput("Cuaca di ${cityInput.ifBlank { "Jakarta" }} saat ini $liveWeatherText. Kasih saran persiapan singkat buat keluar rumah.", forceAi = true)
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
                                        Text(
                                            text = liveWeatherText,
                                            color = textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================= MINI: lingkaran maskot =================
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(collapsedSize)
                    .alpha(mascotAlpha),
                contentAlignment = Alignment.Center
            ) {
                PochiMascot(
                    state = botState,
                    emoteOverride = collapsedEmote,
                    size = collapsedSize * 0.76f
                )
            }

            if (!isExpanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDragBubble(dragAmount.x, dragAmount.y)
                                },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() }
                            )
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { expand() }
                )
            }
        }
    }
}

@Composable
private fun QuickActionBadge(label: String, accent: Color, onClick: () -> Unit) {
    Text(
        text = label,
        color = accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
private fun ShortcutChip(label: String, bg: Color, textColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ActionChipButton(
    modifier: Modifier = Modifier,
    label: String,
    bg: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun TorchTile(
    modifier: Modifier = Modifier,
    surfaceBg: Color,
    borderCol: Color,
    textPrimary: Color,
    primaryBlue: Color,
    isTorchOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceBg)
            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
            contentDescription = null,
            tint = primaryBlue,
            modifier = Modifier.size(17.dp)
        )
        Text("Senter", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(
            checked = isTorchOn,
            onCheckedChange = onToggle,
            modifier = Modifier.height(22.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = primaryBlue
            )
        )
    }
}

@Composable
private fun DarkModeTile(
    modifier: Modifier = Modifier,
    surfaceBg: Color,
    borderCol: Color,
    textPrimary: Color,
    primaryBlue: Color,
    isDarkMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceBg)
            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.DarkMode,
            contentDescription = null,
            tint = textPrimary,
            modifier = Modifier.size(17.dp)
        )
        Text("Gelap", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(
            checked = isDarkMode,
            onCheckedChange = onToggle,
            modifier = Modifier.height(22.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = primaryBlue
            )
        )
    }
}

@Composable
private fun ControlTile(
    modifier: Modifier = Modifier,
    surfaceBg: Color,
    borderCol: Color,
    textPrimary: Color,
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceBg)
            .border(1.dp, borderCol, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = textPrimary, modifier = Modifier.size(17.dp))
            Text(text = title, color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun MusicButton(icon: ImageVector, label: String, tintColor: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tintColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, color = tintColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
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
    } catch (_: Exception) {
        "Gagal memuat cuaca langsung, periksa koneksi internet."
    }
}
