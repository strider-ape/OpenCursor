package com.openquickcursor.input

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

    companion object {
        /**
         * Builds the mapping for a session that starts at ([fingerX], [fingerY]) on the
         * [fromRight] edge. The cursor covers the whole screen; the thumb area is the screen
         * shrunk by [speed], pinned to the trigger edge and centred on the starting height.
         */
        fun forEdgeSession(
            fingerX: Float,
            fingerY: Float,
            fromRight: Boolean,
            speed: Float,
            screenWidth: Int,
            screenHeight: Int,
        ): CursorMapper {
            val target = RectF(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())
            val w = screenWidth / speed
            val h = screenHeight / speed
            val left = if (fromRight) screenWidth - w else 0f
            val top = (fingerY - h / 2f).coerceIn(0f, screenHeight - h)
            return CursorMapper(RectF(left, top, left + w, top + h), target, screenWidth, screenHeight)
        }
    }
}
