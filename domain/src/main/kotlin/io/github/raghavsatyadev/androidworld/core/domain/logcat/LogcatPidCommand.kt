package io.github.raghavsatyadev.androidworld.core.domain.logcat

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellValue

/**
 * Builds the fixed `pidof <pkg>` shell command line (`design/IMPLEMENTATION.md` §4's "logcat by
 * pkg" reference) used to resolve a selected package's process id(s) for task 035's client-side
 * package filter. [packageName] is runtime data, so it is always wrapped in
 * [ShellToken.Value]`(`[ShellValue.of]`)`, matching [io.github.raghavsatyadev.androidworld.core.domain.apps.AppLifecycleCommands]'s
 * established shape for the same `<pkg>` argument.
 */
object LogcatPidCommand {
    fun pidOf(packageName: String): AdbShellCommand = AdbShellCommand.of(
        ShellToken.Literal("pidof"),
        ShellToken.Value(ShellValue.of(packageName)),
    )
}
