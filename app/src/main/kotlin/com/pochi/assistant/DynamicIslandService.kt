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
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
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
    private var xAnimation: Job? = null

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

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            collapsedFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (PochiPrefs.offsetXFlow.value * density).toInt()
            y = (PochiPrefs.offsetYFlow.value * density).toInt()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

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
                PochiIslandBot(
                    onExpandChange = { expanded ->
                        isIslandExpanded = expanded
                        params.flags = if (expanded) expandedFlags else collapsedFlags
                        val targetX = if (expanded) 0 else (PochiPrefs.offsetXFlow.value * density).toInt()
                        animateXTo(targetX)
                    },
                    onPowerOffService = {
                        turnOffTorch()
                        stopSelf()
                    }
                )
            }
        }

        windowManager.addView(composeView, params)

        serviceScope.launch {
            combine(PochiPrefs.offsetXFlow, PochiPrefs.offsetYFlow) { xDp, yDp ->
                Pair(xDp, yDp)
            }.collect { (xDp, yDp) ->
                if (::composeView.isInitialized) {
                    params.y = (yDp * density).toInt()
                    if (!isIslandExpanded && xAnimation?.isActive != true) {
                        params.x = (xDp * density).toInt()
                    }
                    try {
                        windowManager.updateViewLayout(composeView, params)
                    } catch (_: Exception) {}
                }
            }
        }
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

    private fun animateXTo(target: Int) {
        xAnimation?.cancel()
        xAnimation = serviceScope.launch {
            val start = params.x
            val startTime = SystemClock.uptimeMillis()
            val duration = 260L
            while (true) {
                val progress = ((SystemClock.uptimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
                val eased = 1f - (1f - progress) * (1f - progress)
                params.x = (start + (target - start) * eased).toInt()
                try {
                    windowManager.updateViewLayout(composeView, params)
                } catch (_: Exception) {}
                if (progress >= 1f) break
                delay(16)
            }
        }
    }

    private fun turnOffTorch() {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.firstOrNull()?.let { manager.setTorchMode(it, false) }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        xAnimation?.cancel()
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
