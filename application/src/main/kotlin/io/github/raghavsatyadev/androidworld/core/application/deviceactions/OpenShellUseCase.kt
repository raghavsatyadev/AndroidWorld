package io.github.raghavsatyadev.androidworld.core.application.deviceactions

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.deviceactions.DeviceActionResult
import io.github.raghavsatyadev.androidworld.core.domain.deviceactions.ShellSessionIntent
import io.github.raghavsatyadev.androidworld.core.domain.deviceactions.TerminalLaunchResult
import io.github.raghavsatyadev.androidworld.core.domain.deviceactions.TerminalLauncher
import io.github.raghavsatyadev.androidworld.core.domain.discovery.DiscoveryError
import io.github.raghavsatyadev.androidworld.core.domain.discovery.DiscoveryOutcome
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolId
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolLocator

/**
 * Runs task 016's Open-shell basic action (ADR 0007): resolves `adb`'s real, validated executable
 * path via [toolLocator] — never a hardcoded `"adb"` literal, since a configured/SDK path can
 * differ from whatever is on `PATH` — then asks [terminalLauncher] to pre-seed a new terminal tab
 * with the resulting [ShellSessionIntent]. Both a tool-discovery failure and a terminal-adapter
 * ("shell-adapter") failure are preserved as a truthful [DeviceActionResult.Failure], never a
 * silent no-op, per ADR 0007.
 */
class OpenShellUseCase(
    private val toolLocator: ToolLocator,
    private val terminalLauncher: TerminalLauncher,
) {
    suspend fun openShell(serial: DeviceSerial): DeviceActionResult {
        return when (val outcome = toolLocator.locate(ToolId.Adb)) {
            is DiscoveryOutcome.Found -> {
                val intent = ShellSessionIntent(serial, outcome.tool.path.value)
                when (val launch = terminalLauncher.openShell(intent)) {
                    TerminalLaunchResult.Launched -> DeviceActionResult.Success
                    is TerminalLaunchResult.Unavailable -> DeviceActionResult.Failure(launch.reason)
                }
            }
            is DiscoveryOutcome.Failed -> DeviceActionResult.Failure(describe(outcome.error))
        }
    }

    private fun describe(error: DiscoveryError): String = when (error) {
        is DiscoveryError.ToolNotFound -> "adb was not found"
        is DiscoveryError.ExecutableInvalid -> error.reason
        is DiscoveryError.VersionQueryFailed -> error.reason
    }
}
