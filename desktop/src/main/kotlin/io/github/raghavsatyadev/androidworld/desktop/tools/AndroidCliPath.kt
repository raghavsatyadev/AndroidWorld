package io.github.raghavsatyadev.androidworld.desktop.tools

import java.io.File

/** Finds Google's `android` CLI: PATH first, then its default Windows install folder. Null when absent. */
object AndroidCliPath {
    private val windows = System.getProperty("os.name").startsWith("Windows")
    private val names = if (windows) listOf("android.exe", "android.bat", "android.cmd") else listOf("android")

    val resolved: String? by lazy {
        val onPath = System.getenv("PATH").orEmpty().split(File.pathSeparator).asSequence()
            .filter { it.isNotBlank() }
            .flatMap { dir -> names.asSequence().map { File(dir, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
        val fallback = if (windows) File(System.getenv("ProgramData") ?: "C:\\ProgramData", "AndroidCLI\\android.exe") else null
        (onPath ?: fallback?.takeIf { it.isFile })?.absolutePath
    }
}
