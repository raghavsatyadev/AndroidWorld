package io.github.raghavsatyadev.androidworld.core.application.commands

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbDeviceRequest
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import kotlin.time.Duration

/** How a shell command ended: it ran (whatever its exit code), or it never got an answer. */
sealed interface ShellResult {
    data class Ran(val exitCode: Int?, val stdout: String, val stderr: String) : ShellResult {
        val succeeded: Boolean get() = exitCode == null || exitCode == 0
    }

    data class Failed(val reason: String) : ShellResult
}

/**
 * Runs one `adb shell` command on one device and folds every [AdbOutcome] into a [ShellResult] the
 * UI can show. Always bounded by a timeout, since a command like `top` without `-n` never ends.
 */
class ShellCommandRunner(private val transport: AdbTransport) {
    suspend fun run(serial: DeviceSerial, command: AdbShellCommand, timeout: Duration): ShellResult {
        val result = transport.executeText(AdbDeviceRequest(serial, AdbOperation.Shell(command), timeout))
        return when (val outcome = result.outcome) {
            is AdbOutcome.Completed -> ShellResult.Ran(outcome.exitCode, result.stdout, result.stderr)
            AdbOutcome.TimedOut -> ShellResult.Failed("No answer within ${timeout.inWholeSeconds} seconds.")
            AdbOutcome.Cancelled -> ShellResult.Failed("Cancelled.")
            is AdbOutcome.TransportFailure -> ShellResult.Failed(outcome.reason)
            is AdbOutcome.Unsupported -> ShellResult.Failed(outcome.reason)
        }
    }
}
