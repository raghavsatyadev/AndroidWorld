package io.github.raghavsatyadev.androidworld.desktop.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsIntent
import io.github.raghavsatyadev.androidworld.core.application.appdetails.DATABASE_PAGE_SIZE
import io.github.raghavsatyadev.androidworld.core.application.appdetails.DatabaseEditorState
import io.github.raghavsatyadev.androidworld.core.domain.appdata.SqlResult
import io.github.raghavsatyadev.androidworld.core.domain.appdata.SqlRows
import io.github.raghavsatyadev.androidworld.core.domain.appdata.SqlValue

private val CELL_WIDTH = 180.dp

private data class CellEdit(val rowIndex: Int, val column: String, val value: String, val isNull: Boolean)

/** One app database, copied locally: browse tables a page at a time, edit cells, run SQL, push back on Save. */
@Composable
fun DatabaseEditor(db: DatabaseEditorState, onIntent: (AppDetailsIntent) -> Unit) {
    var edit by remember(db.fileName) { mutableStateOf<CellEdit?>(null) }
    Column(Modifier.fillMaxSize()) {
        EditorBar(
            title = db.fileName, dirty = db.dirty, saving = db.saving,
            onSave = { onIntent(AppDetailsIntent.SaveDatabase) }, onRevert = { onIntent(AppDetailsIntent.RevertDatabase) },
        )
        AnimatedVisibility(db.error != null, enter = reveal(), exit = conceal()) {
            Text(db.error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (db.loading && db.tables.isEmpty()) {
            EmptyState("Copying ${db.fileName} from the device…", loading = true)
            return@Column
        }
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(db.tables, key = { it }) { table ->
                FilterChip(selected = table == db.table, onClick = { onIntent(AppDetailsIntent.ShowTable(table)) }, label = { Text(table) })
            }
        }
        SqlBox(db.queryResult, onIntent)
        val page = db.page
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                db.table == null -> EmptyState("Pick a table")
                page == null -> EmptyState("Reading ${db.table}…", loading = true)
                else -> Grid(page, editable = page.rowIds != null) { rowIndex, column, value ->
                    edit = CellEdit(rowIndex, column, if (value is SqlValue.Null) "" else value.display, value is SqlValue.Null)
                }
            }
        }
        page?.let { Pager(it, db.offset, onIntent) }
    }
    edit?.let { current ->
        CellDialog(current, onDismiss = { edit = null }) { done ->
            onIntent(AppDetailsIntent.EditCell(done.rowIndex, done.column, if (done.isNull) null else done.value))
            edit = null
        }
    }
}

@Composable
private fun SqlBox(result: SqlResult?, onIntent: (AppDetailsIntent) -> Unit) {
    var sql by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                sql, { sql = it }, label = { Text("SQL") }, maxLines = 4,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large,
            )
            FilledTonalButton(onClick = { onIntent(AppDetailsIntent.RunSql(sql)) }, enabled = sql.isNotBlank()) { Text("Run") }
        }
        AnimatedVisibility(result != null, enter = reveal(), exit = conceal()) {
            when (result) {
                is SqlResult.Failed -> Text(result.message, color = MaterialTheme.colorScheme.error)
                is SqlResult.Updated -> Text("${result.count} rows changed", color = MaterialTheme.colorScheme.primary)
                is SqlResult.Rows -> Box(Modifier.heightIn(max = 240.dp)) { Grid(result.rows, editable = false) { _, _, _ -> } }
                null -> Unit
            }
        }
    }
}

@Composable
private fun Grid(rows: SqlRows, editable: Boolean, onCell: (Int, String, SqlValue) -> Unit) {
    val scroll: ScrollState = rememberScrollState()
    Column(Modifier.horizontalScroll(scroll)) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            rows.columns.forEach { Cell(it, bold = true) }
        }
        HorizontalDivider(Modifier.width(CELL_WIDTH * rows.columns.size))
        LazyColumn {
            itemsIndexed(rows.rows) { index, row ->
                Row {
                    row.forEachIndexed { c, value ->
                        val column = rows.columns[c]
                        Cell(
                            value.display,
                            muted = value is SqlValue.Null || value is SqlValue.Blob,
                            modifier = if (editable && value !is SqlValue.Blob) Modifier.clickable { onCell(index, column, value) } else Modifier,
                        )
                    }
                }
                HorizontalDivider(Modifier.width(CELL_WIDTH * rows.columns.size), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun Cell(text: String, bold: Boolean = false, muted: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.width(CELL_WIDTH).padding(horizontal = 12.dp, vertical = 8.dp),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        fontFamily = if (bold) null else FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        style = MaterialTheme.typography.bodyMedium,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Pager(page: SqlRows, offset: Long, onIntent: (AppDetailsIntent) -> Unit) {
    val end = offset + page.rows.size
    val total = page.totalRows
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (page.rows.isEmpty()) "No rows" else "Rows ${offset + 1}–$end" + (total?.let { " of $it" } ?: ""),
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onIntent(AppDetailsIntent.ShowPage((offset - DATABASE_PAGE_SIZE).coerceAtLeast(0))) }, enabled = offset > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous page")
        }
        IconButton(
            onClick = { onIntent(AppDetailsIntent.ShowPage(offset + DATABASE_PAGE_SIZE)) },
            enabled = if (total != null) end < total else page.rows.size == DATABASE_PAGE_SIZE,
        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next page") }
    }
}

@Composable
private fun CellDialog(initial: CellEdit, onDismiss: () -> Unit, onDone: (CellEdit) -> Unit) {
    var edit by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${initial.column}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(edit.value, { edit = edit.copy(value = it, isNull = false) }, enabled = !edit.isNull, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(edit.isNull, { edit = edit.copy(isNull = it) })
                    Text("NULL")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(edit) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
