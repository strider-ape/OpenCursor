package com.openquickcursor

/** Default values from SPEC.md. Sizes are in dp, times in milliseconds. */
object Defaults {
    // Trigger zone (§5)
    const val TRIGGER_WIDTH_DP = 16f
    const val TRIGGER_HEIGHT_FRACTION = 0.4f
    const val TRIGGER_CENTER_Y_FRACTION = 0.6f
    const val ACTIVATION_DISTANCE_DP = 20f

    // Cursor speed (§6): levels 0..11 map to these factors
    val SPEED_FACTORS = floatArrayOf(1.5f, 1.75f, 2f, 2.33f, 2.66f, 3f, 3.33f, 3.66f, 4f, 4.33f, 4.66f, 5f)
    const val PORTRAIT_SPEED_LEVEL = 4
    const val LANDSCAPE_SPEED_LEVEL = 3

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
    const val HIDE_TIMEOUT_MS = 2000L

    // Gestures (§8)
    const val TAP_DURATION_MS = 5L
    const val LONG_TAP_TIMEOUT_FACTOR = 1.5f

    // Haptics (§10)
    const val VIBRATION_MS = 10L

    // Click feedback (§10)
    const val RIPPLE_SIZE_DP = 80f
    const val RIPPLE_DURATION_MS = 500L
    const val RIPPLE_COLOR = 0xFFFD746C.toInt()
}
