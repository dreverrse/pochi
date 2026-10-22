package com.pochi.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ApprovalRequest(
    val id: String,
    val agentName: String,
    val friendlyTitle: String,
    val rawCommand: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val expiresInSeconds: Int = 120
)

object ApprovalManager {
    private val _activeRequest = MutableStateFlow<ApprovalRequest?>(null)
    val activeRequest: StateFlow<ApprovalRequest?> = _activeRequest

    private val _lastDecision = MutableStateFlow<String?>(null)
    val lastDecision: StateFlow<String?> = _lastDecision

    private val _dismissSignal = MutableStateFlow(0L)
    val dismissSignal: StateFlow<Long> = _dismissSignal

    fun triggerOutsideDismiss() {
        _dismissSignal.value = System.currentTimeMillis()
    }

    fun requestApproval(agent: String, friendlyTitle: String, rawCommand: String) {
        _lastDecision.value = null
        _activeRequest.value = ApprovalRequest(
            id = System.currentTimeMillis().toString(),
            agentName = agent,
            friendlyTitle = friendlyTitle,
            rawCommand = rawCommand
        )
    }

    fun confirmAllow() {
        val req = _activeRequest.value ?: return
        _lastDecision.value = "Diizinkan: ${req.friendlyTitle}"
        _activeRequest.value = null
    }

    fun confirmDeny(reason: String = "Ditolak oleh pengguna") {
        val req = _activeRequest.value ?: return
        _lastDecision.value = "$reason (${req.friendlyTitle})"
        _activeRequest.value = null
    }
}
