package io.github.raghavsatyadev.androidworld.desktop.ui.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.core.application.currentapp.CurrentAppAction
import io.github.raghavsatyadev.androidworld.core.application.currentapp.CurrentAppDisplay
import io.github.raghavsatyadev.androidworld.core.application.currentapp.CurrentAppViewState
import io.github.raghavsatyadev.androidworld.desktop.ui.components.AppIconImage

@Composable
fun CurrentAppCard(graph: AppGraph) {
    val vm = graph.currentApp
    val state by vm.state.collectAsState()
    DisposableEffect(vm) {
        vm.setVisible(true)
        vm.setWindowFocused(true)
        onDispose { vm.setVisible(false) }
    }
    var confirm by remember { mutableStateOf<Pair<CurrentAppAction, String>?>(null) }

    SectionCard(
        title = "Current app",
        trailing = {
            if (state.refreshing) LoadingIndicator(Modifier.size(36.dp))
            else IconButton(onClick = vm::refreshNow) { Icon(Icons.Filled.Refresh, "Refresh current app") }
        },
    ) {
        when (val display = state.display) {
            CurrentAppDisplay.Reading -> LoadingIndicator(Modifier.size(48.dp))
            CurrentAppDisplay.Locked -> Text("The device is locked.")
            is CurrentAppDisplay.Error -> Text(display.reason, color = MaterialTheme.colorScheme.error)
            is CurrentAppDisplay.Home -> Text(
                "Home screen" + (display.lastApp?.let { " · last app $it" } ?: ""),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is CurrentAppDisplay.App -> AppDetails(display, state, onAction = { action ->
                val pkg = display.app.packageName
                if (action == CurrentAppAction.ClearData || action == CurrentAppAction.Uninstall) confirm = action to pkg
                else vm.perform(action, pkg)
            })
        }
    }

    confirm?.let { (action, pkg) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == CurrentAppAction.Uninstall) "Uninstall $pkg?" else "Clear data of $pkg?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { vm.perform(action, pkg); confirm = null }) {
                    Text(if (action == CurrentAppAction.Uninstall) "Uninstall" else "Clear data", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppDetails(display: CurrentAppDisplay.App, state: CurrentAppViewState, onAction: (CurrentAppAction) -> Unit) {
    val info = display.snapshot.info
    val details = display.snapshot.details
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AppIconImage(info?.icon)
        Column(Modifier.weight(1f)) {
            Text(info?.label ?: display.app.packageName, style = MaterialTheme.typography.titleMedium)
            Text(display.app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(display.app.activity.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        details?.versionName?.let { Chip("v$it" + (details.versionCode?.let { c -> " ($c)" } ?: "")) }
        details?.targetSdk?.let { Chip("target $it") }
        details?.minSdk?.let { Chip("min $it") }
        if (details?.debuggable == true) Chip("debuggable")
        if (details?.system == true) Chip("system")
        display.snapshot.process?.let { p -> Chip(if (display.killed) "killed" else "pid ${p.pid}") }
    }
    val busy = state.busy != null
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Action("Restart", Icons.Filled.RestartAlt, !busy) { onAction(CurrentAppAction.Restart) }
        if (display.killed) Action("Launch", Icons.Filled.PlayArrow, !busy) { onAction(CurrentAppAction.Launch) }
        else Action("Force stop", Icons.Filled.Stop, !busy) { onAction(CurrentAppAction.Kill) }
        Action("Process death", Icons.Filled.Stop, !busy && !display.killed) { onAction(CurrentAppAction.ProcessDeath) }
        Action("Reset permissions", Icons.Filled.RestartAlt, !busy) { onAction(CurrentAppAction.ResetPermissions) }
        Danger("Clear data", Icons.Filled.DeleteSweep, !busy) { onAction(CurrentAppAction.ClearData) }
        Danger("Uninstall", Icons.Filled.Delete, !busy && details?.system != true) { onAction(CurrentAppAction.Uninstall) }
        if (busy) LoadingIndicator(Modifier.size(36.dp))
    }
}

@Composable
private fun Chip(text: String) = AssistChip(onClick = {}, label = { Text(text) })

@Composable
private fun Action(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) =
    FilledTonalButton(onClick = onClick, enabled = enabled) { Icon(icon, null, Modifier.size(18.dp)); Text("  $label") }

@Composable
private fun Danger(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        Text("  $label", color = MaterialTheme.colorScheme.error)
    }

