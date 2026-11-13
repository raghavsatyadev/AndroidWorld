package io.github.raghavsatyadev.androidworld.core.application.sdk

import io.github.raghavsatyadev.androidworld.core.application.emulator.CliResult
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import io.github.raghavsatyadev.androidworld.core.domain.sdk.SystemImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Snapshot/sweep of what an interrupted install leaves in the SDK's download cache; the desktop implements it with java.io.File. */
interface PartialDownloads {
    fun snapshot(): Set<String>

    /** Deletes partial-download files that appeared since [before]. */
    fun sweep(before: Set<String>)
}

/** Names of AVDs whose system image is [image]; used to warn before deleting it. */
fun interface ImageUsers {
    suspend fun avdsUsing(image: SystemImage): List<String>
}

/** Asks before an image is deleted; [usedBy] lists AVDs that would stop working. */
fun interface ImageRemovalConfirmation {
    suspend fun confirm(image: SystemImage, usedBy: List<String>): Boolean
}

enum class ImageTaskKind { Installing, Removing }

data class ImageTask(val image: SystemImage, val kind: ImageTaskKind)

data class ImageMessage(val text: String, val isError: Boolean)

data class SystemImagesState(
    val cliMissing: Boolean = false,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val images: List<SystemImage> = emptyList(),
    val hostAbis: Set<String> = emptySet(),
    val showAllAbis: Boolean = false,
    val task: ImageTask? = null,
    val message: ImageMessage? = null,
) {
    val installed: List<SystemImage> get() = images.filter { it.installed }

    /** Not-installed images, limited to [hostAbis] unless [showAllAbis] or hostAbis is empty. */
    val downloadable: List<SystemImage>
        get() = images.filter { !it.installed && (showAllAbis || hostAbis.isEmpty() || it.abi in hostAbis) }
}

sealed interface SystemImagesIntent {
    data object Load : SystemImagesIntent
    data object Refresh : SystemImagesIntent
    data class SetShowAllAbis(val show: Boolean) : SystemImagesIntent
    data class Install(val image: SystemImage) : SystemImagesIntent
    data class Remove(val image: SystemImage) : SystemImagesIntent
    data object Cancel : SystemImagesIntent
    data object DismissMessage : SystemImagesIntent
}

/**
 * Manages system image discovery, installation, and removal via [SystemImagesCli].
 */
class SystemImagesViewModel(
    private val scope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val cli: SystemImagesCli,
    private val downloads: PartialDownloads,
    private val users: ImageUsers,
    private val confirmRemoval: ImageRemovalConfirmation,
    hostAbis: Set<String>,
) {
    private val _state = MutableStateFlow(SystemImagesState(hostAbis = hostAbis))
    val state: StateFlow<SystemImagesState> = _state.asStateFlow()

    private var activeJob: Job? = null

    fun handle(intent: SystemImagesIntent) {
        when (intent) {
            SystemImagesIntent.Load -> load(force = false)
            SystemImagesIntent.Refresh -> load(force = true)
            is SystemImagesIntent.SetShowAllAbis -> _state.update { it.copy(showAllAbis = intent.show) }
            is SystemImagesIntent.Install -> install(intent.image)
            is SystemImagesIntent.Remove -> remove(intent.image)
            SystemImagesIntent.Cancel -> cancel()
            SystemImagesIntent.DismissMessage -> _state.update { it.copy(message = null) }
        }
    }

    private fun load(force: Boolean) {
        if (_state.value.loading || (!force && _state.value.loaded)) return
        if (!cli.available) {
            _state.update { it.copy(cliMissing = true, loading = false) }
            return
        }
        _state.update { it.copy(loading = true, cliMissing = false) }
        scope.launch(dispatchers.io) {
            try {
                val list = cli.list()
                _state.update { current ->
                    if (list == null) {
                        current.copy(
                            message = ImageMessage("Couldn't read the system image list from the android CLI.", isError = true),
                        )
                    } else {
                        current.copy(
                            loaded = true,
                            images = list,
                        )
                    }
                }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    private fun install(image: SystemImage) {
        if (_state.value.task != null) return
        _state.update { it.copy(task = ImageTask(image, ImageTaskKind.Installing), message = null) }
        val before = downloads.snapshot()
        activeJob = scope.launch(dispatchers.io) {
            try {
                when (val result = cli.install(image.path)) {
                    is CliResult.Ok -> {
                        val list = cli.list()
                        if (list != null) {
                            _state.update { it.copy(images = list, loaded = true) }
                        }
                        val installed = list?.any { it.path == image.path && it.installed } == true
                        val msg = if (installed) {
                            ImageMessage("Installed ${image.apiLabel} ${image.tagLabel} (${image.abi}).", isError = false)
                        } else {
                            ImageMessage("The android CLI finished, but ${image.apiLabel} ${image.tagLabel} isn't installed.", isError = true)
                        }
                        _state.update { it.copy(message = msg) }
                    }
                    is CliResult.Failed -> {
                        _state.update { it.copy(message = ImageMessage(result.message, isError = true)) }
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    downloads.sweep(before)
                    _state.update {
                        it.copy(
                            task = null,
                            message = ImageMessage("Download cancelled; partial files were removed.", isError = false),
                        )
                    }
                    val list = cli.list()
                    if (list != null) {
                        _state.update { it.copy(images = list, loaded = true) }
                    }
                }
                throw e
            } finally {
                _state.update { it.copy(task = null) }
                activeJob = null
            }
        }
    }

    private fun cancel() {
        activeJob?.cancel()
    }

    private fun remove(image: SystemImage) {
        if (_state.value.task != null) return
        scope.launch(dispatchers.io) {
            val usedBy = users.avdsUsing(image)
            if (!confirmRemoval.confirm(image, usedBy)) return@launch

            var claimed = false
            _state.update { current ->
                if (current.task != null) {
                    current
                } else {
                    claimed = true
                    current.copy(task = ImageTask(image, ImageTaskKind.Removing), message = null)
                }
            }
            if (!claimed) return@launch

            try {
                when (val result = cli.remove(image.path)) {
                    is CliResult.Ok -> {
                        _state.update {
                            it.copy(message = ImageMessage("Deleted ${image.apiLabel} ${image.tagLabel} (${image.abi}).", isError = false))
                        }
                        val list = cli.list()
                        if (list != null) {
                            _state.update { it.copy(images = list, loaded = true) }
                        }
                    }
                    is CliResult.Failed -> {
                        _state.update { it.copy(message = ImageMessage(result.message, isError = true)) }
                    }
                }
            } finally {
                _state.update { it.copy(task = null) }
            }
        }
    }
}
