package io.github.raghavsatyadev.androidworld.core.application.commands

import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandCategory
import io.github.raghavsatyadev.androidworld.core.domain.commands.CommandEntry

sealed interface CommandBrowserIntent {
    data class Search(val query: String) : CommandBrowserIntent
    data class FilterCategory(val category: CommandCategory?) : CommandBrowserIntent

    /** Opens one entry (or closes it with `null`) and reads its current value. */
    data class Open(val id: String?) : CommandBrowserIntent
    data class SetField(val id: String, val index: Int, val value: String) : CommandBrowserIntent
    data class ReadCurrent(val id: String) : CommandBrowserIntent
    data class Run(val id: String) : CommandBrowserIntent
    data class Cancel(val id: String) : CommandBrowserIntent
    data class ClearOutput(val id: String) : CommandBrowserIntent
}

/** The value an entry changes, as last read from the device. */
sealed interface CurrentValue {
    data object Reading : CurrentValue
    data class Known(val text: String) : CurrentValue
    data class Unknown(val reason: String) : CurrentValue
}

/** The last time an entry's action ran: the exact command line, and how it went. */
data class CommandRun(
    val commandLine: String,
    val running: Boolean,
    val output: String = "",
    val failed: Boolean = false,
    val exitCode: Int? = null,
)

data class CommandBrowserState(
    val serial: String? = null,
    val query: String = "",
    val category: CommandCategory? = null,
    val entries: List<CommandEntry> = emptyList(),
    val openId: String? = null,
    val values: Map<String, List<String>> = emptyMap(),
    val currents: Map<String, CurrentValue> = emptyMap(),
    val runs: Map<String, CommandRun> = emptyMap(),
) {
    val categories: List<CommandCategory> get() = CommandCategory.entries

    fun valuesOf(entry: CommandEntry): List<String> = values[entry.id] ?: entry.defaults()

    fun problems(entry: CommandEntry): Map<Int, String> = entry.problems(entry.action, valuesOf(entry))

    fun canRun(entry: CommandEntry): Boolean = serial != null && runs[entry.id]?.running != true && problems(entry).isEmpty()

    fun canReadCurrent(entry: CommandEntry): Boolean {
        val current = entry.current ?: return false
        return serial != null && entry.problems(current, valuesOf(entry)).isEmpty()
    }

    fun preview(entry: CommandEntry): String = entry.action.preview(entry.fields, valuesOf(entry))
}
