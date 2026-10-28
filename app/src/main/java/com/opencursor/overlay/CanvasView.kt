package com.opencursor.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.opencursor.Defaults
import com.opencursor.input.EdgeActionResolver

/**
 * The full-screen, untouchable layer. It never moves; the cursor is drawn at a position
 * in screen coordinates, offset by where this window actually sits on screen.
 */
class CanvasView(context: Context) : View(context) {

    /** Cursor diameter in pixels, kept current by the engine. */
    var cursorSizePx = Overlays.dp(context, Defaults.CURSOR_SIZE_DP)
        set(value) {
            field = value
            invalidate()
        }
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
    private val edgeBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Defaults.EDGE_COLOR
        alpha = (Defaults.EDGE_OPACITY * 255).toInt()
    }
    private val edgeLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Defaults.EDGE_COLOR
        textSize = Overlays.dp(context, 14f)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(4f, 0f, 0f, 0xAA000000.toInt())
    }
    private val barThickness = Overlays.dp(context, Defaults.EDGE_BAR_DP)
    private val labelInset = Overlays.dp(context, 28f)
    private var edgeHit: EdgeActionResolver.Hit? = null

    /** Open tracker actions pie, drawn above the cursor; null when closed. */
    var pie: PieMenu? = null
        set(value) {
            field = value
            invalidate()
        }
    private val piePaints = PieMenu.Paints(
        outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Defaults.PIE_OUTSIDE_COLOR },
        inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Defaults.PIE_INSIDE_COLOR },
        selected = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Defaults.EDGE_COLOR; alpha = 170 },
        label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = Overlays.dp(context, 13f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        },
    )

    /** Display size in pixels, kept current by the engine. */
    var screenWidth = 0
    var screenHeight = 0

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

    /** Highlights the selected edge segment and names its action; null clears it. */
    fun showEdge(hit: EdgeActionResolver.Hit?) {
        if (hit == edgeHit) return
        edgeHit = hit
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

        edgeHit?.let { drawEdge(canvas, it, ox, oy) }

        if (!cursorVisible) return
        val cx = cursorX - ox
        val cy = cursorY - oy
        canvas.drawCircle(cx, cy, cursorSizePx / 2f, fill)
        canvas.drawCircle(cx, cy, cursorSizePx / 2f, stroke)
        canvas.drawCircle(cx, cy, dotRadius, dot)
        pie?.draw(canvas, ox, oy, piePaints)
    }


    private fun drawEdge(canvas: Canvas, hit: EdgeActionResolver.Hit, ox: Float, oy: Float) {
        val w = screenWidth.toFloat()
        val h = screenHeight.toFloat()
        val vertical = hit.edge == EdgeActionResolver.Edge.LEFT || hit.edge == EdgeActionResolver.Edge.RIGHT
        val segment = (if (vertical) h else w) / hit.segmentCount
        val start = segment * hit.segment
        val end = start + segment
        val bar = when (hit.edge) {
            EdgeActionResolver.Edge.LEFT -> RectF(0f, start, barThickness, end)
            EdgeActionResolver.Edge.RIGHT -> RectF(w - barThickness, start, w, end)
            EdgeActionResolver.Edge.TOP -> RectF(start, 0f, end, barThickness)
            EdgeActionResolver.Edge.BOTTOM -> RectF(start, h - barThickness, end, h)
        }
        bar.offset(-ox, -oy)
        canvas.drawRect(bar, edgeBar)

        val label = hit.action.name.replace('_', ' ')
        val (lx, ly) = when (hit.edge) {
            EdgeActionResolver.Edge.LEFT -> labelInset * 2 to bar.centerY()
            EdgeActionResolver.Edge.RIGHT -> bar.left - labelInset * 2 to bar.centerY()
            EdgeActionResolver.Edge.TOP -> bar.centerX() to bar.bottom + labelInset * 2
            EdgeActionResolver.Edge.BOTTOM -> bar.centerX() to bar.top - labelInset
        }
        canvas.drawText(label, lx, ly, edgeLabel)
    }
}
