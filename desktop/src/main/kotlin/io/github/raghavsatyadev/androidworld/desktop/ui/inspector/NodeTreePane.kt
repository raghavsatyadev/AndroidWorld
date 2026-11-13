package io.github.raghavsatyadev.androidworld.desktop.ui.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.layout.LayoutInspectorState

@Composable
fun NodeTreePane(
    state: LayoutInspectorState,
    onSelect: (Int) -> Unit,
    onToggle: (Int) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val findingNodeIds: Set<Int> = remember(state.capture) {
        state.capture?.findings?.map { it.node.id }?.toSet().orEmpty()
    }
    val listState = rememberLazyListState()

    LaunchedEffect(state.selectedNodeId) {
        val selectedId = state.selectedNodeId ?: return@LaunchedEffect
        val index = state.rows.indexOfFirst { it.node.id == selectedId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Column(modifier = modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Hierarchy",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onExpandAll) { Text("Expand all") }
            TextButton(onClick = onCollapseAll) { Text("Collapse all") }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.rows, key = { it.node.id }) { row ->
                val selected = row.node.id == state.selectedNodeId
                val hasFinding = row.node.id in findingNodeIds
                val rowBg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(rowBg)
                        .clickable { onSelect(row.node.id) }
                        .padding(
                            start = (row.depth * 14).dp,
                            end = 8.dp,
                            top = 4.dp,
                            bottom = 4.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (row.hasChildren) {
                        Box(
                            modifier = Modifier.size(20.dp).clickable { onToggle(row.node.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (row.expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                contentDescription = if (row.expanded) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Spacer(Modifier.size(20.dp))
                    }

                    if (hasFinding) {
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                        )
                    } else {
                        Spacer(Modifier.width(4.dp))
                    }

                    val shortName = row.node.shortClassName.ifEmpty { "View" }
                    val label = row.node.label
                    val text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(shortName)
                        }
                        if (label.isNotEmpty() && label != shortName) {
                            append(" ")
                            append(label)
                        }
                    }

                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}
