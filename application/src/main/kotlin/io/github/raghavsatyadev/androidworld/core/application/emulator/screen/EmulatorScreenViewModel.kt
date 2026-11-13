package io.github.raghavsatyadev.androidworld.core.application.emulator.screen

import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorConnection
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorConnector
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorEndpointLocator
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorInput
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorKeyFallback
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.FitRect
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.FoldPosture
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.ScreenGeometry
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.ScreenRotation
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.consolePortOf
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manages an active emulator screen streaming session over gRPC, processing touch, key,
 * rotation, and foldable posture intents.
 */
class EmulatorScreenViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val locator: EmulatorEndpointLocator,
    private val connector: EmulatorConnector,
    private val keyFallback: EmulatorKeyFallback? = null,
) {
    private val _state = MutableStateFlow(EmulatorScreenState())
    val state: StateFlow<EmulatorScreenState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var inputJob: Job? = null
    private var inputChannel: Channel<EmulatorInput>? = null
    private var connection: EmulatorConnection? = null

    private var touchDown = false
    private var lastSentPoint: Pair<Int, Int>? = null

    fun handle(intent: EmulatorScreenIntent) {
        when (intent) {
            is EmulatorScreenIntent.Start -> start(intent.serial, intent.maxWidth, intent.maxHeight)
            EmulatorScreenIntent.Stop -> stop()
            is EmulatorScreenIntent.Pointer -> pointer(intent.viewX, intent.viewY, intent.shown, intent.pressed)
            is EmulatorScreenIntent.Key -> enqueueInput(EmulatorInput.Key(intent.key))
            is EmulatorScreenIntent.Text -> enqueueInput(EmulatorInput.Text(intent.text))
            EmulatorScreenIntent.RotateLeft -> rotate(_state.value.rotation.rotatedLeft())
            EmulatorScreenIntent.RotateRight -> rotate(_state.value.rotation.rotatedRight())
            is EmulatorScreenIntent.SetPosture -> setPosture(intent.posture)
        }
    }

    private fun start(serial: String, maxWidth: Int, maxHeight: Int) {
        if (_state.value.serial == serial && _state.value.status == EmulatorScreenStatus.Streaming) {
            return
        }
        stopStreaming()

        _state.update {
            it.copy(
                serial = serial,
                status = EmulatorScreenStatus.Connecting,
                frame = null,
                hardware = null,
                posture = null,
                framesPerSecond = 0,
            )
        }

        if (consolePortOf(serial) == null) {
            _state.update { it.copy(status = EmulatorScreenStatus.Failed("Only emulators can be shown here.")) }
            return
        }

        val channel = Channel<EmulatorInput>(Channel.UNLIMITED)
        inputChannel = channel

        streamJob = scope.launch(dispatchers.io) {
            var conn: EmulatorConnection? = null
            var localInputJob: Job? = null
            try {
                val endpoint = locator.find(serial)
                if (endpoint == null) {
                    _state.update {
                        it.copy(
                            status = EmulatorScreenStatus.Failed(
                                "This emulator has no gRPC endpoint. Start it with -grpc <port> " +
                                    "(for example -grpc 8554 -grpc-use-token), or from Android Studio.",
                            ),
                        )
                    }
                    return@launch
                }

                conn = connector.connect(endpoint)
                connection = conn

                val hw = try {
                    conn.hardware()
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    _state.update { it.copy(status = EmulatorScreenStatus.Failed(e.message ?: e.toString())) }
                    return@launch
                }
                _state.update { it.copy(hardware = hw) }

                val job = scope.launch(dispatchers.io) {
                    for (input in channel) {
                        try {
                            val handled = when (input) {
                                is EmulatorInput.Key,
                                is EmulatorInput.Text -> keyFallback?.send(serial, input) == true
                                else -> false
                            }
                            if (!handled) {
                                conn.send(input)
                            }
                        } catch (e: Throwable) {
                            if (e is CancellationException) throw e
                        }
                    }
                }
                localInputJob = job
                inputJob = job

                val timeSource = TimeSource.Monotonic
                val frameTimes = ArrayDeque<TimeSource.Monotonic.ValueTimeMark>()

                conn.frames(maxWidth, maxHeight).collect { frame ->
                    val now = timeSource.markNow()
                    frameTimes.addLast(now)
                    while (frameTimes.isNotEmpty() && (now - frameTimes.first()) > 1.seconds) {
                        frameTimes.removeFirst()
                    }
                    val fps = frameTimes.size
                    _state.update { current ->
                        current.copy(
                            status = EmulatorScreenStatus.Streaming,
                            frame = frame,
                            rotation = frame.rotation,
                            framesPerSecond = fps,
                        )
                    }
                }

                _state.update { it.copy(status = EmulatorScreenStatus.Failed("The emulator stopped streaming.")) }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                _state.update { it.copy(status = EmulatorScreenStatus.Failed(e.message ?: e.toString())) }
            } finally {
                channel.close()
                localInputJob?.cancel()
                if (inputJob === localInputJob) inputJob = null
                if (inputChannel === channel) inputChannel = null
                try {
                    conn?.close()
                } catch (_: Throwable) {}
                if (connection === conn) {
                    connection = null
                }
            }
        }
    }

    private fun stop() {
        stopStreaming()
        _state.value = EmulatorScreenState()
    }

    private fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        inputJob?.cancel()
        inputJob = null
        inputChannel?.close()
        inputChannel = null
        try {
            connection?.close()
        } catch (_: Throwable) {}
        connection = null
        touchDown = false
        lastSentPoint = null
    }

    private fun pointer(viewX: Float, viewY: Float, shown: FitRect, pressed: Boolean) {
        val hw = _state.value.hardware ?: return
        if (_state.value.frame == null) return

        val point = ScreenGeometry.toDevice(
            viewX = viewX,
            viewY = viewY,
            shown = shown,
            naturalW = hw.width,
            naturalH = hw.height,
            rotation = _state.value.rotation,
        )

        if (pressed) {
            if (point != null) {
                touchDown = true
                lastSentPoint = point
                enqueueInput(EmulatorInput.Mouse(point.first, point.second, buttons = 1))
            }
        } else {
            if (point != null) {
                touchDown = false
                lastSentPoint = point
                enqueueInput(EmulatorInput.Mouse(point.first, point.second, buttons = 0))
            } else if (touchDown && lastSentPoint != null) {
                touchDown = false
                val last = lastSentPoint ?: return
                enqueueInput(EmulatorInput.Mouse(last.first, last.second, buttons = 0))
            }
        }
    }

    private fun enqueueInput(input: EmulatorInput) {
        inputChannel?.trySend(input)
    }

    private fun rotate(target: ScreenRotation) {
        _state.update { it.copy(rotation = target) }
        val conn = connection ?: return
        scope.launch(dispatchers.io) {
            try {
                conn.rotate(target)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
            }
        }
    }

    private fun setPosture(posture: FoldPosture) {
        if (_state.value.hardware?.foldable != true) return
        _state.update { it.copy(posture = posture) }
        val conn = connection ?: return
        scope.launch(dispatchers.io) {
            try {
                conn.setPosture(posture)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
            }
        }
    }
}
