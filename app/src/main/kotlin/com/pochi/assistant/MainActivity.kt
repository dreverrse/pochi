package com.pochi.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
            PochiAppRoot()
        }
    }
}

// ============================================================================
// EXPRESSIVE & NON-MONOTONOUS TYPOGRAPHY SYSTEM
// ============================================================================
data class PochiFontPack(
    val id: String,
    val label: String,
    val displayFamily: FontFamily,
    val titleFamily: FontFamily,
    val bodyFamily: FontFamily,
    val accentFamily: FontFamily,
    val monoFamily: FontFamily = FontFamily.Monospace
)

fun getPochiFontPack(styleId: String): PochiFontPack {
    return when (styleId) {
        "grotesk" -> PochiFontPack(
            id = "grotesk",
            label = "Tech Grotesk",
            displayFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD)),
            titleFamily = FontFamily(Typeface.create("sans-serif-black", Typeface.NORMAL)),
            bodyFamily = FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL)),
            accentFamily = FontFamily.Monospace
        )
        "editorial" -> PochiFontPack(
            id = "editorial",
            label = "Editorial Serif",
            displayFamily = FontFamily(Typeface.create("serif", Typeface.BOLD)),
            titleFamily = FontFamily(Typeface.create("serif", Typeface.BOLD)),
            bodyFamily = FontFamily(Typeface.create("sans-serif", Typeface.NORMAL)),
            accentFamily = FontFamily(Typeface.create("serif", Typeface.ITALIC))
        )
        else -> PochiFontPack(
            id = "jakarta",
            label = "Jakarta Geometric",
            displayFamily = FontFamily(Typeface.create("sans-serif-black", Typeface.NORMAL)),
            titleFamily = FontFamily(Typeface.create("sans-serif-medium", Typeface.BOLD)),
            bodyFamily = FontFamily(Typeface.create("sans-serif", Typeface.NORMAL)),
            accentFamily = FontFamily(Typeface.create("serif", Typeface.ITALIC))
        )
    }
}

// ============================================================================
// COLOR PALETTE (Matches Reference Design Sheet Light & Dark Modes)
// ============================================================================
data class PochiPalette(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val cardElevated: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val pillActiveBg: Color,
    val pillActiveText: Color,
    val pillInactiveBg: Color,
    val accentYellow: Color = Color(0xFFFACC15),
    val accentBlue: Color = Color(0xFF3B82F6),
    val tileTodayBg: Color,
    val tileAgentBg: Color,
    val tileFocusBg: Color,
    val tileDeckBg: Color
)

fun rememberPochiPalette(isDark: Boolean): PochiPalette {
    return if (isDark) {
        PochiPalette(
            isDark = true,
            bg = Color(0xFF0E1015),
            card = Color(0xFF181B23),
            cardElevated = Color(0xFF20242F),
            cardBorder = Color(0xFF282D3B),
            textPrimary = Color(0xFFF9FAFB),
            textSecondary = Color(0xFF9CA3AF),
            textMuted = Color(0xFF6B7280),
            pillActiveBg = Color(0xFFF9FAFB),
            pillActiveText = Color(0xFF0E1015),
            pillInactiveBg = Color(0xFF1F2430),
            tileTodayBg = Color(0xFF172554),
            tileAgentBg = Color(0xFF14532D),
            tileFocusBg = Color(0xFF422006),
            tileDeckBg = Color(0xFF3B0764)
        )
    } else {
        PochiPalette(
            isDark = false,
            bg = Color(0xFFF6F7FB),
            card = Color(0xFFFFFFFF),
            cardElevated = Color(0xFFFFFFFF),
            cardBorder = Color(0xFFEFF1F6),
            textPrimary = Color(0xFF111827),
            textSecondary = Color(0xFF6B7280),
            textMuted = Color(0xFF9CA3AF),
            pillActiveBg = Color(0xFF14151A),
            pillActiveText = Color(0xFFFFFFFF),
            pillInactiveBg = Color(0xFFEEF0F6),
            tileTodayBg = Color(0xFFE0ECFF),
            tileAgentBg = Color(0xFFDCFCE7),
            tileFocusBg = Color(0xFFFEF3C7),
            tileDeckBg = Color(0xFFF3E8FF)
        )
    }
}

// ============================================================================
// NAVIGATION DESTINATIONS
// ============================================================================
enum class MainTab { HOME, AGENT, FOCUS, DECK }

sealed class AppDestination {
    data object Splash : AppDestination()
    data class Onboarding(val step: Int) : AppDestination() // 0, 1, 2
    data class Main(val tab: MainTab = MainTab.HOME) : AppDestination()
    data object TodayDetail : AppDestination()
    data class TaskDetail(val taskId: String) : AppDestination()
    data object Settings : AppDestination()
}

data class AgentChatMessage(
    val id: Long = System.nanoTime(),
    val text: String,
    val isUser: Boolean,
    val time: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
)

