package io.github.raghavsatyadev.androidworld.core.application.remote

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTextResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState
import io.github.raghavsatyadev.androidworld.core.domain.device.toCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.input.InputCommands
import io.github.raghavsatyadev.androidworld.core.domain.input.SystemKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteControlViewState(
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

sealed interface RemoteControlIntent {
    data class Press(val key: SystemKey) : RemoteControlIntent

    data class Type(val text: String) : RemoteControlIntent

    data object DismissError : RemoteControlIntent
}

class RemoteControlViewModel(
    scope: CoroutineScope,
    dispatchers: DispatcherProvider,
    private val transport: AdbTransport,
    selectedDeviceState: StateFlow<SelectedDeviceState>,
) {
    private val _state = MutableStateFlow(RemoteControlViewState())
    val state: StateFlow<RemoteControlViewState> = _state.asStateFlow()

    private var serial: DeviceSerial? = null
    private val requests = Channel<RemoteControlIntent>(Channel.UNLIMITED)

    init {
        scope.launch(dispatchers.io) {
            selectedDeviceState
                .map { (it.toCommandContext() as? DeviceCommandContext.Eligible)?.serial }
                .distinctUntilChanged()
                .collectLatest { eligible ->
                    serial = eligible
                    _state.update { it.copy(enabled = eligible != null) }
                }
        }
        scope.launch(dispatchers.io) {
            for (intent in requests) {
                process(intent)
            }
        }
    }

    fun handle(intent: RemoteControlIntent) {
        requests.trySend(intent)
    }

    private suspend fun process(intent: RemoteControlIntent) {
        when (intent) {
            RemoteControlIntent.DismissError -> {
                _state.update { it.copy(error = null) }
            }
            is RemoteControlIntent.Press -> {
                val target = serial ?: return
                val result = transport.executeText(InputCommands.key(target, intent.key))
                if (!isSuccess(result)) {
                    _state.update { it.copy(error = formatError(result)) }
                }
            }
            is RemoteControlIntent.Type -> {
                val target = serial ?: return
                if (intent.text.isEmpty()) return
                val textRequests =
                    try {
                        InputCommands.typeText(target, intent.text)
                    } catch (e: IllegalArgumentException) {
                        _state.update { it.copy(error = e.message) }
                        return
                    }
                _state.update { it.copy(busy = true) }
                try {
                    for (request in textRequests) {
                        val result = transport.executeText(request)
                        if (!isSuccess(result)) {
                            _state.update { it.copy(error = formatError(result)) }
                            break
                        }
                    }
                } finally {
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    private fun isSuccess(result: AdbTextResult): Boolean {
        val outcome = result.outcome
        return outcome is AdbOutcome.Completed && (outcome.exitCode == null || outcome.exitCode == 0)
    }

    private fun formatError(result: AdbTextResult): String {
        val detail = if (result.stderr.isNotBlank()) result.stderr else result.outcome.toString()
        return "Could not send to the device: $detail"
    }
}
