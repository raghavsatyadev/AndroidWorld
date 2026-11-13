package io.github.raghavsatyadev.androidworld.core.domain.sysconfig

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellValue

/** How a system property behaves when written, read from its name prefix. */
enum class PropertyKind {
    /** `ro.*`: set once at boot; only root can try, and init still rejects a value that is already set. */
    ReadOnly,

    /** `persist.*`: written to /data and kept across reboots. */
    Persistent,

    /** `ctl.*`: not a value at all but a command to init (start/stop a service). */
    Control,
    Normal,
    ;

    companion object {
        fun of(name: String): PropertyKind = when {
            name.startsWith("ro.") -> ReadOnly
            name.startsWith("persist.") -> Persistent
            name.startsWith("ctl.") -> Control
            else -> Normal
        }
    }
}

data class SystemProperty(val name: String, val value: String) {
    val kind: PropertyKind get() = PropertyKind.of(name)
}

/**
 * `getprop` / `setprop` on the device. Names and values always go through [ShellValue] quoting.
 * Android limits non-`ro.*` values to [MAX_VALUE_LENGTH] bytes (PROP_VALUE_MAX - 1).
 */
object SystemPropertyCommands {
    const val MAX_VALUE_LENGTH = 91

    private val NAME = Regex("[A-Za-z0-9_.:@-]+")

    val list: AdbShellCommand = AdbShellCommand.of(ShellToken.Literal("getprop"))

    /** `id -u` prints 0 when adbd runs as root (`adb root` on userdebug and emulator images). */
    val userId: AdbShellCommand = AdbShellCommand.of(ShellToken.Literal("id"), ShellToken.Literal("-u"))

    fun get(name: String): AdbShellCommand =
        AdbShellCommand.of(ShellToken.Literal("getprop"), ShellToken.Value(ShellValue.of(name)))

    fun set(name: String, value: String): AdbShellCommand = AdbShellCommand.of(
        ShellToken.Literal("setprop"),
        ShellToken.Value(ShellValue.of(name)),
        ShellToken.Value(ShellValue.of(value)),
    )

    /** Why [name] = [value] can't be written, or null when it can be tried. */
    fun validate(name: String, value: String): String? = when {
        name.isBlank() -> "Enter a property name."
        !NAME.matches(name) -> "Property names use letters, digits and . _ - : @ only."
        PropertyKind.of(name) == PropertyKind.Control -> "ctl.* properties start and stop services; use the Shell tab for that."
        PropertyKind.of(name) != PropertyKind.ReadOnly && value.encodeToByteArray().size > MAX_VALUE_LENGTH ->
            "Values are limited to $MAX_VALUE_LENGTH bytes (this one has ${value.encodeToByteArray().size})."
        else -> null
    }

    /**
     * Properties from `getprop`, one `[name]: [value]` per entry. A value may span lines (for example
     * `persist.sys.boot.reason.history`), so lines are joined until one ends with `]`.
     */
    fun parse(output: String): List<SystemProperty> {
        val props = mutableListOf<SystemProperty>()
        var pending: StringBuilder? = null
        for (raw in output.lineSequence()) {
            val line = raw.trimEnd('\r')
            val buffer = pending
            if (buffer != null) {
                buffer.append('\n').append(line)
            } else if (line.startsWith("[")) {
                pending = StringBuilder(line)
            } else {
                continue
            }
            val entry = pending!!.toString()
            if (entry.endsWith("]")) {
                parseEntry(entry)?.let(props::add)
                pending = null
            }
        }
        return props.distinctBy { it.name }.sortedBy { it.name }
    }

    private fun parseEntry(entry: String): SystemProperty? {
        val split = entry.indexOf("]: [")
        if (split < 1) return null
        return SystemProperty(name = entry.substring(1, split), value = entry.substring(split + 4, entry.length - 1))
    }

    /** `getprop name` prints the value followed by a newline, or an empty line when it isn't set. */
    fun parseValue(output: String): String = output.removeSuffix("\n").removeSuffix("\r")
}
