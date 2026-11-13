package io.github.raghavsatyadev.androidworld.core.application.files

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTextResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.files.FileCommands
import io.github.raghavsatyadev.androidworld.core.domain.files.FileScope
import io.github.raghavsatyadev.androidworld.core.domain.files.LsParser
import io.github.raghavsatyadev.androidworld.core.domain.files.PrivateFileReader
import io.github.raghavsatyadev.androidworld.core.domain.files.RemoteListing
import io.github.raghavsatyadev.androidworld.core.domain.files.RemotePath
import kotlin.random.Random

/** A file or folder on this computer, as picked or dropped in the UI. */
data class LocalItem(val path: String, val isDirectory: Boolean) {
    val name: String get() = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
}

sealed interface ListResult {
    data class Listed(val listing: RemoteListing) : ListResult
    data object Denied : ListResult
    data object NotADirectory : ListResult
    data class Failed(val reason: String) : ListResult
}

/**
 * The file browser's device operations. Every call returns null on success or a message that can be
 * shown as is. Device scope uses `adb push`/`pull` (whole folders included); app scope goes through
 * `run-as`, which can only copy single files, so folders are refused there with a clear reason.
 */
class DeviceFileService(
    private val transport: AdbTransport,
    private val privateReader: PrivateFileReader,
    private val stagingName: () -> String = { "aw-push-" + Random.nextLong().toULong().toString(16) },
) {
    suspend fun list(serial: DeviceSerial, scope: FileScope, path: String): ListResult {
        val result = transport.executeText(FileCommands.list(serial, scope, path))
        val outcome = result.outcome
        if (outcome !is AdbOutcome.Completed) return ListResult.Failed(outcome.describe())
        // Without shell_v2 stderr arrives on stdout, so both go to the parser.
        val listing = LsParser.parse(result.stdout + "\n" + result.stderr)
        return when {
            listing.notADirectory -> ListResult.NotADirectory
            listing.permissionDenied -> ListResult.Denied
            listing.entries.isEmpty() && outcome.exitCode.let { it != null && it != 0 } ->
                ListResult.Failed(listing.errors.joinToString("\n").ifEmpty { "ls exited with ${outcome.exitCode}" })
            else -> ListResult.Listed(listing)
        }
    }

    suspend fun makeFolder(serial: DeviceSerial, scope: FileScope, parent: String, name: String): String? =
        guard { transport.executeText(FileCommands.makeFolder(serial, scope, RemotePath.child(parent, name))).error() }

    suspend fun rename(serial: DeviceSerial, scope: FileScope, parent: String, from: String, to: String): String? = guard {
        transport.executeText(FileCommands.rename(serial, scope, RemotePath.child(parent, from), RemotePath.child(parent, to))).error()
    }

    suspend fun delete(serial: DeviceSerial, scope: FileScope, path: String): String? =
        guard { transport.executeText(FileCommands.delete(serial, scope, path)).error() }

    suspend fun pull(serial: DeviceSerial, scope: FileScope, remote: String, isFolder: Boolean, localFolder: String): String? = guard {
        when (scope) {
            FileScope.Device -> transport.executeText(FileCommands.pull(serial, remote, localFolder)).error()
            is FileScope.App ->
                if (isFolder) "${RemotePath.name(remote)}: app data can only be copied one file at a time."
                else privateReader.copyToLocal(serial, scope, remote, localFolder)
        }
    }

    suspend fun push(serial: DeviceSerial, scope: FileScope, local: LocalItem, remoteFolder: String): String? = guard {
        when (scope) {
            FileScope.Device -> transport.executeText(FileCommands.push(serial, local.path, remoteFolder)).error()
            is FileScope.App ->
                if (local.isDirectory) "${local.name}: only single files can be copied into app data."
                else pushIntoApp(serial, scope, local, remoteFolder)
        }
    }

    /** `adb push` can't write app data, so stage in /data/local/tmp and `cp` as the app's uid. */
    private suspend fun pushIntoApp(serial: DeviceSerial, scope: FileScope.App, local: LocalItem, remoteFolder: String): String? {
        val staged = FileCommands.STAGING_DIR + stagingName()
        return try {
            transport.executeText(FileCommands.pushFile(serial, local.path, staged)).error()
                ?: transport.executeText(FileCommands.copyIntoApp(serial, scope, staged, RemotePath.child(remoteFolder, local.name))).error()
        } finally {
            transport.executeText(FileCommands.removeStaged(serial, staged))
        }
    }

    private inline fun guard(block: () -> String?): String? = try {
        block()
    } catch (e: IllegalArgumentException) {
        e.message ?: "Not allowed."
    }
}

internal fun AdbOutcome.describe(): String = when (this) {
    is AdbOutcome.Completed -> "exit code $exitCode"
    AdbOutcome.TimedOut -> "The device did not answer in time."
    AdbOutcome.Cancelled -> "Cancelled."
    is AdbOutcome.TransportFailure -> reason
    is AdbOutcome.Unsupported -> reason
}

/** null when the command exited 0 with nothing on stderr that reads as a failure. */
internal fun AdbTextResult.error(): String? {
    val outcome = outcome
    if (outcome !is AdbOutcome.Completed) return outcome.describe()
    val failed = outcome.exitCode.let { it != null && it != 0 }
    if (!failed) return null
    return (stderr.trim() + "\n" + stdout.trim()).trim().ifEmpty { outcome.describe() }
}
