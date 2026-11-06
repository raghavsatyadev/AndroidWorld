package io.github.raghavsatyadev.androidworld.desktop.tools

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries

/**
 * Finds the Android SDK and the newest copy of an SDK tool in it (cmdline-tools, build-tools and
 * emulator keep versioned folders side by side). Looked up on each call, so an SDK installed while
 * the app runs is picked up without a restart.
 */
class AndroidSdk(
    private val env: (String) -> String? = System::getenv,
    private val osName: String = System.getProperty("os.name").orEmpty(),
    private val userHome: String? = System.getProperty("user.home"),
) {
    private val windows get() = osName.startsWith("Windows", ignoreCase = true)

    fun root(): Path? = candidates().firstOrNull { it.isDirectory() }

    internal fun candidates(): List<Path> = buildList {
        env("ANDROID_SDK_ROOT")?.takeIf(String::isNotBlank)?.let { add(Path.of(it)) }
        env("ANDROID_HOME")?.takeIf(String::isNotBlank)?.let { add(Path.of(it)) }
        when {
            windows -> env("LOCALAPPDATA")?.let { add(Path.of(it, "Android", "Sdk")) }
            osName.startsWith("Mac") -> userHome?.let { add(Path.of(it, "Library", "Android", "sdk")) }
            else -> userHome?.let { add(Path.of(it, "Android", "Sdk")) }
        }
    }

    /** `apkanalyzer`, `apksigner`, … with `.bat` on Windows, newest version first. */
    fun tool(folder: String, name: String): Path? {
        val dir = root()?.resolve(folder)?.takeIf { it.isDirectory() } ?: return null
        val file = if (windows) "$name.bat" else name
        val versions = runCatching { dir.listDirectoryEntries().filter { it.isDirectory() } }.getOrDefault(emptyList())
            .sortedWith(compareByDescending(VersionOrder) { it.fileName.toString() })
        return (versions.flatMap { listOf(it.resolve("bin").resolve(file), it.resolve(file)) } + dir.resolve(file))
            .firstOrNull { Files.isRegularFile(it) && (windows || it.isExecutable()) }
    }

    /** `latest` sorts above numbered folders; numbers compare numerically (`35.0.1` > `9.0`). */
    internal object VersionOrder : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            if (a == b) return 0
            if (a == "latest") return 1
            if (b == "latest") return -1
            val x = a.split('.', '-').map { it.toIntOrNull() ?: -1 }
            val y = b.split('.', '-').map { it.toIntOrNull() ?: -1 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return a.compareTo(b)
        }
    }
}
