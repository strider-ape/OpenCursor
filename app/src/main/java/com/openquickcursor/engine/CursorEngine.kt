package com.openquickcursor.engine

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.RectF
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.openquickcursor.Defaults
import com.openquickcursor.actions.Action
import com.openquickcursor.feedback.Haptics
import com.openquickcursor.inject.GestureInjector
import com.openquickcursor.input.CursorMapper
import com.openquickcursor.input.EdgeActionResolver
import com.openquickcursor.input.TriggerLayout
import com.openquickcursor.overlay.CanvasView
import com.openquickcursor.overlay.Overlays
import com.openquickcursor.overlay.PieMenu
import com.openquickcursor.overlay.TrackerView
import com.openquickcursor.overlay.TriggerZoneView
import com.openquickcursor.settings.Settings

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

    private val settings = Settings(service)
    private var trackerSize = 0

    private var canvas: CanvasView? = null
    private val triggers = mutableListOf<TriggerZoneView>()
    private val zones = mutableMapOf<TriggerLayout.Side, TriggerLayout.Zone>()
    private var tracker: TrackerView? = null
    private var trackerParams: WindowManager.LayoutParams? = null
    private var mapper: CursorMapper? = null
    private var screen = Point()
    private val haptics = Haptics(service)
    private var edgeResolver: EdgeActionResolver? = null

    /** Edge action currently highlighted; it fires after a short hold or on release. */
    private var armedEdge: EdgeActionResolver.Hit? = null

    /** Active while an edge volume bar is being slid. */
    private var volumeBar: VolumeBar? = null

    /** True while one of our own gestures is being injected. */
    private var injecting = false

    private val hideRunnable = Runnable { endSession() }
    private val fireEdgeRunnable = Runnable { fireEdgeAction() }

    /** Held as a field: SharedPreferences only keeps weak references to listeners. */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // An import writes dozens of values at once; rebuild once after they settle.
        handler.removeCallbacks(applySettingsRunnable)
        handler.postDelayed(applySettingsRunnable, SETTINGS_SETTLE_MS)
    }
    private val applySettingsRunnable = Runnable { if (canvas != null) applySettings() }

    fun start() {
        CanvasView(service).also {
            wm.addView(it, Overlays.canvasParams())
            canvas = it
        }
        applySettings()
        settings.prefs.registerOnSharedPreferenceChangeListener(settingsListener)
        ContextCompat.registerReceiver(
            service,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Triggers only exist while the screen is on and unlocked (SPEC.md §4 states). */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (canvas != null) applySettings()
        }
    }

    private fun screenUsable(): Boolean {
        val power = service.getSystemService(PowerManager::class.java)
        val keyguard = service.getSystemService(KeyguardManager::class.java)
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    /** Rebuilds everything that depends on settings or screen size. */
    private fun applySettings() {
        endSession()
        removeTriggers()
        updateScreen()
        canvas?.cursorSizePx = Overlays.dp(service, settings.cursorSizeDp)
        if (screenUsable()) addTriggers()
    }

    private fun updateScreen() {
        screen = Overlays.screenSize(service)
        canvas?.screenWidth = screen.x
        canvas?.screenHeight = screen.y
        edgeResolver = if (settings.edgeActionsEnabled) {
            EdgeActionResolver(settings.edgeBindings(), Overlays.dp(service, settings.edgeThresholdDp), screen.x, screen.y)
        } else {
            null
        }
    }

    fun stop() {
        try {
            service.unregisterReceiver(screenReceiver)
        } catch (_: IllegalArgumentException) {
        }
        settings.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        handler.removeCallbacks(applySettingsRunnable)
        endSession()
        removeTriggers()
        removeView(canvas)
        canvas = null
    }

    /** Rotation or display size changed: rebuild the edge triggers for the new screen. */
    fun onScreenChanged() = applySettings()

    /** Trigger strips plus their thumb and cursor areas, from the simple-mode levels (SPEC.md §5–6). */
    private fun addTriggers() {
        val portrait = screen.x < screen.y
        val sides = buildList {
            if (settings.leftTrigger) add(TriggerLayout.Side.LEFT)
            if (settings.rightTrigger) add(TriggerLayout.Side.RIGHT)
        }
        val layout = TriggerLayout.zones(
            sides = sides,
            width = screen.x,
            height = screen.y,
            levels = (if (portrait) settings.portrait else settings.landscape).toLayout(),
            triggerThickness = Overlays.dp(service, settings.triggerWidthDp).toInt().toFloat(),
            dp200 = Overlays.dp(service, 200f),
        )
        for (zone in layout) {
            zones[zone.side] = zone
            val t = zone.trigger
            TriggerZoneView(service, zone.side, this).also {
                wm.addView(it, Overlays.triggerParams(t.width.toInt(), t.height.toInt(), t.left.toInt(), t.top.toInt()))
                triggers += it
            }
        }
    }

    private fun removeTriggers() {
        triggers.forEach(::removeView)
        triggers.clear()
        zones.clear()
    }

    // --- Session

    private fun startSession(side: TriggerLayout.Side, x: Float, y: Float) {
        handler.removeCallbacks(hideRunnable)
        val zone = zones[side] ?: return
        mapper = CursorMapper(zone.move.toRectF(), zone.cursor.toRectF(), screen.x, screen.y)
        if (tracker == null) {
            trackerSize = Overlays.dp(service, settings.trackerSizeDp).toInt()
            val params = Overlays.trackerParams(trackerSize)
            TrackerView(service, this).also {
                it.doubleTapEnabled = settings.doubleTapAction != Action.NOTHING
                it.longTapMs = settings.longTapMs.toLong()
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
        volumeBar?.let {
            it.update(cursor.x)
            return
        }
        canvas?.showCursor(cursor.x, cursor.y)
        updateEdge(edgeResolver?.resolve(m.unclamped))
    }

    private fun updateEdge(hit: EdgeActionResolver.Hit?) {
        if (hit?.action == Action.VOLUME_BAR) {
            startVolumeBar()
            return
        }
        if (hit == armedEdge) return
        handler.removeCallbacks(fireEdgeRunnable)
        armedEdge = hit
        canvas?.showEdge(hit)
        if (hit != null) {
            vibrate()
            handler.postDelayed(fireEdgeRunnable, settings.edgeDelayMs.toLong())
        }
    }

    private fun fireEdgeAction() {
        val hit = armedEdge ?: return
        endSession()
        val c = mapper?.cursor
        val x = c?.x ?: -1f
        val y = c?.y ?: -1f
        endSession()
        hit.action.perform(service, x, y)
    }

    /** Finger lifted after dragging: run a highlighted edge action, otherwise start the idle timer. */
    /** Edge "volume bar": the cursor turns into a slider until the finger lifts. */
    private fun startVolumeBar() {
        val x = mapper?.cursor?.x ?: return
        handler.removeCallbacks(fireEdgeRunnable)
        armedEdge = null
        canvas?.showEdge(null)
        canvas?.hideCursor()
        volumeBar = VolumeBar(service, x, screen.x)
        vibrate()
    }

    private fun onDragFinished() {
        if (volumeBar != null) endSession() else if (armedEdge != null) fireEdgeAction() else scheduleHide()
    }

    private fun endSession() {
        volumeBar = null
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(fireEdgeRunnable)
        armedEdge = null
        canvas?.showEdge(null)
        removeView(tracker)
        tracker = null
        trackerParams = null
        mapper = null
        canvas?.pie = null
        canvas?.hideCursor()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, settings.hideTimeoutMs.toLong())
    }

    /** Injects a gesture at the cursor with the tracker made untouchable so it can't catch it. */
    private fun actAtCursor(longTap: Boolean) {
        val cursor = mapper?.cursor ?: return
        val x = cursor.x
        val y = cursor.y
        injecting = true
        setTrackerTouchable(false)
        if (settings.rippleOnClick) canvas?.playRipple(x, y)
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

    private fun vibrate() {
        if (settings.vibrateOnCursorAction) haptics.tick()
    }

    private fun TriggerLayout.Box.toRectF() = RectF(left, top, right, bottom)

    /**
     * Always asks the window manager to drop the view: a window added just before the
     * screen locked may not report attached yet, and skipping it left an orphaned pad.
     */
    private fun removeView(view: View?) {
        if (view == null) return
        try {
            wm.removeViewImmediate(view)
        } catch (_: IllegalArgumentException) {
            // Never added, or already gone.
        }
    }

    // --- Trigger zone

    override fun onTriggerActivated(side: TriggerLayout.Side, x: Float, y: Float) = startSession(side, x, y)

    override fun onTriggerDrag(x: Float, y: Float) = moveTo(x, y)

    override fun onTriggerReleased(x: Float, y: Float) = onDragFinished()

    // --- Tracker pad

    override fun onTrackerMove(centerX: Float, centerY: Float) = moveTo(centerX, centerY)

    override fun onTrackerTap() = actAtCursor(longTap = false)

    override fun onTrackerLongTap() {
        if (settings.longTapOpensPie) openPie() else actAtCursor(longTap = true)
    }

    // --- Tracker actions pie

    private fun openPie() {
        val view = tracker ?: return
        view.pieMode = true
        canvas?.pie = PieMenu(
            view.centerX,
            view.centerY,
            settings.pieActions,
            Overlays.dp(service, Defaults.PIE_INSIDE_DP) / 2f,
            Overlays.dp(service, Defaults.PIE_OUTSIDE_DP) / 2f,
        )
        vibrate()
    }

    override fun onPieDrag(x: Float, y: Float) {
        val pie = canvas?.pie ?: return
        if (pie.track(x, y)) {
            canvas?.invalidate()
            if (pie.selected >= 0) vibrate()
        }
    }

    override fun onPieRelease() {
        val action = canvas?.pie?.selectedAction()
        canvas?.pie = null
        if (action != null && action != Action.NOTHING) runAtCursor(action) else scheduleHide()
    }

    /** Runs any action at the cursor, keeping the tracker out of the way of its gesture. */
    private fun runAtCursor(action: Action) {
        val c = mapper?.cursor ?: return
        injecting = true
        setTrackerTouchable(false)
        action.perform(service, c.x, c.y)
        handler.postDelayed({
            setTrackerTouchable(true)
            injecting = false
            scheduleHide()
        }, GESTURE_SETTLE_MS)
    }

    override fun onTrackerTouchStart() = handler.removeCallbacks(hideRunnable)

    override fun onTrackerDoubleTap() {
        vibrate()
        runAtCursor(settings.doubleTapAction)
    }

    override fun onTrackerTouchEnd(flung: Boolean) {
        if (flung && armedEdge == null && settings.hideOnFling) endSession() else onDragFinished()
    }

    override fun onTouchOutside() {
        if (!injecting && settings.hideOnOutsideTouch) endSession()
    }

    private companion object {
        const val SETTINGS_SETTLE_MS = 150L

        /** Long enough for the slowest injected gesture (a 500 ms pinch). */
        const val GESTURE_SETTLE_MS = 600L
    }
}
