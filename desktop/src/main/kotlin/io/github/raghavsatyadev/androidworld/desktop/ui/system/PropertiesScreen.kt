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
import androidx.compose.material.icons.filled.Memory
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
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.PropertiesIntent
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.PropertiesState
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.PropertyKind
import io.github.raghavsatyadev.androidworld.desktop.di.SystemModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.ErrorText
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.RefreshButton

@Composable
fun PropertiesScreen(module: SystemModule) {
    val state by module.properties.state.collectAsState()
    Properties(state, module.properties::handle)
}

/** A property being edited; [name] null means a new one. */
private data class PropEdit(val name: String?, val value: String)

/** Every `getprop` value, searchable by name or value; click one to `setprop` it. Stateless apart from the open dialog. */
@Composable
internal fun Properties(state: PropertiesState, onIntent: (PropertiesIntent) -> Unit) {
    if (state.serial == null) {
        EmptyState("Pick a connected device to see its system properties.", icon = Icons.Filled.Memory)
        return
    }
    var editing by remember(state.serial) { mutableStateOf<PropEdit?>(null) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SysSearchField(state.query, "Search names and values", Modifier.weight(1f)) { onIntent(PropertiesIntent.Filter(it)) }
            if (state.writing || state.loading) LoadingIndicator(Modifier.size(32.dp))
            RefreshButton { onIntent(PropertiesIntent.Load) }
            FilledTonalButton(onClick = { editing = PropEdit(null, "") }) {
                Icon(Icons.Filled.Add, null)
                Text("New", Modifier.padding(start = 8.dp))
            }
        }
        val shell = when (state.root) {
            true -> "root shell: ro.* writes can be tried"
            false -> "non-root shell: ro.* is read-only"
            null -> "checking shell"
        }
        Text(
            "${state.shown.size} of ${state.properties.size} properties on ${state.serial} · $shell",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ErrorText(state.error)
        WriteBanner(state.result) { onIntent(PropertiesIntent.DismissResult) }
        if (state.loading && state.properties.isEmpty()) {
            EmptyState("Reading properties", loading = true)
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(state.shown, key = { it.name }) { prop ->
                    KeyValueRow(prop.name, prop.value, state.lockReason(prop.name), selected = false, onClick = { editing = PropEdit(prop.name, prop.value) })
                }
            }
        }
    }
    editing?.let { edit ->
        val note = edit.name?.let(PropertyKind::of)?.let {
            when (it) {
                PropertyKind.ReadOnly -> "ro.* properties are write-once: even as root, init rejects a new value for one already set."
                PropertyKind.Persistent -> "persist.* values are kept across reboots."
                else -> "Values are limited to 91 bytes and are lost on reboot."
            }
        } ?: "New properties can't be removed again until the device reboots."
        ValueEditDialog(
            title = if (edit.name == null) "New property" else "Edit property",
            initialKey = edit.name,
            initialValue = edit.value,
            note = note,
            lockReason = edit.name?.let(state::lockReason),
            onDismiss = { editing = null },
            onSave = { name, value -> editing = null; onIntent(PropertiesIntent.Set(name, value)) },
        )
    }
}
