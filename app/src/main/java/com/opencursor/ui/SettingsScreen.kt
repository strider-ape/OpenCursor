package com.opencursor.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.opencursor.Defaults
import com.opencursor.R
import com.opencursor.settings.QuickCursorImport
import com.opencursor.settings.Settings
import kotlin.math.roundToInt

/** Settings pages, mirroring the original app's split into separate screens. */
private enum class Page(val title: String, val summary: String) {
    TRIGGERS("Triggers", "Where you swipe in from the edge to call the cursor"),
    MOVEMENT("Cursor movement", "Cursor speed and how much of the screen it reaches"),
    CURSOR("Cursor look", "Size of the cursor"),
    TRACKER("Tracker pad", "The round pad under your thumb: size, timing, hiding"),
    GESTURES("Tap behaviour", "What double tap and long press on the pad do"),
    PIE("Action pie", "Ring of actions that opens when you hold the pad"),
    EDGES("Edge actions", "Push the cursor past a screen edge to run an action"),
    FEEDBACK("Vibration & visuals", "Vibration and tap ripple"),
    BACKUP("Backup & reset", "Import a Quick Cursor backup or reset everything"),
}

private typealias Save = (Settings.() -> Unit) -> Unit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, serviceEnabled: Boolean, onOpenAccessibility: () -> Unit) {
    // Settings live in SharedPreferences, not Compose state; bumping this re-reads them.
    var revision by remember { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    var editPortrait by rememberSaveable { mutableStateOf(true) }
    val save: Save = { block ->
        settings.block()
        revision++
    }

    BackHandler(enabled = page != null) { page = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page?.title ?: "OpenCursor") },
                navigationIcon = {
                    if (page != null) {
                        IconButton(onClick = { page = null }) { Text("←", style = MaterialTheme.typography.titleLarge) }
                    }
                },
            )
        },
    ) { padding ->
        key(revision, page) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (page) {
                    null -> Home(serviceEnabled, onOpenAccessibility) { page = it }
                    Page.TRIGGERS -> TriggersPage(settings, editPortrait, { editPortrait = it }, save)
                    Page.MOVEMENT -> MovementPage(settings, editPortrait, { editPortrait = it }, save)
                    Page.CURSOR -> CursorPage(settings, save)
                    Page.TRACKER -> TrackerPage(settings, save)
                    Page.GESTURES -> GesturesPage(settings, save)
                    Page.PIE -> PiePage(settings, save)
                    Page.EDGES -> EdgesPage(settings, save)
                    Page.FEEDBACK -> FeedbackPage(settings, save)
                    Page.BACKUP -> BackupPage(save)
                }
            }
        }
    }
}

@Composable
private fun Home(serviceEnabled: Boolean, onOpenAccessibility: () -> Unit, open: (Page) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.logo), contentDescription = "OpenCursor logo", modifier = Modifier.size(88.dp))
    }
    StatusCard(serviceEnabled, onOpenAccessibility)
    Card(Modifier.fillMaxWidth()) {
        Page.entries.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider()
            ListItem(
                headlineContent = { Text(p.title) },
                supportingContent = { Text(p.summary) },
                trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
                modifier = Modifier.clickable { open(p) },
            )
        }
    }
}

@Composable
private fun OrientationPicker(portrait: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(portrait, { onChange(true) }, label = { Text("Portrait") })
            FilterChip(!portrait, { onChange(false) }, label = { Text("Landscape") })
        }
        Description("Portrait and landscape have separate settings. Pick which one to edit.")
    }
}

@Composable
private fun ColumnScope.TriggersPage(s: Settings, portrait: Boolean, setPortrait: (Boolean) -> Unit, save: Save) {
    SettingsSection {
        SwitchRow("Left edge", s.leftTrigger, "Swipe in from the left edge to call the cursor.") { save { leftTrigger = it } }
        SwitchRow("Right edge", s.rightTrigger, "Swipe in from the right edge to call the cursor.") { save { rightTrigger = it } }
        SliderRow(
            "Thickness", s.triggerWidthDp, 8f..48f, 2f, { "${it.roundToInt()} dp" },
            "How wide the invisible swipe strip is. Wider is easier to hit but can get in the way of apps' own edge swipes.",
        ) { save { triggerWidthDp = it } }
    }
    SettingsSection {
        OrientationPicker(portrait, setPortrait)
        val levels = if (portrait) s.portrait else s.landscape
        SliderRow(
            "Position", levels.position.toFloat(), 0f..10f, 1f, { positionLabel(it, portrait) },
            if (portrait) {
                "Where the strips sit. 5–10 puts them on the side edges (10 = middle of the screen, 5 = bottom corner). 0–4 moves them to the bottom edge."
            } else {
                "Where the strips sit. 5–10 puts them on the bottom edge, moving out toward the corners. 0–4 moves them to the side edges."
            },
        ) { save { levels.position = it.roundToInt() } }
        SliderRow(
            "Length", levels.length.toFloat(), 0f..10f, 1f, { lengthLabel(it) },
            "How long the strips are. Longer strips are easier to find with your thumb.",
        ) { save { levels.length = it.roundToInt() } }
    }
}

