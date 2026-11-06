package io.github.raghavsatyadev.androidworld.core.application.commands

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTextResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.FakeAdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
    override val default = d
    override val io = d
    override val main = d
}

private fun AdbRequest.line() = ((this as AdbDeviceRequest).operation as AdbOperation.Shell).command.render()

private fun ok(stdout: String) = AdbTextResult(AdbOutcome.Completed(0), stdout, "")

@OptIn(ExperimentalCoroutinesApi::class)
class CommandBrowserViewModelTest {
    private val emulator = DeviceCommandContext.Eligible(DeviceSerial.of("emulator-5554"))
    private val context = MutableStateFlow<DeviceCommandContext>(emulator)

    private fun TestScope.browser(transport: FakeAdbTransport, confirm: Boolean = true, asked: MutableList<String> = mutableListOf()): CommandBrowserViewModel {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return CommandBrowserViewModel(
            scope = backgroundScope,
            dispatchers = TestDispatchers(dispatcher),
            runner = ShellCommandRunner(transport),
            commandContext = context,
            confirm = { _, line -> asked += line; confirm },
        ).also { runCurrent() }
    }

    @Test
    fun `opening an entry reads its current value`() = runTest {
        val transport = FakeAdbTransport(textScript = { ok("Current Battery Service state:\n  level: 87\n") })
        val vm = browser(transport)
        vm.handle(CommandBrowserIntent.Open("battery-level"))
        runCurrent()
        vm.state.value.currents["battery-level"] shouldBe CurrentValue.Known("level: 87")
        transport.textRequests.map { it.line() } shouldContainExactly listOf("dumpsys battery")
    }

    @Test
    fun `run sends the built command and re-reads the current value`() = runTest {
        val transport = FakeAdbTransport(textScript = { ok(if (it.line().startsWith("settings get")) "1\n" else "") })
        val vm = browser(transport)
        vm.handle(CommandBrowserIntent.SetField("show-touches", 0, "1"))
        vm.handle(CommandBrowserIntent.Run("show-touches"))
        runCurrent()
        transport.textRequests.map { it.line() } shouldContainExactly listOf("settings put system show_touches '1'", "settings get system show_touches")
        vm.state.value.runs["show-touches"] shouldBe CommandRun("settings put system show_touches '1'", running = false, exitCode = 0)
        vm.state.value.currents["show-touches"] shouldBe CurrentValue.Known("1")
    }

    @Test
    fun `a declined destructive command never runs`() = runTest {
        val transport = FakeAdbTransport()
        val asked = mutableListOf<String>()
        val vm = browser(transport, confirm = false, asked = asked)
        vm.handle(CommandBrowserIntent.SetField("disable-app", 0, "com.example.app"))
        vm.handle(CommandBrowserIntent.Run("disable-app"))
        runCurrent()
        asked shouldContainExactly listOf("pm disable-user --user 0 'com.example.app'")
        transport.textRequests.size shouldBe 0
        vm.state.value.runs["disable-app"] shouldBe null
    }

    @Test
    fun `invalid fields keep run disabled`() = runTest {
        val transport = FakeAdbTransport()
        val vm = browser(transport)
        vm.handle(CommandBrowserIntent.SetField("battery-level", 0, "250"))
        vm.handle(CommandBrowserIntent.Run("battery-level"))
        runCurrent()
        transport.textRequests.size shouldBe 0
        val entry = vm.state.value.entries.single { it.id == "battery-level" }
        vm.state.value.canRun(entry) shouldBe false
    }

    @Test
    fun `a failed command shows its error`() = runTest {
        val vm = browser(FakeAdbTransport(textScript = { AdbTextResult(AdbOutcome.Completed(255), "", "Unknown package: x.y\n") }))
        vm.handle(CommandBrowserIntent.SetField("enable-app", 0, "x.y"))
        vm.handle(CommandBrowserIntent.Run("enable-app"))
        runCurrent()
        vm.state.value.runs["enable-app"] shouldBe CommandRun("pm enable 'x.y'", running = false, output = "Unknown package: x.y", failed = true, exitCode = 255)
    }

    @Test
    fun `switching device clears results`() = runTest {
        val vm = browser(FakeAdbTransport())
        vm.handle(CommandBrowserIntent.Run("uptime"))
        runCurrent()
        vm.state.value.runs.keys shouldContainExactly setOf("uptime")
        context.value = DeviceCommandContext.Disabled.NoDeviceSelected
        runCurrent()
        vm.state.value.serial shouldBe null
        vm.state.value.runs shouldBe emptyMap()
    }

    @Test
    fun `search and category narrow the list`() = runTest {
        val vm = browser(FakeAdbTransport())
        vm.handle(CommandBrowserIntent.FilterCategory(CommandCategory.Battery))
        vm.state.value.entries.all { it.category == CommandCategory.Battery } shouldBe true
        vm.handle(CommandBrowserIntent.Search("saver"))
        vm.state.value.entries.map { it.id } shouldContainExactly listOf("battery-saver")
    }

    @Test
    fun `dumpsys lists services for the device and searches a dump`() = runTest {
        val transport = FakeAdbTransport(textScript = {
            if (it.line() == "dumpsys -l") ok("Currently running services:\n  battery\n  activity\n") else ok("a: 1\nlevel: 87\nb: 2\n")
        })
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = DumpsysViewModel(backgroundScope, TestDispatchers(dispatcher), ShellCommandRunner(transport), context)
        runCurrent()
        vm.state.value.services shouldContainExactly listOf("activity", "battery")
        vm.handle(DumpsysIntent.Dump("battery"))
        runCurrent()
        vm.state.value.lines shouldContainExactly listOf("a: 1", "level: 87", "b: 2")
        vm.handle(DumpsysIntent.SearchDump("LEVEL"))
        runCurrent()
        vm.state.value.visible shouldContainExactly listOf(1)
    }
}
