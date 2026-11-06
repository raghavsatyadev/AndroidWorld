package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.desktop.confirm.ConfirmRequest
import io.github.raghavsatyadev.androidworld.core.application.commands.CommandBrowserViewModel
import io.github.raghavsatyadev.androidworld.core.application.commands.DumpsysViewModel
import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.device.toCommandContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Commands area: the shell command browser and the dumpsys viewer. Destructive commands are
 * confirmed through the app's dialog, which names the device and shows the exact command line.
 */
class CommandsModule(core: AppGraph) {
    private val scope = core.childScope()
    private val selected = core.selectedDevice.state
    private val commandContext = selected.map { it.toCommandContext() }
        .stateIn(scope, SharingStarted.Eagerly, selected.value.toCommandContext())
    private val runner = ShellCommandRunner(core.adbTransport)

    val browser = CommandBrowserViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        runner = runner,
        commandContext = commandContext,
        confirm = { entry, line ->
            val device = (commandContext.value as? DeviceCommandContext.Eligible)?.serial?.value ?: "the device"
            core.confirmations.ask(
                ConfirmRequest(
                    title = "Run \"${entry.name}\" on $device?",
                    message = line,
                    confirmLabel = "Run",
                    note = entry.warning,
                ),
            )
        },
    )

    val dumpsys = DumpsysViewModel(core.childScope(), core.dispatchers, runner, commandContext)
}
