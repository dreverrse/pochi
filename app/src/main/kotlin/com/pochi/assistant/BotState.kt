package com.pochi.assistant

import androidx.compose.ui.graphics.Color

enum class BotState(val accentColor: Color, val statusLabel: String) {
    IDLE(Color(0xFF8E8E93), "Pochi siap"),
    WAITING(Color(0xFFFF9F0A), "Menunggu perintah..."),
    WORKING(Color(0xFF0A84FF), "Pochi berpikir..."),
    DONE(Color(0xFF30D158), "Berhasil!"),
    ANNOYED(Color(0xFFFF453A), "Jangan dicolek terus!")
}