// ============================================================================
// ROOT COMPOSABLE
// ============================================================================
@Composable
fun PochiAppRoot() {
    val context = LocalContext.current
    val isDark by PochiPrefs.darkModeFlow.collectAsState()
    val lang by PochiPrefs.langFlow.collectAsState()
    val onboardingDone by PochiPrefs.onboardingDoneFlow.collectAsState()
    val iconStyle by PochiPrefs.iconStyleFlow.collectAsState()
    val fontStyle by PochiPrefs.fontStyleFlow.collectAsState()

    val palette = rememberPochiPalette(isDark)
    val fonts = remember(fontStyle) { getPochiFontPack(fontStyle) }

    var destination by remember { mutableStateOf<AppDestination>(AppDestination.Splash) }
    var activeTab by remember { mutableStateOf(MainTab.HOME) }

    // Shared Pomodoro state so Home & Focus tabs stay in sync
    var focusPhase by remember { mutableIntStateOf(0) } // 0 = Focus (25m), 1 = Short Break (5m), 2 = Long Break (15m)
    var focusSecondsLeft by remember { mutableIntStateOf(25 * 60) }
    var focusRunning by remember { mutableStateOf(false) }

    LaunchedEffect(focusRunning, focusSecondsLeft) {
        if (focusRunning && focusSecondsLeft > 0) {
            delay(1000L)
            focusSecondsLeft -= 1
            if (focusSecondsLeft == 0) {
                focusRunning = false
                if (focusPhase == 0) {
                    PochiPrefs.incrementFocusSessions(context)
                }
            }
        }
    }

    // Shared Agent chat messages
    val chatMessages = remember { mutableStateListOf<AgentChatMessage>() }

    // Splash auto-transition
    LaunchedEffect(destination) {
        if (destination is AppDestination.Splash) {
            delay(1650L)
            destination = if (onboardingDone) {
                AppDestination.Main(activeTab)
            } else {
                AppDestination.Onboarding(0)
            }
        }
    }

    BackHandler(enabled = destination !is AppDestination.Splash && destination !is AppDestination.Onboarding) {
        when (val current = destination) {
            is AppDestination.TaskDetail -> destination = AppDestination.TodayDetail
            is AppDestination.TodayDetail -> destination = AppDestination.Main(MainTab.HOME)
            is AppDestination.Settings -> destination = AppDestination.Main(activeTab)
            is AppDestination.Main -> {
                if (current.tab != MainTab.HOME) {
                    activeTab = MainTab.HOME
                    destination = AppDestination.Main(MainTab.HOME)
                }
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (destination is AppDestination.Splash) Color(0xFF0B0C10) else palette.bg
            )
    ) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                fadeIn(tween(260)) togetherWith fadeOut(tween(200))
            },
            label = "pochi_nav"
        ) { target ->
            when (target) {
                is AppDestination.Splash -> {
                    PochiSplashScreen(fonts = fonts)
                }
                is AppDestination.Onboarding -> {
                    PochiOnboardingScreen(
                        step = target.step,
                        fonts = fonts,
                        iconStyle = iconStyle,
                        lang = lang,
                        onNext = {
                            if (target.step < 2) {
                                destination = AppDestination.Onboarding(target.step + 1)
                            } else {
                                PochiPrefs.setOnboardingDone(context, true)
                                destination = AppDestination.Main(MainTab.HOME)
                            }
                        },
                        onSkip = {
                            PochiPrefs.setOnboardingDone(context, true)
                            destination = AppDestination.Main(MainTab.HOME)
                        }
                    )
                }
                is AppDestination.Main -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (target.tab) {
                            MainTab.HOME -> PochiHomeScreen(
                                palette = palette,
                                fonts = fonts,
                                iconStyle = iconStyle,
                                lang = lang,
                                onNavigateTab = { newTab ->
                                    activeTab = newTab
                                    destination = AppDestination.Main(newTab)
                                },
                                onOpenTodayDetail = { destination = AppDestination.TodayDetail },
                                onOpenTaskDetail = { taskId -> destination = AppDestination.TaskDetail(taskId) },
                                onOpenSettings = { destination = AppDestination.Settings }
                            )
                            MainTab.AGENT -> PochiAgentChatScreen(
                                palette = palette,
                                fonts = fonts,
                                iconStyle = iconStyle,
                                lang = lang,
                                messages = chatMessages,
                                onOpenSettings = { destination = AppDestination.Settings },
                                onOpenDrawerHome = {
                                    activeTab = MainTab.HOME
                                    destination = AppDestination.Main(MainTab.HOME)
                                }
                            )
                            MainTab.FOCUS -> PochiFocusScreen(
                                palette = palette,
                                fonts = fonts,
                                lang = lang,
                                phase = focusPhase,
                                secondsLeft = focusSecondsLeft,
                                isRunning = focusRunning,
                                onSelectPhase = { newPhase ->
                                    focusPhase = newPhase
                                    focusRunning = false
                                    focusSecondsLeft = when (newPhase) {
                                        1 -> 5 * 60
                                        2 -> 15 * 60
                                        else -> 25 * 60
                                    }
                                },
                                onToggleRun = { focusRunning = !focusRunning },
                                onReset = {
                                    focusRunning = false
                                    focusSecondsLeft = when (focusPhase) {
                                        1 -> 5 * 60
                                        2 -> 15 * 60
                                        else -> 25 * 60
                                    }
                                },
                                onOpenTaskDetail = { taskId -> destination = AppDestination.TaskDetail(taskId) }
                            )
                            MainTab.DECK -> PochiDeckScreen(
                                palette = palette,
                                fonts = fonts,
                                lang = lang
                            )
                        }

                        // Floating Bottom Navigation Bar (Shown on all 4 main tabs)
                        PochiFloatingBottomNav(
                            palette = palette,
                            fonts = fonts,
                            currentTab = target.tab,
                            onSelectTab = { newTab ->
                                activeTab = newTab
                                destination = AppDestination.Main(newTab)
                            },
                            onLightningClick = {
                                toggleDynamicIslandOverlay(context, lang)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
                is AppDestination.TodayDetail -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PochiTodayDetailScreen(
                            palette = palette,
                            fonts = fonts,
                            lang = lang,
                            onBack = { destination = AppDestination.Main(MainTab.HOME) },
                            onOpenTaskDetail = { taskId -> destination = AppDestination.TaskDetail(taskId) }
                        )
                        PochiFloatingBottomNav(
                            palette = palette,
                            fonts = fonts,
                            currentTab = MainTab.HOME,
                            onSelectTab = { newTab ->
                                activeTab = newTab
                                destination = AppDestination.Main(newTab)
                            },
                            onLightningClick = { toggleDynamicIslandOverlay(context, lang) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                }
                is AppDestination.TaskDetail -> {
                    PochiTaskDetailScreen(
                        taskId = target.taskId,
                        palette = palette,
                        fonts = fonts,
                        lang = lang,
                        onBack = { destination = AppDestination.TodayDetail }
                    )
                }
                is AppDestination.Settings -> {
                    PochiSettingsScreen(
                        palette = palette,
                        fonts = fonts,
                        iconStyle = iconStyle,
                        lang = lang,
                        onBack = { destination = AppDestination.Main(activeTab) },
                        onReplayOnboarding = {
                            PochiPrefs.setOnboardingDone(context, false)
                            destination = AppDestination.Onboarding(0)
                        }
                    )
                }
            }
        }
    }
}

private fun toggleDynamicIslandOverlay(context: Context, lang: String) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(
            context,
            if (lang == "id") "Izinkan 'Tampilkan di atas aplikasi lain' untuk mengaktifkan Pochi Dynamic Island"
            else "Allow 'Display over other apps' to activate Pochi Dynamic Island",
            Toast.LENGTH_SHORT
        ).show()
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    }
    val intent = Intent(context, DynamicIslandService::class.java)
    if (DynamicIslandService.isRunning.value) {
        context.stopService(intent)
        Toast.makeText(
            context,
            if (lang == "id") "Pochi Island dinonaktifkan" else "Pochi Island hidden",
            Toast.LENGTH_SHORT
        ).show()
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Toast.makeText(
            context,
            if (lang == "id") "⚡ Pochi Dynamic Island aktif di status bar!" else "⚡ Pochi Dynamic Island active at status bar!",
            Toast.LENGTH_SHORT
        ).show()
    }
}

// ============================================================================
// SIGNATURE 3D MATTE-BLACK POCHI SQUIRCLE MASCOT & APP ICON VARIANTS
// (Matches the Reference Image's App Icon, Light/Dark/Accent/Adaptive Variants,
// and Hero Mascot with Soft Pastel Aura Glow & Happy Curved `^ ^` White Eyes)
// ============================================================================
@Composable
fun PochiSquircleMascot(
    modifier: Modifier = Modifier,
    width: Dp = 148.dp,
    height: Dp = 114.dp,
    cornerRadius: Dp = 36.dp,
    style: String = "dark", // "dark", "light", "accent", "adaptive"
    showPastelAura: Boolean = true,
    interactive: Boolean = true
) {
    var expressionMode by remember { mutableIntStateOf(0) } // 0 = happy ^ ^, 1 = wink ^ -, 2 = sparkly oval, 3 = heart/joy
    var lastTapMs by remember { mutableLongStateOf(0L) }

    // Periodic gentle blink & idle float
    val infinite = rememberInfiniteTransition(label = "pochi_hero_anim")
    val floatY by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float_y"
    )
    val auraScale by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aura_scale"
    )

    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3600L)
            isBlinking = true
            delay(130L)
            isBlinking = false
        }
    }

    val tapScale by animateFloatAsState(
        targetValue = if (System.currentTimeMillis() - lastTapMs < 180L) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tap_scale"
    )

    Box(
        modifier = modifier
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        lastTapMs = System.currentTimeMillis()
                        expressionMode = (expressionMode + 1) % 4
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        // Soft radial pastel aura behind mascot (matches reference image Home & Onboarding)
        if (showPastelAura) {
            Canvas(
                modifier = Modifier.size(width * 1.65f, height * 1.65f)
            ) {
                val r = size.minDimension * 0.48f * auraScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFC084FC).copy(alpha = 0.30f),
                            Color(0xFF93C5FD).copy(alpha = 0.22f),
                            Color.Transparent
                        ),
                        center = Offset(center.x - r * 0.24f, center.y - r * 0.1f),
                        radius = r
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFDE047).copy(alpha = 0.28f),
                            Color(0xFFFDBA74).copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(center.x + r * 0.28f, center.y + r * 0.12f),
                        radius = r * 0.95f
                    )
                )
            }
        }

        // Adaptive layered back card if style == "adaptive"
        if (style == "adaptive") {
            Box(
                modifier = Modifier
                    .offset(y = (floatY * 0.6f).dp - (height * 0.11f))
                    .size(width * 0.86f, height * 0.86f)
                    .clip(RoundedCornerShape(cornerRadius * 0.9f))
                    .background(Color(0xFFBFDBFE))
            )
        }

        // Main 3D Squircle Body + Eyes drawn in crisp Canvas
        Canvas(
            modifier = Modifier
                .offset(y = floatY.dp)
                .size(width * tapScale, height * tapScale)
                .shadow(
                    elevation = if (showPastelAura) 18.dp else 6.dp,
                    shape = RoundedCornerShape(cornerRadius),
                    spotColor = Color.Black.copy(alpha = 0.35f)
                )
        ) {
            val cr = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx())
            val bodyBrush = when (style) {
                "light" -> Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFF3F4F6), Color(0xFFE5E7EB))
                )
                "accent" -> Brush.verticalGradient(
                    listOf(Color(0xFFFDE047), Color(0xFFFACC15), Color(0xFFEAB308))
                )
                else -> Brush.verticalGradient(
                    listOf(Color(0xFF2B2D38), Color(0xFF15161C), Color(0xFF090A0D))
                )
            }

            // Squircle base
            drawRoundRect(
                brush = bodyBrush,
                cornerRadius = cr
            )

            // Subtle 3D glossy inner top rim highlight
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (style == "light") 0.8f else 0.18f),
                        Color.Transparent
                    )
                ),
                cornerRadius = cr,
                style = Stroke(width = 2.dp.toPx())
            )

            val eyeColor = when (style) {
                "light", "accent" -> Color(0xFF14151A)
                else -> Color(0xFFFFFFFF)
            }
            val glowColor = when (style) {
                "adaptive" -> Color(0xFF60A5FA).copy(alpha = 0.45f)
                "light", "accent" -> Color.Black.copy(alpha = 0.10f)
                else -> Color.White.copy(alpha = 0.25f)
            }

            val cx = size.width / 2f
            val cy = size.height * 0.52f
            val eyeSpan = size.width * 0.22f
            val eyeHalfW = size.width * 0.115f
            val archHeight = size.height * 0.145f
            val strokePx = (size.minDimension * 0.115f).coerceAtLeast(6f)

            val leftCenter = Offset(cx - eyeSpan, cy)
            val rightCenter = Offset(cx + eyeSpan, cy)

            fun drawHappyArchEye(centerPt: Offset, color: Color, widthPx: Float, flatBlink: Boolean = false) {
                val path = Path().apply {
                    if (flatBlink) {
                        moveTo(centerPt.x - eyeHalfW, centerPt.y)
                        lineTo(centerPt.x + eyeHalfW, centerPt.y)
                    } else {
                        moveTo(centerPt.x - eyeHalfW, centerPt.y + archHeight * 0.28f)
                        cubicTo(
                            centerPt.x - eyeHalfW * 0.55f, centerPt.y - archHeight,
                            centerPt.x + eyeHalfW * 0.55f, centerPt.y - archHeight,
                            centerPt.x + eyeHalfW, centerPt.y + archHeight * 0.28f
                        )
                    }
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            when {
                isBlinking -> {
                    drawHappyArchEye(leftCenter, eyeColor, strokePx * 0.7f, flatBlink = true)
                    drawHappyArchEye(rightCenter, eyeColor, strokePx * 0.7f, flatBlink = true)
                }
                expressionMode == 1 -> {
                    // Wink (^ -)
                    drawHappyArchEye(leftCenter, glowColor, strokePx * 1.45f)
                    drawHappyArchEye(leftCenter, eyeColor, strokePx)
                    drawHappyArchEye(rightCenter, eyeColor, strokePx * 0.85f, flatBlink = true)
                }
                expressionMode == 2 -> {
                    // Curious glowing ovals (o o)
                    val ovalW = eyeHalfW * 1.25f
                    val ovalH = archHeight * 1.65f
                    drawRoundRect(
                        color = glowColor,
                        topLeft = Offset(leftCenter.x - ovalW * 0.65f, leftCenter.y - ovalH * 0.65f),
                        size = Size(ovalW * 1.3f, ovalH * 1.3f),
                        cornerRadius = CornerRadius(ovalW, ovalW)
                    )
                    drawRoundRect(
                        color = eyeColor,
                        topLeft = Offset(leftCenter.x - ovalW * 0.5f, leftCenter.y - ovalH * 0.5f),
                        size = Size(ovalW, ovalH),
                        cornerRadius = CornerRadius(ovalW, ovalW)
                    )
                    drawRoundRect(
                        color = glowColor,
                        topLeft = Offset(rightCenter.x - ovalW * 0.65f, rightCenter.y - ovalH * 0.65f),
                        size = Size(ovalW * 1.3f, ovalH * 1.3f),
                        cornerRadius = CornerRadius(ovalW, ovalW)
                    )
                    drawRoundRect(
                        color = eyeColor,
                        topLeft = Offset(rightCenter.x - ovalW * 0.5f, rightCenter.y - ovalH * 0.5f),
                        size = Size(ovalW, ovalH),
                        cornerRadius = CornerRadius(ovalW, ovalW)
                    )
                }
                else -> {
                    // Signature Pochi Happy Curved Eyes (^ ^) from the reference image
                    drawHappyArchEye(leftCenter, glowColor, strokePx * 1.48f)
                    drawHappyArchEye(rightCenter, glowColor, strokePx * 1.48f)
                    drawHappyArchEye(leftCenter, eyeColor, strokePx)
                    drawHappyArchEye(rightCenter, eyeColor, strokePx)
                }
            }
        }
    }
}

