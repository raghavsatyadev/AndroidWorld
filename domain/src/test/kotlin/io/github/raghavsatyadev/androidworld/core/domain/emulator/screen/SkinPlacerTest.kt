package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SkinPlacerTest {

    private val pixel9Skin = SkinLayout(
        frameWidth = 1198,
        frameHeight = 2531,
        displayX = 55,
        displayY = 58,
        displayWidth = 1080,
        displayHeight = 2424,
        cornerRadius = 0,
        background = null,
        mask = null,
    )

    private fun assertFitRect(
        actual: FitRect,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        tolerance: Float = 0.01f,
    ) {
        actual.left shouldBe (left plusOrMinus tolerance)
        actual.top shouldBe (top plusOrMinus tolerance)
        actual.width shouldBe (width plusOrMinus tolerance)
        actual.height shouldBe (height plusOrMinus tolerance)
    }

    @Test
    fun `no skin returns same as ScreenGeometry fit`() {
        val placement = SkinPlacer.place(
            skin = null,
            rotation = ScreenRotation.Portrait,
            contentW = 1080,
            contentH = 2424,
            viewW = 1080f,
            viewH = 2424f,
        )
        val expected = ScreenGeometry.fit(1080f, 2424f, 1080f, 2424f)
        placement.frame shouldBe null
        placement.display shouldBe expected
        placement.scale shouldBe (1.0f plusOrMinus 0.01f)
        placement.canvasDegrees shouldBe 0f
    }

    @Test
    fun `pixel_9-like skin in Portrait view`() {
        val placement = SkinPlacer.place(
            skin = pixel9Skin,
            rotation = ScreenRotation.Portrait,
            contentW = 1080,
            contentH = 2424,
            viewW = 1198f,
            viewH = 2531f,
        )
        assertFitRect(placement.display, 55f, 58f, 1080f, 2424f)
        placement.canvasDegrees shouldBe 0f
    }

    @Test
    fun `pixel_9-like skin in Landscape view`() {
        val placement = SkinPlacer.place(
            skin = pixel9Skin,
            rotation = ScreenRotation.Landscape,
            contentW = 1080,
            contentH = 2424,
            viewW = 2531f,
            viewH = 1198f,
        )
        assertFitRect(placement.display, 58f, 63f, 2424f, 1080f)
        placement.canvasDegrees shouldBe -90f
    }

    @Test
    fun `pixel_9-like skin in ReversePortrait view`() {
        val placement = SkinPlacer.place(
            skin = pixel9Skin,
            rotation = ScreenRotation.ReversePortrait,
            contentW = 1080,
            contentH = 2424,
            viewW = 1198f,
            viewH = 2531f,
        )
        assertFitRect(placement.display, 63f, 49f, 1080f, 2424f)
        placement.canvasDegrees shouldBe 180f
    }

    @Test
    fun `pixel_9-like skin in ReverseLandscape view`() {
        val placement = SkinPlacer.place(
            skin = pixel9Skin,
            rotation = ScreenRotation.ReverseLandscape,
            contentW = 1080,
            contentH = 2424,
            viewW = 2531f,
            viewH = 1198f,
        )
        assertFitRect(placement.display, 49f, 55f, 2424f, 1080f)
        placement.canvasDegrees shouldBe 90f
    }
}
