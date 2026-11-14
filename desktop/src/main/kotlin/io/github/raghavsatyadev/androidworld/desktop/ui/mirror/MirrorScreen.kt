package io.github.raghavsatyadev.androidworld.desktop.ui.mirror

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.mirror.AndroidKey
import io.github.raghavsatyadev.androidworld.desktop.mirror.AutoTune
import io.github.raghavsatyadev.androidworld.desktop.mirror.LinkSpeed
import io.github.raghavsatyadev.androidworld.desktop.mirror.ScrcpyMirror
import io.github.raghavsatyadev.androidworld.desktop.mirror.ScrcpySetup
import io.github.raghavsatyadev.androidworld.desktop.mirror.ScrcpyUpdateCheck
import io.github.raghavsatyadev.androidworld.desktop.mirror.ShareOptions
import io.github.raghavsatyadev.androidworld.desktop.mirror.StreamMeasure
import io.github.raghavsatyadev.androidworld.desktop.mirror.TouchAction
import io.github.raghavsatyadev.androidworld.desktop.mirror.encodePng
import io.github.raghavsatyadev.androidworld.desktop.mirror.saveSnapshot
import io.github.raghavsatyadev.androidworld.desktop.persistence.StoredState
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.capture.DesktopRevealInFileManager
import io.github.raghavsatyadev.androidworld.core.adapters.jvm.capture.SettingsBackedCaptureDestination
import io.github.raghavsatyadev.androidworld.core.domain.capture.TimestampFileNamePolicy
import io.github.raghavsatyadev.androidworld.core.domain.feedback.FeedbackAction
import io.github.raghavsatyadev.androidworld.core.domain.feedback.FeedbackSeverity
import io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState
import io.github.raghavsatyadev.androidworld.core.domain.discovery.DiscoveryOutcome
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolId
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live screen share of the selected device through scrcpy's server, with touch and nav keys. */
@Composable
fun MirrorScreen(graph: AppGraph) {
    val selected by graph.selectedDevice.state.collectAsState()
    val serial = (selected as? SelectedDeviceState.Online)?.device?.serial?.value
    if (serial == null) {
        EmptyState("Pick an online device from the menu at the top right to share its screen.", icon = Icons.Filled.PhoneAndroid)
        return
    }
    val mirror = remember(serial) { ScrcpyMirror() }
    var running by remember(serial) { mutableStateOf(false) }
    var frame by remember(serial) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(serial) { mutableStateOf<String?>(null) }
    var updateWarning by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        updateWarning = withContext(Dispatchers.IO) {
            val scrcpy = (graph.toolLocator.locate(ToolId.Scrcpy) as? DiscoveryOutcome.Found)?.tool
            scrcpy?.let { ScrcpyUpdateCheck.warning(it.version.raw) }
        }
    }
    var options by remember { mutableStateOf(graph.store.state.shareOptions()) }
    var auto by remember { mutableStateOf(graph.store.state.shareAuto) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    fun apply(picked: ShareOptions) {
        options = picked
        graph.store.update { it.copy(shareMaxSize = picked.maxSize, shareMaxFps = picked.maxFps, shareBitRate = picked.bitRate) }
    }

    if (running && auto && frame != null) {
        // Not keyed on options: the stream restarting with a new pick must not restart the timer.
        LaunchedEffect(serial) {
            delay(AUTO_WARM_UP_MS)
            var firstCheck = true
            while (true) {
                val measure = measureStream(graph, mirror, serial)
                val image = frame
                if (measure == null || image == null || viewSize == IntSize.Zero) {
                    delay(AUTO_RETRY_MS)
                    continue
                }
                val aspect = minOf(image.width, image.height).toDouble() / maxOf(image.width, image.height)
                val scale = minOf(viewSize.width.toFloat() / image.width, viewSize.height.toFloat() / image.height)
                val picked = AutoTune.next(options, (maxOf(image.width, image.height) * scale).toInt(), aspect, measure)
                if (picked != options) {
                    apply(picked)
                    graph.toast("Auto set ${AutoTune.describe(picked, aspect, measure)}")
                }
                // The first measurement runs before the JVM has compiled the decoder and reads slow, so look again soon.
                delay(if (firstCheck) AUTO_SECOND_LOOK_MS else AutoTune.recheckSeconds(serial, measure.linkMbps) * 1000)
                firstCheck = false
            }
        }
    }

    if (running) {
        // A new options value cancels this effect and starts a fresh stream with the new settings.
        LaunchedEffect(serial, options) {
            error = null
            try {
                val setup = resolveSetup(graph) ?: error("scrcpy was not found. Install scrcpy 3 or newer and put it on PATH.")
                mirror.frames(setup, serial, options).flowOn(Dispatchers.IO).collect { frame = it }
                error = "The device stopped sharing its screen."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            }
            running = false
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (running) {
                OutlinedButton(onClick = { running = false; frame = null }) {
                    Icon(Icons.Filled.Stop, null); Text("  Stop sharing")
                }
            } else {
                Button(onClick = { running = true }) { Icon(Icons.Filled.PlayArrow, null); Text("  Share screen") }
            }
            FilledTonalIconButton(onClick = { mirror.key(AndroidKey.BACK) }, enabled = running) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            FilledTonalIconButton(onClick = { mirror.key(AndroidKey.HOME) }, enabled = running) { Icon(Icons.Filled.Home, "Home") }
            FilledTonalIconButton(onClick = { mirror.key(AndroidKey.APP_SWITCH) }, enabled = running) { Icon(Icons.Filled.Apps, "Recent apps") }
            RecordingControls(graph.recording)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        updateWarning?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.tertiary)
                Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        ShareControls(
            running = running,
            canSnapshot = frame != null,
            options = options,
            auto = auto,
            onAuto = { on ->
                auto = on
                graph.store.update { it.copy(shareAuto = on) }
            },
            onOptions = { picked ->
                // A hand-picked setting would be overwritten at the next check, so it turns Auto off.
                auto = false
                graph.store.update { it.copy(shareAuto = false) }
                apply(picked)
            },
            onSnapshot = { frame?.let { image -> scope.launch { saveFrame(graph, image) } } },
            onRotate = mirror::rotate,
        )
        Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }, contentAlignment = Alignment.Center) {
            val image = frame
            when {
                image != null && running -> DeviceFrame(image, mirror)
                running -> EmptyState("Starting screen share…", loading = true)
                else -> EmptyState("Share the device screen here. Click and drag on it to tap and swipe.", icon = Icons.Filled.PhoneAndroid)
            }
        }
    }
}

