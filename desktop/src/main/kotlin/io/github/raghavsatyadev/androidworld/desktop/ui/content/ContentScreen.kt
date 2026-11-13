package io.github.raghavsatyadev.androidworld.desktop.ui.content

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.content.ContentNotice
import io.github.raghavsatyadev.androidworld.core.application.content.ContentViewerIntent
import io.github.raghavsatyadev.androidworld.core.application.content.ContentViewerState
import io.github.raghavsatyadev.androidworld.core.domain.content.PimTable
import io.github.raghavsatyadev.androidworld.desktop.di.ContentModule
import io.github.raghavsatyadev.androidworld.desktop.platform.saveCsvWithDialog
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.Caption
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.ChoiceGroup
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.RefreshButton

@Composable
fun ContentScreen(module: ContentModule) {
    val state by module.viewer.state.collectAsState()
    var exported by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.table, state.serial) { exported = null }
    ContentViewer(
        state = state,
        exported = exported,
        onIntent = module.viewer::handle,
        onExport = {
            exported = runCatching { saveCsvWithDialog(state.csvFileName(), state.csv())?.let { "Saved ${it.absolutePath}" } }
                .getOrElse { "Couldn't save: ${it.message}" }
        },
    )
}

/** Table picker, search and export on top; the chosen provider's rows below. Stateless. */
@Composable
internal fun ContentViewer(state: ContentViewerState, exported: String?, onIntent: (ContentViewerIntent) -> Unit, onExport: () -> Unit) {
    if (state.serial == null) {
        EmptyState("Pick a connected device to read its contacts, messages, calls and calendar.", icon = Icons.Filled.Contacts)
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceGroup(PimTable.entries, state.table, { it.label }, { onIntent(ContentViewerIntent.Select(it)) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchField(state.search, "Search ${state.table.noun}", Modifier.weight(1f)) { onIntent(ContentViewerIntent.Search(it)) }
            if (state.loading) LoadingIndicator(Modifier.size(32.dp)) else RefreshButton { onIntent(ContentViewerIntent.Refresh) }
            OutlinedButton(onClick = onExport, enabled = state.visible.isNotEmpty()) {
                Icon(Icons.Filled.FileDownload, null, Modifier.size(18.dp))
                Text("Export CSV", Modifier.padding(start = 8.dp))
            }
        }
        val total = state.data.rows.size
        val count = if (state.search.isBlank()) "$total ${if (total == 1) "row" else "rows"}" else "${state.visible.size} of $total rows"
        Caption(listOfNotNull("$count · ${state.table.spec.uri} on ${state.serial}", exported).joinToString(" · "))
        when (val notice = state.notice) {
            null -> if (state.loading && state.data.rows.isEmpty()) EmptyState("Reading ${state.table.noun}", loading = true) else Table(state)
            else -> Notice(notice, state.table)
        }
    }
}

@Composable
private fun Notice(notice: ContentNotice, table: PimTable) {
    when (notice) {
        ContentNotice.NoDevice -> EmptyState("No device selected.", icon = Icons.Filled.Contacts)
        ContentNotice.Empty -> EmptyState("No ${table.noun} on this device.", icon = Icons.Filled.Contacts)
        is ContentNotice.PermissionDenied -> EmptyState(
            "adb shell isn't allowed to read ${table.noun} on this device.\n${notice.message}",
            icon = Icons.Filled.Lock,
        )
        is ContentNotice.ProviderMissing -> EmptyState(
            "This device has no provider for ${table.noun}.\n${notice.message}",
            icon = Icons.Filled.Contacts,
        )
        is ContentNotice.Failed -> EmptyState("Couldn't read ${table.noun}: ${notice.message}", icon = Icons.Filled.Contacts)
    }
}

/** Wide text columns (messages, descriptions) get more room; the rest share a fixed width. */
private fun columnWidth(header: String): Dp = when (header) {
    "Message", "Description" -> 420.dp
    "Name", "Title", "Emails", "Phones", "Location" -> 240.dp
    else -> 170.dp
}

@Composable
private fun Table(state: ContentViewerState) {
    val list = rememberLazyListState()
    LaunchedEffect(state.table, state.search) { list.scrollToItem(0) }
    val headers = state.data.headers
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            TableRow(headers, headers, header = true)
            HorizontalDivider(Modifier.width(headers.sumOf { columnWidth(it).value.toDouble() }.toFloat().dp + 24.dp))
            SelectionContainer {
                LazyColumn(state = list, contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(state.visible) { index -> TableRow(headers, state.data.rows[index], header = false) }
                }
            }
        }
    }
}

@Composable
private fun TableRow(headers: List<String>, cells: List<String>, header: Boolean) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        headers.forEachIndexed { i, column ->
            Text(
                cells.getOrElse(i) { "" },
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else null,
                maxLines = if (header) 1 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(columnWidth(column)).padding(end = 12.dp),
            )
        }
    }
}

@Composable
private fun SearchField(value: String, placeholder: String, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onValue("") }) { Icon(Icons.Filled.Close, "Clear") } },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
    )
}
