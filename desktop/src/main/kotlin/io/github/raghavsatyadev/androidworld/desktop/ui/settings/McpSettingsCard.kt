package io.github.raghavsatyadev.androidworld.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.core.application.mcp.ClaudeCodeConnection
import io.github.raghavsatyadev.androidworld.core.application.mcp.McpAccess
import io.github.raghavsatyadev.androidworld.core.domain.feedback.FeedbackSeverity
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun copyText(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

@Composable
fun McpSettingsCard(graph: AppGraph) {
    val mcp = graph.mcp
    val status by mcp.status.collectAsState()
    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(mcp.token()) }
    var showToken by remember { mutableStateOf(false) }

    SectionCard(title = "MCP server") {
        Text(
            "Lets AI agents (Claude Code, Cursor, …) see and drive the selected device over a local, token-protected server.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val options = listOf(McpAccess.Off to "Off", McpAccess.ReadOnly to "Read only", McpAccess.FullControl to "Full control")
        SingleChoiceSegmentedButtonRow(Modifier.width(480.dp)) {
            options.forEachIndexed { i, (access, label) ->
                SegmentedButton(
                    selected = status.access == access,
                    onClick = { mcp.setAccess(access) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label, maxLines = 1, softWrap = false) }
            }
        }
        when {
            status.error != null -> Text("Could not start: ${status.error}", color = MaterialTheme.colorScheme.error)
            status.running -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(status.url.orEmpty(), fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                IconButton(onClick = { copyText(status.url.orEmpty()); graph.toast("URL copied") }) { Icon(Icons.Filled.ContentCopy, "Copy URL") }
            }
            else -> Text("Server is off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Key, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (showToken) token else "•".repeat(24),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide" else "Show") }
            IconButton(onClick = { copyText(token); graph.toast("Token copied") }) { Icon(Icons.Filled.ContentCopy, "Copy token") }
            OutlinedButton(onClick = { token = mcp.regenerateToken(); graph.toast("New token; connected agents must reconnect") }) { Text("Regenerate") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = status.running && !connecting,
                onClick = {
                    connecting = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { mcp.connectClaudeCode() }
                        connecting = false
                        when (result) {
                            ClaudeCodeConnection.Connected -> graph.toast("Added to Claude Code as \"androidworld\"", FeedbackSeverity.Success)
                            ClaudeCodeConnection.CliNotFound -> graph.toast("Claude Code CLI not found on PATH", FeedbackSeverity.Warning)
                            is ClaudeCodeConnection.Failed -> graph.toast(result.reason, FeedbackSeverity.Error)
                        }
                    }
                },
            ) { Text("Connect Claude Code") }
            if (connecting) LoadingIndicator(Modifier.size(36.dp))
            Text("${status.toolCount} tools", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status.sessions.isNotEmpty()) {
            Text("Connected agents", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                status.sessions.forEach { s ->
                    Text("${s.clientName}" + (s.lastCall?.let { " · last: $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
