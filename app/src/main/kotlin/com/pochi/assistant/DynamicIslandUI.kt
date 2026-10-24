package com.pochi.assistant

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class PochiTab(
    val titleEn: String,
    val titleId: String,
    val icon: ImageVector
) {
    TODAY("Today", "Hari Ini", Icons.Rounded.CheckCircleOutline),
    AGENT("Agent", "Agen", Icons.Rounded.Terminal),
    FOCUS("Focus", "Fokus", Icons.Rounded.Timer),
    DECK("Deck", "Pintasan", Icons.Rounded.Apps);

    fun label(lang: PochiLang): String = if (lang == PochiLang.ID) titleId else titleEn
}

enum class TaskFilter {
    ALL, ACTIVE, DONE
}

enum class PomodoroPhase(val defaultMinutes: Int) {
    WORK(25),
    SHORT_BREAK(5),
    LONG_BREAK(15);

    fun label(lang: PochiLang): String = when (this) {
        WORK -> if (lang == PochiLang.ID) "Fokus" else "Focus"
        SHORT_BREAK -> if (lang == PochiLang.ID) "Rehat 5m" else "Break 5m"
        LONG_BREAK -> if (lang == PochiLang.ID) "Rehat 15m" else "Long 15m"
    }
}

private enum class IslandMode {
    COLLAPSED,
    COMMAND_BAR,
    EXPANDED
}

data class LaunchableAppInfo(
    val label: String,
    val packageName: String
)

