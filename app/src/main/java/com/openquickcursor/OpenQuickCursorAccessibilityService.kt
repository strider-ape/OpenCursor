package com.openquickcursor

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout

class OpenQuickCursorAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var trackpadView: View? = null
    private var cursorView: View? = null
    private var leftEdgeView: View? = null
    private var rightEdgeView: View? = null

    private var cursorX = 0f
    private var cursorY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f

    private val sensitivityMultiplier = 2.5f
    private val autoVanishDelay = 3000L

    private val handler = Handler(Looper.getMainLooper())
    private val autoVanishRunnable = Runnable { hideOverlays() }
    private var autoVanishRunning = false

    private var isOverlaysVisible = false

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
        val edgeWidth = dpToPx(50)
        val edgeHeight = dpToPx(400)

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

        val leftEdgeParams = WindowManager.LayoutParams(
            edgeWidth,
            edgeHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = 0
            y = 40
        }

        leftEdgeView = createEdgeView("left")
        wm.addView(leftEdgeView, leftEdgeParams)

        val rightEdgeParams = WindowManager.LayoutParams(
            edgeWidth,
            edgeHeight,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 0
            y = 40
        }

        rightEdgeView = createEdgeView("right")
        wm.addView(rightEdgeView, rightEdgeParams)

        hideOverlays()
    }

    private fun createEdgeView(side: String): View {
        return object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(80, 255, 255, 0)
                style = Paint.Style.FILL
            }
            private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 255, 200, 0)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            override fun onDraw(canvas: Canvas) {
                val cornerRadius = 12f
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
                        downX = event.rawX
                        downY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - downX
                        val isSwipeIn = if (side == "left") {
                            deltaX > dpToPx(30)
                        } else {
                            deltaX < -dpToPx(30)
                        }
                        if (isSwipeIn) {
                            showOverlays()
                            return true
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setWillNotDraw(false)
        }
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
                        cancelAutoVanish()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isDragging) {
                            val deltaX = (event.rawX - lastTouchX) * sensitivityMultiplier
                            val deltaY = (event.rawY - lastTouchY) * sensitivityMultiplier
                            lastTouchX = event.rawX
                            lastTouchY = event.rawY
                            moveCursor(deltaX, deltaY)
                            resetAutoVanish()
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
                            startAutoVanish()
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

    private fun showOverlays() {
        if (isOverlaysVisible) return
        isOverlaysVisible = true
        trackpadView?.visibility = View.VISIBLE
        cursorView?.visibility = View.VISIBLE
        startAutoVanish()
    }

    private fun hideOverlays() {
        isOverlaysVisible = false
        trackpadView?.visibility = View.INVISIBLE
        cursorView?.visibility = View.INVISIBLE
        cancelAutoVanish()
    }

    private fun startAutoVanish() {
        cancelAutoVanish()
        handler.postDelayed(autoVanishRunnable, autoVanishDelay)
        autoVanishRunning = true
    }

    private fun cancelAutoVanish() {
        if (autoVanishRunning) {
            handler.removeCallbacks(autoVanishRunnable)
            autoVanishRunning = false
        }
    }

    private fun resetAutoVanish() {
        if (autoVanishRunning) {
            handler.removeCallbacks(autoVanishRunnable)
            handler.postDelayed(autoVanishRunnable, autoVanishDelay)
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

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                handler.post { hideOverlays() }
            }
            override fun onCancelled(gestureDescription: GestureDescription) {}
        }, null)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(autoVanishRunnable)
        trackpadView?.let { windowManager?.removeView(it) }
        cursorView?.let { windowManager?.removeView(it) }
        leftEdgeView?.let { windowManager?.removeView(it) }
        rightEdgeView?.let { windowManager?.removeView(it) }
    }
}
