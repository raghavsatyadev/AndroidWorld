package io.github.raghavsatyadev.androidworld.desktop.ui.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.raghavsatyadev.androidworld.desktop.di.AppsModule
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.fadeThrough
import io.github.raghavsatyadev.androidworld.desktop.ui.motion.sharedAxisX
import io.github.raghavsatyadev.androidworld.core.application.apps.AppLifecycleIntent
import io.github.raghavsatyadev.androidworld.core.application.apps.AppsIntent
import io.github.raghavsatyadev.androidworld.core.application.apps.ClearDataIntent
import io.github.raghavsatyadev.androidworld.core.application.apps.UninstallIntent

private val TWO_PANE_MIN = 840.dp
private val LIST_PANE_WIDTH = 380.dp

/**
 * List–detail for installed apps. From [TWO_PANE_MIN] up both panes show, and switching apps fades
 * through the detail pane; narrower, the detail replaces the list along the shared X axis.
 */
@Composable
fun AppsScreen(apps: AppsModule) {
    val list by apps.list.state.collectAsState()
    val details by apps.details.state.collectAsState()
    val lifecycle by apps.lifecycle.state.collectAsState()
    val clear by apps.clearData.state.collectAsState()
    val uninstall by apps.uninstall.state.collectAsState()
    val actions = remember(lifecycle.actionsEnabled, clear.actionEnabled, uninstall.actionEnabled) {
        AppActions(
            enabled = lifecycle.actionsEnabled,
            clearEnabled = clear.actionEnabled,
            uninstallEnabled = uninstall.actionEnabled,
            launch = { apps.lifecycle.handle(AppLifecycleIntent.Launch) },
            restart = { apps.lifecycle.handle(AppLifecycleIntent.Restart) },
            forceStop = { apps.lifecycle.handle(AppLifecycleIntent.ForceStop) },
            clearData = { apps.clearData.handle(ClearDataIntent.ClearData) },
            uninstall = { apps.uninstall.handle(UninstallIntent.Uninstall) },
        )
    }
    val onList: (AppsIntent) -> Unit = apps.list::handle
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= TWO_PANE_MIN) {
            Row(Modifier.fillMaxSize()) {
                AppListPane(list, onList, Modifier.width(LIST_PANE_WIDTH).fillMaxHeight().padding(top = 8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 16.dp, bottom = 16.dp),
                ) {
                    AnimatedContent(details, transitionSpec = fadeThrough(), contentKey = { it.packageName }) { shown ->
                        AppDetailsPane(shown, actions, apps.details::handle, onBack = null)
                    }
                }
            }
        } else {
            AnimatedContent(
                details.isOpen,
                transitionSpec = sharedAxisX { _, toDetail -> toDetail },
            ) { showDetail ->
                if (showDetail) AppDetailsPane(details, actions, apps.details::handle, onBack = { onList(AppsIntent.SelectPackage(null)) })
                else AppListPane(list, onList, Modifier.fillMaxSize().padding(top = 8.dp))
            }
        }
    }
}
