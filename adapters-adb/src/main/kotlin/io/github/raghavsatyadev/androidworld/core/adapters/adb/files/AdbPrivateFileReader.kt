package io.github.raghavsatyadev.androidworld.core.adapters.adb.files

import io.github.raghavsatyadev.androidworld.core.domain.files.PrivateFileReader
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOutcome
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.files.FileCommands
import io.github.raghavsatyadev.androidworld.core.domain.files.FileScope
import io.github.raghavsatyadev.androidworld.core.domain.files.RemotePath
import java.io.File

/**
 * Copies one app-private file to this computer with `adb exec-out run-as <pkg> cat <path>`. It
 * writes to a `.part` file and renames it at the end, so a failed copy never leaves a file that
 * looks complete. `cat` errors arrive on the same stream as the bytes, so a short output that reads
 * as a run-as or cat error is reported instead of saved.
 */
class AdbPrivateFileReader(private val transport: AdbTransport) : PrivateFileReader {
    override suspend fun copyToLocal(serial: DeviceSerial, scope: FileScope.App, remote: String, localFolder: String): String? {
        val name = RemotePath.name(remote)
        val folder = File(localFolder).apply { mkdirs() }
        val target = File(folder, name)
        val part = File(folder, "$name.part")
        val outcome = part.outputStream().buffered().use { out ->
            transport.executeBinary(FileCommands.readPrivate(serial, scope, remote)) { bytes -> out.write(bytes) }
        }
        val failure = when {
            outcome !is AdbOutcome.Completed -> outcome.toString()
            outcome.exitCode != null && outcome.exitCode != 0 -> "exit code ${outcome.exitCode}"
            else -> errorText(part)
        }
        if (failure != null) {
            part.delete()
            return "$name: $failure"
        }
        if (target.exists()) target.delete()
        return if (part.renameTo(target)) null else "$name: could not write ${target.absolutePath}"
    }

    private fun errorText(file: File): String? {
        if (file.length() > MAX_ERROR_LENGTH) return null
        val text = file.readText().trim()
        return text.takeIf { ERROR.containsMatchIn(it) }
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 400L
        val ERROR = Regex("""^(run-as|cat): .*(not debuggable|unknown package|No such file|Permission denied|Is a directory|could not)""")
    }
}
