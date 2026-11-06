package io.github.raghavsatyadev.androidworld.core.domain.commands

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellValue

/**
 * The services `dumpsys` can dump, read live from the device rather than from a fixed list, so every
 * Android version shows exactly what it has.
 */
object DumpsysServices {
    val list: AdbShellCommand = AdbShellCommand.of(ShellToken.Literal("dumpsys"), ShellToken.Literal("-l"))

    fun dump(service: String): AdbShellCommand =
        AdbShellCommand.of(ShellToken.Literal("dumpsys"), ShellToken.Value(ShellValue.of(service)))

    /** Service names from `dumpsys -l`: the indented lines after its header, sorted, without duplicates. */
    fun parse(output: String): List<String> = output.lineSequence()
        .filter { it.startsWith(" ") || it.startsWith("\t") }
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.contains(' ') }
        .distinct()
        .sortedBy { it.lowercase() }
        .toList()
}
