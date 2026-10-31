package com.pochi.assistant

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// ============================================================================
// SIMPLE & COOL BLUE-BLACK GRADIENT THEME PALETTE (NO PURPLE, NO EMOJI)
// ============================================================================
private val BgDeepBlack = Color(0xFF03060C)
private val BgNavyDark = Color(0xFF070E1E)
private val CardSurface = Color(0xFF0A1326)
private val CardSurfaceElevated = Color(0xFF0F1D38)
private val BorderSubtleBlue = Color(0xFF1E3A8A)
private val PrimaryRoyalBlue = Color(0xFF1D4ED8)
private val AccentElectricBlue = Color(0xFF2563EB)
private val AccentSkyBlue = Color(0xFF38BDF8)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMutedBlue = Color(0xFF8DA2C0)

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_PICK_FILE = "com.pochi.assistant.ACTION_PICK_FILE"
        const val EXTRA_MIME_TYPE = "extra_mime_type"
    }

    private val pendingPickMime = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PochiPrefs.init(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handlePickIntent(intent)

        if (Settings.canDrawOverlays(this)) {
            DynamicIslandService.start(this)
        }

        setContent {
            val scheme = darkColorScheme(
                primary = PrimaryRoyalBlue,
                secondary = AccentSkyBlue,
                background = BgDeepBlack,
                surface = CardSurface,
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )
            MaterialTheme(colorScheme = scheme) {
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    val wasFromIsland = pendingPickMime.value != null
                    pendingPickMime.value = null
                    if (uri != null) {
                        val info = PochiJarvisEngine.inspectUri(this@MainActivity, uri)
                        PochiSignals.publishDroppedFile(info)
                        if (wasFromIsland) {
                            PochiSignals.requestExpandIsland()
                            moveTaskToBack(true)
                        }
                    }
                }

                val reqMime by pendingPickMime
                LaunchedEffect(reqMime) {
                    val m = reqMime
                    if (!m.isNullOrBlank()) {
                        runCatching { filePickerLauncher.launch(m) }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDeepBlack
                ) {
                    PochiSimpleCoolApp(
                        onLaunchFilePicker = { mime ->
                            runCatching { filePickerLauncher.launch(mime) }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePickIntent(intent)
    }

    private fun handlePickIntent(intent: Intent?) {
        if (intent?.action == ACTION_PICK_FILE) {
            pendingPickMime.value = intent.getStringExtra(EXTRA_MIME_TYPE) ?: "*/*"
        }
    }
}

@Composable
fun PochiSimpleCoolApp(
    onLaunchFilePicker: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var onboarded by remember { mutableStateOf(PochiPrefs.onboardingDoneFlow.value) }
    var selectedTab by remember { mutableIntStateOf(0) }

    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibility by remember { mutableStateOf(PochiAccessibilityService.instance != null) }
    var hasNotification by remember {
        mutableStateOf(
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.contains(context.packageName) == true
        )
    }
    var batteryIgnored by remember {
        mutableStateOf(
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canOverlay = Settings.canDrawOverlays(context)
                hasAccessibility = PochiAccessibilityService.instance != null
                hasNotification = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                    ?.contains(context.packageName) == true
                batteryIgnored = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.isIgnoringBatteryOptimizations(context.packageName) == true
                if (canOverlay && !DynamicIslandService.isRunningFlow.value) {
                    DynamicIslandService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!onboarded) {
        PochiOnboardingScreen(
            canOverlay = canOverlay,
            hasAccessibility = hasAccessibility,
            hasNotification = hasNotification,
            onFinish = {
                PochiPrefs.setOnboardingDone(context, true)
                onboarded = true
                if (Settings.canDrawOverlays(context)) {
                    DynamicIslandService.start(context)
                }
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        BgNavyDark,
                        BgDeepBlack,
                        BgDeepBlack
                    )
                )
            )
    ) {
        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(220),
            label = "tabFade"
        ) { tab ->
            when (tab) {
                0 -> PochiHomeChatTab(
                    canOverlay = canOverlay,
                    onLaunchFilePicker = onLaunchFilePicker,
                    onOpenSettings = { selectedTab = 2 }
                )
                1 -> PochiMemoryTab()
                else -> PochiSettingsTab(
                    canOverlay = canOverlay,
                    hasAccessibility = hasAccessibility,
                    hasNotification = hasNotification,
                    batteryIgnored = batteryIgnored
                )
            }
        }

        // Clean Floating 3-Tab Bottom Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            PochiSimpleBottomNav(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )
        }
    }
}

@Composable
private fun PochiSimpleBottomNav(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf("Pochi AI", "Memori", "Pengaturan")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF060C1A).copy(alpha = 0.96f))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        PrimaryRoyalBlue.copy(alpha = 0.55f),
                        AccentSkyBlue.copy(alpha = 0.35f),
                        PrimaryRoyalBlue.copy(alpha = 0.55f)
                    )
                ),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { idx, title ->
            val selected = selectedTab == idx
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) {
                            Brush.horizontalGradient(
                                listOf(PrimaryRoyalBlue, Color(0xFF0A2463))
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Transparent)
                            )
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelectTab(idx) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = if (selected) TextWhite else TextMutedBlue,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

