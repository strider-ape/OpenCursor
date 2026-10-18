package com.openquickcursor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.openquickcursor.Defaults
import kotlin.math.hypot

/**
 * The round touch pad under the thumb (SPEC.md §7). Dragging it moves the cursor;
 * a short tap taps at the cursor; holding it long-taps; touching elsewhere dismisses.
 */
@SuppressLint("ViewConstructor")
class TrackerView(
    context: Context,
    private val listener: Listener,
) : View(context) {

    interface Listener {
        /** Raw finger position, already corrected for where the pad was grabbed. */
        fun onTrackerMove(centerX: Float, centerY: Float)
        fun onTrackerTap()
        fun onTrackerLongTap()
        fun onTrackerTouchStart()
        fun onTrackerTouchEnd()
        fun onTouchOutside()
    }

    private val clickDistance = Overlays.dp(context, Defaults.CLICK_DISTANCE_DP)
    private val handler = Handler(Looper.getMainLooper())

    private val inside = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Defaults.TRACKER_INSIDE_COLOR
    }
    private val outside = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Overlays.dp(context, 3f)
        color = Defaults.TRACKER_OUTSIDE_COLOR
    }

    /** Pad centre in screen coordinates, kept by the engine. */
    var centerX = 0f
    var centerY = 0f

    private var downX = 0f
    private var downY = 0f
    private var grabDx = 0f
    private var grabDy = 0f
    private var moved = false
    private var longTapped = false

    private val longTapRunnable = Runnable {
        if (!moved) {
            longTapped = true
            listener.onTrackerLongTap()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        canvas.drawCircle(r, r, r - outside.strokeWidth, inside)
        canvas.drawCircle(r, r, r - outside.strokeWidth, outside)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.rawX
        val y = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> listener.onTouchOutside()
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                grabDx = x - centerX
                grabDy = y - centerY
                moved = false
                longTapped = false
                listener.onTrackerTouchStart()
                handler.postDelayed(longTapRunnable, Defaults.LONG_TAP_THRESHOLD_MS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(x - downX, y - downY) > clickDistance) {
                    moved = true
                    handler.removeCallbacks(longTapRunnable)
                }
                if (moved) listener.onTrackerMove(x - grabDx, y - grabDy)
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longTapRunnable)
                if (!moved && !longTapped) listener.onTrackerTap()
                listener.onTrackerTouchEnd()
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longTapRunnable)
                listener.onTrackerTouchEnd()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longTapRunnable)
        super.onDetachedFromWindow()
    }
}
