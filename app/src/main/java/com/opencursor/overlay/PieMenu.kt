package com.opencursor.overlay

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.opencursor.actions.Action
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The tracker actions pie (SPEC.md §9): a ring of actions around the pad. Slice 0 is
 * centred at the top and the rest follow clockwise. The centre selects nothing.
 */
class PieMenu(
    val centerX: Float,
    val centerY: Float,
    val actions: List<Action>,
    private val innerRadius: Float,
    private val outerRadius: Float,
) {
    var selected: Int = -1
        private set

    private val sweep = 360f / actions.size

    /** Updates the selection from a finger position; returns true when it changed. */
    fun track(x: Float, y: Float): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        val index = if (hypot(dx, dy) < innerRadius) {
            -1
        } else {
            // 0° = up, clockwise, shifted by half a slice so slice 0 is centred on top.
            val angle = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat() + 360f + sweep / 2f) % 360f
            (angle / sweep).toInt().coerceIn(0, actions.lastIndex)
        }
        if (index == selected) return false
        selected = index
        return true
    }

    fun selectedAction(): Action? = actions.getOrNull(selected)

    fun draw(canvas: Canvas, ox: Float, oy: Float, paints: Paints) {
        val cx = centerX - ox
        val cy = centerY - oy
        canvas.drawCircle(cx, cy, outerRadius, paints.outer)
        if (selected >= 0) {
            val oval = RectF(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius)
            canvas.drawArc(oval, -90f - sweep / 2f + selected * sweep, sweep, true, paints.selected)
        }
        canvas.drawCircle(cx, cy, innerRadius, paints.inner)
        val labelRadius = (innerRadius + outerRadius) / 2f
        actions.forEachIndexed { i, action ->
            val a = Math.toRadians((i * sweep - 90f).toDouble())
            val lx = cx + (cos(a) * labelRadius).toFloat()
            val ly = cy + (sin(a) * labelRadius).toFloat() - (paints.label.ascent() + paints.label.descent()) / 2f
            canvas.drawText(action.shortLabel(), lx, ly, paints.label)
        }
    }

    class Paints(val outer: Paint, val inner: Paint, val selected: Paint, val label: Paint)

    private fun Action.shortLabel(): String = when (this) {
        Action.SWIPE_UP -> "↑"
        Action.SWIPE_DOWN -> "↓"
        Action.SWIPE_LEFT -> "←"
        Action.SWIPE_RIGHT -> "→"
        Action.LONG_TAP -> "Hold"
        Action.DOUBLE_TAP -> "2×"
        Action.QUICK_SETTINGS -> "QS"
        Action.NOTIFICATIONS -> "Notif"
        Action.SCREENSHOT -> "Shot"
        Action.PREVIOUS_APP -> "Prev"
        Action.NOTHING -> ""
        else -> name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }.take(6)
    }
}
