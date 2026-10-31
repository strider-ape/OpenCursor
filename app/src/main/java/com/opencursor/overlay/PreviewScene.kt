package com.opencursor.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.opencursor.Defaults
import com.opencursor.input.TriggerLayout

/** A resolved, ready-to-draw slider preview (screen pixels). */
sealed interface PreviewScene {
    data class Layout(val zones: List<TriggerLayout.Zone>) : PreviewScene
    data class Note(val text: String) : PreviewScene
    data class Cursor(val sizePx: Float) : PreviewScene
    data class Pad(val sizePx: Float) : PreviewScene
    data class EdgePush(val distancePx: Float) : PreviewScene
}

/** Draws [PreviewScene]s: blue = cursor reach, green = thumb area, orange = swipe strip. */
class PreviewPainter(private val context: Context) {

    private fun dp(v: Float) = Overlays.dp(context, v)

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        this.color = color
    }

    private val cursorFill = fill(0x332F80ED)
    private val cursorLine = stroke(0xFF2F80ED.toInt())
    private val thumbFill = fill(0x5534C759)
    private val thumbLine = stroke(0xFF34C759.toInt())
    private val triggerFill = fill(0xE6FD746C.toInt())
    private val padFill = fill(Defaults.TRACKER_INSIDE_COLOR)
    private val padLine = stroke(Defaults.TRACKER_OUTSIDE_COLOR).apply { strokeWidth = dp(3f) }
    private val cursorRing = stroke(Defaults.CURSOR_STROKE_COLOR).apply { strokeWidth = dp(Defaults.CURSOR_STROKE_DP) }
    private val cursorDot = fill(Defaults.CURSOR_DOT_COLOR)
    private val edgeBar = fill(Defaults.EDGE_COLOR)
    private val labelBg = fill(0xCC1C1B22.toInt())
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = dp(13f)
        isFakeBoldText = true
    }

    fun draw(canvas: Canvas, scene: PreviewScene, ox: Float, oy: Float, screenW: Int, screenH: Int) {
        canvas.save()
        canvas.translate(-ox, -oy)
        val cx = screenW / 2f
        val cy = screenH / 2f
        when (scene) {
            is PreviewScene.Layout -> {
                scene.zones.firstOrNull()?.cursor?.let { c ->
                    val r = c.rect()
                    canvas.drawRect(r, cursorFill)
                    canvas.drawRect(r, cursorLine)
                    tag(canvas, "Cursor reaches here", r.left + dp(16f), r.top + dp(56f))
                }
                for (z in scene.zones) {
                    val m = z.move.rect()
                    canvas.drawRoundRect(m, dp(12f), dp(12f), thumbFill)
                    canvas.drawRoundRect(m, dp(12f), dp(12f), thumbLine)
                    tag(canvas, "Thumb area", m.left + dp(8f), m.top + dp(8f))
                    canvas.drawRect(z.trigger.rect(), triggerFill)
                }
                scene.zones.firstOrNull()?.trigger?.let { t ->
                    val left = t.left < screenW / 2f
                    val x = if (left) t.right + dp(8f) else t.left - dp(8f) - label.measureText("Swipe strip") - dp(16f)
                    tag(canvas, "Swipe strip", x, t.top + t.height / 2f - dp(14f))
                }
            }
            is PreviewScene.Note -> tag(canvas, scene.text, cx - (label.measureText(scene.text) + dp(16f)) / 2f, cy)
            is PreviewScene.Cursor -> {
                val r = scene.sizePx / 2f
                canvas.drawCircle(cx, cy, r, cursorFill)
                canvas.drawCircle(cx, cy, r, cursorRing)
                canvas.drawCircle(cx, cy, dp(Defaults.CURSOR_TIP_DP) / 2f, cursorDot)
                tag(canvas, "Cursor size", cx - dp(48f), cy + r + dp(16f))
            }
            is PreviewScene.Pad -> {
                val r = scene.sizePx / 2f
                canvas.drawCircle(cx, cy, r, padFill)
                canvas.drawCircle(cx, cy, r, padLine)
                tag(canvas, "Pad size", cx - dp(40f), cy + r + dp(16f))
            }
            is PreviewScene.EdgePush -> {
                // The top edge lit as an edge action, and a ruler showing the push distance.
                canvas.drawRect(cx - screenW * 0.25f, 0f, cx + screenW * 0.25f, dp(Defaults.EDGE_BAR_DP), edgeBar)
                val ruler = stroke(Defaults.EDGE_COLOR).apply { strokeWidth = dp(3f) }
                canvas.drawLine(cx, 0f, cx, scene.distancePx, ruler)
                canvas.drawLine(cx - dp(12f), scene.distancePx, cx + dp(12f), scene.distancePx, ruler)
                tag(canvas, "Push the cursor this far past the edge", cx - dp(130f), scene.distancePx + dp(16f))
            }
        }
        canvas.restore()
    }

    private fun TriggerLayout.Box.rect() = RectF(left, top, right, bottom)

    /** White label on a dark pill, top-left at ([x], [y]). */
    private fun tag(canvas: Canvas, text: String, x: Float, y: Float) {
        val w = label.measureText(text) + dp(16f)
        val h = dp(26f)
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), h / 2f, h / 2f, labelBg)
        canvas.drawText(text, x + dp(8f), y + h / 2f - (label.ascent() + label.descent()) / 2f, label)
    }
}
