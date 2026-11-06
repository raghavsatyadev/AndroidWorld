package io.github.raghavsatyadev.androidworld.core.application.commands

import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.process.InteractiveProcess
import io.github.raghavsatyadev.androidworld.core.domain.process.InteractiveProcessLauncher
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessCommand
import io.github.raghavsatyadev.androidworld.core.domain.terminal.ShellSessionProtocol
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lines kept in the scrollback; older ones are dropped. */
const val TERMINAL_SCROLLBACK = 5_000
private const val HISTORY_SIZE = 200

/**
 * An `adb shell` on the selected device, kept open between commands so `cd`, `export` and `su`
 * carry over. stdin is a pipe, not a tty, so full-screen programs (`top`, `vi`) don't work and
 * Ctrl+C can't reach the device: [TerminalIntent.Restart] ends the shell instead.
 */
class TerminalViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val launcher: InteractiveProcessLauncher,
    private val adbPath: suspend () -> String?,
    commandContext: StateFlow<DeviceCommandContext>,
    private val newToken: () -> String = { UUID.randomUUID().toString().replace("-", "").take(12) },
) {
    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()

    private var session: Job? = null
    @Volatile private var process: InteractiveProcess? = null
    private var writes: Channel<String>? = null
    private var protocol: ShellSessionProtocol? = null
    private var historyIndex = -1

    init {
        commandContext.map { (it as? DeviceCommandContext.Eligible)?.serial?.value }
            .distinctUntilChanged()
            .onEach { serial ->
                stopSession()
                _state.update {
                    TerminalState(
                        serial = serial,
                        status = if (serial == null) TerminalStatus.NoDevice else TerminalStatus.Idle,
                        history = it.history,
                    )
                }
            }
            .launchIn(scope)
    }

    fun handle(intent: TerminalIntent) {
        when (intent) {
            TerminalIntent.Start -> if (_state.value.status == TerminalStatus.Idle) start()
            is TerminalIntent.EditInput -> {
                historyIndex = -1
                _state.update { it.copy(input = intent.text) }
            }
            TerminalIntent.Submit -> submit()
            TerminalIntent.HistoryPrevious -> browseHistory(+1)
            TerminalIntent.HistoryNext -> browseHistory(-1)
            TerminalIntent.Restart -> restart()
            TerminalIntent.Clear -> _state.update { it.copy(lines = emptyList()) }
        }
    }

    private fun submit() {
        val s = _state.value
        val channel = writes
        if (!s.canSubmit || channel == null) return
        val line = s.input
        historyIndex = -1
        if (s.status == TerminalStatus.Running) {
            append(TerminalLine.Command(prompt = "", text = line))
            _state.update { it.copy(input = "") }
            channel.trySend(line.replace("\r", "") + "\n")
            return
        }
        append(TerminalLine.Command(s.prompt, line))
        val history = when {
            line.isBlank() || s.history.firstOrNull() == line -> s.history
            else -> (listOf(line) + s.history).take(HISTORY_SIZE)
        }
        _state.update {
            it.copy(input = "", history = history, status = if (line.isBlank()) it.status else TerminalStatus.Running)
        }
        if (line.isNotBlank()) channel.trySend(protocol!!.command(line))
    }

    private fun browseHistory(step: Int) {
        val history = _state.value.history
        if (history.isEmpty()) return
        historyIndex = (historyIndex + step).coerceIn(-1, history.lastIndex)
        _state.update { it.copy(input = if (historyIndex < 0) "" else history[historyIndex]) }
    }

    private fun restart() {
        val serial = _state.value.serial ?: return
        scope.launch {
            stopSession()
            append(TerminalLine.Notice("Restarted the shell on $serial."))
            start()
        }
    }

    private suspend fun stopSession() {
        // Close first: the output reader blocks in a read that cancellation alone can't interrupt.
        // Clearing [process] first tells the session the end was asked for, so it reports nothing.
        val running = process
        process = null
        running?.close()
        session?.cancelAndJoin()
        session = null
        writes = null
    }

    private fun start() {
        val serial = _state.value.serial ?: return
        val proto = ShellSessionProtocol(newToken()).also { protocol = it }
        val channel = Channel<String>(Channel.UNLIMITED).also { writes = it }
        _state.update { it.copy(status = TerminalStatus.Starting, directory = "/", lastExitCode = null) }
        session = scope.launch(dispatchers.io) {
            var started: InteractiveProcess? = null
            try {
                val adb = adbPath()
                if (adb == null) {
                    end("adb was not found. Set its path in Settings.", isError = true)
                    return@launch
                }
                started = launcher.launch(ProcessCommand(adb, listOf("-s", serial, "shell"))).also { process = it }
                channel.trySend(proto.opening())
                run(started, proto, channel)
                if (process === started) end("The shell ended. Press Restart to open a new one.", isError = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (started == null || process === started) end("The shell failed: ${e.message ?: e}", isError = true)
            } finally {
                channel.close()
                started?.close()
                if (process === started) process = null
            }
        }
    }

    private suspend fun run(process: InteractiveProcess, proto: ShellSessionProtocol, channel: Channel<String>) =
        coroutineScope {
            val writer = launch { for (text in channel) process.write(text) }
            process.output.collect { raw ->
                val parsed = proto.parse(raw)
                parsed.text?.let { append(TerminalLine.Output(it)) }
                parsed.end?.let { end ->
                    _state.update {
                        it.copy(
                            status = TerminalStatus.Ready,
                            directory = end.directory,
                            lastExitCode = if (it.status == TerminalStatus.Starting) null else end.exitCode,
                        )
                    }
                }
            }
            writer.cancel()
        }

    private fun end(message: String, isError: Boolean) {
        append(TerminalLine.Notice(message, isError))
        _state.update { it.copy(status = TerminalStatus.Ended) }
    }

    private fun append(line: TerminalLine) = _state.update {
        val lines = it.lines + line
        it.copy(lines = if (lines.size > TERMINAL_SCROLLBACK) lines.takeLast(TERMINAL_SCROLLBACK) else lines)
    }
}
