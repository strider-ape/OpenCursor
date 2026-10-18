package com.openquickcursor.inject

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.ViewConfiguration
import com.openquickcursor.Defaults

/** Performs taps by injecting gestures (SPEC.md §8). */
class GestureInjector(private val service: AccessibilityService) {

    /** A single 5 ms touch at ([x], [y]). */
    fun tap(x: Float, y: Float, onDone: () -> Unit = {}) =
        dispatch(x, y, Defaults.TAP_DURATION_MS, onDone)

    /** A touch held for 1.5 × the system long-press timeout. */
    fun longTap(x: Float, y: Float, onDone: () -> Unit = {}) {
        val duration = (ViewConfiguration.getLongPressTimeout() * Defaults.LONG_TAP_TIMEOUT_FACTOR).toLong()
        dispatch(x, y, duration, onDone)
    }

    /** Two taps 50 ms apart (SPEC.md §8). */
    fun doubleTap(x: Float, y: Float, onDone: () -> Unit = {}) =
        tap(x, y) { android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tap(x, y, onDone) }, 50) }

    /** Straight swipe from ([x], [y]): 35 dp x 2 (medium) long, 80 ms (SPEC.md §8). */
    fun swipe(x: Float, y: Float, dx: Int, dy: Int, onDone: () -> Unit = {}) {
        val len = dp(70f)
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + dx * len, y + dy * len)
        }
        dispatch(GestureDescription.Builder().addStroke(stroke(path, Defaults.SWIPE_DURATION_MS)).build(), onDone)
    }

    /** Two diagonal strokes around ([x], [y]); medium: 100 dp outer, 500 ms (SPEC.md §8). */
    fun pinch(x: Float, y: Float, zoomIn: Boolean, onDone: () -> Unit = {}) {
        val outer = dp(100f)
        val inner = outer / if (zoomIn) 8f else 5f
        fun diag(sign: Float) = Path().apply {
            val (from, to) = if (zoomIn) outer to inner else inner to outer
            moveTo(x + sign * from, y + sign * from)
            lineTo(x + sign * to, y + sign * to)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(stroke(diag(-1f), Defaults.PINCH_DURATION_MS))
            .addStroke(stroke(diag(1f), Defaults.PINCH_DURATION_MS))
            .build()
        dispatch(gesture, onDone)
    }

    private fun dp(v: Float) = v * service.resources.displayMetrics.density

    private fun stroke(path: Path, ms: Long) = GestureDescription.StrokeDescription(path, 0L, ms)

    private fun dispatch(gesture: GestureDescription, onDone: () -> Unit) {
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onDone()
            override fun onCancelled(gestureDescription: GestureDescription?) = onDone()
        }
        if (!service.dispatchGesture(gesture, callback, null)) onDone()
    }

    private fun dispatch(x: Float, y: Float, durationMs: Long, onDone: () -> Unit) {
        if (x < 0 || y < 0) {
            onDone()
            return
        }
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = onDone()
            override fun onCancelled(gestureDescription: GestureDescription?) = onDone()
        }
        if (!service.dispatchGesture(gesture, callback, null)) onDone()
    }
}
