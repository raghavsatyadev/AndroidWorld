package io.github.raghavsatyadev.androidworld.desktop.mirror

import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import io.github.raghavsatyadev.androidworld.core.domain.capture.CaptureDestination
import io.github.raghavsatyadev.androidworld.core.domain.capture.CaptureLocation
import io.github.raghavsatyadev.androidworld.core.domain.capture.CaptureTarget
import io.github.raghavsatyadev.androidworld.core.domain.capture.FileNamePolicy
import io.github.raghavsatyadev.androidworld.core.domain.process.ByteSink
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FrameSnapshotTest {

    private class FakeTarget(private val failWrite: Boolean) : CaptureTarget {
        val bytes = ByteArrayOutputStream()
        var committed = false
        var discarded = false
        override val sink = ByteSink { if (failWrite) error("disk full") else bytes.write(it) }
        override suspend fun size() = bytes.size().toLong()
        override suspend fun commit(): CaptureLocation { committed = true; return CaptureLocation("C:/shots/x.png") }
        override suspend fun discard() { discarded = true }
    }

    private class FakeDestination(failWrite: Boolean = false) : CaptureDestination {
        val target = FakeTarget(failWrite)
        var name: String? = null
        override suspend fun beginCapture(baseFileName: String): CaptureTarget { name = baseFileName; return target }
    }

    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-14T10:20:30Z") }
    private val names = FileNamePolicy { "screen-${it.epochSeconds}.png" }

    @Test
    fun `saves the png under a timestamped name and commits it`() = runTest {
        val destination = FakeDestination()
        val location = saveSnapshot(byteArrayOf(1, 2, 3), destination, names, clock)

        location.displayPath shouldBe "C:/shots/x.png"
        destination.name shouldBe "screen-${clock.now().epochSeconds}.png"
        destination.target.bytes.toByteArray().toList() shouldBe listOf<Byte>(1, 2, 3)
        destination.target.committed shouldBe true
    }

    @Test
    fun `a failed write discards the partial file`() = runTest {
        val destination = FakeDestination(failWrite = true)
        shouldThrow<IllegalStateException> { saveSnapshot(byteArrayOf(1), destination, names, clock) }

        destination.target.discarded shouldBe true
        destination.target.committed shouldBe false
    }

    @Test
    fun `frames encode as png`() {
        // Built the way the decoder builds frames: RGBA pixels installed into a Skia bitmap.
        val bitmap = Bitmap().apply {
            installPixels(ImageInfo(4, 3, ColorType.RGBA_8888, ColorAlphaType.OPAQUE), ByteArray(4 * 3 * 4) { 0x40 }, 4 * 4)
        }
        val png = bitmap.asComposeImageBitmap().encodePng()
        png.take(8).map { it.toInt() and 0xFF } shouldBe listOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
