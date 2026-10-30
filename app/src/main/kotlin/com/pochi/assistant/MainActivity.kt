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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// ============================================================================
// BLUE-BLACK MONOLITH & PLATINUM SHEET PALETTE (NO PURPLE)
// ============================================================================
private val MonolithBlack = Color(0xFF04070E)
private val DeepCardBlack = Color(0xFF0A101D)
private val ElevatedCardBlack = Color(0xFF0F172A)
private val CardBorderDark = Color(0xFF1E293B)
private val SheetPlatinum = Color(0xFFF4F6F9)
private val SheetCardWhite = Color(0xFFFFFFFF)
private val RoyalBlue = Color(0xFF1D4ED8)
private val DeepNavyBlue = Color(0xFF0A2463)
private val ElectricBlue = Color(0xFF2563EB)
private val SkyCyanBlue = Color(0xFF38BDF8)
private val SoftBlueGlow = Color(0xFF93C5FD)
private val TextPrimaryWhite = Color(0xFFFFFFFF)
private val TextSecondaryMuted = Color(0xFF8DA2C0)
private val TextDarkSheet = Color(0xFF090D16)

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
                primary = RoyalBlue,
                secondary = SkyCyanBlue,
                background = MonolithBlack,
                surface = DeepCardBlack,
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
                    color = SheetPlatinum
                ) {
                    PochiReferenceApp(
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
fun PochiReferenceApp(
    onLaunchFilePicker: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var onboarded by remember { mutableStateOf(PochiPrefs.onboardingDoneFlow.value) }
    // Default to Center Tab (1 = Pochi AI Center)
    var selectedTab by remember { mutableIntStateOf(1) }

    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibility by remember { mutableStateOf(PochiAccessibilityService.instance != null) }
    var hasNotification by remember { mutableStateOf((Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true)) }
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
                hasNotification = (Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true)
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
            .background(SheetPlatinum)
    ) {
        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(260),
            label = "mainTabCrossfade"
        ) { tab ->
            when (tab) {
                0 -> PochiControlHubScreen(
                    canOverlay = canOverlay,
                    hasAccessibility = hasAccessibility,
                    hasNotification = hasNotification,
                    onNavigateToTab = { selectedTab = it }
                )
                1 -> PochiAiCenterScreen(
                    onLaunchFilePicker = onLaunchFilePicker,
                    onNavigateToSettings = { selectedTab = 3 }
                )
                2 -> PochiContextAndMemoryScreen(
                    onNavigateToTab = { selectedTab = it }
                )
                else -> PochiEngineSettingsScreen(
                    canOverlay = canOverlay,
                    hasAccessibility = hasAccessibility,
                    hasNotification = hasNotification,
                    batteryIgnored = batteryIgnored
                )
            }
        }

        // Floating Dual-Pill Bottom Dock matching the reference image 1:1
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            PochiReferenceFloatingDock(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onQuickPochiTrigger = {
                    if (selectedTab != 1) {
                        selectedTab = 1
                    } else if (Settings.canDrawOverlays(context)) {
                        DynamicIslandService.start(context)
                        PochiSignals.requestExpandIsland()
                    }
                }
            )
        }
    }
}

