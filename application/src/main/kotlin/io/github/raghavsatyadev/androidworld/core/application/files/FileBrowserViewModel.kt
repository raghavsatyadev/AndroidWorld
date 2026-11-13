package io.github.raghavsatyadev.androidworld.core.application.files

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.files.FileScope
import io.github.raghavsatyadev.androidworld.core.domain.files.RemoteEntry
import io.github.raghavsatyadev.androidworld.core.domain.files.RemotePath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The device file browser: one folder at a time, with breadcrumbs, multi-select, pull to a local
 * folder, push, new folder, rename and a confirmed delete. Operations run one at a time ([FileBrowserState.busy]),
 * so a long pull never races an edit. Changing the selected device starts again at the scope's home.
 */
class FileBrowserViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val files: DeviceFileService,
    commandContext: StateFlow<DeviceCommandContext>,
    private val defaultPullFolder: suspend () -> String,
    /** Asks the user; true to go ahead. [title] names the device, [detail] lists the exact paths. */
    private val confirm: suspend (title: String, detail: String) -> Boolean,
) {
    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var opJob: Job? = null

    init {
        scope.launch(dispatchers.io) {
            val folder = runCatching { defaultPullFolder() }.getOrDefault("")
            _state.update { if (it.pullFolder.isEmpty()) it.copy(pullFolder = folder) else it }
        }
        commandContext.map { (it as? DeviceCommandContext.Eligible)?.serial }
            .distinctUntilChanged()
            .onEach { serial ->
                listJob?.cancel()
                opJob?.cancel()
                _state.update {
                    FileBrowserState(serial = serial?.value, pullFolder = it.pullFolder)
                }
                if (serial != null) load(FileScope.Device.home)
            }
            .launchIn(scope)
    }

    fun handle(intent: FileBrowserIntent) {
        when (intent) {
            is FileBrowserIntent.Open -> load(intent.path)
            is FileBrowserIntent.OpenEntry -> openEntry(intent.entry)
            FileBrowserIntent.Up -> load(RemotePath.parent(_state.value.path))
            FileBrowserIntent.Refresh -> load(_state.value.path, keepSelection = true)
            is FileBrowserIntent.Select -> select(intent.name, intent.additive)
            FileBrowserIntent.ClearSelection -> _state.update { it.copy(selected = emptySet()) }
            FileBrowserIntent.PullSelected -> pullSelected()
            is FileBrowserIntent.Push -> push(intent.items)
            is FileBrowserIntent.NewFolder -> newFolder(intent.name.trim())
            is FileBrowserIntent.Rename -> rename(intent.from, intent.to.trim())
            FileBrowserIntent.DeleteSelected -> deleteSelected()
            is FileBrowserIntent.SetPullFolder -> _state.update { it.copy(pullFolder = intent.path.trim()) }
            is FileBrowserIntent.SetScope -> setScope(intent.packageName?.trim()?.ifEmpty { null })
            FileBrowserIntent.DismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun serial() = _state.value.serial?.let(DeviceSerial::of)

    private fun load(path: String, keepSelection: Boolean = false, onNotADirectory: (() -> Unit)? = null) {
        val serial = serial() ?: return
        val scopeNow = _state.value.scope
        val target = RemotePath.normalize(path)
        listJob?.cancel()
        _state.update { it.copy(loading = true, error = null, denied = false) }
        listJob = scope.launch(dispatchers.io) {
            val result = files.list(serial, scopeNow, target)
            if (result is ListResult.NotADirectory && onNotADirectory != null) {
                _state.update { it.copy(loading = false) }
                onNotADirectory()
                return@launch
            }
            _state.update { current ->
                val base = current.copy(loading = false, path = target, scope = scopeNow)
                when (result) {
                    is ListResult.Listed -> base.copy(
                        entries = result.listing.entries,
                        unreadable = result.listing.errors,
                        selected = if (keepSelection && current.path == target) {
                            current.selected intersect result.listing.entries.map { it.name }.toSet()
                        } else {
                            emptySet()
                        },
                    )
                    ListResult.Denied -> base.copy(entries = emptyList(), unreadable = emptyList(), selected = emptySet(), denied = true)
                    ListResult.NotADirectory -> base.copy(entries = emptyList(), selected = emptySet(), error = "$target is not a folder.")
                    is ListResult.Failed -> base.copy(entries = emptyList(), selected = emptySet(), error = result.reason)
                }
            }
        }
    }

    /** Folders open; a symlink is tried as a folder and, if it is a file, gets selected instead. */
    private fun openEntry(entry: RemoteEntry) {
        val path = RemotePath.child(_state.value.path, entry.name)
        when {
            entry.isDirectory -> load(path)
            entry.isLink -> load(path) { _state.update { it.copy(selected = setOf(entry.name)) } }
            else -> select(entry.name, additive = false)
        }
    }

    private fun select(name: String, additive: Boolean) = _state.update {
        val selected = when {
            !additive -> setOf(name)
            name in it.selected -> it.selected - name
            else -> it.selected + name
        }
        it.copy(selected = selected)
    }

    private fun setScope(packageName: String?) {
        val next = try {
            packageName?.let(FileScope::App) ?: FileScope.Device
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(message = FileBrowserMessage(e.message ?: "Not a package name.", isError = true)) }
            return
        }
        _state.update { it.copy(scope = next, entries = emptyList(), selected = emptySet(), message = null) }
        load(next.home)
    }

    private fun pullSelected() {
        val entries = _state.value.selectedEntries.ifEmpty { return }
        val folder = _state.value.pullFolder.ifBlank {
            _state.update { it.copy(message = FileBrowserMessage("Choose a folder to pull into.", isError = true)) }
            return
        }
        operation("Pulling ${count(entries.size)}", refresh = false) { serial, scope, path ->
            val errors = entries.mapNotNull { entry ->
                files.pull(serial, scope, RemotePath.child(path, entry.name), entry.canOpen, folder)
            }
            report(errors, "Pulled ${count(entries.size - errors.size)} to $folder", revealPath = folder)
        }
    }

    private fun push(items: List<LocalItem>) {
        if (items.isEmpty()) return
        operation("Pushing ${count(items.size)}") { serial, scope, path ->
            val errors = items.mapNotNull { files.push(serial, scope, it, path) }
            report(errors, "Pushed ${count(items.size - errors.size)} to $path")
        }
    }

    private fun newFolder(name: String) {
        if (!RemotePath.isValidName(name)) return invalidName(name)
        if (_state.value.entries.any { it.name == name }) return exists(name)
        operation("Creating $name") { serial, scope, path ->
            report(listOfNotNull(files.makeFolder(serial, scope, path, name)), "Created $name")
        }
    }

    private fun rename(from: String, to: String) {
        if (from == to) return
        if (!RemotePath.isValidName(to)) return invalidName(to)
        if (_state.value.entries.any { it.name == to }) return exists(to)
        operation("Renaming $from") { serial, scope, path ->
            report(listOfNotNull(files.rename(serial, scope, path, from, to)), "Renamed $from to $to")
        }
    }

    private fun deleteSelected() {
        val names = _state.value.selectedEntries.map { it.name }.ifEmpty { return }
        val path = _state.value.path
        val paths = names.map { RemotePath.child(path, it) }
        val device = _state.value.serial ?: return
        operation("Waiting for confirmation") { serial, scope, _ ->
            val title = "Delete ${count(names.size)} on $device?"
            val detail = paths.joinToString("\n") + "\n\nFolders are deleted with everything in them. This can't be undone."
            if (!confirm(title, detail)) return@operation null
            _state.update { it.copy(busy = "Deleting ${count(names.size)}") }
            val errors = paths.mapNotNull { files.delete(serial, scope, it) }
            report(errors, "Deleted ${count(names.size - errors.size)}")
        }
    }

    /** Runs one operation at a time; [block] returns the message to show, then the folder reloads. */
    private fun operation(
        label: String,
        refresh: Boolean = true,
        block: suspend (DeviceSerial, FileScope, String) -> FileBrowserMessage?,
    ) {
        val serial = serial() ?: return
        if (_state.value.busy != null) return
        val snapshot = _state.value
        _state.update { it.copy(busy = label, message = null) }
        opJob = scope.launch(dispatchers.io) {
            try {
                val message = block(serial, snapshot.scope, snapshot.path)
                _state.update { it.copy(message = message ?: it.message) }
            } finally {
                _state.update { it.copy(busy = null) }
                if (refresh) load(snapshot.path, keepSelection = true)
            }
        }
    }

    private fun report(errors: List<String>, success: String, revealPath: String? = null): FileBrowserMessage =
        if (errors.isEmpty()) FileBrowserMessage(success, isError = false, revealPath = revealPath)
        else FileBrowserMessage(errors.joinToString("\n"), isError = true, revealPath = revealPath)

    private fun invalidName(name: String) =
        _state.update { it.copy(message = FileBrowserMessage("\"$name\" is not a valid name.", isError = true)) }

    private fun exists(name: String) =
        _state.update { it.copy(message = FileBrowserMessage("\"$name\" already exists here.", isError = true)) }

    private fun count(n: Int) = if (n == 1) "1 item" else "$n items"
}
