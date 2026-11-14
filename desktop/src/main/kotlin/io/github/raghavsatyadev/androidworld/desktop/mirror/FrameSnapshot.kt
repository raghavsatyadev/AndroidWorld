package io.github.raghavsatyadev.androidworld.desktop.mirror

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import io.github.raghavsatyadev.androidworld.core.domain.capture.CaptureDestination
import io.github.raghavsatyadev.androidworld.core.domain.capture.CaptureLocation
import io.github.raghavsatyadev.androidworld.core.domain.capture.FileNamePolicy
import kotlin.time.Clock
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** PNG bytes of a decoded share frame. */
fun ImageBitmap.encodePng(): ByteArray =
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        ?: error("Could not encode the screenshot.")

/**
 * Saves [png] to the capture folder under a timestamped name, the same place and naming the
 * device screenshot feature uses. A failed write leaves no partial file behind.
 */
suspend fun saveSnapshot(
    png: ByteArray,
    destination: CaptureDestination,
    names: FileNamePolicy,
    clock: Clock = Clock.System,
): CaptureLocation {
    val target = destination.beginCapture(names.baseFileName(clock.now()))
    try {
        target.sink.write(png)
        return target.commit()
    } catch (failure: Exception) {
        target.discard()
        throw failure
    }
}
