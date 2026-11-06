package io.github.raghavsatyadev.androidworld.core.domain.display

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTextResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken

/**
 * Reads and writes one [AnimationScaleSetting] via `settings get/put global <name>`
 * (`design/IMPLEMENTATION.md` §4). The "Animations off" toggle issues this three times — once per
 * [AnimationScaleSetting] — never as a single combined command, since the three settings are
 * independent on the device. An unset key (`null`) is the framework default, a scale of 1.0.
 */
object AnimationScaleCommand {

    /** What the framework uses when the setting was never written. */
    const val DEFAULT_SCALE: Float = 1.0f

    fun readRequest(serial: DeviceSerial, setting: AnimationScaleSetting): AdbDeviceRequest = AdbDeviceRequest(
        serial = serial,
        operation = AdbOperation.Shell(
            AdbShellCommand.of(
                ShellToken.Literal("settings"),
                ShellToken.Literal("get"),
                ShellToken.Literal("global"),
                ShellToken.Literal(setting.settingName),
            ),
        ),
    )

    fun writeRequest(serial: DeviceSerial, setting: AnimationScaleSetting, enabled: Boolean): AdbDeviceRequest =
        AdbDeviceRequest(
            serial = serial,
            operation = AdbOperation.Shell(
                AdbShellCommand.of(
                    ShellToken.Literal("settings"),
                    ShellToken.Literal("put"),
                    ShellToken.Literal("global"),
                    ShellToken.Literal(setting.settingName),
                    ShellToken.Literal(if (enabled) "1" else "0"),
                ),
            ),
        )

    fun parseRead(result: AdbTextResult): DisplaySettingRead<Float> {
        if (!result.outcome.reachedDevice()) return DisplaySettingRead.TransportFailed(result.outcome)
        if (result.stderr.looksLikePermissionDenial()) return DisplaySettingRead.PermissionDenied(result.stderr)

        val trimmed = result.stdout.trim()
        if (trimmed == "null") return DisplaySettingRead.Value(DEFAULT_SCALE)
        val value = trimmed.toFloatOrNull()
            ?: return DisplaySettingRead.Malformed(
                raw = result.stdout,
                reason = "expected a decimal scale value or 'null'",
            )
        return DisplaySettingRead.Value(value)
    }
}
