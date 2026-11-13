package io.github.raghavsatyadev.androidworld.core.application.sysconfig

import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTextResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.FakeAdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.DeviceSetting
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.SettingsNamespace
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.SystemProperty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class TestDispatchers(d: CoroutineDispatcher) : DispatcherProvider {
    override val default = d
    override val io = d
    override val main = d
}

internal fun AdbRequest.line() = ((this as AdbDeviceRequest).operation as AdbOperation.Shell).command.render()

internal fun ok(stdout: String, stderr: String = "", exit: Int = 0) = AdbTextResult(AdbOutcome.Completed(exit), stdout, stderr)

/** A fake device whose answers come from [answer], keyed on the rendered command line. */
internal fun device(answer: (String) -> AdbTextResult) = FakeAdbTransport(textScript = { answer(it.line()) })

@OptIn(ExperimentalCoroutinesApi::class)
class SysConfigViewModelsTest {
    private val context = MutableStateFlow<DeviceCommandContext>(DeviceCommandContext.Eligible(DeviceSerial.of("emulator-5554")))

    private fun TestScope.properties(transport: FakeAdbTransport, confirm: Boolean = true, asked: MutableList<SysConfigWrite> = mutableListOf()) =
        PropertiesViewModel(backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), ShellCommandRunner(transport), context) {
            asked += it; confirm
        }.also { runCurrent() }

    private fun TestScope.settings(transport: FakeAdbTransport, confirm: Boolean = true, asked: MutableList<SysConfigWrite> = mutableListOf()) =
        DeviceSettingsViewModel(backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)), ShellCommandRunner(transport), context) {
            asked += it; confirm
        }.also { runCurrent() }

    @Test
    fun `properties load, filter by value and know whether the shell is root`() = runTest {
        val vm = properties(device { if (it == "id -u") ok("2000\n") else ok("[ro.build.type]: [user]\n[debug.x]: [abc]\n") })
        vm.state.value.properties shouldContainExactly listOf(SystemProperty("debug.x", "abc"), SystemProperty("ro.build.type", "user"))
        vm.state.value.root shouldBe false
        vm.handle(PropertiesIntent.Filter("USER"))
        vm.state.value.shown.map { it.name } shouldContainExactly listOf("ro.build.type")
    }

    @Test
    fun `setprop is confirmed, run with quoting and read back`() = runTest {
        var value = "old"
        val transport = device { line ->
            when {
                line == "id -u" -> ok("2000\n")
                line == "getprop" -> ok("[debug.x]: [$value]\n")
                line.startsWith("setprop") -> { value = "a b'c"; ok("") }
                else -> ok("$value\n")
            }
        }
        val asked = mutableListOf<SysConfigWrite>()
        val vm = properties(transport, asked = asked)
        vm.handle(PropertiesIntent.Set("debug.x", "a b'c"))
        runCurrent()
        asked.map { it.commandLine } shouldContainExactly listOf("setprop 'debug.x' 'a b'\\''c'")
        transport.textRequests.map { it.line() }.takeLast(2) shouldContainExactly listOf("setprop 'debug.x' 'a b'\\''c'", "getprop 'debug.x'")
        vm.state.value.result.shouldBeInstanceOf<WriteResult.Applied>()
        vm.state.value.properties shouldContainExactly listOf(SystemProperty("debug.x", "a b'c"))
    }

    @Test
    fun `a setprop that doesn't stick reports stderr`() = runTest {
        val transport = device { line ->
            when {
                line == "id -u" -> ok("0\n")
                line == "getprop" -> ok("[ro.x]: [user]\n")
                line.startsWith("setprop") -> ok("", "Failed to set property 'ro.x' to 'foo'.\n", exit = 1)
                else -> ok("user\n")
            }
        }
        val vm = properties(transport)
        vm.handle(PropertiesIntent.Set("ro.x", "foo"))
        runCurrent()
        vm.state.value.result shouldBe WriteResult.Rejected("ro.x didn't change. Failed to set property 'ro.x' to 'foo'.")
    }

    @Test
    fun `ro properties are refused without root and declined writes never run`() = runTest {
        val transport = device { if (it == "id -u") ok("2000\n") else ok("") }
        val vm = properties(transport, confirm = false)
        vm.handle(PropertiesIntent.Set("ro.build.type", "eng"))
        vm.handle(PropertiesIntent.Set("debug.x", "1"))
        runCurrent()
        vm.state.value.result.shouldBeInstanceOf<WriteResult.Rejected>()
        transport.textRequests.map { it.line() } shouldContainExactly listOf("getprop", "id -u")
    }

    @Test
    fun `settings switch namespace and put reads the value back`() = runTest {
        val stored = mutableMapOf("font_scale" to "1.0")
        val transport = device { line ->
            when {
                line.startsWith("settings list") -> ok(stored.entries.joinToString("\n") { "${it.key}=${it.value}" })
                line.startsWith("settings put") -> { stored["aw_test"] = "x y"; ok("") }
                line.startsWith("settings get") -> ok((stored["aw_test"] ?: "null") + "\n")
                else -> ok("")
            }
        }
        val asked = mutableListOf<SysConfigWrite>()
        val vm = settings(transport, asked = asked)
        vm.handle(DeviceSettingsIntent.SelectNamespace(SettingsNamespace.Secure))
        runCurrent()
        vm.handle(DeviceSettingsIntent.Put("aw_test", "x y"))
        runCurrent()
        transport.textRequests.map { it.line() } shouldContainExactly listOf(
            "settings list global", "settings list secure", "settings put secure 'aw_test' 'x y'", "settings get secure 'aw_test'",
        )
        vm.state.value.entries shouldContainExactly listOf(DeviceSetting("aw_test", "x y"), DeviceSetting("font_scale", "1.0"))
        vm.state.value.result.shouldBeInstanceOf<WriteResult.Applied>()
    }

    @Test
    fun `delete checks the key is gone and risky keys carry a note`() = runTest {
        val stored = mutableMapOf("adb_enabled" to "1", "aw_test" to "1")
        val transport = device { line ->
            when {
                line.startsWith("settings list") -> ok(stored.entries.joinToString("\n") { "${it.key}=${it.value}" })
                line.startsWith("settings delete") -> { stored.remove("aw_test"); ok("Deleted 1 rows\n") }
                line.startsWith("settings get global 'aw_test'") -> ok((stored["aw_test"] ?: "null") + "\n")
                else -> ok("")
            }
        }
        val asked = mutableListOf<SysConfigWrite>()
        val vm = settings(transport, asked = asked)
        vm.handle(DeviceSettingsIntent.Delete("aw_test"))
        runCurrent()
        vm.state.value.entries shouldContainExactly listOf(DeviceSetting("adb_enabled", "1"))
        vm.state.value.result shouldBe WriteResult.Applied("Deleted global aw_test.")
        asked.single().note shouldBe null

        val declined = settings(transport, confirm = false, asked = asked)
        declined.handle(DeviceSettingsIntent.Put("adb_enabled", "0"))
        runCurrent()
        (asked.last().note != null) shouldBe true
        transport.textRequests.none { it.line().contains("put") } shouldBe true
    }

    @Test
    fun `a put that exits zero but doesn't stick is reported`() = runTest {
        val transport = device { line ->
            when {
                line.startsWith("settings put") -> ok("java.lang.SecurityException: Permission denial\n")
                line.startsWith("settings get") -> ok("null\n")
                else -> ok("")
            }
        }
        val vm = settings(transport)
        vm.handle(DeviceSettingsIntent.Put("locked", "1"))
        runCurrent()
        vm.state.value.result shouldBe WriteResult.Rejected("global locked didn't change. java.lang.SecurityException: Permission denial")
    }
}
