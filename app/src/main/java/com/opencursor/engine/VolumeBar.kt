package com.opencursor.engine

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import kotlin.math.roundToInt

/**
 * Live media-volume control driven by the cursor's horizontal position (SPEC.md §9
 * "volume bar"). Relative: it starts at the current volume, and moving the cursor
 * across [RANGE_FRACTION] of the screen width spans the whole volume range.
 * Android's own volume panel is the only UI: it opens with the bar and is kept open
 * while sliding, even when the level stops changing at either end.
 */
class VolumeBar(context: Context, private val startX: Float, private val screenWidth: Int) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val max = audio.getStreamMaxVolume(STREAM)
    private val startLevel = audio.getStreamVolume(STREAM)
    private var level = startLevel
    private var lastShown = 0L

    init {
        showPanel()
    }

    fun update(cursorX: Float) {
        val delta = (cursorX - startX) / (screenWidth * RANGE_FRACTION) * max
        val target = (startLevel + delta).roundToInt().coerceIn(0, max)
        if (target == level) {
            if (SystemClock.uptimeMillis() - lastShown > KEEP_OPEN_MS) showPanel()
            return
        }
        level = target
        audio.setStreamVolume(STREAM, target, AudioManager.FLAG_SHOW_UI)
        lastShown = SystemClock.uptimeMillis()
    }

    private fun showPanel() {
        audio.adjustStreamVolume(STREAM, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        lastShown = SystemClock.uptimeMillis()
    }

    companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
        const val RANGE_FRACTION = 0.6f

        /** Re-show the panel this often while the level isn't changing, so it doesn't time out. */
        const val KEEP_OPEN_MS = 1000L

        /** One-shot use (pie, double tap): just bring up the system volume panel. */
        fun showPanel(context: Context) {
            context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(STREAM, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }
    }
}
