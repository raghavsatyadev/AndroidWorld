package io.github.raghavsatyadev.androidworld.desktop.ui.emulators

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.sdk.ImageMessage
import io.github.raghavsatyadev.androidworld.core.application.sdk.ImageTask
import io.github.raghavsatyadev.androidworld.core.application.sdk.ImageTaskKind
import io.github.raghavsatyadev.androidworld.core.application.sdk.SystemImagesIntent
import io.github.raghavsatyadev.androidworld.core.application.sdk.SystemImagesViewModel
import io.github.raghavsatyadev.androidworld.core.domain.sdk.SystemImage
import io.github.raghavsatyadev.androidworld.desktop.ui.components.SectionCard
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.Caption
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.RefreshButton
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import kotlinx.coroutines.delay

/**
 * System images discovery, installation, and removal through the `android` CLI.
 */
@Composable
fun SystemImagesCard(vm: SystemImagesViewModel) {
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { vm.handle(SystemImagesIntent.Load) }
    if (state.cliMissing) return

    SectionCard(
        "System images",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("All ABIs", style = MaterialTheme.typography.labelLarge)
                Switch(state.showAllAbis, { vm.handle(SystemImagesIntent.SetShowAllAbis(it)) })
                RefreshButton { vm.handle(SystemImagesIntent.Refresh) }
            }
        },
    ) {
        MessageBanner(state.message) { vm.handle(SystemImagesIntent.DismissMessage) }
        RunningTaskRow(state.task) { vm.handle(SystemImagesIntent.Cancel) }
        if (state.loading && !state.loaded) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoadingIndicator(Modifier.size(32.dp))
                Caption("Reading system images from the android CLI…")
            }
        } else {
            InstalledSection(
                installed = state.installed,
                taskRunning = state.task != null,
                onRemove = { vm.handle(SystemImagesIntent.Remove(it)) },
            )
            AvailableSection(
                downloadable = state.downloadable,
                showAllAbis = state.showAllAbis,
                taskRunning = state.task != null,
                onInstall = { vm.handle(SystemImagesIntent.Install(it)) },
            )
        }
    }
}

@Composable
private fun InstalledSection(
    installed: List<SystemImage>,
    taskRunning: Boolean,
    onRemove: (SystemImage) -> Unit,
) {
    Text("Installed", style = MaterialTheme.typography.titleSmall)
    if (installed.isEmpty()) {
        Caption("None installed yet.")
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            installed.forEach { image ->
                key(image.path) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${image.apiLabel} · ${image.tagLabel}", style = MaterialTheme.typography.bodyLarge)
                            Caption("${image.abi} · ${image.version}")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { onRemove(image) },
                            enabled = !taskRunning,
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableSection(
    downloadable: List<SystemImage>,
    showAllAbis: Boolean,
    taskRunning: Boolean,
    onInstall: (SystemImage) -> Unit,
) {
    Text("Available", style = MaterialTheme.typography.titleSmall)
    if (!showAllAbis) {
        Caption("Images for this computer's CPU. Turn on All ABIs to see the rest.")
    }
    var filter by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = filter,
        onValueChange = { filter = it },
        singleLine = true,
        placeholder = { Text("Filter, e.g. 35 or Play") },
        modifier = Modifier.fillMaxWidth(),
    )
    val trimmed = filter.trim()
    val filtered = remember(downloadable, trimmed) {
        if (trimmed.isEmpty()) {
            downloadable
        } else {
            downloadable.filter { image ->
                image.apiLabel.contains(trimmed, ignoreCase = true) ||
                    image.tagLabel.contains(trimmed, ignoreCase = true) ||
                    image.abi.contains(trimmed, ignoreCase = true) ||
                    image.description.contains(trimmed, ignoreCase = true)
            }
        }
    }
    val shownMatches = filtered.take(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shownMatches.forEach { image ->
                key(image.path) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${image.apiLabel} · ${image.tagLabel}", style = MaterialTheme.typography.bodyLarge)
                            Caption("${image.abi} · ${image.version}")
                        }
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(
                            onClick = { onInstall(image) },
                            enabled = !taskRunning,
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Install")
                        }
                    }
                }
            }
        }
    }
    if (filtered.size > 50) {
        Caption("Showing 50 of ${filtered.size}; filter to narrow down.")
    }
}

@Composable
private fun RunningTaskRow(task: ImageTask?, onCancel: () -> Unit) {
    var lastTask by remember { mutableStateOf(task) }
    if (task != null) lastTask = task
    AnimatedVisibility(task != null, enter = reveal(), exit = conceal()) {
        val current = task ?: lastTask ?: return@AnimatedVisibility
        val start = remember(task) { System.currentTimeMillis() }
        var now by remember(task) { mutableStateOf(start) }
        LaunchedEffect(task) {
            if (task != null) {
                while (true) {
                    delay(1000)
                    now = System.currentTimeMillis()
                }
            }
        }
        val elapsedSec = maxOf(0L, (now - start) / 1000)
        val elapsedStr = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LoadingIndicator(Modifier.size(24.dp))
            val text = when (current.kind) {
                ImageTaskKind.Installing ->
                    "Downloading and installing ${current.image.apiLabel} ${current.image.tagLabel} (${current.image.abi})… Elapsed $elapsedStr"
                ImageTaskKind.Removing ->
                    "Deleting ${current.image.apiLabel} ${current.image.tagLabel} (${current.image.abi})…"
            }
            Caption(text)
            Spacer(Modifier.weight(1f))
            if (current.kind == ImageTaskKind.Installing) {
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun MessageBanner(message: ImageMessage?, onDismiss: () -> Unit) {
    AnimatedVisibility(message != null, enter = reveal(), exit = conceal()) {
        val shown = message ?: return@AnimatedVisibility
        val colors = MaterialTheme.colorScheme
        Surface(
            color = if (shown.isError) colors.errorContainer else colors.secondaryContainer,
            contentColor = if (shown.isError) colors.onErrorContainer else colors.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (shown.isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle, null)
                Text(shown.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Dismiss") }
            }
        }
    }
}
