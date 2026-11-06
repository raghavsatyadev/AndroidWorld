package io.github.raghavsatyadev.androidworld.core.domain.apps

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class UninstallCommandsTest {

    @Test
    fun `uninstall is a host adb operation and never a remote shell command`() {
        UninstallCommands.uninstall("com.acme.shop") shouldBe
            AdbOperation.Host(listOf("uninstall", "com.acme.shop"))
    }
}
