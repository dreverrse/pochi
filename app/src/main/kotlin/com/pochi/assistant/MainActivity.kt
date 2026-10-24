package com.pochi.assistant

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PochiPrefs.init(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        setContent {
            val isDark by PochiPrefs.darkModeFlow.collectAsState()
            val isRunning by DynamicIslandService.isRunning.collectAsState()
            val lang by PochiPrefs.langFlow.collectAsState()
            val isFlushStatusBar by PochiPrefs.flushStatusBarFlow.collectAsState()
            val isId = lang == PochiLang.ID
            var provider by remember { mutableStateOf(PochiPrefs.getProvider(this)) }
            var apiKey by remember { mutableStateOf(PochiPrefs.getApiKey(this, provider)) }
            var model by remember { mutableStateOf(PochiPrefs.getModel(this, provider)) }
            var showApiKey by remember { mutableStateOf(false) }
            var showAdvancedBrain by remember { mutableStateOf(false) }
            var previewEmote by remember { mutableStateOf(PochiEmote.SMILING) }
            var heroState by remember { mutableStateOf(BotState.IDLE) }
            var heroPokeCount by remember { mutableIntStateOf(0) }
            val defaultSubtitle = if (isId) {
                "Dynamic Island 120fps • Hari Ini, Agen, Fokus & Pintasan"
            } else {
                "120fps Dynamic Island • Today, Agent, Focus & Deck"
            }
            var heroMoodText by remember(isId) { mutableStateOf(defaultSubtitle) }
            val heroScope = rememberCoroutineScope()
            val offsetX by PochiPrefs.offsetXFlow.collectAsState()
            val offsetY by PochiPrefs.offsetYFlow.collectAsState()

            LaunchedEffect(Unit) {
                while (true) {
                    delay((4500..7800).random().toLong())
                    if (heroPokeCount == 0) {
                        previewEmote = PochiMood.nextAutonomousMood(0L, false)
                    }
                }
            }

            val bgPage = if (isDark) Color(0xFF0B0C10) else Color(0xFFF8FAFC)
            val cardBg = if (isDark) Color(0xFF15171E) else Color.White
            val subtleBg = if (isDark) Color(0xFF1F222C) else Color(0xFFF1F5F9)
            val borderCol = if (isDark) Color(0xFF262936) else Color(0xFFE2E8F0)
            val textPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
            val textSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
            val accentBlue = Color(0xFF0A84FF)

            LaunchedEffect(provider) {
                apiKey = PochiPrefs.getApiKey(this@MainActivity, provider)
                model = PochiPrefs.getModel(this@MainActivity, provider)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgPage)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ================= HERO HEADER =================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(24.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isRunning) Color(0xFF30D158).copy(alpha = 0.14f)
                                    else subtleBg
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isRunning) Color(0xFF30D158) else textSecondary)
                            )
                            Text(
                                text = if (isRunning) {
                                    if (isId) "Aktif di Status Bar" else "Active on Status Bar"
                                } else {
                                    if (isId) "Nonaktif" else "Inactive"
                                },
                                color = if (isRunning) Color(0xFF30D158) else textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(subtleBg)
                                    .border(1.dp, borderCol, RoundedCornerShape(50))
                                    .clickable { PochiPrefs.toggleLanguage(this@MainActivity) }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isId) "🇮🇩 ID" else "🇬🇧 EN",
                                    color = textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = { PochiPrefs.setDarkMode(this@MainActivity, !isDark) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(subtleBg)
                            ) {
                                Icon(
                                    imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "Tema",
                                    tint = textPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    PochiMascot(
                        state = if (heroState != BotState.IDLE) heroState else if (isRunning) BotState.DONE else BotState.IDLE,
                        emoteOverride = previewEmote,
                        size = 76.dp,
                        onPoke = {
                            heroPokeCount++
                            val (emote, state, text) = PochiMood.onPokeReaction(heroPokeCount)
                            previewEmote = emote
                            heroState = state
                            heroMoodText = text
                            heroScope.launch {
                                val current = heroPokeCount
                                delay(3600)
                                if (heroPokeCount == current) {
                                    if (heroPokeCount >= 5) heroPokeCount = 0
                                    heroState = BotState.IDLE
                                    heroMoodText = defaultSubtitle
                                }
                            }
                        },
                        onPet = {
                            val wasUpset = heroPokeCount >= 3 || heroState == BotState.ANNOYED
                            heroPokeCount = 0
                            val (emote, state, text) = PochiMood.onPetReaction(wasUpset)
                            previewEmote = emote
                            heroState = state
                            heroMoodText = text
                            heroScope.launch {
                                delay(3600)
                                heroState = BotState.IDLE
                                heroMoodText = defaultSubtitle
                            }
                        }
                    )

                    Text(
                        text = "Pochi • ${previewEmote.title}",
                        color = textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = heroMoodText,
                        color = textSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!Settings.canDrawOverlays(this@MainActivity)) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        if (isId) "Izinkan 'Tampilkan di atas aplikasi lain' untuk Pochi"
                                        else "Grant 'Display over other apps' permission for Pochi",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                } else {
                                    stopService(Intent(this@MainActivity, DynamicIslandService::class.java))
                                    startForegroundService(Intent(this@MainActivity, DynamicIslandService::class.java))
                                    Toast.makeText(
                                        this@MainActivity,
                                        if (isId) "Pochi aktif menempel di status bar" else "Pochi active on status bar",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Text(
                                text = if (isRunning) {
                                    if (isId) "Muat Ulang Island" else "Reload Island"
                                } else {
                                    if (isId) "Aktifkan Pochi" else "Start Pochi"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (isRunning) {
                            OutlinedButton(
                                onClick = {
                                    stopService(Intent(this@MainActivity, DynamicIslandService::class.java))
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Matikan", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // ================= CRASH BANNER (JIKA ADA) =================
                val crashFile = remember { File(filesDir, "last_crash.txt") }
                var crashText by remember {
                    mutableStateOf(
                        runCatching { crashFile.takeIf { it.exists() }?.readText() }.getOrNull()
                    )
                }
                crashText?.let { text ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isDark) Color(0xFF2A1215) else Color(0xFFFFF1F0))
                            .border(1.dp, Color(0xFFFF453A).copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Log Gangguan Terakhir",
                            color = Color(0xFFFF453A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = text.take(800),
                            color = textPrimary,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Pochi crash", text))
                                    Toast.makeText(this@MainActivity, "Log disalin", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Salin", fontSize = 11.sp, color = textPrimary)
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching { crashFile.delete() }
                                    crashText = null
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Bersihkan", fontSize = 11.sp, color = Color(0xFFFF453A))
                            }
                        }
                    }
                }

                // ================= CONTOH PERINTAH INSTAN =================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Pintasan Cepat (Tanpa Delay)",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ketik langsung di bar Pochi kapan saja:",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    QuickExampleRow("Hitung Cepat & Diskon", "45rb * 3  •  250rb - 15%", subtleBg, textPrimary, textSecondary)
                    QuickExampleRow("Catatan & To-Do", "catat beli kopi  •  ingat bayar listrik", subtleBg, textPrimary, textSecondary)
                    QuickExampleRow("Timer & Alarm", "timer 10 menit  •  alarm 06:30", subtleBg, textPrimary, textSecondary)
                    QuickExampleRow("Buka Aplikasi & Peta", "buka wa  •  buka spotify  •  rute ke stasiun", subtleBg, textPrimary, textSecondary)
                }

                // ================= AKSES & SENSOR KONTEKS =================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Izin & Konteks Pintar",
                        color = textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val screenAwareness by PochiPrefs.screenAwarenessFlow.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Baca Konteks Layar", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Bantu rangkum, terjemahkan, atau buat draf balasan dari layar aktif.",
                                color = textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Switch(
                            checked = screenAwareness,
                            onCheckedChange = { enabled ->
                                PochiPrefs.setScreenAwareness(this@MainActivity, enabled)
                                if (enabled && PochiAccessibilityService.instance == null) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Aktifkan 'Pochi Screen Awareness' di Aksesibilitas",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = accentBlue
                            )
                        )
                    }

                    HorizontalDivider(color = borderCol)

                    var contactGranted by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.READ_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val contactLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted -> contactGranted = granted }

                    var micGranted by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val micLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted -> micGranted = granted }

                    var callGranted by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.CALL_PHONE
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val callLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted -> callGranted = granted }

                    var locationGranted by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val locationLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted -> locationGranted = granted }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PermissionPillButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.NotificationsNone,
                            label = "Akses Notifikasi",
                            subtleBg = subtleBg,
                            textPrimary = textPrimary
                        ) {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }

                        PermissionPillButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Contacts,
                            label = if (contactGranted) "Kontak Aktif" else "Izinkan Kontak",
                            subtleBg = subtleBg,
                            textPrimary = if (contactGranted) Color(0xFF30D158) else textPrimary
                        ) {
                            if (!contactGranted) {
                                contactLauncher.launch(Manifest.permission.READ_CONTACTS)
                            } else {
                                Toast.makeText(this@MainActivity, "Akses kontak sudah aktif", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PermissionPillButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Mic,
                            label = if (micGranted) "Mendengar Aktif" else "Izinkan Mikrofon",
                            subtleBg = subtleBg,
                            textPrimary = if (micGranted) Color(0xFF30D158) else textPrimary
                        ) {
                            if (!micGranted) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                Toast.makeText(this@MainActivity, "Mikrofon pendengar Pochi sudah aktif", Toast.LENGTH_SHORT).show()
                            }
                        }

                        PermissionPillButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Rounded.Call,
                            label = if (callGranted) "Telepon Aktif" else "Izinkan Telepon",
                            subtleBg = subtleBg,
                            textPrimary = if (callGranted) Color(0xFF30D158) else textPrimary
                        ) {
                            if (!callGranted) {
                                callLauncher.launch(Manifest.permission.CALL_PHONE)
                            } else {
                                Toast.makeText(this@MainActivity, "Akses panggilan telepon sudah aktif", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PermissionPillButton(
                            modifier = Modifier.fillMaxWidth(),
                            icon = Icons.Rounded.LocationOn,
                            label = if (locationGranted) "Lokasi Cuaca & Sholat Otomatis Aktif" else "Izinkan Lokasi (Cuaca & Sholat Otomatis)",
                            subtleBg = subtleBg,
                            textPrimary = if (locationGranted) Color(0xFF30D158) else textPrimary
                        ) {
                            if (!locationGranted) {
                                locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            } else {
                                Toast.makeText(this@MainActivity, "Lokasi otomatis cuaca & sholat sudah aktif", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                // ================= KALIBRASI POSISI & STATUSBAR DOCK =================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isId) "Posisi & Mode Status Bar" else "Position & Status Bar Dock",
                            color = textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = {
                            PochiPrefs.setFlushStatusBar(this@MainActivity, true)
                            PochiPrefs.saveOffsets(this@MainActivity, 0, 0)
                        }) {
                            Text("Reset", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isId) "Nempel Status Bar" else "Flush with Status Bar",
                                color = textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isId) {
                                    "Ukuran ringkas tepat di lubang kamera/status bar agar tidak mengganggu pemandangan."
                                } else {
                                    "Ultra-slim pill docked inside the top status bar so it never obstructs apps."
                                },
                                color = textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        Switch(
                            checked = isFlushStatusBar,
                            onCheckedChange = { enabled ->
                                PochiPrefs.setFlushStatusBar(this@MainActivity, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            )
                        )
                    }

                    HorizontalDivider(color = borderCol)

                    Text(
                        text = if (isId) "Atas / Bawah: ${offsetY} dp" else "Vertical Offset: ${offsetY} dp",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = offsetY.toFloat(),
                        onValueChange = { PochiPrefs.saveOffsets(this@MainActivity, offsetX, it.roundToInt()) },
                        valueRange = -16f..40f,
                        colors = SliderDefaults.colors(thumbColor = accentBlue, activeTrackColor = accentBlue)
                    )

                    Text(
                        text = if (isId) "Kiri / Kanan: ${offsetX} dp" else "Horizontal Offset: ${offsetX} dp",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = offsetX.toFloat(),
                        onValueChange = { PochiPrefs.saveOffsets(this@MainActivity, it.roundToInt(), offsetY) },
                        valueRange = -150f..150f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF30D158), activeTrackColor = Color(0xFF30D158))
                    )
                }

                // ================= KONEKSI PINTAR (COLLAPSIBLE) =================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvancedBrain = !showAdvancedBrain },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Koneksi Asisten Pintar",
                                color = textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${provider.label} • ${if (apiKey.isBlank()) "Kunci belum diisi" else "Terhubung"}",
                                color = textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Icon(
                            imageVector = if (showAdvancedBrain) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = textSecondary
                        )
                    }

                    if (showAdvancedBrain) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AiProvider.entries.forEach { entry ->
                                    val active = entry == provider
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (active) accentBlue else subtleBg)
                                            .clickable {
                                                provider = entry
                                                PochiPrefs.setProvider(this@MainActivity, entry)
                                            }
                                            .padding(vertical = 9.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = entry.label,
                                            color = if (active) Color.White else textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = {
                                    apiKey = it
                                    PochiPrefs.saveApiKey(this@MainActivity, provider, it)
                                },
                                label = { Text("Kunci API ${provider.label}") },
                                placeholder = { Text(provider.keyHint) },
                                singleLine = true,
                                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showApiKey = !showApiKey }) {
                                        Icon(
                                            imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = null,
                                            tint = textSecondary
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary,
                                    focusedContainerColor = cardBg,
                                    unfocusedContainerColor = cardBg,
                                    focusedBorderColor = accentBlue,
                                    unfocusedBorderColor = borderCol
                                )
                            )

                            OutlinedTextField(
                                value = model,
                                onValueChange = {
                                    model = it
                                    PochiPrefs.saveModel(this@MainActivity, provider, it)
                                },
                                label = { Text("Model") },
                                placeholder = { Text(provider.defaultModel) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary,
                                    focusedContainerColor = cardBg,
                                    unfocusedContainerColor = cardBg,
                                    focusedBorderColor = accentBlue,
                                    unfocusedBorderColor = borderCol
                                )
                            )

                            OutlinedButton(
                                onClick = {
                                    ApprovalManager.requestApproval(
                                        agent = "Pochi",
                                        friendlyTitle = "Tes Konfirmasi Keamanan Biometrik",
                                        rawCommand = "pochi-agent TEST_BIOMETRIC"
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Tes Konfirmasi Biometrik", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // ================= PEMBARUAN & GALERI EKSPRESI =================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, borderCol, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
                    var checking by remember { mutableStateOf(false) }
                    var downloading by remember { mutableStateOf(false) }
                    var downloadProgress by remember { mutableIntStateOf(0) }
                    var downloadedApk by remember { mutableStateOf<File?>(null) }
                    var updateError by remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()
                    val currentVersion = remember {
                        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrDefault("5.9.0")
                    }

                    LaunchedEffect(Unit) {
                        updateInfo = UpdateChecker.check(this@MainActivity)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Pembaruan Aplikasi", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Versi terpasang: v$currentVersion", color = textSecondary, fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                checking = true
                                updateError = null
                                scope.launch {
                                    updateInfo = UpdateChecker.check(this@MainActivity)
                                    checking = false
                                }
                            },
                            enabled = !checking && !downloading,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (checking) "Mengecek..." else "Cek Update", fontSize = 11.sp, color = accentBlue)
                        }
                    }

                    if (downloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Mengunduh pembaruan... ${downloadProgress}%",
                                color = accentBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            LinearProgressIndicator(
                                progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(50)),
                                color = accentBlue,
                                trackColor = subtleBg
                            )
                        }
                    }

                    updateError?.let { err ->
                        Text(err, color = Color(0xFFFF453A), fontSize = 11.sp)
                    }

                    updateInfo?.let { info ->
                        if (info.hasUpdate && info.downloadUrl.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        downloading = true
                                        downloadProgress = 0
                                        updateError = null
                                        scope.launch {
                                            val res = UpdateChecker.downloadAndInstall(
                                                context = this@MainActivity,
                                                url = info.downloadUrl,
                                                version = info.latestVersion,
                                                onProgress = { pct -> downloadProgress = pct }
                                            )
                                            downloading = false
                                            res.onSuccess { file ->
                                                downloadedApk = file
                                            }.onFailure { e ->
                                                updateError = "Gagal update: ${e.localizedMessage}"
                                            }
                                        }
                                    },
                                    enabled = !downloading,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF30D158)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (downloading) "Mengunduh..." else "Update Langsung v${info.latestVersion}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                downloadedApk?.takeIf { it.exists() }?.let { apkFile ->
                                    OutlinedButton(
                                        onClick = { UpdateChecker.installApk(this@MainActivity, apkFile) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Pasang", color = accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else if (info.latestVersion.isNotBlank()) {
                            Text("Sudah menggunakan versi terbaru.", color = textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickExampleRow(
    title: String,
    examples: String,
    subtleBg: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(subtleBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(examples, color = textSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PermissionPillButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    subtleBg: Color,
    textPrimary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(subtleBg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = textPrimary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}
