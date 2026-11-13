package io.github.raghavsatyadev.androidworld.desktop.di

import io.github.raghavsatyadev.androidworld.core.application.sdk.ImageUsers
import io.github.raghavsatyadev.androidworld.core.application.sdk.PartialDownloads
import io.github.raghavsatyadev.androidworld.core.domain.sdk.SystemImage
import java.io.File

/** Cleans up what the android CLI leaves in `<sdk>/.sdk/arch` and `<sdk>/.sdk/unzips` when an install is cancelled. */
internal class SdkDownloads(private val sdkRoot: () -> String?) : PartialDownloads {
    override fun snapshot(): Set<String> = runCatching {
        val root = sdkRoot()?.let(::File) ?: return@runCatching emptySet()
        cacheFolders(root)
            .flatMap { (prefix, folder) ->
                folder.listFiles()?.map { "$prefix/${it.name}" }.orEmpty()
            }
            .toSet()
    }.getOrDefault(emptySet())

    override fun sweep(before: Set<String>) {
        runCatching {
            val root = sdkRoot()?.let(::File) ?: return@runCatching
            for ((prefix, folder) in cacheFolders(root)) {
                val entries = folder.listFiles() ?: continue
                for (entry in entries) {
                    if ("$prefix/${entry.name}" !in before) {
                        deleteWithRetry(entry)
                    }
                }
            }
        }
    }

    private fun cacheFolders(root: File): List<Pair<String, File>> = listOf(
        "arch" to File(root, ".sdk/arch"),
        "unzips" to File(root, ".sdk/unzips"),
    )

    private fun deleteWithRetry(entry: File) {
        if (!entry.exists()) return
        if (entry.deleteRecursively()) return
        repeat(5) {
            runCatching { Thread.sleep(400) }
            if (entry.deleteRecursively() || !entry.exists()) return
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
