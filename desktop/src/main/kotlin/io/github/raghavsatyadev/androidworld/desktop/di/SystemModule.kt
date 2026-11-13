package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.DeviceSettingsViewModel
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.PropertiesViewModel
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.ServicesViewModel
import io.github.raghavsatyadev.androidworld.core.application.sysconfig.SysConfigConfirm
import io.github.raghavsatyadev.androidworld.core.domain.device.toCommandContext
import io.github.raghavsatyadev.androidworld.desktop.confirm.ConfirmRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The System area: system properties, the system/secure/global settings tables and running services.
 * Every write is confirmed through the app's dialog with the exact command line.
 */
class SystemModule(core: AppGraph) {
    private val scope = core.childScope()
    private val selected = core.selectedDevice.state
    private val commandContext = selected.map { it.toCommandContext() }
        .stateIn(scope, SharingStarted.Eagerly, selected.value.toCommandContext())
    private val runner = ShellCommandRunner(core.adbTransport)

    private val confirm: SysConfigConfirm = { write ->
        core.confirmations.ask(ConfirmRequest(title = write.title, message = write.commandLine, confirmLabel = "Apply", note = write.note))
    }

    val properties = PropertiesViewModel(core.childScope(), core.dispatchers, runner, commandContext, confirm)
    val settings = DeviceSettingsViewModel(core.childScope(), core.dispatchers, runner, commandContext, confirm)
    val services = ServicesViewModel(core.childScope(), core.dispatchers, runner, commandContext)
}
