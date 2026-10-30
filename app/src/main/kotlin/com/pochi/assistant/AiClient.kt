package com.pochi.assistant

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AiProvider(
    val label: String,
    val defaultModel: String,
    val keyHint: String
) {
    GEMINI("Gemini", "gemini-2.5-flash", "AIza..."),
    OPENROUTER("OpenRouter", "openai/gpt-4o-mini", "sk-or-..."),
    OPENCODE("OpenCode", "glm-5.3-flash", "API key OpenCode"),
    INCEPTION("InceptionLabs", "mercury-3", "il_..."),
    LAPTOP("Laptop", "pochi-lokal", "Kunci virtual Pochi Gateway")
}

object AiClient {
    private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val OPENCODE_URL = "https://opencode.ai/inference/openai/v1/chat/completions"
    private const val INCEPTION_URL = "https://api.inceptionlabs.ai/v1/chat/completions"

    suspend fun ask(
        provider: AiProvider,
        apiKey: String,
        model: String,
        prompt: String,
        contextNote: String = ""
    ): String = withContext(Dispatchers.IO) {
        val modelName = model.ifBlank { provider.defaultModel }
        val nowStr = SimpleDateFormat("EEEE, dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date())
        val fullPrompt = buildString {
            append(
                "Kamu adalah Pochi, AI Agent pribadi yang cerdas, setia, dan tanggap di HP Tuan Andre. " +
                    "Kamu SELALU memanggil pengguna dengan sebutan 'Tuan Andre' secara konsisten, hormat, hangat, dan akrab. " +
                    "Gaya bicaramu natural, taktis, ekspresif, dan langsung ke inti solusi seperti AI agent kelas atas—bisa ikut senang, tersenyum, waspada, atau fokus penuh. " +
                    "Jangan terdengar kaku seperti robot template, hindari basa-basi panjang, dan jangan gunakan simbol markdown (**). " +
                    "Jawab singkat padat maksimal 2-3 kalimat. Waktu sekarang: $nowStr. " +
                    "Sertakan ekspresi wajahmu di akhir jawaban dengan tag [EMOTE:NAMA] (pilih satu: " +
                    "NORMAL, HAPPY, LAUGHING, SMILING, WINK_LEFT, EXCITED, LOVE, BLUSH, CUTE, SURPRISED, SHOCK, " +
                    "CONFUSED, THINKING, HMM, SUSPICIOUS, ANGRY, ANNOYED, TIRED, SLEEPY, CALM, HAPPY_TEARS, CRYING, " +
                    "SCARED, EMBARRASSED, OK, NO, BYE, HI, GOOD_NIGHT, GOOD_MORNING, SUCCESS, FOCUS). " +
                    "Jika diminta melakukan sesuatu atau menyimpan memori, tambahkan juga [ACTION:TIPE:ARGUMEN]. Pilihan TIPE: " +
                    "OPEN_APP:nama_aplikasi, OPEN_URL:https://..., SEARCH_WEB:kata_kunci, YOUTUBE:kata_kunci, " +
                    "SET_TIMER:detik, SET_ALARM:HH:MM|label, CALL:nama_atau_nomor, SMS:nama_atau_nomor|pesan, " +
                    "WHATSAPP:nama_atau_nomor|pesan, CALENDAR:judul|menit_dari_sekarang|durasi_menit, " +
                    "MAPS:lokasi, CLIPBOARD:teks, SAVE_NOTE:isi_catatan, REMEMBER:kategori|isi_memori, SETTINGS:WIFI_atau_BLUETOOTH. " +
                    "Jangan tulis [ACTION:...] jika hanya mengobrol santai."
            )
            if (contextNote.isNotBlank()) {
                append("\nKonteks & Memori Tuan Andre: ")
                append(contextNote)
            }
            append("\n\nTuan Andre: ")
            append(prompt)
        }
        val raw = when (provider) {
            AiProvider.GEMINI -> askGemini(apiKey, modelName, fullPrompt)
            AiProvider.OPENROUTER -> askOpenAiCompatible(
                url = OPENROUTER_URL,
                apiKey = apiKey,
                model = modelName,
                prompt = fullPrompt,
                referer = "https://pochi.app"
            )
            AiProvider.OPENCODE -> askOpenAiCompatible(
                url = OPENCODE_URL,
                apiKey = apiKey,
                model = modelName,
                prompt = fullPrompt,
                referer = null
            )
            AiProvider.INCEPTION -> askOpenAiCompatible(
                url = INCEPTION_URL,
                apiKey = apiKey,
                model = modelName,
                prompt = fullPrompt,
                referer = null
            )
            AiProvider.LAPTOP -> askOpenAiCompatible(
                url = requirePrivateGatewayUrl(PochiPrefs.gatewayUrlFlow.value) + "/chat/completions",
                apiKey = apiKey,
                model = modelName,
                prompt = fullPrompt,
                referer = null
            )
        }
        raw.replace("**", "").trim()
    }

    /**
     * Tes koneksi ke Pochi Gateway di laptop (redesign Tahap 3b):
     * GET {base}/models bergaya OpenAI. Mengembalikan nama model pertama
     * yang ditawarkan gateway, atau pesan kesalahan yang bisa dibaca.
     */
    suspend fun checkGateway(baseUrl: String, apiKey: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = requirePrivateGatewayUrl(baseUrl.trim().trimEnd('/')) + "/models"
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 8_000
                    if (apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                }
                try {
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) error("HTTP $code")
                    val firstModel = JSONObject(body)
                        .optJSONArray("data")
                        ?.optJSONObject(0)
                        ?.optString("id")
                        .orEmpty()
                    if (firstModel.isNotBlank()) "Terhubung • model: $firstModel" else "Terhubung ke gateway"
                } finally {
                    connection.disconnect()
                }
            }
        }

    /**
     * Penjaga alamat gateway laptop: karena gateway lokal berjalan lewat
     * HTTP (manifest membuka cleartext), URL gateway hanya boleh menunjuk
     * alamat loopback/privat/LAN/Tailscale — tidak pernah internet publik.
     * Mengembalikan base URL bersih (tanpa garis miring akhir).
     */
    private fun requirePrivateGatewayUrl(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/')
        val host = runCatching { URL(clean).host.lowercase() }.getOrDefault("")
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        val isIpv4 = octets.size == 4
        val isPrivateIp = isIpv4 && (
            octets[0] == 10 ||
                octets[0] == 127 ||
                (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168) ||
                (octets[0] == 100 && octets[1] in 64..127)
            )
        val allowed = host == "localhost" || host.endsWith(".local") ||
            (!isIpv4 && !host.contains('.')) || isPrivateIp
        require(allowed) {
            "Alamat Pochi Gateway harus alamat privat/LAN (mis. 100.64.0.1 atau 192.168.1.10)"
        }
        return clean
    }

    private suspend fun askGemini(apiKey: String, modelName: String, prompt: String): String {
        val response = GenerativeModel(modelName = modelName, apiKey = apiKey)
            .generateContent(prompt)
        return response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "Belum ada respons."
    }

    private fun askOpenAiCompatible(
        url: String,
        apiKey: String,
        model: String,
        prompt: String,
        referer: String?
    ): String {
        val payload = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    }
                )
            )
        }.toString()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            if (referer != null) {
                setRequestProperty("HTTP-Referer", referer)
                setRequestProperty("X-Title", "Pochi")
            }
        }

        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull()
                error("HTTP $code: ${apiMessage?.takeIf { it.isNotBlank() } ?: body.take(220)}")
            }

            val content = JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()

            return content.trim().takeUnless { it.isNullOrBlank() } ?: "Belum ada respons."
        } finally {
            connection.disconnect()
        }
    }
}
