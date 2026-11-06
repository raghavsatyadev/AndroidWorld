package io.github.raghavsatyadev.androidworld.desktop.ui.commands

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import io.github.raghavsatyadev.androidworld.desktop.di.CommandsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.ErrorText
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.RefreshButton
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.fadeThrough
import io.github.raghavsatyadev.androidworld.core.application.commands.DumpsysIntent
import io.github.raghavsatyadev.androidworld.core.application.commands.DumpsysState

@Composable
fun DumpsysScreen(module: CommandsModule) {
    val state by module.dumpsys.state.collectAsState()
    Dumpsys(state, module.dumpsys::handle)
}

/** Services on the left; the chosen service's dump on the right, line by line and searchable. Stateless. */
@Composable
internal fun Dumpsys(state: DumpsysState, onIntent: (DumpsysIntent) -> Unit) {
    if (state.serial == null) {
        EmptyState("Pick a connected device to list its services.", icon = Icons.Filled.Storage)
        return
    }
    Row(Modifier.fillMaxSize()) {
        ServiceList(state, onIntent, Modifier.width(280.dp).fillMaxHeight())
        VerticalDivider()
        DumpPane(state, onIntent, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun ServiceList(state: DumpsysState, onIntent: (DumpsysIntent) -> Unit, modifier: Modifier) {
    Column(modifier.padding(top = 16.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(state.serviceQuery, "Filter services", Modifier.weight(1f)) { onIntent(DumpsysIntent.FilterServices(it)) }
            RefreshButton { onIntent(DumpsysIntent.LoadServices) }
        }
        Text(
            "${state.shownServices.size} services on ${state.serial}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.padding(horizontal = 16.dp)) { ErrorText(state.servicesError) }
        if (state.loadingServices && state.services.isEmpty()) {
            EmptyState("Listing services", loading = true)
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
            items(state.shownServices, key = { it }) { service ->
                val selected = service == state.service
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().animateItem(),
                ) {
                    Text(
                        service,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onIntent(DumpsysIntent.Dump(service)) }.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DumpPane(state: DumpsysState, onIntent: (DumpsysIntent) -> Unit, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                state.service?.let { "dumpsys $it" } ?: "Pick a service",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            if (state.dumping) {
                LoadingIndicator(Modifier.size(32.dp))
                OutlinedButton(onClick = { onIntent(DumpsysIntent.Cancel) }) { Text("Cancel") }
            } else if (state.lines.isNotEmpty()) {
                Text("${state.visible.size} of ${state.lines.size} lines", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SearchField(state.search, "Search this dump", Modifier.fillMaxWidth()) { onIntent(DumpsysIntent.SearchDump(it)) }
        ErrorText(state.dumpError)
        AnimatedContent(state.service, Modifier.weight(1f), transitionSpec = fadeThrough(), label = "dump") { service ->
            when {
                service == null -> EmptyState("Choose a service on the left to see its dump.", icon = Icons.Filled.Storage)
                state.dumping && state.lines.isEmpty() -> EmptyState("Dumping $service", loading = true)
                else -> DumpLines(state)
            }
        }
    }
}

@Composable
private fun DumpLines(state: DumpsysState) {
    val list = rememberLazyListState()
    LaunchedEffect(state.service, state.search) { list.scrollToItem(0) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(state = list, modifier = Modifier.horizontalScroll(rememberScrollState()), contentPadding = PaddingValues(12.dp)) {
                items(state.visible) { index ->
                    Text(
                        state.lines[index],
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, placeholder: String, modifier: Modifier, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onValue("") }) { Icon(Icons.Filled.Close, "Clear") } },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
    )
}
