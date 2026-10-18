package com.openquickcursor

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.openquickcursor.overlay.Overlays
import com.openquickcursor.service.CursorService

/** Minimal home screen: explains the gesture and links to the accessibility switch. */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Overlays.dp(this, 24f).toInt()
        status = TextView(this).apply { textSize = 16f }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad * 2, pad, pad)
            addView(TextView(context).apply {
                setText(R.string.main_title)
                textSize = 24f
            })
            addView(TextView(context).apply {
                setText(R.string.main_how_to)
                textSize = 16f
                setPadding(0, pad, 0, pad)
            })
            addView(status)
            addView(Button(context).apply {
                setText(R.string.main_open_settings)
                setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            })
        })
    }

    override fun onResume() {
        super.onResume()
        status.setText(if (isServiceEnabled()) R.string.main_status_on else R.string.main_status_off)
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(-1).any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name == CursorService::class.java.name
        }
    }
}
