package com.openquickcursor

import com.openquickcursor.actions.Action
import com.openquickcursor.input.EdgeActionResolver

/** Default values from SPEC.md. Sizes are in dp, times in milliseconds. */
object Defaults {
    // Trigger zone (§5)
    const val TRIGGER_WIDTH_DP = 16f
    const val ACTIVATION_DISTANCE_DP = 20f

    // Cursor speed (§6): levels 0..11 map to these factors
    val SPEED_FACTORS = floatArrayOf(1.5f, 1.75f, 2f, 2.33f, 2.66f, 3f, 3.33f, 3.66f, 4f, 4.33f, 4.66f, 5f)

    // Simple-mode layout levels (§5–6), per orientation
    val PORTRAIT_LEVELS = Levels(position = 8, length = 5, speed = 4, cursorArea = 3, trackerAlign = 3, trackerDistance = 3)
    val LANDSCAPE_LEVELS = Levels(position = 6, length = 5, speed = 3, cursorArea = 3, trackerAlign = 6, trackerDistance = 3)

    data class Levels(
        val position: Int,
        val length: Int,
        val speed: Int,
        val cursorArea: Int,
        val trackerAlign: Int,
        val trackerDistance: Int,
    )

    // Cursor (§10)
    const val CURSOR_SIZE_DP = 40f
    const val CURSOR_TIP_DP = 6f
    const val CURSOR_STROKE_DP = 4f
    const val CURSOR_STROKE_COLOR = 0xFFFFFFFF.toInt()
    const val CURSOR_FILL_COLOR = 0x19000000
    const val CURSOR_DOT_COLOR = 0xFFFFFFFF.toInt()

    // Tracker (§7, §10)
    const val TRACKER_SIZE_DP = 100f
    const val TRACKER_INSIDE_COLOR = 0x80FFFFFF.toInt()
    const val TRACKER_OUTSIDE_COLOR = 0x80C0C0C0.toInt()
    const val CLICK_DISTANCE_DP = 6f
    const val LONG_TAP_THRESHOLD_MS = 400L
    const val DOUBLE_TAP_THRESHOLD_MS = 200L
    val DOUBLE_TAP_ACTION = Action.NOTHING
    const val HIDE_ON_FLING = true

    /** Our own choice; the original's fling threshold wasn't recovered. */
    const val FLING_VELOCITY_DP_PER_S = 2500f
    const val HIDE_TIMEOUT_MS = 2000L

    // Tracker actions pie (§9, §10)
    const val PIE_INSIDE_DP = 70f
    const val PIE_OUTSIDE_DP = 160f
    const val PIE_INSIDE_COLOR = 0xE61D2935.toInt()
    const val PIE_OUTSIDE_COLOR = 0xE62B3D4F.toInt()
    val PIE_ACTIONS = listOf(
        Action.SWIPE_DOWN, Action.LONG_TAP, Action.SWIPE_LEFT, Action.BACK,
        Action.SWIPE_UP, Action.SCREENSHOT, Action.SWIPE_RIGHT, Action.QUICK_SETTINGS,
    )

    // Gestures (§8)
    const val TAP_DURATION_MS = 5L
    const val LONG_TAP_TIMEOUT_FACTOR = 1.5f
    const val SWIPE_DURATION_MS = 80L
    const val PINCH_DURATION_MS = 500L

    // Edge actions (§6, §10)
    const val EDGE_THRESHOLD_DP = 30f
    const val EDGE_DELAY_MS = 500L
    const val EDGE_BAR_DP = 8f
    const val EDGE_COLOR = 0xFFFD746C.toInt()
    const val EDGE_OPACITY = 0.75f

    /**
     * Our own starting bindings. Side edges stay off: the thumb starts outside the thumb
     * area on the trigger's side, so a side action would fire right after swiping in.
     */
    val EDGE_BINDINGS = mapOf(
        EdgeActionResolver.Edge.LEFT to listOf(Action.NOTHING),
        EdgeActionResolver.Edge.RIGHT to listOf(Action.NOTHING),
        EdgeActionResolver.Edge.TOP to listOf(Action.NOTIFICATIONS),
        EdgeActionResolver.Edge.BOTTOM to listOf(Action.NOTHING),
    )

    // Haptics (§10)
    const val VIBRATION_MS = 10L

    // Click feedback (§10)
    const val RIPPLE_SIZE_DP = 80f
    const val RIPPLE_DURATION_MS = 500L
    const val RIPPLE_COLOR = 0xFFFD746C.toInt()
}
