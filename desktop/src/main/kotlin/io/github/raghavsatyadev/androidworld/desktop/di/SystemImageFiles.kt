package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.core.application.sdk.ImageUsers
import io.github.raghavsatyadev.androidworld.core.application.sdk.PartialDownloads
import io.github.raghavsatyadev.androidworld.core.domain.sdk.SystemImage
import java.io.File

/**
 * Tracks and cleans up temporary download files created by the `android` CLI.
 */
internal object TempDownloads : PartialDownloads {
    private val tmpFileRegex =
        Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.tmp$""", RegexOption.IGNORE_CASE)

    override fun snapshot(): Set<String> = runCatching {
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        tmpDir.listFiles()
            ?.filter { it.isFile && tmpFileRegex.matches(it.name) }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
    }.getOrDefault(emptySet())

    override fun sweep(before: Set<String>) {
        runCatching {
            val tmpDir = File(System.getProperty("java.io.tmpdir"))
            val candidates = tmpDir.listFiles()
                ?.filter { it.isFile && tmpFileRegex.matches(it.name) && it.name !in before }
                .orEmpty()
            for (file in candidates) {
                deleteWithRetry(file)
            }
        }
    }

    private fun deleteWithRetry(file: File) {
        if (!file.exists()) return
        if (file.delete()) return
        repeat(5) {
            runCatching { Thread.sleep(500) }
            if (file.delete() || !file.exists()) return
        }
    }
}

/**
 * Finds all AVDs configured to use a given system image.
 */
internal object AvdImageUsers : ImageUsers {
    private val home: File by lazy {
        System.getenv("ANDROID_AVD_HOME")?.let(::File)
            ?: System.getenv("ANDROID_USER_HOME")?.let { File(it, "avd") }
            ?: File(System.getProperty("user.home"), ".android/avd")
    }

    override suspend fun avdsUsing(image: SystemImage): List<String> = runCatching {
        val iniFiles = home.listFiles { file -> file.isFile && file.name.endsWith(".ini", ignoreCase = true) }
            ?: return@runCatching emptyList()
        val targetPath = image.path.replace('\\', '/').trim().trimEnd('/')
        val matchingAvds = mutableListOf<String>()

        for (iniFile in iniFiles) {
            runCatching {
                val avdName = iniFile.nameWithoutExtension
                val pathPointer = iniFile.readLines()
                    .firstOrNull { it.startsWith("path=") }
                    ?.substringAfter('=')
                    ?.trim()
                    ?.let(::File)
                val folder = pathPointer?.takeIf { it.isDirectory } ?: File(home, "$avdName.avd")
                val configFile = File(folder, "config.ini")
                if (configFile.isFile) {
                    val sysDir = configFile.readLines()
                        .firstOrNull { it.startsWith("image.sysdir.1=") }
                        ?.substringAfter('=')
                        ?.replace('\\', '/')
                        ?.trim()
                        ?.trimEnd('/')
                    if (sysDir == targetPath) {
                        matchingAvds.add(avdName)
                    }
                }
            }
        }
        matchingAvds.sorted()
    }.getOrDefault(emptyList())
}
