package com.opencursor.actions

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.opencursor.inject.GestureInjector

/**
 * Bindable actions (SPEC.md §9). This first set covers the system buttons that
 * `performGlobalAction` provides plus gestures at the cursor; toggles and launches come later.
 */
enum class Action(private val minSdk: Int = Build.VERSION_CODES.O) {
    NOTHING,
    BACK,
    HOME,
    RECENTS,
    PREVIOUS_APP,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    POWER_MENU,
    SPLIT_SCREEN,
    LOCK_SCREEN(Build.VERSION_CODES.P),
    SCREENSHOT(Build.VERSION_CODES.P),
    APP_DRAWER(Build.VERSION_CODES.S),

    // Gestures performed at the cursor
    TAP,
    LONG_TAP,
    DOUBLE_TAP,
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    PINCH_IN,
    PINCH_OUT,

    /** As an edge action: slide left/right to set media volume. Elsewhere: shows the volume panel. */
    VOLUME_BAR;

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= minSdk

    /** Runs the action; gesture actions happen at ([x], [y]), usually the cursor. */
    fun perform(service: AccessibilityService, x: Float = -1f, y: Float = -1f) {
        if (!isSupported) return
        val inject by lazy { GestureInjector(service) }
        when (this) {
            TAP -> inject.tap(x, y)
            LONG_TAP -> inject.longTap(x, y)
            DOUBLE_TAP -> inject.doubleTap(x, y)
            SWIPE_UP -> inject.swipe(x, y, 0, -1)
            SWIPE_DOWN -> inject.swipe(x, y, 0, 1)
            SWIPE_LEFT -> inject.swipe(x, y, -1, 0)
            SWIPE_RIGHT -> inject.swipe(x, y, 1, 0)
            PINCH_IN -> inject.pinch(x, y, zoomIn = true)
            PINCH_OUT -> inject.pinch(x, y, zoomIn = false)
            VOLUME_BAR -> com.opencursor.engine.VolumeBar.showPanel(service)
            NOTHING -> Unit
            BACK -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            HOME -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            RECENTS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            PREVIOUS_APP -> {
                // Opening recents twice in quick succession switches to the previous app.
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                Handler(Looper.getMainLooper()).postDelayed({
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                }, PREVIOUS_APP_DELAY_MS)
            }
            NOTIFICATIONS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            QUICK_SETTINGS -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            POWER_MENU -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            SPLIT_SCREEN -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            LOCK_SCREEN -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            SCREENSHOT -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            APP_DRAWER -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS)
            }
        }
    }

    private companion object {
        const val PREVIOUS_APP_DELAY_MS = 300L
    }
}
