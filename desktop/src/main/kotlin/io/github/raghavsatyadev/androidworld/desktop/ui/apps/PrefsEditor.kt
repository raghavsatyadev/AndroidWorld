package io.github.raghavsatyadev.androidworld.desktop.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsIntent
import io.github.raghavsatyadev.androidworld.core.application.appdetails.PrefsEditorState
import io.github.raghavsatyadev.androidworld.core.domain.appdata.PrefEntry
import io.github.raghavsatyadev.androidworld.core.domain.appdata.PrefType

/** The draft an edit dialog works on; `originalKey == null` means a new entry. */
private data class PrefDraft(val originalKey: String?, val key: String, val type: PrefType, val value: String)

/** One SharedPreferences file: entries edited locally, written back (app stopped first) on Save. */
@Composable
fun PrefsEditor(prefs: PrefsEditorState, onIntent: (AppDetailsIntent) -> Unit) {
    var draft by remember(prefs.fileName) { mutableStateOf<PrefDraft?>(null) }
    Column(Modifier.fillMaxSize()) {
        EditorBar(
            title = prefs.fileName, dirty = prefs.dirty, saving = prefs.saving,
            onSave = { onIntent(AppDetailsIntent.SavePrefs) }, onRevert = { onIntent(AppDetailsIntent.RevertPrefs) },
            extra = {
                Hint("Add entry") {
                    IconButton(onClick = { draft = PrefDraft(null, "", PrefType.Text, "") }, enabled = !prefs.loading) { Icon(Icons.Filled.Add, "Add entry") }
                }
            },
        )
        AnimatedVisibility(prefs.error != null, enter = reveal(), exit = conceal()) {
            Text(prefs.error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        when {
            prefs.loading -> EmptyState("Reading ${prefs.fileName}…", loading = true)
            prefs.entries.isEmpty() -> EmptyState("This file has no entries")
            else -> LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(prefs.entries, key = { it.key }) { entry ->
                    PrefRow(entry, Modifier.animateItem(), onEdit = {
                        draft = PrefDraft(entry.key, entry.key, entry.value.type ?: PrefType.Text, entry.value.display)
                    }, onRemove = { onIntent(AppDetailsIntent.RemovePref(entry.key)) })
                }
            }
        }
    }
    draft?.let { current ->
        PrefDialog(current, onDismiss = { draft = null }) { done ->
            onIntent(AppDetailsIntent.PutPref(done.originalKey, done.key.trim(), done.type, done.value))
            draft = null
        }
    }
}

@Composable
private fun PrefRow(entry: PrefEntry, modifier: Modifier, onEdit: () -> Unit, onRemove: () -> Unit) {
    ListItem(
        modifier = modifier,
        overlineContent = { Text(entry.value.type?.label ?: "Unknown") },
        headlineContent = { Text(entry.key, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(entry.value.display, fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.value.type != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit ${entry.key}") }
                IconButton(onClick = onRemove) { Icon(Icons.Outlined.Delete, "Remove ${entry.key}", tint = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrefDialog(initial: PrefDraft, onDismiss: () -> Unit, onDone: (PrefDraft) -> Unit) {
    var draft by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.originalKey == null) "Add entry" else "Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(draft.key, { draft = draft.copy(key = it) }, label = { Text("Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrefType.entries.forEach { type ->
                        FilterChip(selected = draft.type == type, onClick = { draft = draft.copy(type = type) }, label = { Text(type.label) })
                    }
                }
                OutlinedTextField(
                    draft.value, { draft = draft.copy(value = it) },
                    label = { Text(if (draft.type == PrefType.StringSet) "Values, one per line" else "Value") },
                    singleLine = draft.type != PrefType.StringSet && draft.type != PrefType.Text,
                    minLines = if (draft.type == PrefType.StringSet) 3 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(draft) }, enabled = draft.key.isNotBlank()) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
