package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.desktop.confirm.ConfirmRequest
import io.github.raghavsatyadev.androidworld.desktop.tools.AndroidCliPath
import io.github.raghavsatyadev.androidworld.core.application.device.SelectedDeviceIntent
import io.github.raghavsatyadev.androidworld.core.application.emulator.AvdConfigSource
import io.github.raghavsatyadev.androidworld.core.application.emulator.EmulatorCli
import io.github.raghavsatyadev.androidworld.core.application.emulator.EmulatorsViewModel
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.emulator.Avd
import io.github.raghavsatyadev.androidworld.core.domain.emulator.AvdConfig
import io.github.raghavsatyadev.androidworld.core.domain.emulator.AvdConfigParser
import io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * The Emulators area: AVD lifecycle through the `android` CLI. When the set of running emulators
 * changes, the device list is refreshed at once instead of waiting for its next poll.
 */
class EmulatorsModule(private val core: AppGraph) {
    private val scope = core.childScope()

    val emulators = EmulatorsViewModel(
        scope = core.childScope(),
        dispatchers = core.dispatchers,
        cli = EmulatorCli(core.processExecutor, executable = { AndroidCliPath.resolved }, configs = AvdFiles),
        confirmRemoval = { avd ->
            core.confirmations.ask(
                ConfirmRequest(
                    "Delete ${avd.name}?",
                    "The virtual device and everything on it, including its snapshots, will be deleted.",
                    "Delete",
                ),
            )
        },
    )

    init {
        emulators.state.map { state -> state.running.mapNotNull { it.serial }.toSet() }
            .distinctUntilChanged()
            .drop(1)
            .onEach { core.deviceRepository.refresh() }
            .launchIn(scope)
    }

    /** Makes a running emulator the selected device. */
    fun use(avd: Avd) {
        val serial = avd.serial ?: return
        core.selectedDevice.handle(SelectedDeviceIntent.Select(DeviceSerial.of(serial)))
    }

    /** The serial of the selected device while it is online, to mark which emulator is in use. */
    val selectedSerial: StateFlow<String?> = core.selectedDevice.state
        .map { (it as? SelectedDeviceState.Online)?.device?.serial?.value }
        .stateIn(scope, SharingStarted.Eagerly, null)
}

/**
 * Finds AVD folders the way the SDK tools do: `ANDROID_AVD_HOME`, else `ANDROID_USER_HOME/avd`, else
 * `~/.android/avd`. Each `<id>.ini` names the folder holding `config.ini`.
 */
private object AvdFiles : AvdConfigSource {
    private val home: File by lazy {
        System.getenv("ANDROID_AVD_HOME")?.let(::File)
            ?: System.getenv("ANDROID_USER_HOME")?.let { File(it, "avd") }
            ?: File(System.getProperty("user.home"), ".android/avd")
    }

    override suspend fun read(avdId: String): AvdConfig? = runCatching {
        val pointer = File(home, "$avdId.ini").takeIf { it.isFile }?.readLines()
            ?.firstOrNull { it.startsWith("path=") }?.substringAfter('=')?.trim()?.let(::File)
        val folder = pointer?.takeIf { it.isDirectory } ?: File(home, "$avdId.avd")
        File(folder, "config.ini").takeIf { it.isFile }?.readText()?.let(AvdConfigParser::parse)
    }.getOrNull()
}
