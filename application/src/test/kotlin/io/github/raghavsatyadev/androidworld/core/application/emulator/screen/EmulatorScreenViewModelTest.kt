package io.github.raghavsatyadev.androidworld.core.application.emulator.screen

import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorConnection
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorConnector
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorEndpoint
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorEndpointLocator
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorFrame
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorHardware
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorInput
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorKeyFallback
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.FitRect
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.FoldPosture
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.ScreenRotation
import io.kotest.matchers.shouldBe
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
    override val default = d
    override val io = d
    override val main: CoroutineContext = d
}

private class FakeConnection(
    val hardwareConfig: EmulatorHardware = EmulatorHardware(
        width = 1080,
        height = 2424,
        density = 420,
        postures = listOf(FoldPosture.Closed, FoldPosture.Opened),
        skinName = "pixel_9",
    ),
) : EmulatorConnection {
    val framesFlow = MutableSharedFlow<EmulatorFrame>(extraBufferCapacity = 10)
    val sentInputs = mutableListOf<EmulatorInput>()
    val rotations = mutableListOf<ScreenRotation>()
    val postures = mutableListOf<FoldPosture>()
    var closed = false

    override fun frames(maxWidth: Int, maxHeight: Int): Flow<EmulatorFrame> = framesFlow

    override suspend fun send(input: EmulatorInput) {
        sentInputs += input
    }

    override suspend fun rotate(rotation: ScreenRotation) {
        rotations += rotation
    }

    override suspend fun setPosture(posture: FoldPosture) {
        postures += posture
    }

    override suspend fun hardware(): EmulatorHardware = hardwareConfig

    override fun close() {
        closed = true
    }
}

private class FakeConnector(val connection: FakeConnection = FakeConnection()) : EmulatorConnector {
    var lastEndpoint: EmulatorEndpoint? = null

    override fun connect(endpoint: EmulatorEndpoint): EmulatorConnection {
        lastEndpoint = endpoint
        return connection
    }
}

private class FakeLocator(var endpoint: EmulatorEndpoint? = EmulatorEndpoint(port = 8554, token = "tok")) : EmulatorEndpointLocator {
    override suspend fun find(serial: String): EmulatorEndpoint? = endpoint
}

private class FakeKeyFallback(var result: Boolean = true) : EmulatorKeyFallback {
    val calls = mutableListOf<Pair<String, EmulatorInput>>()

    override suspend fun send(serial: String, input: EmulatorInput): Boolean {
        calls += serial to input
        return result
    }
}

class EmulatorScreenViewModelTest {

