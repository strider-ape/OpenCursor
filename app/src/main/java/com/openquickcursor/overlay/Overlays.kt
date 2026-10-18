package com.openquickcursor.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager

/** Window parameters and screen helpers shared by all overlays (SPEC.md §3). */
object Overlays {

    private const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /** Full-screen, never touchable: draws the cursor and click feedback. */
    fun canvasParams(): WindowManager.LayoutParams = params(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
    )

    /** Edge strip that starts a cursor session when swiped. */
    fun triggerParams(width: Int, height: Int, x: Int, y: Int): WindowManager.LayoutParams = params(
        width, height, BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
    ).apply {
        this.x = x
        this.y = y
    }

    /** Touch pad under the thumb; also told about touches outside it. */
    fun trackerParams(size: Int): WindowManager.LayoutParams = params(
        size,
        size,
        BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
    )

    private fun params(width: Int, height: Int, flags: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /** Full display size in pixels, including system bars. */
    fun screenSize(context: Context): Point {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(it)
            }
        }
    }

    fun dp(context: Context, value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)
}
