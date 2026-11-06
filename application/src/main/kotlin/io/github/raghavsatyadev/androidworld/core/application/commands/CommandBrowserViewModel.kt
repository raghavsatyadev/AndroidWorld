package io.github.raghavsatyadev.androidworld.core.application.commands

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCatalog
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandEntry
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandRisk
import io.github.raghavsatyadev.androidworld.core.domain.commands.currentValue
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The command browser: a searchable catalog of shell commands, each with typed fields, its current
 * value read from the device, and Run. Destructive commands go through [confirm] first, which is
 * handed the exact command line. Everything resets when the selected device changes, so a result is
 * never shown against the wrong device.
 */
class CommandBrowserViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val runner: ShellCommandRunner,
    commandContext: StateFlow<DeviceCommandContext>,
    private val confirm: suspend (entry: CommandEntry, commandLine: String) -> Boolean,
    private val catalog: List<CommandEntry> = CommandCatalog.entries,
) {
    private val _state = MutableStateFlow(CommandBrowserState(entries = catalog))
    val state: StateFlow<CommandBrowserState> = _state.asStateFlow()

    private val runJobs = mutableMapOf<String, Job>()
    private val readJobs = mutableMapOf<String, Job>()

    init {
        commandContext.map { (it as? DeviceCommandContext.Eligible)?.serial }
            .distinctUntilChanged()
            .onEach(::onDevice)
            .launchIn(scope)
    }

    fun handle(intent: CommandBrowserIntent) {
        when (intent) {
            is CommandBrowserIntent.Search -> filter(query = intent.query)
            is CommandBrowserIntent.FilterCategory -> filter(category = intent.category)
            is CommandBrowserIntent.Open -> open(intent.id)
            is CommandBrowserIntent.SetField -> setField(intent)
            is CommandBrowserIntent.ReadCurrent -> entry(intent.id)?.let(::readCurrent)
            is CommandBrowserIntent.Run -> entry(intent.id)?.let(::run)
            is CommandBrowserIntent.Cancel -> runJobs.remove(intent.id)?.cancel()
            is CommandBrowserIntent.ClearOutput -> _state.update { it.copy(runs = it.runs - intent.id) }
        }
    }

    private fun entry(id: String) = catalog.firstOrNull { it.id == id }

    private fun onDevice(serial: DeviceSerial?) {
        (runJobs.values + readJobs.values).forEach(Job::cancel)
        runJobs.clear()
        readJobs.clear()
        _state.update { it.copy(serial = serial?.value, runs = emptyMap(), currents = emptyMap()) }
        _state.value.openId?.let(::entry)?.let(::readCurrent)
    }

    private fun filter(query: String = _state.value.query, category: CommandCategory? = _state.value.category) {
        _state.update { state ->
            state.copy(
                query = query,
                category = category,
                entries = catalog.filter { (category == null || it.category == category) && it.matches(query) },
            )
        }
    }

    private fun open(id: String?) {
        _state.update { it.copy(openId = id) }
        id?.let(::entry)?.let { if (_state.value.currents[it.id] == null) readCurrent(it) }
    }

    private fun setField(intent: CommandBrowserIntent.SetField) {
        val entry = entry(intent.id) ?: return
        _state.update { state ->
            val values = state.valuesOf(entry).toMutableList().also { if (intent.index in it.indices) it[intent.index] = intent.value }
            state.copy(values = state.values + (entry.id to values))
        }
        // A current value that depends on a field (the volume of a stream) is re-read when it changes.
        if (entry.current?.fieldsUsed?.contains(intent.index) == true) readCurrent(entry)
    }

    private fun readCurrent(entry: CommandEntry) {
        val template = entry.current ?: return
        val state = _state.value
        val serial = state.serial?.let(DeviceSerial::of) ?: return
        if (!state.canReadCurrent(entry)) return
        val command = template.build(state.valuesOf(entry))
        readJobs.remove(entry.id)?.cancel()
        _state.update { it.copy(currents = it.currents + (entry.id to CurrentValue.Reading)) }
        readJobs[entry.id] = scope.launch(dispatchers.io) {
            val value = when (val result = runner.run(serial, command, entry.timeout)) {
                is ShellResult.Ran -> if (result.succeeded && result.stdout.isNotBlank()) {
                    CurrentValue.Known(entry.currentValue(result.stdout))
                } else {
                    CurrentValue.Unknown(result.stderr.trim().ifEmpty { result.stdout.trim() }.ifEmpty { "No value" })
                }
                is ShellResult.Failed -> CurrentValue.Unknown(result.reason)
            }
            _state.update { it.copy(currents = it.currents + (entry.id to value)) }
        }
    }

    private fun run(entry: CommandEntry) {
        val state = _state.value
        if (!state.canRun(entry) || runJobs[entry.id]?.isActive == true) return
        val serialText = state.serial ?: return
        val values = state.valuesOf(entry)
        val command = entry.action.build(values)
        val line = command.render()
        runJobs[entry.id] = scope.launch(dispatchers.io) {
            if (entry.risk == CommandRisk.Destructive && !confirm(entry, line)) return@launch
            if (_state.value.serial != serialText) return@launch
            _state.update { it.copy(runs = it.runs + (entry.id to CommandRun(line, running = true))) }
            val result = try {
                runner.run(DeviceSerial.of(serialText), command, entry.timeout)
            } catch (cancelled: CancellationException) {
                // Cancel leaves a finished, failed run behind instead of a spinner, unless the device changed.
                if (_state.value.serial == serialText) finish(entry.id, CommandRun(line, running = false, output = "Cancelled.", failed = true))
                throw cancelled
            }
            finish(entry.id, result.toRun(line))
            readCurrent(entry)
        }
    }

    private fun finish(id: String, run: CommandRun) = _state.update { it.copy(runs = it.runs + (id to run)) }

    private fun ShellResult.toRun(line: String): CommandRun = when (this) {
        is ShellResult.Ran -> CommandRun(
            commandLine = line,
            running = false,
            output = listOf(stdout.trimEnd(), stderr.trimEnd()).filter { it.isNotEmpty() }.joinToString("\n"),
            failed = !succeeded,
            exitCode = exitCode,
        )
        is ShellResult.Failed -> CommandRun(line, running = false, output = reason, failed = true)
    }
}
