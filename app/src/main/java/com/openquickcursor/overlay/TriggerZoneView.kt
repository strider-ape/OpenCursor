package com.openquickcursor.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.openquickcursor.Defaults
import com.openquickcursor.input.TriggerLayout
import kotlin.math.hypot

/**
 * Invisible strip on a screen edge (SPEC.md §5). Swiping inward past the activation
 * distance starts a cursor session; the rest of that swipe drives the cursor.
 */
@SuppressLint("ViewConstructor")
class TriggerZoneView(
    context: Context,
    val side: TriggerLayout.Side,
    private val listener: Listener,
) : View(context) {

    interface Listener {
        fun onTriggerActivated(side: TriggerLayout.Side, x: Float, y: Float)
        fun onTriggerDrag(x: Float, y: Float)
        fun onTriggerReleased(x: Float, y: Float)
    }

    private val activationDistance = Overlays.dp(context, Defaults.ACTIVATION_DISTANCE_DP)
    private var downX = 0f
    private var downY = 0f
    private var activated = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.rawX
        val y = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x
                downY = y
                activated = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!activated && hypot(x - downX, y - downY) > activationDistance) {
                    activated = true
                    listener.onTriggerActivated(side, x, y)
                }
                if (activated) listener.onTriggerDrag(x, y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activated) listener.onTriggerReleased(x, y)
                activated = false
            }
        }
        return true
    }
}
