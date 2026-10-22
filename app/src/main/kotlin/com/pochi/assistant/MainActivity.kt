package com.pochi.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var apiKey by remember { mutableStateOf(PochiPrefs.getApiKey(this)) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0C))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PochiMascot(state = BotState.IDLE, size = 72.dp)

                Text(
                    text = "Pochi 2.0",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Smooth Multifunction Dynamic Island Hub",
                    color = Color.Gray,
                    fontSize = 13.sp
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
                            startService(Intent(this@MainActivity, DynamicIslandService::class.java))
                            Toast.makeText(this@MainActivity, "Pochi Island 2.0 Aktif!", Toast.LENGTH_SHORT).show()
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
