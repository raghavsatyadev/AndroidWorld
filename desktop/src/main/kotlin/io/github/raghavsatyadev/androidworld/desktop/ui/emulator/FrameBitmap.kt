package io.github.raghavsatyadev.androidworld.desktop.ui.emulator

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorFrame
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Converts a raw RGBA8888 [EmulatorFrame] to a Compose [ImageBitmap] via Skia [Bitmap].
 */
fun EmulatorFrame.toImageBitmap(): ImageBitmap {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL))
    bitmap.installPixels(rgba)
    bitmap.setImmutable()
    return bitmap.asComposeImageBitmap()
}
