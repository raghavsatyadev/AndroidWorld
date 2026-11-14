package io.github.raghavsatyadev.androidworld.desktop.ui.mirror

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.mirror.ShareOptions

/**
 * Second share toolbar row: screenshot of the current frame, rotate the device, and the stream
 * settings (resolution, frame rate, bitrate). Picking a new setting restarts the stream.
 */
@Composable
fun ShareControls(
    running: Boolean,
    canSnapshot: Boolean,
    options: ShareOptions,
    onOptions: (ShareOptions) -> Unit,
    onSnapshot: () -> Unit,
    onRotate: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = onSnapshot, enabled = running && canSnapshot) {
            Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Text("  Screenshot")
        }
        FilledTonalButton(onClick = onRotate, enabled = running) {
            Icon(Icons.Filled.ScreenRotation, null, Modifier.size(18.dp)); Text("  Rotate")
        }
        Text("Stream", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OptionPicker("Resolution", options.maxSize, ShareOptions.MAX_SIZES, ShareOptions::sizeLabel) {
            onOptions(options.copy(maxSize = it))
        }
        OptionPicker("Frame rate", options.maxFps, ShareOptions.FPS, ShareOptions::fpsLabel) {
            onOptions(options.copy(maxFps = it))
        }
        OptionPicker("Bitrate", options.bitRate, ShareOptions.BIT_RATES, ShareOptions::bitRateLabel) {
            onOptions(options.copy(bitRate = it))
        }
    }
}

@Composable
private fun OptionPicker(name: String, value: Int, values: List<Int>, label: (Int) -> String, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text("$name: ${label(value)}")
            Icon(Icons.Filled.ArrowDropDown, "Choose $name")
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            values.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        open = false
                        if (option != value) onPick(option)
                    },
                )
            }
        }
    }
}
