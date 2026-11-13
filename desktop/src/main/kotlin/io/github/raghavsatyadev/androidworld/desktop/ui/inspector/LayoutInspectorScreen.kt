package io.github.raghavsatyadev.androidworld.desktop.ui.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.InspectorModule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun LayoutInspectorScreen(module: InspectorModule) {
    val vm = module.viewModel
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.onShown() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Layout inspector", style = MaterialTheme.typography.titleLarge)
                val capture = state.capture
                if (capture != null) {
                    val timeStr = remember(capture.capturedAtMillis) {
                        Instant.ofEpochMilli(capture.capturedAtMillis).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)
                    }
                    val subtitle = listOfNotNull(
                        capture.deviceName.takeIf { it.isNotBlank() },
                        capture.activity?.takeIf { it.isNotBlank() },
                        timeStr,
                    ).joinToString(" • ")
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Highlight issues", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.highlightIssues,
                    onCheckedChange = { vm.setHighlightIssues(it) },
                )
            }

            if (state.capturing) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            }

            FilledTonalButton(
                onClick = { vm.recapture() },
                enabled = state.deviceOnline && !state.capturing,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh")
            }
        }

        if (state.error != null) {
            Text(
                text = state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.deviceGone) {
            Text(
                text = "Device disconnected: showing the last capture.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        val capture = state.capture
        if (capture != null) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                InspectorScreenshot(
                    capture = capture,
                    state = state,
                    onTap = { x, y -> vm.selectAt(x, y) },
                    modifier = Modifier.weight(0.38f).fillMaxHeight(),
                )
                VerticalDivider()
                NodeTreePane(
                    state = state,
                    onSelect = { vm.select(it) },
                    onToggle = { vm.toggle(it) },
                    onExpandAll = { vm.expandAll() },
                    onCollapseAll = { vm.collapseAll() },
                    modifier = Modifier.weight(0.32f).fillMaxHeight(),
                )
                VerticalDivider()
                InspectorSidePanel(
                    state = state,
                    onSelect = { vm.select(it) },
                    modifier = Modifier.weight(0.30f).fillMaxHeight(),
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.capturing) "Capturing…" else "Select an online device and press Refresh.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
