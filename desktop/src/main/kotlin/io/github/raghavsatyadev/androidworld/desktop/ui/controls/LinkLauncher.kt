package io.github.raghavsatyadev.androidworld.desktop.ui.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.DeviceControlsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.DeepLinkLauncherIntent
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.DeepLinkLauncherState
import io.github.raghavsatyadev.androidworld.core.application.deeplinks.LaunchBanner

/** Opens any URI on the device the way a tapped link would, and keeps a history of what was opened. */
@Composable
fun LinkLauncherScreen(controls: DeviceControlsModule) {
    val eligible by controls.eligible.collectAsState()
    if (!eligible) return NoDevice()
    val state by controls.links.state.collectAsState()
    val onIntent = controls.links::handle
    ControlsPage {
        SectionCard("Open a link") {
            LinkForm(state, onIntent)
            HandlersLine(state)
            ResultBanner(state, onIntent, controls.copy)
            CommandPreview(state, onIntent, controls.copy)
        }
        LinkHistory(state, onIntent)
    }
}

@Composable
private fun LinkForm(state: DeepLinkLauncherState, onIntent: (DeepLinkLauncherIntent) -> Unit) {
    val open = { onIntent(DeepLinkLauncherIntent.Open) }
    OutlinedTextField(
        state.uri, { onIntent(DeepLinkLauncherIntent.EditUri(it)) },
        label = { Text("Link") },
        placeholder = { Text("myapp://screen/42 or https://example.com/path") },
        isError = state.uri.isNotBlank() && !state.uriValid,
        supportingText = if (state.uri.isNotBlank() && !state.uriValid) ({ Text("A link needs a scheme, like https:// or myapp://") }) else null,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { open() }),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            state.packageName, { onIntent(DeepLinkLauncherIntent.EditPackage(it)) },
            label = { Text("Only in app (optional)") },
            placeholder = { Text("com.example.app") },
            singleLine = true,
            modifier = Modifier.width(320.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(state.coldStart, { onIntent(DeepLinkLauncherIntent.SetColdStart(it)) })
            Text("Cold start (stop the app first)")
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            Button(onClick = open, enabled = state.canOpen) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.padding(end = 8.dp))
                Text(if (state.launch.busy) "Opening…" else "Open on device")
            }
        }
    }
}

@Composable
private fun ResultBanner(
    state: DeepLinkLauncherState,
    onIntent: (DeepLinkLauncherIntent) -> Unit,
    copy: (String, String) -> Unit,
) {
    val launch = state.launch
    val banner = remember(launch, state.serial, state.packages) {
        val result = launch.result
        val request = launch.request
        val serial = state.serial
        if (result == null || request == null || serial == null) null
        else LaunchBanner.of(result, serial, request.uri, request.packageName, launch.coldStart, state::label)
    }
    AnimatedVisibility(banner != null, enter = reveal(), exit = conceal()) {
        banner?.let { BannerCard(it, state::label, onIntent, copy) }
    }
}

@Composable
private fun CommandPreview(
    state: DeepLinkLauncherState,
    onIntent: (DeepLinkLauncherIntent) -> Unit,
    copy: (String, String) -> Unit,
) {
    if (state.preview.isBlank()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.preview,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            TextButton(onClick = { onIntent(DeepLinkLauncherIntent.SetCommandExpanded(!state.commandExpanded)) }) {
                Text(if (state.commandExpanded) "One line" else "Expand")
            }
            AnimatedVisibility(state.terminalCommand.isNotBlank(), enter = reveal(), exit = conceal()) {
                TextButton(onClick = { copy(state.terminalCommand, "Command copied") }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.padding(end = 8.dp))
                    Text("Copy")
                }
            }
        }
    }
}
