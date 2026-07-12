package com.openquickcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout

class OpenQuickCursorAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var trackpadView: View? = null
    private var cursorView: View? = null

    private var cursorX = 0f
    private var cursorY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f

    private val sensitivityMultiplier = 2.5f

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlays()
    }

    private fun createOverlays() {
        val wm = windowManager ?: return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val trackpadSize = (screenWidth * 0.35).toInt()
        val cursorSize = 40

        cursorX = screenWidth / 2f
        cursorY = screenHeight / 4f

        val trackpadParams = WindowManager.LayoutParams(
            trackpadSize,
            trackpadSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 40
            y = 40
        }

        trackpadView = createTrackpadView(trackpadSize)
        wm.addView(trackpadView, trackpadParams)

        val cursorParams = WindowManager.LayoutParams(
            cursorSize,
            cursorSize,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cursorX.toInt()
            y = cursorY.toInt()
        }

        cursorView = createCursorView(cursorSize)
        wm.addView(cursorView, cursorParams)
    }

    private fun createTrackpadView(size: Int): View {
        return object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 50, 100, 200)
                style = Paint.Style.FILL
            }
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 50, 100, 200)
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }

            override fun onDraw(canvas: Canvas) {
                val cornerRadius = 24f
                canvas.drawRoundRect(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    cornerRadius, cornerRadius, paint
                )
                canvas.drawRoundRect(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    cornerRadius, cornerRadius, borderPaint
                )
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                        downX = event.rawX
                        downY = event.rawY
                        downTime = event.eventTime
                        isDragging = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            val deltaX = (event.rawX - lastTouchX) * sensitivityMultiplier
                            val deltaY = (event.rawY - lastTouchY) * sensitivityMultiplier
                            lastTouchX = event.rawX
                            lastTouchY = event.rawY
                            moveCursor(deltaX, deltaY)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            val totalTime = event.eventTime - downTime
                            val totalDistanceX = Math.abs(event.rawX - downX)
                            val totalDistanceY = Math.abs(event.rawY - downY)
                            if (totalTime < 200 && totalDistanceX < 10 && totalDistanceY < 10) {
                                simulateTap()
                            }
                            isDragging = false
                        }
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            setWillNotDraw(false)
        }
    }

    private fun createCursorView(size: Int): View {
        return object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                style = Paint.Style.FILL
            }
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            override fun onDraw(canvas: Canvas) {
                val center = size / 2f
                canvas.drawCircle(center, center, center - 4, paint)
                canvas.drawCircle(center, center, center - 4, borderPaint)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            setWillNotDraw(false)
        }
    }

    private fun moveCursor(deltaX: Float, deltaY: Float) {
        val wm = windowManager ?: return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels.toFloat()
        val screenHeight = metrics.heightPixels.toFloat()

        cursorX = (cursorX + deltaX).coerceIn(0f, screenWidth - 40)
        cursorY = (cursorY + deltaY).coerceIn(0f, screenHeight - 40)

        val params = cursorView?.layoutParams as? WindowManager.LayoutParams ?: return
        params.x = cursorX.toInt()
        params.y = cursorY.toInt()
        wm.updateViewLayout(cursorView, params)
    }

    private fun simulateTap() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val path = Path().apply {
            moveTo(cursorX, cursorY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        trackpadView?.let { windowManager?.removeView(it) }
        cursorView?.let { windowManager?.removeView(it) }
    }
}
