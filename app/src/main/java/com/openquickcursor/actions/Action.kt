package com.openquickcursor.actions

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Bindable actions (SPEC.md §9). This first set covers the system buttons that
 * `performGlobalAction` provides; touch, toggle and launch actions come later.
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
    APP_DRAWER(Build.VERSION_CODES.S);

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= minSdk

    fun perform(service: AccessibilityService) {
        if (!isSupported) return
        when (this) {
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
