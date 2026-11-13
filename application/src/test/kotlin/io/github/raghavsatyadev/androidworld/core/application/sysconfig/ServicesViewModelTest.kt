package io.github.raghavsatyadev.androidworld.core.application.sysconfig

import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServicesViewModelTest {
    private val context = MutableStateFlow<DeviceCommandContext>(DeviceCommandContext.Eligible(DeviceSerial.of("emulator-5554")))

    @Test
    fun `services merge service list with dumpsys -l and a dump is searchable`() = runTest {
        val transport = device { line ->
            when (line) {
                "service list" -> ok("Found 2 services:\n0\tactivity: [android.app.IActivityManager]\n1\tDockObserver: []\n")
                "dumpsys -l" -> ok("Currently running services:\n  activity\n  extra_only\n")
                else -> ok("ACTIVITY MANAGER\n  mFocused=true\n  other\n")
            }
        }
        val vm = ServicesViewModel(backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), ShellCommandRunner(transport), context)
        runCurrent()
        vm.state.value.rows.map { it.name to it.dumpable } shouldContainExactly listOf(
            "activity" to true, "DockObserver" to false, "extra_only" to true,
        )
        vm.handle(ServicesIntent.Filter("IActivity"))
        vm.state.value.shown.map { it.name } shouldContainExactly listOf("activity")

        vm.handle(ServicesIntent.Open("activity"))
        runCurrent()
        transport.textRequests.last().line() shouldBe "dumpsys 'activity'"
        vm.state.value.lines.size shouldBe 3
        vm.handle(ServicesIntent.SearchDump("focused"))
        runCurrent()
        vm.state.value.visible shouldContainExactly listOf(1)
    }
}
