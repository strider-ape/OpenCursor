package com.opencursor.engine

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/**
 * Live media-volume control for the edge volume bar (SPEC.md §9). Moving the cursor
 * drags the slider's handle 1:1: the full 0–100 % range is [trackWidth] pixels of
 * cursor travel, starting from the current volume, so nothing jumps when it opens.
 * At either end the anchor follows the finger, so reversing responds immediately.
 */
class VolumeBar(context: Context, startX: Float, private val trackWidth: Float) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val max = audio.getStreamMaxVolume(STREAM)
    private var level = audio.getStreamVolume(STREAM)

    private var anchorX = startX
    private var anchorPosition = if (max == 0) 0f else level / max.toFloat()

    /** Continuous handle position 0..1, which glides between volume steps. */
    var position = anchorPosition
        private set

    /**
     * Follows the cursor's unclamped x, so the ends stay reachable even when the
     * cursor is pressed against a screen edge. Returns true when the volume step changed.
     */
    fun update(cursorX: Float): Boolean {
        val raw = anchorPosition + (cursorX - anchorX) / trackWidth
        position = raw.coerceIn(0f, 1f)
        if (raw != position) {
            anchorX = cursorX
            anchorPosition = position
        }
        val target = (position * max).roundToInt()
        if (target == level) return false
        level = target
        audio.setStreamVolume(STREAM, target, 0)
        return true
    }

    companion object {
        const val STREAM = AudioManager.STREAM_MUSIC

        /** One-shot use (pie, double tap): just bring up the system volume panel. */
        fun showPanel(context: Context) {
            context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(STREAM, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }
    }
}
