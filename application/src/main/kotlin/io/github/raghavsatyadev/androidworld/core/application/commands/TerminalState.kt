package io.github.raghavsatyadev.androidworld.core.application.commands

sealed interface TerminalIntent {
    /** Opens the shell if none is running (the screen sends it when it is shown). */
    data object Start : TerminalIntent
    data class EditInput(val text: String) : TerminalIntent
    /** Runs the input line, or sends it to the running command's stdin while one is running. */
    data object Submit : TerminalIntent
    data object HistoryPrevious : TerminalIntent
    data object HistoryNext : TerminalIntent
    /** Ends the shell (and whatever it runs) and opens a new one. */
    data object Restart : TerminalIntent
    data object Clear : TerminalIntent
}

enum class TerminalStatus { NoDevice, Idle, Starting, Ready, Running, Ended }

sealed interface TerminalLine {
    data class Command(val prompt: String, val text: String) : TerminalLine
    data class Output(val text: String) : TerminalLine
    data class Notice(val text: String, val isError: Boolean = false) : TerminalLine
}

data class TerminalState(
    val serial: String? = null,
    val status: TerminalStatus = TerminalStatus.NoDevice,
    val directory: String = "/",
    val lastExitCode: Int? = null,
    val lines: List<TerminalLine> = emptyList(),
    val input: String = "",
    val history: List<String> = emptyList(),
) {
    val prompt: String get() = "${serial ?: "device"}:$directory \$"

    /** Typing is allowed at the prompt and while a command runs (its stdin gets the line). */
    val canSubmit: Boolean get() = status == TerminalStatus.Ready || status == TerminalStatus.Running
}
