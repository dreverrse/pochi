package com.pochi.assistant

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.MediaStore
import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

enum class PochiTab(val title: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Rounded.ChatBubbleOutline),
    TOOLS("Kontrol", Icons.Rounded.Tune),
    MUSIC("Musik", Icons.Rounded.MusicNote),
    WEATHER("Cuaca", Icons.Rounded.CloudQueue),
    NOTES("Notes", Icons.Rounded.Description)
}

@Composable
fun PochiIslandBot(
    onExpandChange: (Boolean) -> Unit,
    onExpandedHeightChange: (Int) -> Unit = {},
    onPowerOffService: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val expandedWidth = (configuration.screenWidthDp - 28).coerceIn(300, 372).dp
    val collapsedSize = 62.dp
    var expandedHeightPx by remember { mutableIntStateOf(0) }
    val expandedHeight = with(density) {
        if (expandedHeightPx > 0) expandedHeightPx.toDp() else 250.dp
    }

    var isExpanded by remember { mutableStateOf(false) }
    var botState by remember { mutableStateOf(BotState.IDLE) }
    var customEmote by remember { mutableStateOf<PochiEmote?>(null) }
    var idleEmote by remember { mutableStateOf<PochiEmote?>(null) }
    var currentTab by remember { mutableStateOf(PochiTab.CHAT) }
    var interactionTick by remember { mutableIntStateOf(0) }

    var prompt by remember { mutableStateOf("") }
    var botReply by remember { mutableStateOf("Halo! Aku Pochi. Ada yang bisa kubantu hari ini?") }
    var pokeCount by remember { mutableIntStateOf(0) }

    var isTorchOn by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf(PochiPrefs.getNotes(context)) }
    var cityInput by remember { mutableStateOf(PochiPrefs.getCity(context)) }
    var liveWeatherText by remember { mutableStateOf("Ketuk Cek Live untuk memuat cuaca.") }

    val isDarkMode by PochiPrefs.darkModeFlow.collectAsState()
    val provider by PochiPrefs.providerFlow.collectAsState()
    val activeApproval by ApprovalManager.activeRequest.collectAsState()
    val lastDecision by ApprovalManager.lastDecision.collectAsState()
    val dismissSignal by ApprovalManager.dismissSignal.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    fun bump() {
        interactionTick++
    }

    fun expand() {
        if (!isExpanded) {
            bump()
            isExpanded = true
            customEmote = null
            idleEmote = null
            onExpandChange(true)
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
            expand()
        }
    }

    LaunchedEffect(lastDecision) {
        lastDecision?.let { decision ->
            botReply = decision
            botState = if (decision.startsWith("Diizinkan")) BotState.DONE else BotState.IDLE
        }
    }

    // Auto-minimize ke lingkaran setelah beberapa detik tanpa interaksi.
    LaunchedEffect(isExpanded, interactionTick, activeApproval, botState) {
        if (isExpanded && activeApproval == null && botState != BotState.WORKING) {
            delay(8000)
            collapse()
        }
    }

    // Ekspresi idle saat minimized, biar terasa hidup seperti AI.
    LaunchedEffect(isExpanded, botState) {
        if (!isExpanded && botState == BotState.IDLE) {
            while (true) {
                delay((4500..8500).random().toLong())
                idleEmote = listOf(
                    PochiEmote.CALM,
                    PochiEmote.THINKING,
                    PochiEmote.HAPPY,
                    PochiEmote.SLEEPY,
                    PochiEmote.NORMAL
                ).random()
            }
        } else if (isExpanded) {
            idleEmote = null
        }
    }

    fun askAi(queryText: String) {
        val currentProvider = PochiPrefs.getProvider(context)
        val apiKey = PochiPrefs.getApiKey(context, currentProvider)
        if (apiKey.isBlank()) {
            botReply = "Isi ${currentProvider.label} API Key dulu di aplikasi Pochi ya!"
            botState = BotState.ANNOYED
            customEmote = PochiEmote.ERROR
            return
        }
        val model = PochiPrefs.getModel(context, currentProvider)
        customEmote = PochiEmote.WORKING
        botState = BotState.WORKING
        bump()
        coroutineScope.launch {
            try {
                val response = AiClient.ask(currentProvider, apiKey, model, queryText)
                botReply = response
                customEmote = PochiEmote.SUCCESS
                botState = BotState.DONE
                delay(2800)
                if (botState == BotState.DONE) {
                    botState = BotState.IDLE
                    customEmote = null
                }
            } catch (e: Exception) {
                botReply = "Kendala AI: ${e.localizedMessage}"
                customEmote = PochiEmote.ERROR
                botState = BotState.ANNOYED
            }
        }
    }

    val transition = updateTransition(targetState = isExpanded, label = "islandMorph")

    val islandWidth by transition.animateDp(
        transitionSpec = { tween(220, easing = FastOutSlowInEasing) },
        label = "islandWidth"
    ) { expanded -> if (expanded) expandedWidth else collapsedSize }

    val islandHeight by transition.animateDp(
        transitionSpec = { tween(220, easing = FastOutSlowInEasing) },
        label = "islandHeight"
    ) { expanded -> if (expanded) expandedHeight else collapsedSize }

    val cornerRadius by transition.animateDp(
        transitionSpec = { tween(220, easing = FastOutSlowInEasing) },
        label = "cornerRadius"
    ) { expanded -> if (expanded) 30.dp else collapsedSize / 2f }

    val panelAlpha by transition.animateFloat(
        transitionSpec = { tween(150, delayMillis = 40) },
        label = "panelAlpha"
    ) { expanded -> if (expanded) 1f else 0f }

    val mascotAlpha by transition.animateFloat(
        transitionSpec = { tween(110) },
        label = "mascotAlpha"
    ) { expanded -> if (expanded) 0f else 1f }

    val panelBg by animateColorAsState(
        targetValue = if (!isExpanded) Color(0xFF09090B)
        else if (isDarkMode) Color(0xFF121215)
        else Color(0xFFFAFBFC),
        animationSpec = tween(180),
        label = "panelBg"
    )

    val surfaceBg = if (isDarkMode) Color(0xFF1E1E24) else Color(0xFFFFFFFF)
    val borderCol by animateColorAsState(
        targetValue = if (!isExpanded) Color(0xFF27272A)
        else if (isDarkMode) Color(0xFF2E2E36) else Color(0xFFE5E7EB),
        animationSpec = tween(220),
        label = "borderCol"
    )
    val textPrimary = if (isDarkMode) Color(0xFFF9FAFB) else Color(0xFF111827)
    val textSecondary = Color(0xFF8E8E93)
    val primaryBlue = Color(0xFF0A84FF)
    val collapsedEmote = customEmote ?: idleEmote ?: botState.defaultEmote
    val currentModel = remember(provider, isExpanded) { PochiPrefs.getModel(context, provider) }

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
            // ================= EXPANDED =================
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .requiredWidth(expandedWidth - 24.dp)
                    .wrapContentHeight(unbounded = true)
                    .onSizeChanged { size ->
                        if (size.height > 0 && size.height != expandedHeightPx) {
                            expandedHeightPx = size.height
                            onExpandedHeightChange(size.height)
                        }
                    }
                    .alpha(panelAlpha)
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PochiMascot(
                            state = botState,
                            emoteOverride = customEmote,
                            size = 24.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = botState.statusLabel,
                                color = textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${provider.label} • $currentModel",
                                color = textSecondary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(surfaceBg)
                                .border(1.dp, borderCol, CircleShape)
                                .clickable { collapse() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Tutup",
                                tint = textPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PochiTab.entries.forEach { tab ->
                            val selected = tab == currentTab
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) primaryBlue.copy(alpha = 0.14f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        bump()
                                        currentTab = tab
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (selected) primaryBlue else textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

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

                    Crossfade(
                        targetState = currentTab,
                        animationSpec = tween(160),
                        label = "tabCrossfade"
                    ) { tab ->
                        when (tab) {
                            PochiTab.CHAT -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PochiMascot(
                                            state = botState,
                                            emoteOverride = customEmote ?: PochiEmote.NORMAL,
                                            size = 64.dp,
                                            onPoke = {
                                                bump()
                                                pokeCount++
                                                if (pokeCount >= 3) {
                                                    botState = BotState.ANNOYED
                                                    customEmote = PochiEmote.ANGRY
                                                    botReply = "Jangan dicolek terus! Pochi lagi fokus."
                                                    coroutineScope.launch {
                                                        delay(2200)
                                                        pokeCount = 0
                                                        botState = BotState.IDLE
                                                        customEmote = null
                                                    }
                                                } else {
                                                    customEmote = PochiEmote.WINK_LEFT
                                                    botReply = "Hei! Ketuk & tahan kepala Pochi kalau mau mengelus ya."
                                                }
                                            },
                                            onPet = {
                                                bump()
                                                botState = BotState.DONE
                                                customEmote = PochiEmote.PET_POKE
                                                botReply = "Nyaman banget dielus! Makasih ya."
                                            }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(surfaceBg)
                                                .border(1.dp, borderCol, RoundedCornerShape(18.dp))
                                                .padding(12.dp)
                                                .heightIn(min = 56.dp, max = 100.dp)
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
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = primaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )

                                        BasicTextField(
                                            value = prompt,
                                            onValueChange = {
                                                prompt = it
                                                bump()
                                                if (botState != BotState.WORKING) botState = BotState.WAITING
                                            },
                                            textStyle = TextStyle(color = textPrimary, fontSize = 13.sp),
                                            cursorBrush = SolidColor(primaryBlue),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { innerTextField ->
                                                if (prompt.isEmpty()) {
                                                    Text(
                                                        text = "Tanya Pochi...",
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
                                                        askAi(q)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Send,
                                                contentDescription = "Kirim",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
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
                                            primaryBlue = primaryBlue,
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
                                        DarkModeTile(
                                            modifier = Modifier.weight(1f),
                                            surfaceBg = surfaceBg,
                                            borderCol = borderCol,
                                            textPrimary = textPrimary,
                                            primaryBlue = primaryBlue,
                                            isDarkMode = isDarkMode,
                                            onToggle = {
                                                bump()
                                                PochiPrefs.setDarkMode(context, it)
                                            }
                                        )

                                        ControlTile(
                                            modifier = Modifier.weight(1f),
                                            surfaceBg = surfaceBg,
                                            borderCol = borderCol,
                                            textPrimary = textPrimary,
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
                                        MusicButton(Icons.Rounded.SkipPrevious, "Prev", textPrimary) {
                                            bump()
                                            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                        }
                                        MusicButton(Icons.Rounded.PlayArrow, "Play", primaryBlue) {
                                            bump()
                                            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                            botState = BotState.DONE
                                        }
                                        MusicButton(Icons.Rounded.SkipNext, "Next", textPrimary) {
                                            bump()
                                            dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(panelBg)
                                                .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    bump()
                                                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                                    am.adjustStreamVolume(
                                                        AudioManager.STREAM_MUSIC,
                                                        AudioManager.ADJUST_LOWER,
                                                        AudioManager.FLAG_SHOW_UI
                                                    )
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.VolumeDown, null, tint = textPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Vol -", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(panelBg)
                                                .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    bump()
                                                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                                    am.adjustStreamVolume(
                                                        AudioManager.STREAM_MUSIC,
                                                        AudioManager.ADJUST_RAISE,
                                                        AudioManager.FLAG_SHOW_UI
                                                    )
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.VolumeUp, null, tint = textPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Vol +", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                        ) {
                                            BasicTextField(
                                                value = cityInput,
                                                onValueChange = {
                                                    cityInput = it
                                                    bump()
                                                    PochiPrefs.saveCity(context, it)
                                                },
                                                textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                                cursorBrush = SolidColor(primaryBlue),
                                                singleLine = true,
                                                decorationBox = { inner ->
                                                    if (cityInput.isEmpty()) {
                                                        Text("Nama kota...", color = textSecondary, fontSize = 12.sp)
                                                    }
                                                    inner()
                                                }
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(primaryBlue)
                                                .clickable {
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
                                                .padding(horizontal = 12.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Rounded.WbSunny, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Text("Cek", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(surfaceBg)
                                                .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    bump()
                                                    currentTab = PochiTab.CHAT
                                                    askAi("Cuaca di ${cityInput.ifBlank { "Jakarta" }} saat ini adalah $liveWeatherText. Berikan saran pakaian dan persiapan singkat dalam 2 kalimat santai.")
                                                }
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, null, tint = primaryBlue, modifier = Modifier.size(14.dp))
                                            Text("Tips", color = primaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                                bump()
                                                PochiPrefs.saveNotes(context, it)
                                            },
                                            textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                            cursorBrush = SolidColor(primaryBlue),
                                            modifier = Modifier.fillMaxSize(),
                                            decorationBox = { inner ->
                                                if (noteText.isEmpty()) {
                                                    Text(
                                                        "Tulis catatan cepat (tersimpan otomatis)...",
                                                        color = textSecondary,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                inner()
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(primaryBlue)
                                                .clickable {
                                                    if (noteText.isNotBlank()) {
                                                        bump()
                                                        currentTab = PochiTab.CHAT
                                                        askAi("Rapikan dan buat daftar to-do singkat dari catatan ini: $noteText")
                                                    }
                                                }
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Rapikan", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(surfaceBg)
                                                .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    bump()
                                                    noteText = ""
                                                    PochiPrefs.saveNotes(context, "")
                                                }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFF453A), modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Hapus", color = Color(0xFFFF453A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
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
                    size = collapsedSize * 0.56f
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 7.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (isTorchOn) Color(0xFFFF9F0A) else botState.accentColor)
                )
            }

            if (!isExpanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { expand() }
                )
            }
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
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceBg)
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
            contentDescription = null,
            tint = primaryBlue,
            modifier = Modifier.size(18.dp)
        )
        Text("Senter", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(
            checked = isTorchOn,
            onCheckedChange = onToggle,
            modifier = Modifier.height(24.dp),
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
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceBg)
            .border(1.dp, borderCol, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.DarkMode,
            contentDescription = null,
            tint = textPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text("Gelap", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(
            checked = isDarkMode,
            onCheckedChange = onToggle,
            modifier = Modifier.height(24.dp),
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
            Icon(imageVector = icon, contentDescription = title, tint = textPrimary, modifier = Modifier.size(18.dp))
            Text(text = title, color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF8E8E93),
            modifier = Modifier.size(18.dp)
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
    } catch (e: Exception) {
        "Gagal memuat cuaca langsung, periksa koneksi internet."
    }
}
