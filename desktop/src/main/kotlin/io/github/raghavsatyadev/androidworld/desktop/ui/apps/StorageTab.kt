package io.github.raghavsatyadev.androidworld.desktop.ui.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.fadeThrough
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsIntent
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsState
import io.github.raghavsatyadev.androidworld.core.application.appdetails.FileAccessState

/** Which editor the right side shows; derived from state so it follows whatever was opened last. */
private sealed interface OpenFile {
    data object None : OpenFile
    data class Prefs(val name: String) : OpenFile
    data class Database(val name: String) : OpenFile
}

/** The app's private SharedPreferences and databases, reachable only through `run-as` or root. */
@Composable
fun StorageTab(state: AppDetailsState, onIntent: (AppDetailsIntent) -> Unit) {
    when (val access = state.access) {
        FileAccessState.Checking -> EmptyState("Checking access to the app's files…", loading = true)
        is FileAccessState.Unavailable -> EmptyState(access.reason, Icons.Outlined.FolderOff)
        is FileAccessState.Available -> Row(Modifier.fillMaxSize()) {
            FileList(state, access, onIntent, Modifier.width(260.dp).fillMaxHeight())
            VerticalDivider()
            val open = when {
                state.database != null -> OpenFile.Database(state.database!!.fileName)
                state.prefs != null -> OpenFile.Prefs(state.prefs!!.fileName)
                else -> OpenFile.None
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(open, transitionSpec = fadeThrough(), contentKey = { it }) { file ->
                    when (file) {
                        OpenFile.None -> EmptyState("Pick a preferences file or a database", Icons.Outlined.Description)
                        is OpenFile.Prefs -> state.prefs?.let { PrefsEditor(it, onIntent) }
                        is OpenFile.Database -> state.database?.let { DatabaseEditor(it, onIntent) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileList(state: AppDetailsState, access: FileAccessState.Available, onIntent: (AppDetailsIntent) -> Unit, modifier: Modifier) {
    LazyColumn(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            Text(
                "Access via ${access.access.label}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp),
            )
        }
        group("Shared preferences", state.sharedPrefsFiles, Icons.Outlined.Description, selected = state.prefs?.fileName.takeIf { state.database == null }) {
            onIntent(AppDetailsIntent.OpenPrefs(it))
        }
        group("Databases", state.databaseFiles, Icons.Outlined.Storage, selected = state.database?.fileName) {
            onIntent(AppDetailsIntent.OpenDatabase(it))
        }
    }
}

private fun LazyListScope.group(
    title: String,
    files: List<String>,
    icon: ImageVector,
    selected: String?,
    onOpen: (String) -> Unit,
) {
    item(key = "h-$title") {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
    }
    if (files.isEmpty()) item(key = "e-$title") {
        Text("None", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
    }
    items(files, key = { "$title/$it" }) { name ->
        ListItem(
            modifier = Modifier.animateItem().clip(MaterialTheme.shapes.large).clickable { onOpen(name) },
            colors = ListItemDefaults.colors(
                containerColor = if (name == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
            ),
            leadingContent = { Icon(icon, null) },
            headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}

/** Save / Revert for an editor that holds unsaved local edits. */
@Composable
internal fun EditorBar(title: String, dirty: Boolean, saving: Boolean, onSave: () -> Unit, onRevert: () -> Unit, extra: @Composable () -> Unit = {}) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                when { saving -> "Saving…"; dirty -> "Unsaved changes"; else -> "Saved" },
                style = MaterialTheme.typography.labelMedium,
                color = if (dirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        extra()
        TextButton(onClick = onRevert, enabled = dirty && !saving) { Text("Revert") }
        Button(onClick = onSave, enabled = dirty && !saving, shapes = ButtonDefaults.shapes()) { Text("Save to device") }
    }
}
