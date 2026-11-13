package io.github.raghavsatyadev.androidworld.desktop.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Asks where to save [csv] with the native save dialog and writes it as UTF-8 with a BOM, so
 * Excel opens non-ASCII names correctly. Returns the file written, or null when cancelled.
 * Must be called on the UI thread (the dialog is modal).
 */
fun saveCsvWithDialog(suggestedName: String, csv: String): File? {
    val dialog = FileDialog(null as Frame?, "Export CSV", FileDialog.SAVE).apply {
        file = suggestedName
        isVisible = true
    }
    val name = dialog.file ?: return null
    val target = File(dialog.directory, if (name.endsWith(".csv", ignoreCase = true)) name else "$name.csv")
    target.writeText("﻿" + csv, Charsets.UTF_8)
    return target
}
