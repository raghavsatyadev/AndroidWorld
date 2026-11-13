package io.github.raghavsatyadev.androidworld.desktop.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.files.FileBrowserIntent
import io.github.raghavsatyadev.androidworld.core.application.files.FileBrowserState
import io.github.raghavsatyadev.androidworld.desktop.di.FilesModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.ErrorText

@Composable
fun FilesScreen(module: FilesModule) {
    val state by module.browser.state.collectAsState()
    Files(state, module.browser::handle)
}

/**
 * The device file browser: toolbar, breadcrumbs, the folder's entries. Files and folders dropped
 * from the OS are pushed into the open folder.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun Files(state: FileBrowserState, onIntent: (FileBrowserIntent) -> Unit) {
    if (state.serial == null) {
        EmptyState("Pick a connected device to browse its files.", icon = Icons.Filled.Folder)
        return
    }
    var dropping by remember { mutableStateOf(false) }
    val dropTarget = remember(onIntent) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { dropping = true }
            override fun onExited(event: DragAndDropEvent) { dropping = false }
            override fun onEnded(event: DragAndDropEvent) { dropping = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                dropping = false
                val items = LocalFiles.dropped(event.awtTransferable)
                if (items.isNotEmpty()) onIntent(FileBrowserIntent.Push(items))
                return items.isNotEmpty()
            }
        }
    }
    Column(
        Modifier.fillMaxSize()
            .dragAndDropTarget(shouldStartDragAndDrop = { state.idle }, target = dropTarget)
            .then(if (dropping) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilesToolbar(state, onIntent)
        Breadcrumbs(state, onIntent)
        PullFolderRow(state, onIntent)
        MessageRow(state, onIntent)
        ErrorText(state.error)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.denied -> EmptyState(deniedText(state), icon = Icons.Filled.Lock)
                state.loading && state.entries.isEmpty() -> EmptyState("Reading ${state.path}…", loading = true)
                state.entries.isEmpty() && state.error == null -> EmptyState("This folder is empty. Drop files here to push them.", icon = Icons.Filled.Folder)
                else -> FileList(
                    state.entries, state.selected,
                    onSelect = { name, additive -> onIntent(FileBrowserIntent.Select(name, additive)) },
                    onOpen = { onIntent(FileBrowserIntent.OpenEntry(it)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (state.unreadable.isNotEmpty()) {
            Text(
                "${state.unreadable.size} entries could not be read.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Breadcrumbs(state: FileBrowserState, onIntent: (FileBrowserIntent) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onIntent(FileBrowserIntent.Up) }, enabled = state.canGoUp) {
            androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
        }
        state.crumbs.forEachIndexed { index, crumb ->
            if (index > 1) Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val last = index == state.crumbs.lastIndex
            TextButton(onClick = { onIntent(FileBrowserIntent.Open(crumb.path)) }, enabled = !last) {
                Text(crumb.label, style = if (last) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun deniedText(state: FileBrowserState): String =
    if (state.scope is io.github.raghavsatyadev.androidworld.core.domain.files.FileScope.App) {
        "run-as can't open ${state.path}. The app must be installed and debuggable."
    } else {
        "The adb shell user can't read ${state.path}. Rooted emulators can browse it after adb root."
    }
