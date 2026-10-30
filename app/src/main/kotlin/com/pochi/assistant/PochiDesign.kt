package com.pochi.assistant

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PochiDesign — token desain redesign Android (Tahap 1).
 *
 * Sumber kebenaran tunggal untuk warna island & maskot, diambil dari
 * referensi pemilik: blueprint ekspresi Pochi (wajah squircle biru-navy
 * gelap, mata putih bercahaya, aksen violet, gelombang suara lavender
 * di sisi wajah) dan kartu dashboard gelap (permukaan #141416, garis
 * rambut #26262B). File lama yang masih memakai warna inline diarahkan
 * ke sini secara bertahap.
 */
object PochiDesign {
    // Fondasi gelap
    val IslandBlack = Color(0xFF000000)      // badan notch/island, hitam murni
    val Void = Color(0xFF05030A)             // latar terdalam di balik island
    val CardSurface = Color(0xFF141416)      // permukaan kartu dashboard/panel
    val CardSurfaceHigh = Color(0xFF1C1C1F)  // kartu di atas kartu
    val Hairline = Color(0xFF26262B)         // garis pemisah tipis

    // Wajah Pochi sesuai blueprint: navy gelap dengan tepi violet
    val FaceBase = Color(0xFF14142B)         // isi squircle wajah
    val FaceRim = Color(0xFF4B3FA6)          // garis tepi violet wajah
    val EyeWhite = Color(0xFFFFFFFF)         // mata putih bercahaya

    // Aksen utama redesign
    val AccentViolet = Color(0xFF7C5CFF)     // violet utama blueprint
    val AccentVioletSoft = Color(0xFF9D8CFF) // violet lembut (gelombang suara)
    val AccentCyan = Color(0xFF38BDF8)       // air mata / info
    val AccentPink = Color(0xFFFF6B9D)       // love / shy
    val AccentRed = Color(0xFFFF3B30)        // angry / error

    // Teks
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF8E8E93)

    // Status (diselaraskan dengan BotState & tema iOS yang sudah ada)
    val StatusGreen = Color(0xFF34C759)
    val StatusAmber = Color(0xFFFF9500)
    val StatusBlue = Color(0xFF007AFF)
    val SpotifyGreen = Color(0xFF1DB954)
}

/**
 * 15 ekspresi kanonis dari "POCHI — EXPRESSION BLUEPRINT" milik pemilik.
 *
 * Ini adalah kosakata ekspresi resmi redesign. Renderer mata yang sudah
 * ada (36 [PochiEmote] di PochiMascot.kt) tetap dipakai lewat [toLegacy]
 * agar tidak ada perilaku lama yang rusak; ekspresi blueprint yang belum
 * punya gambar persis dipetakan ke emote lama yang paling dekat bentuknya.
 */
