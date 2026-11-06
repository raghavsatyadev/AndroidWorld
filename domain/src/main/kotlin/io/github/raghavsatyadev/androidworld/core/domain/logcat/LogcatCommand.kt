package io.github.raghavsatyadev.androidworld.core.domain.logcat

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken

object LogcatCommand {
    fun request(serial: DeviceSerial): AdbDeviceRequest = AdbDeviceRequest(
        serial = serial,
        operation = AdbOperation.Shell(
            AdbShellCommand.of(
                ShellToken.Literal("logcat"),
                ShellToken.Literal("-v"),
                ShellToken.Literal("threadtime"),
            ),
        ),
        timeout = null,
    )
}
