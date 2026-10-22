package com.pochi.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Ink = Color(0xFF0A0A0C)
private val Panel = Color(0xFF17171B)
private val Accent = Color(0xFF78A9FF)

class MainActivity : ComponentActivity() {
    private var torchOn by mutableStateOf(false)
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) toggleTorch() else Toast.makeText(this, "Izin kamera diperlukan untuk senter.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent, background = Ink, surface = Panel,
                onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White
            )) {
                var apiKey by remember { mutableStateOf(PochiPrefs.getApiKey(this)) }
                var notes by remember { mutableStateOf(getPreferences(MODE_PRIVATE).getString("notes", "") ?: "") }
                var idea by remember { mutableStateOf("") }
                var showNotes by remember { mutableStateOf(false) }
                var showIdeas by remember { mutableStateOf(false) }
                var islandActive by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }

                Column(
                    Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("POCHI", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
                            Text("Your little everyday assistant", color = Color.Gray, fontSize = 12.sp)
                        }
                        Surface(shape = RoundedCornerShape(50), color = Color(0xFF1C2A24)) {
                            Text("● Ready", color = Color(0xFF6EE7A8), fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }

                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Halo, aku Pochi 👋", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text("Akses cepat untuk hal-hal yang kamu butuhkan.", color = Color.LightGray, fontSize = 12.sp)
                                Button(
                                    onClick = {
                                        if (!Settings.canDrawOverlays(this@MainActivity)) {
                                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                            Toast.makeText(this@MainActivity, "Izinkan Pochi tampil di atas aplikasi lain, lalu aktifkan lagi.", Toast.LENGTH_LONG).show()
                                        } else {
                                            startService(Intent(this@MainActivity, DynamicIslandService::class.java))
                                            islandActive = true
                                            Toast.makeText(this@MainActivity, "Pochi Island diaktifkan", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) { Text("Aktifkan Island") }
                            }
                            PochiMascot(state = BotState.IDLE, size = 68.dp)
                        }
                    }

                    Text("AKSES CEPAT", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(listOf(
                            Tool("📷", "Kamera", "Buka kamera"),
                            Tool(if (torchOn) "🔦" else "🔆", "Senter", if (torchOn) "Sedang menyala" else "Ketuk untuk menyalakan"),
                            Tool("🌦️", "Cuaca", "Lihat prakiraan"),
                            Tool("🎵", "Musik", "Putar musik"),
                            Tool("📝", "Catatan", "Simpan ide penting"),
                            Tool("💡", "Brainstorm", "Kembangkan ide"),
                            Tool("⚙️", "Pengaturan", "API dan kontrol"),
                            Tool(if (islandActive) "⏹️" else "✨", if (islandActive) "Matikan Island" else "Pochi Island", "Kontrol assistant")
                        )) { tool ->
                            ToolCard(tool) {
                                when (tool.title) {
                                    "Kamera" -> try {
                                        startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                                    } catch (_: Exception) { Toast.makeText(this@MainActivity, "Aplikasi kamera tidak tersedia.", Toast.LENGTH_SHORT).show() }
                                    "Senter" -> {
                                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) toggleTorch()
                                        else cameraPermission.launch(Manifest.permission.CAMERA)
                                    }
                                    "Cuaca" -> openUrl("https://www.google.com/search?q=cuaca+hari+ini")
                                    "Musik" -> try {
                                        startActivity(Intent("android.intent.action.MUSIC_PLAYER"))
                                    } catch (_: Exception) {
                                        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com")), "Pilih pemutar musik"))
                                    }
                                    "Catatan" -> { showNotes = !showNotes; showIdeas = false }
                                    "Brainstorm" -> { showIdeas = !showIdeas; showNotes = false }
                                    "Pengaturan" -> showSettings = !showSettings
                                    else -> {
                                        if (islandActive) {
                                            stopService(Intent(this@MainActivity, DynamicIslandService::class.java))
                                            islandActive = false
                                            Toast.makeText(this@MainActivity, "Pochi Island dimatikan", Toast.LENGTH_SHORT).show()
                                        } else if (!Settings.canDrawOverlays(this@MainActivity)) {
                                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                        } else {
                                            startService(Intent(this@MainActivity, DynamicIslandService::class.java))
                                            islandActive = true
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = showNotes || showIdeas || showSettings,
                        enter = fadeIn(tween(180, easing = FastOutSlowInEasing)) + slideInVertically(tween(220)) { it / 8 },
                        exit = fadeOut(tween(130)) + slideOutVertically(tween(160)) { it / 10 }
                    ) {
                        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                when {
                                    showNotes -> {
                                        Text("Catatan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        OutlinedTextField(
                                            value = notes, onValueChange = {
                                                notes = it
                                                getPreferences(MODE_PRIVATE).edit().putString("notes", it).apply()
                                            }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                                            placeholder = { Text("Tulis catatanmu di sini…") },
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        Text("Tersimpan otomatis di perangkat ini.", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    showIdeas -> {
                                        Text("Brainstorming ide", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        OutlinedTextField(value = idea, onValueChange = { idea = it },
                                            modifier = Modifier.fillMaxWidth(), minLines = 2,
                                            placeholder = { Text("Contoh: ide aplikasi produktivitas…") },
                                            shape = RoundedCornerShape(14.dp))
                                        Button(onClick = {
                                            val seed = idea.trim()
                                            if (seed.isNotEmpty()) {
                                                notes = (notes + if (notes.isBlank()) "" else "\n\n") + "💡 Ide: " + seed +
                                                    "\n• Masalah apa yang diselesaikan?\n• Siapa yang paling terbantu?\n• Buat versi sederhana dalam 1 hari.\n• Apa langkah pertama yang bisa diuji?"
                                                getPreferences(MODE_PRIVATE).edit().putString("notes", notes).apply()
                                                showNotes = true; showIdeas = false
                                            }
                                        }, enabled = idea.isNotBlank()) { Text("Buat kerangka ide & simpan") }
                                        Text("Kerangka awal disimpan ke Catatan agar mudah dikembangkan.", color = Color.Gray, fontSize = 11.sp)
                                    }
                                    else -> {
                                        Text("Pengaturan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        OutlinedTextField(value = apiKey, onValueChange = {
                                            apiKey = it; PochiPrefs.saveApiKey(this@MainActivity, it)
                                        }, label = { Text("Gemini API Key") }, placeholder = { Text("Tempel API key…") },
                                            visualTransformation = PasswordVisualTransformation(), singleLine = true,
                                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                                        Text("Kunci disimpan lokal di perangkat. Jangan unggah API key ke GitHub.", color = Color.Gray, fontSize = 11.sp)
                                        OutlinedButton(onClick = { openUrl("https://aistudio.google.com/app/apikey") }) { Text("Dapatkan API key") }
                                        OutlinedButton(onClick = {
                                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                                        }) { Text("Izin tampil di atas aplikasi") }
                                    }
                                }
                            }
                        }
                    }
                    Text("Pochi tidak dapat mematikan daya HP secara langsung tanpa hak sistem khusus.", color = Color.DarkGray, fontSize = 10.sp)
                }
            }
        }
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Tidak dapat membuka tautan.", Toast.LENGTH_SHORT).show() }
    }

    private fun toggleTorch() {
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: run { Toast.makeText(this, "Senter tidak tersedia di perangkat ini.", Toast.LENGTH_SHORT).show(); return }
            manager.setTorchMode(cameraId, !torchOn)
            torchOn = !torchOn
        } catch (_: Exception) {
            Toast.makeText(this, "Senter tidak dapat diubah. Periksa izin kamera.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        if (torchOn) try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.firstOrNull()?.let { manager.setTorchMode(it, false) }
        } catch (_: Exception) {}
        super.onDestroy()
    }
}

private data class Tool(val emoji: String, val title: String, val subtitle: String)

@Composable
private fun ToolCard(tool: Tool, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(tool.emoji, fontSize = 25.sp)
            Text(tool.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(tool.subtitle, fontSize = 10.sp, color = Color.Gray, lineHeight = 14.sp)
        }
    }
}
