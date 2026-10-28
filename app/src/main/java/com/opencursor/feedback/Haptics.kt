package com.opencursor.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.opencursor.Defaults

/** Short vibration ticks for cursor actions (SPEC.md §10). */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** A barely-there click, like a dial detent, for each volume step. */
    fun lightTick() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            v.vibrate(VibrationEffect.createOneShot(4, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    fun tick() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(Defaults.VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