enum class PochiFace(
    val number: Int,
    val titleEn: String,
    val titleId: String,
    val accent: Color
) {
    PLAYFUL(1, "Playful (Wink)", "Iseng (Kedip)", PochiDesign.AccentVioletSoft),
    ANGRY(2, "Angry", "Marah", PochiDesign.AccentRed),
    NEUTRAL(3, "Neutral (Idle)", "Netral (Diam)", PochiDesign.EyeWhite),
    HAPPY(4, "Happy", "Senang", PochiDesign.EyeWhite),
    EXCITED(5, "Excited", "Antusias", Color(0xFFFFD60A)),
    LAUGHING(6, "Laughing", "Tertawa", Color(0xFFFFD60A)),
    SAD(7, "Sad", "Sedih", PochiDesign.AccentCyan),
    CRYING(8, "Crying", "Menangis", PochiDesign.AccentCyan),
    ANNOYED(9, "Annoyed", "Kesal", PochiDesign.EyeWhite),
    SURPRISED(10, "Surprised", "Terkejut", Color(0xFFFFD60A)),
    CONFUSED(11, "Confused", "Bingung", PochiDesign.AccentVioletSoft),
    SHY(12, "Shy", "Malu", PochiDesign.AccentPink),
    SLEEPY(13, "Sleepy", "Mengantuk", PochiDesign.AccentVioletSoft),
    THINKING(14, "Thinking", "Berpikir", PochiDesign.AccentVioletSoft),
    LOVE(15, "Love", "Jatuh Cinta", PochiDesign.AccentPink);

    /** Pemetaan ke renderer mata lama (PochiMascot) — pendekatan terdekat. */
    fun toLegacy(): PochiEmote = when (this) {
        PLAYFUL -> PochiEmote.WINK_LEFT
        ANGRY -> PochiEmote.ANGRY
        NEUTRAL -> PochiEmote.NORMAL
        HAPPY -> PochiEmote.HAPPY
        EXCITED -> PochiEmote.EXCITED
        LAUGHING -> PochiEmote.LAUGHING
        SAD -> PochiEmote.TIRED
        CRYING -> PochiEmote.CRYING
        ANNOYED -> PochiEmote.ANNOYED
        SURPRISED -> PochiEmote.SURPRISED
        CONFUSED -> PochiEmote.CONFUSED
        SHY -> PochiEmote.BLUSH
        SLEEPY -> PochiEmote.SLEEPY
        THINKING -> PochiEmote.THINKING
        LOVE -> PochiEmote.LOVE
    }

    companion object {
        /** Kebalikan kasar [toLegacy]: emote lama -> wajah blueprint terdekat. */
        fun fromLegacy(emote: PochiEmote): PochiFace = when (emote) {
            PochiEmote.WINK_LEFT, PochiEmote.OK, PochiEmote.BYE -> PLAYFUL
            PochiEmote.ANGRY -> ANGRY
            PochiEmote.NORMAL, PochiEmote.CALM, PochiEmote.WORKING,
            PochiEmote.LOADING, PochiEmote.FOCUS -> NEUTRAL
            PochiEmote.HAPPY, PochiEmote.SMILING, PochiEmote.CUTE,
            PochiEmote.SUCCESS, PochiEmote.PET_POKE, PochiEmote.HI,
            PochiEmote.GOOD_MORNING -> HAPPY
            PochiEmote.EXCITED -> EXCITED
            PochiEmote.LAUGHING -> LAUGHING
            PochiEmote.TIRED -> SAD
            PochiEmote.CRYING, PochiEmote.HAPPY_TEARS -> CRYING
            PochiEmote.ANNOYED, PochiEmote.SUSPICIOUS, PochiEmote.NO,
            PochiEmote.ERROR, PochiEmote.SCARED -> ANNOYED
            PochiEmote.SURPRISED, PochiEmote.SHOCK -> SURPRISED
            PochiEmote.CONFUSED, PochiEmote.HMM -> CONFUSED
            PochiEmote.BLUSH, PochiEmote.EMBARRASSED -> SHY
            PochiEmote.SLEEPY, PochiEmote.GOOD_NIGHT -> SLEEPY
            PochiEmote.THINKING -> THINKING
            PochiEmote.LOVE -> LOVE
        }

        /** Ekspresi default tiap status bot, mengikuti makna blueprint. */
        fun forState(state: BotState): PochiFace = when (state) {
            BotState.IDLE -> NEUTRAL
            BotState.WAITING -> THINKING
            BotState.WORKING -> THINKING
            BotState.DONE -> HAPPY
            BotState.APPROVAL -> SURPRISED
            BotState.ANNOYED -> ANNOYED
        }
    }
}

/**
 * Gelombang suara lavender `(( ))` khas blueprint, digambar di sisi wajah.
 * Murni dekoratif: dua-tiga busur konsentris menghadap keluar.
 */
@Composable
fun PochiSoundWaves(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    flip: Boolean = false,
    color: Color = PochiDesign.AccentVioletSoft
) {
    Canvas(modifier = modifier.size(width = height * 0.45f, height = height)) {
        // Sisi kiri wajah: busur "(" (membuka ke kanan, ke arah wajah).
        // Sisi kanan wajah (flip): busur ")" (membuka ke kiri).
        val cx = if (flip) size.width * 0.15f else size.width * 0.85f
        val cy = size.height / 2f
        val base = size.height * 0.16f
        for (i in 0..2) {
            val r = base * (i + 1)
            val start = if (flip) -60f else 120f
            drawArc(
                color = color.copy(alpha = 0.85f - i * 0.22f),
                startAngle = start,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                style = Stroke(width = (size.height * 0.045f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Maskot wajah blueprint lengkap: gelombang suara kiri-kanan + wajah
 * squircle navy bermata putih, memakai renderer [PochiMascot] yang ada.
 */
@Composable
fun PochiFaceMascot(
    face: PochiFace,
    size: Dp = 44.dp,
    showWaves: Boolean = true,
    onPoke: (() -> Unit)? = null,
    onPet: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showWaves) PochiSoundWaves(height = size * 0.62f)
        PochiMascot(
            state = BotState.IDLE,
            emoteOverride = face.toLegacy(),
            size = size,
            showBlackBacking = true,
            onPoke = onPoke,
            onPet = onPet
        )
        if (showWaves) PochiSoundWaves(height = size * 0.62f, flip = true)
    }
}
