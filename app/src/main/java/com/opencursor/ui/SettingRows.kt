package com.opencursor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opencursor.actions.Action

/** A card that groups related settings, with an optional heading. */
@Composable
fun SettingsSection(title: String? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

/** Grey helper text under a setting that says what it does. */
@Composable
fun Description(text: String?) {
    if (text == null) return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SwitchRow(label: String, checked: Boolean, description: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label)
            Description(description)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * A slider that saves only when the finger lifts, so the running cursor engine
 * isn't rebuilt on every frame of a drag.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    format: (Float) -> String,
    description: String? = null,
    onChange: (Float) -> Unit,
) {
    var current by remember(value) { mutableFloatStateOf(value) }
    val steps = ((range.endInclusive - range.start) / step).toInt() - 1
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f))
            Text(format(current), color = MaterialTheme.colorScheme.primary)
        }
        Description(description)
        Slider(
            value = current,
            onValueChange = { current = it },
            onValueChangeFinished = { onChange(current) },
            valueRange = range,
            steps = steps.coerceAtLeast(0),
        )
    }
}

@Composable
fun ActionRow(label: String, value: Action, description: String? = null, onChange: (Action) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label)
            Description(description)
        }
        Box {
            OutlinedButton(onClick = { open = true }) { Text(value.label()) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Action.entries.filter { it.isSupported }.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label()) },
                        onClick = {
                            open = false
                            onChange(action)
                        },
                    )
                }
            }
        }
    }
}

/** "PREVIOUS_APP" -> "Previous app". */
fun Action.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
