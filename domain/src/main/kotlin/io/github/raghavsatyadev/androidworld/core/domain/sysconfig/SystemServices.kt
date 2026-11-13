package io.github.raghavsatyadev.androidworld.core.domain.sysconfig

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken

/** A binder service from `service list`; [interfaceName] is null when it publishes no descriptor. */
data class SystemService(val name: String, val interfaceName: String?)

/** `service list`: every binder service registered with servicemanager, with its interface. */
object ServiceListCommand {
    val list: AdbShellCommand = AdbShellCommand.of(ShellToken.Literal("service"), ShellToken.Literal("list"))

    private val LINE = Regex("""^\s*\d+\s+(\S+?):\s*\[(.*)]\s*$""")

    /** Lines such as `1\tSurfaceFlinger: [android.ui.ISurfaceComposer]`, after a `Found N services:` header. */
    fun parse(output: String): List<SystemService> = output.lineSequence()
        .mapNotNull { LINE.find(it.trimEnd('\r')) }
        .map { SystemService(it.groupValues[1], it.groupValues[2].trim().ifEmpty { null }) }
        .distinctBy { it.name }
        .sortedBy { it.name.lowercase() }
        .toList()
}
