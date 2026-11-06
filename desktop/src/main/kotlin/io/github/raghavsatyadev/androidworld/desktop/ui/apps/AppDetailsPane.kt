package io.github.raghavsatyadev.androidworld.desktop.ui.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.ui.components.AppIconImage
import io.github.raghavsatyadev.androidworld.desktop.ui.components.EmptyState
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.conceal
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.fadeThrough
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.reveal
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsIntent
import io.github.raghavsatyadev.androidworld.core.application.appdetails.AppDetailsState

/** The tabs of the details page; kept in the UI because they are pure presentation. */
enum class DetailTab(val label: String) { Info("Info"), Permissions("Permissions"), DeepLinks("Deep links"), Storage("Storage") }

/** What the details header's buttons do, already bound to the view models by the caller. */
data class AppActions(
    val enabled: Boolean,
    val clearEnabled: Boolean,
    val uninstallEnabled: Boolean,
    val launch: () -> Unit,
    val restart: () -> Unit,
    val forceStop: () -> Unit,
    val clearData: () -> Unit,
    val uninstall: () -> Unit,
)

@Composable
fun AppDetailsPane(
    state: AppDetailsState,
    actions: AppActions,
    onIntent: (AppDetailsIntent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (!state.isOpen) {
        EmptyState("Select an app to see its details", Icons.Outlined.TouchApp, modifier = modifier)
        return
    }
    var tab by rememberSaveable(state.packageName) { mutableStateOf(DetailTab.Info) }
    Column(modifier.fillMaxSize()) {
        Header(state, actions, onIntent, onBack)
        AnimatedVisibility(state.notice != null, enter = reveal(), exit = conceal()) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            ) { Text(state.notice.orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
        }
        Tabs(tab) { tab = it }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(tab, transitionSpec = fadeThrough()) { current ->
                when (current) {
                    DetailTab.Info -> InfoTab(state)
                    DetailTab.Permissions -> PermissionsTab(state, onIntent)
                    DetailTab.DeepLinks -> DeepLinksTab(state, onIntent)
                    DetailTab.Storage -> StorageTab(state, onIntent)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(state: AppDetailsState, actions: AppActions, onIntent: (AppDetailsIntent) -> Unit, onBack: (() -> Unit)?) {
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to apps") }
            AppIconImage(state.icon, size = 64.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(state.label ?: state.packageName.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(state.packageName.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val version = state.details?.versionName?.let { "Version $it" }
                val running = state.runningPid?.let { "Running · pid $it" } ?: "Not running"
                Text(listOfNotNull(version, running).joinToString("  ·  "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Hint("Reload details") { IconButton(onClick = { onIntent(AppDetailsIntent.Refresh) }) { Icon(Icons.Filled.Refresh, "Reload details") } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Action("Launch", Icons.Filled.PlayArrow, actions.enabled, actions.launch)
            Action("Restart", Icons.Filled.RestartAlt, actions.enabled, actions.restart)
            Action("Force stop", Icons.Filled.Stop, actions.enabled, actions.forceStop)
            Danger("Clear data", Icons.Outlined.CleaningServices, actions.clearEnabled, actions.clearData)
            Danger("Uninstall", Icons.Filled.DeleteForever, actions.uninstallEnabled, actions.uninstall)
        }
    }
}

@Composable
private fun Action(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, shapes = ButtonDefaults.shapes()) {
        Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
        Text(label, Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

@Composable
private fun Danger(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
        Text(label, Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

@Composable
private fun Tabs(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    val tabs = DetailTab.entries
    Row(
        Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        tabs.forEachIndexed { index, tab ->
            ToggleButton(
                checked = tab == selected,
                onCheckedChange = { onSelect(tab) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) { Text(tab.label) }
        }
    }
}
