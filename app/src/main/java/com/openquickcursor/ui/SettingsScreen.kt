package com.openquickcursor.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openquickcursor.Defaults
import com.openquickcursor.settings.QuickCursorImport
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
    var editPortrait by remember { mutableStateOf(true) }
    fun save(block: Settings.() -> Unit) {
        settings.block()
        revision++
    }

    val context = LocalContext.current
    var importMessage by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importMessage = try {
            val json = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            val result = QuickCursorImport.apply(json, settings, context.resources.displayMetrics.density)
            revision++
            buildString {
                append("Imported ${result.applied.size} settings.")
                if (result.skipped.isNotEmpty()) {
                    append("\n\nNot carried over:\n")
                    result.skipped.forEach { append("• ").append(it).append('\n') }
                }
            }
        } catch (e: Exception) {
            "Couldn't read that file as a Quick Cursor backup.\n\n${e.message}"
        }
    }
    importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { importMessage = null },
            confirmButton = { TextButton(onClick = { importMessage = null }) { Text("OK") } },
            title = { Text("Import backup") },
            text = { Text(message) },
        )
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
                    SliderRow("Thickness", settings.triggerWidthDp, 8f..48f, 2f, { "${it.roundToInt()} dp" }) {
                        save { triggerWidthDp = it }
                    }
                }

                SettingsSection("Layout") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(editPortrait, { editPortrait = true }, label = { Text("Portrait") })
                        FilterChip(!editPortrait, { editPortrait = false }, label = { Text("Landscape") })
                    }
                    val levels = if (editPortrait) settings.portrait else settings.landscape
                    SliderRow("Trigger position", levels.position.toFloat(), 0f..10f, 1f, { positionLabel(it, editPortrait) }) {
                        save { levels.position = it.roundToInt() }
                    }
                    SliderRow("Trigger length", levels.length.toFloat(), 0f..10f, 1f, { "${it.roundToInt()}" }) {
                        save { levels.length = it.roundToInt() }
                    }
                    SliderRow("Cursor speed", levels.speed.toFloat(), 0f..11f, 1f, ::speedLabel) {
                        save { levels.speed = it.roundToInt() }
                    }
                    SliderRow("Cursor area", levels.cursorArea.toFloat(), 0f..7f, 1f, { "top ${it.roundToInt() + 1}/8" }) {
                        save { levels.cursorArea = it.roundToInt() }
                    }
                    SliderRow("Thumb area align", levels.trackerAlign.toFloat(), 0f..6f, 1f, { "${it.roundToInt()}" }) {
                        save { levels.trackerAlign = it.roundToInt() }
                    }
                    SliderRow("Thumb area distance", levels.trackerDistance.toFloat(), 0f..10f, 1f, { "${it.roundToInt()}" }) {
                        save { levels.trackerDistance = it.roundToInt() }
                    }
                }

                SettingsSection("Cursor") {
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

                SettingsSection("Backup") {
                    Text(
                        "Bring over your setup from the original Quick Cursor app: pick a backup " +
                            "file it exported (.json).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        Modifier.fillMaxWidth(),
                    ) { Text("Import Quick Cursor backup") }
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

/** Portrait puts triggers on the side edges at positions 5–10; landscape at 0–4. */
private fun positionLabel(level: Float, portrait: Boolean): String {
    val n = level.roundToInt()
    val side = if (portrait) n >= 5 else n < 5
    return "$n (${if (side) "side edges" else "bottom edge"})"
}
