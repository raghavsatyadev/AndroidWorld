package io.github.raghavsatyadev.androidworld.desktop.tools

import java.io.File

/** Finds Google's `android` CLI: PATH first, then Windows fallbacks (%ProgramData%, %USERPROFILE%\AppData). Null when absent. */
object AndroidCliPath {
    private val windows = System.getProperty("os.name").startsWith("Windows")
    private val names = if (windows) listOf("android.exe", "android.bat", "android.cmd") else listOf("android")

    val resolved: String? by lazy {
        val onPath = System.getenv("PATH").orEmpty().split(File.pathSeparator).asSequence()
            .filter { it.isNotBlank() }
            .flatMap { dir -> names.asSequence().map { File(dir, it) } }
            .firstOrNull { it.isFile && it.canExecute() }
        val fallback = if (windows) {
            listOfNotNull(
                File(System.getenv("ProgramData") ?: "C:\\ProgramData", "AndroidCLI\\android.exe"),
                System.getProperty("user.home")?.let { File(it, "AppData\\AndroidCLI\\android.exe") },
            ).firstOrNull { it.isFile }
        } else {
            null
        }
        (onPath ?: fallback)?.absolutePath
    }
}