    private fun testScope(
        keyFallback: EmulatorKeyFallback? = null,
        block: suspend TestScope.(EmulatorScreenViewModel, FakeConnection, FakeLocator) -> Unit,
    ) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val connection = FakeConnection()
        val connector = FakeConnector(connection)
        val locator = FakeLocator()
        val viewModel = EmulatorScreenViewModel(
            scope = backgroundScope,
            dispatchers = dispatchers,
            locator = locator,
            connector = connector,
            keyFallback = keyFallback,
        )
        block(viewModel, connection, locator)
    }

    private fun testScopeWithFallback(
        fallback: FakeKeyFallback,
        block: suspend TestScope.(EmulatorScreenViewModel, FakeConnection, FakeLocator, FakeKeyFallback) -> Unit,
    ) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dispatchers = TestDispatchers(dispatcher)
        val connection = FakeConnection()
        val connector = FakeConnector(connection)
        val locator = FakeLocator()
        val viewModel = EmulatorScreenViewModel(
            scope = backgroundScope,
            dispatchers = dispatchers,
            locator = locator,
            connector = connector,
            keyFallback = fallback,
        )
        block(viewModel, connection, locator, fallback)
    }

    @Test
    fun `start transitions to Streaming upon receiving frame`() = testScope { viewModel, connection, _ ->
        viewModel.handle(EmulatorScreenIntent.Start("emulator-5554"))
        runCurrent()

        viewModel.state.value.status shouldBe EmulatorScreenStatus.Connecting
        viewModel.state.value.hardware shouldBe connection.hardwareConfig

        val frame = EmulatorFrame(1080, 2424, ByteArray(1080 * 2424 * 4), 1, ScreenRotation.Portrait)
        connection.framesFlow.emit(frame)
        runCurrent()

        viewModel.state.value.status shouldBe EmulatorScreenStatus.Streaming
        viewModel.state.value.frame shouldBe frame
        viewModel.state.value.rotation shouldBe ScreenRotation.Portrait
        viewModel.state.value.framesPerSecond shouldBe 1
    }

    @Test
    fun `locator returning null transitions to Failed with gRPC instruction message`() = testScope { viewModel, _, locator ->
        locator.endpoint = null
        viewModel.handle(EmulatorScreenIntent.Start("emulator-5554"))
        runCurrent()

        viewModel.state.value.status shouldBe EmulatorScreenStatus.Failed(
            "This emulator has no gRPC endpoint. Start it with -grpc <port> " +
                "(for example -grpc 8554 -grpc-use-token), or from Android Studio.",
        )
    }

    @Test
    fun `non-emulator serial transitions to Failed`() = testScope { viewModel, _, _ ->
        viewModel.handle(EmulatorScreenIntent.Start("device-1234"))
        runCurrent()

        viewModel.state.value.status shouldBe EmulatorScreenStatus.Failed("Only emulators can be shown here.")
    }

    @Test
    fun `pointer press and release are mapped to device coordinates`() = testScope { viewModel, connection, _ ->
        viewModel.handle(EmulatorScreenIntent.Start("emulator-5554"))
        runCurrent()

        val frame = EmulatorFrame(1080, 2424, ByteArray(1080 * 2424 * 4), 1, ScreenRotation.Portrait)
        connection.framesFlow.emit(frame)
        runCurrent()

        val shown = FitRect(0f, 0f, 540f, 1212f)
        viewModel.handle(EmulatorScreenIntent.Pointer(viewX = 270f, viewY = 606f, shown = shown, pressed = true))
        runCurrent()
        viewModel.handle(EmulatorScreenIntent.Pointer(viewX = 270f, viewY = 606f, shown = shown, pressed = false))
        runCurrent()

        connection.sentInputs shouldBe listOf(
            EmulatorInput.Mouse(540, 1212, buttons = 1),
            EmulatorInput.Mouse(540, 1212, buttons = 0),
        )
    }

    @Test
    fun `key intent handled by keyFallback is not sent to connection`() = testScopeWithFallback(
        FakeKeyFallback(result = true),
    ) { viewModel, connection, _, fallback ->
        viewModel.handle(EmulatorScreenIntent.Start("emulator-5554"))
        runCurrent()

        val inputKey = EmulatorInput.Key("GoHome")
        viewModel.handle(EmulatorScreenIntent.Key("GoHome"))
        runCurrent()

        fallback.calls shouldBe listOf("emulator-5554" to inputKey)
        connection.sentInputs shouldBe emptyList()
    }

    @Test
    fun `key intent rejected by keyFallback is sent to connection`() = testScopeWithFallback(
        FakeKeyFallback(result = false),
    ) { viewModel, connection, _, fallback ->
        viewModel.handle(EmulatorScreenIntent.Start("emulator-5554"))
        runCurrent()

        val inputKey = EmulatorInput.Key("GoHome")
        viewModel.handle(EmulatorScreenIntent.Key("GoHome"))
        runCurrent()

        fallback.calls shouldBe listOf("emulator-5554" to inputKey)
        connection.sentInputs shouldBe listOf(inputKey)
    }

    @Test
    fun `RotateLeft calls rotate with Landscape and updates rotation optimistically`() = testScope { viewModel, connection, _ ->
        viewModel.handle(EmulatorScreenIntent.Start("emulator-5554"))
        runCurrent()

        viewModel.handle(EmulatorScreenIntent.RotateLeft)
        runCurrent()

        viewModel.state.value.rotation shouldBe ScreenRotation.Landscape
        connection.rotations shouldBe listOf(ScreenRotation.Landscape)
    }

    @Test
    fun `Stop closes connection and resets state to Idle`() = testScope { viewModel, connection, _ ->
        viewModel.handle(EmulatorScreenIntent.Start("emulator-5554"))
        runCurrent()

        connection.closed shouldBe false
        viewModel.handle(EmulatorScreenIntent.Stop)
        runCurrent()

        connection.closed shouldBe true
        viewModel.state.value.status shouldBe EmulatorScreenStatus.Idle
        viewModel.state.value.serial shouldBe null
    }
}
