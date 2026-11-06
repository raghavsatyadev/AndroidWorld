package io.github.raghavsatyadev.androidworld.core.domain.layout

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTextResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken

/** What one `uiautomator dump` produced. */
sealed interface UiDumpRead {
    data class Dumped(val root: UiNode) : UiDumpRead

    /** Nothing printed: uiautomator refused, usually because the screen was still animating. */
    data object Empty : UiDumpRead

    data class Malformed(val reason: String) : UiDumpRead

    data class TransportFailed(val outcome: AdbOutcome) : UiDumpRead
}

/**
 * `uiautomator dump` of the current screen (ADR 0014), in one shell call: dump into the shell user's
 * `/data/local/tmp` (not visible to apps), print it, and remove it whatever happened.
 */
object UiAutomatorDumpCommand {
    // Device-side name kept from the pre-rename "ADB Toolbox" name for compatibility with helpers and files already on devices.
    private const val FILE = "/data/local/tmp/adbtoolbox-ui.xml"

    fun request(serial: DeviceSerial): AdbDeviceRequest = AdbDeviceRequest(
        serial = serial,
        operation = AdbOperation.Shell(
            AdbShellCommand.of(
                *"uiautomator dump $FILE >/dev/null 2>&1 && cat $FILE; rm -f $FILE"
                    .split(' ').map { ShellToken.Literal(it) }.toTypedArray(),
            ),
        ),
    )

    fun parse(result: AdbTextResult): UiDumpRead {
        if (result.outcome !is AdbOutcome.Completed) return UiDumpRead.TransportFailed(result.outcome)
        if (result.stdout.isBlank()) return UiDumpRead.Empty
        return when (val parsed = UiAutomatorXmlParser.parse(result.stdout)) {
            is UiTreeParse.Parsed -> UiDumpRead.Dumped(parsed.root)
            is UiTreeParse.Malformed -> UiDumpRead.Malformed(parsed.reason)
        }
    }
}