@Composable
fun PochiIslandBot(
    onExpandChange: (Boolean) -> Unit,
    onExpandedHeightChange: (Int) -> Unit = {},
    isHidden: Boolean,
    peekMode: Boolean,
    peekSide: String = "right",
    onRestoreFromHidden: () -> Unit,
    onDragBubble: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onPowerOffService: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Core matte-black #0A0A0A palette
    val islandBlack = Color(0xFF0A0A0A)
    val surfaceDark = Color(0xFF141418)
    val subtleDark = Color(0xFF1E1E24)
    val borderDark = Color(0xFF25252E)
    val textPrimary = Color(0xFFF8FAFC)
    val textSecondary = Color(0xFF94A3B8)
    val accentIndigo = Color(0xFF6366F1)
    val accentEmerald = Color(0xFF10B981)
    val accentAmber = Color(0xFFF59E0B)
    val accentSky = Color(0xFF38BDF8)

    val lang by PochiPrefs.langFlow.collectAsState()
    val isFlushStatusBar by PochiPrefs.flushStatusBarFlow.collectAsState()
    val offsetY by PochiPrefs.offsetYFlow.collectAsState()
    val isId = lang == PochiLang.ID

    var islandMode by remember { mutableStateOf(IslandMode.COLLAPSED) }
    var currentTab by remember { mutableStateOf(PochiTab.TODAY) }
    var botState by remember { mutableStateOf(BotState.IDLE) }
    var customEmote by remember { mutableStateOf<PochiEmote?>(null) }
    var idleEmote by remember { mutableStateOf<PochiEmote?>(PochiEmote.SMILING) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var lastActiveTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isIdleDimmed by remember { mutableStateOf(false) }
    var pokeCount by remember { mutableIntStateOf(0) }
    var eyeGazeOffset by remember { mutableStateOf(Offset.Zero) }

    // Reactive signals & preferences
    val tasks by PochiPrefs.tasksFlow.collectAsState()
    val pinnedPackages by PochiPrefs.pinnedAppsFlow.collectAsState()
    val mcpUrl by PochiPrefs.mcpUrlFlow.collectAsState()
    val completedFocusSessions by PochiPrefs.focusSessionsFlow.collectAsState()
    val activeApproval by ApprovalManager.activeRequest.collectAsState()
    val lastDecision by ApprovalManager.lastDecision.collectAsState()
    val dismissSignal by ApprovalManager.dismissSignal.collectAsState()
    val mediaPlayback by PochiSignals.mediaPlayback.collectAsState()
    val activeDownload by PochiSignals.activeDownload.collectAsState()
    val latestScreenshot by PochiSignals.latestScreenshot.collectAsState()
    val agentSession by PochiSignals.agentSession.collectAsState()

    // Phase 2: Today Module State
    var selectedDayOffset by remember { mutableIntStateOf(0) }
    var taskFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var newTaskInput by remember { mutableStateOf("") }

    // Phase 2: Focus (Pomodoro) Module State
    var pomodoroPhase by remember { mutableStateOf(PomodoroPhase.WORK) }
    var focusDurationMinutes by remember { mutableIntStateOf(25) }
    var focusRemainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var isFocusRunning by remember { mutableStateOf(false) }
    var linkedTaskId by remember { mutableStateOf<String?>(null) }

    // Phase 3: Quick Command Bar State
    var commandBarInput by remember { mutableStateOf("") }

    // Phase 4: Agent Module State
    var selectedAgentHost by remember { mutableStateOf("Claude Code") }
    var agentPromptInput by remember { mutableStateOf("") }
    var isEditingMcpUrl by remember { mutableStateOf(false) }
    var mcpUrlDraft by remember(mcpUrl) { mutableStateOf(mcpUrl) }

    // Phase 3: Deck Module State
    var installedApps by remember { mutableStateOf<List<LaunchableAppInfo>>(emptyList()) }
    var isCustomizingDeck by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }

    fun bump() {
        interactionTick++
        lastActiveTime = System.currentTimeMillis()
        isIdleDimmed = false
    }

    fun setMode(target: IslandMode) {
        if (islandMode != target) {
            val wasExpanded = islandMode != IslandMode.COLLAPSED
            val willExpand = target != IslandMode.COLLAPSED
            islandMode = target
            bump()
            if (wasExpanded != willExpand) {
                onExpandChange(willExpand)
            }
        }
    }

    // Load launchable apps once on IO dispatcher for Deck
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolved = pm.queryIntentActivities(mainIntent, 0)
                    .mapNotNull { info ->
                        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                        if (pkg == context.packageName) return@mapNotNull null
                        val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                        if (label.isBlank()) return@mapNotNull null
                        LaunchableAppInfo(label = label, packageName = pkg)
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase(Locale.getDefault()) }
                installedApps = resolved
            }
        }
    }

    // Subtle auto-dimming after 6s when collapsed & idle so the status bar pill never distracts
    LaunchedEffect(islandMode, interactionTick, isFocusRunning, agentSession.isBusy) {
        if (islandMode == IslandMode.COLLAPSED && !isFocusRunning && !agentSession.isBusy) {
            delay(6000)
            isIdleDimmed = true
        } else {
            isIdleDimmed = false
        }
    }

    // Focus Pomodoro countdown ticker
    LaunchedEffect(isFocusRunning) {
        while (isFocusRunning && focusRemainingSeconds > 0) {
            delay(1000)
            focusRemainingSeconds--
            if (focusRemainingSeconds == 0) {
                isFocusRunning = false
                botState = BotState.DONE
                customEmote = PochiEmote.HAPPY
                if (pomodoroPhase == PomodoroPhase.WORK) {
                    PochiPrefs.incrementFocusSessions(context)
                    linkedTaskId?.let { id ->
                        val updated = tasks.map { if (it.id == id) it.copy(isDone = true, isOverdue = false) else it }
                        PochiPrefs.saveTasks(context, updated)
                    }
                }
            }
        }
    }

    // Outside touch dismiss
    LaunchedEffect(dismissSignal) {
        if (dismissSignal > 0L && islandMode != IslandMode.COLLAPSED) {
            setMode(IslandMode.COLLAPSED)
        }
    }

    // Auto-expand on approval request or screenshot capture
    LaunchedEffect(activeApproval) {
        if (activeApproval != null) {
            botState = BotState.APPROVAL
            customEmote = PochiEmote.SURPRISED
            currentTab = PochiTab.AGENT
            setMode(IslandMode.EXPANDED)
        }
    }

    LaunchedEffect(latestScreenshot) {
        if (latestScreenshot != null && islandMode == IslandMode.COLLAPSED) {
            customEmote = PochiEmote.EXCITED
            setMode(IslandMode.EXPANDED)
        }
    }

    LaunchedEffect(lastDecision) {
        lastDecision?.let { decision ->
            val approved = decision.startsWith("Diizinkan") || decision.startsWith("Approved")
            botState = if (approved) BotState.DONE else BotState.IDLE
            customEmote = if (approved) PochiEmote.HAPPY else PochiEmote.NO
            PochiSignals.updateAgentSession {
                it.copy(
                    compactStatus = if (approved) {
                        if (isId) "Diizinkan • Jalan" else "Approved • Running"
                    } else {
                        if (isId) "Perintah ditolak" else "Command rejected"
                    },
                    recentLogs = (listOf(if (approved) "✓ $decision" else "✕ $decision") + it.recentLogs).take(8)
                )
            }
        }
    }

    // Autonomous Pochi blob mood cycle (including automatic after-11PM Sleepy `- -` state)
    LaunchedEffect(botState, isHidden, peekMode) {
        while (true) {
            delay((4000..7200).random().toLong())
            if (botState == BotState.IDLE) {
                val idleDuration = System.currentTimeMillis() - lastActiveTime
                idleEmote = PochiMood.nextAutonomousMood(
                    idleMillis = idleDuration,
                    isPeeking = isHidden && peekMode
                )
            }
        }
    }

    // Auto-collapse after 18s of inactivity when no text is being typed
    LaunchedEffect(islandMode, interactionTick, activeApproval, newTaskInput, commandBarInput, agentPromptInput) {
        if (islandMode != IslandMode.COLLAPSED &&
            activeApproval == null &&
            newTaskInput.isBlank() &&
            commandBarInput.isBlank() &&
            agentPromptInput.isBlank()
        ) {
            delay(18_000)
            setMode(IslandMode.COLLAPSED)
        }
    }

    // Execute Command Palette or Agent prompt (supports both Indonesian & English)
    fun executeUnifiedCommand(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        bump()

        val lower = text.lowercase(Locale.getDefault())
        // 1. Focus Timer shortcut: e.g. "25m review PR", "fokus 25m cek PR", "45 menit belajar"
        val focusRegex = Regex("""^(?:focus\s+|fokus\s+|timer\s+)?(\d{1,3})\s*(?:m|min|menit|mnt)\s*(.*)$""", RegexOption.IGNORE_CASE)
        focusRegex.matchEntire(text)?.let { match ->
            val mins = match.groupValues[1].toIntOrNull()?.coerceIn(1, 180) ?: 25
            val taskTitle = match.groupValues[2].trim()
            pomodoroPhase = PomodoroPhase.WORK
            focusDurationMinutes = mins
            focusRemainingSeconds = mins * 60
            isFocusRunning = true
            if (taskTitle.isNotBlank()) {
                val newTasks = NaturalTaskParser.parse(taskTitle, selectedDayOffset)
                if (newTasks.isNotEmpty()) {
                    val merged = newTasks + tasks
                    PochiPrefs.saveTasks(context, merged)
                    linkedTaskId = newTasks.first().id
                }
            }
            customEmote = PochiEmote.HAPPY
            currentTab = PochiTab.FOCUS
            setMode(IslandMode.COLLAPSED)
            return
        }

        // 2. Agent prompt: starts with "@agent", ">", "run ", "edit ", "git ", "claude ", "codex "
        if (lower.startsWith("@agent") || lower.startsWith(">") ||
            lower.startsWith("git ") || lower.startsWith("claude ") || lower.startsWith("codex ")
        ) {
            val cleanPrompt = text.removePrefix("@agent").removePrefix(">").trim()
            botState = BotState.WORKING
            customEmote = PochiEmote.WORKING
            PochiSignals.updateAgentSession {
                it.copy(
                    isBusy = true,
                    compactStatus = "Working: ${cleanPrompt.take(20)}",
                    currentFileOrTool = cleanPrompt.take(24),
                    recentLogs = (listOf("→ Prompt: $cleanPrompt") + it.recentLogs).take(8)
                )
            }
            coroutineScope.launch {
                val provider = PochiPrefs.getProvider(context)
                val apiKey = PochiPrefs.getApiKey(context, provider)
                val reply = if (apiKey.isNotBlank()) {
                    runCatching {
                        AiClient.ask(
                            provider = provider,
                            apiKey = apiKey,
                            model = PochiPrefs.getModel(context, provider),
                            prompt = cleanPrompt
                        )
                    }.getOrDefault("Agent processed: $cleanPrompt")
                } else {
                    if (isId) {
                        "MCP ($selectedAgentHost): Perintah '$cleanPrompt' dikirim ke workspace."
                    } else {
                        "MCP ($selectedAgentHost): Dispatched '$cleanPrompt' to active workspace."
                    }
                }
                val (afterAction, action) = PochiAgent.extract(reply)
                val (cleanReply, emote) = PochiAgent.extractEmote(afterAction, cleanPrompt)
                if (action != null) {
                    PochiAgent.execute(context, action)
                }
                PochiSignals.updateAgentSession {
                    it.copy(
                        isBusy = false,
                        compactStatus = if (isId) "Selesai • ${cleanPrompt.take(16)}" else "Done • ${cleanPrompt.take(16)}",
                        contextTokensUsed = (it.contextTokensUsed + 1450).coerceAtMost(it.contextTokensMax),
                        recentLogs = (listOf("✓ ${cleanReply.take(90)}") + it.recentLogs).take(8)
                    )
                }
                botState = BotState.DONE
                customEmote = emote
                delay(2800)
                botState = BotState.IDLE
                customEmote = null
            }
            currentTab = PochiTab.AGENT
            setMode(IslandMode.EXPANDED)
            return
        }

        // 3. Default: Parse natural language tasks into Today planner
        val parsedTasks = NaturalTaskParser.parse(text, selectedDayOffset)
        if (parsedTasks.isNotEmpty()) {
            val updated = parsedTasks + tasks
            PochiPrefs.saveTasks(context, updated)
            customEmote = PochiEmote.HAPPY
            botState = BotState.DONE
            currentTab = PochiTab.TODAY
            setMode(IslandMode.EXPANDED)
        }
    }

    // 120fps Spring sizing rules (ultra-slim status-bar hug when collapsed)
    val hasCompactRightStatus = isFocusRunning ||
        agentSession.isBusy ||
        activeDownload != null ||
        mediaPlayback.isPlaying

    val collapsedWidth = if (hasCompactRightStatus) 178.dp else 102.dp
    val collapsedHeight = 26.dp
    val expandedWidth = (configuration.screenWidthDp.dp - 20.dp).coerceIn(320.dp, 388.dp)
    var measuredExpandedHeightPx by remember { mutableIntStateOf(0) }
    val expandedHeight = with(density) {
        when (islandMode) {
            IslandMode.COMMAND_BAR -> 114.dp
            IslandMode.EXPANDED -> if (measuredExpandedHeightPx > 0) measuredExpandedHeightPx.toDp() else 364.dp
            IslandMode.COLLAPSED -> collapsedHeight
        }
    }

    // Mandatory 120fps spring specification: Spring.DampingRatioLowBouncy + Spring.StiffnessMediumLow
    val transition = updateTransition(targetState = islandMode, label = "island120fpsTransition")

    val islandWidth by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "islandWidth"
    ) { mode ->
        when (mode) {
            IslandMode.COLLAPSED -> collapsedWidth
            IslandMode.COMMAND_BAR -> expandedWidth
            IslandMode.EXPANDED -> expandedWidth
        }
    }

    val islandHeight by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "islandHeight"
    ) { mode ->
        when (mode) {
            IslandMode.COLLAPSED -> collapsedHeight
            IslandMode.COMMAND_BAR -> 114.dp
            IslandMode.EXPANDED -> expandedHeight
        }
    }

    val bottomCornerRadius by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "bottomCornerRadius"
    ) { mode ->
        when (mode) {
            IslandMode.COLLAPSED -> 16.dp
            IslandMode.COMMAND_BAR -> 24.dp
            IslandMode.EXPANDED -> 28.dp
        }
    }

    // When flush with status bar (offsetY <= 1 and isFlushStatusBar), top corners hug the top screen edge
    // when collapsed, or round smoothly when expanded.
    val topCornerRadius by transition.animateDp(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "topCornerRadius"
    ) { mode ->
        when {
            mode == IslandMode.COLLAPSED && isFlushStatusBar && offsetY <= 1 -> 6.dp
            mode == IslandMode.COLLAPSED -> 16.dp
            else -> 26.dp
        }
    }

    val expandedContentAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 150) },
        label = "expandedContentAlpha"
    ) { mode -> if (mode == IslandMode.COLLAPSED) 0f else 1f }

    val collapsedContentAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 130) },
        label = "collapsedContentAlpha"
    ) { mode -> if (mode == IslandMode.COLLAPSED) 1f else 0f }

    val idleDimAlpha by animateFloatAsState(
        targetValue = if (islandMode == IslandMode.COLLAPSED && isIdleDimmed) 0.85f else 1f,
        animationSpec = tween(durationMillis = 450),
        label = "idleDimAlpha"
    )

    val activeEmote = customEmote ?: idleEmote ?: botState.defaultEmote

    // Edge-hidden peek mode
    if (isHidden) {
        val restShift = with(density) { (if (peekSide == "left") (-14).dp else 14.dp).toPx() }
        val peekShift = with(density) { (if (peekSide == "left") (-5).dp else 5.dp).toPx() }
        val peekOffset = remember(peekMode, peekSide) { Animatable(restShift) }
        LaunchedEffect(peekMode, peekSide) {
            if (peekMode) {
                while (true) {
                    peekOffset.animateTo(
                        targetValue = peekShift,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    delay(1400)
                    peekOffset.animateTo(
                        targetValue = restShift,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    delay(4000)
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
                ) { onRestoreFromHidden() },
            contentAlignment = if (peekSide == "left") Alignment.CenterStart else Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    translationX = peekOffset.value
                    rotationZ = if (peekSide == "left") 14f else -14f
                },
                contentAlignment = Alignment.Center
            ) {
                PochiMascot(
                    state = botState,
                    emoteOverride = activeEmote,
                    size = 40.dp
                )
            }
        }
        return
    }

    val islandShape = RoundedCornerShape(
        topStart = topCornerRadius,
        topEnd = topCornerRadius,
        bottomStart = bottomCornerRadius,
        bottomEnd = bottomCornerRadius
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        // Matte-black (#0A0A0A) 120fps Hardware-Accelerated Island Container
        Box(
            modifier = Modifier
                .width(islandWidth)
                .height(islandHeight)
                .graphicsLayer {
                    alpha = idleDimAlpha
                    clip = true
                    shape = islandShape
                }
                .background(islandBlack, islandShape)
                .border(
                    width = 0.8.dp,
                    color = if (activeApproval != null) accentAmber.copy(alpha = 0.65f) else borderDark.copy(alpha = 0.75f),
                    shape = islandShape
                )
        ) {
            // ================= 1. COLLAPSED STATUS-BAR PILL =================
            if (collapsedContentAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = collapsedContentAlpha }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    customEmote = PochiEmote.HAPPY
                                    setMode(IslandMode.EXPANDED)
                                },
                                onLongPress = {
                                    customEmote = PochiEmote.FOCUS
                                    setMode(IslandMode.COMMAND_BAR)
                                }
                            )
                        }
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
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left side: Organic blobatar.dev Pochi Mascot with expressive white eyes
                    PochiMascot(
                        state = botState,
                        emoteOverride = activeEmote,
                        size = 20.dp,
                        eyeTrackerOffset = eyeGazeOffset
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right side: Ultra-compact live status indicator inside the status bar
                    when {
                        isFocusRunning -> {
                            val mins = focusRemainingSeconds / 60
                            val secs = focusRemainingSeconds % 60
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(accentEmerald)
                                )
                                Text(
                                    text = String.format(Locale.US, "%02d:%02d", mins, secs),
                                    color = accentEmerald,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        activeDownload != null -> {
                            val dl = activeDownload!!
                            val pct = (dl.fraction * 100).toInt()
                            Text(
                                text = "↓ $pct%",
                                color = accentSky,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        agentSession.isBusy || agentSession.currentFileOrTool.isNotBlank() -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(accentSky)
                                )
                                Text(
                                    text = agentSession.currentFileOrTool.ifBlank { agentSession.compactStatus },
                                    color = textPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        mediaPlayback.isPlaying -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = accentIndigo,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = mediaPlayback.title.take(12),
                                    color = textPrimary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        else -> {
                            val remainingCount = tasks.count { !it.isDone && it.dayIndex == 0 }
                            val overdueCount = tasks.count { !it.isDone && it.dayIndex == 0 && it.isCurrentlyOverdue() }
                            Text(
                                text = when {
                                    overdueCount > 0 -> if (isId) "$overdueCount telat" else "$overdueCount late"
                                    remainingCount > 0 -> if (isId) "$remainingCount tugas" else "$remainingCount left"
                                    else -> "Pochi"
                                },
                                color = if (overdueCount > 0) accentAmber else textSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // ================= 2. LONG-PRESS QUICK COMMAND PALETTE =================
            if (islandMode == IslandMode.COMMAND_BAR) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = expandedContentAlpha }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PochiMascot(
                            state = botState,
                            emoteOverride = PochiEmote.FOCUS,
                            size = 26.dp
                        )
                        Text(
                            text = if (isId) "Bar Perintah Cepat" else "Quick Command Palette",
                            color = textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(subtleDark)
                                .clickable { setMode(IslandMode.EXPANDED) }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isId) "Buka Panel" else "Expand Hub",
                                color = accentSky,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = textSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { setMode(IslandMode.COLLAPSED) }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(surfaceDark)
                            .border(1.dp, accentIndigo.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = accentAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        BasicTextField(
                            value = commandBarInput,
                            onValueChange = {
                                commandBarInput = it
                                bump()
                                eyeGazeOffset = Offset(
                                    x = ((it.length % 18) / 18f - 0.5f),
                                    y = 0.25f
                                )
                            },
                            textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                            cursorBrush = SolidColor(accentIndigo),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    val q = commandBarInput
                                    commandBarInput = ""
                                    executeUnifiedCommand(q)
                                }
                            ),
                            modifier = Modifier.weight(1f),
                            decorationBox = { inner ->
                                if (commandBarInput.isEmpty()) {
                                    Text(
                                        text = if (isId) {
                                            "\"25m cek PR\", \"cek PR, lalu jam 2 rapat\", atau \"> jalankan tes\""
                                        } else {
                                            "\"25m review PR\", \"review PR, then 2pm sync\", or \"> run tests\""
                                        },
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                inner()
                            }
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentIndigo)
                                .clickable {
                                    val q = commandBarInput
                                    commandBarInput = ""
                                    executeUnifiedCommand(q)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isId) "Jalan" else "Run",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickCommandHintChip(if (isId) "⏱ 25m cek PR" else "⏱ 25m review PR") {
                            executeUnifiedCommand(if (isId) "25m cek PR" else "25m review PR")
                        }
                        QuickCommandHintChip(
                            if (isId) "📅 cek PR, lalu jam 2 siang rapat tim" else "📅 review PR, then 2pm team meeting"
                        ) {
                            executeUnifiedCommand(
                                if (isId) "cek PR, lalu jam 2 siang rapat tim" else "review PR, then 2pm team meeting"
                            )
                        }
                        QuickCommandHintChip(if (isId) "🤖 > jalankan unit test" else "🤖 > run test suite") {
                            executeUnifiedCommand(if (isId) "> jalankan unit test" else "> run test suite")
                        }
                    }
                }
            }

            // ================= 3. EXPANDED 4-MODULE DYNAMIC ISLAND HUB =================
            if (islandMode == IslandMode.EXPANDED) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .requiredWidth(expandedWidth - 20.dp)
                        .wrapContentHeight(unbounded = true)
                        .onSizeChanged { size ->
                            val padded = size.height + with(density) { 24.dp.roundToPx() }
                            if (padded > 0 && padded != measuredExpandedHeightPx) {
                                measuredExpandedHeightPx = padded
                                onExpandedHeightChange(padded)
                            }
                        }
                        .graphicsLayer { alpha = expandedContentAlpha }
                        .padding(horizontal = 4.dp, vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Top Header: Interactive Pochi Blob + 4-Module Switcher + ID/EN Toggle + Collapse
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PochiMascot(
                                state = botState,
                                emoteOverride = activeEmote,
                                size = 32.dp,
                                eyeTrackerOffset = eyeGazeOffset,
                                onPoke = {
                                    bump()
                                    pokeCount++
                                    val (emote, state, _) = PochiMood.onPokeReaction(pokeCount)
                                    customEmote = emote
                                    botState = state
                                },
                                onPet = {
                                    bump()
                                    val wasUpset = pokeCount >= 3 || botState == BotState.ANNOYED
                                    pokeCount = 0
                                    val (emote, state, _) = PochiMood.onPetReaction(wasUpset)
                                    customEmote = emote
                                    botState = state
                                },
                                onSwipe = { delta ->
                                    eyeGazeOffset = Offset(
                                        x = (delta.x / 20f).coerceIn(-1f, 1f),
                                        y = (delta.y / 20f).coerceIn(-1f, 1f)
                                    )
                                }
                            )

                            // 4-Tab Pill Switcher (Today/Hari Ini, Agent/Agen, Focus/Fokus, Deck/Pintasan)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(surfaceDark)
                                    .border(1.dp, borderDark, RoundedCornerShape(14.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PochiTab.entries.forEach { tab ->
                                    val selected = tab == currentTab
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) subtleDark else Color.Transparent)
                                            .clickable {
                                                bump()
                                                currentTab = tab
                                                customEmote = when (tab) {
                                                    PochiTab.TODAY -> PochiEmote.SMILING
                                                    PochiTab.AGENT -> PochiEmote.WORKING
                                                    PochiTab.FOCUS -> PochiEmote.FOCUS
                                                    PochiTab.DECK -> PochiEmote.WINK_LEFT
                                                }
                                            }
                                            .padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label(lang),
                                            tint = if (selected) accentIndigo else textSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = tab.label(lang),
                                            color = if (selected) textPrimary else textSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // 1-Tap Language Switcher (ID <-> EN)
                            Box(
                                modifier = Modifier
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(surfaceDark)
                                    .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                                    .clickable {
                                        bump()
                                        PochiPrefs.toggleLanguage(context)
                                    }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang.shortBadge,
                                    color = accentSky,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            // Command Bar button
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(surfaceDark)
                                    .border(1.dp, borderDark, CircleShape)
                                    .clickable { setMode(IslandMode.COMMAND_BAR) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = "Command Bar",
                                    tint = accentAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            // Collapse button
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(surfaceDark)
                                    .border(1.dp, borderDark, CircleShape)
                                    .clickable { setMode(IslandMode.COLLAPSED) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "Collapse",
                                    tint = textPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // ================= SYSTEM OVERLAYS (Media, Screenshot, Downloads, Approvals) =================
                        activeApproval?.let { req ->
                            PochiApprovalCard(
                                request = req,
                                isDark = true,
                                onDeny = {
                                    bump()
                                    ApprovalManager.confirmDeny(if (isId) "Ditolak" else "Rejected")
                                    botState = BotState.IDLE
                                }
                            )
                        }

                        latestScreenshot?.let { shot ->
                            ScreenshotOverlayCard(
                                screenshot = shot,
                                isId = isId,
                                surfaceDark = surfaceDark,
                                borderDark = borderDark,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                accentSky = accentSky,
                                onCopy = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newUri(context.contentResolver, "Screenshot", shot.uri))
                                    Toast.makeText(
                                        context,
                                        if (isId) "Screenshot disalin ke clipboard" else "Screenshot copied to clipboard",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    PochiSignals.dismissScreenshot()
                                },
                                onSave = {
                                    Toast.makeText(
                                        context,
                                        if (isId) "Tersimpan: ${shot.displayName}" else "Saved: ${shot.displayName}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    PochiSignals.dismissScreenshot()
                                },
                                onShare = {
                                    runCatching {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/*"
                                            putExtra(Intent.EXTRA_STREAM, shot.uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, if (isId) "Bagikan Screenshot" else "Share Screenshot").apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        )
                                    }
                                    PochiSignals.dismissScreenshot()
                                    setMode(IslandMode.COLLAPSED)
                                },
                                onDismiss = { PochiSignals.dismissScreenshot() }
                            )
                        }

                        activeDownload?.let { dl ->
                            DownloadOverlayBar(
                                download = dl,
                                surfaceDark = surfaceDark,
                                borderDark = borderDark,
                                textPrimary = textPrimary,
                                accentSky = accentSky
                            )
                        }

                        if (mediaPlayback.hasTrack) {
                            CompactMediaBar(
                                media = mediaPlayback,
                                surfaceDark = surfaceDark,
                                borderDark = borderDark,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                accentIndigo = accentIndigo,
                                onPrev = {
                                    bump()
                                    dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                                },
                                onPlayPause = {
                                    bump()
                                    dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                },
                                onNext = {
                                    bump()
                                    dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                                }
                            )
                        }

                        // ================= 4 CORE MODULES =================
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(durationMillis = 150),
                            label = "moduleCrossfade"
                        ) { tab ->
                            when (tab) {
                                // ----------------- PHASE 2 MODULE 1: TODAY (Task Planner) -----------------
                                PochiTab.TODAY -> {
                                    TodayModuleView(
                                        lang = lang,
                                        tasks = tasks,
                                        selectedDayOffset = selectedDayOffset,
                                        taskFilter = taskFilter,
                                        onSelectDay = {
                                            bump()
                                            selectedDayOffset = it
                                        },
                                        onSelectFilter = {
                                            bump()
                                            taskFilter = it
                                        },
                                        newTaskInput = newTaskInput,
                                        onNewTaskInputChange = {
                                            newTaskInput = it
                                            bump()
                                        },
                                        onSubmitTasks = {
                                            val parsed = NaturalTaskParser.parse(newTaskInput, selectedDayOffset)
                                            if (parsed.isNotEmpty()) {
                                                PochiPrefs.saveTasks(context, parsed + tasks)
                                                newTaskInput = ""
                                                customEmote = PochiEmote.HAPPY
                                                botState = BotState.DONE
                                            }
                                        },
                                        onToggleTask = { task ->
                                            bump()
                                            val nowDone = !task.isDone
                                            val updated = tasks.map {
                                                if (it.id == task.id) it.copy(isDone = nowDone, isOverdue = false) else it
                                            }
                                            PochiPrefs.saveTasks(context, updated)
                                            if (nowDone) {
                                                customEmote = PochiEmote.HAPPY
                                                botState = BotState.DONE
                                            }
                                        },
                                        onDeleteTask = { task ->
                                            bump()
                                            PochiPrefs.saveTasks(context, tasks.filterNot { it.id == task.id })
                                        },
                                        onClearCompleted = {
                                            bump()
                                            PochiPrefs.saveTasks(
                                                context,
                                                tasks.filterNot { it.dayIndex == selectedDayOffset && it.isDone }
                                            )
                                        },
                                        onStartFocusForTask = { task ->
                                            bump()
                                            linkedTaskId = task.id
                                            pomodoroPhase = PomodoroPhase.WORK
                                            currentTab = PochiTab.FOCUS
                                        },
                                        surfaceDark = surfaceDark,
                                        subtleDark = subtleDark,
                                        borderDark = borderDark,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        accentIndigo = accentIndigo,
                                        accentEmerald = accentEmerald,
                                        accentAmber = accentAmber
                                    )
                                }

                                // ----------------- PHASE 4 MODULE 2: AGENT (Remote AI & MCP Hub) -----------------
                                PochiTab.AGENT -> {
                                    AgentModuleView(
                                        lang = lang,
                                        session = agentSession,
                                        selectedHost = selectedAgentHost,
                                        onSelectHost = { host ->
                                            bump()
                                            selectedAgentHost = host
                                            PochiSignals.updateAgentSession {
                                                it.copy(
                                                    providerName = host,
                                                    compactStatus = if (isId) "Terhubung ke $host" else "Connected to $host",
                                                    recentLogs = (listOf("✓ Switched MCP session to $host") + it.recentLogs).take(8)
                                                )
                                            }
                                        },
                                        promptInput = agentPromptInput,
                                        onPromptChange = {
                                            agentPromptInput = it
                                            bump()
                                        },
                                        onSendPrompt = {
                                            val q = agentPromptInput.trim()
                                            if (q.isNotEmpty()) {
                                                agentPromptInput = ""
                                                executeUnifiedCommand("> $q")
                                            }
                                        },
                                        onSimulateFileEdit = { fileLabel ->
                                            bump()
                                            customEmote = PochiEmote.WORKING
                                            PochiSignals.updateAgentSession {
                                                it.copy(
                                                    isBusy = true,
                                                    currentFileOrTool = fileLabel,
                                                    compactStatus = fileLabel,
                                                    recentLogs = (listOf("• Agent status: $fileLabel") + it.recentLogs).take(8)
                                                )
                                            }
                                        },
                                        onTriggerRemoteApproval = {
                                            bump()
                                            ApprovalManager.requestApproval(
                                                agent = selectedAgentHost,
                                                friendlyTitle = if (isId) "Push commit ke branch utama" else "Push commits to remote branch",
                                                rawCommand = "git push origin main"
                                            )
                                        },
                                        onTriggerChoicePrompt = {
                                            bump()
                                            PochiSignals.updateAgentSession {
                                                it.copy(
                                                    activeChoicePrompt = AgentChoicePrompt(
                                                        id = System.currentTimeMillis().toString(),
                                                        question = if (isId) {
                                                            "Pilih branch tujuan deployment rilis:"
                                                        } else {
                                                            "Select deployment target branch for release:"
                                                        },
                                                        options = listOf("main (production)", "staging", "canary-120fps")
                                                    )
                                                )
                                            }
                                        },
                                        onSelectChoiceOption = { option ->
                                            bump()
                                            customEmote = PochiEmote.HAPPY
                                            PochiSignals.updateAgentSession {
                                                it.copy(
                                                    activeChoicePrompt = null,
                                                    compactStatus = "Deploying -> $option",
                                                    currentFileOrTool = "Deploying $option",
                                                    recentLogs = (listOf("✓ Selected branch: $option") + it.recentLogs).take(8)
                                                )
                                            }
                                        },
                                        isEditingMcpUrl = isEditingMcpUrl,
                                        mcpUrlDraft = mcpUrlDraft,
                                        onMcpUrlDraftChange = { mcpUrlDraft = it },
                                        onToggleEditMcpUrl = {
                                            if (isEditingMcpUrl) {
                                                PochiPrefs.saveMcpUrl(context, mcpUrlDraft)
                                                PochiSignals.updateAgentSession {
                                                    it.copy(endpointUrl = mcpUrlDraft)
                                                }
                                            }
                                            isEditingMcpUrl = !isEditingMcpUrl
                                        },
                                        surfaceDark = surfaceDark,
                                        subtleDark = subtleDark,
                                        borderDark = borderDark,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        accentIndigo = accentIndigo,
                                        accentEmerald = accentEmerald,
                                        accentAmber = accentAmber,
                                        accentSky = accentSky
                                    )
                                }

                                // ----------------- PHASE 2 MODULE 3: FOCUS (Pomodoro Timer) -----------------
                                PochiTab.FOCUS -> {
                                    FocusModuleView(
                                        lang = lang,
                                        tasks = tasks.filter { !it.isDone },
                                        linkedTaskId = linkedTaskId,
                                        onSelectTask = {
                                            bump()
                                            linkedTaskId = it
                                        },
                                        onCompleteLinkedTask = { task ->
                                            bump()
                                            val updated = tasks.map {
                                                if (it.id == task.id) it.copy(isDone = true, isOverdue = false) else it
                                            }
                                            PochiPrefs.saveTasks(context, updated)
                                            customEmote = PochiEmote.HAPPY
                                            botState = BotState.DONE
                                        },
                                        pomodoroPhase = pomodoroPhase,
                                        onSelectPhase = { phase ->
                                            bump()
                                            isFocusRunning = false
                                            pomodoroPhase = phase
                                            focusDurationMinutes = phase.defaultMinutes
                                            focusRemainingSeconds = phase.defaultMinutes * 60
                                        },
                                        completedSessions = completedFocusSessions,
                                        durationMinutes = focusDurationMinutes,
                                        remainingSeconds = focusRemainingSeconds,
                                        isRunning = isFocusRunning,
                                        onSelectPreset = { mins ->
                                            bump()
                                            isFocusRunning = false
                                            focusDurationMinutes = mins
                                            focusRemainingSeconds = mins * 60
                                        },
                                        onToggleStartPause = {
                                            bump()
                                            isFocusRunning = !isFocusRunning
                                            customEmote = if (isFocusRunning) PochiEmote.FOCUS else PochiEmote.SMILING
                                        },
                                        onReset = {
                                            bump()
                                            isFocusRunning = false
                                            focusRemainingSeconds = focusDurationMinutes * 60
                                        },
                                        surfaceDark = surfaceDark,
                                        subtleDark = subtleDark,
                                        borderDark = borderDark,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        accentIndigo = accentIndigo,
                                        accentEmerald = accentEmerald,
                                        accentAmber = accentAmber
                                    )
                                }

                                // ----------------- PHASE 3 MODULE 4: DECK (Quick App & Action Grid) -----------------
                                PochiTab.DECK -> {
                                    DeckModuleView(
                                        lang = lang,
                                        installedApps = installedApps,
                                        pinnedPackages = pinnedPackages,
                                        isCustomizing = isCustomizingDeck,
                                        isTorchOn = isTorchOn,
                                        isFlushStatusBar = isFlushStatusBar,
                                        onToggleFlushStatusBar = {
                                            bump()
                                            PochiPrefs.setFlushStatusBar(context, !isFlushStatusBar)
                                        },
                                        onToggleCustomize = {
                                            bump()
                                            isCustomizingDeck = !isCustomizingDeck
                                        },
                                        onTogglePinApp = { pkg ->
                                            bump()
                                            PochiPrefs.togglePinnedApp(context, pkg)
                                        },
                                        onLaunchApp = { pkg ->
                                            bump()
                                            PochiAgent.execute(context, AgentAction("OPEN_APP", pkg))
                                            setMode(IslandMode.COLLAPSED)
                                        },
                                        onActionDownloads = {
                                            bump()
                                            runCatching {
                                                val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            }
                                            setMode(IslandMode.COLLAPSED)
                                        },
                                        onActionScreenshot = {
                                            bump()
                                            setMode(IslandMode.COLLAPSED)
                                            coroutineScope.launch {
                                                delay(280)
                                                val ok = PochiAccessibilityService.takeScreenshot()
                                                if (!ok) {
                                                    Toast.makeText(
                                                        context,
                                                        if (isId) {
                                                            "Aktifkan Aksesibilitas Pochi untuk Screenshot 1-ketuk"
                                                        } else {
                                                            "Enable Pochi Accessibility Service for 1-tap Screenshot"
                                                        },
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        },
                                        onActionLockScreen = {
                                            bump()
                                            setMode(IslandMode.COLLAPSED)
                                            val ok = PochiAccessibilityService.lockScreen()
                                            if (!ok) {
                                                Toast.makeText(
                                                    context,
                                                    if (isId) {
                                                        "Aktifkan Aksesibilitas Pochi untuk Kunci Layar 1-ketuk"
                                                    } else {
                                                        "Enable Pochi Accessibility Service for 1-tap Lock Screen"
                                                    },
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        onActionToggleTorch = {
                                            bump()
                                            val next = !isTorchOn
                                            if (setTorchState(context, next)) {
                                                isTorchOn = next
                                                customEmote = if (next) PochiEmote.EXCITED else PochiEmote.CALM
                                            }
                                        },
                                        onActionPowerOff = onPowerOffService,
                                        surfaceDark = surfaceDark,
                                        subtleDark = subtleDark,
                                        borderDark = borderDark,
                                        textPrimary = textPrimary,
                                        textSecondary = textSecondary,
                                        accentIndigo = accentIndigo,
                                        accentEmerald = accentEmerald,
                                        accentAmber = accentAmber
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
// PHASE 2 - MODULE 1: TODAY (Weekly Strip Calendar + Progress Ring + NLP Planner)
// ============================================================================
@Composable
private fun TodayModuleView(
    lang: PochiLang,
    tasks: List<TodayTask>,
    selectedDayOffset: Int,
    taskFilter: TaskFilter,
    onSelectDay: (Int) -> Unit,
    onSelectFilter: (TaskFilter) -> Unit,
    newTaskInput: String,
    onNewTaskInputChange: (String) -> Unit,
    onSubmitTasks: () -> Unit,
    onToggleTask: (TodayTask) -> Unit,
    onDeleteTask: (TodayTask) -> Unit,
    onClearCompleted: () -> Unit,
    onStartFocusForTask: (TodayTask) -> Unit,
    surfaceDark: Color,
    subtleDark: Color,
    borderDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentIndigo: Color,
    accentEmerald: Color,
    accentAmber: Color
) {
    val isId = lang == PochiLang.ID
    val allDayTasks = tasks.filter { it.dayIndex == selectedDayOffset }
    val totalCount = allDayTasks.size
    val doneCount = allDayTasks.count { it.isDone }
    val remainingCount = totalCount - doneCount
    val overdueCount = allDayTasks.count { it.isCurrentlyOverdue() }
    val progressFraction = if (totalCount > 0) doneCount.toFloat() / totalCount.toFloat() else 1f

    val filteredTasks = remember(allDayTasks, taskFilter) {
        when (taskFilter) {
            TaskFilter.ALL -> allDayTasks
            TaskFilter.ACTIVE -> allDayTasks.filter { !it.isDone }
            TaskFilter.DONE -> allDayTasks.filter { it.isDone }
        }
    }

    val weekDays = remember(lang) {
        val cal = Calendar.getInstance()
        val locale = if (lang == PochiLang.ID) Locale("id", "ID") else Locale.US
        val dayFmt = SimpleDateFormat("EEE", locale)
        (-3..3).map { offset ->
            val c = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            Triple(
                offset,
                dayFmt.format(c.time).take(3).uppercase(locale),
                c.get(Calendar.DAY_OF_MONTH)
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Weekly Strip Calendar + Progress Ring Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 7-Day Weekly Strip
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekDays.forEach { (offset, dayLabel, dateNum) ->
                    val isSelected = offset == selectedDayOffset
                    val isToday = offset == 0
                    val hasOverdue = tasks.any { it.dayIndex == offset && it.isCurrentlyOverdue() }
                    val hasPending = tasks.any { it.dayIndex == offset && !it.isDone }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) accentIndigo else Color.Transparent)
                            .clickable { onSelectDay(offset) }
                            .padding(horizontal = 5.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = dayLabel.take(1),
                            color = if (isSelected) Color.White else textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dateNum.toString(),
                            color = when {
                                isSelected -> Color.White
                                isToday -> accentIndigo
                                else -> textPrimary
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        hasOverdue -> accentAmber
                                        isSelected && hasPending -> Color.White
                                        hasPending -> accentEmerald
                                        else -> Color.Transparent
                                    }
                                )
                        )
                    }
                }
            }

            // Progress Ring (Remaining & Overdue summary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(subtleDark)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
                    Canvas(modifier = Modifier.size(30.dp)) {
                        val sw = 3.5.dp.toPx()
                        drawArc(
                            color = borderDark,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = sw, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = if (overdueCount > 0) accentAmber else accentEmerald,
                            startAngle = -90f,
                            sweepAngle = 360f * progressFraction,
                            useCenter = false,
                            style = Stroke(width = sw, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "$doneCount/$totalCount",
                        color = textPrimary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(
                        text = if (isId) "$remainingCount sisa" else "$remainingCount left",
                        color = textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            overdueCount > 0 -> if (isId) "$overdueCount telat" else "$overdueCount overdue"
                            else -> if (isId) "Sesuai jadwal" else "On track"
                        },
                        color = if (overdueCount > 0) accentAmber else accentEmerald,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Sub-filter bar (All / Active / Done + Clear Done)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    TaskFilter.ALL to (if (isId) "Semua ($totalCount)" else "All ($totalCount)"),
                    TaskFilter.ACTIVE to (if (isId) "Aktif ($remainingCount)" else "Active ($remainingCount)"),
                    TaskFilter.DONE to (if (isId) "Selesai ($doneCount)" else "Done ($doneCount)")
                ).forEach { (filter, label) ->
                    val active = taskFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) accentIndigo.copy(alpha = 0.22f) else surfaceDark)
                            .border(1.dp, if (active) accentIndigo else borderDark, RoundedCornerShape(50))
                            .clickable { onSelectFilter(filter) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (active) Color.White else textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (doneCount > 0) {
                Text(
                    text = if (isId) "Hapus Selesai" else "Clear Done",
                    color = accentIndigo,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onClearCompleted() }
                )
            }
        }

        // Interactive Checklist
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 74.dp, max = 140.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(16.dp))
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isId) {
                            "Belum ada tugas. Ketik di bawah (mis: \"cek PR, lalu jam 2 siang rapat\")"
                        } else {
                            "No tasks here. Type below (e.g. \"review PR, then 2pm team meeting\")"
                        },
                        color = textSecondary,
                        fontSize = 10.sp
                    )
                }
            } else {
                filteredTasks.forEach { task ->
                    val isLate = task.isCurrentlyOverdue()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(subtleDark)
                            .clickable { onToggleTask(task) }
                            .padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = if (task.isDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = when {
                                task.isDone -> accentEmerald
                                isLate -> accentAmber
                                else -> textSecondary
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = task.title,
                            color = if (task.isDone) textSecondary else textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (task.isHighPriority && !task.isDone) {
                            Text(
                                text = "!",
                                color = Color(0xFFEF4444),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.18f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        if (isLate && !task.isDone) {
                            Text(
                                text = if (isId) "TELAT" else "LATE",
                                color = accentAmber,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentAmber.copy(alpha = 0.16f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        if (task.timeLabel.isNotBlank()) {
                            Text(
                                text = task.timeLabel,
                                color = accentIndigo,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentIndigo.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (!task.isDone) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = "Focus",
                                tint = accentEmerald,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable { onStartFocusForTask(task) }
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Delete",
                            tint = textSecondary,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onDeleteTask(task) }
                        )
                    }
                }
            }
        }

        // Bottom Natural Language Task Input Bar (Bilingual ID / EN)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = accentIndigo,
                modifier = Modifier.size(15.dp)
            )
            BasicTextField(
                value = newTaskInput,
                onValueChange = onNewTaskInputChange,
                textStyle = TextStyle(color = textPrimary, fontSize = 11.sp),
                cursorBrush = SolidColor(accentIndigo),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmitTasks() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (newTaskInput.isEmpty()) {
                        Text(
                            text = if (isId) {
                                "Ketik tugas: \"cek PR, lalu jam 2 siang rapat tim\"..."
                            } else {
                                "Add tasks: \"review PR, then 2pm team meeting\"..."
                            },
                            color = textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accentIndigo)
                    .clickable { onSubmitTasks() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isId) "Tambah" else "Add",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ============================================================================
// PHASE 4 - MODULE 2: AGENT (Remote AI & WebSocket/MCP Hub)
// ============================================================================
@Composable
private fun AgentModuleView(
    lang: PochiLang,
    session: AgentSessionState,
    selectedHost: String,
    onSelectHost: (String) -> Unit,
    promptInput: String,
    onPromptChange: (String) -> Unit,
    onSendPrompt: () -> Unit,
    onSimulateFileEdit: (String) -> Unit,
    onTriggerRemoteApproval: () -> Unit,
    onTriggerChoicePrompt: () -> Unit,
    onSelectChoiceOption: (String) -> Unit,
    isEditingMcpUrl: Boolean,
    mcpUrlDraft: String,
    onMcpUrlDraftChange: (String) -> Unit,
    onToggleEditMcpUrl: () -> Unit,
    surfaceDark: Color,
    subtleDark: Color,
    borderDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentIndigo: Color,
    accentEmerald: Color,
    accentAmber: Color,
    accentSky: Color
) {
    val isId = lang == PochiLang.ID
    val hosts = listOf("Claude Code", "Codex", "Gemini")
    val contextRatio = (session.contextTokensUsed.toFloat() / session.contextTokensMax.toFloat()).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            hosts.forEach { host ->
                val active = host == selectedHost
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) accentIndigo.copy(alpha = 0.22f) else surfaceDark)
                        .border(
                            1.dp,
                            if (active) accentIndigo else borderDark,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectHost(host) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = host,
                        color = if (active) accentSky else textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfaceDark)
                    .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                    .clickable { onToggleEditMcpUrl() }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isEditingMcpUrl) (if (isId) "Simpan WS" else "Save WS") else "MCP WS",
                    color = accentEmerald,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(visible = isEditingMcpUrl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfaceDark)
                    .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("WS: ", color = textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                BasicTextField(
                    value = mcpUrlDraft,
                    onValueChange = onMcpUrlDraftChange,
                    textStyle = TextStyle(color = accentSky, fontSize = 10.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(accentSky),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (session.isBusy) accentSky else accentEmerald)
                    )
                    Text(
                        text = session.compactStatus,
                        color = textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MiniStatusButton("Editing auth.ts", accentSky) {
                        onSimulateFileEdit("Editing auth.ts")
                    }
                    MiniStatusButton("Running tests", accentEmerald) {
                        onSimulateFileEdit("Running tests")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isId) "Konteks" else "Context Window", color = textSecondary, fontSize = 9.sp)
                        Text(
                            text = "${session.contextTokensUsed / 1000}k / ${session.contextTokensMax / 1000}k",
                            color = textPrimary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    LinearProgressIndicator(
                        progress = { contextRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = accentSky,
                        trackColor = subtleDark
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isId) "Kuota Sesi" else "Session Quota", color = textSecondary, fontSize = 9.sp)
                        Text(
                            text = "${(session.sessionQuotaPercent * 100).toInt()}%",
                            color = accentEmerald,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    LinearProgressIndicator(
                        progress = { session.sessionQuotaPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50)),
                        color = accentEmerald,
                        trackColor = subtleDark
                    )
                }
            }
        }

        session.activeChoicePrompt?.let { choice ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(subtleDark)
                    .border(1.dp, accentIndigo.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = choice.question,
                    color = textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    choice.options.forEach { opt ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentIndigo.copy(alpha = 0.22f))
                                .border(1.dp, accentIndigo, RoundedCornerShape(8.dp))
                                .clickable { onSelectChoiceOption(opt) }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(opt, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(subtleDark)
                    .clickable { onTriggerRemoteApproval() }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isId) "⚡ Izin: git push" else "⚡ Approval: git push",
                    color = accentAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(subtleDark)
                    .clickable { onTriggerChoicePrompt() }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isId) "🔀 Pilihan Branch" else "🔀 Branch Prompt",
                    color = accentSky,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Terminal,
                contentDescription = null,
                tint = accentSky,
                modifier = Modifier.size(15.dp)
            )
            BasicTextField(
                value = promptInput,
                onValueChange = onPromptChange,
                textStyle = TextStyle(color = textPrimary, fontSize = 11.sp),
                cursorBrush = SolidColor(accentSky),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendPrompt() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (promptInput.isEmpty()) {
                        Text(
                            text = if (isId) "Kirim instruksi ke $selectedHost..." else "Send instruction to $selectedHost...",
                            color = textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    inner()
                }
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(accentIndigo)
                    .clickable { onSendPrompt() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isId) "Kirim" else "Send",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ============================================================================
// PHASE 2 - MODULE 3: FOCUS (Task-Linked Pomodoro Timer with Work/Break Phases)
// ============================================================================
@Composable
private fun FocusModuleView(
    lang: PochiLang,
    tasks: List<TodayTask>,
    linkedTaskId: String?,
    onSelectTask: (String?) -> Unit,
    onCompleteLinkedTask: (TodayTask) -> Unit,
    pomodoroPhase: PomodoroPhase,
    onSelectPhase: (PomodoroPhase) -> Unit,
    completedSessions: Int,
    durationMinutes: Int,
    remainingSeconds: Int,
    isRunning: Boolean,
    onSelectPreset: (Int) -> Unit,
    onToggleStartPause: () -> Unit,
    onReset: () -> Unit,
    surfaceDark: Color,
    subtleDark: Color,
    borderDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentIndigo: Color,
    accentEmerald: Color,
    accentAmber: Color
) {
    val isId = lang == PochiLang.ID
    val totalSecs = (durationMinutes * 60).coerceAtLeast(1)
    val fraction = (remainingSeconds.toFloat() / totalSecs.toFloat()).coerceIn(0f, 1f)
    val mm = remainingSeconds / 60
    val ss = remainingSeconds % 60
    val linkedTask = tasks.firstOrNull { it.id == linkedTaskId } ?: tasks.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Phase Switcher (Focus / Short Break / Long Break) + Session Counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PomodoroPhase.entries.forEach { phase ->
                    val active = phase == pomodoroPhase
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) accentIndigo.copy(alpha = 0.22f) else surfaceDark)
                            .border(1.dp, if (active) accentIndigo else borderDark, RoundedCornerShape(50))
                            .clickable { onSelectPhase(phase) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = phase.label(lang),
                            color = if (active) Color.White else textSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = if (isId) "🔥 $completedSessions sesi" else "🔥 $completedSessions sessions",
                color = accentAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Timer Display + Ring + Presets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(74.dp)) {
                Canvas(modifier = Modifier.size(74.dp)) {
                    val sw = 6.dp.toPx()
                    drawArc(
                        color = subtleDark,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = if (isRunning) accentEmerald else accentIndigo,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = String.format(Locale.US, "%02d:%02d", mm, ss),
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = linkedTask?.title ?: (if (isId) "Sesi Fokus Mendalam" else "Deep Focus Session"),
                        color = textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (linkedTask != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentEmerald.copy(alpha = 0.18f))
                                .clickable { onCompleteLinkedTask(linkedTask) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isId) "✓ Selesai" else "✓ Done",
                                color = accentEmerald,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Preset chips (5m, 15m, 25m, 45m, 60m)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(5, 15, 25, 45, 60).forEach { preset ->
                        val selected = preset == durationMinutes
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) accentIndigo else subtleDark)
                                .clickable { onSelectPreset(preset) }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${preset}m",
                                color = if (selected) Color.White else textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Start/Pause + Reset buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isRunning) accentAmber else accentEmerald)
                            .clickable { onToggleStartPause() }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                isRunning -> if (isId) "Jeda" else "Pause"
                                else -> if (isId) "Mulai Fokus" else "Start Focus"
                            },
                            color = Color(0xFF052E16),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(subtleDark)
                            .clickable { onReset() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isId) "Ulang" else "Reset",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Link to Today Task selector
        if (tasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isId) "Tugas:" else "Link task:",
                    color = textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                tasks.take(6).forEach { t ->
                    val active = (linkedTask?.id == t.id)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) accentIndigo.copy(alpha = 0.22f) else surfaceDark)
                            .border(1.dp, if (active) accentIndigo else borderDark, RoundedCornerShape(50))
                            .clickable { onSelectTask(t.id) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = t.title.take(22),
                            color = if (active) Color.White else textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// PHASE 3 - MODULE 4: DECK (Quick App & System Shortcut Grid)
// ============================================================================
@Composable
private fun DeckModuleView(
    lang: PochiLang,
    installedApps: List<LaunchableAppInfo>,
    pinnedPackages: Set<String>,
    isCustomizing: Boolean,
    isTorchOn: Boolean,
    isFlushStatusBar: Boolean,
    onToggleFlushStatusBar: () -> Unit,
    onToggleCustomize: () -> Unit,
    onTogglePinApp: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    onActionDownloads: () -> Unit,
    onActionScreenshot: () -> Unit,
    onActionLockScreen: () -> Unit,
    onActionToggleTorch: () -> Unit,
    onActionPowerOff: () -> Unit,
    surfaceDark: Color,
    subtleDark: Color,
    borderDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentIndigo: Color,
    accentEmerald: Color,
    accentAmber: Color
) {
    val isId = lang == PochiLang.ID
    val displayedApps = remember(installedApps, pinnedPackages, isCustomizing) {
        if (isCustomizing) {
            installedApps.take(24)
        } else {
            val pinned = installedApps.filter { it.packageName in pinnedPackages }
            if (pinned.isNotEmpty()) pinned.take(8) else installedApps.take(8)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // System Shortcuts Row (Downloads, Screenshot, Lock Screen, Torch)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DeckShortcutTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Download,
                label = if (isId) "Unduhan" else "Downloads",
                accent = accentIndigo,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionDownloads
            )
            DeckShortcutTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Screenshot,
                label = "Screenshot",
                accent = accentEmerald,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionScreenshot
            )
            DeckShortcutTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Lock,
                label = if (isId) "Kunci" else "Lock",
                accent = accentAmber,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionLockScreen
            )
            DeckShortcutTile(
                modifier = Modifier.weight(1f),
                icon = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                label = if (isId) (if (isTorchOn) "Senter On" else "Senter") else (if (isTorchOn) "Torch On" else "Torch"),
                accent = if (isTorchOn) accentAmber else textSecondary,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionToggleTorch
            )
        }

        // Pinned Apps Header + Status-bar Flush toggle + Customize toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    isCustomizing -> if (isId) "Ketuk aplikasi untuk pin" else "Tap apps to pin/unpin"
                    else -> if (isId) "Aplikasi Favorit" else "Pinned Apps"
                },
                color = textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isFlushStatusBar) {
                        if (isId) "✓ Nempel Statusbar" else "✓ Statusbar Dock"
                    } else {
                        if (isId) "Mode Melayang" else "Floating Mode"
                    },
                    color = accentEmerald,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onToggleFlushStatusBar() }
                )
                Text(
                    text = if (isCustomizing) (if (isId) "Selesai" else "Done") else (if (isId) "Atur Pin" else "Edit Pins"),
                    color = accentIndigo,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onToggleCustomize() }
                )
                Text(
                    text = if (isId) "Matikan" else "Hide",
                    color = Color(0xFFEF4444),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onActionPowerOff() }
                )
            }
        }

        // 4-Column App Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp, max = 140.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            displayedApps.chunked(4).forEach { rowApps ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowApps.forEach { app ->
                        val isPinned = app.packageName in pinnedPackages
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCustomizing && isPinned) accentIndigo.copy(alpha = 0.22f) else surfaceDark)
                                .border(
                                    1.dp,
                                    if (isCustomizing && isPinned) accentIndigo else borderDark,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (isCustomizing) {
                                        onTogglePinApp(app.packageName)
                                    } else {
                                        onLaunchApp(app.packageName)
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(subtleDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = app.label.take(1).uppercase(Locale.getDefault()),
                                    color = textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = app.label,
                                color = textPrimary,
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    repeat(4 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ============================================================================
// SYSTEM OVERLAY CARDS (Screenshot, Downloads, Media Controller, Chips)
// ============================================================================
@Composable
private fun ScreenshotOverlayCard(
    screenshot: ScreenshotPreviewInfo,
    isId: Boolean,
    surfaceDark: Color,
    borderDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentSky: Color,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceDark)
            .border(1.dp, accentSky.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Image,
            contentDescription = null,
            tint = accentSky,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isId) "Screenshot Diambil" else "Screenshot Captured",
                color = textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = screenshot.displayName,
                color = textSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        MiniStatusButton(if (isId) "Salin" else "Copy", accentSky, onCopy)
        MiniStatusButton(if (isId) "Simpan" else "Save", Color(0xFF10B981), onSave)
        MiniStatusButton(if (isId) "Bagikan" else "Share", Color(0xFF6366F1), onShare)
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Dismiss",
            tint = textSecondary,
            modifier = Modifier
                .size(15.dp)
                .clickable { onDismiss() }
        )
    }
}

@Composable
private fun DownloadOverlayBar(
    download: DownloadStatusInfo,
    surfaceDark: Color,
    borderDark: Color,
    textPrimary: Color,
    accentSky: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceDark)
            .border(1.dp, borderDark, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = download.title,
                color = textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(download.fraction * 100).toInt()}%",
                color = accentSky,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { download.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50)),
            color = accentSky,
            trackColor = borderDark
        )
    }
}

@Composable
private fun CompactMediaBar(
    media: MediaPlaybackInfo,
    surfaceDark: Color,
    borderDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentIndigo: Color,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(surfaceDark)
            .border(1.dp, borderDark, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = accentIndigo,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = media.title,
                color = textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (media.artist.isNotBlank()) {
                Text(
                    text = "${media.artist} • ${media.appName}",
                    color = textSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous",
            tint = textPrimary,
            modifier = Modifier
                .size(18.dp)
                .clickable { onPrev() }
        )
        Icon(
            imageVector = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = "Play/Pause",
            tint = accentIndigo,
            modifier = Modifier
                .size(20.dp)
                .clickable { onPlayPause() }
        )
        Icon(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = "Next",
            tint = textPrimary,
            modifier = Modifier
                .size(18.dp)
                .clickable { onNext() }
        )
    }
}

@Composable
private fun DeckShortcutTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    accent: Color,
    surfaceDark: Color,
    borderDark: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceDark)
            .border(1.dp, borderDark, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, color = Color(0xFFF8FAFC), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun MiniStatusButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.16f))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(label, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickCommandHintChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF1E1E24))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(label, color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

private fun dispatchMediaKey(context: Context, keyCode: Int) {
    runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val eventTime = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        am.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
    }
}

private fun setTorchState(context: Context, enabled: Boolean): Boolean {
    return try {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull() ?: return false
        cm.setTorchMode(id, enabled)
        true
    } catch (_: Exception) {
        false
    }
}
