package com.openquickcursor.input

import kotlin.math.abs

/**
 * Computes the "simple mode" layout (SPEC.md §5–6): for each side, the trigger strip,
 * the area the thumb moves in, and the area the cursor covers. All values come from
 * slider levels, so the same settings give the same layout on any screen.
 *
 * Coordinates are in pixels for the current orientation; [short] and [long] are the
 * display's short and long sides.
 */
object TriggerLayout {

    enum class Side { LEFT, RIGHT }

    /** A plain rectangle so the layout can be unit-tested without Android. */
    data class Box(val left: Float, val top: Float, val width: Float, val height: Float) {
        val right get() = left + width
        val bottom get() = top + height
        val centerX get() = left + width / 2f
    }

    data class Zone(val side: Side, val trigger: Box, val move: Box, val cursor: Box)

    data class Levels(
        /** 0–10. Portrait: 5–10 side edge (10 = centre, 5 = bottom), 0–4 bottom edge. Landscape: the reverse split. */
        val position: Int,
        /** 0–10, scales the trigger length. */
        val length: Int,
        /** 0–11, index into [com.openquickcursor.Defaults.SPEED_FACTORS]. */
        val speedFactor: Float,
        /** 0–7: the cursor covers (n + 1) / 8 of the screen, from the top. */
        val cursorArea: Int,
        /** 0–6: where the thumb area sits along the trigger (n / 6 of its length). */
        val trackerAlign: Int,
        /** 0–10: thumb area inset from the trigger edge, in steps of 1/32 (side) or 1/48 (bottom) of the screen. */
        val trackerDistance: Int,
    )

    /** Trigger length multipliers for length levels 0–10 (base length = 200 dp on a side edge). */
    private val LENGTH_FACTORS = floatArrayOf(0.25f, 0.4f, 0.55f, 0.7f, 0.85f, 1f, 1.2f, 1.4f, 1.6f, 1.8f, 2f)

    fun zones(
        sides: List<Side>,
        width: Int,
        height: Int,
        levels: Levels,
        triggerThickness: Float,
        dp200: Float,
    ): List<Zone> {
        val portrait = width < height
        val cursor = cursorArea(width, height, levels.cursorArea)
        return sides.map { side ->
            val trigger = triggerArea(side, width, height, portrait, levels, triggerThickness, dp200)
            Zone(side, trigger, moveArea(side, width, height, portrait, trigger, cursor, levels), cursor)
        }
    }

    private fun lengthFactor(level: Int) = LENGTH_FACTORS[level.coerceIn(0, LENGTH_FACTORS.lastIndex)]

    private fun cursorArea(width: Int, height: Int, level: Int): Box =
        Box(0f, 0f, width.toFloat(), (height / 8 * (level.coerceIn(0, 7) + 1)).toFloat())

    /** Side triggers in portrait use positions 5–10; landscape uses 0–4. */
    private fun onSideEdge(portrait: Boolean, position: Int) = if (portrait) position >= 5 else position < 5

    private fun triggerArea(
        side: Side,
        width: Int,
        height: Int,
        portrait: Boolean,
        levels: Levels,
        thickness: Float,
        dp200: Float,
    ): Box {
        val left = side == Side.LEFT
        val p = levels.position
        val factor = lengthFactor(levels.length)
        if (onSideEdge(portrait, p)) {
            val length: Float
            val top: Float
            if (portrait) {
                length = (factor * dp200).toInt().toFloat()
                top = (height / 2 + ((height / 2f - length) / 5f * abs(p - 10)).toInt()).toFloat()
            } else {
                length = (factor * (height / 4f)).toInt().toFloat()
                top = (height / 2 - length + ((height / 2f) / 5f * (p + 1)).toInt())
            }
            val x = if (left) 0f else width - thickness
            return Box(x, top, thickness, length)
        }
        // Bottom edge: two strips placed symmetrically, moving outward with the position.
        val length: Float
        val inset: Float
        if (portrait) {
            length = (factor * (width / 3f)).toInt().toFloat()
            inset = ((width / 2f - length) / 4f * (4 - p)).toInt().toFloat()
        } else {
            length = (factor * dp200).toInt().toFloat()
            inset = ((width / 2f - length) / 5f * (p - 5)).toInt().toFloat()
        }
        val x = if (left) inset else width - length - inset
        return Box(x, height - thickness, length, thickness)
    }

    private fun moveArea(
        side: Side,
        width: Int,
        height: Int,
        portrait: Boolean,
        trigger: Box,
        cursor: Box,
        levels: Levels,
    ): Box {
        val w = (cursor.width / levels.speedFactor).toInt().toFloat()
        val h = (cursor.height / levels.speedFactor).toInt().toFloat()
        val sign = if (side == Side.LEFT) 1 else -1
        val align = levels.trackerAlign
        val distance = levels.trackerDistance
        val left: Float
        val top: Float
        if (onSideEdge(portrait, levels.position)) {
            // Beside a side trigger: inset from the edge, aligned along the trigger.
            val inset = (width / 32 * distance).toFloat()
            left = if (side == Side.LEFT) inset else width - w - inset
            val triggerMid = (trigger.top + trigger.height / 2f).toInt()
            top = ((trigger.height / 6f * align + triggerMid - trigger.height / 2f).toInt() - h.toInt() / 2).toFloat()
        } else {
            // Above a bottom trigger: lifted off the bottom, shifted along the trigger.
            top = height - h - height / 48 * distance
            val triggerMid = trigger.centerX.toInt()
            left = (trigger.width / 2f * sign * -1 + (triggerMid - w.toInt() / 2) +
                (trigger.width / 6f * align * sign).toInt()).toInt().toFloat()
        }
        return Box(left, top, w, h)
    }
}
