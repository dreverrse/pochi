package com.pochi.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PochiPrefs.init(this)

        setContent {
            var apiKey by remember { mutableStateOf(PochiPrefs.getApiKey(this)) }
            val offsetX by PochiPrefs.offsetXFlow.collectAsState()
            val offsetY by PochiPrefs.offsetYFlow.collectAsState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0C))
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                PochiMascot(state = BotState.IDLE, size = 68.dp)

                Text(
                    text = "Pochi 4.0",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Camera Cutout Fit & Outside-Tap Dismiss",
                    color = Color.Gray,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        PochiPrefs.saveApiKey(this@MainActivity, it)
                    },
                    label = { Text("Gemini API Key", color = Color.Gray) },
                    placeholder = { Text("Tempel API Key di sini...", color = Color.DarkGray) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF0A84FF),
                        unfocusedBorderColor = Color(0xFF2C2C2E)
                    )
                )

                // Panel Kalibrasi Posisi Lubang Kamera Real-Time
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF141417))
                        .border(1.dp, Color(0xFF27272A), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎯 Kalibrasi Posisi Lubang Kamera",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { PochiPrefs.saveOffsets(this@MainActivity, 0, 4) }
                        ) {
                            Text("Reset", color = Color(0xFF0A84FF), fontSize = 12.sp)
                        }
                    }

                    Text(
                        text = "Posisi Vertikal (Atas/Bawah): ${offsetY} dp",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = offsetY.toFloat(),
                        onValueChange = { newY ->
                            PochiPrefs.saveOffsets(this@MainActivity, offsetX, newY.roundToInt())
                        },
                        valueRange = -20f..50f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF0A84FF),
                            activeTrackColor = Color(0xFF0A84FF)
                        )
                    )

                    Text(
                        text = "Posisi Horizontal (Kiri/Kanan): ${offsetX} dp",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = offsetX.toFloat(),
                        onValueChange = { newX ->
                            PochiPrefs.saveOffsets(this@MainActivity, newX.roundToInt(), offsetY)
                        },
                        valueRange = -140f..140f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF30D158),
                            activeTrackColor = Color(0xFF30D158)
                        )
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
                            startService(Intent(this@MainActivity, DynamicIslandService::class.java))
                            Toast.makeText(this@MainActivity, "Pochi Island 4.0 Aktif!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aktifkan Pochi Island", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        ApprovalManager.requestApproval(
                            agent = "Pochi Flash",
                            friendlyTitle = "Menjalankan skrip pembersihan cache proyek",
                            rawCommand = "rm -rf ./build/cache && ./gradlew clean"
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9F0A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Simulasi Request Allow / Deny", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        stopService(Intent(this@MainActivity, DynamicIslandService::class.java))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Matikan Pochi Island", color = Color(0xFFFF453A))
                }
            }
        }
    }
}
