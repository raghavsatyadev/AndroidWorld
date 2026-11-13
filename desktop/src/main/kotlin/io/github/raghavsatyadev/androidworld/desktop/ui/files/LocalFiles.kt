package io.github.raghavsatyadev.androidworld.desktop.ui.files

import io.github.raghavsatyadev.androidworld.core.application.files.LocalItem
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import javax.swing.JFileChooser

/** This computer's side of the file browser: pickers, drops and opening a folder. UI glue only. */
internal object LocalFiles {
    /** The native multi-file picker. AWT FileDialog can't pick folders on Windows, hence [chooseFolder]. */
    fun chooseFiles(): List<LocalItem> {
        val dialog = FileDialog(null as Frame?, "Push files to the device", FileDialog.LOAD).apply { isMultipleMode = true }
        dialog.isVisible = true
        return dialog.files.orEmpty().map { LocalItem(it.absolutePath, it.isDirectory) }
    }

    fun chooseFolder(title: String, initial: String?): String? {
        val chooser = JFileChooser(initial?.takeIf { File(it).isDirectory }).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showDialog(null, "Choose") == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }

    /** Files and folders dragged from the OS file manager, or empty for any other drop. */
    fun dropped(transferable: Transferable): List<LocalItem> {
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
        val files = runCatching { transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*> }.getOrNull().orEmpty()
        return files.filterIsInstance<File>().map { LocalItem(it.absolutePath, it.isDirectory) }
    }

    fun open(folder: String) {
        Thread { runCatching { Desktop.getDesktop().open(File(folder)) } }.start()
    }
}
