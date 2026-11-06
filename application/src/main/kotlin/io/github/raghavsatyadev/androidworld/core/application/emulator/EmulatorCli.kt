package io.github.raghavsatyadev.androidworld.core.application.emulator

import io.github.raghavsatyadev.androidworld.core.domain.emulator.Avd
import io.github.raghavsatyadev.androidworld.core.domain.emulator.AvdConfig
import io.github.raghavsatyadev.androidworld.core.domain.emulator.AvdListParser
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessCommand
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessEvent
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessExecutor
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutcome
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutputKind
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessRequest
import io.github.raghavsatyadev.androidworld.core.domain.process.executeBuffered
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.last

/** Reads an AVD's `config.ini`; null when the file is missing or unreadable. */
fun interface AvdConfigSource {
    suspend fun read(avdId: String): AvdConfig?
}

/** The result of one `android emulator` command. [message] is the CLI's own words when it failed. */
sealed interface CliResult {
    data object Ok : CliResult

    data class Failed(val message: String) : CliResult
}

/**
 * Emulator lifecycle through Google's `android` CLI (`android emulator list|start|stop|create|remove`),
 * so AVD handling stays the SDK's own. [executable] is null when the CLI isn't installed.
 */
class EmulatorCli(
    private val processes: ProcessExecutor,
    private val executable: () -> String?,
    private val configs: AvdConfigSource,
) {
    val available: Boolean get() = executable() != null

    /** Every AVD with its running state; null when the CLI is missing or its output was not a listing. */
    suspend fun list(): List<Avd>? {
        val result = text(listOf("emulator", "list", "--long"), 30.seconds) ?: return null
        if (result.first != 0) return null
        return AvdListParser.parse(result.second).map { avd ->
            configs.read(avd.id)?.let { avd.copy(formFactor = it.formFactor, device = it.device) } ?: avd
        }
    }

    suspend fun profiles(): List<String> =
        text(listOf("emulator", "create", "--list-profiles"), 30.seconds)
            ?.takeIf { it.first == 0 }?.let { AvdListParser.parseProfiles(it.second) }.orEmpty()

    /** Boots [avdId] and returns once it is ready. Output is detached: the emulator outlives this call. */
    suspend fun start(avdId: String, headless: Boolean, cold: Boolean): CliResult {
        val exe = executable() ?: return missing
        val args = buildList {
            addAll(listOf("emulator", "start"))
            if (headless) add("--headless")
            if (cold) add("--cold")
            add(avdId)
        }
        val outcome = processes.execute(ProcessRequest(ProcessCommand(exe, args), ProcessOutputKind.Detached, timeout = 5.minutes))
            .filterIsInstance<ProcessEvent.Completed>().last().outcome
        return when (outcome) {
            is ProcessOutcome.Completed -> if (outcome.exitCode == 0) CliResult.Ok else CliResult.Failed("The emulator didn't start (exit ${outcome.exitCode}).")
            ProcessOutcome.TimedOut -> CliResult.Failed("The emulator took over 5 minutes to boot and was stopped.")
            is ProcessOutcome.StartFailure -> CliResult.Failed(outcome.reason)
        }
    }

    suspend fun stop(serialOrId: String): CliResult = simple(listOf("emulator", "stop", serialOrId), 2.minutes)

    /** Creates an AVD from a device [profile]; the CLI downloads a system image first when none fits. */
    suspend fun create(profile: String): CliResult = simple(listOf("emulator", "create", profile), 30.minutes)

    suspend fun remove(avdId: String): CliResult = simple(listOf("emulator", "remove", avdId), 1.minutes)

    private suspend fun simple(args: List<String>, timeout: Duration): CliResult {
        val (exit, out) = text(args, timeout) ?: return missing
        return if (exit == 0) CliResult.Ok else CliResult.Failed(lastLine(out) ?: "The android CLI failed (exit $exit).")
    }

    /** Exit code and combined output, or null when the CLI is missing. Timeouts and start failures read as exit -1. */
    private suspend fun text(args: List<String>, timeout: Duration): Pair<Int, String>? {
        val exe = executable() ?: return null
        val result = processes.executeBuffered(ProcessRequest(ProcessCommand(exe, args), timeout = timeout))
        val exit = (result.outcome as? ProcessOutcome.Completed)?.exitCode ?: -1
        val reason = (result.outcome as? ProcessOutcome.StartFailure)?.reason
        return exit to listOfNotNull(result.stdout, result.stderr, reason).joinToString("\n")
    }

    private fun lastLine(text: String): String? =
        AvdListParser.strip(text).lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }

    private companion object {
        val missing = CliResult.Failed("Google's android CLI isn't installed.")
    }
}
