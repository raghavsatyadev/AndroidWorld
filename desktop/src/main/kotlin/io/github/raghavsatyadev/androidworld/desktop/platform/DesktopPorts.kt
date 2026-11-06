package io.github.raghavsatyadev.androidworld.desktop.platform

import io.github.raghavsatyadev.androidworld.core.domain.capture.ImageClipboard
import io.github.raghavsatyadev.androidworld.core.domain.devicefacts.ClipboardPort
import io.github.raghavsatyadev.androidworld.core.domain.dispatch.DispatcherProvider
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing

/** Background work on the shared pools; UI state lands on the Swing/Compose thread. */
object DesktopDispatchers : DispatcherProvider {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineContext = Dispatchers.Swing
}

private fun onEdt(block: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
}

object DesktopClipboard : ClipboardPort {
    override fun writeText(text: String) = onEdt {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

object DesktopImageClipboard : ImageClipboard {
    override fun copyPng(png: ByteArray): Boolean {
        val image = runCatching { ImageIO.read(ByteArrayInputStream(png)) }.getOrNull() ?: return false
        return runCatching {
            onEdt { Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageTransferable(image), null) }
            true
        }.getOrDefault(false)
    }

    private class ImageTransferable(private val image: Image) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

        override fun getTransferData(flavor: DataFlavor): Any {
            if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
            return image
        }
    }
}
