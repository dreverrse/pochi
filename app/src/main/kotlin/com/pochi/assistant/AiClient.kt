package com.pochi.assistant

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class AiProvider(
    val label: String,
    val defaultModel: String,
    val keyHint: String
) {
    GEMINI("Gemini", "gemini-2.5-flash", "AIza..."),
    OPENROUTER("OpenRouter", "openai/gpt-4o-mini", "sk-or-..."),
    OPENCODE("OpenCode", "glm-5.3-flash", "API key OpenCode Console")
}

object AiClient {
    private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val OPENCODE_URL = "https://opencode.ai/inference/openai/v1/chat/completions"

    suspend fun ask(
        provider: AiProvider,
        apiKey: String,
        model: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        val modelName = model.ifBlank { provider.defaultModel }
        when (provider) {
            AiProvider.GEMINI -> askGemini(apiKey, modelName, prompt)
            AiProvider.OPENROUTER -> askOpenAiCompatible(
                url = OPENROUTER_URL,
                apiKey = apiKey,
                model = modelName,
                prompt = prompt,
                referer = "https://pochi.app"
            )
            AiProvider.OPENCODE -> askOpenAiCompatible(
                url = OPENCODE_URL,
                apiKey = apiKey,
                model = modelName,
                prompt = prompt,
                referer = null
            )
        }
    }

    private suspend fun askGemini(apiKey: String, modelName: String, prompt: String): String {
        val response = GenerativeModel(modelName = modelName, apiKey = apiKey)
            .generateContent(prompt)
        return response.text?.trim().takeUnless { it.isNullOrBlank() } ?: "Tidak ada jawaban."
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

            return content.trim().takeUnless { it.isNullOrBlank() } ?: "Tidak ada jawaban."
        } finally {
            connection.disconnect()
        }
    }
}
