package io.github.raghavsatyadev.androidworld.desktop.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import io.github.raghavsatyadev.androidworld.core.domain.discovery.DiscoveryError
import io.github.raghavsatyadev.androidworld.core.domain.discovery.DiscoveryOutcome
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolId
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolSource
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun ToolSource.label() = when (this) {
    ToolSource.ConfiguredPath -> "path set here"
    ToolSource.AndroidSdk -> "Android SDK"
    ToolSource.PathFallback -> "PATH"
}

/** Text under the field: the path the app actually uses, or why it has none. */
private fun DiscoveryOutcome.describe(): Pair<String, Boolean> = when (this) {
    is DiscoveryOutcome.Found -> "Using ${java.io.File(tool.path.value).path} (${tool.source.label()}, ${tool.version})" to false
    is DiscoveryOutcome.Failed -> when (val e = error) {
        is DiscoveryError.ToolNotFound -> "Not found in ANDROID_HOME, the default SDK folder or PATH" to true
        is DiscoveryError.ExecutableInvalid -> "Not usable: ${e.path} (${e.reason})" to true
        is DiscoveryError.VersionQueryFailed -> "Found ${e.path}, but it did not report a version (${e.reason})" to true
    }
}

/**
 * A tool path field that shows what AndroidWorld detected: the detected path as the placeholder when
 * the field is empty, and the path in use under it. The (?) icon opens the install guide.
 * [refreshKey] changes after Save so the detection runs again.
 */
@Composable
internal fun ToolPathField(
    graph: AppGraph,
    tool: ToolId,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    refreshKey: Int,
    onHelp: () -> Unit,
) {
    var outcome by remember { mutableStateOf<DiscoveryOutcome?>(null) }
    LaunchedEffect(refreshKey) {
        outcome = withContext(Dispatchers.IO) { runCatching { graph.toolLocator.locate(tool, forceRefresh = refreshKey > 0) }.getOrNull() }
    }
    val found = (outcome as? DiscoveryOutcome.Found)?.tool
    val (status, isError) = outcome?.describe() ?: ("Detecting…" to false)
    OutlinedTextField(
        value, onValueChange,
        label = { Text(label) },
        placeholder = found?.takeIf { it.source != ToolSource.ConfiguredPath }?.let { { Text(java.io.File(it.path.value).path) } },
        supportingText = {
            Text(
                status, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            IconButton(onClick = onHelp) { Icon(Icons.AutoMirrored.Outlined.HelpOutline, "How to install ${tool.name.lowercase()}") }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
