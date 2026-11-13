package io.github.raghavsatyadev.androidworld.core.application.sysconfig

import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.application.commands.ShellResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.PropertyKind
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.SystemProperty
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.SystemPropertyCommands
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

sealed interface PropertiesIntent {
    data object Load : PropertiesIntent
    data class Filter(val query: String) : PropertiesIntent
    data class Set(val name: String, val value: String) : PropertiesIntent
    data object DismissResult : PropertiesIntent
}

data class PropertiesState(
    val serial: String? = null,
    val properties: List<SystemProperty> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    /** True when adbd runs as root, so `ro.*` writes can be tried; null until known. */
    val root: Boolean? = null,
    val writing: Boolean = false,
    val result: WriteResult? = null,
) {
    val shown: List<SystemProperty>
        get() = query.trim().let { q ->
            if (q.isEmpty()) properties
            else properties.filter { it.name.contains(q, ignoreCase = true) || it.value.contains(q, ignoreCase = true) }
        }

    /** Why [name] can't be edited on this device, or null when it can be tried. */
    fun lockReason(name: String): String? = when (PropertyKind.of(name)) {
        PropertyKind.ReadOnly -> if (root == true) null else "ro.* properties need a root shell (adb root on an emulator or userdebug build)."
        PropertyKind.Control -> "ctl.* properties start and stop services; use the Shell tab for that."
        else -> null
    }
}

/**
 * Lists every system property of the selected device, filters by name or value, and writes one with
 * `setprop` after the user confirms. The write is read back with `getprop` to tell whether it took.
 */
class PropertiesViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val runner: ShellCommandRunner,
    commandContext: StateFlow<DeviceCommandContext>,
    private val confirm: SysConfigConfirm,
) {
    private val _state = MutableStateFlow(PropertiesState())
    val state: StateFlow<PropertiesState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var writeJob: Job? = null

    init {
        commandContext.eligibleSerial()
            .onEach { serial ->
                listOfNotNull(loadJob, writeJob).forEach(Job::cancel)
                _state.value = PropertiesState(serial = serial?.value, query = _state.value.query)
                if (serial != null) load()
            }
            .launchIn(scope)
    }

    fun handle(intent: PropertiesIntent) {
        when (intent) {
            PropertiesIntent.Load -> load()
            is PropertiesIntent.Filter -> _state.update { it.copy(query = intent.query) }
            is PropertiesIntent.Set -> set(intent.name.trim(), intent.value)
            PropertiesIntent.DismissResult -> _state.update { it.copy(result = null) }
        }
    }

    private fun serial() = _state.value.serial?.let(DeviceSerial::of)

    private fun load() {
        val serial = serial() ?: return
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch(dispatchers.io) {
            val list = runner.run(serial, SystemPropertyCommands.list, 15.seconds)
            val id = runner.run(serial, SystemPropertyCommands.userId, 10.seconds)
            val root = (id as? ShellResult.Ran)?.stdout?.trim()?.let { it == "0" }
            _state.update {
                when (list) {
                    is ShellResult.Ran -> it.copy(loading = false, properties = SystemPropertyCommands.parse(list.stdout), root = root)
                    is ShellResult.Failed -> it.copy(loading = false, error = list.reason, root = root)
                }
            }
        }
    }

    private fun set(name: String, value: String) {
        val serial = serial() ?: return
        val invalid = SystemPropertyCommands.validate(name, value) ?: _state.value.lockReason(name)
        if (invalid != null) {
            _state.update { it.copy(result = WriteResult.Rejected(invalid)) }
            return
        }
        writeJob?.cancel()
        writeJob = scope.launch(dispatchers.io) {
            val command = SystemPropertyCommands.set(name, value)
            val note = when (PropertyKind.of(name)) {
                PropertyKind.ReadOnly -> "ro.* properties are write-once: init rejects a new value for one that is already set."
                PropertyKind.Persistent -> "persist.* properties are kept across reboots."
                else -> "The value is lost on reboot."
            }
            if (!confirm(SysConfigWrite("Set $name on ${serial.value}?", command.render(), note))) return@launch
            _state.update { it.copy(writing = true, result = null) }
            try {
                val ran = runner.run(serial, command, 15.seconds)
                val readBack = runner.run(serial, SystemPropertyCommands.get(name), 10.seconds)
                val now = (readBack as? ShellResult.Ran)?.stdout?.let(SystemPropertyCommands::parseValue)
                val result = when {
                    now == value -> WriteResult.Applied("$name is now \"$value\".")
                    ran is ShellResult.Failed -> WriteResult.Rejected("setprop failed: ${ran.reason}")
                    ran is ShellResult.Ran -> WriteResult.Rejected("$name didn't change.${failureDetail(ran.stdout, ran.stderr)}")
                    else -> WriteResult.Rejected("$name didn't change.")
                }
                _state.update { state ->
                    val props = if (now == null) state.properties else upsert(state.properties, SystemProperty(name, now))
                    state.copy(properties = props, result = result)
                }
            } finally {
                _state.update { it.copy(writing = false) }
            }
        }
    }

    private fun upsert(list: List<SystemProperty>, prop: SystemProperty): List<SystemProperty> =
        if (prop.value.isEmpty() && list.none { it.name == prop.name }) list
        else (list.filterNot { it.name == prop.name } + prop).sortedBy { it.name }
}
