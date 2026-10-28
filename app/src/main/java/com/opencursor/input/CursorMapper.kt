package com.opencursor.input

import android.graphics.PointF
import android.graphics.RectF

/**
 * Linear finger-to-cursor mapping (SPEC.md §6): a point in [source] (where the thumb moves)
 * maps to the same relative point in [target] (where the cursor moves). No acceleration.
 */
class CursorMapper(val source: RectF, val target: RectF, private val screenWidth: Int, private val screenHeight: Int) {

    /** Cursor position before clamping; overshoot past an edge will drive edge actions. */
    val unclamped = PointF()

    /** Cursor position clamped to the screen. */
    val cursor = PointF()

    fun map(fingerX: Float, fingerY: Float): PointF {
        unclamped.x = (fingerX - source.left) / source.width() * target.width() + target.left
        unclamped.y = (fingerY - source.top) / source.height() * target.height() + target.top
        cursor.x = unclamped.x.coerceIn(0f, (screenWidth - 1).toFloat())
        cursor.y = unclamped.y.coerceIn(0f, (screenHeight - 1).toFloat())
        return cursor
    }
}
