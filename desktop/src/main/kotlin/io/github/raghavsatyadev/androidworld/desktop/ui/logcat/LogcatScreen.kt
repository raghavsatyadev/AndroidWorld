package io.github.raghavsatyadev.androidworld.desktop.ui.logcat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.core.application.logcat.LogcatControlsController
import io.github.raghavsatyadev.androidworld.core.application.logcat.LogcatControlsIntent
import io.github.raghavsatyadev.androidworld.core.application.logcat.LogcatFilterUpdate
import io.github.raghavsatyadev.androidworld.core.domain.logcat.LogSeverity
import io.github.raghavsatyadev.androidworld.core.domain.logcat.LogcatEntry
import io.github.raghavsatyadev.androidworld.core.domain.logcat.LogcatPauseState
import io.github.raghavsatyadev.androidworld.core.domain.logcat.SequencedLogcatEntry

private const val MAX_ROWS = 50_000

/** The core logcat pipeline (filter engine + pause/follow) rendered as an M3E list. */
@Composable
fun LogcatScreen(controller: LogcatControlsController) {
    val state by controller.state.collectAsState()
    // One immutable list swapped per batch: SnapshotStateList.removeAll is quadratic and froze the UI on busy devices.
    var rows by remember { mutableStateOf(controller.snapshot().takeLast(MAX_ROWS)) }
    val listState = rememberLazyListState()

    LaunchedEffect(controller) {
        controller.filterUpdates.collect { update ->
            when (update) {
                is LogcatFilterUpdate.Reset -> rows = update.entries.takeLast(MAX_ROWS)
                is LogcatFilterUpdate.Delta -> {
                    val oldest = update.oldestRetainedSequence
                    val kept = if (oldest == null) rows else rows.dropWhile { it.sequence < oldest }
                    rows = (kept + update.appended).takeLast(MAX_ROWS)
                }
            }
        }
    }
    LaunchedEffect(rows.size, state.follow) {
        if (state.follow && rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Toolbar(controller, state.query, state.minSeverity, state.packageFilterOn, state.packageFilterLabel,
            state.wrap, state.pauseState is LogcatPauseState.Paused, state.follow, state.unseen.unseenCount.toInt())
        Surface(
            Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            if (!state.isDeviceEligible) {
                EmptyState("Logcat starts when an online device is selected.")
            } else if (rows.isEmpty()) {
                EmptyState("Waiting for log lines…", loading = true)
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    ) {
                        items(rows, key = { it.sequence }) { LogRow(it, state.wrap) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Toolbar(
    c: LogcatControlsController,
    query: String,
    min: LogSeverity?,
    packageOn: Boolean,
    packageLabel: String?,
    wrap: Boolean,
    paused: Boolean,
    follow: Boolean,
    unseen: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { c.handle(LogcatControlsIntent.SetQuery(it)) },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Filter: text, tag:Foo, level:W") },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.width(320.dp),
        )
        listOf(LogSeverity.VERBOSE, LogSeverity.DEBUG, LogSeverity.INFO, LogSeverity.WARN, LogSeverity.ERROR).forEach { level ->
            FilterChip(
                selected = (min ?: LogSeverity.VERBOSE) == level,
                onClick = { c.handle(LogcatControlsIntent.SetMinSeverity(if (level == LogSeverity.VERBOSE) null else level)) },
                label = { Text(level.name.first().toString()) },
            )
        }
        FilterChip(
            selected = packageOn,
            onClick = { c.handle(LogcatControlsIntent.TogglePackageFilter) },
            leadingIcon = { Icon(Icons.Filled.FilterAlt, null) },
            label = { Text(packageLabel ?: "All apps", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        Row(Modifier.weight(1f)) {}
        IconToggleButton(checked = wrap, onCheckedChange = { c.handle(LogcatControlsIntent.ToggleWrap) }) {
            Icon(Icons.AutoMirrored.Filled.WrapText, "Wrap lines")
        }
        IconToggleButton(checked = paused, onCheckedChange = { c.handle(LogcatControlsIntent.TogglePause) }) {
            Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (paused) "Resume" else "Pause")
        }
        IconToggleButton(checked = follow, onCheckedChange = { c.handle(LogcatControlsIntent.ToggleFollow) }) {
            Icon(Icons.Filled.VerticalAlignBottom, "Follow")
        }
        if (unseen > 0) Text("$unseen new", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        IconButton(onClick = { c.handle(LogcatControlsIntent.ClearLocal) }) { Icon(Icons.Filled.ClearAll, "Clear") }
    }
}

@Composable
private fun LogRow(item: SequencedLogcatEntry, wrap: Boolean) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    when (val e = item.entry) {
        is LogcatEntry.Record -> {
            val r = e.record
            val t = r.timestamp
            val time = "%02d-%02d %02d:%02d:%02d.%03d".format(t.month, t.day, t.hour, t.minute, t.second, t.millisecond)
            val text = buildString {
                append(time).append("  ").append(r.pid.value.toString().padStart(5)).append(' ')
                append(r.severity.name.first()).append("  ").append(r.tag.value).append(": ").append(r.message.value)
                e.continuationLines.forEach { append('\n').append(it) }
            }
            Text(text, style = style, color = severityColor(r.severity), softWrap = wrap, maxLines = if (wrap) Int.MAX_VALUE else 1 + e.continuationLines.size, overflow = TextOverflow.Clip, modifier = Modifier.fillMaxWidth())
        }
        is LogcatEntry.DaemonMarker -> Text(e.raw, style = style, color = MaterialTheme.colorScheme.tertiary)
        is LogcatEntry.Malformed -> Text(e.raw, style = style, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun severityColor(s: LogSeverity): Color = when (s) {
    LogSeverity.VERBOSE -> MaterialTheme.colorScheme.outline
    LogSeverity.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    LogSeverity.INFO -> MaterialTheme.colorScheme.onSurface
    LogSeverity.WARN -> Color(0xFFC77700)
    LogSeverity.ERROR, LogSeverity.ASSERT -> MaterialTheme.colorScheme.error
}