@Composable
private fun ColumnScope.MovementPage(s: Settings, portrait: Boolean, setPortrait: (Boolean) -> Unit, save: Save) {
    SettingsSection {
        OrientationPicker(portrait, setPortrait)
        val levels = if (portrait) s.portrait else s.landscape
        SliderRow(
            "Cursor speed", levels.speed.toFloat(), 0f..11f, 1f, ::speedLabel,
            "How far the cursor travels for each bit of thumb movement. At 2.66×, moving your thumb 1 cm moves the cursor 2.66 cm.",
        ) { save { levels.speed = it.roundToInt() } }
        SliderRow(
            "Cursor reach", levels.cursorArea.toFloat(), 0f..7f, 1f, { "top ${it.roundToInt() + 1}/8 of screen" },
            "How much of the screen the cursor covers, counted from the top. Your thumb reaches the bottom part directly, so half is usually enough.",
        ) { save { levels.cursorArea = it.roundToInt() } }
        SliderRow(
            "Thumb area position", levels.trackerAlign.toFloat(), 0f..6f, 1f, { "${it.roundToInt()} of 6" },
            "Slides the area your thumb works in along the trigger strip: 0 = toward its start, 6 = toward its end.",
        ) { save { levels.trackerAlign = it.roundToInt() } }
        SliderRow(
            "Thumb area distance", levels.trackerDistance.toFloat(), 0f..10f, 1f, { "${it.roundToInt()}" },
            "How far in from the screen edge your thumb works. Higher values keep your thumb away from the edge.",
        ) { save { levels.trackerDistance = it.roundToInt() } }
    }
}

@Composable
private fun ColumnScope.CursorPage(s: Settings, save: Save) {
    SettingsSection {
        SliderRow("Cursor size", s.cursorSizeDp, 20f..80f, 2f, { "${it.roundToInt()} dp" }, "Diameter of the round cursor.") {
            save { cursorSizeDp = it }
        }
    }
}

@Composable
private fun ColumnScope.TrackerPage(s: Settings, save: Save) {
    SettingsSection {
        SliderRow("Pad size", s.trackerSizeDp, 60f..160f, 5f, { "${it.roundToInt()} dp" }, "Size of the round pad that appears under your thumb.") {
            save { trackerSizeDp = it }
        }
        SliderRow(
            "Long press time", s.longTapMs.toFloat(), 200f..1000f, 50f, { "${it.roundToInt()} ms" },
            "How long you hold the pad before it counts as a long press.",
        ) { save { longTapMs = it.roundToInt() } }
        SliderRow(
            "Hide after", s.hideTimeoutMs.toFloat(), 500f..5000f, 250f, { "%.1f s".format(it / 1000f) },
            "The pad and cursor disappear after this long without being touched.",
        ) { save { hideTimeoutMs = it.roundToInt() } }
        SwitchRow("Hide when flicked", s.hideOnFling, "Flick the pad quickly to dismiss it.") { save { hideOnFling = it } }
        SwitchRow("Hide on touch elsewhere", s.hideOnOutsideTouch, "Touching anywhere outside the pad dismisses it.") {
            save { hideOnOutsideTouch = it }
        }
    }
}

@Composable
private fun ColumnScope.GesturesPage(s: Settings, save: Save) {
    SettingsSection {
        Description("A single tap on the pad always taps where the cursor points.")
        ActionRow("Double tap", s.doubleTapAction, "Two quick taps on the pad. While this is set, single taps wait a moment to tell them apart.") {
            save { doubleTapAction = it }
        }
        SwitchRow(
            "Long press opens action pie", s.longTapOpensPie,
            "On: holding the pad opens the action pie. Off: holding the pad does a long press where the cursor points.",
        ) { save { longTapOpensPie = it } }
    }
}

