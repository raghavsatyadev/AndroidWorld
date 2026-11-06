package io.github.raghavsatyadev.androidworld.desktop.ui.commands

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.commands.TerminalIntent
import io.github.raghavsatyadev.androidworld.core.application.commands.TerminalLine
import io.github.raghavsatyadev.androidworld.core.application.commands.TerminalState
import io.github.raghavsatyadev.androidworld.core.application.commands.TerminalStatus
import io.github.raghavsatyadev.androidworld.desktop.di.CommandsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.apps.Hint
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState

@Composable
fun TerminalScreen(module: CommandsModule) {
    val state by module.terminal.state.collectAsState()
    LaunchedEffect(state.serial, state.status) {
        if (state.status == TerminalStatus.Idle) module.terminal.handle(TerminalIntent.Start)
    }
    Terminal(state, module.terminal::handle)
}

/** Scrollback above, an input line with its prompt below. Up/Down walk the history. Stateless. */
@Composable
internal fun Terminal(state: TerminalState, onIntent: (TerminalIntent) -> Unit) {
    if (state.status == TerminalStatus.NoDevice) {
        EmptyState("Pick an online device from the menu at the top right to open its shell.", icon = Icons.Filled.Terminal)
        return
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("adb shell on ${state.serial}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.status == TerminalStatus.Starting || state.status == TerminalStatus.Running) {
                LoadingIndicator(Modifier.size(28.dp))
            }
            state.lastExitCode?.takeIf { it != 0 }?.let {
                Text("exit $it", color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace)
            }
            Hint("Clear the scrollback") {
                IconButton(onClick = { onIntent(TerminalIntent.Clear) }) { Icon(Icons.Filled.ClearAll, "Clear") }
            }
            Hint("End the shell and anything it runs, then open a new one (resets the directory)") {
                IconButton(onClick = { onIntent(TerminalIntent.Restart) }) { Icon(Icons.Filled.RestartAlt, "Restart shell") }
            }
        }
        Scrollback(state.lines, Modifier.weight(1f))
        InputLine(state, onIntent)
        Text(
            "No tty: full-screen programs such as top or vi don't work (try top -n 1). Restart stops a command that won't end.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Scrollback(lines: List<TerminalLine>, modifier: Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        SelectionContainer {
            LazyColumn(state = list, modifier = Modifier.horizontalScroll(rememberScrollState()), contentPadding = PaddingValues(12.dp)) {
                items(lines) { line ->
                    val (text, color) = when (line) {
                        is TerminalLine.Command -> (if (line.prompt.isEmpty()) line.text else "${line.prompt} ${line.text}") to
                            MaterialTheme.colorScheme.primary
                        is TerminalLine.Output -> line.text to MaterialTheme.colorScheme.onSurface
                        is TerminalLine.Notice -> line.text to
                            if (line.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(text, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun InputLine(state: TerminalState, onIntent: (TerminalIntent) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(state.canSubmit) { if (state.canSubmit) focus.requestFocus() }
    OutlinedTextField(
        value = state.input,
        onValueChange = { onIntent(TerminalIntent.EditInput(it.replace("\n", ""))) },
        enabled = state.canSubmit,
        singleLine = true,
        prefix = { Text(if (state.status == TerminalStatus.Running) "stdin> " else "${state.prompt} ", fontFamily = FontFamily.Monospace) },
        placeholder = { if (state.status == TerminalStatus.Ended) Text("The shell ended. Press Restart.") },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> { onIntent(TerminalIntent.Submit); true }
                Key.DirectionUp -> { onIntent(TerminalIntent.HistoryPrevious); true }
                Key.DirectionDown -> { onIntent(TerminalIntent.HistoryNext); true }
                else -> false
            }
        },
    )
}
