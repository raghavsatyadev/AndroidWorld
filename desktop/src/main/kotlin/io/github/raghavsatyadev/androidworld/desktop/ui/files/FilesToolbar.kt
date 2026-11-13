package io.github.raghavsatyadev.androidworld.desktop.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.files.FileBrowserIntent
import io.github.raghavsatyadev.androidworld.core.application.files.FileBrowserState
import io.github.raghavsatyadev.androidworld.core.application.files.LocalItem
import io.github.raghavsatyadev.androidworld.core.domain.files.FileScope
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.RefreshButton

private sealed interface NameDialog {
    data object NewFolder : NameDialog
    data class Rename(val from: String) : NameDialog
    data object AppPackage : NameDialog
}

/** Scope chips, then the actions on the open folder and the selection. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilesToolbar(state: FileBrowserState, onIntent: (FileBrowserIntent) -> Unit) {
    var dialog by remember { mutableStateOf<NameDialog?>(null) }
    val idle = state.idle
    val selection = state.selectedEntries
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(state.scope == FileScope.Device, { onIntent(FileBrowserIntent.SetScope(null)) }, label = { Text("Device") }, enabled = idle)
        val app = state.scope as? FileScope.App
        FilterChip(
            app != null, { dialog = NameDialog.AppPackage },
            label = { Text(app?.let { "App data: ${it.packageName}" } ?: "App data (run-as)…") }, enabled = idle,
        )
        RefreshButton { onIntent(FileBrowserIntent.Refresh) }
        Action("New folder", Icons.Filled.CreateNewFolder, idle) { dialog = NameDialog.NewFolder }
        Action("Push files…", Icons.Filled.Upload, idle) {
            LocalFiles.chooseFiles().takeIf { it.isNotEmpty() }?.let { onIntent(FileBrowserIntent.Push(it)) }
        }
        if (state.scope == FileScope.Device) {
            Action("Push folder…", Icons.Filled.Upload, idle) {
                LocalFiles.chooseFolder("Push a folder to the device", null)?.let {
                    onIntent(FileBrowserIntent.Push(listOf(LocalItem(it, isDirectory = true))))
                }
            }
        }
        Action("Pull", Icons.Filled.Download, idle && selection.isNotEmpty(), primary = true) { onIntent(FileBrowserIntent.PullSelected) }
        Action("Rename", Icons.Filled.DriveFileRenameOutline, idle && selection.size == 1) { dialog = NameDialog.Rename(selection.single().name) }
        Action("Delete", Icons.Filled.Delete, idle && selection.isNotEmpty()) { onIntent(FileBrowserIntent.DeleteSelected) }
        state.busy?.let { Text("$it…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
    }
    when (val open = dialog) {
        null -> Unit
        NameDialog.NewFolder -> NamePrompt("New folder in ${state.path}", "Folder name", "", "Create") {
            dialog = null
            it?.let { name -> onIntent(FileBrowserIntent.NewFolder(name)) }
        }
        is NameDialog.Rename -> NamePrompt("Rename ${open.from}", "New name", open.from, "Rename") {
            dialog = null
            it?.let { name -> onIntent(FileBrowserIntent.Rename(open.from, name)) }
        }
        NameDialog.AppPackage -> NamePrompt(
            "Browse an app's private data", "Package name (the app must be debuggable)",
            (state.scope as? FileScope.App)?.packageName.orEmpty(), "Open",
        ) {
            dialog = null
            it?.let { name -> onIntent(FileBrowserIntent.SetScope(name)) }
        }
    }
}

@Composable
private fun Action(label: String, icon: ImageVector, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null)
            Text(label)
        }
    }
    if (primary) Button(onClick, enabled = enabled) { content() } else OutlinedButton(onClick, enabled = enabled) { content() }
}

/** Where pulls go, with a picker and a shortcut to open it. */
@Composable
internal fun PullFolderRow(state: FileBrowserState, onIntent: (FileBrowserIntent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pull to:", style = MaterialTheme.typography.bodyMedium)
        Text(state.pullFolder.ifEmpty { "(none)" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = {
            LocalFiles.chooseFolder("Pull files into", state.pullFolder)?.let { onIntent(FileBrowserIntent.SetPullFolder(it)) }
        }) { Text("Change…") }
        if (state.pullFolder.isNotEmpty()) TextButton(onClick = { LocalFiles.open(state.pullFolder) }) { Text("Open") }
    }
}

@Composable
internal fun MessageRow(state: FileBrowserState, onIntent: (FileBrowserIntent) -> Unit) {
    val message = state.message ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            message.text, Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium,
            color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        message.revealPath?.takeIf { !message.isError }?.let { folder -> TextButton(onClick = { LocalFiles.open(folder) }) { Text("Open folder") } }
        IconButton(onClick = { onIntent(FileBrowserIntent.DismissMessage) }) { Icon(Icons.Filled.Close, contentDescription = "Dismiss") }
    }
}

/** A one-field dialog, focused with its text selected; Enter confirms. [onDone] gets the text, or null when cancelled. */
@Composable
private fun NamePrompt(title: String, label: String, initial: String, confirmLabel: String, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { if (text.text.isNotBlank()) onDone(text.text) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it }, label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.width(420.dp).focusRequester(focus),
            )
        },
        confirmButton = { Button(onClick = submit, enabled = text.text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}
