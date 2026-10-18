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
