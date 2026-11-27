package io.github.raghavsatyadev.androidworld.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolId
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(graph: AppGraph, darkTheme: Boolean?, onDarkTheme: (Boolean?) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).widthIn(max = 900.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        McpSettingsCard(graph)
        ToolsCard(graph)
        AppearanceCard(graph, darkTheme, onDarkTheme)
        AboutCard()
    }
}

@Composable
private fun ToolsCard(graph: AppGraph) {
    val initial = remember { graph.adapters.readSettingsNow() }
    var adb by remember { mutableStateOf(initial.adbPathOverride.orEmpty()) }
    var scrcpy by remember { mutableStateOf(initial.scrcpyPathOverride.orEmpty()) }
    var captureDir by remember { mutableStateOf(initial.captureDirectory.orEmpty()) }
    var bufferKb by remember { mutableStateOf(initial.logcatBufferSizeKb.toString()) }
    var refresh by remember { mutableStateOf(0) }
    val guide = rememberInstallGuideState()
    val scope = rememberCoroutineScope()
    InstallGuideHost(graph, guide)
    SectionCard(title = "Tools and capture") {
        Text(
            "Leave a path empty to find it from ANDROID_HOME, the default SDK folder or PATH.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToolPathField(graph, ToolId.Adb, "adb path", adb, { adb = it }, refresh) { guide.open = ToolId.Adb }
        ToolPathField(graph, ToolId.Scrcpy, "scrcpy path", scrcpy, { scrcpy = it }, refresh) { guide.open = ToolId.Scrcpy }
        OutlinedTextField(captureDir, { captureDir = it }, label = { Text("Screenshot folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            bufferKb, { bufferKb = it.filter(Char::isDigit) }, label = { Text("Logcat buffer (KB)") }, singleLine = true,
            modifier = Modifier.widthIn(max = 240.dp),
        )
        Button(onClick = {
            val saving = graph.saveSettings {
                it.copy(
                    adbPathOverride = adb.trim().ifEmpty { null },
                    scrcpyPathOverride = scrcpy.trim().ifEmpty { null },
                    captureDirectory = captureDir.trim().ifEmpty { null },
                    logcatBufferSizeKb = (bufferKb.toIntOrNull() ?: it.logcatBufferSizeKb).coerceIn(64, 64 * 1024),
                )
            }
            graph.toast("Settings saved")
            scope.launch { saving?.join(); refresh++ }
        }) { Text("Save") }
    }
}

@Composable
private fun AppearanceCard(graph: AppGraph, darkTheme: Boolean?, onDarkTheme: (Boolean?) -> Unit) {
    var copy by remember { mutableStateOf(graph.store.state.copyScreenshotsToClipboard) }
    SectionCard(title = "Appearance and behaviour") {
        SwitchRow("Copy screenshots to the clipboard", copy) { v ->
            copy = v
            graph.store.update { it.copy(copyScreenshotsToClipboard = v) }
        }
        SwitchRow("Follow system theme", darkTheme == null) { follow -> onDarkTheme(if (follow) null else false) }
        if (darkTheme != null) SwitchRow("Dark theme", darkTheme) { onDarkTheme(it) }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