// ============================================================================
// SPLASH SCREEN (Bottom-Left 1st Screen in Reference Image)
// ============================================================================
@Composable
fun PochiSplashScreen(fonts: PochiFontPack) {
    val infinite = rememberInfiniteTransition(label = "splash_bar")
    val progress by infinite.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "bar"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0F14)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing white ^ ^ eyes directly on pitch-black background (exact match to Splash Screen in image)
            Canvas(modifier = Modifier.size(120.dp, 54.dp)) {
                val cx = size.width / 2f
                val cy = size.height * 0.55f
                val span = size.width * 0.25f
                val halfW = size.width * 0.14f
                val archH = size.height * 0.36f
                val strokeW = 10.dp.toPx()

                fun drawSplashEye(centerPt: Offset) {
                    val path = Path().apply {
                        moveTo(centerPt.x - halfW, centerPt.y + archH * 0.2f)
                        cubicTo(
                            centerPt.x - halfW * 0.55f, centerPt.y - archH,
                            centerPt.x + halfW * 0.55f, centerPt.y - archH,
                            centerPt.x + halfW, centerPt.y + archH * 0.2f
                        )
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.25f),
                        style = Stroke(width = strokeW * 1.6f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                drawSplashEye(Offset(cx - span, cy))
                drawSplashEye(Offset(cx + span, cy))
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Pochi",
                fontFamily = fonts.displayFamily,
                fontWeight = FontWeight.Black,
                fontSize = 36.sp,
                letterSpacing = (-0.8).sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your AI Companion",
                fontFamily = fonts.bodyFamily,
                fontSize = 14.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        // Bottom minimalist progress bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
                .width(120.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

// ============================================================================
// ONBOARDING FLOW (Onboarding 1, 2, 3 from Bottom-Left of Reference Image)
// ============================================================================
@Composable
fun PochiOnboardingScreen(
    step: Int,
    fonts: PochiFontPack,
    iconStyle: String,
    lang: String,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Top-right Skip button on steps 0 & 1
        if (step < 2) {
            Text(
                text = if (lang == "id") "Lewati" else "Skip",
                fontFamily = fonts.titleFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .clickable { onSkip() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 36.dp, bottom = 84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                0 -> {
                    // ONBOARDING 1: Welcome to Pochi 👋
                    PochiSquircleMascot(
                        width = 148.dp,
                        height = 114.dp,
                        cornerRadius = 36.dp,
                        style = iconStyle,
                        showPastelAura = true
                    )
                    Spacer(modifier = Modifier.height(42.dp))
                    Text(
                        text = if (lang == "id") "Welcome to\nPochi 👋" else "Welcome to\nPochi 👋",
                        fontFamily = fonts.displayFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        letterSpacing = (-0.7).sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (lang == "id") {
                            "Teman AI pintar untuk hari yang lebih\ncerdas dan produktif."
                        } else {
                            "Your AI companion for\na smarter and more\nproductive day."
                        },
                        fontFamily = fonts.bodyFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF6B7280)
                    )
                }

                1 -> {
                    // ONBOARDING 2: Today, Agent, Focus & Deck (Orbiting Pastel Feature Icons)
                    Box(
                        modifier = Modifier.size(250.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Subtle orbit ring
                        Canvas(modifier = Modifier.size(210.dp)) {
                            drawCircle(
                                color = Color(0xFFE5E7EB),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }

                        // Center Pochi Mascot
                        PochiSquircleMascot(
                            width = 118.dp,
                            height = 92.dp,
                            cornerRadius = 28.dp,
                            style = iconStyle,
                            showPastelAura = true
                        )

                        // Top-Left: Blue Calendar Bubble
                        OrbitFeatureBubble(
                            icon = Icons.Rounded.CalendarMonth,
                            bg = Color(0xFFDBEAFE),
                            tint = Color(0xFF2563EB),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 18.dp, y = 8.dp)
                        )
                        // Top-Right: Yellow Bolt Bubble
                        OrbitFeatureBubble(
                            icon = Icons.Rounded.Bolt,
                            bg = Color(0xFFFEF3C7),
                            tint = Color(0xFFD97706),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-18).dp, y = 8.dp)
                        )
                        // Bottom-Left: Green Agent Bubble
                        OrbitFeatureBubble(
                            icon = Icons.Rounded.SmartToy,
                            bg = Color(0xFFDCFCE7),
                            tint = Color(0xFF16A34A),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = 8.dp, y = (-32).dp)
                        )
                        // Bottom-Right: Indigo Code Bubble
                        OrbitFeatureBubble(
                            icon = Icons.Rounded.Code,
                            bg = Color(0xFFE0E7FF),
                            tint = Color(0xFF4F46E5),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = (-8).dp, y = (-32).dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Today, Agent,\nFocus & Deck",
                        fontFamily = fonts.displayFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        letterSpacing = (-0.6).sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (lang == "id") {
                            "Semua kebutuhan produktivitasmu\ndalam satu tempat."
                        } else {
                            "Everything you need\nin one place."
                        },
                        fontFamily = fonts.bodyFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF6B7280)
                    )
                }

                else -> {
                    // ONBOARDING 3: Smarter. Simpler. With You. + Get Started Pill
                    Box(
                        modifier = Modifier.size(230.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Decorative soft pastel orbs matching Onboarding 3 in the reference image
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFFFDE047).copy(alpha = 0.55f), Color.Transparent)
                                ),
                                radius = 58.dp.toPx(),
                                center = Offset(size.width * 0.22f, size.height * 0.22f)
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFFC4B5FD).copy(alpha = 0.55f), Color.Transparent)
                                ),
                                radius = 54.dp.toPx(),
                                center = Offset(size.width * 0.80f, size.height * 0.26f)
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFFBBF7D0).copy(alpha = 0.55f), Color.Transparent)
                                ),
                                radius = 52.dp.toPx(),
                                center = Offset(size.width * 0.78f, size.height * 0.78f)
                            )
                        }

                        PochiSquircleMascot(
                            width = 128.dp,
                            height = 98.dp,
                            cornerRadius = 30.dp,
                            style = iconStyle,
                            showPastelAura = false
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = "Smarter.\nSimpler.\nWith You.",
                        fontFamily = fonts.displayFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        letterSpacing = (-0.8).sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF111827)
                    )
                }
            }
        }

        // Bottom Controls
        if (step < 2) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Indicator Dots (● • •)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { idx ->
                        val active = idx == step
                        Box(
                            modifier = Modifier
                                .size(if (active) 9.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) Color(0xFF111827) else Color(0xFFD1D5DB)
                                )
                        )
                    }
                }

                // Circular Black Next Button (→)
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .shadow(10.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFF14151A))
                        .clickable { onNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        } else {
            // Full-width Black Pill CTA "Get Started"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .height(56.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF14151A))
                    .clickable { onNext() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (lang == "id") "Get Started" else "Get Started",
                    fontFamily = fonts.titleFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun OrbitFeatureBubble(
    icon: ImageVector,
    bg: Color,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(52.dp)
            .shadow(8.dp, CircleShape, spotColor = tint.copy(alpha = 0.25f))
            .clip(CircleShape)
            .background(Color.White)
            .padding(6.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ============================================================================
// FLOATING BOTTOM NAVIGATION BAR (Top Row of Reference Image)
// ============================================================================
@Composable
fun PochiFloatingBottomNav(
    palette: PochiPalette,
    fonts: PochiFontPack,
    currentTab: MainTab,
    onSelectTab: (MainTab) -> Unit,
    onLightningClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val islandRunning by DynamicIslandService.isRunning.collectAsState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(34.dp),
                spotColor = Color.Black.copy(alpha = 0.16f)
            ),
        shape = RoundedCornerShape(34.dp),
        color = palette.card.copy(alpha = 0.97f),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. HOME
            BottomNavItem(
                selected = currentTab == MainTab.HOME,
                label = "Home",
                icon = Icons.Rounded.Home,
                palette = palette,
                fonts = fonts,
                onClick = { onSelectTab(MainTab.HOME) }
            )
            // 2. AGENT
            BottomNavItem(
                selected = currentTab == MainTab.AGENT,
                label = "Agent",
                icon = Icons.Rounded.SmartToy,
                palette = palette,
                fonts = fonts,
                onClick = { onSelectTab(MainTab.AGENT) }
            )
            // 3. FOCUS
            BottomNavItem(
                selected = currentTab == MainTab.FOCUS,
                label = "Focus",
                icon = Icons.Rounded.Timer,
                palette = palette,
                fonts = fonts,
                onClick = { onSelectTab(MainTab.FOCUS) }
            )
            // 4. DECK
            BottomNavItem(
                selected = currentTab == MainTab.DECK,
                label = "Deck",
                icon = Icons.Rounded.GridView,
                palette = palette,
                fonts = fonts,
                onClick = { onSelectTab(MainTab.DECK) }
            )

            // 5. Signature Golden-Yellow Lightning Button (⚡) on Right
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(
                        elevation = if (islandRunning) 12.dp else 6.dp,
                        shape = CircleShape,
                        spotColor = Color(0xFFF59E0B)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFDE047), Color(0xFFFACC15), Color(0xFFF59E0B))
                        )
                    )
                    .clickable { onLightningClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Toggle Pochi Dynamic Island",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    palette: PochiPalette,
    fonts: PochiFontPack,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (selected) palette.pillInactiveBg else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = if (selected) 14.dp else 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) palette.textPrimary else palette.textSecondary,
            modifier = Modifier.size(21.dp)
        )
        AnimatedVisibility(visible = selected) {
            Text(
                text = label,
                fontFamily = fonts.titleFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = palette.textPrimary
            )
        }
    }
}

