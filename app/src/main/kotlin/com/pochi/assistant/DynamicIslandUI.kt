package com.pochi.assistant

import android.Manifest
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * 6 Human-Centric Daily AI Assistant Modules (Icon-only top bar so text is NEVER truncated to "..."):
 * 1. TODAY  - Asisten Harian, Pengingat Otomatis (Sarapan/Air/Istirahat), & Jadwal Tugas
 * 2. CHAT   - Chat & Suara Pochi (Bisa Ngobrol Suara/Teks, Telepon, SMS, WA, & Buka Aplikasi Apa Saja)
 * 3. LIFE   - Cuaca Pintar & Jadwal Sholat 5 Waktu (dengan Hitung Mundur Otomatis)
 * 4. WALLET - Money Tracker (Catat Pengeluaran Rp, Batas Budget Harian, & Pintasan Dompet/QRIS)
 * 5. FOCUS  - Timer Fokus & Produktivitas Otomatis
 * 6. DECK   - Pintasan Alat Harian & Pencarian Semua Aplikasi di HP
 */
enum class PochiTab(
    val titleEn: String,
    val titleId: String,
    val subtitleEn: String,
    val subtitleId: String,
    val icon: ImageVector
) {
    TODAY(
        titleEn = "Today & Assistant",
        titleId = "Hari Ini & Asisten",
        subtitleEn = "Daily plan, breakfast & hydration care",
        subtitleId = "Rencana harian, pengingat sarapan & air putih",
        icon = Icons.Rounded.CheckCircleOutline
    ),
    CHAT(
        titleEn = "Chat, Voice & Phone Actions",
        titleId = "Chat, Suara & Aksi HP",
        subtitleEn = "Talk or chat with Pochi • Call, SMS, WA & Open any app",
        subtitleId = "Ngobrol teks/suara • Telepon, SMS, WA & buka aplikasi",
        icon = Icons.Rounded.Mic
    ),
    LIFE(
        titleEn = "Weather & Prayer",
        titleId = "Cuaca & Sholat",
        subtitleEn = "Live forecast & 5 daily prayer times",
        subtitleId = "Prakiraan cuaca & jadwal sholat 5 waktu",
        icon = Icons.Rounded.WbSunny
    ),
    WALLET(
        titleEn = "Money & Quick Pay",
        titleId = "Dompet & Keuangan",
        subtitleEn = "Daily expense tracker & e-wallet shortcuts",
        subtitleId = "Catatan pengeluaran harian & pintasan QRIS/Dompet",
        icon = Icons.Rounded.AccountBalanceWallet
    ),
    FOCUS(
        titleEn = "Focus Timer",
        titleId = "Timer Fokus",
        subtitleEn = "Auto-productivity Pomodoro & break timer",
        subtitleId = "Timer produktivitas fokus & istirahat mata",
        icon = Icons.Rounded.Timer
    ),
    DECK(
        titleEn = "All Apps & Quick Tools",
        titleId = "Semua Aplikasi & Alat",
        subtitleEn = "Open any installed app, call, SMS & system tools",
        subtitleId = "Buka semua aplikasi di HP, telepon, SMS & alat sistem",
        icon = Icons.Rounded.GridView
    );

    fun label(lang: PochiLang): String = if (lang == PochiLang.ID) titleId else titleEn
    fun subtitle(lang: PochiLang): String = if (lang == PochiLang.ID) subtitleId else subtitleEn
}

data class PochiChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val actionBadge: String? = null,
    val timeLabel: String
)

enum class QuickPhoneComposerMode {
    NONE, CALL, SMS, WHATSAPP, OPEN_APP
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

    // Pure Matte-Black (#0A0A0A) Rectangular Palette
    val islandBlack = Color(0xFF0A0A0A)
    val surfaceDark = Color(0xFF141418)
    val subtleDark = Color(0xFF1E1E24)
    val borderDark = Color(0xFF262630)
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
    var isSlimEyeBox by remember { mutableStateOf(false) }
    var pokeCount by remember { mutableIntStateOf(0) }
    var eyeGazeOffset by remember { mutableStateOf(Offset.Zero) }

    // Reactive preferences & system signals
    val tasks by PochiPrefs.tasksFlow.collectAsState()
    val expenses by PochiPrefs.expensesFlow.collectAsState()
    val dailyBudgetRp by PochiPrefs.dailyBudgetFlow.collectAsState()
    val waterGlasses by PochiPrefs.waterGlassesFlow.collectAsState()
    val selectedCity by PochiPrefs.cityFlow.collectAsState()
    val pinnedPackages by PochiPrefs.pinnedAppsFlow.collectAsState()
    val completedFocusSessions by PochiPrefs.focusSessionsFlow.collectAsState()
    val activeApproval by ApprovalManager.activeRequest.collectAsState()
    val dismissSignal by ApprovalManager.dismissSignal.collectAsState()
    val mediaPlayback by PochiSignals.mediaPlayback.collectAsState()
    val activeDownload by PochiSignals.activeDownload.collectAsState()
    val latestScreenshot by PochiSignals.latestScreenshot.collectAsState()

    // Today Module State
    var selectedDayOffset by remember { mutableIntStateOf(0) }
    var taskFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var newTaskInput by remember { mutableStateOf("") }

    // Weather & Prayer Module State (100% Automatic User Location Detection + Hourly Chart Data)
    var liveTempOverride by remember(selectedCity) { mutableStateOf<Int?>(null) }
    var liveHumidityOverride by remember(selectedCity) { mutableStateOf<Int?>(null) }
    var liveWindOverride by remember(selectedCity) { mutableStateOf<Int?>(null) }
    var liveHourlyTemps by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var livePrayerSchedule by remember { mutableStateOf<List<PrayerTimeItem>?>(null) }
    var locationSourceLabel by remember { mutableStateOf("Auto GPS/IP") }
    var isRefreshingWeather by remember { mutableStateOf(false) }

    // Wallet / Money Tracker Module State
    var expenseInput by remember { mutableStateOf("") }
    var isEditingBudget by remember { mutableStateOf(false) }
    var budgetDraft by remember(dailyBudgetRp) { mutableStateOf((dailyBudgetRp / 1000).toString()) }

    // Focus Module State
    var pomodoroPhase by remember { mutableStateOf(PomodoroPhase.WORK) }
    var focusDurationMinutes by remember { mutableIntStateOf(25) }
    var focusRemainingSeconds by remember { mutableIntStateOf(25 * 60) }
    var isFocusRunning by remember { mutableStateOf(false) }
    var linkedTaskId by remember { mutableStateOf<String?>(null) }

    // Smart AI Command Bar & Chat/Voice Assistant State (Pochi Hanya Mendengar - Tanpa Suara TTS)
    var commandBarInput by remember { mutableStateOf("") }
    var aiQuickReply by remember { mutableStateOf<String?>(null) }
    var isAiThinking by remember { mutableStateOf(false) }
    var chatInput by remember { mutableStateOf("") }
    var isListeningVoice by remember { mutableStateOf(false) }
    var voicePartialText by remember { mutableStateOf("") }
    var quickComposerMode by remember { mutableStateOf(QuickPhoneComposerMode.NONE) }
    var quickTargetInput by remember { mutableStateOf("") }
    var quickMessageInput by remember { mutableStateOf("") }
    var chatMessages by remember(isId) {
        mutableStateOf(
            listOf(
                PochiChatMessage(
                    id = "welcome_1",
                    isUser = false,
                    text = if (isId) {
                        "Halo! Aku Pochi. Ketuk tombol mikrofon 🎙️ untuk bicara (aku siap mendengarkan) atau ketik pesan untuk buka aplikasi, telepon, SMS/WA, cek cuaca & sholat otomatis, atau catat uang!"
                    } else {
                        "Hi! I'm Pochi. Tap the mic 🎙️ to speak (I'm listening) or type to open any app, call contacts, send SMS/WA, check auto-located weather & prayer, or track money!"
                    },
                    actionBadge = if (isId) "Siap Mendengar" else "Listening Ready",
                    timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
            )
        )
    }

    // Deck Module State
    var installedApps by remember { mutableStateOf<List<LaunchableAppInfo>>(emptyList()) }
    var isCustomizingDeck by remember { mutableStateOf(false) }
    var deckSearchQuery by remember { mutableStateOf("") }
    var showAllAppsInDeck by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }

    val remainingTodayCount = tasks.count { !it.isDone && it.dayIndex == 0 }
    val smartBanner = remember(lang, selectedCity, waterGlasses, remainingTodayCount, interactionTick) {
        SmartLifeAssistant.currentBanner(
            lang = lang,
            city = selectedCity,
            waterGlasses = waterGlasses,
            remainingTasks = remainingTodayCount
        )
    }

