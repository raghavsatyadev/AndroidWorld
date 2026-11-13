package io.github.raghavsatyadev.androidworld.core.adapters.jvm.process

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WindowsArgumentsTest {

    @Test
    fun `escape handles quote inside argument`() {
        WindowsArguments.escape("x\"y") shouldBe "x\\\"y"
    }

    @Test
    fun `escape handles multiple quotes`() {
        WindowsArguments.escape("a \"b\" c") shouldBe "a \\\"b\\\" c"
    }

    @Test
    fun `escape doubles backslashes before quote plus one`() {
        WindowsArguments.escape("a\\\"b") shouldBe "a\\\\\\\"b"
    }

    @Test
    fun `escape preserves trailing backslash`() {
        WindowsArguments.escape("a b\\") shouldBe "a b\\"
    }

    @Test
    fun `escape leaves backslashes without quotes unchanged`() {
        WindowsArguments.escape("c:\\dir\\file") shouldBe "c:\\dir\\file"
    }

    @Test
    fun `escape handles empty string`() {
        WindowsArguments.escape("") shouldBe ""
    }

    @Test
    fun `argv escapes arguments on Windows`() {
        WindowsArguments.argv("adb", listOf("shell", "x\"y"), onWindows = true) shouldBe
            listOf("adb", "shell", "x\\\"y")
    }

    @Test
    fun `argv leaves arguments unchanged when not on Windows`() {
        WindowsArguments.argv("adb", listOf("x\"y"), onWindows = false) shouldBe
            listOf("adb", "x\"y")
    }

    @Test
    fun `argv leaves arguments unchanged for batch scripts on Windows`() {
        WindowsArguments.argv("C:\\sdk\\sdkmanager.BAT", listOf("x\"y"), onWindows = true) shouldBe
            listOf("C:\\sdk\\sdkmanager.BAT", "x\"y")
    }
}
