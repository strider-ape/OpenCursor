package com.openquickcursor.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.min

/**
 * The floating volume slider shown while an edge volume bar is active: a dark glass
 * capsule with a speaker glyph, a caption, a glowing gradient track and the level.
 */
class VolumeBarArt(private val context: Context) {

    private fun dp(v: Float) = Overlays.dp(context, v)

    private val capsule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xEB15151B.toInt()
        setShadowLayer(dp(18f), 0f, dp(6f), 0x73000000)
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = 0x24FFFFFF
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFFFFF }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ACCENT
        alpha = 70
    }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(dp(4f), 0f, dp(1f), 0x55000000)
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xA6FFFFFF.toInt()
        textSize = dp(11f)
        letterSpacing = 0.08f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val percent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = dp(17f)
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }
    private var gradientLeft = Float.NaN
    private var gradientRight = Float.NaN

    /** Draws the capsule centred at [centerX] with its top at [top]; [level] is 0..1. */
    fun draw(canvas: Canvas, level: Float, centerX: Float, top: Float, screenWidth: Int) {
        val l = level.coerceIn(0f, 1f)
        val width = min(screenWidth * 0.82f, dp(360f))
        val height = dp(68f)
        val box = RectF(centerX - width / 2f, top, centerX + width / 2f, top + height)
        val radius = height / 2f
        canvas.drawRoundRect(box, radius, radius, capsule)
        canvas.drawRoundRect(box, radius, radius, rim)

        // Speaker glyph on the left, vertically centred.
        drawSpeaker(canvas, box.left + dp(30f), box.centerY(), l)

        // Caption and percentage share the upper line; the track sits below.
        val contentLeft = box.left + dp(58f)
        val contentRight = box.right - dp(24f)
        val textBaseline = box.top + dp(27f)
        canvas.drawText("MEDIA VOLUME", contentLeft, textBaseline, caption)
        canvas.drawText("${(l * 100).toInt()}%", contentRight, textBaseline + dp(1f), percent)

        val trackY = box.top + dp(46f)
        val trackH = dp(6f)
        val trackRect = RectF(contentLeft, trackY - trackH / 2f, contentRight, trackY + trackH / 2f)
        canvas.drawRoundRect(trackRect, trackH, trackH, track)

        if (gradientLeft != trackRect.left || gradientRight != trackRect.right) {
            gradientLeft = trackRect.left
            gradientRight = trackRect.right
            fill.shader = LinearGradient(
                trackRect.left, 0f, trackRect.right, 0f,
                intArrayOf(0xFFFFB36B.toInt(), ACCENT, 0xFFFF5C8A.toInt()),
                null, Shader.TileMode.CLAMP,
            )
        }
        val knobX = trackRect.left + trackRect.width() * l
        if (l > 0f) {
            canvas.drawRoundRect(RectF(trackRect.left, trackRect.top, knobX, trackRect.bottom), trackH, trackH, fill)
        }
        canvas.drawCircle(knobX, trackY, dp(15f), glow)
        canvas.drawCircle(knobX, trackY, dp(8.5f), knob)
    }

    /** Speaker body plus 0–3 sound waves; a slash when muted. */
    private fun drawSpeaker(canvas: Canvas, cx: Float, cy: Float, level: Float) {
        val s = dp(1f)
        glyph.style = Paint.Style.FILL
        val body = Path().apply {
            moveTo(cx - 9 * s, cy - 4 * s)
            lineTo(cx - 5 * s, cy - 4 * s)
            lineTo(cx + 1 * s, cy - 9 * s)
            lineTo(cx + 1 * s, cy + 9 * s)
            lineTo(cx - 5 * s, cy + 4 * s)
            lineTo(cx - 9 * s, cy + 4 * s)
            close()
        }
        canvas.drawPath(body, glyph)

        glyph.style = Paint.Style.STROKE
        if (level == 0f) {
            canvas.drawLine(cx + 5 * s, cy - 4 * s, cx + 12 * s, cy + 4 * s, glyph)
            canvas.drawLine(cx + 12 * s, cy - 4 * s, cx + 5 * s, cy + 4 * s, glyph)
            return
        }
        val waves = when {
            level < 0.34f -> 1
            level < 0.67f -> 2
            else -> 3
        }
        for (i in 1..waves) {
            val r = (2f + i * 4f) * s
            canvas.drawArc(RectF(cx + s - r, cy - r, cx + s + r, cy + r), -45f, 90f, false, glyph)
        }
    }

    private companion object {
        const val ACCENT = 0xFFFD746C.toInt()
    }
}
