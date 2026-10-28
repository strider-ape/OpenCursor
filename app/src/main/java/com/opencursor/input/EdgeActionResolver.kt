package com.opencursor.input

import android.graphics.PointF
import com.opencursor.actions.Action
import kotlin.math.ceil

/**
 * Turns cursor overshoot past a screen edge into an edge action (SPEC.md §6).
 * Each edge is split into equal segments, each bound to its own action.
 */
class EdgeActionResolver(
    private val bindings: Map<Edge, List<Action>>,
    private val thresholdPx: Float,
    private val screenWidth: Int,
    private val screenHeight: Int,
) {

    enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

    data class Hit(val edge: Edge, val segment: Int, val segmentCount: Int, val action: Action)

    /** The edge action selected by an unclamped cursor point, or null when none is. */
    fun resolve(unclamped: PointF): Hit? {
        val overshoots = mapOf(
            Edge.LEFT to -unclamped.x,
            Edge.RIGHT to unclamped.x - (screenWidth - 1),
            Edge.TOP to -unclamped.y,
            Edge.BOTTOM to unclamped.y - (screenHeight - 1),
        )
        val (edge, overshoot) = overshoots.maxBy { it.value }
        if (overshoot <= thresholdPx) return null

        val actions = bindings[edge].orEmpty()
        if (actions.isEmpty()) return null
        val vertical = edge == Edge.LEFT || edge == Edge.RIGHT
        val length = if (vertical) screenHeight else screenWidth
        val position = (if (vertical) unclamped.y else unclamped.x).coerceIn(1f, length.toFloat())
        val index = ceil(position / (length.toFloat() / actions.size)).toInt().coerceIn(1, actions.size) - 1
        val action = actions[index]
        if (action == Action.NOTHING || !action.isSupported) return null
        return Hit(edge, index, actions.size, action)
    }
}
