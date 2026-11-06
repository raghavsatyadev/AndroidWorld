package io.github.raghavsatyadev.androidworld.core.application.commands

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.commands.DumpsysServices
import io.github.raghavsatyadev.androidworld.core.domain.device.DeviceCommandContext
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import kotlin.time.Duration.Companion.seconds
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
import kotlinx.coroutines.withContext

sealed interface DumpsysIntent {
    data object LoadServices : DumpsysIntent
    data class FilterServices(val query: String) : DumpsysIntent
    data class Dump(val service: String) : DumpsysIntent
    data class SearchDump(val query: String) : DumpsysIntent
    data object Cancel : DumpsysIntent
}

data class DumpsysState(
    val serial: String? = null,
    val services: List<String> = emptyList(),
    val loadingServices: Boolean = false,
    val servicesError: String? = null,
    val serviceQuery: String = "",
    val service: String? = null,
    val dumping: Boolean = false,
    val dumpError: String? = null,
    val lines: List<String> = emptyList(),
    val search: String = "",
    /** Indexes into [lines] that match [search]; every line when the search is empty. */
    val visible: List<Int> = emptyList(),
) {
    val shownServices: List<String>
        get() = if (serviceQuery.isBlank()) services else services.filter { it.contains(serviceQuery.trim(), ignoreCase = true) }
}

/**
 * Lists the services `dumpsys` can dump on the selected device and shows one service's dump, split
 * into lines and searchable, since dumps such as `package` or `batterystats` run to megabytes.
 */
class DumpsysViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val runner: ShellCommandRunner,
    commandContext: StateFlow<DeviceCommandContext>,
) {
    private val _state = MutableStateFlow(DumpsysState())
    val state: StateFlow<DumpsysState> = _state.asStateFlow()

    private var listJob: Job? = null
    private var dumpJob: Job? = null
    private var searchJob: Job? = null

    init {
        commandContext.map { (it as? DeviceCommandContext.Eligible)?.serial }
            .distinctUntilChanged()
            .onEach { serial ->
                listOfNotNull(listJob, dumpJob, searchJob).forEach(Job::cancel)
                _state.value = DumpsysState(serial = serial?.value, serviceQuery = _state.value.serviceQuery)
                if (serial != null) loadServices()
            }
            .launchIn(scope)
    }

    fun handle(intent: DumpsysIntent) {
        when (intent) {
            DumpsysIntent.LoadServices -> loadServices()
            is DumpsysIntent.FilterServices -> _state.update { it.copy(serviceQuery = intent.query) }
            is DumpsysIntent.Dump -> dump(intent.service)
            is DumpsysIntent.SearchDump -> search(intent.query)
            DumpsysIntent.Cancel -> dumpJob?.cancel()
        }
    }

    private fun serial() = _state.value.serial?.let(DeviceSerial::of)

    private fun loadServices() {
        val serial = serial() ?: return
        listJob?.cancel()
        _state.update { it.copy(loadingServices = true, servicesError = null) }
        listJob = scope.launch(dispatchers.io) {
            val result = runner.run(serial, DumpsysServices.list, 15.seconds)
            _state.update {
                when (result) {
                    is ShellResult.Ran -> it.copy(loadingServices = false, services = DumpsysServices.parse(result.stdout))
                    is ShellResult.Failed -> it.copy(loadingServices = false, servicesError = result.reason)
                }
            }
        }
    }

    private fun dump(service: String) {
        val serial = serial() ?: return
        dumpJob?.cancel()
        _state.update { it.copy(service = service, dumping = true, dumpError = null, lines = emptyList(), visible = emptyList()) }
        dumpJob = scope.launch(dispatchers.io) {
            try {
                when (val result = runner.run(serial, DumpsysServices.dump(service), 90.seconds)) {
                    is ShellResult.Ran -> {
                        val lines = (result.stdout.ifBlank { result.stderr }).trimEnd().lines()
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
