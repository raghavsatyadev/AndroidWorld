package io.github.raghavsatyadev.androidworld.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.AppShell
import io.github.raghavsatyadev.androidworld.desktop.ui.theme.AndroidWorldTheme
import kotlinx.coroutines.flow.MutableSharedFlow

@Suppress("DEPRECATION") // classpath painterResource is the simplest way to load the window icon SVG
fun main() {
    // A second launch brings the open window forward instead of starting another copy.
    val showRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    if (!SingleInstance.acquire { showRequests.tryEmit(Unit) }) return

    application {
        val graph = remember { AppGraph() }
        var dark by remember { mutableStateOf(graph.store.state.darkTheme) }
        Window(
            onCloseRequest = { graph.dispose(); exitApplication() },
            title = "AndroidWorld",
            icon = painterResource("androidworld-logo.svg"),
            state = rememberWindowState(size = DpSize(1280.dp, 840.dp)),
        ) {
            LaunchedEffect(Unit) {
                showRequests.collect {
                    window.isMinimized = false
                    // Windows won't let a background app take focus; a brief always-on-top raises it.
                    window.isAlwaysOnTop = true
                    window.toFront()
                    window.isAlwaysOnTop = false
                    window.requestFocus()
                }
            }
            AndroidWorldTheme(dark = dark) {
                AppShell(graph, dark) { value ->
                    dark = value
                    graph.store.update { it.copy(darkTheme = value) }
                }
            }
        }
    }
}