@Composable
private fun ColumnScope.PiePage(s: Settings, save: Save) {
    SettingsSection {
        SwitchRow(
            "Open with long press", s.longTapOpensPie,
            "Hold the pad, slide toward a slice, and let go to run it where the cursor points. Let go in the middle to cancel.",
        ) { save { longTapOpensPie = it } }
    }
    SettingsSection("Slices (clockwise from the top)") {
        val slices = s.pieActions
        slices.forEachIndexed { i, action ->
            ActionRow("Slice ${i + 1}", action) { picked -> save { pieActions = slices.toMutableList().also { it[i] = picked } } }
        }
    }
}

@Composable
private fun ColumnScope.EdgesPage(s: Settings, save: Save) {
    SettingsSection {
        SwitchRow(
            "Edge actions", s.edgeActionsEnabled,
            "While moving the cursor, keep pushing past a screen edge: the edge lights up, and holding there or letting go runs its action.",
        ) { save { edgeActionsEnabled = it } }
        SliderRow(
            "Push distance", s.edgeThresholdDp, 10f..80f, 5f, { "${it.roundToInt()} dp" },
            "How far past the edge the cursor must be pushed before the action lights up.",
        ) { save { edgeThresholdDp = it } }
        SliderRow(
            "Hold time", s.edgeDelayMs.toFloat(), 0f..1500f, 100f, { "${it.roundToInt()} ms" },
            "How long to hold at a lit edge before its action runs on its own.",
        ) { save { edgeDelayMs = it.roundToInt() } }
    }
    SettingsSection("Actions") {
        Description("Side edges are off by default: your thumb starts near them, so they'd fire right after swiping in.")
        ActionRow("Top edge", s.edgeTop) { save { edgeTop = it } }
        ActionRow("Left edge", s.edgeLeft) { save { edgeLeft = it } }
        ActionRow("Right edge", s.edgeRight) { save { edgeRight = it } }
        ActionRow("Bottom edge", s.edgeBottom) { save { edgeBottom = it } }
    }
}

@Composable
private fun ColumnScope.FeedbackPage(s: Settings, save: Save) {
    SettingsSection {
        SwitchRow("Vibrate on cursor actions", s.vibrateOnCursorAction, "A short buzz when an edge action lights up or a pie slice is picked.") {
            save { vibrateOnCursorAction = it }
        }
        SwitchRow("Ripple on tap", s.rippleOnClick, "An orange ripple where the cursor taps.") { save { rippleOnClick = it } }
    }
}

@Composable
private fun ColumnScope.BackupPage(save: Save) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        message = try {
            val json = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            var result: QuickCursorImport.Result? = null
            save { result = QuickCursorImport.apply(json, this, context.resources.displayMetrics.density) }
            val r = result!!
            buildString {
                append("Imported ${r.applied.size} settings.")
                if (r.skipped.isNotEmpty()) {
                    append("\n\nNot carried over:\n")
                    r.skipped.forEach { append("• ").append(it).append('\n') }
                }
            }
        } catch (e: Exception) {
            "Couldn't read that file as a Quick Cursor backup.\n\n${e.message}"
        }
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            title = { Text("Import backup") },
            text = { Text(it) },
        )
    }
    SettingsSection {
        Description("Bring over your setup from the original Quick Cursor app: pick a backup file it exported (.json).")
        Button(
            onClick = { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            Modifier.fillMaxWidth(),
        ) { Text("Import Quick Cursor backup") }
    }
    SettingsSection {
        Description("Puts every setting back to its default.")
        OutlinedButton(onClick = { save { reset() } }, Modifier.fillMaxWidth()) { Text("Reset all settings") }
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
            Text(if (serviceEnabled) "Cursor is on" else "Cursor is off", style = MaterialTheme.typography.titleMedium)
            Text(
                if (serviceEnabled) {
                    "Swipe in from a screen edge to bring up the cursor. Move the round pad to aim, tap it to tap."
                } else {
                    "Turn on OpenCursor in Accessibility settings to start."
                },
            )
            Button(onClick = onOpenAccessibility) { Text("Open accessibility settings") }
        }
    }
}

private fun speedLabel(level: Float): String {
    val factor = Defaults.SPEED_FACTORS[level.roundToInt().coerceIn(0, Defaults.SPEED_FACTORS.lastIndex)]
    return "$factor×"
}

private fun lengthLabel(level: Float): String {
    val factors = listOf(0.25, 0.4, 0.55, 0.7, 0.85, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0)
    return "${(factors[level.roundToInt().coerceIn(0, 10)] * 100).roundToInt()} %"
}

/** Portrait puts triggers on the side edges at positions 5–10; landscape at 0–4. */
private fun positionLabel(level: Float, portrait: Boolean): String {
    val n = level.roundToInt()
    val side = if (portrait) n >= 5 else n < 5
    return "$n · ${if (side) "side edges" else "bottom edge"}"
}
