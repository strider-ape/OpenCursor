package com.opencursor.engine

import com.opencursor.input.TriggerLayout

/** What a settings slider wants shown on screen while it is being dragged. */
sealed interface Preview {
    /** Trigger strips, thumb areas and cursor area for the edited orientation. */
    data class Layout(
        val levels: TriggerLayout.Levels,
        val portrait: Boolean,
        val thicknessDp: Float,
        val left: Boolean,
        val right: Boolean,
    ) : Preview

    data class CursorSize(val dp: Float) : Preview
    data class PadSize(val dp: Float) : Preview
    data class EdgePush(val dp: Float) : Preview
}

/**
 * Hands previews from the settings screen to the running cursor service. Both live in
 * the same app process; when the service is off, previews are simply dropped.
 */
object LivePreview {
    @Volatile
    var sink: ((Preview) -> Unit)? = null

    fun show(preview: Preview) {
        sink?.invoke(preview)
    }
}
