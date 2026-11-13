package io.github.raghavsatyadev.androidworld.core.application.sysconfig

import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.application.commands.ShellResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbShellCommand
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.DeviceSetting
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.DeviceSettingsCommands
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.SettingsNamespace
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DeviceSettingsIntent {
    data object Load : DeviceSettingsIntent
    data class SelectNamespace(val namespace: SettingsNamespace) : DeviceSettingsIntent
    data class Filter(val query: String) : DeviceSettingsIntent
    data class Put(val key: String, val value: String) : DeviceSettingsIntent
    data class Delete(val key: String) : DeviceSettingsIntent
    data object DismissResult : DeviceSettingsIntent
}

data class DeviceSettingsState(
    val serial: String? = null,
    val namespace: SettingsNamespace = SettingsNamespace.Global,
    val entries: List<DeviceSetting> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val writing: Boolean = false,
    val result: WriteResult? = null,
) {
    val shown: List<DeviceSetting>
        get() = query.trim().let { q ->
            if (q.isEmpty()) entries
            else entries.filter { it.key.contains(q, ignoreCase = true) || it.value.contains(q, ignoreCase = true) }
        }
}

/**
 * Browses the `system`, `secure` and `global` settings tables of the selected device and puts or
 * deletes one key after the user confirms. Every write is read back with `settings get`.
 */
class DeviceSettingsViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val runner: ShellCommandRunner,
    commandContext: StateFlow<DeviceCommandContext>,
    private val confirm: SysConfigConfirm,
) {
    private val _state = MutableStateFlow(DeviceSettingsState())
    val state: StateFlow<DeviceSettingsState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var writeJob: Job? = null

    init {
        commandContext.eligibleSerial()
            .onEach { serial ->
                listOfNotNull(loadJob, writeJob).forEach(Job::cancel)
                val old = _state.value
                _state.value = DeviceSettingsState(serial = serial?.value, namespace = old.namespace, query = old.query)
                if (serial != null) load()
            }
            .launchIn(scope)
    }

    fun handle(intent: DeviceSettingsIntent) {
        when (intent) {
            DeviceSettingsIntent.Load -> load()
            is DeviceSettingsIntent.SelectNamespace -> if (intent.namespace != _state.value.namespace) {
                _state.update { it.copy(namespace = intent.namespace, entries = emptyList(), result = null) }
                load()
            }
            is DeviceSettingsIntent.Filter -> _state.update { it.copy(query = intent.query) }
            is DeviceSettingsIntent.Put -> put(intent.key.trim(), intent.value)
            is DeviceSettingsIntent.Delete -> delete(intent.key)
            DeviceSettingsIntent.DismissResult -> _state.update { it.copy(result = null) }
        }
    }

    private fun serial() = _state.value.serial?.let(DeviceSerial::of)

    private fun load() {
        val serial = serial() ?: return
        val namespace = _state.value.namespace
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch(dispatchers.io) {
            val result = runner.run(serial, DeviceSettingsCommands.list(namespace), 15.seconds)
            _state.update {
                if (it.namespace != namespace) it
                else when (result) {
                    is ShellResult.Ran -> it.copy(loading = false, entries = DeviceSettingsCommands.parseList(result.stdout))
                    is ShellResult.Failed -> it.copy(loading = false, error = result.reason)
                }
            }
        }
    }

    private fun put(key: String, value: String) {
        DeviceSettingsCommands.validateKey(key)?.let { reason ->
            _state.update { it.copy(result = WriteResult.Rejected(reason)) }
            return
        }
        val namespace = _state.value.namespace
        write(namespace, key, DeviceSettingsCommands.put(namespace, key, value), "Set ${namespace.cli} $key", expected = value)
    }

    private fun delete(key: String) {
        val namespace = _state.value.namespace
        write(namespace, key, DeviceSettingsCommands.delete(namespace, key), "Delete ${namespace.cli} $key", expected = null)
    }

    /** Runs [command] after confirmation, then reads [key] back; [expected] null means it should be gone. */
    private fun write(namespace: SettingsNamespace, key: String, command: AdbShellCommand, title: String, expected: String?) {
        val serial = serial() ?: return
        writeJob?.cancel()
        writeJob = scope.launch(dispatchers.io) {
            if (!confirm(SysConfigWrite("$title on ${serial.value}?", command.render(), DeviceSettingsCommands.riskNote(key)))) return@launch
            _state.update { it.copy(writing = true, result = null) }
            try {
                val ran = runner.run(serial, command, 15.seconds)
                val readBack = runner.run(serial, DeviceSettingsCommands.get(namespace, key), 10.seconds) as? ShellResult.Ran
                val now = readBack?.let { DeviceSettingsCommands.parseGet(it.stdout) }
                val result = when {
                    readBack != null && now == expected ->
                        WriteResult.Applied(if (expected == null) "Deleted ${namespace.cli} $key." else "${namespace.cli} $key is now \"$expected\".")
                    ran is ShellResult.Failed -> WriteResult.Rejected("settings failed: ${ran.reason}")
                    ran is ShellResult.Ran -> WriteResult.Rejected("${namespace.cli} $key didn't change.${failureDetail(ran.stdout, ran.stderr)}")
                    else -> WriteResult.Rejected("Couldn't read ${namespace.cli} $key back.")
                }
                _state.update { state ->
                    if (state.namespace != namespace || readBack == null) return@update state.copy(result = result)
                    val others = state.entries.filterNot { it.key == key }
                    val entries = if (now == null) others else (others + DeviceSetting(key, now)).sortedBy { it.key.lowercase() }
                    state.copy(entries = entries, result = result)
                }
            } finally {
                _state.update { it.copy(writing = false) }
            }
        }
    }
}
