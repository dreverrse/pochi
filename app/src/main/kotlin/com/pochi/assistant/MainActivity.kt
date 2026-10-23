package com.pochi.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            var provider by remember { mutableStateOf(PochiPrefs.getProvider(this)) }
            var apiKey by remember { mutableStateOf(PochiPrefs.getApiKey(this, provider)) }
            var model by remember { mutableStateOf(PochiPrefs.getModel(this, provider)) }
            var previewEmote by remember { mutableStateOf(PochiEmote.NORMAL) }
            val offsetX by PochiPrefs.offsetXFlow.collectAsState()
            val offsetY by PochiPrefs.offsetYFlow.collectAsState()

            LaunchedEffect(provider) {
                apiKey = PochiPrefs.getApiKey(this@MainActivity, provider)
                model = PochiPrefs.getModel(this@MainActivity, provider)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFAFBFC))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                PochiMascot(
                    state = BotState.IDLE,
                    emoteOverride = previewEmote,
                    size = 84.dp,
                    onPoke = {
                        previewEmote = PochiEmote.entries.random()
                    }
                )

                Text(
                    text = "Pochi 5.4.0",
                    color = Color(0xFF111827),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Minimalis • 2D • 36 Ekspresi • Multi-Provider AI",
                    color = Color(0xFF8E8E93),
                    fontSize = 12.sp
                )

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
                                .background(if (active) Color(0xFF0A84FF) else Color.White)
                                .border(
                                    1.dp,
                                    if (active) Color(0xFF0A84FF) else Color(0xFFE5E7EB),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    provider = entry
                                    PochiPrefs.setProvider(this@MainActivity, entry)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = entry.label,
                                color = if (active) Color.White else Color(0xFF111827),
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
                    label = { Text("${provider.label} API Key") },
                    placeholder = { Text(provider.keyHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF111827),
                        unfocusedTextColor = Color(0xFF111827),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF0A84FF),
                        unfocusedBorderColor = Color(0xFFE5E7EB)
                    )
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        PochiPrefs.saveModel(this@MainActivity, provider, it)
                    },
                    label = { Text("Model ${provider.label}") },
                    placeholder = { Text(provider.defaultModel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF111827),
                        unfocusedTextColor = Color(0xFF111827),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF0A84FF),
                        unfocusedBorderColor = Color(0xFFE5E7EB)
                    )
                )

                if (provider == AiProvider.OPENCODE) {
                    Text(
                        text = "Catatan: model gratis OpenCode (mis. big-pickle) hanya bisa dipakai dari dalam OpenCode. Gunakan model berbayar + kredit.",
                        color = Color(0xFF8E8E93),
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }

                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                            Toast.makeText(
                                this@MainActivity,
                                "Aktifkan izin 'Tampilkan di atas aplikasi lain' untuk Pochi",
                                Toast.LENGTH_LONG
                            ).show()
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        } else {
                            stopService(Intent(this@MainActivity, DynamicIslandService::class.java))
                            startForegroundService(Intent(this@MainActivity, DynamicIslandService::class.java))
                            Toast.makeText(this@MainActivity, "Pochi Island 5.4.0 Aktif!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aktifkan Pochi Island", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            ApprovalManager.requestApproval(
                                agent = "Pochi",
                                friendlyTitle = "Menjalankan skrip pembersihan cache proyek",
                                rawCommand = "rm -rf ./build/cache && ./gradlew clean"
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🔒 Tes Approval", color = Color(0xFF111827), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            stopService(Intent(this@MainActivity, DynamicIslandService::class.java))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Matikan Island", color = Color(0xFFFF453A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎯 Kalibrasi Posisi Island",
                            color = Color(0xFF111827),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { PochiPrefs.saveOffsets(this@MainActivity, 0, 4) }) {
                            Text("Reset", color = Color(0xFF0A84FF), fontSize = 12.sp)
                        }
                    }

                    Text("Vertikal (Atas/Bawah): ${offsetY} dp", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    Slider(
                        value = offsetY.toFloat(),
                        onValueChange = { PochiPrefs.saveOffsets(this@MainActivity, offsetX, it.roundToInt()) },
                        valueRange = -20f..50f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF0A84FF), activeTrackColor = Color(0xFF0A84FF))
                    )

                    Text("Horizontal (Kiri/Kanan): ${offsetX} dp", color = Color(0xFF8E8E93), fontSize = 11.sp)
                    Slider(
                        value = offsetX.toFloat(),
                        onValueChange = { PochiPrefs.saveOffsets(this@MainActivity, it.roundToInt(), offsetY) },
                        valueRange = -140f..140f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF30D158), activeTrackColor = Color(0xFF30D158))
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val screenAwareness by PochiPrefs.screenAwarenessFlow.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👁️ Screen Awareness",
                            color = Color(0xFF111827),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = screenAwareness,
                            onCheckedChange = { enabled ->
                                PochiPrefs.setScreenAwareness(this@MainActivity, enabled)
                                if (enabled) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Aktifkan 'Pochi Screen Awareness' di daftar layanan",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF0A84FF)
                            )
                        )
                    }
                    Text(
                        text = "Opsional. Pochi membaca teks layar untuk memberi saran. Bisa dimatikan kapan saja.",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "🔔 Akses Notifikasi",
                        color = Color(0xFF111827),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Izinkan Pochi membaca notifikasi agar bisa meringkas dan menjalankan aksi otomatis (dengan persetujuan).",
                        color = Color(0xFF8E8E93),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    OutlinedButton(
                        onClick = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            Toast.makeText(
                                this@MainActivity,
                                "Aktifkan 'Pochi' di daftar akses notifikasi",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Buka Setelan Akses Notifikasi", color = Color(0xFF0A84FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "😺 36 Emote Pochi (Ketuk untuk Preview)",
                        color = Color(0xFF111827),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val rows = PochiEmote.entries.chunked(4)
                    rows.forEach { rowEmotes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowEmotes.forEach { emote ->
                                val isSelected = previewEmote == emote
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) Color(0xFF0A84FF).copy(alpha = 0.1f) else Color(0xFFFAFBFC))
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF0A84FF) else Color(0xFFE5E7EB),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { previewEmote = emote }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                ) {
                                    PochiMascot(state = BotState.IDLE, emoteOverride = emote, size = 38.dp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${emote.number}. ${emote.title}",
                                        color = Color(0xFF111827),
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
