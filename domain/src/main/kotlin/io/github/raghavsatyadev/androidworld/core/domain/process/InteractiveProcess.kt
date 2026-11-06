package io.github.raghavsatyadev.androidworld.core.domain.process

import kotlinx.coroutines.flow.Flow

/**
 * Starts a long-running process whose stdin stays open, such as `adb shell` for the terminal.
 * [ProcessExecutor] runs a command to completion; this keeps one running and feeds it lines.
 */
fun interface InteractiveProcessLauncher {
    /** Starts [command] with stderr merged into stdout. Throws if the process cannot start. */
    fun launch(command: ProcessCommand): InteractiveProcess
}

/** A running process: its merged output, line by line, and its stdin. */
interface InteractiveProcess {
    /**
     * Output lines without line endings, in order. A final line with no newline is emitted when
     * the process exits. The flow completes when the process exits and can be collected once.
     */
    val output: Flow<String>

    /** Writes [text] to stdin as UTF-8 and flushes. Throws once the process has exited. */
    suspend fun write(text: String)

    /** Ends the process and its children. Safe to call more than once. */
    fun close()
}
