package com.openquickcursor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openquickcursor.Defaults
import com.openquickcursor.settings.Settings
import kotlin.math.roundToInt

/**
 * The home screen: service status plus every saved setting, grouped like the
 * original app's settings screens (SPEC.md §10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, serviceEnabled: Boolean, onOpenAccessibility: () -> Unit) {
    // Settings live in SharedPreferences, not Compose state; bumping this re-reads them.
    var revision by remember { mutableIntStateOf(0) }
    fun save(block: Settings.() -> Unit) {
        settings.block()
        revision++
    }

    Scaffold(topBar = { TopAppBar(title = { Text("OpenQuickCursor") }) }) { padding ->
        key(revision) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusCard(serviceEnabled, onOpenAccessibility)

                SettingsSection("Triggers") {
                    SwitchRow("Left edge", settings.leftTrigger) { save { leftTrigger = it } }
                    SwitchRow("Right edge", settings.rightTrigger) { save { rightTrigger = it } }
                    SliderRow("Width", settings.triggerWidthDp, 8f..48f, 2f, { "${it.roundToInt()} dp" }) {
                        save { triggerWidthDp = it }
                    }
                    SliderRow("Height", settings.triggerHeightPercent.toFloat(), 10f..100f, 5f, { "${it.roundToInt()} %" }) {
                        save { triggerHeightPercent = it.roundToInt() }
                    }
                    SliderRow("Position", settings.triggerCenterPercent.toFloat(), 10f..90f, 5f, { "${it.roundToInt()} % down" }) {
                        save { triggerCenterPercent = it.roundToInt() }
                    }
                }

                SettingsSection("Cursor") {
                    SliderRow("Speed (portrait)", settings.portraitSpeedLevel.toFloat(), 0f..11f, 1f, ::speedLabel) {
                        save { portraitSpeedLevel = it.roundToInt() }
                    }
                    SliderRow("Speed (landscape)", settings.landscapeSpeedLevel.toFloat(), 0f..11f, 1f, ::speedLabel) {
                        save { landscapeSpeedLevel = it.roundToInt() }
                    }
                    SliderRow("Size", settings.cursorSizeDp, 20f..80f, 2f, { "${it.roundToInt()} dp" }) {
                        save { cursorSizeDp = it }
                    }
                }

                SettingsSection("Tracker") {
                    SliderRow("Size", settings.trackerSizeDp, 60f..160f, 5f, { "${it.roundToInt()} dp" }) {
                        save { trackerSizeDp = it }
                    }
                    SliderRow("Long tap after", settings.longTapMs.toFloat(), 200f..1000f, 50f, { "${it.roundToInt()} ms" }) {
                        save { longTapMs = it.roundToInt() }
                    }
                    SliderRow("Hide after idle", settings.hideTimeoutMs.toFloat(), 500f..5000f, 250f, { "${it.roundToInt()} ms" }) {
                        save { hideTimeoutMs = it.roundToInt() }
                    }
                    SwitchRow("Hide when flung", settings.hideOnFling) { save { hideOnFling = it } }
                    SwitchRow("Hide on touch outside", settings.hideOnOutsideTouch) { save { hideOnOutsideTouch = it } }
                    ActionRow("Double tap", settings.doubleTapAction) { save { doubleTapAction = it } }
                }

                SettingsSection("Edge actions") {
                    SwitchRow("Enabled", settings.edgeActionsEnabled) { save { edgeActionsEnabled = it } }
                    SliderRow("Push past edge by", settings.edgeThresholdDp, 10f..80f, 5f, { "${it.roundToInt()} dp" }) {
                        save { edgeThresholdDp = it }
                    }
                    SliderRow("Hold to run", settings.edgeDelayMs.toFloat(), 0f..1500f, 100f, { "${it.roundToInt()} ms" }) {
                        save { edgeDelayMs = it.roundToInt() }
                    }
                    ActionRow("Left edge", settings.edgeLeft) { save { edgeLeft = it } }
                    ActionRow("Right edge", settings.edgeRight) { save { edgeRight = it } }
                    ActionRow("Top edge", settings.edgeTop) { save { edgeTop = it } }
                    ActionRow("Bottom edge", settings.edgeBottom) { save { edgeBottom = it } }
                }

                SettingsSection("Feedback") {
                    SwitchRow("Vibrate on cursor actions", settings.vibrateOnCursorAction) { save { vibrateOnCursorAction = it } }
                    SwitchRow("Ripple on tap", settings.rippleOnClick) { save { rippleOnClick = it } }
                }

                OutlinedButton(onClick = { save { reset() } }, Modifier.fillMaxWidth()) {
                    Text("Reset all settings to defaults")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(serviceEnabled: Boolean, onOpenAccessibility: () -> Unit) {
    val colors = if (serviceEnabled) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    Card(Modifier.fillMaxWidth(), colors = colors) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (serviceEnabled) "Cursor is on" else "Cursor is off",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (serviceEnabled) {
                    "Swipe in from a screen edge to bring up the cursor. Move the round pad to aim, tap it to tap."
                } else {
                    "Turn on OpenQuickCursor in Accessibility settings to start."
                },
            )
            Button(onClick = onOpenAccessibility) { Text("Open accessibility settings") }
        }
    }
}

private fun speedLabel(level: Float): String {
    val factor = Defaults.SPEED_FACTORS[level.roundToInt().coerceIn(0, Defaults.SPEED_FACTORS.lastIndex)]
    return "${level.roundToInt()} (${factor}×)"
}
