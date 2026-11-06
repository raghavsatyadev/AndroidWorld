package io.github.raghavsatyadev.androidworld.desktop.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the app remembers between runs, in one JSON file. */
@Serializable
data class StoredState(
    val adbPathOverride: String? = null,
    val scrcpyPathOverride: String? = null,
    val captureDirectory: String? = null,
    val logcatBufferSizeKb: Int = 1024,
    val talkBackOnCommand: String? = null,
    val talkBackOffCommand: String? = null,
    val copyScreenshotsToClipboard: Boolean = true,
    val selectedSerial: String? = null,
    val selectedPackageSerial: String? = null,
    val selectedPackageName: String? = null,
    val logMinLevel: String? = null,
    val logPackageFilterOn: Boolean = true,
    val logWrap: Boolean = false,
    val lastView: String? = null,
    val mcpAccess: String = "Off",
    val mcpPort: Int = 0,
    val mcpToken: String? = null,
    val pinnedPackages: List<String> = emptyList(),
    val originalLocales: Map<String, String> = emptyMap(),
    val darkTheme: Boolean? = null,
    /** Most recent first, each `host:port` as [io.github.raghavsatyadev.androidworld.core.domain.network.ProxyEndpoint.render] writes it. */
    val recentProxies: List<String> = emptyList(),
    val deepLinks: StoredDeepLinks = StoredDeepLinks(),
)

/** The device deep-link launcher's history and remembered form options. */
@Serializable
data class StoredDeepLinks(
    val history: List<StoredDeepLink> = emptyList(),
    val lastPackage: String = "",
    val coldStart: Boolean = false,
    val commandExpanded: Boolean = false,
)

@Serializable
data class StoredDeepLink(val uri: String, val packageName: String = "", val lastRun: Long = 0, val handledBy: String = "")

/** Thread-safe read/modify/write over `~/.androidworld/state.json` (atomic replace on save). */
class AppStore(private val file: Path = defaultFile()) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    @Volatile
    var state: StoredState = load()
        private set

    @Synchronized
    fun update(change: (StoredState) -> StoredState) {
        val next = change(state)
        if (next == state) return
        state = next
        save(next)
    }

    /** The bearer token agents must send; created on first use. */
    fun mcpToken(): String = state.mcpToken ?: regenerateMcpToken()

    fun regenerateMcpToken(): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        update { it.copy(mcpToken = token) }
        return token
    }

    private fun load(): StoredState = runCatching {
        if (Files.exists(file)) json.decodeFromString(StoredState.serializer(), Files.readString(file)) else StoredState()
    }.getOrDefault(StoredState())

    private fun save(value: StoredState) {
        runCatching {
            Files.createDirectories(file.parent)
            val tmp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(tmp, json.encodeToString(StoredState.serializer(), value))
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }

    companion object {
        fun appDirectory(): Path = Path.of(System.getProperty("user.home"), ".androidworld")

        fun defaultFile(): Path = appDirectory().resolve("state.json")
    }
}
