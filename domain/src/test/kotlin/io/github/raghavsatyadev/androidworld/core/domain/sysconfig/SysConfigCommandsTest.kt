package io.github.raghavsatyadev.androidworld.core.domain.sysconfig

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Parsers are checked against output captured from an API 36 emulator. */
class SysConfigCommandsTest {

    @Test
    fun `getprop output parses including a value that spans lines`() {
        val output = """
            [apex.all.ready]: [true]
            [bluetooth.device.class_of_device]: [90,2,12]
            [persist.sys.boot.reason.history]: [reboot,factory_reset,1794402365
            reboot,1794402305]
            [ro.build.type]: [user]
            [sys.empty]: []
        """.trimIndent()
        SystemPropertyCommands.parse(output) shouldContainExactly listOf(
            SystemProperty("apex.all.ready", "true"),
            SystemProperty("bluetooth.device.class_of_device", "90,2,12"),
            SystemProperty("persist.sys.boot.reason.history", "reboot,factory_reset,1794402365\nreboot,1794402305"),
            SystemProperty("ro.build.type", "user"),
            SystemProperty("sys.empty", ""),
        )
    }

    @Test
    fun `property kinds follow the name prefix`() {
        PropertyKind.of("ro.build.type") shouldBe PropertyKind.ReadOnly
        PropertyKind.of("persist.sys.locale") shouldBe PropertyKind.Persistent
        PropertyKind.of("ctl.start") shouldBe PropertyKind.Control
        PropertyKind.of("debug.hwui.overdraw") shouldBe PropertyKind.Normal
    }

    @Test
    fun `setprop quotes the name and a value with spaces and quotes`() {
        SystemPropertyCommands.set("debug.aw.test", "a b'c").render() shouldBe "setprop 'debug.aw.test' 'a b'\\''c'"
        SystemPropertyCommands.get("debug.aw.test").render() shouldBe "getprop 'debug.aw.test'"
    }

    @Test
    fun `property validation rejects bad names, ctl and long values`() {
        SystemPropertyCommands.validate("debug.x", "1").shouldBeNull()
        SystemPropertyCommands.validate("", "1").shouldNotBeNull()
        SystemPropertyCommands.validate("debug x", "1").shouldNotBeNull()
        SystemPropertyCommands.validate("ctl.start", "adbd").shouldNotBeNull()
        SystemPropertyCommands.validate("debug.x", "a".repeat(92)).shouldNotBeNull()
        SystemPropertyCommands.validate("ro.x", "a".repeat(200)).shouldBeNull()
    }

    @Test
    fun `settings list splits on the first equals and joins continuation lines`() {
        val output = "adb_enabled=1\nlock_pattern=a=b\nmultiline=one\ntwo\n_boot_Phenotype_flags=\n"
        DeviceSettingsCommands.parseList(output) shouldContainExactly listOf(
            DeviceSetting("_boot_Phenotype_flags", ""),
            DeviceSetting("adb_enabled", "1"),
            DeviceSetting("lock_pattern", "a=b"),
            DeviceSetting("multiline", "one\ntwo"),
        )
    }

    @Test
    fun `settings get reads the literal null as missing`() {
        DeviceSettingsCommands.parseGet("null\n").shouldBeNull()
        DeviceSettingsCommands.parseGet("1\n") shouldBe "1"
        DeviceSettingsCommands.parseGet("\n") shouldBe ""
    }

    @Test
    fun `settings commands render with quoted key and value`() {
        val ns = SettingsNamespace.Global
        DeviceSettingsCommands.list(ns).render() shouldBe "settings list global"
        DeviceSettingsCommands.get(ns, "k").render() shouldBe "settings get global 'k'"
        DeviceSettingsCommands.put(SettingsNamespace.Secure, "k", "a b'c").render() shouldBe "settings put secure 'k' 'a b'\\''c'"
        DeviceSettingsCommands.delete(SettingsNamespace.System, "k").render() shouldBe "settings delete system 'k'"
    }

    @Test
    fun `settings keys are validated and risky ones carry a note`() {
        DeviceSettingsCommands.validateKey("font_scale").shouldBeNull()
        DeviceSettingsCommands.validateKey(" ").shouldNotBeNull()
        DeviceSettingsCommands.validateKey("bad key").shouldNotBeNull()
        DeviceSettingsCommands.validateKey("a=b").shouldNotBeNull()
        DeviceSettingsCommands.riskNote("adb_enabled").shouldNotBeNull()
        DeviceSettingsCommands.riskNote("font_scale").shouldBeNull()
    }

    @Test
    fun `service list parses names and interfaces`() {
        val output = "Found 332 services:\n0\tDockObserver: []\n1\tSurfaceFlinger: [android.ui.ISurfaceComposer]\n2\tactivity: [android.app.IActivityManager]\n"
        ServiceListCommand.parse(output) shouldContainExactly listOf(
            SystemService("activity", "android.app.IActivityManager"),
            SystemService("DockObserver", null),
            SystemService("SurfaceFlinger", "android.ui.ISurfaceComposer"),
        )
        ServiceListCommand.list.render() shouldBe "service list"
    }
}
