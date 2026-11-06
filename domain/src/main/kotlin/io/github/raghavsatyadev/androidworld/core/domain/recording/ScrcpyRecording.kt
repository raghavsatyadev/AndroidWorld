package io.github.raghavsatyadev.androidworld.core.domain.recording

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellValue
import io.github.raghavsatyadev.androidworld.core.domain.discovery.ToolVersion

private val LEADING_VERSION = Regex("""^(\d+)\.(\d+)""")
private val SERVER_ID = Regex("""com\.genymobile\.scrcpy\.Server\b.*?\bscid=([0-9a-fA-F]+)""")

/**
 * Screen recording through scrcpy instead of `screenrecord`, which Android stops after 180 s.
 *
 * scrcpy finalizes the MP4 only when it shuts down cleanly. On Windows the JVM can only hard-kill a
 * child process, which leaves a file with no index, so a recording is stopped by ending its server
 * on the device instead ([stopServer]): the client sees the device disconnect, writes the index and
 * exits by itself. Each server is told apart by the `scid=` argument the client gives it.
 */
object ScrcpyRecording {

    /** `--no-window` (scrcpy 2.4+) keeps scrcpy from opening a mirror window while it records. */
    fun supports(version: ToolVersion): Boolean {
        val match = LEADING_VERSION.find(version.raw) ?: return false
        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        return major > 2 || (major == 2 && minor >= 4)
    }

    /** The format is forced because [outputPath] is a temp file whose extension is not `.mp4`. */
    fun arguments(serial: DeviceSerial, outputPath: String): List<String> = listOf(
        "-s", serial.value,
        "--no-window", "--no-playback",
        "--record=$outputPath", "--record-format=mp4",
    )

    /** `ps -A -o ARGS`: every process's command line, to find running scrcpy servers. */
    fun listProcesses(): AdbShellCommand = AdbShellCommand.of(
        ShellToken.Literal("ps"), ShellToken.Literal("-A"), ShellToken.Literal("-o"), ShellToken.Literal("ARGS"),
    )

    /** The `scid` of every scrcpy server in [psOutput] (the output of [listProcesses]). */
    fun serverIds(psOutput: String): Set<String> =
        psOutput.lineSequence().mapNotNull { SERVER_ID.find(it)?.groupValues?.get(1) }.toSet()

    /** `pkill -f scid=<id>`: ends one scrcpy server so its client finishes the recording. */
    fun stopServer(scid: String): AdbShellCommand = AdbShellCommand.of(
        ShellToken.Literal("pkill"), ShellToken.Literal("-f"), ShellToken.Value(ShellValue.of("scid=$scid")),
    )
}
