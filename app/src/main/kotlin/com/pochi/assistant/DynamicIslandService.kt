package com.pochi.assistant

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DynamicIslandService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var isIslandExpanded = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        PochiPrefs.init(this)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val collapsedWidthPx = (180 * density).toInt()

        val collapsedFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        val expandedFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        params = WindowManager.LayoutParams(
            collapsedWidthPx,
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
                        serviceScope.launch {
                            if (expanded) {
                                params.width = WindowManager.LayoutParams.MATCH_PARENT
                                params.x = 0
                                params.flags = expandedFlags
                                windowManager.updateViewLayout(this@apply, params)
                            } else {
                                delay(230)
                                params.width = collapsedWidthPx
                                params.x = (PochiPrefs.offsetXFlow.value * density).toInt()
                                params.flags = collapsedFlags
                                windowManager.updateViewLayout(this@apply, params)
                            }
                        }
                    },
                    onPowerOffService = {
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
                    params.x = if (isIslandExpanded) 0 else (xDp * density).toInt()
                    params.y = (yDp * density).toInt()
                    try {
                        windowManager.updateViewLayout(composeView, params)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        if (::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
