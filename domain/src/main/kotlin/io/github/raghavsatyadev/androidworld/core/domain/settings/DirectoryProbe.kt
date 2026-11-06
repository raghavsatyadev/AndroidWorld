package io.github.raghavsatyadev.androidworld.core.domain.settings

/** Checks whether [path] points to an existing directory on the host filesystem — the only place
 * settings validation touches the filesystem for the capture-directory field, never inline in
 * `:domain` or `:intellij`. Mirrors [io.github.raghavsatyadev.androidworld.core.domain.discovery.ExecutableFileProbe]'s
 * shape. */
interface DirectoryProbe {
    suspend fun isValidDirectory(path: String): Boolean
}
