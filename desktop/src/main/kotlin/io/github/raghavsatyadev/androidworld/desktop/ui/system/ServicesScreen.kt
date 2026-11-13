package io.github.raghavsatyadev.androidworld.desktop.ui.system

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.ServiceRow
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.ServicesIntent
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.ServicesState
import io.github.raghavsatyadev.androidworld.desktop.di.SystemModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.ErrorText
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.RefreshButton

@Composable
fun ServicesScreen(module: SystemModule) {
    val state by module.services.state.collectAsState()
    Services(state, module.services::handle)
}

/** Running services (from `service list` and `dumpsys -l`) on the left; the chosen one's dumpsys on the right. */
@Composable
internal fun Services(state: ServicesState, onIntent: (ServicesIntent) -> Unit) {
    if (state.serial == null) {
        EmptyState("Pick a connected device to list its running services.", icon = Icons.Filled.Storage)
        return
    }
    Row(Modifier.fillMaxSize()) {
        ServiceColumn(state, onIntent, Modifier.width(340.dp).fillMaxHeight())
        VerticalDivider()
        ServiceDump(state, onIntent, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun ServiceColumn(state: ServicesState, onIntent: (ServicesIntent) -> Unit, modifier: Modifier) {
    Column(modifier.padding(top = 16.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SysSearchField(state.query, "Filter by name or interface", Modifier.weight(1f)) { onIntent(ServicesIntent.Filter(it)) }
            RefreshButton { onIntent(ServicesIntent.Load) }
        }
        Text(
            "${state.shown.size} services on ${state.serial} · ${state.rows.count { it.dumpable }} dumpable",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp)) { ErrorText(state.error) }
        if (state.loading && state.rows.isEmpty()) {
            EmptyState("Listing services", loading = true)
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            items(state.shown, key = { it.name }) { row -> ServiceItem(row, row.name == state.selected) { onIntent(ServicesIntent.Open(row.name)) } }
        }
    }
}

@Composable
private fun ServiceItem(row: ServiceRow, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.dumpable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(row.service.interfaceName, "no dumpsys".takeIf { !row.dumpable }).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ServiceDump(state: ServicesState, onIntent: (ServicesIntent) -> Unit, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                state.selected?.let { "dumpsys $it" } ?: "Pick a service",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            if (state.dumping) {
                LoadingIndicator(Modifier.size(32.dp))
                OutlinedButton(onClick = { onIntent(ServicesIntent.Cancel) }) { Text("Cancel") }
            } else if (state.lines.isNotEmpty()) {
                Text("${state.visible.size} of ${state.lines.size} lines", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SysSearchField(state.search, "Search this dump", Modifier.fillMaxWidth()) { onIntent(ServicesIntent.SearchDump(it)) }
        ErrorText(state.dumpError)
        Box(Modifier.weight(1f)) {
            when {
                state.selected == null -> EmptyState("Choose a service on the left to see its dumpsys output.", icon = Icons.Filled.Storage)
                state.dumping && state.lines.isEmpty() -> EmptyState("Dumping ${state.selected}", loading = true)
                else -> DumpText(state)
            }
        }
    }
}

@Composable
private fun DumpText(state: ServicesState) {
    val list = rememberLazyListState()
    LaunchedEffect(state.selected, state.search) { list.scrollToItem(0) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(state = list, modifier = Modifier.horizontalScroll(rememberScrollState()), contentPadding = PaddingValues(12.dp)) {
                items(state.visible) { index ->
                    Text(state.lines[index], fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, softWrap = false)
                }
            }
        }
    }
}
