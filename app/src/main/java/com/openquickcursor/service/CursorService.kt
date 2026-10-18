package com.openquickcursor.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import com.openquickcursor.engine.CursorEngine

/** Hosts the cursor engine for as long as the user keeps the accessibility service on. */
class CursorService : AccessibilityService() {

    private var engine: CursorEngine? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine?.stop()
        engine = CursorEngine(this).also { it.start() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        engine?.onScreenChanged()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        engine?.stop()
        engine = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        engine?.stop()
        engine = null
        super.onDestroy()
    }
}
