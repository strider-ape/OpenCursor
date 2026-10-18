package com.openquickcursor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import com.openquickcursor.Defaults
import kotlin.math.hypot

/**
 * The round touch pad under the thumb (SPEC.md §7). Dragging it moves the cursor;
 * a short tap taps at the cursor; a second quick tap is a double tap (when enabled);
 * holding it long-taps; a fast fling or a touch elsewhere dismisses it.
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
        fun onTrackerDoubleTap()
        fun onTrackerLongTap()
        fun onTrackerTouchStart()

        /** Finger lifted; [flung] is true when it left the pad fast enough to count as a fling. */
        fun onTrackerTouchEnd(flung: Boolean)
        fun onTouchOutside()

        /** While the pie is open, finger moves pick a slice and lifting runs it. */
        fun onPieDrag(x: Float, y: Float)
        fun onPieRelease()
    }

    /** Set by the engine while the actions pie is open. */
    var pieMode = false

    /** Hold time before a press counts as a long tap. */
    var longTapMs = Defaults.LONG_TAP_THRESHOLD_MS

    /** When false, taps fire at once; when true, a tap waits briefly for a second one. */
    var doubleTapEnabled = false

    private val clickDistance = Overlays.dp(context, Defaults.CLICK_DISTANCE_DP)
    private val flingVelocity = Overlays.dp(context, Defaults.FLING_VELOCITY_DP_PER_S)
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
    private var velocity: VelocityTracker? = null

    /** Set while a first tap waits to see whether a second one follows. */
    private var tapPending = false

    private val longTapRunnable = Runnable {
        if (!moved) {
            longTapped = true
            listener.onTrackerLongTap()
        }
    }

    private val singleTapRunnable = Runnable {
        tapPending = false
        listener.onTrackerTap()
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
                velocity?.recycle()
                velocity = VelocityTracker.obtain().also { it.addMovement(event) }
                listener.onTrackerTouchStart()
                handler.postDelayed(longTapRunnable, longTapMs)
            }
            MotionEvent.ACTION_MOVE -> {
                if (pieMode) {
                    listener.onPieDrag(x, y)
                    return true
                }
                velocity?.addMovement(event)
                if (!moved && hypot(x - downX, y - downY) > clickDistance) {
                    moved = true
                    handler.removeCallbacks(longTapRunnable)
                    cancelPendingTap()
                }
                if (moved) listener.onTrackerMove(x - grabDx, y - grabDy)
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longTapRunnable)
                if (pieMode) {
                    pieMode = false
                    listener.onPieRelease()
                    return true
                }
                if (!moved && !longTapped) onTapUp()
                listener.onTrackerTouchEnd(moved && isFling(event))
                velocity?.recycle()
                velocity = null
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longTapRunnable)
                listener.onTrackerTouchEnd(false)
                velocity?.recycle()
                velocity = null
            }
        }
        return true
    }

    private fun onTapUp() {
        when {
            !doubleTapEnabled -> listener.onTrackerTap()
            tapPending -> {
                cancelPendingTap()
                listener.onTrackerDoubleTap()
            }
            else -> {
                tapPending = true
                handler.postDelayed(singleTapRunnable, Defaults.DOUBLE_TAP_THRESHOLD_MS)
            }
        }
    }

    private fun cancelPendingTap() {
        tapPending = false
        handler.removeCallbacks(singleTapRunnable)
    }

    private fun isFling(event: MotionEvent): Boolean {
        val v = velocity ?: return false
        v.addMovement(event)
        v.computeCurrentVelocity(1000)
        return hypot(v.xVelocity, v.yVelocity) > flingVelocity
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(longTapRunnable)
        cancelPendingTap()
        velocity?.recycle()
        velocity = null
        super.onDetachedFromWindow()
    }
}
