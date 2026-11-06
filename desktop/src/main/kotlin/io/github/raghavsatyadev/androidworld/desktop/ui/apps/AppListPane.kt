package io.github.raghavsatyadev.androidworld.desktop.ui.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.AppIconImage
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.core.application.apps.AppsIntent
import io.github.raghavsatyadev.androidworld.core.application.apps.AppsRow
import io.github.raghavsatyadev.androidworld.core.application.apps.AppsViewState

/** Searchable installed-app list. Stateless: everything comes from [state], every action is an [AppsIntent]. */
@Composable
fun AppListPane(state: AppsViewState, onIntent: (AppsIntent) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { onIntent(AppsIntent.SetQuery(it)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { onIntent(AppsIntent.ClearFilter) }) { Icon(Icons.Filled.Close, "Clear search") }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = state.showSystemPackages,
                onClick = { onIntent(AppsIntent.ToggleSystemPackages) },
                label = { Text("System apps") },
            )
            Text(
                "${state.rows.size} apps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Hint("Refresh") { IconButton(onClick = { onIntent(AppsIntent.Refresh) }) { Icon(Icons.Filled.Refresh, "Refresh") } }
        }
        when {
            !state.hasDevice -> EmptyState("Connect a device to see its apps", Icons.Outlined.Apps)
            state.errorMessage != null && state.rows.isEmpty() -> EmptyState(state.errorMessage!!, Icons.Outlined.Apps)
            state.isLoading && state.rows.isEmpty() -> EmptyState("Reading packages…", loading = true)
            state.isFilteredEmpty -> EmptyState("No app matches “${state.query}”", Icons.Filled.Search)
            state.isGenuinelyEmpty -> EmptyState("No apps installed", Icons.Outlined.Apps)
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.rows, key = { it.packageName }) { row ->
                    Column(Modifier.animateItem()) {
                        row.sectionHeader?.let {
                            Text(
                                it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        AppRow(row, onIntent)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(row: AppsRow, onIntent: (AppsIntent) -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
            .clickable { onIntent(AppsIntent.SelectPackage(row.packageName)) },
        colors = ListItemDefaults.colors(
            containerColor = if (row.isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        leadingContent = { AppIconImage(row.icon, size = 40.dp) },
        headlineContent = { Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(row.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.isDebuggable == true) Hint("Debuggable") { Icon(Icons.Outlined.BugReport, "Debuggable", tint = MaterialTheme.colorScheme.tertiary) }
                IconToggleButton(checked = row.isPinned, onCheckedChange = { onIntent(AppsIntent.TogglePin(row.packageName)) }) {
                    Icon(if (row.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (row.isPinned) "Unpin" else "Pin")
                }
            }
        },
    )
}

/** A plain tooltip, the desktop replacement for a long-press label. */
@Composable
fun Hint(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}
