package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.application.content.ContentViewerViewModel
import io.github.raghavsatyadev.androidworld.core.domain.device.toCommandContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The Personal data area: read-only contacts, SMS, call log and calendar tables. */
class ContentModule(core: AppGraph) {
    private val scope = core.childScope()
    private val selected = core.selectedDevice.state
    private val commandContext = selected.map { it.toCommandContext() }
        .stateIn(scope, SharingStarted.Eagerly, selected.value.toCommandContext())

    val viewer = ContentViewerViewModel(core.childScope(), core.dispatchers, ShellCommandRunner(core.adbTransport), commandContext)
}