// ============================================================================
// SCREEN 1: HOME SCREEN (Overview & Quick Access - Top-Left 1st Phone in Image)
// ============================================================================
@Composable
fun PochiHomeScreen(
    palette: PochiPalette,
    fonts: PochiFontPack,
    iconStyle: String,
    lang: String,
    onNavigateTab: (MainTab) -> Unit,
    onOpenTodayDetail: () -> Unit,
    onOpenTaskDetail: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val tasks by PochiPrefs.tasksFlow.collectAsState()
    val islandRunning by DynamicIslandService.isRunning.collectAsState()

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(hour, lang) {
        when {
            hour in 4..11 -> if (lang == "id") "Good Morning" else "Good Morning"
            hour in 12..17 -> if (lang == "id") "Good Afternoon" else "Good Afternoon"
            else -> if (lang == "id") "Good Evening" else "Good Evening"
        }
    }

    val doneCount = tasks.count { it.done }
    val totalCount = tasks.size.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 12.dp, bottom = 110.dp)
    ) {
        // Header Row: "Good Morning / Pochi ✦" + Notification Bell Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = greeting,
                    fontFamily = fonts.titleFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = palette.textSecondary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Pochi",
                        fontFamily = fonts.displayFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        letterSpacing = (-0.8).sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "✦",
                        fontSize = 20.sp,
                        color = palette.textSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your AI companion\nfor Today, Agent, Focus & Deck.",
                    fontFamily = fonts.bodyFamily,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    color = palette.textSecondary
                )
            }

            // Notification / Settings Bell Icon with Red Badge Dot
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.08f))
                    .clip(CircleShape)
                    .background(palette.card)
                    .border(1.dp, palette.cardBorder, CircleShape)
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = "Settings & Notifications",
                    tint = palette.textPrimary,
                    modifier = Modifier.size(21.dp)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-10).dp, y = 10.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Center Hero: 3D Matte-Black Pochi Squircle Mascot with Pastel Aura Glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp),
            contentAlignment = Alignment.Center
        ) {
            PochiSquircleMascot(
                width = 152.dp,
                height = 114.dp,
                cornerRadius = 36.dp,
                style = iconStyle,
                showPastelAura = true,
                interactive = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 4 Pastel Quick-Access Tiles: Today | Agent | Focus | Deck
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HomeQuickTile(
                label = "Today",
                icon = Icons.Rounded.CalendarMonth,
                tileBg = palette.tileTodayBg,
                iconTint = Color(0xFF2563EB),
                palette = palette,
                fonts = fonts,
                onClick = onOpenTodayDetail
            )
            HomeQuickTile(
                label = "Agent",
                icon = Icons.Rounded.SmartToy,
                tileBg = palette.tileAgentBg,
                iconTint = Color(0xFF16A34A),
                palette = palette,
                fonts = fonts,
                onClick = { onNavigateTab(MainTab.AGENT) }
            )
            HomeQuickTile(
                label = "Focus",
                icon = Icons.Rounded.Bolt,
                tileBg = palette.tileFocusBg,
                iconTint = Color(0xFFD97706),
                palette = palette,
                fonts = fonts,
                onClick = { onNavigateTab(MainTab.FOCUS) }
            )
            HomeQuickTile(
                label = "Deck",
                icon = Icons.Rounded.GridView,
                tileBg = palette.tileDeckBg,
                iconTint = Color(0xFF7C3AED),
                palette = palette,
                fonts = fonts,
                onClick = { onNavigateTab(MainTab.DECK) }
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Today's Plan Card (Matches Reference Image: "Today's Plan  3/5 completed" + Checklist)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.06f)),
            shape = RoundedCornerShape(24.dp),
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenTodayDetail() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Plan",
                        fontFamily = fonts.titleFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = palette.textPrimary
                    )
                    Text(
                        text = "$doneCount/$totalCount completed",
                        fontFamily = fonts.monoFamily,
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                tasks.take(4).forEachIndexed { index, task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenTaskDetail(task.id) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            PochiTaskCheckbox(
                                checked = task.done,
                                onToggle = { PochiPrefs.toggleTask(context, task.id) }
                            )
                            Text(
                                text = task.title,
                                fontFamily = fonts.bodyFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.5.sp,
                                color = if (task.done) palette.textSecondary else palette.textPrimary,
                                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Details",
                            tint = palette.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (index < tasks.take(4).lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(palette.cardBorder)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dynamic Island Status Pill Card (Quick toggle right from Home)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { toggleDynamicIslandOverlay(context, lang) },
            shape = RoundedCornerShape(20.dp),
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF14151A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "^ ^",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                    Column {
                        Text(
                            text = if (islandRunning) "Dynamic Island Active" else "Activate Dynamic Island",
                            fontFamily = fonts.titleFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = palette.textPrimary
                        )
                        Text(
                            text = if (lang == "id") "Menempel di bawah status bar • Ketuk ⚡ untuk on/off"
                            else "Flush below status bar • Tap ⚡ to toggle",
                            fontFamily = fonts.bodyFamily,
                            fontSize = 12.sp,
                            color = palette.textSecondary
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (islandRunning) Color(0xFF22C55E) else Color(0xFF9CA3AF))
                )
            }
        }
    }
}

@Composable
private fun HomeQuickTile(
    label: String,
    icon: ImageVector,
    tileBg: Color,
    iconTint: Color,
    palette: PochiPalette,
    fonts: PochiFontPack,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(6.dp, RoundedCornerShape(22.dp), spotColor = iconTint.copy(alpha = 0.18f))
                .clip(RoundedCornerShape(22.dp))
                .background(tileBg),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontFamily = fonts.titleFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = palette.textPrimary
        )
    }
}

