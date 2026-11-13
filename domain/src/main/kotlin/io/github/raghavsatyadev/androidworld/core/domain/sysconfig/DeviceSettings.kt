package io.github.raghavsatyadev.androidworld.core.domain.sysconfig

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellToken
import io.github.raghavsatyadev.androidworld.core.domain.adb.ShellValue

/** The three `settings` tables. [cli] is the word the `settings` tool takes. */
enum class SettingsNamespace(val cli: String, val label: String) {
    System("system", "System"),
    Secure("secure", "Secure"),
    Global("global", "Global"),
}

data class DeviceSetting(val key: String, val value: String)

/** `settings list|get|put|delete` for one namespace. Keys and values always go through [ShellValue] quoting. */
object DeviceSettingsCommands {
    private val WHITESPACE = Regex("\\s")

    /** Keys that cut adb, hide developer options or the network when changed; their confirmation says so. */
    private val RISKY = mapOf(
        "adb_enabled" to "Turning adb off disconnects this app from the device.",
        "adb_wifi_enabled" to "Changing wireless debugging can disconnect a device paired over Wi-Fi.",
        "development_settings_enabled" to "Turning developer options off also turns adb off on most devices.",
        "airplane_mode_on" to "Airplane mode cuts the network (and adb over Wi-Fi).",
        "wifi_on" to "Turning Wi-Fi off disconnects a device paired over Wi-Fi.",
        "device_provisioned" to "Unprovisioned devices lock the home screen and status bar.",
        "user_setup_complete" to "A user marked as not set up is sent back to the setup wizard.",
        "lockscreen.disabled" to "This changes the lock screen.",
    )

    fun list(namespace: SettingsNamespace): AdbShellCommand =
        AdbShellCommand.of(ShellToken.Literal("settings"), ShellToken.Literal("list"), ShellToken.Literal(namespace.cli))

    fun get(namespace: SettingsNamespace, key: String): AdbShellCommand = AdbShellCommand.of(
        ShellToken.Literal("settings"), ShellToken.Literal("get"), ShellToken.Literal(namespace.cli),
        ShellToken.Value(ShellValue.of(key)),
    )

    fun put(namespace: SettingsNamespace, key: String, value: String): AdbShellCommand = AdbShellCommand.of(
        ShellToken.Literal("settings"), ShellToken.Literal("put"), ShellToken.Literal(namespace.cli),
        ShellToken.Value(ShellValue.of(key)), ShellToken.Value(ShellValue.of(value)),
    )

    fun delete(namespace: SettingsNamespace, key: String): AdbShellCommand = AdbShellCommand.of(
        ShellToken.Literal("settings"), ShellToken.Literal("delete"), ShellToken.Literal(namespace.cli),
        ShellToken.Value(ShellValue.of(key)),
    )

    /** Why [key] can't be used, or null when it can. */
    fun validateKey(key: String): String? = when {
        key.isBlank() -> "Enter a key."
        WHITESPACE.containsMatchIn(key) -> "Keys can't contain spaces."
        '=' in key -> "Keys can't contain '='."
        else -> null
    }

    /** A warning for keys that can break the connection or the device's state, or null. */
    fun riskNote(key: String): String? = RISKY[key]

    /**
     * Entries from `settings list`: `key=value`, split on the first `=`. A line without `=` continues
     * the previous value (values may hold newlines).
     */
    fun parseList(output: String): List<DeviceSetting> {
        val entries = mutableListOf<DeviceSetting>()
        for (raw in output.lineSequence()) {
            val line = raw.trimEnd('\r')
            val eq = line.indexOf('=')
            when {
                eq > 0 -> entries += DeviceSetting(line.substring(0, eq), line.substring(eq + 1))
                entries.isNotEmpty() && line.isNotEmpty() ->
                    entries[entries.lastIndex] = entries.last().let { it.copy(value = it.value + "\n" + line) }
            }
        }
        return entries.distinctBy { it.key }.sortedBy { it.key.lowercase() }
    }

    /** `settings get` prints the literal `null` for a missing key; that reads back as null here. */
    fun parseGet(output: String): String? {
        val value = output.removeSuffix("\n").removeSuffix("\r")
        return if (value == "null") null else value
    }
}
