package com.opencursor.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import kotlin.math.min

/**
 * The edge volume bar, drawn as a Material 3 slider on a floating card: thick active
 * track, a gap, a tall thin handle, a lighter inactive track and a stop dot. Colours
 * come from the phone's Material You palette on Android 12+, in light or dark.
 */
class VolumeSlider(private val context: Context) {

    private fun dp(v: Float) = Overlays.dp(context, v)

    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val active = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inactive = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(14f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(14f)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
    }
    private val path = Path()
    private var dark: Boolean? = null

    /** Re-reads the palette when light/dark mode changes. */
    private fun updateColors() {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (isDark == dark) return
        dark = isDark
        val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        fun sys(light: Int, night: Int, fallbackLight: Int, fallbackNight: Int): Int =
            if (dynamic) context.getColor(if (isDark) night else light) else if (isDark) fallbackNight else fallbackLight
        val primary = sys(android.R.color.system_accent1_600, android.R.color.system_accent1_200, 0xFF65558F.toInt(), 0xFFD0BCFF.toInt())
        val secondaryContainer = sys(android.R.color.system_accent1_100, android.R.color.system_accent1_700, 0xFFE8DEF8.toInt(), 0xFF4A4458.toInt())
        val surface = sys(android.R.color.system_neutral1_50, android.R.color.system_neutral1_900, 0xFFF7F2FA.toInt(), 0xFF1D1B20.toInt())
        val onSurface = sys(android.R.color.system_neutral1_900, android.R.color.system_neutral1_100, 0xFF1D1B20.toInt(), 0xFFE6E0E9.toInt())
        card.color = surface
        card.setShadowLayer(dp(12f), 0f, dp(4f), 0x40000000)
        active.color = primary
        handle.color = primary
        inactive.color = secondaryContainer
        label.color = onSurface
        value.color = primary
    }

    /** Draws the card centred at [centerX] with its top at [top]; [level] is 0..1. */
    fun draw(canvas: Canvas, level: Float, centerX: Float, top: Float) {
        updateColors()
        val l = level.coerceIn(0f, 1f)
        val width = dp(CARD_WIDTH_DP)
        val box = RectF(centerX - width / 2f, top, centerX + width / 2f, top + dp(84f))
        canvas.drawRoundRect(box, dp(28f), dp(28f), card)

        val pad = dp(PAD_DP)
        val left = box.left + pad
        val right = box.right - pad
        val textY = box.top + dp(30f)
        canvas.drawText("Media volume", left, textY, label)
        canvas.drawText("${(l * 100).toInt()}%", right, textY, value)

        // Track geometry, following Material 3's slider: 16 dp track, 4 dp handle, 6 dp gaps.
        val trackH = dp(16f)
        val cy = box.top + dp(58f)
        val handleW = dp(4f)
        val gap = dp(6f)
        val outer = trackH / 2f
        val inner = dp(2f)
        val handleX = left + (right - left) * l

        val activeRight = handleX - handleW / 2f - gap
        if (activeRight - left > inner) {
            roundedBar(left, cy - trackH / 2f, activeRight, cy + trackH / 2f, outer, inner)
            canvas.drawPath(path, active)
        }
        val inactiveLeft = handleX + handleW / 2f + gap
        if (right - inactiveLeft > inner) {
            roundedBar(inactiveLeft, cy - trackH / 2f, right, cy + trackH / 2f, inner, outer)
            canvas.drawPath(path, inactive)
            // Stop indicator at the end of the track.
            canvas.drawCircle(right - outer, cy, dp(2f), active)
        }
        val handleH = dp(44f)
        canvas.drawRoundRect(
            RectF(handleX - handleW / 2f, cy - handleH / 2f, handleX + handleW / 2f, cy + handleH / 2f),
            handleW / 2f, handleW / 2f, handle,
        )
    }

    /** A bar with [leftRadius] corners on its left end and [rightRadius] on its right. */
    private fun roundedBar(l: Float, t: Float, r: Float, b: Float, leftRadius: Float, rightRadius: Float) {
        val lr = min(leftRadius, (r - l) / 2f)
        val rr = min(rightRadius, (r - l) / 2f)
        path.reset()
        path.addRoundRect(RectF(l, t, r, b), floatArrayOf(lr, lr, rr, rr, rr, rr, lr, lr), Path.Direction.CW)
    }

    companion object {
        /** A compact card: about a third of a phone's width. */
        const val CARD_WIDTH_DP = 280f
        const val PAD_DP = 20f

        /** Cursor travel for the full 0–100 % range: the track's own width. */
        fun trackWidthPx(context: Context) = Overlays.dp(context, CARD_WIDTH_DP - 2 * PAD_DP)

        /** Keeps the card on screen with a 16 dp margin while centring it on [x]. */
        fun clampCenter(context: Context, x: Float, screenWidth: Int): Float {
            val half = Overlays.dp(context, CARD_WIDTH_DP) / 2f + Overlays.dp(context, 16f)
            return x.coerceIn(half, (screenWidth - half).coerceAtLeast(half))
        }
    }
}
