package io.github.raghavsatyadev.androidworld.core.domain.input

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellValue

/** A system key an agent can press, with the shell line that presses it. */
enum class SystemKey(val id: String, val command: List<String>) {
    Back("back", listOf("input", "keyevent", "4")),
    Home("home", listOf("input", "keyevent", "3")),
    Recents("recents", listOf("input", "keyevent", "187")),
    Enter("enter", listOf("input", "keyevent", "66")),
    Delete("delete", listOf("input", "keyevent", "67")),
    Tab("tab", listOf("input", "keyevent", "61")),
    Escape("escape", listOf("input", "keyevent", "111")),
    Up("up", listOf("input", "keyevent", "19")),
    Down("down", listOf("input", "keyevent", "20")),
    Left("left", listOf("input", "keyevent", "21")),
    Right("right", listOf("input", "keyevent", "22")),
    VolumeUp("volume_up", listOf("input", "keyevent", "24")),
    VolumeDown("volume_down", listOf("input", "keyevent", "25")),
    Power("power", listOf("input", "keyevent", "26")),
    Wakeup("wakeup", listOf("input", "keyevent", "224")),
    Notifications("notifications", listOf("cmd", "statusbar", "expand-notifications")),
    QuickSettings("quick_settings", listOf("cmd", "statusbar", "expand-settings")),
    DpadCenter("dpad_center", listOf("input", "keyevent", "23")),
    Menu("menu", listOf("input", "keyevent", "82")),
    Mute("mute", listOf("input", "keyevent", "164")),
    PlayPause("play_pause", listOf("input", "keyevent", "85")),
    MediaNext("media_next", listOf("input", "keyevent", "87")),
    MediaPrevious("media_previous", listOf("input", "keyevent", "88")),
    MediaStop("media_stop", listOf("input", "keyevent", "86")),
    Rewind("rewind", listOf("input", "keyevent", "89")),
    FastForward("fast_forward", listOf("input", "keyevent", "90")),
    Space("space", listOf("input", "keyevent", "62")),
    Settings("settings", listOf("input", "keyevent", "176")),
    ;

    companion object {
        fun of(id: String): SystemKey? = entries.firstOrNull { it.id == id }
    }
}

/**
 * `input` gestures and typing in device pixels (callers convert from dp).
 */
object InputCommands {
    fun tap(serial: DeviceSerial, x: Int, y: Int) = shell(serial, "input", "tap", x.toString(), y.toString())

    fun longPress(serial: DeviceSerial, x: Int, y: Int, durationMs: Int = 800) = swipe(serial, x, y, x, y, durationMs)

    fun swipe(serial: DeviceSerial, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int) =
        shell(serial, "input", "swipe", x1.toString(), y1.toString(), x2.toString(), y2.toString(), durationMs.toString())

    fun key(serial: DeviceSerial, key: SystemKey) = shell(serial, *key.command.toTypedArray())

    /** True when `input text` can type [text]: printable ASCII on one line (an adb limit). */
    fun canType(text: String): Boolean = text.isNotEmpty() && text.all { it.code in 0x20..0x7E }

    /** `input text` reads `%s` as a space; the text itself is shell-quoted. */
    fun text(serial: DeviceSerial, text: String): AdbDeviceRequest {
        require(canType(text)) { "input text types printable ASCII only" }
        return AdbDeviceRequest(
            serial,
            AdbOperation.Shell(AdbShellCommand.of(ShellToken.Literal("input"), ShellToken.Literal("text"), ShellToken.Value(ShellValue.of(text.replace(" ", "%s"))))),
        )
    }

    /**
     * Types arbitrary printable-ASCII text, with '\n' (and "\r\n") allowed as line breaks.
     * Splitting on '%s' avoids adb turning literal "%s" sequences into spaces.
     */
    fun typeText(serial: DeviceSerial, text: String): List<AdbDeviceRequest> {
        if (text.isEmpty()) return emptyList()
        val normalized = text.replace("\r\n", "\n")
        require(normalized.all { it == '\n' || it.code in 0x20..0x7E }) {
            "Only printable ASCII can be typed (adb input text limit)"
        }
        val lines = normalized.split('\n')
        val requests = mutableListOf<AdbDeviceRequest>()
        for (i in lines.indices) {
            if (i > 0) {
                requests.add(key(serial, SystemKey.Enter))
            }
            val line = lines[i]
            if (line.isNotEmpty()) {
                for (chunk in InputText.textChunks(line)) {
                    requests.add(text(serial, chunk))
                }
            }
        }
        return requests
    }

    private fun shell(serial: DeviceSerial, vararg literals: String) =
        AdbDeviceRequest(serial, AdbOperation.Shell(AdbShellCommand.of(*literals.map { ShellToken.Literal(it) }.toTypedArray())))
}
