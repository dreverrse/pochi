package com.pochi.assistant

import android.content.Context
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

private data class PendingAction(val context: Context, val action: AgentAction)

object ApprovalManager {
    private val _activeRequest = MutableStateFlow<ApprovalRequest?>(null)
    val activeRequest: StateFlow<ApprovalRequest?> = _activeRequest

    private val _lastDecision = MutableStateFlow<String?>(null)
    val lastDecision: StateFlow<String?> = _lastDecision

    private val _dismissSignal = MutableStateFlow(0L)
    val dismissSignal: StateFlow<Long> = _dismissSignal

    private var pendingAction: PendingAction? = null

    fun triggerOutsideDismiss() {
        _dismissSignal.value = System.currentTimeMillis()
    }

    fun requestApproval(agent: String, friendlyTitle: String, rawCommand: String) {
        pendingAction = null
        _lastDecision.value = null
        _activeRequest.value = ApprovalRequest(
            id = System.currentTimeMillis().toString(),
            agentName = agent,
            friendlyTitle = friendlyTitle,
            rawCommand = rawCommand
        )
    }

    fun requestAction(context: Context, action: AgentAction) {
        pendingAction = PendingAction(context.applicationContext, action)
        _lastDecision.value = null
        _activeRequest.value = ApprovalRequest(
            id = System.currentTimeMillis().toString(),
            agentName = "Pochi",
            friendlyTitle = PochiAgent.describe(context, action),
            rawCommand = "pochi-agent ${action.type}:${action.argument}"
        )
    }

    fun confirmAllow() {
        val req = _activeRequest.value ?: return
        val action = pendingAction
        pendingAction = null
        val result = action?.let { PochiAgent.execute(it.context, it.action) }
        _lastDecision.value = if (result != null) {
            "Diizinkan: ${req.friendlyTitle}. $result"
        } else {
            "Diizinkan: ${req.friendlyTitle}"
        }
        _activeRequest.value = null
    }

    fun confirmDeny(reason: String = "Ditolak oleh pengguna") {
        val req = _activeRequest.value ?: return
        pendingAction = null
        _lastDecision.value = "$reason (${req.friendlyTitle})"
        _activeRequest.value = null
    }
}