// ============================================================================
// TAB 1: POCHI AI HOME & LIVE CHAT (SIMPLE, CLEAN & RESPONSIVE)
// ============================================================================
@Composable
private fun PochiHomeChatTab(
    canOverlay: Boolean,
    onLaunchFilePicker: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val islandRunning by DynamicIslandService.isRunningFlow.collectAsState()
    val droppedFile by PochiSignals.droppedFile.collectAsState()
    val screenApp by PochiSignals.screenApp.collectAsState()

    var isThinking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var promptInput by remember { mutableStateOf("") }
    var lastUserMsg by remember { mutableStateOf("") }
    var pochiReply by remember {
        mutableStateOf("Halo Tuan Andre! Ketik pesan di bawah untuk mencari harga kripto live, membuat grafik interaktif, cek suhu cuaca, jadwal sholat, atau tekan + untuk melampirkan file.")
    }
    var activeCard by remember { mutableStateOf<PochiRichCard?>(null) }
    var currentEmote by remember { mutableStateOf(PochiEmote.SMILING) }

    val currentProvider = remember { PochiPrefs.getProvider(context) }
    val currentModel = remember(currentProvider) { PochiPrefs.getModel(context, currentProvider) }
    val hasApiKey = remember(currentProvider) { PochiPrefs.getApiKey(context, currentProvider).isNotBlank() }

    val chatScroll = rememberScrollState()

    // Automatically inspect dropped file if triggered
    LaunchedEffect(droppedFile) {
        val df = droppedFile ?: return@LaunchedEffect
        isThinking = true
        lastUserMsg = "File: ${df.fileName} (${df.formattedSize})"
        val res = PochiJarvisEngine.processQuery(context, promptInput, df)
        pochiReply = res.text
        activeCard = res.richCard
        currentEmote = res.emote
        PochiSignals.consumeDroppedFile()
        isThinking = false
        delay(80)
        chatScroll.animateScrollTo(chatScroll.maxValue)
    }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { speechRecognizer?.destroy() } }
    }

    fun sendQuery(q: String) {
        val clean = q.trim()
        if (clean.isEmpty()) return
        promptInput = ""
        lastUserMsg = clean
        isThinking = true
        pochiReply = "Memproses permintaan Tuan Andre..."
        scope.launch {
            val res = PochiJarvisEngine.processQuery(context, clean, null)
            pochiReply = res.text
            activeCard = res.richCard
            currentEmote = res.emote
            isThinking = false
            delay(80)
            chatScroll.animateScrollTo(chatScroll.maxValue)
        }
    }

    fun startVoice() {
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() { isListening = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val txt = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (txt.isNotBlank()) sendQuery(txt)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        runCatching { recognizer.startListening(intent) }.onFailure { isListening = false }
    }

    val inf = rememberInfiniteTransition(label = "pochiPulse")
    val auraPulse by inf.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "auraPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 82.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Compact Pochi Hero & Notch Master Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF0A1D42),
                            CardSurface,
                            BgDeepBlack
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(PrimaryRoyalBlue.copy(alpha = 0.65f), AccentSkyBlue.copy(alpha = 0.35f))
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Glowing Pochi Mascot Orb
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            PrimaryRoyalBlue.copy(alpha = 0.65f * auraPulse),
                                            Color(0xFF08142C),
                                            BgDeepBlack
                                        )
                                    )
                                )
                                .border(1.5.dp, AccentSkyBlue.copy(alpha = 0.7f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            PochiMascot(
                                state = if (isThinking) BotState.WORKING else BotState.IDLE,
                                emoteOverride = if (isThinking) PochiEmote.THINKING else if (isListening) PochiEmote.SURPRISED else currentEmote,
                                size = 36.dp,
                                showBlackBacking = false
                            )
                        }

                        Column {
                            Text(
                                text = "POCHI AI",
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (hasApiKey) "${currentProvider.label} • $currentModel" else "Mode Live API • Ketuk untuk isi API Key",
                                color = AccentSkyBlue,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { onOpenSettings() }
                            )
                        }
                    }

                    // Dynamic Notch ON/OFF Pill Switch
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (islandRunning) PrimaryRoyalBlue else CardSurfaceElevated)
                            .border(
                                1.dp,
                                if (islandRunning) AccentSkyBlue else BorderSubtleBlue,
                                RoundedCornerShape(50)
                            )
                            .clickable {
                                if (!Settings.canDrawOverlays(context)) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                    )
                                } else if (islandRunning) {
                                    DynamicIslandService.stop(context)
                                } else {
                                    DynamicIslandService.start(context)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (islandRunning) AccentSkyBlue else TextMutedBlue)
                        )
                        Text(
                            text = if (islandRunning) "NOTCH ON" else "NOTCH OFF",
                            color = TextWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Quick Status & Open Notch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Posisi terkunci di tengah atas & pinggir saat miring",
                        color = TextMutedBlue,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "Buka Notch",
                        color = AccentSkyBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable {
                                if (canOverlay) {
                                    DynamicIslandService.start(context)
                                    PochiSignals.requestExpandIsland()
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 2. Scrollable Chat & Generative Visual Card Stream
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(CardSurface)
                .border(1.dp, BorderSubtleBlue.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .padding(14.dp)
                .verticalScroll(chatScroll),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (lastUserMsg.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(PrimaryRoyalBlue, Color(0xFF0A2463))
                                )
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = lastUserMsg,
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Pochi Reply Bubble
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .background(CardSurfaceElevated)
                    .border(1.dp, PrimaryRoyalBlue.copy(alpha = 0.35f), RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "POCHI",
                        color = AccentSkyBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = pochiReply,
                        color = Color(0xFFEFF6FF),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }

            // Generative Visual Card (Crypto / Chart / Weather / Prayer / FileDrop / System / Knowledge)
            if (activeCard != null) {
                PochiRichCardView(
                    card = activeCard!!,
                    compact = false
                )
            }
        }

        // 3. Pinned Bottom Chat Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(CardSurface)
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(PrimaryRoyalBlue.copy(alpha = 0.7f), AccentSkyBlue.copy(alpha = 0.45f))
                    ),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "+" File Drop Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(CardSurfaceElevated)
                    .border(1.dp, BorderSubtleBlue, CircleShape)
                    .clickable { onLaunchFilePicker("*/*") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Lampirkan File",
                    tint = TextWhite,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (promptInput.isEmpty()) {
                    Text(
                        text = "Ketik pesan ke Pochi...",
                        color = TextMutedBlue,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    textStyle = TextStyle(
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(AccentSkyBlue),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendQuery(promptInput) }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Mic Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable { startVoice() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                    contentDescription = "Suara",
                    tint = if (isListening) AccentSkyBlue else TextMutedBlue,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Send Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(TextWhite)
                    .clickable { sendQuery(promptInput) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(15.dp)) {
                    val w = size.width
                    val h = size.height
                    val p = Path().apply {
                        moveTo(w * 0.12f, h * 0.48f)
                        lineTo(w * 0.88f, h * 0.14f)
                        lineTo(w * 0.58f, h * 0.88f)
                        lineTo(w * 0.44f, h * 0.56f)
                        close()
                    }
                    drawPath(
                        path = p,
                        color = BgDeepBlack,
                        style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

// ============================================================================
// TAB 2: POCHI MEMORY & CONTEXT (SIMPLE, CLEAN & RESPONSIVE)
// ============================================================================
@Composable
private fun PochiMemoryTab() {
    val context = LocalContext.current
    val db = remember { PochiDb.getInstance(context) }
    var memories by remember { mutableStateOf(db.getAllMemories().take(40)) }
    var newMemoryText by remember { mutableStateOf("") }

    val selectedCity by PochiPrefs.cityFlow.collectAsState()
    val screenApp by PochiSignals.screenApp.collectAsState()

    val bm = remember { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    val batteryPct = remember { bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(1, 100) ?: 88 }

    val am = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
    val ramUsedGb = remember {
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val used = ((info.totalMem - info.availMem).coerceAtLeast(1024L * 1024L * 1024L)) / (1024.0 * 1024.0 * 1024.0)
        String.format(Locale.US, "%.1f GB", used)
    }

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 82.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Memori & Konteks Pochi",
            color = TextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // 1. Telemetry & Context Summary Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SimpleStatCard(
                label = "BATERAI & RAM",
                value = "$batteryPct% • $ramUsedGb",
                modifier = Modifier.weight(1f)
            )
            SimpleStatCard(
                label = "LAYAR AKTIF",
                value = screenApp.ifBlank { "Pochi Home" },
                modifier = Modifier.weight(1f)
            )
        }

        // 2. City Selector Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardSurface)
                .border(1.dp, BorderSubtleBlue.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Kota Jadwal Sholat & Cuaca Live",
                color = AccentSkyBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PrayerScheduleHelper.supportedCities.take(4).forEach { city ->
                    val active = city.equals(selectedCity, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) PrimaryRoyalBlue else CardSurfaceElevated)
                            .clickable { PochiPrefs.saveCity(context, city) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = city,
                            color = TextWhite,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PrayerScheduleHelper.supportedCities.drop(4).take(4).forEach { city ->
                    val active = city.equals(selectedCity, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) PrimaryRoyalBlue else CardSurfaceElevated)
                            .clickable { PochiPrefs.saveCity(context, city) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = city,
                            color = TextWhite,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 3. Add Permanent Memory Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardSurface)
                .border(1.dp, BorderSubtleBlue.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Tambah Ingatan Baru untuk Tuan Andre",
                color = AccentSkyBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgDeepBlack)
                        .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (newMemoryText.isEmpty()) {
                        Text(
                            text = "Misal: Saya suka kopi americano...",
                            color = TextMutedBlue,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = newMemoryText,
                        onValueChange = { newMemoryText = it },
                        textStyle = TextStyle(color = TextWhite, fontSize = 12.sp),
                        cursorBrush = SolidColor(AccentSkyBlue),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue)))
                        .clickable {
                            if (newMemoryText.isNotBlank()) {
                                db.saveMemory("preferensi", newMemoryText.trim())
                                newMemoryText = ""
                                memories = db.getAllMemories().take(40)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Simpan",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Saved Memories List
        Text(
            text = "Daftar Memori Tersimpan (${memories.size})",
            color = TextWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardSurface)
                    .border(1.dp, BorderSubtleBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Pochi selalu mengingat panggilan kehormatan 'Tuan Andre' secara permanen.",
                    color = TextMutedBlue,
                    fontSize = 12.sp
                )
            }
        } else {
            memories.forEach { mem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CardSurface)
                        .border(1.dp, BorderSubtleBlue.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mem.fact,
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = mem.category.uppercase(Locale.US),
                            color = AccentSkyBlue,
                            fontSize = 10.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Hapus",
                        tint = TextMutedBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                db.deleteMemory(mem.id)
                                memories = db.getAllMemories().take(40)
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .border(1.dp, BorderSubtleBlue.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = AccentSkyBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = TextWhite,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================================
// TAB 3: POCHI SETTINGS (API KEY, MODEL & ANDROID PERMISSIONS)
// ============================================================================
@Composable
private fun PochiSettingsTab(
    canOverlay: Boolean,
    hasAccessibility: Boolean,
    hasNotification: Boolean,
    batteryIgnored: Boolean
) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf(PochiPrefs.getProvider(context)) }
    var apiKeyInput by remember(selectedProvider) {
        mutableStateOf(PochiPrefs.getApiKey(context, selectedProvider))
    }
    var modelInput by remember(selectedProvider) {
        mutableStateOf(PochiPrefs.getModel(context, selectedProvider))
    }
    var showApiKey by remember { mutableStateOf(false) }
    var savedBanner by remember { mutableStateOf(false) }

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 82.dp)
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Pengaturan Pochi",
            color = TextWhite,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )

        // 1. AI Brain & API Key Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardSurface)
                .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Otak AI & Kunci API",
                color = AccentSkyBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiProvider.entries.forEach { prov ->
                    val active = prov == selectedProvider
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) PrimaryRoyalBlue else CardSurfaceElevated)
                            .clickable {
                                selectedProvider = prov
                                PochiPrefs.setProvider(context, prov)
                                savedBanner = false
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = prov.label,
                            color = if (active) TextWhite else TextMutedBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "API Key (${selectedProvider.label})",
                    color = TextMutedBlue,
                    fontSize = 11.sp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgDeepBlack)
                        .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            savedBanner = false
                        },
                        textStyle = TextStyle(
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        cursorBrush = SolidColor(AccentSkyBlue),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = "Tampilkan Key",
                        tint = TextMutedBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { showApiKey = !showApiKey }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Model (${selectedProvider.defaultModel})",
                    color = TextMutedBlue,
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgDeepBlack)
                        .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    BasicTextField(
                        value = modelInput,
                        onValueChange = {
                            modelInput = it
                            savedBanner = false
                        },
                        textStyle = TextStyle(color = TextWhite, fontSize = 13.sp),
                        cursorBrush = SolidColor(AccentSkyBlue),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue)))
                    .clickable {
                        PochiPrefs.setProvider(context, selectedProvider)
                        PochiPrefs.setApiKey(context, selectedProvider, apiKeyInput.trim())
                        PochiPrefs.saveModel(context, selectedProvider, modelInput.trim().ifBlank { selectedProvider.defaultModel })
                        savedBanner = true
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (savedBanner) "Tersimpan untuk Tuan Andre" else "Simpan API Key & Model",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // 2. System Permissions Checklist
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardSurface)
                .border(1.dp, BorderSubtleBlue.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Izin Sistem Android",
                color = AccentSkyBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            DarkPermissionRow(
                title = "Overlay Dynamic Notch",
                subtitle = "Menampilkan Notch Pochi di atas layar",
                granted = canOverlay
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }

            DarkPermissionRow(
                title = "Aksesibilitas Konteks Layar",
                subtitle = "Mendeteksi aktivitas layar secara real-time",
                granted = hasAccessibility
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            DarkPermissionRow(
                title = "Pendengar Media & Notifikasi",
                subtitle = "Sinkronisasi musik & pesan masuk",
                granted = hasNotification
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            DarkPermissionRow(
                title = "Bebas Optimasi Baterai",
                subtitle = "Menjaga Pochi tetap aktif di latar belakang",
                granted = batteryIgnored
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DarkPermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextMutedBlue,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (granted) PrimaryRoyalBlue.copy(alpha = 0.28f) else Color(0xFFEF4444).copy(alpha = 0.20f))
                .border(
                    1.dp,
                    if (granted) AccentSkyBlue.copy(alpha = 0.6f) else Color(0xFFEF4444).copy(alpha = 0.6f),
                    RoundedCornerShape(50)
                )
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (granted) "AKTIF" else "IZINKAN",
                color = if (granted) AccentSkyBlue else Color(0xFFF87171),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// ============================================================================
// ONBOARDING SCREEN FOR TUAN ANDRE
// ============================================================================
@Composable
private fun PochiOnboardingScreen(
    canOverlay: Boolean,
    hasAccessibility: Boolean,
    hasNotification: Boolean,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeepBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(PrimaryRoyalBlue, BgNavyDark, BgDeepBlack)))
                    .border(1.5.dp, AccentSkyBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                PochiMascot(
                    state = BotState.IDLE,
                    emoteOverride = PochiEmote.EXCITED,
                    size = 64.dp,
                    showBlackBacking = false
                )
            }

            Text(
                text = "Selamat Datang, Tuan Andre",
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Pochi siap menjadi AI Agent di tengah atas layar HP Tuan Andre (dan otomatis menyesuaikan di pinggir layar saat miring).",
                color = TextMutedBlue,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            DarkPermissionRow(
                title = "1. Izin Overlay Notch (Wajib)",
                subtitle = "Agar Notch Pochi tampil di atas layar",
                granted = canOverlay
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }

            DarkPermissionRow(
                title = "2. Aksesibilitas Konteks Layar",
                subtitle = "Agar mata Pochi bereaksi terhadap aktivitas layar",
                granted = hasAccessibility
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            DarkPermissionRow(
                title = "3. Pendengar Media & Notifikasi",
                subtitle = "Deteksi musik & pesan secara real-time",
                granted = hasNotification
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(PrimaryRoyalBlue, AccentElectricBlue)))
                    .clickable { onFinish() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Mulai Pochi Sekarang",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
