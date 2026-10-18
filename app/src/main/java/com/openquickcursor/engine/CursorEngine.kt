package com.openquickcursor.engine

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import com.openquickcursor.Defaults
import com.openquickcursor.actions.Action
import com.openquickcursor.feedback.Haptics
import com.openquickcursor.inject.GestureInjector
import com.openquickcursor.input.CursorMapper
import com.openquickcursor.input.EdgeActionResolver
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
    private val haptics = Haptics(service)
    private var edgeResolver: EdgeActionResolver? = null

    /** Edge action currently highlighted; it fires after a short hold or on release. */
    private var armedEdge: EdgeActionResolver.Hit? = null

    /** True while one of our own gestures is being injected. */
    private var injecting = false

    private val hideRunnable = Runnable { endSession() }
    private val fireEdgeRunnable = Runnable { fireEdgeAction() }

    fun start() {
        CanvasView(service).also {
            wm.addView(it, Overlays.canvasParams())
            canvas = it
        }
        updateScreen()
        addTriggers()
    }

    private fun updateScreen() {
        screen = Overlays.screenSize(service)
        canvas?.screenWidth = screen.x
        canvas?.screenHeight = screen.y
        edgeResolver = EdgeActionResolver(
            Defaults.EDGE_BINDINGS,
            Overlays.dp(service, Defaults.EDGE_THRESHOLD_DP),
            screen.x,
            screen.y,
        )
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
        updateScreen()
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
                it.doubleTapEnabled = Defaults.DOUBLE_TAP_ACTION != Action.NOTHING
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

        val m = mapper ?: return
        val cursor = m.map(cx, cy)
        canvas?.showCursor(cursor.x, cursor.y)
        updateEdge(edgeResolver?.resolve(m.unclamped))
    }

    private fun updateEdge(hit: EdgeActionResolver.Hit?) {
        if (hit == armedEdge) return
        handler.removeCallbacks(fireEdgeRunnable)
        armedEdge = hit
        canvas?.showEdge(hit)
        if (hit != null) {
            haptics.tick()
            handler.postDelayed(fireEdgeRunnable, Defaults.EDGE_DELAY_MS)
        }
    }

    private fun fireEdgeAction() {
        val hit = armedEdge ?: return
        endSession()
        hit.action.perform(service)
    }

    /** Finger lifted after dragging: run a highlighted edge action, otherwise start the idle timer. */
    private fun onDragFinished() {
        if (armedEdge != null) fireEdgeAction() else scheduleHide()
    }

    private fun endSession() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(fireEdgeRunnable)
        armedEdge = null
        canvas?.showEdge(null)
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

    override fun onTriggerReleased(x: Float, y: Float) = onDragFinished()

    // --- Tracker pad

    override fun onTrackerMove(centerX: Float, centerY: Float) = moveTo(centerX, centerY)

    override fun onTrackerTap() = actAtCursor(longTap = false)

    override fun onTrackerLongTap() = actAtCursor(longTap = true)

    override fun onTrackerTouchStart() = handler.removeCallbacks(hideRunnable)

    override fun onTrackerDoubleTap() {
        haptics.tick()
        Defaults.DOUBLE_TAP_ACTION.perform(service)
        scheduleHide()
    }

    override fun onTrackerTouchEnd(flung: Boolean) {
        if (flung && armedEdge == null && Defaults.HIDE_ON_FLING) endSession() else onDragFinished()
    }

    override fun onTouchOutside() {
        if (!injecting) endSession()
    }
}
