package io.github.raghavsatyadev.androidworld.desktop.ui.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.components.FactTile
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.core.application.capture.CaptureIntent
import io.github.raghavsatyadev.androidworld.core.application.devicefacts.DeviceFactsIntent
import io.github.raghavsatyadev.androidworld.core.application.devicefacts.DeviceFactsViewState
import io.github.raghavsatyadev.androidworld.core.domain.devicecontext.ControlPolicy
import io.github.raghavsatyadev.androidworld.core.domain.devicefacts.DeviceFactId
import io.github.raghavsatyadev.androidworld.core.domain.devicefacts.DeviceFactState
import io.github.raghavsatyadev.androidworld.core.domain.devicefacts.DeviceFactValue
import io.github.raghavsatyadev.androidworld.core.domain.devicefacts.DeviceFactsSnapshot

@Composable
fun DeviceScreen(graph: AppGraph) {
    val facts by graph.deviceFacts.state.collectAsState()
    val bar by graph.deviceBar.state.collectAsState()
    if (facts is DeviceFactsViewState.NoDevice) {
        val text = if (bar.picker.items.isEmpty()) "Connect a device with USB debugging on, or start an emulator."
        else "Pick a device from the menu at the top right."
        EmptyState(text, icon = Icons.Filled.PhoneAndroid)
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FactsCard(graph, facts)
        CurrentAppCard(graph)
        CaptureCard(graph)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactsCard(graph: AppGraph, state: DeviceFactsViewState) {
    SectionCard(
        title = "Device",
        trailing = {
            IconButton(onClick = { graph.deviceFacts.handle(DeviceFactsIntent.CopyReport) }) { Icon(Icons.Filled.ContentCopy, "Copy report") }
            IconButton(onClick = { graph.deviceFacts.handle(DeviceFactsIntent.Refresh) }) { Icon(Icons.Filled.Refresh, "Refresh facts") }
        },
    ) {
        when (state) {
            DeviceFactsViewState.Loading, DeviceFactsViewState.NoDevice -> LoadingIndicator(Modifier.size(48.dp))
            is DeviceFactsViewState.RecoverableError -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is DeviceFactsViewState.Connected -> FactGrid(state.snapshot)
            is DeviceFactsViewState.Partial -> FactGrid(state.snapshot)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactGrid(snapshot: DeviceFactsSnapshot) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DeviceFactId.entries.forEach { id ->
            FactTile(label = factLabel(id), value = factText(snapshot.facts[id]), modifier = Modifier.widthIn(min = 160.dp))
        }
    }
}

private fun factLabel(id: DeviceFactId) = when (id) {
    DeviceFactId.AndroidVersion -> "Android"
    DeviceFactId.Resolution -> "Resolution"
    DeviceFactId.Density -> "Density"
    DeviceFactId.Battery -> "Battery"
    DeviceFactId.Abi -> "ABI"
    DeviceFactId.Uptime -> "Uptime"
}

private fun factText(state: DeviceFactState?): String = when (state) {
    null, DeviceFactState.Loading -> "…"
    is DeviceFactState.Unavailable -> "—"
    is DeviceFactState.Available -> when (val v = state.value) {
        is DeviceFactValue.AndroidVersion -> "${v.release} (API ${v.sdk})"
        is DeviceFactValue.Resolution -> "${v.widthPx} × ${v.heightPx}"
        is DeviceFactValue.Density -> "${v.dpi} dpi"
        is DeviceFactValue.Battery -> "${v.levelPercent}%" + if (v.charging) " · charging" else ""
        is DeviceFactValue.Abi -> v.value
        is DeviceFactValue.Uptime -> v.duration.toComponents { d, h, m, _, _ -> if (d > 0) "${d}d ${h}h" else "${h}h ${m}m" }
    }
}

@Composable
private fun CaptureCard(graph: AppGraph) {
    val state by graph.capture.state.collectAsState()
    val enabled = state.controlPolicy is ControlPolicy.Enabled && !state.isCapturing
    SectionCard(title = "Screenshot") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { graph.capture.handle(CaptureIntent.CaptureScreenshot) }, enabled = enabled) {
                Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Capture")
            }
            FilledTonalButton(onClick = { graph.capture.handle(CaptureIntent.CaptureFullScreenshot) }, enabled = enabled) {
                Icon(Icons.Filled.Screenshot, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Full page")
            }
            if (state.isCapturing) LoadingIndicator(Modifier.size(36.dp))
        }
        state.lastCapture?.let { last ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(last.displayPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { graph.capture.handle(CaptureIntent.RevealLastCapture) }) {
                    Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Show")
                }
            }
        }
    }
}
