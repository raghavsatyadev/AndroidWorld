package io.github.raghavsatyadev.androidworld.core.domain.process

/**
 * Whether a process's stdout/stderr should be decoded as incremental UTF-8 text (line events) or
 * passed through as raw, binary-safe byte chunks. A single request never mixes the two — text and
 * binary output must never be conflated.
 */
sealed interface ProcessOutputKind {
    data object Text : ProcessOutputKind

    data object Binary : ProcessOutputKind

    /**
     * For a launcher that starts a long-lived child and exits, like `android emulator start`. Output is
     * discarded rather than piped (a child holding the pipe would keep the read open forever), only
     * [ProcessEvent.Completed] is emitted, and children still running after a normal exit are left
     * alone. Cancellation or a timeout still ends the whole tree.
     */
    data object Detached : ProcessOutputKind
}
