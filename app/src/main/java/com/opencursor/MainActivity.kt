package com.opencursor

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import com.opencursor.service.CursorService
import com.opencursor.settings.Settings
import com.opencursor.ui.SettingsScreen

/** Home screen: shows whether the service is on and edits the saved settings. */
class MainActivity : ComponentActivity() {

    private val serviceEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = Settings(this)
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val colors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                SettingsScreen(
                    settings = settings,
                    serviceEnabled = serviceEnabled.value,
                    onOpenAccessibility = { startActivity(Intent(ACTION_ACCESSIBILITY_SETTINGS)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled.value = isServiceEnabled()
    }

    private fun isServiceEnabled(): Boolean {
        val am = getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(-1).any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                it.resolveInfo.serviceInfo.name == CursorService::class.java.name
        }
    }
}
