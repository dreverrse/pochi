package com.pochi.assistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DynamicIslandService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "pochi_dynamic_island_channel"
        private const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, DynamicIslandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DynamicIslandService::class.java))
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams

    private var isIslandExpanded = false
    private val landscapeSideState = mutableStateOf("NONE") // "NONE" (portrait), "LEFT", "RIGHT"
    private var collapseJob: Job? = null
    private var heightJob: Job? = null
    private var expandedHeightPx = 0
    private var screenshotObserver: ContentObserver? = null
    private var lastDetectedScreenshotUri: String = ""

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

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

        val collapsedWidthPx = (200 * density).toInt()
        val collapsedHeightPx = (36 * density).toInt()

        landscapeSideState.value = detectLandscapeSide()

        params = WindowManager.LayoutParams(
            collapsedWidthPx,
            collapsedHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            collapsedFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        applyFixedPositionAndSize(expanded = false, density = density)

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

            setOnDragListener { _, event ->
                when (event.action) {
                    android.view.DragEvent.ACTION_DRAG_STARTED -> true
                    android.view.DragEvent.ACTION_DROP -> {
                        val clip = event.clipData
                        if (clip != null && clip.itemCount > 0) {
                            val item = clip.getItemAt(0)
                            val uri = item.uri
                            if (uri != null) {
                                val info = PochiJarvisEngine.inspectUri(context, uri)
                                PochiSignals.publishDroppedFile(info)
                            } else if (!item.text.isNullOrBlank()) {
                                val txt = item.text.toString()
                                PochiSignals.publishDroppedFile(
                                    DroppedFileInfo(
                                        uri = null,
                                        fileName = "dropped_snippet.txt",
                                        mimeType = "text/plain",
                                        sizeBytes = txt.length.toLong(),
                                        textPreview = txt
                                    )
                                )
                            }
                        }
                        true
                    }
                    else -> true
                }
            }

            setContent {
                val sideMode by landscapeSideState
                PochiIslandBot(
                    landscapeSide = sideMode,
                    onExpandChange = { expanded ->
                        isIslandExpanded = expanded
                        collapseJob?.cancel()
                        heightJob?.cancel()
                        landscapeSideState.value = detectLandscapeSide()

                        if (expanded) {
                            applyFixedPositionAndSize(expanded = true, density = density)
                            applyLayout()
                        } else {
                            params.flags = collapsedFlags
                            applyLayout()
                            collapseJob = serviceScope.launch {
                                delay(300)
                                applyFixedPositionAndSize(expanded = false, density = density)
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
                                delay(240)
                                if (isIslandExpanded) {
                                    applyFixedPositionAndSize(expanded = true, density = density)
                                    applyLayout()
                                }
                            }
                        }
                    },
                    onPowerOffService = {
                        stopSelf()
                    }
                )
            }
        }

        runCatching {
            windowManager.addView(composeView, params)
            _isRunning.value = true
        }.onFailure {
            stopSelf()
            return
        }

        registerScreenshotObserver()
    }

    @Suppress("DEPRECATION")
    private fun detectLandscapeSide(): String {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape) return "NONE"
        val rot = runCatching { windowManager.defaultDisplay.rotation }.getOrDefault(Surface.ROTATION_90)
        return if (rot == Surface.ROTATION_270) "RIGHT" else "LEFT"
    }

    /**
     * Strictly locks Pochi's position so it cannot be dragged or shifted off-center:
     * - Portrait ("NONE"): Exact horizontal center at top (`Gravity.TOP or Gravity.CENTER_HORIZONTAL`, `x = 0`, `y = statusBarHeight`).
     * - Landscape ("LEFT" / "RIGHT"): Docked directly on the side edge of the phone screen (`Gravity.START/END or Gravity.CENTER_VERTICAL`, `x = 0`, `y = 0`).
     */
    private fun applyFixedPositionAndSize(expanded: Boolean, density: Float) {
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val side = detectLandscapeSide()
        landscapeSideState.value = side
        val minTopY = getMinTopYPx()

        if (expanded) {
            params.flags = expandedFlags
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            val expW = if (side != "NONE") {
                (sw - (48 * density).toInt()).coerceIn((320 * density).toInt(), (480 * density).toInt())
            } else {
                (sw - (16 * density).toInt()).coerceIn((328 * density).toInt(), (396 * density).toInt())
            }
            val maxExpH = (sh - minTopY - (16 * density).toInt()).coerceAtLeast((220 * density).toInt())
            val defExpH = if (side != "NONE") {
                (290 * density).toInt().coerceAtMost(maxExpH)
            } else {
                (420 * density).toInt().coerceAtMost(maxExpH)
            }
            val targetH = if (expandedHeightPx > 0) {
                expandedHeightPx.coerceIn((220 * density).toInt(), maxExpH)
            } else {
                defExpH
            }

            params.width = expW
            params.height = targetH

            when (side) {
                "LEFT" -> {
                    params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    params.x = 0
                    params.y = 0
                }
                "RIGHT" -> {
                    params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    params.x = 0
                    params.y = 0
                }
                else -> {
                    params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    params.x = 0
                    params.y = minTopY
                }
            }
        } else {
            params.flags = collapsedFlags
            when (side) {
                "LEFT" -> {
                    params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    params.width = (176 * density).toInt()
                    params.height = (36 * density).toInt()
                    params.x = 0
                    params.y = 0
                }
                "RIGHT" -> {
                    params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    params.width = (176 * density).toInt()
                    params.height = (36 * density).toInt()
                    params.x = 0
                    params.y = 0
                }
                else -> {
                    params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    params.width = (200 * density).toInt()
                    params.height = (36 * density).toInt()
                    params.x = 0
                    params.y = minTopY
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::composeView.isInitialized || !::params.isInitialized) return
        val density = resources.displayMetrics.density
        applyFixedPositionAndSize(expanded = isIslandExpanded, density = density)
        applyLayout()
    }

    private fun registerScreenshotObserver() {
        runCatching {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    inspectRecentScreenshot(uri)
                }
            }
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            screenshotObserver = observer
        }
    }

    private fun inspectRecentScreenshot(changedUri: Uri?) {
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DATE_ADDED
                )
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val name = cursor.getString(1).orEmpty()
                        val relPath = cursor.getString(2).orEmpty()
                        val dateAddedSec = cursor.getLong(3)
                        val nowSec = System.currentTimeMillis() / 1000L
                        val isScreenshot = name.contains("screenshot", ignoreCase = true) ||
                            relPath.contains("screenshot", ignoreCase = true)
                        if (isScreenshot && (nowSec - dateAddedSec) <= 8L) {
                            val contentUri = ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                id
                            )
                            if (contentUri.toString() != lastDetectedScreenshotUri) {
                                lastDetectedScreenshotUri = contentUri.toString()
                                PochiSignals.publishScreenshot(
                                    ScreenshotPreviewInfo(
                                        uri = contentUri,
                                        displayName = name.ifBlank { "Screenshot.png" }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyLayout() {
        if (!::composeView.isInitialized || !::params.isInitialized) return
        try {
            windowManager.updateViewLayout(composeView, params)
        } catch (_: Exception) {
        }
    }

    private fun getStatusBarHeightPx(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        if (result <= 0) {
            result = (38 * resources.displayMetrics.density).toInt()
        }
        return result
    }

    private fun getMinTopYPx(): Int {
        return getStatusBarHeightPx()
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pochi Dynamic Notch",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Pochi active at the screen cutout"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Pochi Active")
            .setContentText("Tap to open Pochi settings")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.value = false
        screenshotObserver?.let {
            runCatching { contentResolver.unregisterContentObserver(it) }
        }
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        if (::composeView.isInitialized) {
            runCatching { windowManager.removeView(composeView) }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
