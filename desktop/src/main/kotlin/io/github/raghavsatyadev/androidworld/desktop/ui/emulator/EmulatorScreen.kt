package io.github.raghavsatyadev.androidworld.desktop.ui.emulator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.core.application.emulator.screen.EmulatorScreenIntent
import io.github.raghavsatyadev.androidworld.core.application.emulator.screen.EmulatorScreenStatus
import io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorKeys
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.SkinLayout
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.SkinMatcher
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Embedded screen view for running Android emulators over gRPC, with hardware skins,
 * orientation rotation, and foldable posture controls.
 */
@Composable
fun EmulatorScreen(graph: AppGraph) {
    val selected by graph.selectedDevice.state.collectAsState()
    val serial = (selected as? SelectedDeviceState.Online)?.device?.serial?.value
    if (serial == null) {
        EmptyState(
            "Pick an online device from the menu at the top right to share its screen.",
            icon = Icons.Filled.PhoneAndroid,
        )
        return
    }
    if (!serial.startsWith("emulator-")) {
        EmptyState(
            "The embedded screen works with emulators only. Use Share for phones.",
            icon = Icons.Filled.PhoneAndroid,
        )
        return
    }

    val vm = graph.emulatorScreen.viewModel
    val state by vm.state.collectAsState()

    LaunchedEffect(serial) {
        vm.handle(EmulatorScreenIntent.Start(serial))
    }
    DisposableEffect(serial) {
        onDispose {
            vm.handle(EmulatorScreenIntent.Stop)
        }
    }

    var showFrame by remember { mutableStateOf(true) }

    val skins = graph.emulatorScreen.skins
    val skinPair by produceState<Pair<SkinLayout?, ImageBitmap?>>(
        initialValue = Pair(null, null),
        key1 = state.hardware,
    ) {
        val hw = state.hardware
        if (hw == null) {
            value = Pair(null, null)
            return@produceState
        }
        withContext(Dispatchers.IO) {
            val layouts = skins.layouts()
            val name = SkinMatcher.pick(layouts, hw)
            val layout = name?.let { layouts[it] }
            val background = name?.let { skins.background(it) }
            value = Pair(layout, background)
        }
    }
    val (skinLayout, skinBackground) = skinPair

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = { vm.handle(EmulatorScreenIntent.Key(EmulatorKeys.BACK)) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            FilledTonalIconButton(onClick = { vm.handle(EmulatorScreenIntent.Key(EmulatorKeys.HOME)) }) {
                Icon(Icons.Filled.Home, "Home")
            }
            FilledTonalIconButton(onClick = { vm.handle(EmulatorScreenIntent.Key(EmulatorKeys.RECENTS)) }) {
                Icon(Icons.Filled.Apps, "Recent apps")
            }
            FilledTonalIconButton(onClick = { vm.handle(EmulatorScreenIntent.RotateLeft) }) {
                Icon(Icons.Filled.RotateLeft, "Rotate left")
            }
            FilledTonalIconButton(onClick = { vm.handle(EmulatorScreenIntent.RotateRight) }) {
                Icon(Icons.Filled.RotateRight, "Rotate right")
            }
            FilterChip(
                selected = showFrame,
                onClick = { showFrame = !showFrame },
                label = { Text("Frame") },
            )
            when (val status = state.status) {
                EmulatorScreenStatus.Idle -> {}
                EmulatorScreenStatus.Connecting -> Text(
                    "Connecting…",
                    style = MaterialTheme.typography.bodyMedium,
                )
                EmulatorScreenStatus.Streaming -> Text(
                    "${state.framesPerSecond} fps",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is EmulatorScreenStatus.Failed -> Text(
                    status.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val hw = state.hardware
        if (hw?.foldable == true && hw.postures.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (posture in hw.postures) {
                    FilterChip(
                        selected = state.posture == posture,
                        onClick = { vm.handle(EmulatorScreenIntent.SetPosture(posture)) },
                        label = { Text(posture.label) },
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.frame != null -> EmulatorDeviceView(
                    frame = state.frame,
                    rotation = state.rotation,
                    skin = if (showFrame) skinLayout else null,
                    background = if (showFrame) skinBackground else null,
                    onPointer = { x, y, shown, pressed ->
                        vm.handle(EmulatorScreenIntent.Pointer(x, y, shown, pressed))
                    },
                    onKey = { vm.handle(it) },
                )
                state.status is EmulatorScreenStatus.Connecting -> EmptyState(
                    "Connecting to emulator…",
                    loading = true,
                )
                state.status is EmulatorScreenStatus.Failed -> EmptyState(
                    (state.status as EmulatorScreenStatus.Failed).message,
                    icon = Icons.Filled.PhoneAndroid,
                )
                else -> EmptyState(
                    "Starting emulator screen…",
                    loading = true,
                )
            }
        }
    }
}
