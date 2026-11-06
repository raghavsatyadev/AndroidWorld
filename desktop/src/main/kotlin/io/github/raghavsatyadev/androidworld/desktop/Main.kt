package io.github.raghavsatyadev.androidworld.desktop

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

@Suppress("DEPRECATION") // classpath painterResource is the simplest way to load the window icon SVG
fun main() = application {
    val graph = remember { AppGraph() }
    var dark by remember { mutableStateOf(graph.store.state.darkTheme) }
    Window(
        onCloseRequest = { graph.dispose(); exitApplication() },
        title = "AndroidWorld",
        icon = painterResource("androidworld-logo.svg"),
        state = rememberWindowState(size = DpSize(1280.dp, 840.dp)),
    ) {
        AndroidWorldTheme(dark = dark) {
            AppShell(graph, dark) { value ->
                dark = value
                graph.store.update { it.copy(darkTheme = value) }
            }
        }
    }
}
