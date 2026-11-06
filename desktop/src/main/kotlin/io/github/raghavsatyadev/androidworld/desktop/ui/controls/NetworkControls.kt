package io.github.raghavsatyadev.androidworld.desktop.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.AssistChip
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.DeviceControlsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.core.application.network.NetworkThrottleIntent
import io.github.raghavsatyadev.androidworld.core.application.network.NetworkThrottleViewState
import io.github.raghavsatyadev.androidworld.core.application.network.ProxyIntent
import io.github.raghavsatyadev.androidworld.core.application.network.ProxyViewState
import io.github.raghavsatyadev.androidworld.core.application.network.ThrottleAvailability
import io.github.raghavsatyadev.androidworld.core.domain.network.NetworkThrottle
import io.github.raghavsatyadev.androidworld.core.domain.network.ProxyReadState

/** The device's global HTTP proxy, and emulator network throttling. */
@Composable
fun NetworkScreen(controls: DeviceControlsModule) {
    val eligible by controls.eligible.collectAsState()
    if (!eligible) return NoDevice()
    val proxy by controls.proxy.state.collectAsState()
    val throttle by controls.throttle.state.collectAsState()
    ControlsPage {
        ProxyCard(proxy, controls.proxy::handle)
        ThrottleCard(throttle, controls.throttle::handle)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProxyCard(state: ProxyViewState, onIntent: (ProxyIntent) -> Unit) {
    val busy = state.isBusy || !state.isDeviceEligible
    SectionCard("HTTP proxy", trailing = { RefreshButton { onIntent(ProxyIntent.Refresh) } }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when (val read = state.readState) {
                    null -> "Reading…"
                    ProxyReadState.Disabled -> "No proxy: the device connects directly."
                    is ProxyReadState.Active -> "Traffic goes through ${read.endpoint.render()}"
                    is ProxyReadState.Unrecognized -> "Set to something this app doesn't recognise: ${read.raw}"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.isBusy) LoadingIndicator(Modifier.size(24.dp))
            OutlinedButton(
                onClick = { onIntent(ProxyIntent.Reset) },
                enabled = !busy && state.readState != null && state.readState != ProxyReadState.Disabled,
            ) { Text("Turn off") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                state.hostInput, { onIntent(ProxyIntent.EditHost(it)) }, label = { Text("Host") }, singleLine = true,
                isError = state.hostError != null, supportingText = state.hostError?.let { { Text(it) } },
                modifier = Modifier.width(280.dp),
            )
            OutlinedTextField(
                state.portInput, { onIntent(ProxyIntent.EditPort(it)) }, label = { Text("Port") }, singleLine = true,
                isError = state.portError != null, supportingText = state.portError?.let { { Text(it) } },
                modifier = Modifier.width(120.dp),
            )
            Button(
                onClick = { onIntent(ProxyIntent.Enable(state.hostInput, state.portInput)) },
                enabled = !busy && state.canSubmit,
            ) { Text("Use proxy") }
        }
        TextButton(onClick = { onIntent(ProxyIntent.UseComputerIp) }, enabled = !state.isResolvingIp) {
            Icon(Icons.Filled.Computer, null, Modifier.padding(end = 8.dp))
            Text(if (state.isResolvingIp) "Finding this computer's IP…" else "Use this computer's IP")
        }
        if (state.ipChoices.isNotEmpty()) {
            Caption("This computer has several networks. Pick the one the device can reach:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.ipChoices.forEach { candidate ->
                    AssistChip(
                        onClick = { onIntent(ProxyIntent.EditHost(candidate.ipv4Address)) },
                        label = { Text("${candidate.displayName} · ${candidate.ipv4Address}") },
                        leadingIcon = { Icon(Icons.Filled.Lan, null) },
                    )
                }
            }
        }
        if (state.recents.isNotEmpty()) {
            Caption("Recent")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.recents.forEach { endpoint ->
                    AssistChip(
                        onClick = { onIntent(ProxyIntent.SelectRecent(endpoint)) },
                        label = { Text(endpoint.render()) },
                        leadingIcon = { Icon(Icons.Filled.History, null) },
                    )
                }
            }
        }
        ErrorText(state.error)
    }
}

@Composable
private fun ThrottleCard(state: NetworkThrottleViewState, onIntent: (NetworkThrottleIntent) -> Unit) {
    SectionCard("Network speed", trailing = { RefreshButton { onIntent(NetworkThrottleIntent.Refresh) } }) {
        when (state.availability) {
            ThrottleAvailability.NoDevice -> Caption("Pick a device.")
            ThrottleAvailability.PhysicalDevice ->
                Caption("Android can't slow a phone's network without root; this works on emulators.")
            ThrottleAvailability.Emulator -> {
                Text(
                    state.current?.let { "Now ${it.label}" } ?: state.customDescription?.let { "Custom: $it" } ?: "Reading…",
                    style = MaterialTheme.typography.titleMedium,
                )
                ChoiceGroup(
                    options = NetworkThrottle.entries,
                    selected = state.current,
                    label = { it.label },
                    onSelect = { onIntent(NetworkThrottleIntent.Apply(it)) },
                    enabled = !state.busy,
                )
            }
        }
        ErrorText(state.error)
    }
}
