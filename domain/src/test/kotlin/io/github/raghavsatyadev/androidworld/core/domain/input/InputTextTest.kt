package io.github.raghavsatyadev.androidworld.core.domain.input

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InputTextTest {
    private val serial = DeviceSerial.of("emulator-5554")
    private fun AdbDeviceRequest.line() = (operation as AdbOperation.Shell).command.render()

    @Test
    fun `textChunks splits on percent immediately followed by s`() {
        InputText.textChunks("a%sb") shouldBe listOf("a%", "sb")
        InputText.textChunks("100% sure") shouldBe listOf("100% sure")
        InputText.textChunks("%s%s") shouldBe listOf("%", "s%", "s")
        InputText.textChunks("plain") shouldBe listOf("plain")
    }

    @Test
    fun `typeText renders simple text and escaping`() {
        val simple = InputCommands.typeText(serial, "a b")
        simple.size shouldBe 1
        simple[0].line() shouldBe "input text 'a%sb'"

        val quoted = InputCommands.typeText(serial, "it's")
        quoted.size shouldBe 1
        quoted[0].line() shouldBe "input text 'it'\\''s'"
    }

    @Test
    fun `typeText renders multi-line text with enter key events`() {
        val requests = InputCommands.typeText(serial, "x\ny")
        requests.map { it.line() } shouldBe listOf(
            "input text 'x'",
            "input keyevent 66",
            "input text 'y'",
        )
    }

    @Test
    fun `typeText handles empty input`() {
        InputCommands.typeText(serial, "") shouldBe emptyList()
    }

    @Test
    fun `typeText rejects non-ASCII and control characters`() {
        shouldThrow<IllegalArgumentException> {
            InputCommands.typeText(serial, "é")
        }
        shouldThrow<IllegalArgumentException> {
            InputCommands.typeText(serial, "\t")
        }
    }

    @Test
    fun `tv remote system keys are defined`() {
        InputCommands.key(serial, SystemKey.DpadCenter).line() shouldBe "input keyevent 23"
        InputCommands.key(serial, SystemKey.Menu).line() shouldBe "input keyevent 82"
        InputCommands.key(serial, SystemKey.Mute).line() shouldBe "input keyevent 164"
        InputCommands.key(serial, SystemKey.PlayPause).line() shouldBe "input keyevent 85"
        InputCommands.key(serial, SystemKey.MediaNext).line() shouldBe "input keyevent 87"
        InputCommands.key(serial, SystemKey.MediaPrevious).line() shouldBe "input keyevent 88"
        InputCommands.key(serial, SystemKey.MediaStop).line() shouldBe "input keyevent 86"
        InputCommands.key(serial, SystemKey.Rewind).line() shouldBe "input keyevent 89"
        InputCommands.key(serial, SystemKey.FastForward).line() shouldBe "input keyevent 90"
        InputCommands.key(serial, SystemKey.Space).line() shouldBe "input keyevent 62"
        InputCommands.key(serial, SystemKey.Settings).line() shouldBe "input keyevent 176"

        SystemKey.of("dpad_center") shouldBe SystemKey.DpadCenter
        SystemKey.of("menu") shouldBe SystemKey.Menu
        SystemKey.of("mute") shouldBe SystemKey.Mute
        SystemKey.of("play_pause") shouldBe SystemKey.PlayPause
        SystemKey.of("media_next") shouldBe SystemKey.MediaNext
        SystemKey.of("media_previous") shouldBe SystemKey.MediaPrevious
        SystemKey.of("media_stop") shouldBe SystemKey.MediaStop
        SystemKey.of("rewind") shouldBe SystemKey.Rewind
        SystemKey.of("fast_forward") shouldBe SystemKey.FastForward
        SystemKey.of("space") shouldBe SystemKey.Space
        SystemKey.of("settings") shouldBe SystemKey.Settings
    }
}
