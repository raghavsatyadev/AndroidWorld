package io.github.raghavsatyadev.androidworld.core.application.content

import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTextResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.FakeAdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.content.PimTable
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test

private class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
    override val default = d
    override val io = d
    override val main = d
}

private fun AdbRequest.line() = ((this as AdbDeviceRequest).operation as AdbOperation.Shell).command.render()

@OptIn(ExperimentalCoroutinesApi::class)
class ContentViewerViewModelTest {
    private val context = MutableStateFlow<DeviceCommandContext>(DeviceCommandContext.Eligible(DeviceSerial.of("emulator-5554")))

    private fun TestScope.viewer(script: (String) -> AdbTextResult): Pair<ContentViewerViewModel, FakeAdbTransport> {
        val transport = FakeAdbTransport(textScript = { script(it.line()) })
        val vm = ContentViewerViewModel(
            scope = backgroundScope,
            dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
            runner = ShellCommandRunner(transport),
            commandContext = context,
            zone = { TimeZone.UTC },
        )
        runCurrent()
        return vm to transport
    }

    private fun ok(stdout: String, stderr: String = "") = AdbTextResult(AdbOutcome.Completed(0), stdout, stderr)

    @Test
    fun `selecting a table queries it and search filters rows`() = runTest {
        val (vm, transport) = viewer { line ->
            if (line.contains("sms/inbox")) {
                ok("Row: 0 address=111, date=1763000000000, read=1, body=Hi, there\nRow: 1 address=222, date=1763000000000, read=0, body=Bye")
            } else {
                ok("No result found.\n")
            }
        }
        vm.state.value.notice shouldBe ContentNotice.Empty
        vm.handle(ContentViewerIntent.Select(PimTable.SmsInbox))
        runCurrent()
        transport.textRequests.last().line() shouldStartWith "content query --uri 'content://sms/inbox'"
        vm.state.value.notice shouldBe null
        vm.state.value.data.rows.size shouldBe 2
        vm.handle(ContentViewerIntent.Search("there"))
        runCurrent()
        vm.state.value.visible shouldBe listOf(0)
        vm.state.value.csv() shouldBe "Address,Date,Message,Read\r\n111,2025-11-13T02:13:20,\"Hi, there\",Yes\r\n"
        vm.state.value.csvFileName() shouldBe "emulator-5554-sms-inbox.csv"
    }

    @Test
    fun `a permission denial is a notice, not rows`() = runTest {
        val (vm, _) = viewer {
            ok("", "Error while accessing provider:sms\njava.lang.SecurityException: Permission Denial: requires android.permission.READ_SMS")
        }
        vm.handle(ContentViewerIntent.Select(PimTable.SmsSent))
        runCurrent()
        vm.state.value.notice.shouldBeInstanceOf<ContentNotice.PermissionDenied>()
        vm.state.value.loading shouldBe false
    }

    @Test
    fun `no device means no query`() = runTest {
        context.value = DeviceCommandContext.Disabled.NoDeviceSelected
        val (vm, transport) = viewer { ok("") }
        vm.state.value.notice shouldBe ContentNotice.NoDevice
        transport.textRequests.size shouldBe 0
    }
}
