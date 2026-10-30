package com.pochi.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * PochiAgents — jembatan agen coding ala Coucou (Tahap 13).
 *
 * Pochi di Android berbicara dengan server jembatan kecil di
 * laptop (`desktop/bridge/pochi_bridge.py`, port 8788, host yang
 * sama dengan Pochi Gateway). Jembatan menerima hook dari agen
 * coding (Claude Code, dst.) dan menyajikan:
 *
 *   GET  /pochi/agents   -> { sessions: [...], pendingApproval }
 *   POST /pochi/approve  -> { id, decision: allow|deny|always }
 *
 * Sesi tampil sebagai ticker di kapsul & halaman Sesi di panel;
 * permintaan izin agen muncul sebagai kartu Allow/Deny/Always
 * di island, persis model Coucou — keputusannya diteruskan ke
 * agen yang sedang menunggu di laptop.
 */
data class AgentSession(
    val id: String,
    val agent: String,
    val project: String,
    val state: String,
    val action: String,
    val file: String,
    val plus: Int,
    val minus: Int,
    val updatedAtMs: Long
) {
    val isWorking: Boolean get() = state == "WORKING"
    val isDone: Boolean get() = state == "DONE"
}

data class AgentApproval(
    val id: String,
    val agent: String,
    val tool: String,
    val summary: String,
    val createdAtMs: Long
)

object AgentBridge {

    const val BRIDGE_PORT = 8788

    private val _sessions = MutableStateFlow<List<AgentSession>>(emptyList())
    val sessionsFlow: StateFlow<List<AgentSession>> = _sessions

    private val _approval = MutableStateFlow<AgentApproval?>(null)
    val approvalFlow: StateFlow<AgentApproval?> = _approval

    private val _online = MutableStateFlow(false)
    val onlineFlow: StateFlow<Boolean> = _online

    /** Alamat jembatan diturunkan dari URL gateway (host sama). */
    fun bridgeBase(gatewayUrl: String): String? = runCatching {
        val uri = URI(gatewayUrl.trim())
        val host = uri.host ?: return null
        "http://$host:$BRIDGE_PORT"
    }.getOrNull()

    private fun httpGet(urlText: String): String? = runCatching {
        val conn = URL(urlText).openConnection() as HttpURLConnection
        conn.connectTimeout = 2500
        conn.readTimeout = 2500
        conn.requestMethod = "GET"
        try {
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    private fun httpPostJson(urlText: String, body: JSONObject): Boolean = runCatching {
        val conn = URL(urlText).openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            conn.responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    /** Satu siklus ambil status dari jembatan. */
    suspend fun pollOnce(gatewayUrl: String) {
        val base = bridgeBase(gatewayUrl) ?: run {
            _online.value = false
            return
        }
        val body = withContext(Dispatchers.IO) { httpGet("$base/pochi/agents") }
        if (body == null) {
            _online.value = false
            return
        }
        runCatching {
            val root = JSONObject(body)
            val arr = root.optJSONArray("sessions")
            val list = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        add(
                            AgentSession(
                                id = o.optString("id"),
                                agent = o.optString("agent", "Agent"),
                                project = o.optString("project", ""),
                                state = o.optString("state", "IDLE"),
                                action = o.optString("action", ""),
                                file = o.optString("file", ""),
                                plus = o.optInt("plus", 0),
                                minus = o.optInt("minus", 0),
                                updatedAtMs = o.optLong("updated", 0L)
                            )
                        )
                    }
                }
            }
            _sessions.value = list
            val ap = root.optJSONObject("pendingApproval")
            _approval.value = if (ap != null) {
                AgentApproval(
                    id = ap.optString("id"),
                    agent = ap.optString("agent", "Agent"),
                    tool = ap.optString("tool", ""),
                    summary = ap.optString("summary", ""),
                    createdAtMs = ap.optLong("created", 0L)
                )
            } else {
                null
            }
            _online.value = true
        }.onFailure {
            _online.value = false
        }
    }

    /** Kirim keputusan izin pengguna kembali ke agen di laptop. */
    suspend fun decide(gatewayUrl: String, approvalId: String, decision: String): Boolean {
        val base = bridgeBase(gatewayUrl) ?: return false
        val ok = withContext(Dispatchers.IO) {
            httpPostJson(
                "$base/pochi/approve",
                JSONObject()
                    .put("id", approvalId)
                    .put("decision", decision)
            )
        }
        if (ok) {
            _approval.value = null
        }
        return ok
    }

    /** Loop polling selama island hidup (dipanggil dari service). */
    suspend fun runPolling(gatewayUrlProvider: () -> String) {
        while (isActiveContext()) {
            runCatching { pollOnce(gatewayUrlProvider()) }
            delay(3000)
        }
    }

    private suspend fun isActiveContext(): Boolean =
        kotlinx.coroutines.currentCoroutineContext().isActive
}
