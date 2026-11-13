package io.github.raghavsatyadev.androidworld.core.application.files

import io.github.raghavsatyadev.androidworld.core.domain.files.Crumb
import io.github.raghavsatyadev.androidworld.core.domain.files.FileScope
import io.github.raghavsatyadev.androidworld.core.domain.files.RemoteEntry
import io.github.raghavsatyadev.androidworld.core.domain.files.RemotePath

sealed interface FileBrowserIntent {
    data class Open(val path: String) : FileBrowserIntent
    data class OpenEntry(val entry: RemoteEntry) : FileBrowserIntent
    data object Up : FileBrowserIntent
    data object Refresh : FileBrowserIntent
    data class Select(val name: String, val additive: Boolean) : FileBrowserIntent
    data object ClearSelection : FileBrowserIntent
    data object PullSelected : FileBrowserIntent
    data class Push(val items: List<LocalItem>) : FileBrowserIntent
    data class NewFolder(val name: String) : FileBrowserIntent
    data class Rename(val from: String, val to: String) : FileBrowserIntent
    data object DeleteSelected : FileBrowserIntent
    data class SetPullFolder(val path: String) : FileBrowserIntent
    /** Browse a debuggable app's private data through run-as; null goes back to the device file system. */
    data class SetScope(val packageName: String?) : FileBrowserIntent
    data object DismissMessage : FileBrowserIntent
}

/** A result line under the toolbar; [revealPath] is a local folder the UI can offer to open. */
data class FileBrowserMessage(val text: String, val isError: Boolean, val revealPath: String? = null)

data class FileBrowserState(
    val serial: String? = null,
    val scope: FileScope = FileScope.Device,
    val path: String = FileScope.Device.home,
    val entries: List<RemoteEntry> = emptyList(),
    /** Lines `ls` reported for single entries it could not read. */
    val unreadable: List<String> = emptyList(),
    val loading: Boolean = false,
    val denied: Boolean = false,
    val error: String? = null,
    val selected: Set<String> = emptySet(),
    /** What is running now, e.g. "Pulling 3 items"; edits are disabled while set. */
    val busy: String? = null,
    val message: FileBrowserMessage? = null,
    /** Where pulls go; starts as the screenshot folder from settings. */
    val pullFolder: String = "",
) {
    val crumbs: List<Crumb> get() = RemotePath.crumbs(path)
    val canGoUp: Boolean get() = path != RemotePath.ROOT
    val selectedEntries: List<RemoteEntry> get() = entries.filter { it.name in selected }
    val idle: Boolean get() = busy == null && serial != null
}
