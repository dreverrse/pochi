package com.pochi.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DynamicIslandService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private companion object {
        const val CHANNEL_ID = "pochi_island"
        const val NOTIFICATION_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isIslandExpanded = false
    private val hiddenState = mutableStateOf(false)
    private var collapseJob: Job? = null
    private var heightJob: Job? = null
    private var expandedHeightPx = 0

    private val collapsedFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    private val expandedFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        PochiPrefs.init(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val collapsedPx = (62 * density).toInt()

        var lastInteraction = System.currentTimeMillis()
        var peekSide = "right"
        val peekState = mutableStateOf(false)

        fun updateInteraction() {
            lastInteraction = System.currentTimeMillis()
            if (peekState.value) {
                peekState.value = false
                restoreFromHidden(collapsedPx, density)
            }
        }

        val screenWidthPx = resources.displayMetrics.widthPixels
        val expandedWidthPx = (screenWidthPx - (28 * density).toInt())
            .coerceIn((300 * density).toInt(), (372 * density).toInt())

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DynamicIslandService)
            setViewTreeViewModelStoreOwner(this@DynamicIslandService)
            setViewTreeSavedStateRegistryOwner(this@DynamicIslandService)

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE && isIslandExpanded) {
                    ApprovalManager.triggerOutsideDismiss()
                    true
                } else {
                    false
                }
            }

            setContent {
                val peek by peekState
                PochiIslandBot(
                    onExpandChange = { expanded ->
                        updateInteraction()
                        isIslandExpanded = expanded
                        collapseJob?.cancel()
                        heightJob?.cancel()
                        if (expanded) {
                            params.flags = expandedFlags
                            params.x = 0
                            params.width = expandedWidthPx
                            params.height = if (expandedHeightPx > 0) { expandedHeightPx } else { (250 * density).toInt() }
                            applyLayout()
                        } else {
                            params.flags = collapsedFlags
                            applyLayout()
                            collapseJob = serviceScope.launch {
                                delay(260)
                                params.width = collapsedPx
                                params.height = collapsedPx
                                params.x = (PochiPrefs.offsetXFlow.value * density).toInt()
                                params.y = (PochiPrefs.offsetYFlow.value * density).toInt()
                                applyLayout()
                            }
                        }
                    },
                    onExpandedHeightChange = { heightPx ->
                        expandedHeightPx = heightPx
                        if (isIslandExpanded) {
                            if (heightPx > params.height) {
                                params.height = heightPx
                                composeView.post { applyLayout() }
                            }
                            heightJob?.cancel()
                            heightJob = serviceScope.launch {
                                delay(280)
                                if (isIslandExpanded) {
                                    params.width = expandedWidthPx
                                    params.height = expandedHeightPx
                                    applyLayout()
                                }
                            }
                        }
                    },
                    isHidden = hiddenState.value,
                    peekMode = peek,
                    peekSide = peekSide,
                    onRestoreFromHidden = { updateInteraction(); restoreFromHidden(collapsedPx, density) },
                    onDragBubble = { dx, dy -> updateInteraction(); if (!hiddenState.value && !isIslandExpanded) { val screenWidthPx = resources.displayMetrics.widthPixels; val screenHeightPx = resources.displayMetrics.heightPixels; params.x = (params.x + dx.toInt()).coerceIn(-screenWidthPx/2, screenWidthPx/2); params.y = (params.y + dy.toInt()).coerceIn(0, screenHeightPx - params.height); applyLayout() } },
                    onDragEnd = { updateInteraction(); finishDrag(collapsedPx, density) },
                    onPowerOffService = { turnOffTorch(); stopSelf() }
                )
            }

        windowManager.addView(composeView, params)

        serviceScope.launch {
            while (true) {
                delay(1000)
                if (hiddenState.value && !isIslandExpanded && !peekState.value) {
                    if (System.currentTimeMillis() - lastInteraction > 15000) {
                        peekSide = if (Math.random() > 0.5) "left" else "right"
                        peekState.value = true
                        hideAtEdgeForPeek(peekSide, collapsedPx, density)
                    }
                }
            }
        }

        serviceScope.launch {
            combine(PochiPrefs.offsetXFlow, PochiPrefs.offsetYFlow) { xDp, yDp ->
                Pair(xDp, yDp)
            }.collect { (xDp, yDp) ->
                if (::composeView.isInitialized) {
                    params.y = (yDp * density).toInt()
                    if (!isIslandExpanded && !hiddenState.value) {
                        params.x = (xDp * density).toInt()
                    }
                    applyLayout()
                }
            }
        }
    }

    private fun applyLayout() {
        if (!::composeView.isInitialized) return
        try {
            windowManager.updateViewLayout(composeView, params)
        } catch (_: Exception) {}
    }

    private fun finishDrag(collapsedPx: Int, density: Float) {
        if (hiddenState.value || isIslandExpanded) return
        val screenWidthPx = resources.displayMetrics.widthPixels
        val edgeMargin = (48 * density).toInt()
        if (params.x > screenWidthPx / 2 - edgeMargin) {
            hideAtEdge(right = true, collapsedPx = collapsedPx, density = density)
        } else if (params.x < -(screenWidthPx / 2 - edgeMargin)) {
            hideAtEdge(right = false, collapsedPx = collapsedPx, density = density)
        } else {
            PochiPrefs.saveOffsets(
                this,
                (params.x / density).toInt(),
                (params.y / density).toInt()
            )
        }
    }

    private fun hideAtEdgeForPeek(rightSide: String, collapsedPx: Int, density: Float) {
        hiddenState.value = true
        val peekWidthPx = (58 * density).toInt()
        val peekHeightPx = (58 * density).toInt()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val screenHeightPx = resources.displayMetrics.heightPixels
        params.flags = collapsedFlags
        params.width = peekWidthPx
        params.height = peekHeightPx
        if (rightSide == "right") {
            params.x = screenWidthPx / 2 - peekWidthPx / 4
        } else {
            params.x = -(screenWidthPx / 2 - peekWidthPx / 4)
        }
        val baseY = (PochiPrefs.offsetYFlow.value * density).toInt()
        params.y = (baseY + screenHeightPx / 6)
            .coerceIn((20 * density).toInt(), screenHeightPx - peekHeightPx - (80 * density).toInt())
        applyLayout()
    }

    private fun hideAtEdge(right: Boolean, collapsedPx: Int, density: Float) {
        hiddenState.value = true
        peekState.value = false
        val tabWidthPx = (58 * density).toInt()
        val tabHeightPx = (58 * density).toInt()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val screenHeightPx = resources.displayMetrics.heightPixels
        params.flags = collapsedFlags
        params.width = tabWidthPx
        params.height = tabHeightPx
        if (right) {
            params.x = screenWidthPx / 2 - tabWidthPx / 4
        } else {
            params.x = -(screenWidthPx / 2 - tabWidthPx / 4)
        }
        val baseY = (PochiPrefs.offsetYFlow.value * density).toInt()
        params.y = (baseY + screenHeightPx / 6)
            .coerceIn((20 * density).toInt(), screenHeightPx - tabHeightPx - (80 * density).toInt())
        applyLayout()
    }

    private fun restoreFromHidden(collapsedPx: Int, density: Float) {
        hiddenState.value = false
        peekState.value = false
        params.width = collapsedPx
        params.height = collapsedPx
        params.x = (PochiPrefs.offsetXFlow.value * density).toInt()
        params.y = (PochiPrefs.offsetYFlow.value * density).toInt()
        applyLayout()
    }

    private fun startAsForeground() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pochi Island",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga asisten mengambang Pochi tetap aktif"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pochi aktif")
            .setContentText("Asisten mengambang sedang berjalan. Ketuk untuk membuka pengaturan.")
            .setSmallIcon(R.drawable.ic_pochi_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun turnOffTorch() {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.firstOrNull()?.let { manager.setTorchMode(it, false) }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        collapseJob?.cancel()
        heightJob?.cancel()
        serviceScope.cancel()
        turnOffTorch()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (::composeView.isInitialized) {
            try {
                windowManager.removeView(composeView)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
