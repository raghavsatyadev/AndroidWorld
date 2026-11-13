package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ScreenGeometryTest {

    @Test
    fun `fit centers content horizontally when view is wider`() {
        val rect = ScreenGeometry.fit(contentW = 500f, contentH = 1000f, viewW = 2000f, viewH = 1000f)
        rect shouldBe FitRect(left = 750f, top = 0f, width = 500f, height = 1000f)
    }

    @Test
    fun `fit centers content vertically when view is taller`() {
        val rect = ScreenGeometry.fit(contentW = 1000f, contentH = 500f, viewW = 1000f, viewH = 2000f)
        rect shouldBe FitRect(left = 0f, top = 750f, width = 1000f, height = 500f)
    }

    @Test
    fun `fit returns all zeros for zero or negative dimensions`() {
        ScreenGeometry.fit(0f, 100f, 100f, 100f) shouldBe FitRect(0f, 0f, 0f, 0f)
        ScreenGeometry.fit(100f, -5f, 100f, 100f) shouldBe FitRect(0f, 0f, 0f, 0f)
    }

    @Test
    fun `toDevice returns null when pointer is outside shown rectangle`() {
        val shown = FitRect(left = 100f, top = 200f, width = 540f, height = 1212f)

        ScreenGeometry.toDevice(50f, 300f, shown, 1080, 2424, ScreenRotation.Portrait) shouldBe null
        ScreenGeometry.toDevice(150f, 100f, shown, 1080, 2424, ScreenRotation.Portrait) shouldBe null
        ScreenGeometry.toDevice(700f, 300f, shown, 1080, 2424, ScreenRotation.Portrait) shouldBe null
        ScreenGeometry.toDevice(150f, 1500f, shown, 1080, 2424, ScreenRotation.Portrait) shouldBe null
    }

    @Test
    fun `toDevice maps corner and center points across all rotations`() {
        val shown = FitRect(left = 100f, top = 200f, width = 540f, height = 1212f)
        val w = 1080
        val h = 2424

        // Top-left corner (u = 0, v = 0)
        ScreenGeometry.toDevice(100f, 200f, shown, w, h, ScreenRotation.Portrait) shouldBe (0 to 0)
        ScreenGeometry.toDevice(100f, 200f, shown, w, h, ScreenRotation.Landscape) shouldBe (1079 to 0)
        ScreenGeometry.toDevice(100f, 200f, shown, w, h, ScreenRotation.ReversePortrait) shouldBe (1079 to 2423)
        ScreenGeometry.toDevice(100f, 200f, shown, w, h, ScreenRotation.ReverseLandscape) shouldBe (0 to 2423)

        // Center point (u = 0.5, v = 0.5)
        ScreenGeometry.toDevice(370f, 806f, shown, w, h, ScreenRotation.Portrait) shouldBe (540 to 1212)
        ScreenGeometry.toDevice(370f, 806f, shown, w, h, ScreenRotation.Landscape) shouldBe (540 to 1212)
        ScreenGeometry.toDevice(370f, 806f, shown, w, h, ScreenRotation.ReversePortrait) shouldBe (540 to 1212)
        ScreenGeometry.toDevice(370f, 806f, shown, w, h, ScreenRotation.ReverseLandscape) shouldBe (540 to 1212)

        // Bottom-right corner (u = 1.0, v = 1.0)
        ScreenGeometry.toDevice(640f, 1412f, shown, w, h, ScreenRotation.Portrait) shouldBe (1079 to 2423)
        ScreenGeometry.toDevice(640f, 1412f, shown, w, h, ScreenRotation.Landscape) shouldBe (0 to 2423)
        ScreenGeometry.toDevice(640f, 1412f, shown, w, h, ScreenRotation.ReversePortrait) shouldBe (0 to 0)
        ScreenGeometry.toDevice(640f, 1412f, shown, w, h, ScreenRotation.ReverseLandscape) shouldBe (1079 to 0)
    }
}
