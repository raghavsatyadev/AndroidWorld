package io.github.raghavsatyadev.androidworld.desktop.ui.mirror

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.recording.RecordingIntent
import io.github.raghavsatyadev.androidworld.core.application.recording.RecordingPresentationState
import io.github.raghavsatyadev.androidworld.core.application.recording.RecordingViewModel
import io.github.raghavsatyadev.androidworld.core.domain.devicecontext.ControlPolicy

/** Record / Stop & save toggle, elapsed time and a Reveal button for the last saved recording. */
@Composable
fun RecordingControls(recording: RecordingViewModel) {
    val state by recording.state.collectAsState()
    val enabled = state.controlPolicy is ControlPolicy.Enabled
    when (val presentation = state.presentationState) {
        RecordingPresentationState.Recording -> {
            OutlinedButton(onClick = { recording.handle(RecordingIntent.Toggle) }) {
                Icon(Icons.Filled.Stop, null, tint = MaterialTheme.colorScheme.error)
                Text("  Stop & save")
            }
            Text(
                "Recording · ${state.elapsedLabel ?: "00:00"}",
                color = MaterialTheme.colorScheme.error,
                fontFamily = FontFamily.Monospace,
            )
        }
        RecordingPresentationState.Starting,
        RecordingPresentationState.Stopping,
        RecordingPresentationState.Pulling -> {
            LoadingIndicator(Modifier.size(32.dp))
            Text(
                when (presentation) {
                    RecordingPresentationState.Starting -> "Starting recording…"
                    RecordingPresentationState.Pulling -> "Copying the recording…"
                    else -> "Saving…"
                },
            )
        }
        else -> {
            FilledTonalButton(onClick = { recording.handle(RecordingIntent.Toggle) }, enabled = enabled) {
                Icon(Icons.Filled.FiberManualRecord, null, tint = MaterialTheme.colorScheme.error)
                Text("  Record")
            }
            if (state.lastRecording != null) {
                TextButton(onClick = { recording.handle(RecordingIntent.RevealLastRecording) }) {
                    Icon(Icons.Filled.FolderOpen, null)
                    Text("  Show recording")
                }
            }
            if (presentation is RecordingPresentationState.Error) {
                Text(presentation.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
