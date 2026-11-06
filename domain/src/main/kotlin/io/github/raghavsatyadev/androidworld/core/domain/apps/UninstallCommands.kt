package io.github.raghavsatyadev.androidworld.core.domain.apps

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbOperation

/** Exact host-side uninstall command from `design/IMPLEMENTATION.md` §4. */
object UninstallCommands {
    fun uninstall(packageName: String): AdbOperation.Host =
        AdbOperation.Host(listOf("uninstall", packageName))
}
