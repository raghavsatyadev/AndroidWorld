package io.github.raghavsatyadev.androidworld.core.application.content

import io.github.raghavsatyadev.androidworld.core.application.commands.ShellCommandRunner
import io.github.raghavsatyadev.androidworld.core.application.commands.ShellResult
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.content.ContentQueryResult
import io.github.raghavsatyadev.androidworld.core.domain.content.CsvFormat
import io.github.raghavsatyadev.androidworld.core.domain.content.PimTable
import io.github.raghavsatyadev.androidworld.core.domain.content.PimTableData
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
import kotlinx.datetime.TimeZone

sealed interface ContentViewerIntent {
    data class Select(val table: PimTable) : ContentViewerIntent
    data object Refresh : ContentViewerIntent
    data class Search(val query: String) : ContentViewerIntent
}

/** Why a table shows no rows, when it doesn't. */
sealed interface ContentNotice {
    data object NoDevice : ContentNotice
    data object Empty : ContentNotice
    data class PermissionDenied(val message: String) : ContentNotice
    data class ProviderMissing(val message: String) : ContentNotice
    data class Failed(val message: String) : ContentNotice
}

data class ContentViewerState(
    val serial: String? = null,
    val table: PimTable = PimTable.Contacts,
    val loading: Boolean = false,
    val notice: ContentNotice? = ContentNotice.NoDevice,
    val data: PimTableData = PimTableData.None,
    val search: String = "",
    /** Indexes into [data] rows matching [search]; every row when the search is empty. */
    val visible: List<Int> = emptyList(),
) {
    /** The rows on screen (search applied) as CSV, headers first. */
    fun csv(): String = CsvFormat.format(PimTableData(data.headers, visible.map { data.rows[it] }))

    /** A file name such as `emulator-5554-sms-inbox.csv`. */
    fun csvFileName(): String =
        "${serial ?: "device"}-${table.label.lowercase().replace(' ', '-')}.csv".replace(Regex("[^A-Za-z0-9._-]"), "_")
}

/**
 * Read-only viewer for the device's contacts, SMS, call log and calendar, read through
 * `content query` as the shell user. Missing permissions and missing or empty providers are shown
 * as a [ContentNotice] instead of an error dump; search runs over the loaded rows on the host.
 */
class ContentViewerViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val runner: ShellCommandRunner,
    commandContext: StateFlow<DeviceCommandContext>,
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    private val _state = MutableStateFlow(ContentViewerState())
    val state: StateFlow<ContentViewerState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null

    init {
        commandContext.map { (it as? DeviceCommandContext.Eligible)?.serial }
            .distinctUntilChanged()
            .onEach { serial ->
                loadJob?.cancel()
                searchJob?.cancel()
                val old = _state.value
                _state.value = ContentViewerState(
                    serial = serial?.value, table = old.table, search = old.search,
                    notice = if (serial == null) ContentNotice.NoDevice else null,
                )
                if (serial != null) load()
            }
            .launchIn(scope)
    }

    fun handle(intent: ContentViewerIntent) {
        when (intent) {
            is ContentViewerIntent.Select -> if (intent.table != _state.value.table || _state.value.notice != null) {
                _state.update { it.copy(table = intent.table) }
                load()
            }
            ContentViewerIntent.Refresh -> load()
            is ContentViewerIntent.Search -> search(intent.query)
        }
    }

    private fun load() {
        val serial = _state.value.serial?.let(DeviceSerial::of) ?: return
        val table = _state.value.table
        loadJob?.cancel()
        _state.update { it.copy(loading = true, notice = null, data = PimTableData.None, visible = emptyList()) }
        loadJob = scope.launch(dispatchers.io) {
            val (data, notice) = when (val ran = runner.run(serial, table.spec.command(), 60.seconds)) {
                is ShellResult.Failed -> PimTableData.None to ContentNotice.Failed(ran.reason)
                is ShellResult.Ran -> when (val result = table.spec.parse(ran.stdout, ran.stderr)) {
                    is ContentQueryResult.Rows -> table.table(result.rows, zone()).let { it to if (it.rows.isEmpty()) ContentNotice.Empty else null }
                    ContentQueryResult.Empty -> PimTableData.None to ContentNotice.Empty
                    is ContentQueryResult.PermissionDenied -> PimTableData.None to ContentNotice.PermissionDenied(result.message)
                    is ContentQueryResult.ProviderMissing -> PimTableData.None to ContentNotice.ProviderMissing(result.message)
                    is ContentQueryResult.Error -> PimTableData.None to ContentNotice.Failed(result.message)
                }
            }
            val visible = withContext(dispatchers.default) { matching(data, _state.value.search) }
            _state.update { if (it.table == table) it.copy(loading = false, data = data, notice = notice, visible = visible) else it }
        }
    }

    private fun search(query: String) {
        _state.update { it.copy(search = query) }
        searchJob?.cancel()
        searchJob = scope.launch {
            val data = _state.value.data
            val visible = withContext(dispatchers.default) { matching(data, query) }
            _state.update { if (it.data === data && it.search == query) it.copy(visible = visible) else it }
        }
    }

    private fun matching(data: PimTableData, query: String): List<Int> {
        val q = query.trim()
        return if (q.isEmpty()) data.rows.indices.toList()
        else data.rows.indices.filter { i -> data.rows[i].any { it.contains(q, ignoreCase = true) } }
    }
}
