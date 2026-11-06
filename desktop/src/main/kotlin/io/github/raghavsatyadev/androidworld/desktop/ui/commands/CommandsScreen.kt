package io.github.raghavsatyadev.androidworld.desktop.ui.commands

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.CommandsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.core.application.commands.CommandBrowserIntent
import io.github.raghavsatyadev.androidworld.core.application.commands.CommandBrowserState

@Composable
fun CommandsScreen(module: CommandsModule) {
    val state by module.browser.state.collectAsState()
    CommandBrowser(state, module.browser::handle)
}

/** The command catalog: search, category chips, and one expandable card per command. Stateless. */
@Composable
internal fun CommandBrowser(state: CommandBrowserState, onIntent: (CommandBrowserIntent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        BrowserHeader(state, onIntent)
        if (state.entries.isEmpty()) {
            EmptyState("No command matches \"${state.query}\".", icon = Icons.Filled.Search)
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.entries, key = { it.id }) { entry ->
                CommandEntryCard(
                    entry = entry,
                    state = state,
                    open = state.openId == entry.id,
                    onIntent = onIntent,
                    modifier = Modifier.widthIn(max = 960.dp).animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowserHeader(state: CommandBrowserState, onIntent: (CommandBrowserIntent) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp).widthIn(max = 960.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(CommandBrowserIntent.Search(it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search commands") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onIntent(CommandBrowserIntent.Search("")) }) { Icon(Icons.Filled.Close, "Clear search") }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            TargetDevice(state.serial)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.category == null,
                onClick = { onIntent(CommandBrowserIntent.FilterCategory(null)) },
                label = { Text("All") },
            )
            state.categories.forEach { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { onIntent(CommandBrowserIntent.FilterCategory(if (state.category == category) null else category)) },
                    label = { Text(category.label) },
                )
            }
        }
    }
}

/** Which device Run talks to, so a command never goes to a device the user didn't expect. */
@Composable
internal fun TargetDevice(serial: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Terminal, null, tint = MaterialTheme.colorScheme.primary)
        Text(
            serial ?: "No device selected",
            style = MaterialTheme.typography.labelLarge,
            color = if (serial == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
