package io.github.raghavsatyadev.androidworld.desktop.ui.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.DeviceSettingsIntent
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.DeviceSettingsState
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.DeviceSettingsCommands
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.SettingsNamespace
import io.github.raghavsatyadev.androidworld.desktop.di.SystemModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.ChoiceGroup
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.ErrorText
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.RefreshButton

@Composable
fun DeviceSettingsScreen(module: SystemModule) {
    val state by module.settings.state.collectAsState()
    DeviceSettings(state, module.settings::handle)
}

/** A setting being edited; [key] null means a new one. */
private data class SettingEdit(val key: String?, val value: String)

/** The system, secure and global tables of `settings`, searchable; click a key to put or delete it. */
@Composable
internal fun DeviceSettings(state: DeviceSettingsState, onIntent: (DeviceSettingsIntent) -> Unit) {
    if (state.serial == null) {
        EmptyState("Pick a connected device to browse its settings.", icon = Icons.Filled.Tune)
        return
    }
    var editing by remember(state.serial, state.namespace) { mutableStateOf<SettingEdit?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceGroup(SettingsNamespace.entries, state.namespace, { it.label }, { onIntent(DeviceSettingsIntent.SelectNamespace(it)) })
            SysSearchField(state.query, "Search keys and values", Modifier.weight(1f)) { onIntent(DeviceSettingsIntent.Filter(it)) }
            if (state.writing || state.loading) LoadingIndicator(Modifier.size(32.dp))
            RefreshButton { onIntent(DeviceSettingsIntent.Load) }
            FilledTonalButton(onClick = { editing = SettingEdit(null, "") }) {
                Icon(Icons.Filled.Add, null)
                Text("New", Modifier.padding(start = 8.dp))
            }
        }
        Text(
            "${state.shown.size} of ${state.entries.size} ${state.namespace.cli} settings on ${state.serial}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ErrorText(state.error)
        WriteBanner(state.result) { onIntent(DeviceSettingsIntent.DismissResult) }
        if (state.loading && state.entries.isEmpty()) {
            EmptyState("Reading ${state.namespace.cli} settings", loading = true)
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(state.shown, key = { it.key }) { entry ->
                    KeyValueRow(entry.key, entry.value, locked = null, selected = false, onClick = { editing = SettingEdit(entry.key, entry.value) })
                }
            }
        }
    }
    editing?.let { edit ->
        ValueEditDialog(
            title = if (edit.key == null) "New ${state.namespace.cli} setting" else "Edit ${state.namespace.cli} setting",
            initialKey = edit.key,
            initialValue = edit.value,
            note = edit.key?.let(DeviceSettingsCommands::riskNote) ?: "Apps read settings when they choose to; some changes need a restart to show.",
            lockReason = null,
            onDismiss = { editing = null },
            onSave = { key, value -> editing = null; onIntent(DeviceSettingsIntent.Put(key, value)) },
            onDelete = edit.key?.let { key -> { editing = null; onIntent(DeviceSettingsIntent.Delete(key)) } },
        )
    }
}
