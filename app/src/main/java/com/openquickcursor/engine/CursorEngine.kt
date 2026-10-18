package com.openquickcursor.engine

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.openquickcursor.Defaults
import com.openquickcursor.inject.GestureInjector
import com.openquickcursor.input.CursorMapper
import com.openquickcursor.overlay.CanvasView
import com.openquickcursor.overlay.Overlays
import com.openquickcursor.overlay.TrackerView
import com.openquickcursor.overlay.TriggerZoneView

/**
 * Owns the overlays and runs a cursor session:
 * swipe in from the edge -> tracker + cursor appear -> move the tracker to aim ->
 * tap the tracker to tap at the cursor -> hides after a short idle time.
 */
class CursorEngine(private val service: AccessibilityService) :
    TriggerZoneView.Listener, TrackerView.Listener {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val injector = GestureInjector(service)

    private val trackerSize = Overlays.dp(service, Defaults.TRACKER_SIZE_DP).toInt()

    private var canvas: CanvasView? = null
    private val triggers = mutableListOf<TriggerZoneView>()
    private var tracker: TrackerView? = null
    private var trackerParams: WindowManager.LayoutParams? = null
    private var mapper: CursorMapper? = null
    private var screen = Point()

    /** True while one of our own gestures is being injected. */
    private var injecting = false

    private val hideRunnable = Runnable { endSession() }

    fun start() {
        screen = Overlays.screenSize(service)
        CanvasView(service).also {
            wm.addView(it, Overlays.canvasParams())
            canvas = it
        }
        addTriggers()
    }

    fun stop() {
        endSession()
        removeTriggers()
        removeView(canvas)
        canvas = null
    }

    /** Rotation or display size changed: rebuild the edge triggers for the new screen. */
    fun onScreenChanged() {
        endSession()
        removeTriggers()
        screen = Overlays.screenSize(service)
        addTriggers()
    }

    /** One invisible strip on each side edge (SPEC.md §5 default layout). */
    private fun addTriggers() {
        val width = Overlays.dp(service, Defaults.TRIGGER_WIDTH_DP).toInt()
        val height = (screen.y * Defaults.TRIGGER_HEIGHT_FRACTION).toInt()
        val y = (screen.y * Defaults.TRIGGER_CENTER_Y_FRACTION - height / 2f).toInt()
        for (side in TriggerZoneView.Side.entries) {
            val x = if (side == TriggerZoneView.Side.LEFT) 0 else screen.x - width
            TriggerZoneView(service, side, this).also {
                wm.addView(it, Overlays.triggerParams(width, height, x, y))
                triggers += it
            }
        }
    }

    private fun removeTriggers() {
        triggers.forEach(::removeView)
        triggers.clear()
    }

    // --- Session

    private fun startSession(side: TriggerZoneView.Side, x: Float, y: Float) {
        handler.removeCallbacks(hideRunnable)
        val portrait = service.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val level = if (portrait) Defaults.PORTRAIT_SPEED_LEVEL else Defaults.LANDSCAPE_SPEED_LEVEL
        mapper = CursorMapper.forEdgeSession(
            fingerX = x,
            fingerY = y,
            fromRight = side == TriggerZoneView.Side.RIGHT,
            speed = Defaults.SPEED_FACTORS[level],
            screenWidth = screen.x,
            screenHeight = screen.y,
        )
        if (tracker == null) {
            val params = Overlays.trackerParams(trackerSize)
            TrackerView(service, this).also {
                wm.addView(it, params)
                tracker = it
                trackerParams = params
            }
        }
        moveTo(x, y)
    }

    private fun moveTo(x: Float, y: Float) {
        val view = tracker ?: return
        val params = trackerParams ?: return
        val cx = x.coerceIn(0f, screen.x.toFloat())
        val cy = y.coerceIn(0f, screen.y.toFloat())
        view.centerX = cx
        view.centerY = cy
        params.x = (cx - trackerSize / 2f).toInt()
        params.y = (cy - trackerSize / 2f).toInt()
        wm.updateViewLayout(view, params)

        val cursor = mapper?.map(cx, cy) ?: return
        canvas?.showCursor(cursor.x, cursor.y)
    }

    private fun endSession() {
        handler.removeCallbacks(hideRunnable)
        removeView(tracker)
        tracker = null
        trackerParams = null
        mapper = null
        canvas?.hideCursor()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, Defaults.HIDE_TIMEOUT_MS)
    }

    /** Injects a gesture at the cursor with the tracker made untouchable so it can't catch it. */
    private fun actAtCursor(longTap: Boolean) {
        val cursor = mapper?.cursor ?: return
        val x = cursor.x
        val y = cursor.y
        injecting = true
        setTrackerTouchable(false)
        canvas?.playRipple(x, y)
        val done = {
            setTrackerTouchable(true)
            handler.postDelayed({ injecting = false }, 50)
            scheduleHide()
        }
        if (longTap) injector.longTap(x, y, done) else injector.tap(x, y, done)
    }

    private fun setTrackerTouchable(touchable: Boolean) {
        val view = tracker ?: return
        val params = trackerParams ?: return
        params.flags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        wm.updateViewLayout(view, params)
    }

    private fun removeView(view: View?) {
        if (view?.isAttachedToWindow == true) wm.removeView(view)
    }

    // --- Trigger zone

    override fun onTriggerActivated(side: TriggerZoneView.Side, x: Float, y: Float) = startSession(side, x, y)

    override fun onTriggerDrag(x: Float, y: Float) = moveTo(x, y)

    override fun onTriggerReleased(x: Float, y: Float) = scheduleHide()

    // --- Tracker pad

    override fun onTrackerMove(centerX: Float, centerY: Float) = moveTo(centerX, centerY)

    override fun onTrackerTap() = actAtCursor(longTap = false)

    override fun onTrackerLongTap() = actAtCursor(longTap = true)

    override fun onTrackerTouchStart() = handler.removeCallbacks(hideRunnable)

    override fun onTrackerTouchEnd() = scheduleHide()

    override fun onTouchOutside() {
        if (!injecting) endSession()
    }
}
