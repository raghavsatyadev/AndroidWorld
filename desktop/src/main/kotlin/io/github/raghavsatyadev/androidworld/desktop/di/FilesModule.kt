package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.core.adapters.adb.files.AdbPrivateFileReader
import io.github.raghavsatyadev.androidworld.core.application.files.DeviceFileService
import io.github.raghavsatyadev.androidworld.core.application.files.FileBrowserViewModel
import io.github.raghavsatyadev.androidworld.core.domain.device.toCommandContext
import io.github.raghavsatyadev.androidworld.desktop.confirm.ConfirmRequest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.nio.file.Path

/**
 * The Files area: the device file browser. Pulls default to the screenshot folder from settings
 * (the desktop when none is set, like captures); deletes are confirmed through the app's dialog.
 */
class FilesModule(core: AppGraph) {
    private val scope = core.childScope()
    private val selected = core.selectedDevice.state
    private val commandContext = selected.map { it.toCommandContext() }
        .stateIn(scope, SharingStarted.Eagerly, selected.value.toCommandContext())

    val browser = FileBrowserViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        files = DeviceFileService(core.adbTransport, AdbPrivateFileReader(core.adbTransport)),
        commandContext = commandContext,
        defaultPullFolder = {
            core.adapters.settings.readSettings().captureDirectory?.trim()?.ifEmpty { null }
                ?: Path.of(System.getProperty("user.home"), "Desktop").toString()
        },
        confirm = { title, detail ->
            core.confirmations.ask(ConfirmRequest(title = title, message = detail, confirmLabel = "Delete"))
        },
    )
}
