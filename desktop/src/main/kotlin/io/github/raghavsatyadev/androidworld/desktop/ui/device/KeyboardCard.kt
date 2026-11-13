package io.github.raghavsatyadev.androidworld.desktop.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.remote.RemoteControlIntent
import io.github.raghavsatyadev.androidworld.core.domain.input.SystemKey
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardCard(graph: AppGraph) {
    val vm = graph.remote.remote
    val state by vm.state.collectAsState()
    var text by remember { mutableStateOf("") }

    fun sendText() {
        if (state.enabled && text.isNotEmpty() && !state.busy) {
            vm.handle(RemoteControlIntent.Type(text))
            text = ""
        }
    }

    SectionCard(title = "Keyboard") {
        Text(
            text = "Types into the field that has focus on the device. Printable ASCII only; new lines press Enter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            minLines = 2,
            maxLines = 5,
            label = { Text("Text to send") },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                            sendText()
                            true
                        } else {
                            false
                        }
                    },
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = ::sendText,
                enabled = state.enabled && text.isNotEmpty() && !state.busy,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Send")
            }

            OutlinedButton(
                onClick = { vm.handle(RemoteControlIntent.Press(SystemKey.Enter)) },
                enabled = state.enabled && !state.busy,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Enter")
            }

            OutlinedButton(
                onClick = { vm.handle(RemoteControlIntent.Press(SystemKey.Delete)) },
                enabled = state.enabled && !state.busy,
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Backspace")
            }

            OutlinedButton(
                onClick = { vm.handle(RemoteControlIntent.Press(SystemKey.Tab)) },
                enabled = state.enabled && !state.busy,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardTab, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tab")
            }

            OutlinedButton(
                onClick = { vm.handle(RemoteControlIntent.Press(SystemKey.Space)) },
                enabled = state.enabled && !state.busy,
            ) {
                Icon(Icons.Filled.SpaceBar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Space")
            }

            if (state.busy) {
                LoadingIndicator(modifier = Modifier.size(32.dp))
            }
        }

        state.error?.let { errorText ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.handle(RemoteControlIntent.DismissError) }) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
