package io.github.raghavsatyadev.androidworld.desktop.ui.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.domain.files.RemoteEntry
import io.github.raghavsatyadev.androidworld.core.domain.files.RemoteEntryType

/** The folder's entries. Click selects one, the box adds to the selection, double-click opens. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileList(
    entries: List<RemoteEntry>,
    selected: Set<String>,
    onSelect: (name: String, additive: Boolean) -> Unit,
    onOpen: (RemoteEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(entries, key = { it.name }) { entry ->
            val isSelected = entry.name in selected
            Row(
                Modifier.fillMaxWidth()
                    .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .combinedClickable(onClick = { onSelect(entry.name, false) }, onDoubleClick = { onOpen(entry) })
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(isSelected, onCheckedChange = { onSelect(entry.name, true) })
                Icon(iconFor(entry), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    entry.name + (entry.linkTarget?.let { "  →  $it" } ?: ""),
                    Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Meta(entry.size?.let(::humanSize).orEmpty(), 80)
                Meta(entry.modified, 140)
                Meta(entry.permissions, 100, mono = true)
                Meta("${entry.owner}:${entry.group}", 150)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Meta(text: String, width: Int, mono: Boolean = false) {
    Text(
        text, Modifier.width(width.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = if (mono) FontFamily.Monospace else null,
    )
}

private fun iconFor(entry: RemoteEntry) = when (entry.type) {
    RemoteEntryType.Directory -> Icons.Filled.Folder
    RemoteEntryType.Link -> Icons.Filled.Link
    RemoteEntryType.File -> Icons.AutoMirrored.Filled.InsertDriveFile
    else -> Icons.Filled.Memory
}

internal fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
    return "%.1f %s".format(value, units[unit])
}
