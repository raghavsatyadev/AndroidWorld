package io.github.raghavsatyadev.androidworld.core.application.sysconfig

import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.application.commands.ShellResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.commands.DumpsysServices
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.ServiceListCommand
import io.github.raghavsatyadev.androidworld.core.domain.sysconfig.SystemService
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ServicesIntent {
    data object Load : ServicesIntent
    data class Filter(val query: String) : ServicesIntent
    data class Open(val name: String) : ServicesIntent
    data class SearchDump(val query: String) : ServicesIntent
    data object Cancel : ServicesIntent
}

/** One row: a binder service and whether `dumpsys -l` lists it (only those can be dumped). */
data class ServiceRow(val service: SystemService, val dumpable: Boolean) {
    val name: String get() = service.name
}

data class ServicesState(
    val serial: String? = null,
    val rows: List<ServiceRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val selected: String? = null,
    val dumping: Boolean = false,
    val dumpError: String? = null,
    val lines: List<String> = emptyList(),
    val search: String = "",
    /** Indexes into [lines] that match [search]; every line when the search is empty. */
    val visible: List<Int> = emptyList(),
) {
    val shown: List<ServiceRow>
        get() = query.trim().let { q ->
            if (q.isEmpty()) rows
            else rows.filter { it.name.contains(q, ignoreCase = true) || it.service.interfaceName?.contains(q, ignoreCase = true) == true }
        }
}

/**
 * Running services from `service list` (with their binder interface), merged with `dumpsys -l`, and
 * the chosen service's `dumpsys` output, searchable line by line.
 */
class ServicesViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val runner: ShellCommandRunner,
    commandContext: StateFlow<DeviceCommandContext>,
) {
    private val _state = MutableStateFlow(ServicesState())
    val state: StateFlow<ServicesState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var dumpJob: Job? = null
    private var searchJob: Job? = null

    init {
        commandContext.eligibleSerial()
            .onEach { serial ->
                listOfNotNull(loadJob, dumpJob, searchJob).forEach(Job::cancel)
                _state.value = ServicesState(serial = serial?.value, query = _state.value.query)
                if (serial != null) load()
            }
            .launchIn(scope)
    }

    fun handle(intent: ServicesIntent) {
        when (intent) {
            ServicesIntent.Load -> load()
            is ServicesIntent.Filter -> _state.update { it.copy(query = intent.query) }
            is ServicesIntent.Open -> open(intent.name)
            is ServicesIntent.SearchDump -> search(intent.query)
            ServicesIntent.Cancel -> dumpJob?.cancel()
        }
    }

    private fun serial() = _state.value.serial?.let(DeviceSerial::of)

    private fun load() {
        val serial = serial() ?: return
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch(dispatchers.io) {
            val dumpable = async { runner.run(serial, DumpsysServices.list, 15.seconds) }
            val listed = runner.run(serial, ServiceListCommand.list, 15.seconds)
            val dumpNames = (dumpable.await() as? ShellResult.Ran)?.let { DumpsysServices.parse(it.stdout).toSet() } ?: emptySet()
            _state.update {
                when (listed) {
                    is ShellResult.Ran -> it.copy(loading = false, rows = merge(ServiceListCommand.parse(listed.stdout), dumpNames))
                    is ShellResult.Failed -> it.copy(loading = false, error = listed.reason)
                }
            }
        }
    }

    /** `service list` rows, plus any service only `dumpsys -l` knows (it can list more on some builds). */
    private fun merge(services: List<SystemService>, dumpNames: Set<String>): List<ServiceRow> {
        val known = services.map { it.name }.toSet()
        val extra = (dumpNames - known).map { SystemService(it, null) }
        return (services + extra).sortedBy { it.name.lowercase() }.map { ServiceRow(it, it.name in dumpNames) }
    }

    private fun open(name: String) {
        val serial = serial() ?: return
        dumpJob?.cancel()
        _state.update { it.copy(selected = name, dumping = true, dumpError = null, lines = emptyList(), visible = emptyList()) }
        dumpJob = scope.launch(dispatchers.io) {
            try {
                when (val result = runner.run(serial, DumpsysServices.dump(name), 90.seconds)) {
                    is ShellResult.Ran -> {
                        val lines = result.stdout.ifBlank { result.stderr }.trimEnd().lines()
                        _state.update { it.copy(lines = lines, visible = matching(lines, it.search)) }
                    }
                    is ShellResult.Failed -> _state.update { it.copy(dumpError = result.reason) }
                }
            } finally {
                _state.update { it.copy(dumping = false) }
            }
        }
    }

    private fun search(query: String) {
        _state.update { it.copy(search = query) }
        searchJob?.cancel()
        searchJob = scope.launch {
            val lines = _state.value.lines
            val visible = withContext(dispatchers.default) { matching(lines, query) }
            _state.update { if (it.lines === lines && it.search == query) it.copy(visible = visible) else it }
        }
    }

    private fun matching(lines: List<String>, query: String): List<Int> =
        if (query.isBlank()) lines.indices.toList() else lines.indices.filter { lines[it].contains(query.trim(), ignoreCase = true) }
}