    fun bump() {
        interactionTick++
        lastActiveTime = System.currentTimeMillis()
        isSlimEyeBox = false
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

    // Load installed apps on IO dispatcher
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

    // 100% Automatic User Location + Live Weather + Hourly Temperature Chart + Prayer Times
    fun autoDetectLocationAndWeather() {
        if (isRefreshingWeather) return
        isRefreshingWeather = true
        coroutineScope.launch(Dispatchers.IO) {
            var lat: Double? = null
            var lon: Double? = null
            var detectedCity: String? = null
            var sourceBadge = "Auto IP"

            // 1. Try Android GPS / Network LocationManager first if permission is granted
            runCatching {
                val hasCoarse = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasFine = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (hasCoarse || hasFine) {
                    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    val providers = listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER
                    )
                    var bestLoc: Location? = null
                    for (p in providers) {
                        val loc = runCatching { lm?.getLastKnownLocation(p) }.getOrNull() ?: continue
                        if (bestLoc == null || loc.time > bestLoc.time) {
                            bestLoc = loc
                        }
                    }
                    if (bestLoc != null) {
                        lat = bestLoc.latitude
                        lon = bestLoc.longitude
                        sourceBadge = "GPS"
                        @Suppress("DEPRECATION")
                        val addresses = runCatching {
                            Geocoder(context, Locale.getDefault()).getFromLocation(bestLoc.latitude, bestLoc.longitude, 1)
                        }.getOrNull()
                        val addr = addresses?.firstOrNull()
                        detectedCity = addr?.locality
                            ?: addr?.subAdminArea
                            ?: addr?.adminArea
                    }
                }
            }

            // 2. Automatic IP Geolocation fallback (works immediately without GPS lock)
            if (lat == null || lon == null || detectedCity.isNullOrBlank()) {
                runCatching {
                    val conn = (URL("https://ipwho.is/").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3500
                        readTimeout = 3500
                    }
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (json.optBoolean("success", true)) {
                        if (lat == null) lat = json.optDouble("latitude", -6.2088)
                        if (lon == null) lon = json.optDouble("longitude", 106.8456)
                        if (detectedCity.isNullOrBlank()) {
                            detectedCity = json.optString("city").takeIf { it.isNotBlank() }
                                ?: json.optString("region").takeIf { it.isNotBlank() }
                        }
                    }
                }.onFailure {
                    runCatching {
                        val conn2 = (URL("https://get.geojs.io/v1/ip/geo.json").openConnection() as HttpURLConnection).apply {
                            connectTimeout = 3500
                            readTimeout = 3500
                        }
                        val json2 = JSONObject(conn2.inputStream.bufferedReader().use { it.readText() })
                        if (lat == null) lat = json2.optString("latitude").toDoubleOrNull()
                        if (lon == null) lon = json2.optString("longitude").toDoubleOrNull()
                        if (detectedCity.isNullOrBlank()) {
                            detectedCity = json2.optString("city").takeIf { it.isNotBlank() }
                        }
                    }
                }
            }

            val finalLat = lat ?: -6.2088
            val finalLon = lon ?: 106.8456
            val finalCity = detectedCity?.trim()?.takeIf { it.isNotBlank() } ?: selectedCity

            // 3. Fetch Live Weather + Hourly Temperature Chart (06:00 - 21:00) from Open-Meteo
            var fetchedTemp: Int? = null
            var fetchedWind: Int? = null
            var fetchedHumidity: Int? = null
            val hourlySlots = mutableListOf<Pair<String, Int>>()

            runCatching {
                val weatherUrl = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$finalLat&longitude=$finalLon&current_weather=true&hourly=temperature_2m,relative_humidity_2m&timezone=auto&forecast_days=1"
                )
                val conn = (weatherUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val cw = json.optJSONObject("current_weather")
                if (cw != null) {
                    fetchedTemp = cw.optDouble("temperature", 29.0).roundToInt()
                    fetchedWind = cw.optDouble("windspeed", 11.0).roundToInt()
                }
                val hourly = json.optJSONObject("hourly")
                val tempsArr = hourly?.optJSONArray("temperature_2m")
                val humArr = hourly?.optJSONArray("relative_humidity_2m")
                val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (humArr != null && nowHour < humArr.length()) {
                    fetchedHumidity = humArr.optInt(nowHour, 72)
                }
                if (tempsArr != null && tempsArr.length() >= 22) {
                    listOf(6 to "06:00", 9 to "09:00", 12 to "12:00", 15 to "15:00", 18 to "18:00", 21 to "21:00").forEach { (hIdx, label) ->
                        hourlySlots.add(label to tempsArr.optDouble(hIdx, 28.0).roundToInt())
                    }
                }
            }

            // 4. Fetch Accurate 5-Time Prayer Schedule for User Coordinates (Aladhan Kemenag RI Method 20)
            var fetchedPrayers: List<PrayerTimeItem>? = null
            runCatching {
                val prayerUrl = URL("https://api.aladhan.com/v1/timings?latitude=$finalLat&longitude=$finalLon&method=20")
                val conn = (prayerUrl.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val timings = json.optJSONObject("data")?.optJSONObject("timings")
                if (timings != null) {
                    fun parseHm(key: String, nameId: String, nameEn: String, defH: Int, defM: Int): PrayerTimeItem {
                        val raw = timings.optString(key, "").substringBefore(" ").trim()
                        val parts = raw.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: defH
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: defM
                        return PrayerTimeItem(nameId, nameEn, h, m)
                    }
                    fetchedPrayers = listOf(
                        parseHm("Fajr", "Subuh", "Fajr", 4, 38),
                        parseHm("Dhuhr", "Dzuhur", "Dhuhr", 11, 56),
                        parseHm("Asr", "Ashar", "Asr", 15, 14),
                        parseHm("Maghrib", "Maghrib", "Maghrib", 17, 57),
                        parseHm("Isha", "Isya", "Isha", 19, 8)
                    )
                }
            }

            withContext(Dispatchers.Main) {
                if (finalCity != selectedCity) {
                    PochiPrefs.saveCity(context, finalCity)
                }
                locationSourceLabel = sourceBadge
                if (fetchedTemp != null) liveTempOverride = fetchedTemp
                if (fetchedHumidity != null) liveHumidityOverride = fetchedHumidity
                if (fetchedWind != null) liveWindOverride = fetchedWind
                if (hourlySlots.isNotEmpty()) liveHourlyTemps = hourlySlots
                if (fetchedPrayers != null) livePrayerSchedule = fetchedPrayers
                isRefreshingWeather = false
            }
        }
    }

    // Automatically detect user location & weather on launch
    LaunchedEffect(Unit) {
        autoDetectLocationAndWeather()
    }

    // Auto-clear customEmote after 2.6s so collapsed Pochi keeps cycling through lively expressions!
    LaunchedEffect(customEmote) {
        if (customEmote != null) {
            delay(2600)
            customEmote = null
            if (botState == BotState.DONE) {
                botState = BotState.IDLE
            }
        }
    }

    // Automatically shrink into the ultra-clean "Thin Rounded Eye Box" in the center after 4.5s of idle
    LaunchedEffect(islandMode, interactionTick, isFocusRunning, mediaPlayback.isPlaying, activeDownload) {
        if (islandMode == IslandMode.COLLAPSED && !isFocusRunning && !mediaPlayback.isPlaying && activeDownload == null) {
            delay(4500)
            isSlimEyeBox = true
        } else {
            isSlimEyeBox = false
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

    // Auto-expand on approval or screenshot capture
    LaunchedEffect(activeApproval) {
        if (activeApproval != null) {
            botState = BotState.APPROVAL
            customEmote = PochiEmote.SURPRISED
            setMode(IslandMode.EXPANDED)
        }
    }

    LaunchedEffect(latestScreenshot) {
        if (latestScreenshot != null && islandMode == IslandMode.COLLAPSED) {
            customEmote = PochiEmote.EXCITED
            setMode(IslandMode.EXPANDED)
        }
    }

    // Autonomous Lively Pochi Mood & Gaze Cycle (especially rich & dynamic when collapsed!)
    val collapsedExpressivePool = remember {
        listOf(
            PochiEmote.NORMAL,
            PochiEmote.SMILING,
            PochiEmote.HAPPY,
            PochiEmote.WINK_LEFT,
            PochiEmote.HMM,
            PochiEmote.CUTE,
            PochiEmote.EXCITED,
            PochiEmote.CALM,
            PochiEmote.LOVE,
            PochiEmote.FOCUS,
            PochiEmote.SURPRISED,
            PochiEmote.BLUSH,
            PochiEmote.LAUGHING
        )
    }
    LaunchedEffect(botState, islandMode, isHidden, peekMode, selectedCity, waterGlasses, remainingTodayCount) {
        var stepIdx = 0
        while (true) {
            val waitTime = if (islandMode == IslandMode.COLLAPSED) (2100..3400).random().toLong() else (3800..5800).random().toLong()
            delay(waitTime)
            if (botState == BotState.IDLE) {
                stepIdx++
                val idleDuration = System.currentTimeMillis() - lastActiveTime
                idleEmote = when {
                    islandMode == IslandMode.COLLAPSED && stepIdx % 4 != 0 -> collapsedExpressivePool.random()
                    stepIdx % 4 == 0 -> smartBanner.emote
                    else -> PochiMood.nextAutonomousMood(
                        idleMillis = idleDuration,
                        isPeeking = isHidden && peekMode
                    )
                }
                // Smooth autonomous eye gaze shifts (left, right, up, down, center) so Pochi looks around organically
                eyeGazeOffset = Offset(
                    x = listOf(-0.62f, -0.35f, -0.15f, 0f, 0f, 0.15f, 0.35f, 0.62f).random(),
                    y = listOf(-0.28f, -0.12f, 0f, 0.12f, 0.25f).random()
                )
            }
        }
    }

    // Auto-collapse after 25s of inactivity when no text is being typed or voice listening
    LaunchedEffect(
        islandMode,
        interactionTick,
        activeApproval,
        newTaskInput,
        commandBarInput,
        expenseInput,
        chatInput,
        isListeningVoice,
        deckSearchQuery
    ) {
        if (islandMode != IslandMode.COLLAPSED &&
            activeApproval == null &&
            newTaskInput.isBlank() &&
            commandBarInput.isBlank() &&
            expenseInput.isBlank() &&
            chatInput.isBlank() &&
            deckSearchQuery.isBlank() &&
            !isListeningVoice
        ) {
            delay(25_000)
            setMode(IslandMode.COLLAPSED)
        }
    }

    fun appendChatMessage(isUser: Boolean, text: String, actionBadge: String? = null) {
        val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val msg = PochiChatMessage(
            id = "msg_${System.currentTimeMillis()}_${(100..999).random()}",
            isUser = isUser,
            text = text,
            actionBadge = actionBadge,
            timeLabel = stamp
        )
        chatMessages = (chatMessages + msg).takeLast(30)
    }

    // Smart Local Conversational Responder when no external API key is configured
    fun buildLocalSmartReply(prompt: String): Pair<String, PochiEmote> {
        val lower = prompt.lowercase(Locale.getDefault())
        val nextPrayer = PrayerScheduleHelper.getNextPrayer(selectedCity)
        val weather = WeatherHelper.getSnapshot(selectedCity, liveTempOverride)
        val spentToday = expenses.sumOf { it.amountRp }

        return when {
            lower.contains("sholat") || lower.contains("solat") || lower.contains("adzan") || lower.contains("prayer") -> {
                val msg = if (isId) {
                    "Jadwal sholat berikutnya di $selectedCity adalah ${nextPrayer.prayer.nameId} pukul ${nextPrayer.prayer.formatted} (${nextPrayer.remainingTextId})."
                } else {
                    "Next prayer in $selectedCity is ${nextPrayer.prayer.nameEn} at ${nextPrayer.prayer.formatted} (${nextPrayer.remainingTextEn})."
                }
                msg to PochiEmote.CALM
            }
            lower.contains("cuaca") || lower.contains("hujan") || lower.contains("panas") || lower.contains("weather") -> {
                val msg = if (isId) {
                    "Cuaca $selectedCity saat ini ${weather.tempC}°C (${weather.conditionId}). ${weather.adviceId}"
                } else {
                    "Weather in $selectedCity is ${weather.tempC}°C (${weather.conditionEn}). ${weather.adviceEn}"
                }
                msg to PochiEmote.SMILING
            }
            lower.contains("uang") || lower.contains("pengeluaran") || lower.contains("dompet") || lower.contains("budget") -> {
                val msg = if (isId) {
                    "Total pengeluaranmu hari ini ${NaturalExpenseParser.formatRupiah(spentToday)} dari batas harian ${NaturalExpenseParser.formatRupiah(dailyBudgetRp)}."
                } else {
                    "Your spending today is ${NaturalExpenseParser.formatRupiah(spentToday)} out of ${NaturalExpenseParser.formatRupiah(dailyBudgetRp)}."
                }
                msg to PochiEmote.HAPPY
            }
            lower.contains("halo") || lower.contains("hai") || lower.contains("pagi") || lower.contains("siang") || lower.contains("malam") || lower.contains("hello") -> {
                val msg = if (isId) {
                    "Halo! ${smartBanner.title}. Mau Pochi bantu buka aplikasi, telepon seseorang, kirim SMS/WA, atau atur jadwal?"
                } else {
                    "Hello! ${smartBanner.title}. Want me to open an app, call someone, send SMS/WA, or plan your day?"
                }
                msg to PochiEmote.HAPPY
            }
            lower.contains("siapa kamu") || lower.contains("pochi") -> {
                val msg = if (isId) {
                    "Aku Pochi, asisten AI Dynamic Island kamu! Aku bisa dihubungkan ke semua aplikasi di HP kamu, menelpon, kirim SMS/WA, catat uang, dan jaga produktivitasmu."
                } else {
                    "I'm Pochi, your Dynamic Island AI assistant! I can open any app on your phone, place calls, send SMS/WA, track money, and boost your productivity."
                }
                msg to PochiEmote.LOVE
            }
            else -> {
                // Check if user mentioned an installed app name casually
                val matchedApp = installedApps.firstOrNull { app ->
                    lower.contains(app.label.lowercase(Locale.getDefault())) && app.label.length >= 3
                }
                if (matchedApp != null && (lower.contains("buka") || lower.contains("main") || lower.contains("open"))) {
                    PochiAgent.execute(context, AgentAction("OPEN_APP", matchedApp.packageName))
                    val msg = if (isId) "Siap! Membuka aplikasi ${matchedApp.label} untukmu." else "Opening ${matchedApp.label} for you!"
                    msg to PochiEmote.EXCITED
                } else {
                    val msg = if (isId) {
                        "Siap! Kamu bisa bilang \"buka [nama aplikasi]\", \"telepon [nama/nomor]\", \"kirim sms ke [nama]: [pesan]\", \"wa ke [nama] [pesan]\", atau isi API Key di pengaturan untuk obrolan AI penuh."
                    } else {
                        "Got it! Say \"open [app]\", \"call [name/number]\", \"sms [name]: [text]\", \"wa [name] [text]\", or add an API key in settings for full AI chat."
                    }
                    msg to PochiEmote.SMILING
                }
            }
        }
    }

    // Unified AI Assistant Command & Chat Execution (Phone Call, SMS, WA, Open Any App, Focus, Money Tracker, Tasks, or AI Chat)
    fun executeUnifiedCommand(raw: String, fromChatOrVoice: Boolean = false) {
        val text = raw.trim()
        if (text.isEmpty()) return
        bump()

        if (fromChatOrVoice) {
            appendChatMessage(isUser = true, text = text)
        }

        // 1. Check PochiAgent local phone actions FIRST (Buka Aplikasi, Telepon, SMS, WhatsApp, Share, Kamera, Senter, Timer, Alarm, Maps, Math, Notes)
        PochiAgent.tryHandleLocal(context, text)?.let { (localReply, action) ->
            val execResult = action?.let { PochiAgent.execute(context, it) }
            val finalMsg = if (execResult != null && !localReply.contains(execResult, ignoreCase = true)) {
                "$localReply ($execResult)"
            } else {
                localReply
            }
            aiQuickReply = finalMsg
            appendChatMessage(
                isUser = false,
                text = finalMsg,
                actionBadge = action?.type
            )
            customEmote = PochiEmote.HAPPY
            botState = BotState.DONE
            if (action?.type in listOf("OPEN_APP", "CALL", "SMS", "WHATSAPP", "MAPS", "YOUTUBE", "SEARCH_WEB", "CAMERA", "SHARE")) {
                setMode(IslandMode.COLLAPSED)
            }
            return
        }

        // 2. Focus Timer shortcut: e.g. "25m belajar", "fokus 30m kerja"
        val focusRegex = Regex("""^(?:focus\s+|fokus\s+|timer\s+)(\d{1,3})\s*(?:m|min|menit|mnt)\s*(.*)$|^(\d{1,3})\s*(?:m|min|menit|mnt)\s+(.+)$""", RegexOption.IGNORE_CASE)
        focusRegex.matchEntire(text)?.let { match ->
            val minsStr = match.groupValues[1].ifBlank { match.groupValues[3] }
            val taskTitle = match.groupValues[2].ifBlank { match.groupValues[4] }.trim()
            val mins = minsStr.toIntOrNull()?.coerceIn(1, 180) ?: 25
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
            val msg = if (isId) "Timer fokus $mins menit dimulai!" else "Focus timer for $mins mins started!"
            appendChatMessage(isUser = false, text = msg, actionBadge = "FOCUS")
            customEmote = PochiEmote.FOCUS
            currentTab = PochiTab.FOCUS
            setMode(IslandMode.COLLAPSED)
            return
        }

        // 3. Expense / Money Tracker shortcut: e.g. "beli makan 25rb", "kopi 18k", "bensin 35000"
        val looksLikeExpense = Regex("""\b(\d+)\s*(rb|ribu|k|jt|juta)\b|(?:beli|bayar|jajan|kopi|makan|bensin|parkir)\s+.*\d{3,}""", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
        if (looksLikeExpense) {
            val parsedExpense = NaturalExpenseParser.parse(text)
            if (parsedExpense != null) {
                PochiPrefs.saveExpenses(context, listOf(parsedExpense) + expenses)
                val msg = if (isId) {
                    "Tercatat di Dompet: ${parsedExpense.title} sebesar ${NaturalExpenseParser.formatRupiah(parsedExpense.amountRp)}."
                } else {
                    "Logged expense: ${parsedExpense.title} (${NaturalExpenseParser.formatRupiah(parsedExpense.amountRp)})."
                }
                appendChatMessage(isUser = false, text = msg, actionBadge = "WALLET")
                customEmote = PochiEmote.HAPPY
                botState = BotState.DONE
                if (!fromChatOrVoice) {
                    currentTab = PochiTab.WALLET
                    setMode(IslandMode.EXPANDED)
                }
                return
            }
        }

        // 4. If sent from Chat/Voice tab OR starts with question/assistant keywords, handle as AI Chat + Action Agent!
        val lower = text.lowercase(Locale.getDefault())
        val isQuestionOrChat = fromChatOrVoice ||
            lower.startsWith("?") || lower.endsWith("?") ||
            lower.startsWith("tanya ") || lower.startsWith("pochi ") ||
            lower.startsWith("tips ") || lower.startsWith("cara ") ||
            lower.startsWith("halo") || lower.startsWith("hai")

        if (isQuestionOrChat) {
            val cleanPrompt = text.removePrefix("?").removePrefix("tanya ").removePrefix("pochi ").trim()
            isAiThinking = true
            botState = BotState.WORKING
            customEmote = PochiEmote.THINKING
            coroutineScope.launch {
                val provider = PochiPrefs.getProvider(context)
                val apiKey = PochiPrefs.getApiKey(context, provider)
                if (apiKey.isNotBlank()) {
                    val reply = runCatching {
                        AiClient.ask(
                            provider = provider,
                            apiKey = apiKey,
                            model = PochiPrefs.getModel(context, provider),
                            prompt = cleanPrompt
                        )
                    }.getOrNull()

                    if (reply != null) {
                        val (afterAction, action) = PochiAgent.extract(reply)
                        val (cleanReply, emote) = PochiAgent.extractEmote(afterAction, cleanPrompt)
                        if (action != null) {
                            PochiAgent.execute(context, action)
                        }
                        aiQuickReply = cleanReply
                        appendChatMessage(isUser = false, text = cleanReply, actionBadge = action?.type ?: "AI")
                        isAiThinking = false
                        botState = BotState.DONE
                        customEmote = emote
                        return@launch
                    }
                }

                // Fallback to smart local conversational assistant
                val (localReply, emote) = buildLocalSmartReply(cleanPrompt)
                aiQuickReply = localReply
                appendChatMessage(isUser = false, text = localReply, actionBadge = "POCHI")
                isAiThinking = false
                botState = BotState.DONE
                customEmote = emote
            }
            return
        }

        // 5. Default: Parse natural language tasks into Today planner
        val parsedTasks = NaturalTaskParser.parse(text, selectedDayOffset)
        if (parsedTasks.isNotEmpty()) {
            val updated = parsedTasks + tasks
            PochiPrefs.saveTasks(context, updated)
            val msg = if (isId) "Jadwal \"${parsedTasks.first().title}\" ditambahkan ke Hari Ini." else "Task \"${parsedTasks.first().title}\" added to Today."
            appendChatMessage(isUser = false, text = msg, actionBadge = "TASK")
            customEmote = PochiEmote.HAPPY
            botState = BotState.DONE
            currentTab = PochiTab.TODAY
            setMode(IslandMode.EXPANDED)
        }
    }

    // SpeechRecognizer (Input Suara Langsung ke Pochi - Pochi Hanya Mendengar Tanpa Suara TTS)
    var speechRecognizerRef by remember { mutableStateOf<SpeechRecognizer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                speechRecognizerRef?.destroy()
                speechRecognizerRef = null
            }
        }
    }

    fun toggleVoiceListening() {
        bump()
        if (isListeningVoice) {
            runCatching { speechRecognizerRef?.stopListening() }
            isListeningVoice = false
            customEmote = PochiEmote.SMILING
            return
        }

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPerm) {
            Toast.makeText(
                context,
                if (isId) "Izinkan Mikrofon di aplikasi Pochi untuk fitur Suara" else "Grant Microphone permission in Pochi app for Voice",
                Toast.LENGTH_LONG
            ).show()
            runCatching {
                val intent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            return
        }

        runCatching {
            if (speechRecognizerRef == null) {
                speechRecognizerRef = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
            }
            val recognizer = speechRecognizerRef ?: return
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningVoice = true
                    voicePartialText = if (isId) "Mendengarkan suaramu..." else "Listening..."
                    customEmote = PochiEmote.HMM
                }

                override fun onBeginningOfSpeech() {
                    customEmote = PochiEmote.HMM
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListeningVoice = false
                }

                override fun onError(error: Int) {
                    isListeningVoice = false
                    voicePartialText = ""
                    customEmote = PochiEmote.CONFUSED
                }

                override fun onResults(results: Bundle?) {
                    isListeningVoice = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    voicePartialText = ""
                    if (spoken.isNotBlank()) {
                        executeUnifiedCommand(spoken, fromChatOrVoice = true)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        voicePartialText = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isId) "id-ID" else "en-US")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            isListeningVoice = true
            voicePartialText = if (isId) "Silakan bicara..." else "Speak now..."
            customEmote = PochiEmote.HMM
            recognizer.startListening(speechIntent)
        }.onFailure {
            isListeningVoice = false
            Toast.makeText(
                context,
                if (isId) "Layanan pengenal suara tidak tersedia di perangkat ini" else "Speech recognition unavailable",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 120fps Rectangular Sizing Rules (Optimized for Nubia Neo 3 GT 5G / Android 16 & all devices)
    val collapsedWidth = when {
        isFocusRunning || mediaPlayback.isPlaying || activeDownload != null -> 196.dp
        isSlimEyeBox -> 86.dp // Ultra-clean thin rounded rectangle in the center with expressive white eyes!
        else -> 168.dp
    }
    val collapsedHeight = if (isSlimEyeBox && !isFocusRunning && !mediaPlayback.isPlaying) 26.dp else 30.dp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val expandedWidth = if (isLandscape) {
        (configuration.screenWidthDp.dp - 32.dp).coerceIn(340.dp, 520.dp)
    } else {
        (configuration.screenWidthDp.dp - 16.dp).coerceIn(328.dp, 396.dp)
    }
    val maxExpandedHeight = (configuration.screenHeightDp.dp - 40.dp).coerceAtLeast(180.dp)
    var measuredExpandedHeightPx by remember { mutableIntStateOf(0) }
    val expandedHeight = with(density) {
        when (islandMode) {
            IslandMode.COMMAND_BAR -> if (aiQuickReply != null || isAiThinking) 280.dp else 232.dp
            IslandMode.EXPANDED -> if (measuredExpandedHeightPx > 0) measuredExpandedHeightPx.toDp() else 420.dp
            IslandMode.COLLAPSED -> collapsedHeight
        }
    }.coerceAtMost(maxExpandedHeight)

    // Mandatory 120fps spring specification
    val transition = updateTransition(targetState = islandMode, label = "island120fpsTransition")

    // Subtle organic breathing & floating animation when collapsed so the island never looks stiff
    val collapsedAmbientTransition = rememberInfiniteTransition(label = "collapsedAmbient")
    val collapsedBreathScale by collapsedAmbientTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "collapsedBreathScale"
    )
    val collapsedFloatPx by collapsedAmbientTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "collapsedFloatPx"
    )

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
            IslandMode.COMMAND_BAR -> if (aiQuickReply != null || isAiThinking) 280.dp else 232.dp
            IslandMode.EXPANDED -> expandedHeight
        }
    }