@Composable
fun PochiTaskCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    size: Dp = 22.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (checked) Color(0xFF3B82F6) else Color.Transparent)
            .border(
                width = 2.dp,
                color = if (checked) Color(0xFF3B82F6) else Color(0xFFD1D5DB),
                shape = CircleShape
            )
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Checked",
                tint = Color.White,
                modifier = Modifier.size(size * 0.68f)
            )
        }
    }
}

// ============================================================================
// SCREEN 2: AGENT / CHAT SCREEN (Chat with Pochi - Top Row 2nd Phone in Image)
// Listening-only voice input via SpeechRecognizer (No TTS output)
// ============================================================================
@Composable
fun PochiAgentChatScreen(
    palette: PochiPalette,
    fonts: PochiFontPack,
    iconStyle: String,
    lang: String,
    messages: MutableList<AgentChatMessage>,
    onOpenSettings: () -> Unit,
    onOpenDrawerHome: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var showMoreActionsSheet by remember { mutableStateOf(false) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    fun submitPrompt(promptText: String) {
        val clean = promptText.trim()
        if (clean.isEmpty() || isThinking) return
        messages.add(AgentChatMessage(text = clean, isUser = true))
        inputText = ""
        isThinking = true
        scope.launch {
            val localResult = PochiAgent.tryHandleLocal(context, clean)
            val reply = if (localResult.handled) {
                localResult.reply
            } else {
                val apiKey = PochiPrefs.getApiKey(context)
                val provider = PochiPrefs.getProvider(context)
                if (apiKey.isBlank()) {
                    if (lang == "id") {
                        "✨ Aku mencatat pesanmu: \"$clean\".\nUntuk jawaban AI penuh (Gemini/OpenAI/Claude), masukkan API Key di Settings > Data & Storage, atau gunakan perintah langsung seperti 'buka WhatsApp', 'telepon Budi', 'cuaca', atau 'fokus'."
                    } else {
                        "✨ Got your prompt: \"$clean\".\nFor full LLM replies, set your API Key in Settings > Data & Storage, or try device commands like 'open WhatsApp', 'call Mom', 'weather', or 'start focus'."
                    }
                } else {
                    AiClient.ask(
                        prompt = clean,
                        apiKey = apiKey,
                        provider = provider,
                        systemContext = "You are Pochi, a friendly, concise AI companion for Today, Agent, Focus & Deck."
                    )
                }
            }
            messages.add(AgentChatMessage(text = reply, isUser = false))
            isThinking = false
        }
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
                    submitPrompt(spoken)
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
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "id") "id-ID" else "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer.startListening(intent)
        }
    }

    fun toggleListeningMic() {
        if (speechRecognizer == null) {
            Toast.makeText(context, "Speech recognition unavailable on this device", Toast.LENGTH_SHORT).show()
            return
        }
        if (isListening) {
            runCatching { speechRecognizer.stopListening() }
            isListening = false
            return
        }
        val hasPerm = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == "id") "id-ID" else "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            runCatching { speechRecognizer.startListening(intent) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 104.dp)
    ) {
        // Top App Bar: ☰ | Pochi (Your AI Agent) | ⚙
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularIconButton(
                icon = Icons.Rounded.Menu,
                palette = palette,
                onClick = onOpenDrawerHome
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Pochi",
                    fontFamily = fonts.displayFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = "Your AI Agent",
                    fontFamily = fonts.bodyFamily,
                    fontSize = 12.5.sp,
                    color = palette.textSecondary
                )
            }
            CircularIconButton(
                icon = Icons.Rounded.Settings,
                palette = palette,
                onClick = onOpenSettings
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Scrollable Center Area: Mascot + Greeting Bubble + Conversation + 6 Prompt Chips
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Pochi Squircle Mascot
            PochiSquircleMascot(
                width = 116.dp,
                height = 88.dp,
                cornerRadius = 28.dp,
                style = iconStyle,
                showPastelAura = true
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Speech Bubble Card: "Hi! I'm Pochi ✨ / How can I help you today?"
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .shadow(10.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.06f)),
                shape = RoundedCornerShape(24.dp),
                color = palette.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)
                ) {
                    Text(
                        text = "Hi! I'm Pochi ✨",
                        fontFamily = fonts.titleFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isListening) {
                            if (lang == "id") "Mendengarkan suaramu... (Tanpa suara balasan)"
                            else "Listening to your voice... (Silent mode)"
                        } else {
                            "How can I help you today?"
                        },
                        fontFamily = fonts.titleFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.5.sp,
                        color = if (isListening) Color(0xFF2563EB) else palette.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Conversation History (if user has chatted)
            if (messages.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    messages.takeLast(8).forEach { msg ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 18.dp,
                                    topEnd = 18.dp,
                                    bottomStart = if (msg.isUser) 18.dp else 6.dp,
                                    bottomEnd = if (msg.isUser) 6.dp else 18.dp
                                ),
                                color = if (msg.isUser) palette.pillActiveBg else palette.card,
                                border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
                                modifier = Modifier.widthIn(max = 280.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    fontFamily = fonts.bodyFamily,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = if (msg.isUser) palette.pillActiveText else palette.textPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                    if (isThinking) {
                        Text(
                            text = "Pochi is thinking...",
                            fontFamily = fonts.accentFamily,
                            fontSize = 13.sp,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 6 Quick Prompt Pills (2 rows matching Reference Image)
            // Row 1: 💡 Explain this | ✏️ Write | 🔍 Research
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                AgentPromptChip(
                    icon = Icons.Rounded.Lightbulb,
                    iconTint = Color(0xFFF59E0B),
                    label = "Explain this",
                    palette = palette,
                    fonts = fonts,
                    onClick = { submitPrompt("Explain quantum computing in simple terms") }
                )
                AgentPromptChip(
                    icon = Icons.Rounded.Edit,
                    iconTint = Color(0xFFEA580C),
                    label = "Write",
                    palette = palette,
                    fonts = fonts,
                    onClick = { submitPrompt("Write a concise productive morning plan") }
                )
                AgentPromptChip(
                    icon = Icons.Rounded.Search,
                    iconTint = Color(0xFF3B82F6),
                    label = "Research",
                    palette = palette,
                    fonts = fonts,
                    onClick = { submitPrompt("Research top UI/UX design trends for 2026") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: 🛠️ Code | 📄 Summarize | ••• More
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                AgentPromptChip(
                    icon = Icons.Rounded.Build,
                    iconTint = Color(0xFF6366F1),
                    label = "Code",
                    palette = palette,
                    fonts = fonts,
                    onClick = { submitPrompt("Write a clean Kotlin Coroutine Flow example") }
                )
                AgentPromptChip(
                    icon = Icons.Rounded.Description,
                    iconTint = Color(0xFF10B981),
                    label = "Summarize",
                    palette = palette,
                    fonts = fonts,
                    onClick = { submitPrompt("Summarize my tasks for today") }
                )
                AgentPromptChip(
                    icon = Icons.Rounded.MoreHoriz,
                    iconTint = palette.textSecondary,
                    label = "More",
                    palette = palette,
                    fonts = fonts,
                    onClick = { showMoreActionsSheet = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Input Bar: [ + ]  Ask me anything...  [ 🎙️ / Send ]
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(alpha = 0.07f)),
            shape = RoundedCornerShape(28.dp),
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Quick Actions",
                    tint = palette.textSecondary,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { showMoreActionsSheet = true }
                        .padding(2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = if (isListening) "Listening..." else "Ask me anything...",
                            fontFamily = fonts.bodyFamily,
                            fontSize = 14.5.sp,
                            color = palette.textMuted
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        textStyle = TextStyle(
                            fontFamily = fonts.bodyFamily,
                            fontSize = 14.5.sp,
                            color = palette.textPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))

                if (inputText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(palette.pillActiveBg)
                            .clickable { submitPrompt(inputText) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = palette.pillActiveText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Listening-only Microphone Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) Color(0xFFEF4444).copy(alpha = 0.16f)
                                else Color.Transparent
                            )
                            .clickable { toggleListeningMic() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Voice Input (Listening Only)",
                            tint = if (isListening) Color(0xFFEF4444) else palette.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (showMoreActionsSheet) {
        AlertDialog(
            onDismissRequest = { showMoreActionsSheet = false },
            title = {
                Text(
                    text = "Pochi Phone & App Actions",
                    fontFamily = fonts.titleFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (lang == "id") "Ketuk aksi cepat untuk menelpon, kirim SMS/WhatsApp, atau membuka aplikasi:"
                        else "Tap a quick command to call, send SMS/WhatsApp, or launch apps:",
                        fontFamily = fonts.bodyFamily,
                        fontSize = 13.sp,
                        color = palette.textSecondary
                    )
                    listOf(
                        "📞 Call Contact" to "telepon ",
                        "💬 Send WhatsApp" to "wa ",
                        "✉️ Send SMS" to "sms ",
                        "🚀 Open Installed App" to "buka aplikasi ",
                        "🕌 Prayer Schedule" to "jadwal sholat",
                        "🌤️ Current Weather" to "cuaca hari ini"
                    ).forEach { (title, cmdPrefix) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showMoreActionsSheet = false
                                    if (cmdPrefix.endsWith(" ")) {
                                        inputText = cmdPrefix
                                    } else {
                                        submitPrompt(cmdPrefix)
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.pillInactiveBg
                        ) {
                            Text(
                                text = title,
                                fontFamily = fonts.titleFamily,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoreActionsSheet = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun AgentPromptChip(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    palette: PochiPalette,
    fonts: PochiFontPack,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(CircleShape)
            .clickable { onClick() },
        shape = CircleShape,
        color = palette.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontFamily = fonts.titleFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = palette.textPrimary
            )
        }
    }
}

@Composable
private fun CircularIconButton(
    icon: ImageVector,
    palette: PochiPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .shadow(4.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(CircleShape)
            .background(palette.card)
            .border(1.dp, palette.cardBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.textPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ============================================================================
// SCREEN 3: FOCUS SCREEN (Pomodoro & Task Management - Top Row 3rd Phone in Image)
// ============================================================================
@Composable
fun PochiFocusScreen(
    palette: PochiPalette,
    fonts: PochiFontPack,
    lang: String,
    phase: Int,
    secondsLeft: Int,
    isRunning: Boolean,
    onSelectPhase: (Int) -> Unit,
    onToggleRun: () -> Unit,
    onReset: () -> Unit,
    onOpenTaskDetail: (String) -> Unit
) {
    val context = LocalContext.current
    var subTab by remember { mutableIntStateOf(0) } // 0 = Timer, 1 = Tasks, 2 = Stats
    val tasks by PochiPrefs.tasksFlow.collectAsState()
    val focusSessions by PochiPrefs.focusSessionsFlow.collectAsState()

    val totalSeconds = when (phase) {
        1 -> 5 * 60
        2 -> 15 * 60
        else -> 25 * 60
    }
    val progress = (secondsLeft.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    val mm = (secondsLeft / 60).toString().padStart(2, '0')
    val ss = (secondsLeft % 60).toString().padStart(2, '0')

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 14.dp, bottom = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header Row: "Focus / Stay focused and get more done." + Reset Timer Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Focus",
                    fontFamily = fonts.displayFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    letterSpacing = (-0.8).sp,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Stay focused and get more done.",
                    fontFamily = fonts.bodyFamily,
                    fontSize = 13.5.sp,
                    color = palette.textSecondary
                )
            }
            CircularIconButton(
                icon = Icons.Rounded.Timer,
                palette = palette,
                onClick = onReset
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Segmented Pill Tabs: Timer | Tasks | Stats
        Surface(
            shape = CircleShape,
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Timer", "Tasks", "Stats").forEachIndexed { idx, title ->
                    val active = subTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (active) palette.pillActiveBg else Color.Transparent)
                            .clickable { subTab = idx }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontFamily = fonts.titleFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (active) palette.pillActiveText else palette.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        when (subTab) {
            0 -> {
                // LARGE CIRCULAR GOLDEN-YELLOW RING PROGRESS TIMER (Matches Reference Image 3. Focus)
                Box(
                    modifier = Modifier.size(248.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer soft white circular card shadow
                    Box(
                        modifier = Modifier
                            .size(236.dp)
                            .shadow(16.dp, CircleShape, spotColor = Color(0xFFF59E0B).copy(alpha = 0.18f))
                            .clip(CircleShape)
                            .background(palette.card)
                    )

                    Canvas(modifier = Modifier.size(210.dp)) {
                        val strokeW = 14.dp.toPx()
                        // Track ring
                        drawArc(
                            color = if (palette.isDark) Color(0xFF282D3B) else Color(0xFFF1F3F9),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                        // Golden-yellow progress arc
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(Color(0xFFFDE047), Color(0xFFFACC15), Color(0xFFF59E0B), Color(0xFFFDE047))
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Coffee,
                            contentDescription = null,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$mm:$ss",
                            fontFamily = fonts.displayFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 44.sp,
                            letterSpacing = (-1).sp,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (phase) {
                                1 -> "Short Break"
                                2 -> "Long Break"
                                else -> "Focus Time"
                            },
                            fontFamily = fonts.bodyFamily,
                            fontSize = 13.5.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Phase Pills: [ ✦ Focus ]  [ Short Break ]  [ Long Break ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    listOf("✦ Focus", "Short Break", "Long Break").forEachIndexed { idx, label ->
                        val active = phase == idx
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onSelectPhase(idx) },
                            shape = CircleShape,
                            color = if (active) palette.pillActiveBg else palette.card,
                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
                        ) {
                            Text(
                                text = label,
                                fontFamily = fonts.titleFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (active) palette.pillActiveText else palette.textSecondary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Golden-Yellow Pill CTA Button: [ ▶ Start Focus ]
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(56.dp)
                        .shadow(14.dp, CircleShape, spotColor = Color(0xFFF59E0B).copy(alpha = 0.45f))
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFDE047), Color(0xFFFACC15), Color(0xFFF59E0B))
                            )
                        )
                        .clickable { onToggleRun() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = if (isRunning) "Pause Focus" else "Start Focus",
                            fontFamily = fonts.titleFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
            }

            1 -> {
                // Focus Tasks sub-view
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = palette.card,
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Focus Tasks",
                            fontFamily = fonts.titleFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        tasks.forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenTaskDetail(task.id) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PochiTaskCheckbox(
                                        checked = task.done,
                                        onToggle = { PochiPrefs.toggleTask(context, task.id) }
                                    )
                                    Text(
                                        text = task.title,
                                        fontFamily = fonts.bodyFamily,
                                        fontSize = 14.5.sp,
                                        color = palette.textPrimary
                                    )
                                }
                                Text(
                                    text = task.time,
                                    fontFamily = fonts.monoFamily,
                                    fontSize = 12.5.sp,
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                // Focus Stats sub-view with visual bar chart
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = palette.card,
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Focus Productivity Stats",
                            fontFamily = fonts.titleFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Completed Sessions: $focusSessions • Total Focus: ${focusSessions * 25} mins",
                            fontFamily = fonts.monoFamily,
                            fontSize = 13.sp,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        // Weekly Focus Bar Chart
                        val bars = listOf(0.45f, 0.7f, 0.55f, 0.85f, 0.65f, 0.92f, (focusSessions * 0.2f).coerceIn(0.35f, 1f))
                        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Today")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            bars.forEachIndexed { i, ratio ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height((88 * ratio).dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (i == bars.lastIndex) Color(0xFFFACC15)
                                                else Color(0xFF3B82F6).copy(alpha = 0.7f)
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = days[i],
                                        fontFamily = fonts.monoFamily,
                                        fontSize = 10.sp,
                                        color = palette.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// SCREEN 4: DECK SCREEN (Collect & Organize - Top Row 4th Phone in Image)
// ============================================================================
@Composable
fun PochiDeckScreen(
    palette: PochiPalette,
    fonts: PochiFontPack,
    lang: String
) {
    val context = LocalContext.current
    val deckItems by PochiPrefs.deckItemsFlow.collectAsState()
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("Notes") }

    val categories = listOf("All", "Notes", "Bookmarks", "Files", "Apps")
    val installedApps = remember { PochiAgent.getInstalledLauncherApps(context) }

    val filteredItems = remember(deckItems, selectedCategory) {
        if (selectedCategory == "All") deckItems
        else deckItems.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 14.dp, bottom = 110.dp)
    ) {
        // Header Row: "Deck / Collect, organize, and explore." + [ + ] Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Deck",
                    fontFamily = fonts.displayFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    letterSpacing = (-0.8).sp,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Collect, organize, and explore.",
                    fontFamily = fonts.bodyFamily,
                    fontSize = 13.5.sp,
                    color = palette.textSecondary
                )
            }
            CircularIconButton(
                icon = Icons.Rounded.Add,
                palette = palette,
                onClick = { showAddDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Filter Pills: [ All ]  [ Notes ]  [ Bookmarks ]  [ Files ]  [ Apps ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val active = selectedCategory == cat
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { selectedCategory = cat },
                    shape = CircleShape,
                    color = if (active) palette.pillActiveBg else palette.card,
                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
                ) {
                    Text(
                        text = cat,
                        fontFamily = fonts.titleFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (active) palette.pillActiveText else palette.textSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (selectedCategory == "Apps") {
            // Installed Android Apps Launcher Grid inside Deck
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                installedApps.take(25).forEach { app ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launchIntent)
                                }
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = palette.card,
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFDBEAFE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GridView,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB)
                                    )
                                }
                                Column {
                                    Text(
                                        text = app.label,
                                        fontFamily = fonts.titleFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp,
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontFamily = fonts.monoFamily,
                                        fontSize = 11.5.sp,
                                        color = palette.textSecondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = palette.textMuted
                            )
                        }
                    }
                }
            }
        } else {
            // Collection Cards matching Reference Image:
            // UI Inspiration, Programming, Design System, AI Research
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filteredItems.forEach { item ->
                    val (iconVec, iconBg, iconTint) = when (item.iconType) {
                        "code" -> Triple(Icons.Rounded.Code, Color(0xFFE0F2FE), Color(0xFF0284C7))
                        "palette" -> Triple(Icons.Rounded.Palette, Color(0xFFFEF3C7), Color(0xFFD97706))
                        "doc" -> Triple(Icons.Rounded.Description, Color(0xFFE0E7FF), Color(0xFF4F46E5))
                        else -> Triple(Icons.Rounded.Image, Color(0xFFDBEAFE), Color(0xFF2563EB))
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(6.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.05f))
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "${item.title} (${item.itemCount} items)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = palette.card,
                        border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(iconBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVec,
                                        contentDescription = item.title,
                                        tint = iconTint,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = item.title,
                                        fontFamily = fonts.titleFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = palette.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "${item.itemCount} items • ${item.updatedAgo}",
                                        fontFamily = fonts.bodyFamily,
                                        fontSize = 12.5.sp,
                                        color = palette.textSecondary
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Open",
                                tint = palette.textMuted
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "New Deck Collection",
                    fontFamily = fonts.titleFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        textStyle = TextStyle(
                            fontFamily = fonts.bodyFamily,
                            fontSize = 15.sp,
                            color = palette.textPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.pillInactiveBg)
                            .padding(14.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Notes", "Bookmarks", "Files").forEach { cat ->
                            Surface(
                                shape = CircleShape,
                                color = if (newCategory == cat) palette.pillActiveBg else palette.pillInactiveBg,
                                modifier = Modifier.clickable { newCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    color = if (newCategory == cat) palette.pillActiveText else palette.textSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            PochiPrefs.addDeckCollection(context, newTitle, newCategory)
                            newTitle = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ============================================================================
// TODAY DETAIL SCREEN (Bottom Row 5th Screen in Reference Image)
// ============================================================================
@Composable
fun PochiTodayDetailScreen(
    palette: PochiPalette,
    fonts: PochiFontPack,
    lang: String,
    onBack: () -> Unit,
    onOpenTaskDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val tasks by PochiPrefs.tasksFlow.collectAsState()
    var subMode by remember { mutableIntStateOf(0) } // 0 = Tasks, 1 = Calendar
    var newTaskTitle by remember { mutableStateOf("") }

    val formattedDate = remember {
        SimpleDateFormat("EEE, MMM dd, yyyy", Locale.US).format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 14.dp, bottom = 110.dp)
    ) {
        // Header: "Today / Tue, Oct 26, 2026" + Calendar Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "Today",
                    fontFamily = fonts.displayFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    letterSpacing = (-0.8).sp,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    fontFamily = fonts.bodyFamily,
                    fontSize = 13.5.sp,
                    color = palette.textSecondary
                )
            }
            CircularIconButton(
                icon = Icons.Rounded.CalendarMonth,
                palette = palette,
                onClick = { subMode = if (subMode == 0) 1 else 0 }
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Segmented Pill: [ Tasks ]  [ Calendar ]
        Surface(
            shape = CircleShape,
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                listOf("Tasks", "Calendar").forEachIndexed { idx, label ->
                    val active = subMode == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(if (active) palette.pillActiveBg else Color.Transparent)
                            .clickable { subMode = idx }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = fonts.titleFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (active) palette.pillActiveText else palette.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Input Bar: [ ⊕ Add new task... ] + Black Circular [ + ] Button
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.04f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, palette.textSecondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (newTaskTitle.isEmpty()) {
                        Text(
                            text = "Add new task...",
                            fontFamily = fonts.bodyFamily,
                            fontSize = 14.sp,
                            color = palette.textMuted
                        )
                    }
                    BasicTextField(
                        value = newTaskTitle,
                        onValueChange = { newTaskTitle = it },
                        textStyle = TextStyle(
                            fontFamily = fonts.bodyFamily,
                            fontSize = 14.sp,
                            color = palette.textPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(palette.pillActiveBg)
                        .clickable {
                            if (newTaskTitle.isNotBlank()) {
                                PochiPrefs.addTask(context, newTaskTitle)
                                newTaskTitle = ""
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add Task",
                        tint = palette.pillActiveText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Task List Card with Right-Aligned Timestamps (10:00, 14:00, 16:00, 19:00)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                tasks.forEachIndexed { index, task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenTaskDetail(task.id) }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            PochiTaskCheckbox(
                                checked = task.done,
                                onToggle = { PochiPrefs.toggleTask(context, task.id) }
                            )
                            Text(
                                text = task.title,
                                fontFamily = fonts.titleFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = if (task.done) palette.textSecondary else palette.textPrimary,
                                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                            )
                        }
                        Text(
                            text = task.time,
                            fontFamily = fonts.monoFamily,
                            fontSize = 13.sp,
                            color = palette.textSecondary
                        )
                    }
                    if (index < tasks.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(palette.cardBorder)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// TASK DETAIL SCREEN (Bottom Row 6th Screen in Reference Image)
// ============================================================================
@Composable
fun PochiTaskDetailScreen(
    taskId: String,
    palette: PochiPalette,
    fonts: PochiFontPack,
    lang: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tasks by PochiPrefs.tasksFlow.collectAsState()
    val task = tasks.firstOrNull { it.id == taskId } ?: tasks.firstOrNull()

    if (task == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var editingNotes by remember(task.id) { mutableStateOf(false) }
    var notesDraft by remember(task.notes) { mutableStateOf(task.notes) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Top Action Bar: ← on left | ✎  📎  🗑 on right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = palette.textPrimary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(6.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit Notes",
                    tint = palette.textSecondary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable { editingNotes = !editingNotes }
                        .padding(4.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "Attach",
                    tint = palette.textSecondary,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable {
                            Toast.makeText(context, "Attachment ready", Toast.LENGTH_SHORT).show()
                        }
                        .padding(4.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete Task",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable {
                            PochiPrefs.deleteTask(context, task.id)
                            onBack()
                        }
                        .padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))

        // Large Blue Rounded Squircle Checkmark Badge (Matches Task Detail in Reference Image)
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = Color(0xFF3B82F6).copy(alpha = 0.35f))
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF60A5FA), Color(0xFF3B82F6))
                    )
                )
                .clickable { PochiPrefs.toggleTask(context, task.id) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Done",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Task Title ("Study UI Design")
        Text(
            text = task.title,
            fontFamily = fonts.displayFamily,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            letterSpacing = (-0.5).sp,
            color = palette.textPrimary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Date & Time Row: 🕒 Tue, Oct 26, 2026 • 14:00 - 16:00
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Tue, Oct 26, 2026\n${task.time} - 16:00",
                fontFamily = fonts.monoFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = palette.textSecondary
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Notes Section
        Text(
            text = "Notes",
            fontFamily = fonts.titleFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = palette.textPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (editingNotes) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = palette.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.accentBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    BasicTextField(
                        value = notesDraft,
                        onValueChange = { notesDraft = it },
                        textStyle = TextStyle(
                            fontFamily = fonts.bodyFamily,
                            fontSize = 14.5.sp,
                            color = palette.textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Save Notes",
                        fontFamily = fonts.titleFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = palette.accentBlue,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable {
                                PochiPrefs.updateTaskNotes(context, task.id, notesDraft)
                                editingNotes = false
                            }
                    )
                }
            }
        } else {
            Text(
                text = task.notes,
                fontFamily = fonts.bodyFamily,
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
                color = palette.textSecondary,
                modifier = Modifier.clickable { editingNotes = true }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Subtasks Section (Watch tutorial, Create mockup, Review design)
        Text(
            text = "Subtasks",
            fontFamily = fonts.titleFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = palette.textPrimary
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            task.subtasks.forEachIndexed { idx, sub ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { PochiPrefs.toggleSubtask(context, task.id, idx) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PochiTaskCheckbox(
                        checked = sub.done,
                        onToggle = { PochiPrefs.toggleSubtask(context, task.id, idx) }
                    )
                    Text(
                        text = sub.title,
                        fontFamily = fonts.bodyFamily,
                        fontSize = 15.sp,
                        color = if (sub.done) palette.textSecondary else palette.textPrimary,
                        textDecoration = if (sub.done) TextDecoration.LineThrough else TextDecoration.None
                    )
                }
            }
        }
    }
}

// ============================================================================
// SETTINGS SCREEN (Light Mode & Dark Mode - Bottom-Right 2 Screens in Image)
// Includes App Icon Variants Selector (Light, Dark, Accent, Adaptive) & Font Selector
// ============================================================================
@Composable
fun PochiSettingsScreen(
    palette: PochiPalette,
    fonts: PochiFontPack,
    iconStyle: String,
    lang: String,
    onBack: () -> Unit,
    onReplayOnboarding: () -> Unit
) {
    val context = LocalContext.current
    val isDark by PochiPrefs.darkModeFlow.collectAsState()
    val fontStyle by PochiPrefs.fontStyleFlow.collectAsState()
    val islandRunning by DynamicIslandService.isRunning.collectAsState()

    var notificationsEnabled by remember { mutableStateOf(true) }
    var showApiDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        // Top Bar: ←  |  Settings
        Box(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = palette.textPrimary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(6.dp)
            )
            Text(
                text = "Settings",
                fontFamily = fonts.displayFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = palette.textPrimary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 1. Pochi Profile Card ("Pochi • Your AI Companion >")
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.06f))
                .clickable { toggleDynamicIslandOverlay(context, lang) },
            shape = RoundedCornerShape(22.dp),
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PochiSquircleMascot(
                        width = 48.dp,
                        height = 42.dp,
                        cornerRadius = 14.dp,
                        style = iconStyle,
                        showPastelAura = false,
                        interactive = false
                    )
                    Column {
                        Text(
                            text = "Pochi",
                            fontFamily = fonts.titleFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.5.sp,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Your AI Companion",
                            fontFamily = fonts.bodyFamily,
                            fontSize = 13.sp,
                            color = palette.textSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = palette.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. App Icon Style Selector (Matches the 4 Icon Variants on Left of Design Sheet)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "App Icon & Mascot Style",
                    fontFamily = fonts.titleFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = palette.textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        "light" to "Light Mode",
                        "dark" to "Dark Mode",
                        "accent" to "Accent Icon",
                        "adaptive" to "Adaptive Icon"
                    ).forEach { (styleKey, label) ->
                        val selected = iconStyle == styleKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { PochiPrefs.setIconStyle(context, styleKey) }
                                .padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .border(
                                        width = if (selected) 2.dp else 0.dp,
                                        color = if (selected) palette.accentBlue else Color.Transparent,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .padding(3.dp)
                            ) {
                                PochiSquircleMascot(
                                    width = 54.dp,
                                    height = 46.dp,
                                    cornerRadius = 15.dp,
                                    style = styleKey,
                                    showPastelAura = false,
                                    interactive = false
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = label,
                                fontFamily = fonts.bodyFamily,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                color = if (selected) palette.textPrimary else palette.textSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Main Settings List (Theme, Font Style, Language, Notifications, Data & Storage, Privacy & Security, About)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = palette.card,
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Theme Row (Light / Dark)
                SettingsRowItem(
                    icon = if (isDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    title = "Theme",
                    valueText = if (isDark) "Dark" else "Light",
                    palette = palette,
                    fonts = fonts,
                    onClick = { PochiPrefs.setDarkMode(context, !isDark) }
                )
                SettingsDivider(palette)

                // Font Style Row (Non-monotonous typography switcher)
                SettingsRowItem(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Font Style",
                    valueText = fonts.label,
                    palette = palette,
                    fonts = fonts,
                    onClick = {
                        val nextFont = when (fontStyle) {
                            "jakarta" -> "grotesk"
                            "grotesk" -> "editorial"
                            else -> "jakarta"
                        }
                        PochiPrefs.setFontStyle(context, nextFont)
                    }
                )
                SettingsDivider(palette)

                // Language Row (English / Indonesia)
                SettingsRowItem(
                    icon = Icons.Rounded.Language,
                    title = "Language",
                    valueText = if (lang == "id") "Indonesia" else "English",
                    palette = palette,
                    fonts = fonts,
                    onClick = {
                        PochiPrefs.setLanguage(context, if (lang == "id") "en" else "id")
                    }
                )
                SettingsDivider(palette)

                // Notifications Row with Blue Switch Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                        Text(
                            text = "Notifications",
                            fontFamily = fonts.titleFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = palette.textPrimary
                        )
                    }
                    Switch(
                        checked = notificationsEnabled || islandRunning,
                        onCheckedChange = { checked ->
                            notificationsEnabled = checked
                            toggleDynamicIslandOverlay(context, lang)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF3B82F6)
                        )
                    )
                }
                SettingsDivider(palette)

                // Data & Storage Row (AI API Key & Provider)
                SettingsRowItem(
                    icon = Icons.Rounded.Storage,
                    title = "Data & Storage",
                    valueText = PochiPrefs.getProvider(context).uppercase(),
                    palette = palette,
                    fonts = fonts,
                    onClick = { showApiDialog = true }
                )
                SettingsDivider(palette)

                // Privacy & Security Row (Permissions)
                SettingsRowItem(
                    icon = Icons.Rounded.Lock,
                    title = "Privacy & Security",
                    valueText = "",
                    palette = palette,
                    fonts = fonts,
                    onClick = { showPermissionsDialog = true }
                )
                SettingsDivider(palette)

                // About Row (Replay Welcome & Onboarding)
                SettingsRowItem(
                    icon = Icons.Rounded.Info,
                    title = "About",
                    valueText = "Welcome Tour",
                    palette = palette,
                    fonts = fonts,
                    onClick = onReplayOnboarding
                )
            }
        }
    }

    // AI API Key & Provider Dialog
    if (showApiDialog) {
        var apiKeyInput by remember { mutableStateOf(PochiPrefs.getApiKey(context)) }
        var providerInput by remember { mutableStateOf(PochiPrefs.getProvider(context)) }

        AlertDialog(
            onDismissRequest = { showApiDialog = false },
            title = {
                Text(
                    text = "AI Engine & API Key",
                    fontFamily = fonts.titleFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("gemini", "openai", "claude").forEach { prov ->
                            Surface(
                                shape = CircleShape,
                                color = if (providerInput == prov) palette.pillActiveBg else palette.pillInactiveBg,
                                modifier = Modifier.clickable { providerInput = prov }
                            ) {
                                Text(
                                    text = prov.uppercase(),
                                    fontSize = 12.sp,
                                    color = if (providerInput == prov) palette.pillActiveText else palette.textSecondary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    BasicTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        textStyle = TextStyle(
                            fontFamily = fonts.monoFamily,
                            fontSize = 13.5.sp,
                            color = palette.textPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.pillInactiveBg)
                            .padding(14.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        PochiPrefs.setProvider(context, providerInput)
                        PochiPrefs.setApiKey(context, apiKeyInput.trim())
                        showApiDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Permissions Quick-Grant Dialog
    if (showPermissionsDialog) {
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }
        AlertDialog(
            onDismissRequest = { showPermissionsDialog = false },
            title = {
                Text(
                    text = "Privacy & Device Permissions",
                    fontFamily = fonts.titleFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Grant permissions for Pochi Dynamic Island, Listening Mic, Auto-Location Weather & Phone Calls:",
                        fontFamily = fonts.bodyFamily,
                        fontSize = 13.sp,
                        color = palette.textSecondary
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.pillInactiveBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                permLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.CALL_PHONE,
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.SEND_SMS,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                    ) {
                        Text(
                            text = "🎙️ 📍 📞 Grant Mic, Location & Call Permissions",
                            fontFamily = fonts.titleFamily,
                            fontSize = 13.5.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.pillInactiveBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                    ) {
                        Text(
                            text = "🪟 Overlay Permission (Dynamic Island)",
                            fontFamily = fonts.titleFamily,
                            fontSize = 13.5.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.pillInactiveBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                context.startActivity(
                                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                    ) {
                        Text(
                            text = "♿ Accessibility Service",
                            fontFamily = fonts.titleFamily,
                            fontSize = 13.5.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPermissionsDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    valueText: String,
    palette: PochiPalette,
    fonts: PochiFontPack,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = palette.textPrimary,
                modifier = Modifier.size(21.dp)
            )
            Text(
                text = title,
                fontFamily = fonts.titleFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = palette.textPrimary
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (valueText.isNotEmpty()) {
                Text(
                    text = valueText,
                    fontFamily = fonts.bodyFamily,
                    fontSize = 13.5.sp,
                    color = palette.textSecondary
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsDivider(palette: PochiPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(palette.cardBorder)
    )
}