// ============================================================================
// BOTTOM DUAL-PILL FLOATING DOCK (EXACT 1:1 REFERENCE GEOMETRY, BLUE GLOW)
// ============================================================================
@Composable
private fun PochiReferenceFloatingDock(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onQuickPochiTrigger: () -> Unit
) {
    val inf = rememberInfiniteTransition(label = "dockStarAnim")
    val starAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing)),
        label = "starRot"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Left Black Capsule containing 4 custom-drawn vector icons
        Row(
            modifier = Modifier
                .height(58.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF060A14))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DockVectorIconButton(
                selected = selectedTab == 0,
                onClick = { onSelectTab(0) }
            ) { tint ->
                // 1. Home Icon
                Canvas(modifier = Modifier.size(20.dp)) {
                    val w = size.width
                    val h = size.height
                    val p = Path().apply {
                        moveTo(w * 0.15f, h * 0.46f)
                        lineTo(w * 0.50f, h * 0.14f)
                        lineTo(w * 0.85f, h * 0.46f)
                        lineTo(w * 0.85f, h * 0.86f)
                        lineTo(w * 0.15f, h * 0.86f)
                        close()
                    }
                    drawPath(
                        path = p,
                        color = tint,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            DockVectorIconButton(
                selected = selectedTab == 1,
                onClick = { onSelectTab(1) }
            ) { tint ->
                // 2. Concentric Target / AI Center Icon
                Canvas(modifier = Modifier.size(20.dp)) {
                    drawCircle(
                        color = tint,
                        radius = size.minDimension * 0.38f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = tint,
                        radius = size.minDimension * 0.15f
                    )
                }
            }

            DockVectorIconButton(
                selected = selectedTab == 2,
                onClick = { onSelectTab(2) }
            ) { tint ->
                // 3. Person / Memory Profile Icon
                Canvas(modifier = Modifier.size(20.dp)) {
                    val w = size.width
                    val h = size.height
                    drawCircle(
                        color = tint,
                        radius = w * 0.18f,
                        center = Offset(w * 0.5f, h * 0.30f)
                    )
                    val arc = Path().apply {
                        moveTo(w * 0.20f, h * 0.84f)
                        cubicTo(w * 0.20f, h * 0.58f, w * 0.80f, h * 0.58f, w * 0.80f, h * 0.84f)
                    }
                    drawPath(
                        path = arc,
                        color = tint,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            DockVectorIconButton(
                selected = selectedTab == 3,
                onClick = { onSelectTab(3) }
            ) { tint ->
                // 4. Vertical 3-Dots Settings Icon
                Canvas(modifier = Modifier.size(20.dp)) {
                    val cx = size.width * 0.5f
                    val r = 2.1.dp.toPx()
                    drawCircle(color = tint, radius = r, center = Offset(cx, size.height * 0.24f))
                    drawCircle(color = tint, radius = r, center = Offset(cx, size.height * 0.50f))
                    drawCircle(color = tint, radius = r, center = Offset(cx, size.height * 0.76f))
                }
            }
        }

        // Right Companion Black Capsule with Blue-Black Gradient Aura & 8-Ray AI Starburst
        Box(
            modifier = Modifier
                .height(58.dp)
                .width(62.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF060A14))
                .border(1.dp, Color(0xFF1D4ED8).copy(alpha = 0.45f), RoundedCornerShape(50))
                .clickable { onQuickPochiTrigger() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1D4ED8).copy(alpha = 0.55f),
                                Color(0xFF0A2463).copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = size.minDimension * 0.44f
                    rotate(starAngle, pivot = Offset(cx, cy)) {
                        for (i in 0 until 8) {
                            val angle = Math.toRadians((i * 45).toDouble())
                            val ex = cx + cos(angle).toFloat() * radius
                            val ey = cy + sin(angle).toFloat() * radius
                            drawLine(
                                color = Color(0xFF93C5FD),
                                start = Offset(cx, cy),
                                end = Offset(ex, ey),
                                strokeWidth = 1.8.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    drawCircle(
                        color = Color.White,
                        radius = 2.2.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}

@Composable
private fun DockVectorIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xFF111C33) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        icon(if (selected) Color.White else Color(0xFF6E82A3))
    }
}

// ============================================================================
// SCREEN 1 (LEFT REFERENCE LAYOUT): "POCHI CONTROL" (SETTINGS & NOTCH HUB)
// ============================================================================
@Composable
private fun PochiControlHubScreen(
    canOverlay: Boolean,
    hasAccessibility: Boolean,
    hasNotification: Boolean,
    onNavigateToTab: (Int) -> Unit
) {
    val context = LocalContext.current
    val islandRunning by DynamicIslandService.isRunningFlow.collectAsState()
    val providers = remember { AiProvider.entries }
    var currentProvider by remember { mutableStateOf(PochiPrefs.getProvider(context)) }
    val apiKey = remember(currentProvider) { PochiPrefs.getApiKey(context, currentProvider) }
    val currentModel = remember(currentProvider) { PochiPrefs.getModel(context, currentProvider) }
    var showInlineKeyModal by remember { mutableStateOf(false) }
    var keyDraft by remember(currentProvider) { mutableStateOf(apiKey) }

    val maskedKeySuffix = remember(apiKey) {
        if (apiKey.length >= 4) apiKey.takeLast(4).uppercase(Locale.US) else "NONE"
    }

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetPlatinum)
            .verticalScroll(scroll)
    ) {
        // 1. Upper Pitch-Black Monolith Container with 38.dp bottom corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MonolithBlack,
                            Color(0xFF060C1A),
                            MonolithBlack
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Row: Blue-Glowing Pochi Avatar + "Pochi Control" + Status Bell
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Blue-Black Glowing Pochi Mascot Avatar
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        RoyalBlue,
                                        DeepNavyBlue,
                                        Color(0xFF040814)
                                    )
                                )
                            )
                            .border(1.5.dp, SkyCyanBlue.copy(alpha = 0.65f), CircleShape)
                            .clickable { onNavigateToTab(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        PochiMascot(
                            state = BotState.IDLE,
                            emoteOverride = PochiEmote.SMILING,
                            size = 34.dp,
                            showBlackBacking = false
                        )
                    }

                    Text(
                        text = "Pochi Control",
                        color = TextPrimaryWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Right Circular Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ElevatedCardBlack)
                            .border(1.dp, CardBorderDark, CircleShape)
                            .clickable { onNavigateToTab(3) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = "Notifikasi Sistem",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Provider Selector Pill (matching the `v USD` pill in the reference)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ElevatedCardBlack)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(50))
                        .clickable {
                            val nextIdx = (providers.indexOf(currentProvider) + 1) % providers.size
                            val nextP = providers[nextIdx]
                            PochiPrefs.setProvider(context, nextP)
                            currentProvider = nextP
                            keyDraft = PochiPrefs.getApiKey(context, nextP)
                        }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "v  ${currentProvider.label}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Status Pochi Tuan Andre",
                    color = TextSecondaryMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Large Responsive Status Hero (matching the large hero typography in reference)
                Text(
                    text = if (islandRunning) "POCHI ACTIVE" else "STANDBY",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Model: $currentModel  •  Posisi Tengah & Pinggir",
                    color = SkyCyanBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Action Capsule Row: [ ON | OFF ] and [ + ] (matching reference geometry)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .height(46.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ElevatedCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(50))
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
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = if (islandRunning) "ON" else "OFF",
                            color = if (islandRunning) SkyCyanBlue else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(14.dp)
                                .background(Color.White.copy(alpha = 0.22f))
                        )
                        Text(
                            text = "NOTCH",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Expand Notch Now Button (+)
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(ElevatedCardBlack)
                            .border(1.dp, CardBorderDark, CircleShape)
                            .clickable {
                                if (Settings.canDrawOverlays(context)) {
                                    DynamicIslandService.start(context)
                                    PochiSignals.requestExpandIsland()
                                } else {
                                    onNavigateToTab(1)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Buka Chat Notch",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Masked API Key Row (`••••  ••••  ••••  A8F2`, matching `•••• •••• •••• 4320` in reference)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "••••   ••••   ••••",
                        color = TextSecondaryMuted,
                        fontSize = 15.sp,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = maskedKeySuffix,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Dual Core Badge & Quick Key Edit (+) Row (matching Mastercard + `+` row in reference)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Blue-Cyan Dual Core Emblem Card
                    Box(
                        modifier = Modifier
                            .width(66.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElevatedCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(14.dp))
                            .clickable { showInlineKeyModal = !showInlineKeyModal },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(32.dp, 20.dp)) {
                            val r = size.height * 0.45f
                            drawCircle(
                                color = RoyalBlue,
                                radius = r,
                                center = Offset(size.width * 0.36f, size.height * 0.5f)
                            )
                            drawCircle(
                                color = SkyCyanBlue.copy(alpha = 0.88f),
                                radius = r,
                                center = Offset(size.width * 0.64f, size.height * 0.5f)
                            )
                        }
                    }

                    // Quick Edit API Key Button (+)
                    Box(
                        modifier = Modifier
                            .width(66.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElevatedCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(14.dp))
                            .clickable { showInlineKeyModal = !showInlineKeyModal },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Input API Key",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Inline Quick API Key Input Drawer
                AnimatedVisibility(
                    visible = showInlineKeyModal,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(ElevatedCardBlack)
                            .border(1.dp, RoyalBlue.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "API Key ${currentProvider.label}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        BasicTextField(
                            value = keyDraft,
                            onValueChange = { keyDraft = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(SkyCyanBlue),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MonolithBlack)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.horizontalGradient(listOf(RoyalBlue, ElectricBlue)))
                                .clickable {
                                    PochiPrefs.setApiKey(context, currentProvider, keyDraft.trim())
                                    showInlineKeyModal = false
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Simpan API Key",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Pengaturan AI Lengkap",
                    color = TextSecondaryMuted,
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onNavigateToTab(3) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Home Handle Indicator inside dark container
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        }

        // 2. Lower Off-White Platinum Sheet (`Izin & Modul Pochi` + `See All`)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Modul & Izin Pochi",
                    color = TextDarkSheet,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Pengaturan",
                    color = Color(0xFF475569),
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onNavigateToTab(3) }
                )
            }

            // Primary Dark Pill Card on White Sheet (Notch Overlay Status)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(MonolithBlack)
                    .clickable {
                        if (!canOverlay) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else if (!islandRunning) {
                            DynamicIslandService.start(context)
                        } else {
                            PochiSignals.requestExpandIsland()
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(RoyalBlue, DeepNavyBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        PochiMascot(
                            state = BotState.IDLE,
                            emoteOverride = PochiEmote.HAPPY,
                            size = 28.dp,
                            showBlackBacking = false
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (canOverlay) "Dynamic Notch Terkunci di Tengah Atas" else "Aktifkan Izin Overlay Dynamic Notch",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Otomatis pindah ke pinggir layar saat HP miring",
                            color = TextSecondaryMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = if (canOverlay) "AKTIF" else "IZINKAN",
                    color = SkyCyanBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Secondary White Card: Accessibility Context Service
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SheetCardWhite)
                    .clickable {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(RoyalBlue, DeepNavyBlue)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AI",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sensor Konteks Layar (Accessibility)",
                            color = TextDarkSheet,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (hasAccessibility) "Pochi membaca konteks aktivitas layar real-time" else "Ketuk untuk mengaktifkan sensor layar",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = if (hasAccessibility) "ON" else "OFF",
                    color = if (hasAccessibility) RoyalBlue else Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Third White Card: Notification Listener
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(SheetCardWhite)
                    .clickable {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SYNC",
                            color = SkyCyanBlue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pendengar Media & Notifikasi",
                            color = TextDarkSheet,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (hasNotification) "Tersinkronisasi dengan musik & notifikasi" else "Ketuk untuk mengizinkan akses notifikasi",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = if (hasNotification) "ON" else "OFF",
                    color = if (hasNotification) RoyalBlue else Color(0xFF64748B),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(92.dp))
        }
    }
}

// ============================================================================
// SCREEN 2 (CENTER REFERENCE LAYOUT): "POCHI AI CENTER" (BLUE-BLACK AURORA)
// ============================================================================
@Composable
private fun PochiAiCenterScreen(
    onLaunchFilePicker: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isThinking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var promptInput by remember { mutableStateOf("") }
    var lastQuestion by remember { mutableStateOf("Apa yang bisa Pochi bantu hari ini, Tuan Andre?") }
    var lastAnswer by remember {
        mutableStateOf("Ketik pertanyaan untuk mencari harga kripto live, membuat grafik data interaktif, cek suhu cuaca, jadwal sholat, atau tekan + untuk drop file apapun.")
    }
    var activeCard by remember { mutableStateOf<PochiRichCard?>(null) }
    var currentEmote by remember { mutableStateOf(PochiEmote.SMILING) }

    val islandRunning by DynamicIslandService.isRunningFlow.collectAsState()
    var autoTransparentNotch by remember { mutableStateOf(true) }
    val droppedFile by PochiSignals.droppedFile.collectAsState()

    val currentProvider = remember { PochiPrefs.getProvider(context) }
    val currentModel = remember(currentProvider) { PochiPrefs.getModel(context, currentProvider) }
    val timeNow = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }

    // Automatically inspect dropped file if triggered
    LaunchedEffect(droppedFile) {
        val df = droppedFile ?: return@LaunchedEffect
        isThinking = true
        lastQuestion = "File: ${df.fileName} (${df.formattedSize})"
        val res = PochiJarvisEngine.processQuery(context, promptInput, df)
        lastAnswer = res.text
        activeCard = res.richCard
        currentEmote = res.emote
        PochiSignals.consumeDroppedFile()
        isThinking = false
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
        lastQuestion = clean
        isThinking = true
        scope.launch {
            val res = PochiJarvisEngine.processQuery(context, clean, null)
            lastAnswer = res.text
            activeCard = res.richCard
            currentEmote = res.emote
            isThinking = false
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

    val inf = rememberInfiniteTransition(label = "blueAuroraCenter")
    val orbRot by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "orbRot"
    )
    val auroraShift by inf.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "auroraShift"
    )

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetPlatinum)
            .verticalScroll(scroll)
    ) {
        // Upper Pitch-Black Monolith with Sweeping Blue-Black Aurora at Top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                .background(MonolithBlack)
        ) {
            // Sweeping Blue-Black Aurora Glow Canvas at Top (No Purple)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            ) {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF38BDF8).copy(alpha = 0.42f),
                            Color(0xFF1D4ED8).copy(alpha = 0.55f),
                            Color(0xFF0A2463).copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.52f, h * 0.16f),
                        radius = w * 0.72f * auroraShift
                    ),
                    radius = w * 0.72f * auroraShift,
                    center = Offset(w * 0.52f, h * 0.16f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(26.dp))

                // Central Glowing Blue AI Orb with Superscript "AI" Badge
                Box(
                    modifier = Modifier.size(96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AI",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF38BDF8).copy(alpha = 0.45f),
                                        Color(0xFF1D4ED8).copy(alpha = 0.72f),
                                        Color(0xFF071330).copy(alpha = 0.94f)
                                    )
                                )
                            )
                            .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                            .clickable { startVoice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(30.dp)) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val r = size.minDimension * 0.44f
                            rotate(orbRot, pivot = Offset(cx, cy)) {
                                for (i in 0 until 8) {
                                    val rad = Math.toRadians((i * 45).toDouble())
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.90f),
                                        start = Offset(cx, cy),
                                        end = Offset(cx + cos(rad).toFloat() * r, cy + sin(rad).toFloat() * r),
                                        strokeWidth = 1.8.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Pochi Mascot Eyes beneath the AI Orb
                PochiMascot(
                    state = if (isThinking) BotState.WORKING else BotState.IDLE,
                    emoteOverride = if (isThinking) PochiEmote.THINKING else if (isListening) PochiEmote.SURPRISED else currentEmote,
                    size = 44.dp,
                    showBlackBacking = false
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when {
                        isThinking -> "Thinking..."
                        isListening -> "Listening to Tuan Andre..."
                        else -> "Thinking..."
                    },
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(34.dp))

                // Two Side-by-Side Dark Bento Cards with Toggle Switches (1:1 Reference Layout, Pochi Settings Content)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Bento Card: Dynamic Notch Overlay
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(DeepCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(26.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Dynamic Notch",
                            color = TextSecondaryMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Tengah",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "& Pinggir",
                                color = TextSecondaryMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "P",
                                    color = RoyalBlue,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            ReferencePillSwitch(
                                checked = islandRunning,
                                onCheckedChange = {
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
                            )
                        }
                    }

                    // Right Bento Card: Idle Transparency & Shrink
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(DeepCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(26.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Mode Transparan",
                            color = TextSecondaryMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "4.5s",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto Kecil",
                                color = TextSecondaryMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 3.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(14.dp)) {
                                    for (i in -1..1) {
                                        val y = size.height * (0.5f + i * 0.24f)
                                        drawLine(
                                            color = MonolithBlack,
                                            start = Offset(size.width * 0.18f, y),
                                            end = Offset(size.width * 0.82f, y),
                                            strokeWidth = 1.8.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }

                            ReferencePillSwitch(
                                checked = autoTransparentNotch,
                                onCheckedChange = { autoTransparentNotch = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Prompt & Generative Visual Card (1:1 Reference Layout)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(DeepCardBlack)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(28.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = lastQuestion,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "$timeNow  •  ${currentProvider.label} ($currentModel)",
                        color = TextSecondaryMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onNavigateToSettings() }
                    )

                    Text(
                        text = lastAnswer,
                        color = Color(0xFFD8E4F8),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    if (activeCard != null) {
                        PochiRichCardView(
                            card = activeCard!!,
                            compact = false
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Bottom Prompt Input Row: [+] + Input Text + White Paper-Plane Circle Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // "+" File Drop / Attach Circle Button
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ElevatedCardBlack)
                                    .border(1.dp, CardBorderDark, CircleShape)
                                    .clickable { onLaunchFilePicker("*/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Drop File",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (promptInput.isEmpty()) {
                                    Text(
                                        text = "Pochi AI...",
                                        color = TextSecondaryMuted,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = promptInput,
                                    onValueChange = { promptInput = it },
                                    textStyle = TextStyle(
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    cursorBrush = SolidColor(SkyCyanBlue),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { sendQuery(promptInput) }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Voice Mic Button
                        Icon(
                            imageVector = if (isListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                            contentDescription = "Suara",
                            tint = if (isListening) SkyCyanBlue else TextSecondaryMuted,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(20.dp)
                                .clickable { startVoice() }
                        )

                        // White Circular Send Button with Paper-Plane Vector
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .clickable { sendQuery(promptInput) },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(16.dp)) {
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
                                    color = MonolithBlack,
                                    style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Home Handle Indicator
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                )
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun ReferencePillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .width(38.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) RoyalBlue.copy(alpha = 0.35f) else Color(0xFF060A14))
            .border(
                1.dp,
                if (checked) SkyCyanBlue else Color.White.copy(alpha = 0.35f),
                RoundedCornerShape(50)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

// ============================================================================
// SCREEN 3 (RIGHT REFERENCE LAYOUT): "POCHI CONTEXT & MEMORY"
// ============================================================================
@Composable
private fun PochiContextAndMemoryScreen(
    onNavigateToTab: (Int) -> Unit
) {
    val context = LocalContext.current
    val db = remember { PochiDb.getInstance(context) }
    var memories by remember { mutableStateOf(db.getAllMemories().take(30)) }
    var newMemoryText by remember { mutableStateOf("") }

    val selectedCity by PochiPrefs.cityFlow.collectAsState()
    val cities = remember { PrayerScheduleHelper.supportedCities }
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

    val inf = rememberInfiniteTransition(label = "orbitalSphere")
    val orbitAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(15000, easing = LinearEasing)),
        label = "orbitRot"
    )

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetPlatinum)
            .verticalScroll(scroll)
    ) {
        // Upper Pitch-Black Monolith Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                .background(MonolithBlack)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header: Centered "Pochi Context" + Right Search/Settings Button
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Pochi Context",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ElevatedCardBlack)
                            .border(1.dp, CardBorderDark, CircleShape)
                            .clickable { onNavigateToTab(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Cari dengan Pochi",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Top Hero Card with Animated Blue Crystalline Orbital Sphere (1:1 Reference Layout)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(DeepCardBlack)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(26.dp))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Telemetri Sistem",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Baterai & RAM",
                                color = TextSecondaryMuted,
                                fontSize = 11.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ElevatedCardBlack)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = SkyCyanBlue,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "$batteryPct% • $ramUsedGb",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // Right Orbital Crystalline Sphere Graphic + Badge
                    Box(
                        modifier = Modifier.size(106.dp, 88.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width * 0.48f
                            val cy = size.height * 0.46f
                            val r = size.minDimension * 0.30f

                            rotate(orbitAngle, pivot = Offset(cx, cy)) {
                                drawOval(
                                    color = Color.White.copy(alpha = 0.25f),
                                    topLeft = Offset(cx - r * 1.6f, cy - r * 0.55f),
                                    size = Size(r * 3.2f, r * 1.1f),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                            rotate(-orbitAngle * 0.8f + 45f, pivot = Offset(cx, cy)) {
                                drawOval(
                                    color = SkyCyanBlue.copy(alpha = 0.35f),
                                    topLeft = Offset(cx - r * 1.5f, cy - r * 0.5f),
                                    size = Size(r * 3.0f, r * 1.0f),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }

                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White,
                                        SkyCyanBlue,
                                        RoyalBlue,
                                        DeepNavyBlue
                                    ),
                                    center = Offset(cx - r * 0.2f, cy - r * 0.2f),
                                    radius = r * 1.2f
                                ),
                                radius = r,
                                center = Offset(cx, cy)
                            )
                        }

                        Column(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            horizontalAlignment = Alignment.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF0A1931).copy(alpha = 0.92f))
                                    .border(1.dp, SkyCyanBlue.copy(alpha = 0.35f), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${memories.size} Memori",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Tuan Andre",
                                color = TextSecondaryMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp, end = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Centered Filter Pill (`Kota: Jakarta v`)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ElevatedCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(50))
                            .clickable {
                                val nextIdx = (cities.indexOf(selectedCity) + 1) % cities.size
                                PochiPrefs.saveCity(context, cities[nextIdx])
                            }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kota: $selectedCity  v",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section Header: "Konteks Aktif" + "Real-Time"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Konteks Aktif",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Sinkronisasi Otomatis",
                        color = TextSecondaryMuted,
                        fontSize = 12.sp,
                        textDecoration = TextDecoration.Underline
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2-Column Bento Cards (matching the 2-column Bento row in the right reference screen)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Bento Card: Kota Cuaca & Sholat
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(DeepCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(22.dp))
                            .clickable {
                                val nextIdx = (cities.indexOf(selectedCity) + 1) % cities.size
                                PochiPrefs.saveCity(context, cities[nextIdx])
                            }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ElevatedCardBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "LOC",
                                    color = SkyCyanBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Lokasi Kota",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = selectedCity,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Right Bento Card: Active Screen App
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(DeepCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(22.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ElevatedCardBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "APP",
                                    color = SkyCyanBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Layar Aktif",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = screenApp.ifBlank { "Pochi Home" },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Full-Width Bento Card: Add Permanent Memory for Tuan Andre
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(DeepCardBlack)
                        .border(1.dp, CardBorderDark, RoundedCornerShape(24.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Simpan Memori Baru untuk Pochi",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicTextField(
                            value = newMemoryText,
                            onValueChange = { newMemoryText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            cursorBrush = SolidColor(SkyCyanBlue),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MonolithBlack)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.horizontalGradient(listOf(RoyalBlue, ElectricBlue)))
                                .clickable {
                                    if (newMemoryText.isNotBlank()) {
                                        db.saveMemory("preferensi", newMemoryText.trim())
                                        newMemoryText = ""
                                        memories = db.getAllMemories().take(30)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Simpan",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Lower Platinum White Sheet (`Memori Tersimpan` + Dark Pill Cards)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Memori Tersimpan",
                    color = TextDarkSheet,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${memories.size} memori",
                    color = Color(0xFF475569),
                    fontSize = 12.sp,
                    textDecoration = TextDecoration.Underline
                )
            }

            if (memories.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(MonolithBlack)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Pochi selalu memanggil Tuan Andre",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Memori Inti • Permanen",
                            color = TextSecondaryMuted,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "AKTIF",
                        color = SkyCyanBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                memories.forEach { mem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(MonolithBlack)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mem.content,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Kategori: ${mem.category.uppercase(Locale.US)}",
                                color = TextSecondaryMuted,
                                fontSize = 10.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Hapus",
                            tint = Color(0xFF64748B),
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    db.deleteMemory(mem.id)
                                    memories = db.getAllMemories().take(30)
                                }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(92.dp))
        }
    }
}

// ============================================================================
// SCREEN 4 (⋮ TAB): POCHI AI ENGINE, API KEY & SYSTEM SETTINGS
// ============================================================================
@Composable
private fun PochiEngineSettingsScreen(
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

    val selectedCity by PochiPrefs.cityFlow.collectAsState()
    val islandRunning by DynamicIslandService.isRunningFlow.collectAsState()

    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SheetPlatinum)
            .verticalScroll(scroll)
    ) {
        // Upper Pitch-Black Monolith Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                .background(MonolithBlack)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Pochi AI Setup",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Konfigurasi API Key, Model & Dynamic Notch Tuan Andre",
                        color = TextSecondaryMuted,
                        fontSize = 12.sp
                    )
                }
                PochiMascot(
                    state = BotState.IDLE,
                    emoteOverride = PochiEmote.WINK_LEFT,
                    size = 40.dp,
                    showBlackBacking = false
                )
            }

            // 1. AI Provider & API Key Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DeepCardBlack)
                    .border(1.dp, CardBorderDark, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "1. Pilih Provider Otak AI & Simpan API Key",
                    color = SkyCyanBlue,
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (active) Brush.horizontalGradient(listOf(RoyalBlue, DeepNavyBlue))
                                    else Brush.horizontalGradient(listOf(ElevatedCardBlack, ElevatedCardBlack))
                                )
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
                                color = if (active) Color.White else TextSecondaryMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // API Key Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "API Key (${selectedProvider.label})",
                        color = TextSecondaryMuted,
                        fontSize = 11.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElevatedCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                savedBanner = false
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            cursorBrush = SolidColor(SkyCyanBlue),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = "Tampilkan Key",
                            tint = TextSecondaryMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { showApiKey = !showApiKey }
                        )
                    }
                }

                // Model Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Model (${selectedProvider.defaultModel})",
                        color = TextSecondaryMuted,
                        fontSize = 11.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ElevatedCardBlack)
                            .border(1.dp, CardBorderDark, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = modelInput,
                            onValueChange = {
                                modelInput = it
                                savedBanner = false
                            },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(SkyCyanBlue),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(RoyalBlue, ElectricBlue)))
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
                        text = if (savedBanner) "Tersimpan untuk Tuan Andre" else "Simpan Pengaturan AI",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 2. City & Notch Service Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(DeepCardBlack)
                    .border(1.dp, CardBorderDark, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "2. Kota Jadwal Sholat & Cuaca Live",
                    color = SkyCyanBlue,
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
                                .background(if (active) RoyalBlue else ElevatedCardBlack)
                                .clickable { PochiPrefs.saveCity(context, city) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = city,
                                color = Color.White,
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
                                .background(if (active) RoyalBlue else ElevatedCardBlack)
                                .clickable { PochiPrefs.saveCity(context, city) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = city,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Status Dynamic Notch",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (islandRunning) "Aktif di Tengah Atas & Pinggir Miring" else "Sedang Nonaktif",
                            color = TextSecondaryMuted,
                            fontSize = 11.sp
                        )
                    }
                    ReferencePillSwitch(
                        checked = islandRunning,
                        onCheckedChange = {
                            if (islandRunning) DynamicIslandService.stop(context)
                            else DynamicIslandService.start(context)
                        }
                    )
                }
            }
        }

        // Bottom Platinum Sheet: System Permissions Checklist
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Status Izin Sistem Android",
                color = TextDarkSheet,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            PermissionStatusRow(
                title = "Izin Overlay Dynamic Notch",
                subtitle = "Menampilkan Notch di atas navigasi ponsel",
                granted = canOverlay
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }

            PermissionStatusRow(
                title = "Aksesibilitas Konteks Layar",
                subtitle = "Mendeteksi apa yang sedang Tuan Andre lakukan",
                granted = hasAccessibility
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            PermissionStatusRow(
                title = "Akses Notifikasi & Media",
                subtitle = "Membaca lagu yang diputar & pesan masuk",
                granted = hasNotification
            ) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }

            PermissionStatusRow(
                title = "Bebas Optimasi Baterai",
                subtitle = "Menjaga Pochi tetap berjalan 24/7 di latar belakang",
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

            Spacer(modifier = Modifier.height(92.dp))
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SheetCardWhite)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextDarkSheet,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (granted) RoyalBlue.copy(alpha = 0.14f) else Color(0xFFEF4444).copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = if (granted) "AKTIF" else "AKTIFKAN",
                color = if (granted) RoyalBlue else Color(0xFFEF4444),
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
            .background(MonolithBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(RoyalBlue, DeepNavyBlue, MonolithBlack)))
                    .border(1.5.dp, SkyCyanBlue, CircleShape),
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
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Pochi hadir sebagai Notch Dynamic Island di tengah atas layar HP Tuan Andre (dan otomatis menyesuaikan di pinggir layar saat miring), lengkap dengan kartu visual kripto, grafik gradien, cuaca, jadwal sholat, dan drop file.",
                color = TextSecondaryMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            PermissionStatusRow(
                title = "1. Izin Overlay Notch (Wajib)",
                subtitle = "Agar Notch Pochi menempel mulus di atas layar",
                granted = canOverlay
            ) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }

            PermissionStatusRow(
                title = "2. Aksesibilitas Konteks Layar",
                subtitle = "Agar mata Pochi bereaksi terhadap aktivitas layar",
                granted = hasAccessibility
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            PermissionStatusRow(
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
                    .background(Brush.horizontalGradient(listOf(RoyalBlue, ElectricBlue)))
                    .clickable { onFinish() }
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Mulai Pochi Sekarang",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
