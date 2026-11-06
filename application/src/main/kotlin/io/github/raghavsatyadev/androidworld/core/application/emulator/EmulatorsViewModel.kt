package io.github.raghavsatyadev.androidworld.core.application.emulator

import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.emulator.Avd
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What is happening to one AVD right now. */
enum class AvdBusy(val label: String) { Starting("Starting…"), Stopping("Stopping…"), Removing("Deleting…") }

/** Asks before an AVD and its data are deleted. */
fun interface AvdRemovalConfirmation {
    suspend fun confirm(avd: Avd): Boolean
}

data class EmulatorsState(
    val cliMissing: Boolean = false,
    val loading: Boolean = true,
    val avds: List<Avd> = emptyList(),
    val busy: Map<String, AvdBusy> = emptyMap(),
    val headless: Boolean = true,
    val profiles: List<String> = emptyList(),
    val creating: String? = null,
    val message: EmulatorMessage? = null,
) {
    val running: List<Avd> get() = avds.filter { it.running }
}

/** The last outcome worth telling the user about. */
data class EmulatorMessage(val text: String, val isError: Boolean)

sealed interface EmulatorsIntent {
    /** The screen became visible (poll the list) or hidden (stop polling). */
    data class SetVisible(val visible: Boolean) : EmulatorsIntent

    data object Refresh : EmulatorsIntent

    data class SetHeadless(val headless: Boolean) : EmulatorsIntent

    data class Start(val avd: Avd, val cold: Boolean = false) : EmulatorsIntent

    data class Stop(val avd: Avd) : EmulatorsIntent

    data class Remove(val avd: Avd) : EmulatorsIntent

    data class Create(val profile: String) : EmulatorsIntent

    data object DismissMessage : EmulatorsIntent
}

/**
 * The Emulators screen: lists AVDs and starts, stops, creates and deletes them through [EmulatorCli].
 * Each AVD runs at most one action at a time; different AVDs run in parallel. While visible the list
 * is re-read every [pollInterval], so emulators started elsewhere show up too.
 */
class EmulatorsViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val cli: EmulatorCli,
    private val confirmRemoval: AvdRemovalConfirmation,
    private val pollInterval: Duration = 5.seconds,
) {
    private val _state = MutableStateFlow(EmulatorsState())
    val state: StateFlow<EmulatorsState> = _state.asStateFlow()

    private val refreshing = Mutex()
    private var poller: Job? = null
    private var profilesLoaded = false

    fun handle(intent: EmulatorsIntent) {
        when (intent) {
            is EmulatorsIntent.SetVisible -> setVisible(intent.visible)
            EmulatorsIntent.Refresh -> scope.launch(dispatchers.io) { refresh() }
            is EmulatorsIntent.SetHeadless -> _state.update { it.copy(headless = intent.headless) }
            is EmulatorsIntent.Start -> act(intent.avd, AvdBusy.Starting) {
                cli.start(intent.avd.id, headless = _state.value.headless, cold = intent.cold)
                    .report(ok = "${intent.avd.name} is running.")
            }
            is EmulatorsIntent.Stop -> act(intent.avd, AvdBusy.Stopping) {
                cli.stop(intent.avd.serial ?: intent.avd.id).report(ok = "${intent.avd.name} stopped.")
            }
            is EmulatorsIntent.Remove -> remove(intent.avd)
            is EmulatorsIntent.Create -> create(intent.profile)
            EmulatorsIntent.DismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun setVisible(visible: Boolean) {
        poller?.cancel()
        poller = null
        if (!visible) return
        poller = scope.launch(dispatchers.io) {
            if (!profilesLoaded && cli.available) {
                profilesLoaded = true
                val profiles = cli.profiles()
                _state.update { it.copy(profiles = profiles) }
            }
            while (isActive) {
                refresh()
                delay(pollInterval)
            }
        }
    }

    private suspend fun refresh() = refreshing.withLock {
        if (!cli.available) {
            _state.update { it.copy(cliMissing = true, loading = false) }
            return@withLock
        }
        val avds = cli.list()
        _state.update { current ->
            if (avds == null) current.copy(cliMissing = false, loading = false, message = current.message ?: listError)
            else current.copy(cliMissing = false, loading = false, avds = avds)
        }
    }

    private fun remove(avd: Avd) {
        if (avd.id in _state.value.busy) return
        scope.launch(dispatchers.io) {
            if (!confirmRemoval.confirm(avd)) return@launch
            run(avd, AvdBusy.Removing) { cli.remove(avd.id).report(ok = "${avd.name} deleted.") }
        }
    }

    private fun create(profile: String) {
        if (_state.value.creating != null) return
        _state.update { it.copy(creating = profile, message = null) }
        scope.launch(dispatchers.io) {
            try {
                val message = cli.create(profile).report(ok = "Created a ${profile.replace('_', ' ')} emulator.")
                _state.update { it.copy(message = message) }
                refresh()
            } finally {
                _state.update { it.copy(creating = null) }
            }
        }
    }

    private fun act(avd: Avd, busy: AvdBusy, block: suspend () -> EmulatorMessage) {
        if (avd.id in _state.value.busy) return
        scope.launch(dispatchers.io) { run(avd, busy, block) }
    }

    private suspend fun run(avd: Avd, busy: AvdBusy, block: suspend () -> EmulatorMessage) {
        var claimed = false
        _state.update {
            claimed = avd.id !in it.busy
            if (claimed) it.copy(busy = it.busy + (avd.id to busy), message = null) else it
        }
        if (!claimed) return
        try {
            val message = block()
            _state.update { it.copy(message = message) }
            refresh()
        } finally {
            _state.update { it.copy(busy = it.busy - avd.id) }
        }
    }

    private fun CliResult.report(ok: String): EmulatorMessage = when (this) {
        CliResult.Ok -> EmulatorMessage(ok, isError = false)
        is CliResult.Failed -> EmulatorMessage(message, isError = true)
    }

    private companion object {
        val listError = EmulatorMessage("Couldn't read the emulator list from the android CLI.", isError = true)
    }
}
