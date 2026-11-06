package io.github.raghavsatyadev.androidworld.desktop.ui.commands

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.apps.Hint
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.fadeThrough
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import io.github.raghavsatyadev.androidworld.core.application.commands.CommandBrowserIntent
import io.github.raghavsatyadev.androidworld.core.application.commands.CommandBrowserState
import io.github.raghavsatyadev.androidworld.core.application.commands.CommandRun
import io.github.raghavsatyadev.androidworld.core.application.commands.CurrentValue
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandEntry
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandRisk

/** One catalog entry: a header that opens it, then its fields, current value, preview, Run and output. */
@Composable
internal fun CommandEntryCard(
    entry: CommandEntry,
    state: CommandBrowserState,
    open: Boolean,
    onIntent: (CommandBrowserIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec())) {
            EntryHeader(entry, open) { onIntent(CommandBrowserIntent.Open(if (open) null else entry.id)) }
            AnimatedVisibility(open, enter = reveal(), exit = conceal()) {
                EntryBody(entry, state, onIntent)
            }
        }
    }
}

@Composable
private fun EntryHeader(entry: CommandEntry, open: Boolean, onToggle: () -> Unit) {
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RiskBadge(entry.risk)
        Text(entry.category.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Filled.ExpandMore, if (open) "Collapse" else "Expand", Modifier.rotate(turn))
    }
}

@Composable
private fun RiskBadge(risk: CommandRisk) {
    val (label, container, content) = when (risk) {
        CommandRisk.Read -> Triple("Read", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        CommandRisk.Reversible -> Triple("Changes", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        CommandRisk.Destructive -> Triple("Asks first", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun EntryBody(entry: CommandEntry, state: CommandBrowserState, onIntent: (CommandBrowserIntent) -> Unit) {
    val values = state.valuesOf(entry)
    val problems = state.problems(entry)
    val run = state.runs[entry.id]
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        entry.warning?.let { WarningLine(it) }
        entry.fields.forEachIndexed { index, field ->
            CommandFieldInput(
                field = field,
                value = values.getOrElse(index) { "" },
                problem = problems[index],
                onValue = { onIntent(CommandBrowserIntent.SetField(entry.id, index, it)) },
            )
        }
        if (entry.current != null) CurrentValueRow(state.currents[entry.id], state.canReadCurrent(entry)) { onIntent(CommandBrowserIntent.ReadCurrent(entry.id)) }
        CommandLine(state.preview(entry))
        RunRow(entry, state, run, onIntent)
        AnimatedVisibility(run != null && !run.running, enter = reveal(), exit = conceal()) {
            run?.let { RunOutput(it) { onIntent(CommandBrowserIntent.ClearOutput(entry.id)) } }
        }
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CurrentValueRow(current: CurrentValue?, canRead: Boolean, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Now", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedContent(current, Modifier.weight(1f), transitionSpec = fadeThrough(), label = "current") { value ->
            when (value) {
                CurrentValue.Reading -> LoadingIndicator(Modifier.size(24.dp))
                is CurrentValue.Known -> SelectionContainer { Text(value.text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
                is CurrentValue.Unknown -> Text(value.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                null -> Text(if (canRead) "Not read yet" else "Fill in the fields to read it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Hint("Read again from the device") {
            IconButton(onClick = onRefresh, enabled = canRead && current != CurrentValue.Reading) { Icon(Icons.Filled.Refresh, "Read again") }
        }
    }
}

/** The exact command line that Run sends, with placeholders for fields still empty. */
@Composable
private fun CommandLine(line: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                "$ adb shell $line",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun RunRow(entry: CommandEntry, state: CommandBrowserState, run: CommandRun?, onIntent: (CommandBrowserIntent) -> Unit) {
    val running = run?.running == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { onIntent(CommandBrowserIntent.Run(entry.id)) },
            enabled = state.canRun(entry),
            colors = if (entry.risk == CommandRisk.Destructive) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            } else {
                ButtonDefaults.buttonColors()
            },
            shapes = ButtonDefaults.shapes(),
        ) {
            Icon(Icons.Filled.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize))
            Text("Run", Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
        AnimatedVisibility(running, enter = reveal(), exit = conceal()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadingIndicator(Modifier.size(32.dp))
                OutlinedButton(onClick = { onIntent(CommandBrowserIntent.Cancel(entry.id)) }, shapes = ButtonDefaults.shapes()) { Text("Cancel") }
            }
        }
        if (state.serial == null) Text("Pick a device first", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun RunOutput(run: CommandRun, onClear: () -> Unit) {
    val color = if (run.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(shape = MaterialTheme.shapes.medium, color = color, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val status = when {
                    run.exitCode != null -> "Exit code ${run.exitCode}"
                    run.failed -> "Failed"
                    else -> "Done"
                }
                Text(status, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Clear") }
            }
            SelectionContainer {
                Text(
                    run.output.ifEmpty { "(no output)" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
