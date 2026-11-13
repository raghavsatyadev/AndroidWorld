package io.github.raghavsatyadev.androidworld.core.application.sdk

import io.github.raghavsatyadev.androidworld.core.application.emulator.CliResult
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessCommand
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessExecutor
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutcome
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessRequest
import io.github.raghavsatyadev.androidworld.core.domain.process.executeBuffered
import io.github.raghavsatyadev.androidworld.core.domain.sdk.SdkListParser
import io.github.raghavsatyadev.androidworld.core.domain.sdk.SystemImage
import io.github.raghavsatyadev.androidworld.core.domain.sdk.SystemImages
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * System image management through Google's `android` CLI and AVD creation via `avdmanager`.
 */
class SystemImagesCli(
    private val processes: ProcessExecutor,
    private val executable: () -> String?,
    private val sdkRoot: () -> String?,
    private val avdManager: () -> String?,
) {
    val available: Boolean get() = executable() != null && sdkRoot() != null

    /** Lists system images; null when the CLI is missing or the command fails. */
    suspend fun list(): List<SystemImage>? {
        val (exit, out) = text(listOf("sdk", "list", "--all"), 3.minutes) ?: return null
        if (exit != 0) return null
        return SystemImages.merge(SdkListParser.parse(out))
    }

    /** Installs the system image at [path]. */
    suspend fun install(path: String): CliResult =
        runSdkCommand(listOf("sdk", "install", path), 2.hours)

    /** Removes the system image at [path]. */
    suspend fun remove(path: String): CliResult =
        runSdkCommand(listOf("sdk", "remove", path), 5.minutes)

    /** Creates an AVD using `avdmanager`. */
    suspend fun createAvd(name: String, imagePackageId: String, deviceId: String): CliResult {
        val exe = avdManager()
            ?: return CliResult.Failed("avdmanager wasn't found in the Android SDK (cmdline-tools).")
        val isBat = exe.endsWith(".bat", ignoreCase = true) || exe.endsWith(".cmd", ignoreCase = true)
        val pkgArg = if (isBat) "\"$imagePackageId\"" else imagePackageId
        val args = listOf("create", "avd", "-n", name, "-k", pkgArg, "-d", deviceId)
        val env = sdkRoot()?.let {
            mapOf("ANDROID_HOME" to it, "ANDROID_SDK_ROOT" to it)
        }.orEmpty()

        val request = ProcessRequest(
            command = ProcessCommand(executable = exe, arguments = args, environment = env),
            timeout = 5.minutes,
        )
        val result = processes.executeBuffered(request)
        val exit = (result.outcome as? ProcessOutcome.Completed)?.exitCode ?: -1
        val reason = (result.outcome as? ProcessOutcome.StartFailure)?.reason
        val out = listOfNotNull(result.stdout, result.stderr, reason).joinToString("\n")
        val failure = SdkListParser.failure(out)
        return if (exit == 0 && failure == null) {
            CliResult.Ok
        } else {
            CliResult.Failed(failure ?: lastLine(out) ?: "avdmanager failed (exit $exit).")
        }
    }

    private suspend fun runSdkCommand(subArgs: List<String>, timeout: Duration): CliResult {
        val (exit, out) = text(subArgs, timeout) ?: return missing
        val failure = SdkListParser.failure(out)
        return if (exit == 0 && failure == null) {
            CliResult.Ok
        } else {
            CliResult.Failed(failure ?: lastLine(out) ?: "The android CLI failed (exit $exit).")
        }
    }

    private suspend fun text(subArgs: List<String>, timeout: Duration): Pair<Int, String>? {
        val exe = executable() ?: return null
        val root = sdkRoot() ?: return null
        val args = listOf("--sdk=$root") + subArgs
        val result = processes.executeBuffered(ProcessRequest(ProcessCommand(exe, args), timeout = timeout))
        val exit = (result.outcome as? ProcessOutcome.Completed)?.exitCode ?: -1
        val reason = (result.outcome as? ProcessOutcome.StartFailure)?.reason
        return exit to listOfNotNull(result.stdout, result.stderr, reason).joinToString("\n")
    }

    private fun lastLine(text: String): String? =
        SdkListParser.strip(text).lines().map { it.trim() }.lastOrNull { it.isNotEmpty() }

    private companion object {
        val missing = CliResult.Failed("Google's android CLI isn't installed.")
    }
}

/** Pure helpers, unit-tested. */
object AvdNames {
    /**
     * avdmanager device id for an `android emulator create` profile: small_phone/medium_phone/medium_tablet unchanged,
     * small_desktop -> desktop_small, medium_desktop -> desktop_medium, large_desktop -> desktop_large,
     * any profile containing "foldable" -> "7.6in Foldable", anything else -> medium_phone.
     */
    fun deviceFor(profile: String): String = when {
        profile in setOf("small_phone", "medium_phone", "medium_tablet") -> profile
        profile == "small_desktop" -> "desktop_small"
        profile == "medium_desktop" -> "desktop_medium"
        profile == "large_desktop" -> "desktop_large"
        profile.contains("foldable", ignoreCase = true) -> "7.6in Foldable"
        else -> "medium_phone"
    }

    /**
     * A new AVD id like "Medium_Phone_API_37.2" (profile words capitalised, joined by '_', + "_API_" + apiLevel,
     * + "_ext"+N when extension != null); characters outside [A-Za-z0-9._-] become '_'; if taken (case-insensitive) in
     * [existing], append "_2", "_3", …
     */
    fun unique(profile: String, image: SystemImage, existing: Collection<String>): String {
        val formattedProfile = profile
            .split('_', '-', ' ')
            .filter { it.isNotEmpty() }
            .joinToString("_") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        val extSuffix = if (image.extension != null) "_ext${image.extension}" else ""
        val candidate = "${formattedProfile}_API_${image.apiLevel}$extSuffix"
        val baseName = candidate.replace(Regex("[^A-Za-z0-9._-]"), "_")

        val taken = existing.map { it.lowercase() }.toSet()
        if (baseName.lowercase() !in taken) return baseName

        var suffix = 2
        while ("${baseName}_$suffix".lowercase() in taken) {
            suffix++
        }
        return "${baseName}_$suffix"
    }
}
