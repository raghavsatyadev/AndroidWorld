package io.github.raghavsatyadev.androidworld.core.adapters.emulator

import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorDiscoveryParser
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorEndpoint
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorEndpointLocator
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.consolePortOf
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Default directories scanned for emulator discovery `.ini` files across Windows, macOS, and Linux.
 */
fun defaultDiscoveryDirectories(): List<File> {
    val dirs = mutableListOf<File>()
    val os = System.getProperty("os.name")?.lowercase().orEmpty()
    val user = System.getProperty("user.name").orEmpty()
    val home = System.getProperty("user.home").orEmpty()

    if (os.contains("win")) {
        System.getenv("LOCALAPPDATA")?.let { localAppData ->
            dirs += File(localAppData, "Temp/avd/running")
        }
        val tmpDir = System.getProperty("java.io.tmpdir")
        if (!tmpDir.isNullOrBlank()) {
            dirs += File(tmpDir, "avd/running")
        }
    } else if (os.contains("mac") || os.contains("darwin")) {
        dirs += File(home, "Library/Caches/TemporaryItems/avd/running")
    } else {
        System.getenv("XDG_RUNTIME_DIR")?.let { xdg ->
            dirs += File(xdg, "avd/running")
        }
        dirs += File("/tmp/android-$user/avd/running")
    }

    return dirs.map { it.canonicalFile }.distinct().filter { it.isDirectory }
}

/**
 * Locates an emulator's gRPC endpoint by scanning discovery files (named `pid_*.ini`) written
 * by running Android emulator processes.
 */
class DiscoveryFileEndpointLocator(
    private val directories: () -> List<File> = ::defaultDiscoveryDirectories,
) : EmulatorEndpointLocator {

    override suspend fun find(serial: String): EmulatorEndpoint? {
        val port = consolePortOf(serial) ?: return null
        return withContext(Dispatchers.IO) {
            directories()
                .flatMap { dir ->
                    dir.listFiles { _, name -> name.startsWith("pid_") && name.endsWith(".ini") }
                        ?.toList()
                        .orEmpty()
                }
                .mapNotNull { file ->
                    val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
                    val discovery = EmulatorDiscoveryParser.parse(text)
                    val endpoint = discovery.endpoint() ?: return@mapNotNull null
                    if (discovery.consolePort == port) {
                        file.lastModified() to endpoint
                    } else {
                        null
                    }
                }
                .maxByOrNull { it.first }
                ?.second
        }
    }
}
