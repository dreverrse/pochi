package com.pochi.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PochiAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!PochiPrefs.isScreenAwarenessEnabled(this)) return
        if (event.isPassword) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> Unit
            else -> return
        }

        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isBlank() || pkg == packageName || pkg == "com.android.systemui") return

        val root = rootInActiveWindow ?: return
        val appLabel = runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)

        val texts = mutableListOf<String>()
        collectTexts(root, texts, 0)
        val content = texts.distinct().joinToString(" • ").take(700)
        PochiSignals.updateScreen(appLabel, content)
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 12 || out.size >= 90) return
        try {
            if (node.isPassword) return
            node.text?.toString()?.trim()
                ?.takeIf { it.length > 1 }
                ?.let { out.add(it) }
            node.contentDescription?.toString()?.trim()
                ?.takeIf { it.length > 1 }
                ?.let { out.add(it) }
            for (index in 0 until node.childCount) {
                collectTexts(node.getChild(index), out, depth + 1)
                if (out.size >= 90) break
            }
        } catch (_: Exception) {
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: PochiAccessibilityService? = null

        fun takeScreenshot(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } else {
                false
            }
        }

        fun lockScreen(): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            } else {
                false
            }
        }
    }
}
