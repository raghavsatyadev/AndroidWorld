package io.github.raghavsatyadev.androidworld.desktop.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.domain.discovery.DiscoveryOutcome
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolId
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/** What a user needs to install one external tool. AndroidWorld does not bundle adb or scrcpy. */
private class ToolGuide(
    val title: String,
    val what: String,
    val winget: String,
    val brew: String,
    val apt: String,
    val aptNote: String?,
    val downloadUrl: String,
    val downloadLabel: String,
    val exe: String,
)

private fun guideFor(tool: ToolId) = when (tool) {
    ToolId.Adb -> ToolGuide(
        title = "Install adb",
        what = "adb comes with Google's Android SDK Platform Tools. If Android Studio is installed, " +
            "AndroidWorld already finds it through ANDROID_HOME or the default SDK folder.",
        winget = "winget install Google.PlatformTools",
        brew = "brew install android-platform-tools",
        apt = "sudo apt install adb",
        aptNote = null,
        downloadUrl = "https://developer.android.com/tools/releases/platform-tools",
        downloadLabel = "Platform Tools download page",
        exe = "adb",
    )
    ToolId.Scrcpy -> ToolGuide(
        title = "Install scrcpy",
        what = "scrcpy mirrors and controls the phone screen. AndroidWorld needs scrcpy 3 or newer.",
        winget = "winget install Genymobile.scrcpy",
        brew = "brew install scrcpy",
        apt = "sudo apt install scrcpy",
        aptNote = "Some distributions ship a version older than 3; if so, use the GitHub release.",
        downloadUrl = "https://github.com/Genymobile/scrcpy/releases",
        downloadLabel = "scrcpy releases on GitHub",
        exe = "scrcpy",
    )
}

private fun copyText(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

internal fun openInBrowser(url: String) {
    Thread { runCatching { Desktop.getDesktop().browse(URI(url)) } }.start()
}

/** Holds which install guide (if any) is open, so any screen can show the same dialog. */
class InstallGuideState {
    var open by mutableStateOf<ToolId?>(null)
}

@Composable
fun rememberInstallGuideState() = remember { InstallGuideState() }

/** Shows the guide dialog while [state] has a tool open. */
@Composable
fun InstallGuideHost(graph: AppGraph, state: InstallGuideState) {
    state.open?.let { tool -> ToolInstallGuideDialog(graph, tool) { state.open = null } }
}

@Composable
fun ToolInstallGuideDialog(graph: AppGraph, tool: ToolId, onDismiss: () -> Unit) {
    val guide = guideFor(tool)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(guide.title) },
        text = {
            Column(
                Modifier.widthIn(min = 460.dp, max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(guide.what, style = MaterialTheme.typography.bodyMedium)
                GuideHeading("Windows")
                CommandRow(graph, guide.winget)
                GuideHeading("macOS (Homebrew)")
                CommandRow(graph, guide.brew)
                GuideHeading("Linux (Debian, Ubuntu)")
                CommandRow(graph, guide.apt)
                guide.aptNote?.let { Hint(it) }
                GuideHeading("Or download it")
                TextButton(onClick = { openInBrowser(guide.downloadUrl) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                    Text("  ${guide.downloadLabel}")
                }
                GuideHeading("Point AndroidWorld at it")
                Text(
                    "Leave the ${guide.exe} path empty in Settings to find it from ANDROID_HOME, the default SDK " +
                        "folder or PATH. Or paste the full path to ${guide.exe}.exe (${guide.exe} on macOS and " +
                        "Linux) and press Save; a pasted path works at once.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Hint(
                    "AndroidWorld reads PATH when it starts. After an installer changes PATH (winget does), " +
                        "restart AndroidWorld, or paste the full path instead.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun GuideHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CommandRow(graph: AppGraph, command: String) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionContainer(Modifier.weight(1f)) {
            Text(command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
        IconButton(onClick = { copyText(command); graph.toast("Command copied") }) {
            Icon(Icons.Filled.ContentCopy, "Copy command", Modifier.size(18.dp))
        }
    }
}

/** A "How to install" link that opens the install guide for [tool]. */
@Composable
fun HowToInstallLink(graph: AppGraph, tool: ToolId) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("How to install ${tool.name.lowercase()}") }
    if (open) ToolInstallGuideDialog(graph, tool) { open = false }
}

/** Returns the first of [tools] the app cannot find, re-checked whenever [key] changes; null while checking or when all are found. */
@Composable
fun rememberMissingTool(graph: AppGraph, vararg tools: ToolId, key: Any? = Unit): ToolId? {
    var missing by remember { mutableStateOf<ToolId?>(null) }
    LaunchedEffect(key) {
        missing = withContext(Dispatchers.IO) {
            tools.firstOrNull { runCatching { graph.toolLocator.locate(it) }.getOrNull() !is DiscoveryOutcome.Found }
        }
    }
    return missing
}
