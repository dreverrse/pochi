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
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Phase 1: 120fps Floating Overlay Service Architecture.
 *
 * - Hosts a `ComposeView` with custom `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner`.
 * - Anchors the matte-black (`#0A0A0A`) pill-shaped Dynamic Island at the top-center camera cutout.
 * - Strict 120fps Rule: NEVER animates `WindowManager.LayoutParams` width/height per frame via `updateViewLayout`.
 *   Instead, resizes the transparent window container ONCE prior to expansion, and drives all morphing inside
 *   Jetpack Compose using `spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)`
 *   and hardware-accelerated `Modifier.graphicsLayer`.
 */
class DynamicIslandService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val CHANNEL_ID = "pochi_island"
        private const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
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
    private val peekState = mutableStateOf(false)
    private val peekSideState = mutableStateOf("right")
    private var lastInteraction = System.currentTimeMillis()
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

        // Top-notch bounding box (228dp x 34dp) so the island sits flush with the top status bar
        // cutout (`y = 0`) without obstructing app toolbars below it.
        val collapsedWidthPx = (228 * density).toInt()
        val collapsedHeightPx = (34 * density).toInt()
        // Dynamic Island bounding box (200dp x 36dp) sits cleanly below the status bar
        val collapsedWidthPx = (200 * density).toInt()
        val collapsedHeightPx = (36 * density).toInt()
        val minTopY = getMinTopYPx()
        val initialY = computeY(PochiPrefs.offsetYFlow.value)
        val initialX = (PochiPrefs.offsetXFlow.value * density).toInt()

        params = WindowManager.LayoutParams(
            collapsedWidthPx,
            collapsedHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            collapsedFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = initialX
            y = initialY

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        val screenWidthPx = resources.displayMetrics.widthPixels
        val screenHeightPx = resources.displayMetrics.heightPixels
        val isLandscapeInit = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val expandedWidthPx = if (isLandscapeInit) {
            (screenWidthPx - (32 * density).toInt()).coerceIn((320 * density).toInt(), (520 * density).toInt())
        } else {
            (screenWidthPx - (16 * density).toInt()).coerceIn((328 * density).toInt(), (396 * density).toInt())
        }
        val maxExpandedHeightPx = (screenHeightPx - minTopY - (16 * density).toInt()).coerceAtLeast((180 * density).toInt())
        val defaultExpandedHeightPx = if (isLandscapeInit) {
            (260 * density).toInt().coerceAtMost(maxExpandedHeightPx)
            (210 * density).toInt().coerceAtMost(maxExpandedHeightPx)
        } else {
            (440 * density).toInt().coerceAtMost(maxExpandedHeightPx)
            (230 * density).toInt().coerceAtMost(maxExpandedHeightPx)
        }

        fun updateInteraction() {
            lastInteraction = System.currentTimeMillis()
            if (peekState.value || hiddenState.value) {
                restoreFromHidden(collapsedWidthPx, collapsedHeightPx, density)
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
                val peek by peekState
                val currentPeekSide by peekSideState
                val hidden by hiddenState
                PochiIslandBot(
                    onExpandChange = { expanded ->
                        lastInteraction = System.currentTimeMillis()
                        if (hiddenState.value) {
                            hiddenState.value = false
                            peekState.value = false
                        }
                        isIslandExpanded = expanded
                        collapseJob?.cancel()
                        heightJob?.cancel()
                        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                        val curSw = resources.displayMetrics.widthPixels
                        val curSh = resources.displayMetrics.heightPixels
                        val curMinTop = getMinTopYPx()
                        val curExpW = if (isLandscape) {
                            (curSw - (32 * density).toInt()).coerceIn((320 * density).toInt(), (520 * density).toInt())
                        } else {
                            (curSw - (16 * density).toInt()).coerceIn((328 * density).toInt(), (396 * density).toInt())
                        }
                        val curMaxExpH = (curSh - curMinTop - (16 * density).toInt()).coerceAtLeast((180 * density).toInt())
                        val curDefExpH = if (isLandscape) {
                            (260 * density).toInt().coerceAtMost(curMaxExpH)
                            (210 * density).toInt().coerceAtMost(curMaxExpH)
                        } else {
                            (440 * density).toInt().coerceAtMost(curMaxExpH)
                            (230 * density).toInt().coerceAtMost(curMaxExpH)
                        }

                        if (expanded) {
                            params.flags = expandedFlags
                            params.x = 0
                            params.y = curMinTop
                            params.width = curExpW
                            params.height = if (expandedHeightPx > 0) {
                                expandedHeightPx.coerceIn((180 * density).toInt(), curMaxExpH)
                            } else {
                                curDefExpH
                            }
                            applyLayout()
                        } else {
                            params.flags = collapsedFlags
                            applyLayout()
                            collapseJob = serviceScope.launch {
                                delay(360)
                                params.width = collapsedWidthPx
                                params.height = collapsedHeightPx
                                params.x = (PochiPrefs.offsetXFlow.value * density).toInt()
                                params.y = computeY(PochiPrefs.offsetYFlow.value)
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
                                delay(260)
                                if (isIslandExpanded) {
                                    val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                                    val curSw = resources.displayMetrics.widthPixels
                                    val curSh = resources.displayMetrics.heightPixels
                                    val curMinTop = getMinTopYPx()
                                    val curExpW = if (isLandscape) {
                                        (curSw - (32 * density).toInt()).coerceIn((320 * density).toInt(), (520 * density).toInt())
                                    } else {
                                        (curSw - (16 * density).toInt()).coerceIn((328 * density).toInt(), (396 * density).toInt())
                                    }
                                    val curMaxExpH = (curSh - curMinTop - (16 * density).toInt()).coerceAtLeast((180 * density).toInt())
                                    params.width = curExpW
                                    params.height = expandedHeightPx.coerceIn((160 * density).toInt(), curMaxExpH)
                                    applyLayout()
                                }
                            }
                        }
                    },
                    isHidden = hidden,
                    peekMode = peek,
                    peekSide = currentPeekSide,
                    onRestoreFromHidden = { updateInteraction() },
                    onDragBubble = { dx, dy ->
                        lastInteraction = System.currentTimeMillis()
                        if (!hiddenState.value && !isIslandExpanded) {
                            val sw = resources.displayMetrics.widthPixels
                            val sh = resources.displayMetrics.heightPixels
                            val curMinTop = getMinTopYPx()
                            val maxBottomY = (sh - params.height - (16 * density).toInt()).coerceAtLeast(curMinTop)
                            // Allow flexible horizontal and vertical movement across the display
                            params.x = (params.x + dx.toInt()).coerceIn(-sw / 2 + params.width / 4, sw / 2 - params.width / 4)
                            params.y = (params.y + dy.toInt()).coerceIn(curMinTop, maxBottomY)
                            applyLayout()
                        }
                    },
                    onDragEnd = {
                        lastInteraction = System.currentTimeMillis()
                        finishDrag(density)
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

        serviceScope.launch {
            combine(PochiPrefs.offsetXFlow, PochiPrefs.offsetYFlow) { xDp, yDp ->
                Pair(xDp, yDp)
            }.collect { (xDp, yDp) ->
                if (::composeView.isInitialized && ::params.isInitialized) {
                    if (!isIslandExpanded && !hiddenState.value) {
                        params.x = (xDp * density).toInt()
                        params.y = computeY(yDp)
                        applyLayout()
                    }
                }
            }
        }
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
        val density = resources.displayMetrics.density
        // Ensures the island sits cleanly BELOW the phone infobar / status bar (never covered by clock/battery)
        return getStatusBarHeightPx() + (6 * density).toInt()
    }

    private fun computeY(userOffsetYDp: Int): Int {
        val density = resources.displayMetrics.density
        val minTopY = getMinTopYPx()
        val userPx = (userOffsetYDp * density).toInt()
        return (minTopY + userPx).coerceAtLeast(minTopY)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::composeView.isInitialized || !::params.isInitialized) return

        val density = resources.displayMetrics.density
        val sw = resources.displayMetrics.widthPixels
        val sh = resources.displayMetrics.heightPixels
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val minTopY = getMinTopYPx()

        // 1. Re-clamp horizontal position within new screen bounds
        params.x = params.x.coerceIn(-sw / 2 + params.width / 4, sw / 2 - params.width / 4)

        // 2. Re-clamp vertical position below infobar and within landscape bounds
        val maxBottomY = (sh - params.height - (12 * density).toInt()).coerceAtLeast(minTopY)
        params.y = params.y.coerceIn(minTopY, maxBottomY)

        // 3. Responsive dimension adaptation when phone is tilted (miring / landscape)
        if (isIslandExpanded) {
            val expandedWidthPx = if (isLandscape) {
                (sw - (32 * density).toInt()).coerceIn((320 * density).toInt(), (520 * density).toInt())
            } else {
                (sw - (16 * density).toInt()).coerceIn((328 * density).toInt(), (396 * density).toInt())
            }
            val maxExpandedHeightPx = (sh - minTopY - (16 * density).toInt()).coerceAtLeast((180 * density).toInt())
            params.width = expandedWidthPx
            params.height = params.height.coerceIn((180 * density).toInt(), maxExpandedHeightPx)
        } else {
            params.width = (228 * density).toInt()
            params.height = (34 * density).toInt()
            params.width = (200 * density).toInt()
            params.height = (36 * density).toInt()
        }

        applyLayout()
    }

    private fun finishDrag(density: Float) {
        if (hiddenState.value || isIslandExpanded) return
        val screenWidthPx = resources.displayMetrics.widthPixels
        val edgeMargin = (16 * density).toInt()
        if (params.x > screenWidthPx / 2 - edgeMargin) {
            hideAtEdge(right = true, density = density)
        } else if (params.x < -(screenWidthPx / 2 - edgeMargin)) {
            hideAtEdge(right = false, density = density)
        } else {
            val minTopY = getMinTopYPx()
            val savedY = ((params.y - minTopY) / density).toInt().coerceAtLeast(0)
            val savedX = (params.x / density).toInt()
            PochiPrefs.saveOffsets(this, savedX, savedY)
        }
    }

    private fun hideAtEdge(right: Boolean, density: Float) {
        val side = if (right) "right" else "left"
        peekSideState.value = side
        peekState.value = true
        hiddenState.value = true
        val peekWidthPx = (64 * density).toInt()
        val peekHeightPx = (64 * density).toInt()
        val screenWidthPx = resources.displayMetrics.widthPixels
        val screenHeightPx = resources.displayMetrics.heightPixels
        val minTopY = getMinTopYPx()
        params.flags = collapsedFlags
        params.width = peekWidthPx
        params.height = peekHeightPx
        params.x = if (right) {
            screenWidthPx / 2 - peekWidthPx / 2
        } else {
            -(screenWidthPx / 2 - peekWidthPx / 2)
        }
        params.y = params.y.coerceIn(minTopY, screenHeightPx - peekHeightPx - (24 * density).toInt())
        applyLayout()
    }

    private fun restoreFromHidden(collapsedWidthPx: Int, collapsedHeightPx: Int, density: Float) {
        hiddenState.value = false
        peekState.value = false
        params.flags = collapsedFlags
        params.width = collapsedWidthPx
        params.height = collapsedHeightPx
        params.x = (PochiPrefs.offsetXFlow.value * density).toInt()
        params.y = computeY(PochiPrefs.offsetYFlow.value)
        applyLayout()
    }



    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pochi Dynamic Island",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Pochi 120fps Dynamic Island active at the top cutout"
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
            .setContentTitle("Pochi Island Active")
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