@Composable
private fun DeviceFrame(image: ImageBitmap, mirror: ScrcpyMirror) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    // Maps a pointer position in the view to device-frame pixels, following ContentScale.Fit.
    fun toFrame(p: Offset): Pair<Int, Int>? {
        if (box.width == 0 || box.height == 0) return null
        val scale = minOf(box.width.toFloat() / image.width, box.height.toFloat() / image.height)
        val left = (box.width - image.width * scale) / 2
        val top = (box.height - image.height * scale) / 2
        val x = ((p.x - left) / scale).toInt()
        val y = ((p.y - top) / scale).toInt()
        return if (x in 0 until image.width && y in 0 until image.height) x to y else null
    }
    Image(
        bitmap = image,
        contentDescription = "Device screen",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
            .pointerInput(image.width, image.height) {
                box = size
                var down = false
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val point = toFrame(change.position)
                        when (event.type) {
                            PointerEventType.Press -> point?.let { (x, y) ->
                                down = true
                                mirror.touch(TouchAction.DOWN, x, y, image.width, image.height)
                            }
                            PointerEventType.Move -> if (down && point != null) {
                                mirror.touch(TouchAction.MOVE, point.first, point.second, image.width, image.height)
                            }
                            PointerEventType.Release -> if (down) {
                                down = false
                                val (x, y) = point ?: clampToFrame(change.position, box, image)
                                mirror.touch(TouchAction.UP, x, y, image.width, image.height)
                            }
                        }
                        change.consume()
                    }
                }
            },
    )
}

private fun clampToFrame(p: Offset, box: IntSize, image: ImageBitmap): Pair<Int, Int> {
    val scale = minOf(box.width.toFloat() / image.width, box.height.toFloat() / image.height)
    val x = ((p.x - (box.width - image.width * scale) / 2) / scale).toInt().coerceIn(0, image.width - 1)
    val y = ((p.y - (box.height - image.height * scale) / 2) / scale).toInt().coerceIn(0, image.height - 1)
    return x to y
}

private fun StoredState.shareOptions() = ShareOptions(shareMaxSize, shareMaxFps, shareBitRate).normalized()

private suspend fun saveFrame(graph: AppGraph, image: ImageBitmap) {
    try {
        val location = withContext(Dispatchers.IO) {
            saveSnapshot(image.encodePng(), SettingsBackedCaptureDestination(graph.adapters.settings), TimestampFileNamePolicy())
        }
        graph.toast(
            "Screenshot saved to ${location.displayPath}",
            FeedbackSeverity.Success,
            FeedbackAction("Show") { DesktopRevealInFileManager().reveal(location) },
        )
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        graph.toast("Could not save the screenshot. ${e.message.orEmpty()}", FeedbackSeverity.Error)
    }
}

/** Times key frame decoding on the running stream and the adb link; null when a tool or the stream is missing. */
private suspend fun measureStream(graph: AppGraph, mirror: ScrcpyMirror, serial: String): StreamMeasure? {
    val setup = resolveSetup(graph) ?: return null
    val cost = mirror.keyframeCost() ?: return null
    return StreamMeasure(cost, withContext(Dispatchers.IO) { LinkSpeed.measureMbps(setup.adb, serial) })
}

private const val AUTO_WARM_UP_MS = 2_000L
private const val AUTO_RETRY_MS = 5_000L
private const val AUTO_SECOND_LOOK_MS = 15_000L

private suspend fun resolveSetup(graph: AppGraph): ScrcpySetup? {
    val scrcpy = (graph.toolLocator.locate(ToolId.Scrcpy) as? DiscoveryOutcome.Found)?.tool ?: return null
    val adb = (graph.toolLocator.locate(ToolId.Adb) as? DiscoveryOutcome.Found)?.tool?.path?.value ?: return null
    val server = File(scrcpy.path.value).parentFile?.resolve("scrcpy-server")?.takeIf { it.isFile } ?: return null
    val version = Regex("""\d+\.\d+(\.\d+)?""").find(scrcpy.version.raw)?.value ?: return null
    return ScrcpySetup(adb, server, version)
}
