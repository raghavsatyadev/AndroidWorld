@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.raghavsatyadev.androidworld.core.application.remote

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.FakeAdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.device.Device
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceConnectionState
import io.github.raghavsatyadev.androidworld.core.domain.device.SelectedDeviceState
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.input.SystemKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class TestDispatchers(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val default = dispatcher
    override val io = dispatcher
    override val main = dispatcher
}

private fun online(serial: String) =
    SelectedDeviceState.Online(Device(DeviceSerial.of(serial), DeviceConnectionState.Online))

class RemoteControlViewModelTest {

    private fun harness(
        device: SelectedDeviceState = online("emulator-5554"),
        transport: FakeAdbTransport = FakeAdbTransport(),
    ): Triple<TestScope, RemoteControlViewModel, FakeAdbTransport> {
        val scope = TestScope()
        val dispatchers = TestDispatchers(StandardTestDispatcher(scope.testScheduler))
        val viewModel =
            RemoteControlViewModel(scope, dispatchers, transport, MutableStateFlow(device))
        return Triple(scope, viewModel, transport)
    }

    @Test
    fun `Press sends a request with shell line input keyevent 23 for selected serial`() = runTest {
        val (scope, viewModel, transport) = harness()
        scope.runCurrent()
        viewModel.state.value.enabled shouldBe true

        viewModel.handle(RemoteControlIntent.Press(SystemKey.DpadCenter))
        scope.runCurrent()

        transport.textRequests.size shouldBe 1
        val request = transport.textRequests.single() as AdbDeviceRequest
        request.serial.value shouldBe "emulator-5554"
        val shell = (request.operation as AdbOperation.Shell).command.render()
        shell shouldBe "input keyevent 23"
    }

    @Test
    fun `Type sends one input text request for printable ASCII with space`() = runTest {
        val (scope, viewModel, transport) = harness()
        scope.runCurrent()

        viewModel.handle(RemoteControlIntent.Type("a b"))
        scope.runCurrent()

        transport.textRequests.size shouldBe 1
        val request = transport.textRequests.single() as AdbDeviceRequest
        request.serial.value shouldBe "emulator-5554"
        val shell = (request.operation as AdbOperation.Shell).command.render()
        shell shouldBe "input text 'a%sb'"
        viewModel.state.value.busy shouldBe false
        viewModel.state.value.error shouldBe null
    }

    @Test
    fun `Type with non-ASCII sends nothing and sets error`() = runTest {
        val (scope, viewModel, transport) = harness()
        scope.runCurrent()

        viewModel.handle(RemoteControlIntent.Type("é"))
        scope.runCurrent()

        transport.textRequests shouldBe emptyList()
        viewModel.state.value.error shouldBe
            "Only printable ASCII can be typed (adb input text limit)"
        viewModel.state.value.busy shouldBe false

        viewModel.handle(RemoteControlIntent.DismissError)
        scope.runCurrent()
        viewModel.state.value.error shouldBe null
    }

    @Test
    fun `no device sets enabled false and ignores intents`() = runTest {
        val (scope, viewModel, transport) = harness(device = SelectedDeviceState.None)
        scope.runCurrent()

        viewModel.state.value.enabled shouldBe false

        viewModel.handle(RemoteControlIntent.Press(SystemKey.DpadCenter))
        viewModel.handle(RemoteControlIntent.Type("hello"))
        scope.runCurrent()

        transport.textRequests shouldBe emptyList()
        viewModel.state.value.enabled shouldBe false
    }

    @Test
    fun `presses keep order strictly`() = runTest {
        val (scope, viewModel, transport) = harness()
        scope.runCurrent()

        viewModel.handle(RemoteControlIntent.Press(SystemKey.Up))
        viewModel.handle(RemoteControlIntent.Press(SystemKey.Down))
        scope.runCurrent()

        transport.textRequests.size shouldBe 2
        val r1 = (transport.textRequests[0] as AdbDeviceRequest).operation as AdbOperation.Shell
        val r2 = (transport.textRequests[1] as AdbDeviceRequest).operation as AdbOperation.Shell
        r1.command.render() shouldBe "input keyevent 19"
        r2.command.render() shouldBe "input keyevent 20"
    }
}
