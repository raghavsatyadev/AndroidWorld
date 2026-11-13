package io.github.raghavsatyadev.androidworld.core.adapters.jvm.process

import io.github.raghavsatyadev.androidworld.core.adapters.jvm.discovery.mapOsName
import io.github.raghavsatyadev.androidworld.core.domain.discovery.OperatingSystem

/**
 * Utilities for preparing process command line arguments on Windows hosts.
 *
 * On Windows, `java.lang.ProcessBuilder` builds the `CreateProcess` command line without escaping
 * double quotes inside an argument, so the child process's MSVC argv parser drops them. For example,
 * an argument like `input text 'x"y'` reaches `adb.exe` as `input text 'xy'`.
 *
 * Pre-escaping arguments according to MSVC argv parsing rules before passing them to ProcessBuilder
 * preserves inner double quotes. Batch files (.bat and .cmd) are excluded because `cmd.exe` does not
 * use MSVC rules.
 */
internal object WindowsArguments {

    /**
     * True when the running JVM is on Windows: mapOsName(System.getProperty("os.name") ?: "") ==
     * OperatingSystem.Windows (reuse io.github.raghavsatyadev.androidworld.core.adapters.jvm.discovery.mapOsName
     * and core.domain.discovery.OperatingSystem).
     */
    val isWindowsHost: Boolean by lazy {
        mapOsName(System.getProperty("os.name") ?: "") == OperatingSystem.Windows
    }

    /**
     * Escapes `"` for the MSVC argv rules: a run of N backslashes followed by `"` becomes 2N+1
     * backslashes then `"`. Backslashes not followed by `"` are left alone. Outer quotes are NOT
     * added (ProcessBuilder adds them for arguments with spaces).
     */
    fun escape(argument: String): String {
        if (!argument.contains('"')) return argument
        val result = StringBuilder(argument.length + 8)
        var backslashCount = 0
        for (i in 0 until argument.length) {
            val c = argument[i]
            when (c) {
                '\\' -> {
                    backslashCount++
                }
                '"' -> {
                    repeat(2 * backslashCount + 1) {
                        result.append('\\')
                    }
                    result.append('"')
                    backslashCount = 0
                }
                else -> {
                    repeat(backslashCount) {
                        result.append('\\')
                    }
                    backslashCount = 0
                    result.append(c)
                }
            }
        }
        repeat(backslashCount) {
            result.append('\\')
        }
        return result.toString()
    }

    /**
     * Full argv for ProcessBuilder. When onWindows is true and the executable does not end with .bat
     * or .cmd (case-insensitive; cmd.exe does not use MSVC rules), every argument goes through
     * escape(); the executable itself is never changed. Otherwise returns listOf(executable) +
     * arguments unchanged.
     */
    fun argv(executable: String, arguments: List<String>, onWindows: Boolean): List<String> {
        val isBatch = executable.endsWith(".bat", ignoreCase = true) || executable.endsWith(".cmd", ignoreCase = true)
        return if (onWindows && !isBatch) {
            listOf(executable) + arguments.map(::escape)
        } else {
            listOf(executable) + arguments
        }
    }
}