    // Persegi Panjang dengan Pinggir Rounded (8.dp - 14.dp, TIDAK terlalu circle/kapsul!)
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
            IslandMode.COLLAPSED -> 9.dp
            IslandMode.COMMAND_BAR -> 14.dp
            IslandMode.EXPANDED -> 14.dp
        }
    }

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
            mode == IslandMode.COLLAPSED && isFlushStatusBar && offsetY <= 2 -> 3.dp
            mode == IslandMode.COLLAPSED -> 9.dp
            isFlushStatusBar && offsetY <= 2 -> 8.dp
            else -> 14.dp
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

    val activeEmote = customEmote ?: idleEmote ?: smartBanner.emote

    // Edge-hidden peek mode (Persegi panjang rounded, bukan lingkaran)
    if (isHidden) {
        val restShift = with(density) { (if (peekSide == "left") (-12).dp else 12.dp).toPx() }
        val peekShift = with(density) { (if (peekSide == "left") (-4).dp else 4.dp).toPx() }
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
                },
                contentAlignment = Alignment.Center
            ) {
                PochiMascot(
                    state = botState,
                    emoteOverride = activeEmote,
                    size = 38.dp,
                    showBlackBacking = true
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
        // Matte-black (#0A0A0A) 120fps Hardware-Accelerated Rounded-Rectangle Container
        Box(
            modifier = Modifier
                .width(islandWidth)
                .height(islandHeight)
                .graphicsLayer {
                    clip = true
                    shape = islandShape
                    if (islandMode == IslandMode.COLLAPSED) {
                        scaleX = collapsedBreathScale
                        scaleY = 2f - collapsedBreathScale
                        translationY = if (isFlushStatusBar && offsetY <= 2) 0f else collapsedFloatPx
                    }
                }
                .background(islandBlack, islandShape)
                .border(
                    width = 1.dp,
                    color = if (activeApproval != null) accentAmber.copy(alpha = 0.7f) else borderDark,
                    shape = islandShape
                )
        ) {
            // ================= 1. COLLAPSED KOTAK ROUNDED TIPIS DI TENGAH =================
            if (collapsedContentAlpha > 0.01f) {
                val showOnlyEyes = isSlimEyeBox && !isFocusRunning && !mediaPlayback.isPlaying && activeDownload == null
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = collapsedContentAlpha }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var accumulatedX = 0f
                                var accumulatedY = 0f
                                var isDragging = false
                                val touchSlop = viewConfiguration.touchSlop

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUp()) {
                                        if (!isDragging) {
                                            customEmote = PochiEmote.HAPPY
                                            setMode(IslandMode.EXPANDED)
                                        } else {
                                            onDragEnd()
                                        }
                                        break
                                    }
                                    if (change.isConsumed) break

                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    accumulatedX += dx
                                    accumulatedY += dy

                                    if (!isDragging) {
                                        if (kotlin.math.hypot(accumulatedX, accumulatedY) > touchSlop) {
                                            isDragging = true
                                            change.consume()
                                            onDragBubble(accumulatedX, accumulatedY)
                                        }
                                    } else {
                                        change.consume()
                                        onDragBubble(dx, dy)
                                    }
                                }
                            }
                        }
                        .padding(horizontal = if (showOnlyEyes) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (showOnlyEyes) Arrangement.Center else Arrangement.SpaceBetween
                ) {
                    // Glowing white eyes inside the thin black rounded rectangle
                    PochiMascot(
                        state = botState,
                        emoteOverride = activeEmote,
                        size = if (showOnlyEyes) 24.dp else 25.dp,
                        eyeTrackerOffset = eyeGazeOffset,
                        showBlackBacking = false
                    )

                    if (!showOnlyEyes) {
                        Spacer(modifier = Modifier.width(6.dp))

                        when {
                            isFocusRunning -> {
                                val mins = focusRemainingSeconds / 60
                                val secs = focusRemainingSeconds % 60
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(accentAmber)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%02d:%02d", mins, secs),
                                        color = textPrimary,
                                        fontSize = 11.sp,
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
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            mediaPlayback.isPlaying -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = accentIndigo,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = mediaPlayback.title.take(14),
                                        color = textPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            else -> {
                                // Smart AI Assistant status in the thin rectangle (e.g. "Ingat Sarapan ☀️" or "Ashar 15:14")
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = smartBanner.shortPillText,
                                        color = textPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= 2. LONG-PRESS BAR PERINTAH AI ASISTEN =================
            if (islandMode == IslandMode.COMMAND_BAR) {
                val previewFocusMatch = remember(commandBarInput) {
                    Regex("""^(?:focus\s+|fokus\s+|timer\s+)?(\d{1,3})\s*(?:m|min|menit|mnt)\s*(.*)$""", RegexOption.IGNORE_CASE)
                        .matchEntire(commandBarInput.trim())
                }
                val previewMins = previewFocusMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 180) ?: 25
                val previewTitle = previewFocusMatch?.groupValues?.getOrNull(2)?.trim()?.ifBlank { null }
                    ?: commandBarInput.trim().ifBlank { if (isId) "Fokus Produktif" else "Deep Focus" }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = expandedContentAlpha }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top Input Row (Persegi panjang rounded 10.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(surfaceDark)
                            .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PochiMascot(
                            state = botState,
                            emoteOverride = if (isAiThinking) PochiEmote.THINKING else PochiEmote.HMM,
                            size = 26.dp,
                            eyeTrackerOffset = eyeGazeOffset,
                            showBlackBacking = false
                        )
                        BasicTextField(
                            value = commandBarInput,
                            onValueChange = {
                                commandBarInput = it
                                bump()
                                eyeGazeOffset = Offset(
                                    x = ((it.length % 18) / 18f - 0.5f),
                                    y = 0.2f
                                )
                            },
                            textStyle = TextStyle(
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(accentAmber),
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
                                        text = if (isId) "Ketik \"buka wa\", \"telepon Ibu\", \"sms Budi\", atau tanya Pochi..." else "Type \"open wa\", \"call Mom\", \"sms Budi\", or ask Pochi...",
                                        color = textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                inner()
                            }
                        )
                        // Voice Mic Button inside Command Bar
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isListeningVoice) Color(0xFFEF4444) else subtleDark)
                                .border(
                                    1.dp,
                                    if (isListeningVoice) Color(0xFFEF4444) else borderDark,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { toggleVoiceListening() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isListeningVoice) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                                contentDescription = "Voice Input",
                                tint = if (isListeningVoice) Color.White else accentAmber,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(subtleDark)
                                .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                                .clickable {
                                    aiQuickReply = null
                                    setMode(IslandMode.COLLAPSED)
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Esc",
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // AI Quick Reply Box (if user asked a question)
                    if (isAiThinking || aiQuickReply != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(subtleDark)
                                .border(1.dp, accentSky.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = accentSky,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isAiThinking) {
                                    if (isId) "Pochi sedang berpikir..." else "Pochi is thinking..."
                                } else {
                                    aiQuickReply.orEmpty()
                                },
                                color = textPrimary,
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Live Focus Card with Rectangular Rounded "Mulai" / "Start" Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(surfaceDark)
                            .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (isId) "Sesi Fokus Produktif" else "Focus Session",
                                color = accentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = String.format(Locale.US, "%02d:00", previewMins),
                                color = textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = previewTitle,
                                color = textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Persegi panjang rounded (bukan kapsul bulat)
                        Box(
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(accentAmber)
                                .clickable {
                                    val cmd = if (commandBarInput.isNotBlank()) {
                                        commandBarInput
                                    } else {
                                        "${previewMins}m $previewTitle"
                                    }
                                    commandBarInput = ""
                                    executeUnifiedCommand(cmd)
                                }
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isId) "Mulai" else "Start",
                                color = Color(0xFF0A0A0A),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    // Quick Human Assistant Suggestions
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        QuickCommandRowItem(
                            icon = Icons.Rounded.AccountBalanceWallet,
                            title = if (isId) "Catat pengeluaran — Sarapan & Teh 18rb" else "Log expense — Breakfast 18k",
                            trailing = "Rp 18.000",
                            subtleDark = subtleDark,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onClick = {
                                commandBarInput = ""
                                executeUnifiedCommand(if (isId) "Sarapan & Teh 18rb" else "Breakfast 18k")
                            }
                        )
                        QuickCommandRowItem(
                            icon = Icons.Rounded.WbSunny,
                            title = if (isId) "Cek Jadwal Sholat & Cuaca ($selectedCity)" else "Check Prayer & Weather ($selectedCity)",
                            trailing = PrayerScheduleHelper.getNextPrayer(selectedCity).prayer.formatted,
                            subtleDark = subtleDark,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            onClick = {
                                currentTab = PochiTab.LIFE
                                setMode(IslandMode.EXPANDED)
                            }
                        )
                    }
                }
            }

            // ================= 3. EXPANDED 5-MODULE DAILY AI ASSISTANT HUB =================
            if (islandMode == IslandMode.EXPANDED) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .requiredWidth(expandedWidth - 20.dp)
                        .wrapContentHeight(unbounded = true)
                        .onSizeChanged { size ->
                            val padded = size.height + with(density) { 22.dp.roundToPx() }
                            if (padded > 0 && padded != measuredExpandedHeightPx) {
                                measuredExpandedHeightPx = padded
                                onExpandedHeightChange(padded)
                            }
                        }
                        .graphicsLayer { alpha = expandedContentAlpha }
                        .padding(horizontal = 4.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Top Navigation Bar: Pochi Eyes + 5 Clean ICON-ONLY Rounded-Rectangle Tabs + Actions
                        // (Zero truncated "..." text on Nubia Neo 3 GT 5G or any phone!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PochiMascot(
                                state = botState,
                                emoteOverride = activeEmote,
                                size = 30.dp,
                                eyeTrackerOffset = eyeGazeOffset,
                                showBlackBacking = false,
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

                            // 5 Clean Icon-Only Rounded-Rectangle Tabs (Today, Weather/Prayer, Wallet, Focus, Deck)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(surfaceDark)
                                    .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PochiTab.entries.forEach { tab ->
                                    val selected = tab == currentTab
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (selected) Color.White else Color.Transparent)
                                            .clickable {
                                                bump()
                                                currentTab = tab
                                                customEmote = when (tab) {
                                                    PochiTab.TODAY -> PochiEmote.SMILING
                                                    PochiTab.CHAT -> PochiEmote.HMM
                                                    PochiTab.LIFE -> PochiEmote.CALM
                                                    PochiTab.WALLET -> PochiEmote.HAPPY
                                                    PochiTab.FOCUS -> PochiEmote.FOCUS
                                                    PochiTab.DECK -> PochiEmote.WINK_LEFT
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label(lang),
                                            tint = if (selected) Color(0xFF0A0A0A) else textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Quick Command Bar (🔍) - Rectangular Rounded 34dp
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(surfaceDark)
                                    .border(1.dp, borderDark, RoundedCornerShape(9.dp))
                                    .clickable { setMode(IslandMode.COMMAND_BAR) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Command Bar",
                                    tint = textPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Collapse to Thin Center Box (⌃) - Rectangular Rounded 34dp
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(surfaceDark)
                                    .border(1.dp, borderDark, RoundedCornerShape(9.dp))
                                    .clickable { setMode(IslandMode.COLLAPSED) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowUp,
                                    contentDescription = "Collapse",
                                    tint = textPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // Active Module Title & Language Badge Bar (Full readable title, zero "..." truncation!)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTab.label(lang),
                                    color = textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentTab.subtitle(lang),
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Slim Eye Box Quick Preview Toggle
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(surfaceDark)
                                        .border(1.dp, borderDark, RoundedCornerShape(7.dp))
                                        .clickable {
                                            isSlimEyeBox = true
                                            islandMode = IslandMode.COLLAPSED
                                            onExpandChange(false)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isId) "Mode Tipis" else "Slim Box",
                                        color = accentEmerald,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Language Switcher (ID / EN)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(surfaceDark)
                                        .border(1.dp, borderDark, RoundedCornerShape(7.dp))
                                        .clickable {
                                            bump()
                                            PochiPrefs.toggleLanguage(context)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = lang.shortBadge,
                                        color = accentSky,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
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

                        // ================= 5 HUMAN-CENTRIC DAILY MODULES =================
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(durationMillis = 150),
                            label = "moduleCrossfade"
                        ) { tab ->
                            when (tab) {
                                // ----------------- MODULE 1: TODAY & SMART AI CARE -----------------
                                PochiTab.TODAY -> {
                                    TodayModuleView(
                                        lang = lang,
                                        smartBanner = smartBanner,
                                        waterGlasses = waterGlasses,
                                        onAddWater = {
                                            bump()
                                            PochiPrefs.addWaterGlass(context)
                                            customEmote = PochiEmote.HAPPY
                                        },
                                        onResetWater = {
                                            bump()
                                            PochiPrefs.resetWaterGlasses(context)
                                        },
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
                                        onQuickAddPresetTask = { presetText ->
                                            bump()
                                            val parsed = NaturalTaskParser.parse(presetText, selectedDayOffset)
                                            if (parsed.isNotEmpty()) {
                                                PochiPrefs.saveTasks(context, parsed + tasks)
                                                customEmote = PochiEmote.HAPPY
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
                                        accentAmber = accentAmber,
                                        accentSky = accentSky
                                    )
                                }

                                // ----------------- MODULE 1B: CHAT, SUARA (MENDENGAR) & KONEKSI HP -----------------
                                PochiTab.CHAT -> {
                                    ChatAndVoiceModuleView(
                                        lang = lang,
                                        messages = chatMessages,
                                        chatInput = chatInput,
                                        onChatInputChange = {
                                            chatInput = it
                                            bump()
                                        },
                                        onSendChat = {
                                            val q = chatInput.trim()
                                            if (q.isNotEmpty()) {
                                                chatInput = ""
                                                executeUnifiedCommand(q, fromChatOrVoice = true)
                                            }
                                        },
                                        isListeningVoice = isListeningVoice,
                                        voicePartialText = voicePartialText,
                                        onToggleVoiceMic = { toggleVoiceListening() },
                                        isAiThinking = isAiThinking,
                                        quickComposerMode = quickComposerMode,
                                        onSelectQuickComposer = { mode ->
                                            bump()
                                            quickComposerMode = if (quickComposerMode == mode) QuickPhoneComposerMode.NONE else mode
                                            quickTargetInput = ""
                                            quickMessageInput = ""
                                        },
                                        quickTargetInput = quickTargetInput,
                                        onQuickTargetChange = {
                                            quickTargetInput = it
                                            bump()
                                        },
                                        quickMessageInput = quickMessageInput,
                                        onQuickMessageChange = {
                                            quickMessageInput = it
                                            bump()
                                        },
                                        installedApps = installedApps,
                                        onExecuteQuickComposer = {
                                            bump()
                                            when (quickComposerMode) {
                                                QuickPhoneComposerMode.CALL -> {
                                                    val target = quickTargetInput.trim()
                                                    if (target.isNotEmpty()) {
                                                        executeUnifiedCommand("telepon $target", fromChatOrVoice = true)
                                                        quickComposerMode = QuickPhoneComposerMode.NONE
                                                    } else {
                                                        PochiAgent.execute(context, AgentAction("CALL", ""))
                                                        setMode(IslandMode.COLLAPSED)
                                                    }
                                                }
                                                QuickPhoneComposerMode.SMS -> {
                                                    val target = quickTargetInput.trim()
                                                    val body = quickMessageInput.trim()
                                                    if (target.isNotEmpty()) {
                                                        executeUnifiedCommand("kirim sms ke $target: $body", fromChatOrVoice = true)
                                                        quickComposerMode = QuickPhoneComposerMode.NONE
                                                    } else {
                                                        PochiAgent.execute(context, AgentAction("SMS", "|$body"))
                                                        setMode(IslandMode.COLLAPSED)
                                                    }
                                                }
                                                QuickPhoneComposerMode.WHATSAPP -> {
                                                    val target = quickTargetInput.trim()
                                                    val body = quickMessageInput.trim()
                                                    if (target.isNotEmpty() || body.isNotEmpty()) {
                                                        executeUnifiedCommand("kirim wa ke $target: $body", fromChatOrVoice = true)
                                                        quickComposerMode = QuickPhoneComposerMode.NONE
                                                    } else {
                                                        PochiAgent.execute(context, AgentAction("OPEN_APP", "com.whatsapp"))
                                                        setMode(IslandMode.COLLAPSED)
                                                    }
                                                }
                                                QuickPhoneComposerMode.OPEN_APP -> {
                                                    val target = quickTargetInput.trim()
                                                    if (target.isNotEmpty()) {
                                                        executeUnifiedCommand("buka $target", fromChatOrVoice = true)
                                                        quickComposerMode = QuickPhoneComposerMode.NONE
                                                    }
                                                }
                                                QuickPhoneComposerMode.NONE -> {}
                                            }
                                        },
                                        onLaunchAppDirect = { app ->
                                            bump()
                                            appendChatMessage(isUser = true, text = if (isId) "Buka ${app.label}" else "Open ${app.label}")
                                            PochiAgent.execute(context, AgentAction("OPEN_APP", app.packageName))
                                            appendChatMessage(
                                                isUser = false,
                                                text = if (isId) "Membuka aplikasi ${app.label}..." else "Opening ${app.label}...",
                                                actionBadge = "OPEN_APP"
                                            )
                                            quickComposerMode = QuickPhoneComposerMode.NONE
                                            setMode(IslandMode.COLLAPSED)
                                        },
                                        onShareAnything = {
                                            bump()
                                            val textToShare = chatInput.ifBlank {
                                                chatMessages.lastOrNull { !it.isUser }?.text.orEmpty()
                                            }
                                            if (textToShare.isNotBlank()) {
                                                PochiAgent.execute(context, AgentAction("SHARE", textToShare))
                                                setMode(IslandMode.COLLAPSED)
                                            }
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

                                // ----------------- MODULE 2: CUACA OTOMATIS & JADWAL SHOLAT + CHART -----------------
                                PochiTab.LIFE -> {
                                    WeatherAndPrayerModuleView(
                                        lang = lang,
                                        selectedCity = selectedCity,
                                        locationSourceLabel = locationSourceLabel,
                                        liveTempOverride = liveTempOverride,
                                        liveHumidityOverride = liveHumidityOverride,
                                        liveWindOverride = liveWindOverride,
                                        liveHourlyTemps = liveHourlyTemps,
                                        livePrayerSchedule = livePrayerSchedule,
                                        isRefreshingWeather = isRefreshingWeather,
                                        onRefreshWeather = {
                                            bump()
                                            autoDetectLocationAndWeather()
                                        },
                                        onSchedulePrayerTask = { prayer ->
                                            bump()
                                            val title = if (isId) "Sholat ${prayer.nameId} tepat waktu" else "${prayer.nameEn} Prayer"
                                            val newTask = TodayTask(
                                                id = "prayer_${System.currentTimeMillis()}",
                                                title = title,
                                                timeLabel = prayer.formatted,
                                                hour24 = prayer.hour,
                                                minute = prayer.minute,
                                                dayIndex = 0,
                                                isDone = false
                                            )
                                            PochiPrefs.saveTasks(context, listOf(newTask) + tasks)
                                            customEmote = PochiEmote.HAPPY
                                            Toast.makeText(
                                                context,
                                                if (isId) "Pengingat ${prayer.nameId} ditambahkan ke Hari Ini" else "${prayer.nameEn} added to Today",
                                                Toast.LENGTH_SHORT
                                            ).show()
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

                                // ----------------- MODULE 3: DOMPET, MONEY TRACKER & PEMBAYARAN -----------------
                                PochiTab.WALLET -> {
                                    WalletModuleView(
                                        lang = lang,
                                        expenses = expenses,
                                        dailyBudgetRp = dailyBudgetRp,
                                        expenseInput = expenseInput,
                                        onExpenseInputChange = {
                                            expenseInput = it
                                            bump()
                                        },
                                        onSubmitExpense = {
                                            val parsed = NaturalExpenseParser.parse(expenseInput)
                                            if (parsed != null) {
                                                PochiPrefs.saveExpenses(context, listOf(parsed) + expenses)
                                                expenseInput = ""
                                                customEmote = PochiEmote.HAPPY
                                            }
                                        },
                                        onQuickAddExpense = { title, amount, category ->
                                            bump()
                                            val now = Calendar.getInstance()
                                            val timeStr = String.format(
                                                Locale.US,
                                                "%02d:%02d",
                                                now.get(Calendar.HOUR_OF_DAY),
                                                now.get(Calendar.MINUTE)
                                            )
                                            val entry = ExpenseEntry(
                                                id = System.currentTimeMillis().toString(),
                                                title = title,
                                                amountRp = amount,
                                                category = category,
                                                timeLabel = timeStr
                                            )
                                            PochiPrefs.saveExpenses(context, listOf(entry) + expenses)
                                            customEmote = PochiEmote.HAPPY
                                        },
                                        onDeleteExpense = { entry ->
                                            bump()
                                            PochiPrefs.saveExpenses(context, expenses.filterNot { it.id == entry.id })
                                        },
                                        isEditingBudget = isEditingBudget,
                                        budgetDraft = budgetDraft,
                                        onBudgetDraftChange = { budgetDraft = it },
                                        onToggleEditBudget = {
                                            bump()
                                            if (isEditingBudget) {
                                                val parsedRb = budgetDraft.trim().toLongOrNull()
                                                if (parsedRb != null && parsedRb > 0) {
                                                    PochiPrefs.setDailyBudget(context, parsedRb * 1000L)
                                                }
                                            }
                                            isEditingBudget = !isEditingBudget
                                        },
                                        onOpenPaymentApp = { appQuery, pkgCandidates ->
                                            bump()
                                            val pm = context.packageManager
                                            val installedPkg = pkgCandidates.firstOrNull { pkg ->
                                                pm.getLaunchIntentForPackage(pkg) != null
                                            } ?: installedApps.firstOrNull {
                                                it.label.contains(appQuery, ignoreCase = true)
                                            }?.packageName

                                            if (installedPkg != null) {
                                                PochiAgent.execute(context, AgentAction("OPEN_APP", installedPkg))
                                                setMode(IslandMode.COLLAPSED)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    if (isId) "Aplikasi $appQuery belum terinstal di HP ini" else "$appQuery is not installed",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
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

                                // ----------------- MODULE 4: FOCUS (Pomodoro Timer) -----------------
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

                                // ----------------- MODULE 5: DECK (Pintasan & Semua Aplikasi) -----------------
                                PochiTab.DECK -> {
                                    DeckModuleView(
                                        lang = lang,
                                        installedApps = installedApps,
                                        pinnedPackages = pinnedPackages,
                                        searchQuery = deckSearchQuery,
                                        onSearchQueryChange = {
                                            deckSearchQuery = it
                                            bump()
                                        },
                                        showAllApps = showAllAppsInDeck,
                                        onToggleShowAllApps = {
                                            bump()
                                            showAllAppsInDeck = !showAllAppsInDeck
                                        },
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
                                        onActionCallDialer = {
                                            bump()
                                            PochiAgent.execute(context, AgentAction("CALL", ""))
                                            setMode(IslandMode.COLLAPSED)
                                        },
                                        onActionSendSms = {
                                            bump()
                                            PochiAgent.execute(context, AgentAction("SMS", "|"))
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
                                        onActionAlarm = {
                                            bump()
                                            runCatching {
                                                val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            }
                                            setMode(IslandMode.COLLAPSED)
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
// MODULE 1: TODAY & SMART AI HUMAN CARE (Sarapan, Hidrasi, Kalender & Tugas)
// ============================================================================
@Composable
private fun TodayModuleView(
    lang: PochiLang,
    smartBanner: SmartAssistantBanner,
    waterGlasses: Int,
    onAddWater: () -> Unit,
    onResetWater: () -> Unit,
    tasks: List<TodayTask>,
    selectedDayOffset: Int,
    taskFilter: TaskFilter,
    onSelectDay: (Int) -> Unit,
    onSelectFilter: (TaskFilter) -> Unit,
    newTaskInput: String,
    onNewTaskInputChange: (String) -> Unit,
    onSubmitTasks: () -> Unit,
    onQuickAddPresetTask: (String) -> Unit,
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
    accentAmber: Color,
    accentSky: Color
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
        // Proactive AI Human Care Card (Pengingat Sarapan, Makan Siang, Sholat, & Hidrasi Air Putih)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = smartBanner.title,
                    color = accentAmber,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = smartBanner.subtitle,
                    color = textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 1-Tap Hydration Tracker ("💧 3/8 +")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(subtleDark)
                    .border(1.dp, accentSky.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .clickable { onAddWater() }
                    .padding(horizontal = 9.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "💧 $waterGlasses/8",
                    color = accentSky,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "+",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Weekly Strip Calendar (Persegi Panjang Rounded) + Progress Ring
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectDay(offset) }
                            .padding(horizontal = 3.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = dayLabel.take(1),
                            color = if (isSelected) Color.White else textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        // Kotak persegi panjang rounded (bukan lingkaran)
                        Box(
                            modifier = Modifier
                                .size(width = 26.dp, height = 24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        isSelected -> Color.White
                                        isToday -> subtleDark
                                        else -> Color.Transparent
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dateNum.toString(),
                                color = when {
                                    isSelected -> Color(0xFF0A0A0A)
                                    isToday -> Color.White
                                    else -> textPrimary
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 8.dp, height = 3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        hasOverdue -> accentAmber
                                        hasPending -> Color.White.copy(alpha = 0.7f)
                                        else -> Color.Transparent
                                    }
                                )
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isId) "$remainingCount sisa" else "$remainingCount left",
                        color = textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = when {
                            overdueCount > 0 -> if (isId) "$overdueCount telat" else "$overdueCount overdue"
                            else -> if (isId) "$doneCount beres" else "$doneCount done"
                        },
                        color = if (overdueCount > 0) accentAmber else accentEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                    Canvas(modifier = Modifier.size(28.dp)) {
                        val sw = 3.dp.toPx()
                        drawArc(
                            color = borderDark,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = sw, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = if (overdueCount > 0) accentAmber else Color.White,
                            startAngle = -90f,
                            sweepAngle = 360f * progressFraction.coerceAtLeast(0.18f),
                            useCenter = false,
                            style = Stroke(width = sw, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }

        // Quick 1-Tap Human Routine Chips (Sarapan, Makan Siang, Olahraga, Belanja) + Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                TaskFilter.ALL to (if (isId) "Semua ($totalCount)" else "All ($totalCount)"),
                TaskFilter.ACTIVE to (if (isId) "Aktif ($remainingCount)" else "Active ($remainingCount)"),
                TaskFilter.DONE to (if (isId) "Selesai ($doneCount)" else "Done ($doneCount)")
            ).forEach { (filter, label) ->
                val active = taskFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Color.White else surfaceDark)
                        .border(1.dp, if (active) Color.White else borderDark, RoundedCornerShape(8.dp))
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        color = if (active) Color(0xFF0A0A0A) else textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick Auto-Routine Buttons
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(subtleDark)
                    .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                    .clickable {
                        onQuickAddPresetTask(if (isId) "Sarapan sehat jam 7 pagi" else "Healthy breakfast at 7am")
                    }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isId) "+ Sarapan" else "+ Breakfast",
                    color = accentAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (doneCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceDark)
                        .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                        .clickable { onClearCompleted() }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isId) "Hapus Selesai" else "Clear Done",
                        color = accentSky,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Interactive Checklist (Kotak Persegi Panjang Rounded)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp, max = 148.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)
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
                            "Belum ada tugas. Ketik di bawah (mis: \"sarapan jam 7, lalu rapat jam 2\")"
                        } else {
                            "No tasks yet. Type below (e.g. \"breakfast 7am, then 2pm meeting\")"
                        },
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                }
            } else {
                filteredTasks.forEach { task ->
                    val isLate = task.isCurrentlyOverdue()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(subtleDark.copy(alpha = 0.7f))
                            .clickable { onToggleTask(task) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        // Kotak checkbox rounded (bukan lingkaran)
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (task.isDone) accentEmerald else Color.Transparent)
                                .border(
                                    width = 1.5.dp,
                                    color = when {
                                        task.isDone -> accentEmerald
                                        isLate -> accentAmber
                                        else -> textSecondary
                                    },
                                    shape = RoundedCornerShape(5.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.isDone) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF0A0A0A),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        Text(
                            text = task.title,
                            color = if (task.isDone) textSecondary else textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (isLate && !task.isDone) {
                            Text(
                                text = if (isId) "Telat" else "Overdue",
                                color = accentAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (task.timeLabel.isNotBlank()) {
                            Text(
                                text = task.timeLabel,
                                color = textSecondary,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (!task.isDone) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(surfaceDark)
                                    .clickable { onStartFocusForTask(task) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = "Focus",
                                    tint = accentEmerald,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(surfaceDark)
                                .clickable { onDeleteTask(task) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Delete",
                                tint = textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Weekly Productivity & Hydration Mini-Chart (Visual Bar Chart & Table)
        TodayProductivityChartCard(
            isId = isId,
            weekDays = weekDays,
            tasks = tasks,
            selectedDayOffset = selectedDayOffset,
            waterGlasses = waterGlasses,
            surfaceDark = surfaceDark,
            subtleDark = subtleDark,
            borderDark = borderDark,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentEmerald = accentEmerald,
            accentSky = accentSky,
            accentAmber = accentAmber
        )

        // Bottom Rectangular Rounded Task Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(18.dp)
            )
            BasicTextField(
                value = newTaskInput,
                onValueChange = onNewTaskInputChange,
                textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(Color.White),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmitTasks() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (newTaskInput.isEmpty()) {
                        Text(
                            text = if (isId) {
                                "Tambah jadwal: \"sarapan jam 7, lalu rapat jam 2\"..."
                            } else {
                                "Add schedule: \"breakfast 7am, then 2pm meeting\"..."
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
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { onSubmitTasks() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isId) "Tambah" else "Add",
                    color = Color(0xFF0A0A0A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun TodayProductivityChartCard(
    isId: Boolean,
    weekDays: List<Triple<Int, String, Int>>,
    tasks: List<TodayTask>,
    selectedDayOffset: Int,
    waterGlasses: Int,
    surfaceDark: Color,
    subtleDark: Color,
    borderDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentEmerald: Color,
    accentSky: Color,
    accentAmber: Color
) {
    val waterPct = ((waterGlasses.coerceIn(0, 8) / 8f) * 100).roundToInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(surfaceDark)
            .border(1.dp, borderDark, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isId) "📊 Grafik Produktivitas 7 Hari & Hidrasi" else "📊 7-Day Productivity & Hydration Chart",
                color = textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isId) "Air: $waterPct%" else "Water: $waterPct%",
                color = accentSky,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            weekDays.forEach { (offset, dayLabel, _) ->
                val dayTasks = tasks.filter { it.dayIndex == offset }
                val total = dayTasks.size
                val done = dayTasks.count { it.isDone }
                val ratio = when {
                    total > 0 -> (done.toFloat() / total.toFloat()).coerceIn(0.18f, 1f)
                    offset == 0 -> 0.45f
                    offset < 0 -> 0.65f
                    else -> 0.22f
                }
                val isSel = offset == selectedDayOffset
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height((26.dp * ratio).coerceAtLeast(5.dp))
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    isSel -> accentEmerald
                                    done > 0 -> accentSky
                                    else -> subtleDark
                                }
                            )
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = dayLabel.take(2),
                        color = if (isSel) Color.White else textSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ============================================================================
// MODULE 1B: CHAT, SUARA (HANYA MENDENGAR) & KONEKSI SEMUA APLIKASI/TELEPON/SMS/WA
// ============================================================================
@Composable
private fun ChatAndVoiceModuleView(
    lang: PochiLang,
    messages: List<PochiChatMessage>,
    chatInput: String,
    onChatInputChange: (String) -> Unit,
    onSendChat: () -> Unit,
    isListeningVoice: Boolean,
    voicePartialText: String,
    onToggleVoiceMic: () -> Unit,
    isAiThinking: Boolean,
    quickComposerMode: QuickPhoneComposerMode,
    onSelectQuickComposer: (QuickPhoneComposerMode) -> Unit,
    quickTargetInput: String,
    onQuickTargetChange: (String) -> Unit,
    quickMessageInput: String,
    onQuickMessageChange: (String) -> Unit,
    installedApps: List<LaunchableAppInfo>,
    onExecuteQuickComposer: () -> Unit,
    onLaunchAppDirect: (LaunchableAppInfo) -> Unit,
    onShareAnything: () -> Unit,
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
    val scrollState = rememberScrollState()

    LaunchedEffect(messages.size, isAiThinking) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1-Tap Phone Connection Bar: Telepon, SMS, WhatsApp, Buka Aplikasi, & Bagikan (Tanpa Suara TTS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Telepon Action Button
            QuickConnectActionChip(
                icon = Icons.Rounded.Call,
                label = if (isId) "Telepon" else "Call",
                active = quickComposerMode == QuickPhoneComposerMode.CALL,
                accentColor = accentEmerald,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = { onSelectQuickComposer(QuickPhoneComposerMode.CALL) }
            )

            // SMS Action Button
            QuickConnectActionChip(
                icon = Icons.Rounded.Sms,
                label = "SMS",
                active = quickComposerMode == QuickPhoneComposerMode.SMS,
                accentColor = accentSky,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = { onSelectQuickComposer(QuickPhoneComposerMode.SMS) }
            )

            // WhatsApp Action Button
            QuickConnectActionChip(
                icon = Icons.Rounded.Send,
                label = "WhatsApp",
                active = quickComposerMode == QuickPhoneComposerMode.WHATSAPP,
                accentColor = Color(0xFF25D366),
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = { onSelectQuickComposer(QuickPhoneComposerMode.WHATSAPP) }
            )

            // Buka Aplikasi Action Button
            QuickConnectActionChip(
                icon = Icons.Rounded.Apps,
                label = if (isId) "Buka App (${installedApps.size})" else "Open App (${installedApps.size})",
                active = quickComposerMode == QuickPhoneComposerMode.OPEN_APP,
                accentColor = accentAmber,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = { onSelectQuickComposer(QuickPhoneComposerMode.OPEN_APP) }
            )

            // Share ke Aplikasi Mana Saja
            QuickConnectActionChip(
                icon = Icons.Rounded.Share,
                label = if (isId) "Bagikan" else "Share",
                active = false,
                accentColor = accentIndigo,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onShareAnything
            )
        }

        // Inline Quick Composer for Call / SMS / WhatsApp / Open Any Installed App
        AnimatedVisibility(visible = quickComposerMode != QuickPhoneComposerMode.NONE) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(surfaceDark)
                    .border(1.dp, accentSky.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (quickComposerMode) {
                    QuickPhoneComposerMode.OPEN_APP -> {
                        val filteredApps = remember(installedApps, quickTargetInput) {
                            val q = quickTargetInput.trim().lowercase(Locale.getDefault())
                            if (q.isEmpty()) installedApps.take(24)
                            else installedApps.filter {
                                it.label.lowercase(Locale.getDefault()).contains(q) ||
                                    it.packageName.lowercase(Locale.getDefault()).contains(q)
                            }.take(24)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(subtleDark)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = accentAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            BasicTextField(
                                value = quickTargetInput,
                                onValueChange = onQuickTargetChange,
                                textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                cursorBrush = SolidColor(accentAmber),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    if (quickTargetInput.isEmpty()) {
                                        Text(
                                            text = if (isId) "Cari dari ${installedApps.size} aplikasi di HP kamu..." else "Search ${installedApps.size} installed apps...",
                                            color = textSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredApps.forEach { app ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(subtleDark)
                                        .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                                        .clickable { onLaunchAppDirect(app) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "🚀 ${app.label}",
                                        color = textPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    QuickPhoneComposerMode.CALL,
                    QuickPhoneComposerMode.SMS,
                    QuickPhoneComposerMode.WHATSAPP -> {
                        val isCall = quickComposerMode == QuickPhoneComposerMode.CALL
                        val isWa = quickComposerMode == QuickPhoneComposerMode.WHATSAPP
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(subtleDark)
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                BasicTextField(
                                    value = quickTargetInput,
                                    onValueChange = onQuickTargetChange,
                                    textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                    cursorBrush = SolidColor(accentEmerald),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        if (quickTargetInput.isEmpty()) {
                                            Text(
                                                text = if (isId) "Nama kontak atau nomor (0812...)..." else "Contact name or number...",
                                                color = textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        inner()
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCall) accentEmerald else if (isWa) Color(0xFF25D366) else accentSky)
                                    .clickable { onExecuteQuickComposer() }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCall) Icons.Rounded.Call else Icons.Rounded.Send,
                                    contentDescription = "Execute",
                                    tint = Color(0xFF0A0A0A),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (!isCall) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(subtleDark)
                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                            ) {
                                BasicTextField(
                                    value = quickMessageInput,
                                    onValueChange = onQuickMessageChange,
                                    textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                                    cursorBrush = SolidColor(accentSky),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { onExecuteQuickComposer() }),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        if (quickMessageInput.isEmpty()) {
                                            Text(
                                                text = if (isId) "Isi pesan yang ingin dikirim..." else "Message to send...",
                                                color = textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        inner()
                                    }
                                )
                            }
                        }
                    }

                    QuickPhoneComposerMode.NONE -> {}
                }
            }
        }

        // Live Voice Listening Banner (when microphone is active)
        AnimatedVisibility(visible = isListeningVoice || voicePartialText.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = voicePartialText.ifBlank {
                        if (isId) "Mendengarkan... Ucapkan misal: \"Buka WhatsApp\" atau \"Telepon Ibu\""
                        else "Listening... Say e.g. \"Open WhatsApp\" or \"Call Mom\""
                    },
                    color = textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Chat Conversation History Box (Persegi Panjang Rounded)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp, max = 160.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            messages.forEach { msg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 275.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (msg.isUser) accentIndigo else subtleDark)
                            .border(
                                1.dp,
                                if (msg.isUser) accentIndigo else borderDark,
                                RoundedCornerShape(9.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (!msg.isUser && !msg.actionBadge.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "POCHI • ${msg.actionBadge}",
                                    color = accentAmber,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = msg.timeLabel,
                                    color = textSecondary,
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Text(
                            text = msg.text,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (isAiThinking) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(subtleDark)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = accentSky,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isId) "Pochi sedang memproses..." else "Pochi is thinking...",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Bottom Chat & Voice Input Bar (Teks + Tombol Mic Suara + Tombol Kirim Icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Microphone Voice Button (Bicara dengan Pochi)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isListeningVoice) Color(0xFFEF4444) else subtleDark)
                    .border(
                        1.dp,
                        if (isListeningVoice) Color(0xFFEF4444) else accentAmber.copy(alpha = 0.6f),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onToggleVoiceMic() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListeningVoice) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                    contentDescription = "Voice Input",
                    tint = if (isListeningVoice) Color.White else accentAmber,
                    modifier = Modifier.size(17.dp)
                )
            }

            BasicTextField(
                value = chatInput,
                onValueChange = onChatInputChange,
                textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(Color.White),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSendChat() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (chatInput.isEmpty()) {
                        Text(
                            text = if (isId) {
                                "Chat / suara: \"buka wa\", \"telepon Ibu\", \"sms Budi: halo\"..."
                            } else {
                                "Chat / voice: \"open wa\", \"call Mom\", \"sms Budi: hi\"..."
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

            // Send Icon Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable { onSendChat() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Send,
                    contentDescription = "Send",
                    tint = Color(0xFF0A0A0A),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickConnectActionChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accentColor: Color,
    surfaceDark: Color,
    borderDark: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accentColor else surfaceDark)
            .border(1.dp, if (active) accentColor else borderDark, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color(0xFF0A0A0A) else accentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = if (active) Color(0xFF0A0A0A) else Color(0xFFF8FAFC),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ============================================================================
// MODULE 2: CUACA OTOMATIS, GRAFIK SUHU HARIAN & TABEL JADWAL SHOLAT 5 WAKTU
// ============================================================================
@Composable
private fun WeatherAndPrayerModuleView(
    lang: PochiLang,
    selectedCity: String,
    locationSourceLabel: String,
    liveTempOverride: Int?,
    liveHumidityOverride: Int?,
    liveWindOverride: Int?,
    liveHourlyTemps: List<Pair<String, Int>>,
    livePrayerSchedule: List<PrayerTimeItem>?,
    isRefreshingWeather: Boolean,
    onRefreshWeather: () -> Unit,
    onSchedulePrayerTask: (PrayerTimeItem) -> Unit,
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
    val baseWeather = remember(selectedCity, liveTempOverride) {
        WeatherHelper.getSnapshot(selectedCity, liveTempOverride)
    }
    val humidity = liveHumidityOverride ?: baseWeather.humidityPercent
    val windKmh = liveWindOverride ?: baseWeather.windKmh

    val prayerSchedule = remember(selectedCity, livePrayerSchedule) {
        livePrayerSchedule ?: PrayerScheduleHelper.getScheduleForCity(selectedCity)
    }
    val nowCal = remember { Calendar.getInstance() }
    val nowMins = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
    val upcomingPrayer = remember(prayerSchedule, nowMins) {
        prayerSchedule.firstOrNull { it.totalMinutes > nowMins } ?: prayerSchedule.first()
    }
    val diffMins = remember(upcomingPrayer, nowMins) {
        if (upcomingPrayer.totalMinutes > nowMins) {
            upcomingPrayer.totalMinutes - nowMins
        } else {
            (1440 - nowMins) + upcomingPrayer.totalMinutes
        }
    }
    val remText = remember(diffMins, isId) {
        val h = diffMins / 60
        val m = diffMins % 60
        if (isId) {
            if (h > 0) "${h}j ${m}m lagi" else "${m}m lagi"
        } else {
            if (h > 0) "in ${h}h ${m}m" else "in ${m}m"
        }
    }

    val hourlyChartData = remember(liveHourlyTemps, baseWeather.tempC) {
        if (liveHourlyTemps.size >= 5) {
            liveHourlyTemps
        } else {
            val t = baseWeather.tempC
            listOf(
                "06:00" to (t - 4),
                "09:00" to (t - 1),
                "12:00" to (t + 2),
                "15:00" to (t + 1),
                "18:00" to (t - 1),
                "21:00" to (t - 3)
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1. Smart Auto-Located Weather Card + Icon-Only Refresh Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(subtleDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WbSunny,
                            contentDescription = null,
                            tint = accentAmber,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${baseWeather.tempC}°C",
                                color = textPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isId) baseWeather.conditionId else baseWeather.conditionEn,
                                color = accentSky,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(accentEmerald.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "📍 ${baseWeather.city} ($locationSourceLabel)",
                                    color = accentEmerald,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = if (isId) {
                                "Lokasi otomatis • Kelembapan $humidity% • Angin $windKmh km/j"
                            } else {
                                "Auto-located • Humidity $humidity% • Wind $windKmh km/h"
                            },
                            color = textSecondary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Icon-only Refresh Button (Deteksi ulang lokasi & cuaca otomatis)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(subtleDark)
                        .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                        .clickable { onRefreshWeather() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRefreshingWeather) Icons.Rounded.Sync else Icons.Rounded.Refresh,
                        contentDescription = if (isId) "Segarkan Lokasi & Cuaca" else "Refresh Auto Location & Weather",
                        tint = accentSky,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Hourly Temperature Curve & Bar Chart (06:00 - 21:00)
            WeatherHourlyChartCard(
                isId = isId,
                hourlyData = hourlyChartData,
                subtleDark = subtleDark,
                borderDark = borderDark,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                accentSky = accentSky,
                accentAmber = accentAmber
            )
        }

        // 2. Clean 5-Time Prayer Schedule Table (Tanpa Scrollbar / Elemen Kanan yang Mengganggu!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isId) {
                        "🕌 Jadwal Sholat ${baseWeather.city}"
                    } else {
                        "🕌 Prayer Schedule (${baseWeather.city})"
                    },
                    color = textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isId) {
                        "Berikutnya: ${upcomingPrayer.nameId} ${upcomingPrayer.formatted} ($remText)"
                    } else {
                        "Next: ${upcomingPrayer.nameEn} ${upcomingPrayer.formatted} ($remText)"
                    },
                    color = accentEmerald,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 5-Column Clean Prayer Schedule Table/Grid (Zero Scrollbar!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                prayerSchedule.forEach { item ->
                    val isNext = item.nameId == upcomingPrayer.nameId
                    val isPassed = item.totalMinutes <= nowMins && !isNext
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isNext -> accentEmerald.copy(alpha = 0.22f)
                                    isPassed -> subtleDark.copy(alpha = 0.45f)
                                    else -> subtleDark
                                }
                            )
                            .border(
                                1.dp,
                                if (isNext) accentEmerald else borderDark,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSchedulePrayerTask(item) }
                            .padding(vertical = 7.dp, horizontal = 3.dp)
                    ) {
                        Text(
                            text = if (isId) item.nameId else item.nameEn,
                            color = when {
                                isNext -> accentEmerald
                                isPassed -> textSecondary.copy(alpha = 0.65f)
                                else -> textSecondary
                            },
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = item.formatted,
                            color = if (isPassed) textSecondary else textPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = when {
                                isNext -> if (isId) "● Aktif" else "● Next"
                                isPassed -> if (isId) "✓ Lewat" else "✓ Done"
                                else -> if (isId) "Nanti" else "Later"
                            },
                            color = when {
                                isNext -> accentEmerald
                                isPassed -> textSecondary.copy(alpha = 0.6f)
                                else -> accentSky
                            },
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherHourlyChartCard(
    isId: Boolean,
    hourlyData: List<Pair<String, Int>>,
    subtleDark: Color,
    borderDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentSky: Color,
    accentAmber: Color
) {
    val temps = hourlyData.map { it.second }
    val minT = (temps.minOrNull() ?: 22) - 1
    val maxT = (temps.maxOrNull() ?: 34) + 1
    val span = (maxT - minT).coerceAtLeast(4)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(subtleDark)
            .border(1.dp, borderDark, RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isId) "📈 Grafik Suhu Harian (06:00–21:00)" else "📈 Hourly Temperature Chart (06:00–21:00)",
                color = textSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Min ${temps.minOrNull() ?: 24}° • Max ${temps.maxOrNull() ?: 32}°",
                color = accentAmber,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Smooth Curve + Gradient Area Chart on Compose Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
        ) {
            val w = size.width
            val h = size.height
            if (hourlyData.size < 2) return@Canvas

            val stepX = w / (hourlyData.size - 1).toFloat()
            val points = hourlyData.mapIndexed { idx, (_, temp) ->
                val norm = ((temp - minT).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                val x = idx * stepX
                val y = h - (norm * (h * 0.72f) + h * 0.14f)
                Offset(x, y)
            }

            val areaPath = Path().apply {
                moveTo(points.first().x, h)
                points.forEach { pt -> lineTo(pt.x, pt.y) }
                lineTo(points.last().x, h)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentSky.copy(alpha = 0.34f), Color.Transparent)
                )
            )

            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val prev = points[i - 1]
                    val curr = points[i]
                    val midX = (prev.x + curr.x) / 2f
                    cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                }
            }
            drawPath(
                path = linePath,
                color = accentSky,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )

            points.forEachIndexed { idx, pt ->
                val isPeak = hourlyData[idx].second == temps.maxOrNull()
                drawCircle(
                    color = if (isPeak) accentAmber else Color.White,
                    radius = if (isPeak) 3.6.dp.toPx() else 2.8.dp.toPx(),
                    center = pt
                )
            }
        }

        // Data Table Row under Chart (Hour & Temp °C)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            hourlyData.forEach { (hourLabel, tempVal) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${tempVal}°",
                        color = textPrimary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = hourLabel,
                        color = textSecondary,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ============================================================================
// MODULE 3: DOMPET, MONEY TRACKER, CHART KATEGORI & PINTASAN PEMBAYARAN
// ============================================================================
@Composable
private fun WalletModuleView(
    lang: PochiLang,
    expenses: List<ExpenseEntry>,
    dailyBudgetRp: Long,
    expenseInput: String,
    onExpenseInputChange: (String) -> Unit,
    onSubmitExpense: () -> Unit,
    onQuickAddExpense: (String, Long, String) -> Unit,
    onDeleteExpense: (ExpenseEntry) -> Unit,
    isEditingBudget: Boolean,
    budgetDraft: String,
    onBudgetDraftChange: (String) -> Unit,
    onToggleEditBudget: () -> Unit,
    onOpenPaymentApp: (String, List<String>) -> Unit,
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
    val totalSpentRp = expenses.sumOf { it.amountRp }
    val remainingBudgetRp = (dailyBudgetRp - totalSpentRp).coerceAtLeast(0L)
    val isOverBudget = totalSpentRp > dailyBudgetRp
    val spentRatio = (totalSpentRp.toFloat() / dailyBudgetRp.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Daily Budget & Spent Summary Card (Persegi Panjang Rounded)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isId) "Pengeluaran Hari Ini" else "Today's Spending",
                        color = textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = NaturalExpenseParser.formatRupiah(totalSpentRp),
                        color = if (isOverBudget) Color(0xFFF87171) else textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isOverBudget) {
                                if (isId) "Melebihi Target!" else "Over Budget!"
                            } else {
                                if (isId) "Sisa: ${NaturalExpenseParser.formatRupiah(remainingBudgetRp)}" else "Left: ${NaturalExpenseParser.formatRupiah(remainingBudgetRp)}"
                            },
                            color = if (isOverBudget) Color(0xFFF87171) else accentEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(subtleDark)
                                .clickable { onToggleEditBudget() }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isEditingBudget) (if (isId) "Simpan" else "Save") else (if (isId) "Atur" else "Target"),
                                color = accentSky,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = if (isId) "Batas: ${NaturalExpenseParser.formatRupiah(dailyBudgetRp)}/hari" else "Limit: ${NaturalExpenseParser.formatRupiah(dailyBudgetRp)}/day",
                        color = textSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            AnimatedVisibility(visible = isEditingBudget) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(subtleDark)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isId) "Batas Harian (ribu Rp):" else "Daily Limit (k Rp):",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    BasicTextField(
                        value = budgetDraft,
                        onValueChange = onBudgetDraftChange,
                        textStyle = TextStyle(color = accentSky, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(accentSky),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { spentRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    isOverBudget -> Color(0xFFEF4444)
                    spentRatio > 0.8f -> accentAmber
                    else -> accentEmerald
                },
                trackColor = subtleDark
            )

            // Expense Category Distribution Chart & Summary Table
            if (expenses.isNotEmpty()) {
                val categoryTotals = remember(expenses) {
                    expenses.groupBy { it.category }
                        .mapValues { (_, list) -> list.sumOf { it.amountRp } }
                        .entries
                        .sortedByDescending { it.value }
                        .take(4)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryTotals.forEachIndexed { index, (cat, amt) ->
                        val pct = ((amt.toFloat() / totalSpentRp.coerceAtLeast(1L).toFloat()) * 100).roundToInt()
                        val badgeColor = when (index) {
                            0 -> accentAmber
                            1 -> accentSky
                            2 -> accentEmerald
                            else -> accentIndigo
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .background(subtleDark)
                                .padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cat,
                                    color = badgeColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "$pct%",
                                    color = textSecondary,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(borderDark)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth((pct / 100f).coerceIn(0.15f, 1f))
                                        .height(3.dp)
                                        .background(badgeColor)
                                )
                            }
                            Text(
                                text = "${amt / 1000}rb",
                                color = textPrimary,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Quick 1-Tap Expense Presets + E-Wallet / QRIS Payment Shortcuts
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1-Tap Quick Expenses
            listOf(
                Triple(if (isId) "🍜 Makan 20rb" else "🍜 Meal 20k", 20_000L, "Makan"),
                Triple(if (isId) "☕ Kopi 15rb" else "☕ Coffee 15k", 15_000L, "Jajan"),
                Triple(if (isId) "🛵 Ojek/Bensin 15rb" else "🛵 Ride 15k", 15_000L, "Transport")
            ).forEach { (label, amt, cat) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceDark)
                        .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                        .clickable { onQuickAddExpense(label.substringAfter(" "), amt, cat) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(label, color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Quick E-Wallet / QRIS Launchers
            listOf(
                "DANA" to listOf("id.dana"),
                "GoPay" to listOf("com.gojek.gopay", "com.gojek.app"),
                "OVO" to listOf("ovo.id"),
                "ShopeePay" to listOf("com.shopee.id"),
                "BCA" to listOf("com.bca")
            ).forEach { (walletName, pkgs) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(subtleDark)
                        .border(1.dp, accentSky.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { onOpenPaymentApp(walletName, pkgs) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "💳 $walletName",
                        color = accentSky,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Expense History List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 128.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (expenses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isId) "Belum ada catatan pengeluaran hari ini." else "No expenses recorded today.",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                }
            } else {
                expenses.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(subtleDark.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(surfaceDark)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = item.category,
                                color = accentAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = item.title,
                            color = textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = NaturalExpenseParser.formatRupiah(item.amountRp),
                            color = accentEmerald,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(surfaceDark)
                                .clickable { onDeleteExpense(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Delete",
                                tint = textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Natural Expense Input Bar ("makan siang 25rb")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AccountBalanceWallet,
                contentDescription = null,
                tint = accentEmerald,
                modifier = Modifier.size(17.dp)
            )
            BasicTextField(
                value = expenseInput,
                onValueChange = onExpenseInputChange,
                textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(accentEmerald),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmitExpense() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (expenseInput.isEmpty()) {
                        Text(
                            text = if (isId) "Catat cepat: \"makan siang 25rb\" atau \"kopi 18k\"..." else "Quick log: \"lunch 25k\" or \"coffee 18k\"...",
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
                    .height(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentEmerald)
                    .clickable { onSubmitExpense() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isId) "Catat" else "Save",
                    color = Color(0xFF0A0A0A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// ============================================================================
// MODULE 4: FOCUS (Task-Linked Pomodoro Timer with Rectangular Rounded Controls)
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
        // Phase Switcher (Persegi Panjang Rounded) + Session Counter
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
                            .heightIn(min = 32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) Color.White else surfaceDark)
                            .border(1.dp, if (active) Color.White else borderDark, RoundedCornerShape(8.dp))
                            .clickable { onSelectPhase(phase) }
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = phase.label(lang),
                            color = if (active) Color(0xFF0A0A0A) else textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = if (isId) "🔥 $completedSessions sesi" else "🔥 $completedSessions sessions",
                color = accentAmber,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Timer Display + Presets (Persegi Panjang Rounded)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(78.dp)) {
                Canvas(modifier = Modifier.size(78.dp)) {
                    val sw = 6.dp.toPx()
                    drawArc(
                        color = subtleDark,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = if (isRunning) accentAmber else Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        style = Stroke(width = sw, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = String.format(Locale.US, "%02d:%02d", mm, ss),
                    color = textPrimary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = linkedTask?.title ?: (if (isId) "Sesi Fokus Produktif" else "Deep Focus Session"),
                        color = textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (linkedTask != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(accentEmerald.copy(alpha = 0.2f))
                                .clickable { onCompleteLinkedTask(linkedTask) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isId) "✓ Selesai" else "✓ Done",
                                color = accentEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(10, 25, 45, 60).forEach { preset ->
                        val selected = preset == durationMinutes
                        Box(
                            modifier = Modifier
                                .heightIn(min = 30.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (selected) Color.White else subtleDark)
                                .clickable { onSelectPreset(preset) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${preset}m",
                                color = if (selected) Color(0xFF0A0A0A) else textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentAmber)
                            .clickable { onToggleStartPause() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                isRunning -> if (isId) "Jeda" else "Pause"
                                else -> if (isId) "Mulai Fokus" else "Start Focus"
                            },
                            color = Color(0xFF0A0A0A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(subtleDark)
                            .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                            .clickable { onReset() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isId) "Ulang" else "Reset",
                            color = textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (tasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isId) "Target:" else "Task:",
                    color = textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                tasks.take(6).forEach { t ->
                    val active = (linkedTask?.id == t.id)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) subtleDark else surfaceDark)
                            .border(1.dp, if (active) Color.White.copy(alpha = 0.5f) else borderDark, RoundedCornerShape(8.dp))
                            .clickable { onSelectTask(t.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = t.title.take(22),
                            color = if (active) Color.White else textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// MODULE 5: DECK (Grid Persegi Panjang Rounded untuk Semua Aplikasi & Alat HP)
// ============================================================================
@Composable
private fun DeckModuleView(
    lang: PochiLang,
    installedApps: List<LaunchableAppInfo>,
    pinnedPackages: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showAllApps: Boolean,
    onToggleShowAllApps: () -> Unit,
    isCustomizing: Boolean,
    isTorchOn: Boolean,
    isFlushStatusBar: Boolean,
    onToggleFlushStatusBar: () -> Unit,
    onToggleCustomize: () -> Unit,
    onTogglePinApp: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    onActionCallDialer: () -> Unit,
    onActionSendSms: () -> Unit,
    onActionDownloads: () -> Unit,
    onActionScreenshot: () -> Unit,
    onActionLockScreen: () -> Unit,
    onActionToggleTorch: () -> Unit,
    onActionAlarm: () -> Unit,
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
    val displayedApps = remember(installedApps, pinnedPackages, isCustomizing, showAllApps, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        when {
            q.isNotEmpty() -> {
                installedApps.filter {
                    it.label.lowercase(Locale.getDefault()).contains(q) ||
                        it.packageName.lowercase(Locale.getDefault()).contains(q)
                }
            }
            isCustomizing || showAllApps -> installedApps
            else -> {
                val pinned = installedApps.filter { it.packageName in pinnedPackages }
                if (pinned.isNotEmpty()) pinned.take(12) else installedApps.take(12)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // System & Phone Shortcuts Row (Telepon, SMS, Snip, Alarm, Kunci, Senter)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DeckShortcutTile(
                modifier = Modifier.width(62.dp),
                icon = Icons.Rounded.Call,
                label = if (isId) "Telepon" else "Call",
                accent = accentEmerald,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionCallDialer
            )
            DeckShortcutTile(
                modifier = Modifier.width(62.dp),
                icon = Icons.Rounded.Sms,
                label = "SMS",
                accent = Color(0xFF38BDF8),
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionSendSms
            )
            DeckShortcutTile(
                modifier = Modifier.width(62.dp),
                icon = Icons.Rounded.Download,
                label = if (isId) "Unduhan" else "Downloads",
                accent = Color.White,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionDownloads
            )
            DeckShortcutTile(
                modifier = Modifier.width(62.dp),
                icon = Icons.Rounded.ContentCut,
                label = "Snip",
                accent = Color.White,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionScreenshot
            )
            DeckShortcutTile(
                modifier = Modifier.width(62.dp),
                icon = Icons.Rounded.Alarm,
                label = "Alarm",
                accent = Color.White,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionAlarm
            )
            DeckShortcutTile(
                modifier = Modifier.width(62.dp),
                icon = Icons.Rounded.Lock,
                label = if (isId) "Kunci" else "Lock",
                accent = Color.White,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionLockScreen
            )
            DeckShortcutTile(
                modifier = Modifier.width(62.dp),
                icon = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                label = if (isId) "Senter" else "Torch",
                accent = if (isTorchOn) accentAmber else Color.White,
                surfaceDark = surfaceDark,
                borderDark = borderDark,
                onClick = onActionToggleTorch
            )
        }

        // Instant App Search Bar ("Cari & buka semua aplikasi di HP...")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(surfaceDark)
                .border(1.dp, borderDark, RoundedCornerShape(9.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = accentAmber,
                modifier = Modifier.size(16.dp)
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                textStyle = TextStyle(color = textPrimary, fontSize = 12.sp),
                cursorBrush = SolidColor(accentAmber),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        displayedApps.firstOrNull()?.let { onLaunchApp(it.packageName) }
                    }
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = if (isId) "Cari & buka dari ${installedApps.size} aplikasi di HP..." else "Search & open from ${installedApps.size} installed apps...",
                            color = textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            )
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear",
                    tint = textSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onSearchQueryChange("") }
                )
            }
        }

        // Pinned / All Apps Header + Status-bar Dock Toggle + Pin Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (showAllApps) accentIndigo.copy(alpha = 0.22f) else surfaceDark)
                    .border(1.dp, if (showAllApps) accentIndigo else borderDark, RoundedCornerShape(8.dp))
                    .clickable { onToggleShowAllApps() }
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (showAllApps) {
                        if (isId) "Semua App (${installedApps.size})" else "All Apps (${installedApps.size})"
                    } else {
                        if (isId) "Tampilkan Semua (${installedApps.size})" else "Show All (${installedApps.size})"
                    },
                    color = if (showAllApps) Color.White else accentAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceDark)
                        .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                        .clickable { onToggleFlushStatusBar() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isFlushStatusBar) {
                            if (isId) "✓ Statusbar" else "✓ Docked"
                        } else {
                            if (isId) "Melayang" else "Floating"
                        },
                        color = accentEmerald,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(surfaceDark)
                        .border(1.dp, borderDark, RoundedCornerShape(8.dp))
                        .clickable { onToggleCustomize() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isCustomizing) (if (isId) "Selesai" else "Done") else (if (isId) "+ Pin" else "+ Pin"),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.16f))
                        .clickable { onActionPowerOff() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isId) "Off" else "Off",
                        color = Color(0xFFF87171),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4-Column Rectangular Rounded App Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 152.dp)
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
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 60.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCustomizing && isPinned) accentIndigo.copy(alpha = 0.25f) else surfaceDark)
                                .border(
                                    1.dp,
                                    if (isCustomizing && isPinned) accentIndigo else borderDark,
                                    RoundedCornerShape(10.dp)
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
                                    .size(width = 32.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(subtleDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = app.label.take(2).uppercase(Locale.getDefault()),
                                    color = textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = app.label,
                                color = textPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
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
// SYSTEM OVERLAY CARDS (Persegi Panjang Rounded)
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
            .clip(RoundedCornerShape(10.dp))
            .background(surfaceDark)
            .border(1.dp, accentSky.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
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
                fontSize = 10.sp,
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
                .size(18.dp)
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
            .clip(RoundedCornerShape(10.dp))
            .background(surfaceDark)
            .border(1.dp, borderDark, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(download.fraction * 100).toInt()}%",
                color = accentSky,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { download.fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp)),
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
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(surfaceDark)
            .border(1.dp, borderDark, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = accentIndigo,
            modifier = Modifier.size(17.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (media.artist.isNotBlank()) "${media.title} — ${media.artist}" else media.title,
                color = textPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .clickable { onPrev() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Previous",
                tint = textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .clickable { onPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (media.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play/Pause",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(7.dp))
                .clickable { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next",
                tint = textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
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
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(surfaceDark)
            .border(1.dp, borderDark, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 26.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF1E1E24)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = accent, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = Color(0xFFF8FAFC), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun MiniStatusButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(accent.copy(alpha = 0.18f))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QuickCommandRowItem(
    icon: ImageVector,
    title: String,
    trailing: String,
    subtleDark: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(subtleDark)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = title,
            color = textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = trailing,
            color = textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
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
