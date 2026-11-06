package io.github.raghavsatyadev.androidworld.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.components.ConfirmDialogHost
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.fadeThrough
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Area
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Location
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Navigator
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Section
import io.github.raghavsatyadev.androidworld.core.application.feedback.FeedbackIntent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppShell(graph: AppGraph, darkTheme: Boolean?, onDarkTheme: (Boolean?) -> Unit) {
    val location by graph.navigator.location.collectAsState()
    val mcpStatus by graph.mcp.status.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    Toasts(graph, snackbar)

    Row(Modifier.fillMaxSize()) {
        Rail(graph.navigator, location, mcpRunning = mcpStatus.running)
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AnimatedContent(location.area, transitionSpec = fadeThrough(), modifier = Modifier.weight(1f)) { area ->
                                Text(area.label, style = MaterialTheme.typography.headlineSmall)
                            }
                            if (mcpStatus.running) {
                                Icon(Icons.Outlined.Hub, "MCP on", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp))
                                Text(
                                    "MCP · ${mcpStatus.sessions.size} agent" + if (mcpStatus.sessions.size == 1) "" else "s",
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 16.dp),
                                )
                            }
                            DeviceBar(graph.deviceBar)
                        }
                        AnimatedVisibility(location.area.sections.size > 1, enter = reveal(), exit = conceal()) {
                            SectionTabs(location, graph.navigator::select)
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(location, transitionSpec = fadeThrough(), contentKey = { it.encode() }) { at ->
                    Destinations(graph, at, darkTheme, onDarkTheme)
                }
            }
        }
    }
    ConfirmDialogHost(graph.confirmations)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Rail(navigator: Navigator, location: Location, mcpRunning: Boolean) {
    val state = rememberWideNavigationRailState(WideNavigationRailValue.Collapsed)
    val scope = rememberCoroutineScope()
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    WideNavigationRail(
        state = state,
        header = {
            IconButton(
                onClick = { scope.launch { if (expanded) state.collapse() else state.expand() } },
                modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Icon(if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu, if (expanded) "Collapse menu" else "Expand menu")
            }
        },
    ) {
        Area.entries.forEach { area ->
            WideNavigationRailItem(
                railExpanded = expanded,
                selected = location.area == area,
                onClick = { navigator.open(area) },
                icon = {
                    if (area == Area.Settings && mcpRunning) {
                        BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary) }) { Icon(area.icon, null) }
                    } else Icon(area.icon, null)
                },
                label = { Text(area.label) },
            )
        }
    }
}

/** Connected toggle buttons for an area's sections (M3E button group). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionTabs(location: Location, onSelect: (Section) -> Unit) {
    val sections = location.area.sections
    Row(
        Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        sections.forEachIndexed { index, section ->
            ToggleButton(
                checked = section == location.section,
                onCheckedChange = { onSelect(section) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    sections.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.semantics { role = Role.Tab },
            ) { Text(section.label) }
        }
    }
}

@Composable
private fun Toasts(graph: AppGraph, snackbar: SnackbarHostState) {
    val feedback by graph.feedback.state.collectAsState()
    val head = feedback.toasts.firstOrNull()
    LaunchedEffect(head?.id) {
        head ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message = head.text, actionLabel = head.action?.label, withDismissAction = true)
        if (result == SnackbarResult.ActionPerformed) graph.feedback.handle(FeedbackIntent.InvokeAction(head.id))
        graph.feedback.handle(FeedbackIntent.Dismiss(head.id))
    }
}
