package com.openquickcursor.engine

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/**
 * Live media-volume control driven by the cursor's horizontal position (SPEC.md §9
 * "volume bar"). Relative: it starts at the current volume, and moving the cursor
 * across [RANGE_FRACTION] of the screen width spans the whole volume range.
 */
class VolumeBar(context: Context, private val startX: Float, private val screenWidth: Int) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val max = audio.getStreamMaxVolume(STREAM)
    private val startLevel = audio.getStreamVolume(STREAM)
    private var level = startLevel

    /** Current volume as 0..1, for drawing. */
    val fraction: Float get() = if (max == 0) 0f else level / max.toFloat()

    fun update(cursorX: Float) {
        val delta = (cursorX - startX) / (screenWidth * RANGE_FRACTION) * max
        val target = (startLevel + delta).roundToInt().coerceIn(0, max)
        if (target == level) return
        level = target
        audio.setStreamVolume(STREAM, target, AudioManager.FLAG_SHOW_UI)
    }

    companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
        const val RANGE_FRACTION = 0.6f

        /** One-shot use (pie, double tap): just bring up the system volume panel. */
        fun showPanel(context: Context) {
            context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(STREAM, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }
    }
}
