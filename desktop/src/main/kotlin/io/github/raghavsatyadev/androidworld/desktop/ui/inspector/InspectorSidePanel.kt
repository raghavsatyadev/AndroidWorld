package io.github.raghavsatyadev.androidworld.desktop.ui.inspector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.layout.LayoutInspectorState
import io.github.raghavsatyadev.androidworld.core.domain.layout.AccessibilityIssueKind

@Composable
fun InspectorSidePanel(
    state: LayoutInspectorState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Text(
                text = "Properties",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp),
            )
            val selectedNode = state.selectedNode
            if (selectedNode == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Click the screenshot or a tree row.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (state.selectedFindings.isNotEmpty()) {
                            items(state.selectedFindings) { finding ->
                                Text(
                                    text = "• ${finding.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                )
                            }
                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                        items(state.properties) { (key, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(130.dp),
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        val findings = state.capture?.findings.orEmpty()
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Text(
                text = "Accessibility audit (${findings.size} ${if (findings.size == 1) "issue" else "issues"})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp),
            )
            if (findings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No issues found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(findings) { finding ->
                        val isSelected = finding.node.id == state.selectedNodeId
                        val rowBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = rowBg,
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(finding.node.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ) {
                                    Text(
                                        text = kindLabel(finding.kind),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1,
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = finding.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "[${finding.node.id}] ${finding.node.shortClassName.ifEmpty { "View" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun kindLabel(kind: AccessibilityIssueKind): String =
    when (kind) {
        AccessibilityIssueKind.NoLabel,
        AccessibilityIssueKind.ImageWithoutDescription -> "Missing description"
        AccessibilityIssueKind.SmallTouchTarget -> "Small target"
        AccessibilityIssueKind.DuplicateLabel -> "Duplicate label"
        AccessibilityIssueKind.NotAccessibilityFriendly -> "Not accessibility friendly"
    }
