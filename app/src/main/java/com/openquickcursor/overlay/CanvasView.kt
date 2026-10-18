package com.openquickcursor.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.openquickcursor.Defaults

/**
 * The full-screen, untouchable layer. It never moves; the cursor is drawn at a position
 * in screen coordinates, offset by where this window actually sits on screen.
 */
class CanvasView(context: Context) : View(context) {

    private val cursorRadius = Overlays.dp(context, Defaults.CURSOR_SIZE_DP) / 2f
    private val dotRadius = Overlays.dp(context, Defaults.CURSOR_TIP_DP) / 2f
    private val rippleMaxRadius = Overlays.dp(context, Defaults.RIPPLE_SIZE_DP) / 2f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Defaults.CURSOR_FILL_COLOR
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Overlays.dp(context, Defaults.CURSOR_STROKE_DP)
        color = Defaults.CURSOR_STROKE_COLOR
        setShadowLayer(4f, 0f, 0f, 0x66000000)
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Defaults.CURSOR_DOT_COLOR
        setShadowLayer(3f, 0f, 0f, 0x66000000)
    }
    private val ripple = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val location = IntArray(2)
    private var cursorVisible = false
    private var cursorX = 0f
    private var cursorY = 0f

    private var rippleX = 0f
    private var rippleY = 0f
    private var rippleProgress = -1f
    private var rippleAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // shadow layers need software rendering
    }

    fun showCursor(x: Float, y: Float) {
        cursorVisible = true
        cursorX = x
        cursorY = y
        invalidate()
    }

    fun hideCursor() {
        cursorVisible = false
        invalidate()
    }

    /** Expanding, fading circle at a tap point (SPEC.md §10 click feedback). */
    fun playRipple(x: Float, y: Float) {
        rippleX = x
        rippleY = y
        rippleAnimator?.cancel()
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Defaults.RIPPLE_DURATION_MS
            addUpdateListener {
                rippleProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(location)
        val ox = location[0].toFloat()
        val oy = location[1].toFloat()

        if (rippleProgress in 0f..<1f) {
            ripple.color = Defaults.RIPPLE_COLOR
            ripple.alpha = ((1f - rippleProgress) * 160).toInt()
            canvas.drawCircle(rippleX - ox, rippleY - oy, rippleMaxRadius * rippleProgress, ripple)
        }

        if (!cursorVisible) return
        val cx = cursorX - ox
        val cy = cursorY - oy
        canvas.drawCircle(cx, cy, cursorRadius, fill)
        canvas.drawCircle(cx, cy, cursorRadius, stroke)
        canvas.drawCircle(cx, cy, dotRadius, dot)
    }
}
