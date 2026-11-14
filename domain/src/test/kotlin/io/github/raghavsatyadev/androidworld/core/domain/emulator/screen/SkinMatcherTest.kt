package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SkinMatcherTest {

    private fun layout(width: Int, height: Int) = SkinLayout(
        frameWidth = width + 100,
        frameHeight = height + 100,
        displayX = 50,
        displayY = 50,
        displayWidth = width,
        displayHeight = height,
        cornerRadius = 40,
        background = null,
        mask = null,
    )

    @Test
    fun `picks hardware skinName directly when available`() {
        val available = mapOf(
            "pixel_8" to layout(1080, 2400),
            "pixel_9" to layout(1080, 2424),
        )
        val hw = EmulatorHardware(
            width = 1080,
            height = 2424,
            density = 420,
            postures = emptyList(),
            skinName = "pixel_8",
        )

        SkinMatcher.pick(available, hw) shouldBe "pixel_8"
    }

    @Test
    fun `prefers pixel names over non-pixel names of matching dimensions`() {
        val available = mapOf(
            "nexus_5x" to layout(1080, 1920),
            "pixel_phone" to layout(1080, 1920),
        )
        val hw = EmulatorHardware(
            width = 1080,
            height = 1920,
            density = 420,
            postures = emptyList(),
            skinName = null,
        )

        SkinMatcher.pick(available, hw) shouldBe "pixel_phone"
    }

    @Test
    fun `prefers shortest pixel name and then alphabetical`() {
        val available = mapOf(
            "pixel_9_pro_xl" to layout(1080, 2424),
            "pixel_9_pro" to layout(1080, 2424),
            "pixel_9" to layout(1080, 2424),
        )
        val hw = EmulatorHardware(
            width = 1080,
            height = 2424,
            density = 420,
            postures = emptyList(),
            skinName = null,
        )

        SkinMatcher.pick(available, hw) shouldBe "pixel_9"
    }

    @Test
    fun `matches layout dimensions in flipped orientation`() {
        val available = mapOf(
            "pixel_fold" to layout(1840, 2208),
        )
        val hw = EmulatorHardware(
            width = 2208,
            height = 1840,
            density = 420,
            postures = emptyList(),
            skinName = null,
        )

        SkinMatcher.pick(available, hw) shouldBe "pixel_fold"
    }

    @Test
    fun `returns null when no matching skin layout exists`() {
        val available = mapOf(
            "pixel_7" to layout(1080, 2400),
        )
        val hw = EmulatorHardware(
            width = 720,
            height = 1280,
            density = 320,
            postures = emptyList(),
            skinName = null,
        )

        SkinMatcher.pick(available, hw) shouldBe null
    }

    private val foldVariants = mapOf(
        "pixel_9_pro_fold/closed" to layout(1080, 2424),
        "pixel_9_pro_fold/default" to layout(2076, 2152),
    )

    @Test
    fun `foldable without frame dimensions picks default variant`() {
        val hw = EmulatorHardware(
            width = 2076,
            height = 2152,
            density = 420,
            postures = emptyList(),
            skinName = "pixel_9_pro_fold",
        )

        SkinMatcher.pick(foldVariants, hw) shouldBe "pixel_9_pro_fold/default"
    }

    @Test
    fun `foldable frame matching closed aspect picks closed variant`() {
        val hw = EmulatorHardware(
            width = 2076,
            height = 2152,
            density = 420,
            postures = emptyList(),
            skinName = "pixel_9_pro_fold",
        )

        SkinMatcher.pick(foldVariants, hw, 540, 1212) shouldBe "pixel_9_pro_fold/closed"
    }

    @Test
    fun `foldable frame matching default aspect picks default variant`() {
        val hw = EmulatorHardware(
            width = 2076,
            height = 2152,
            density = 420,
            postures = emptyList(),
            skinName = "pixel_9_pro_fold",
        )

        SkinMatcher.pick(foldVariants, hw, 1038, 1076) shouldBe "pixel_9_pro_fold/default"
    }

    @Test
    fun `rotated closed frame picks closed variant`() {
        val hw = EmulatorHardware(
            width = 2076,
            height = 2152,
            density = 420,
            postures = emptyList(),
            skinName = "pixel_9_pro_fold",
        )

        SkinMatcher.pick(foldVariants, hw, 1212, 540) shouldBe "pixel_9_pro_fold/closed"
    }

    @Test
    fun `size fallback to a foldable variant re-picks by frame aspect`() {
        val hw = EmulatorHardware(
            width = 2076,
            height = 2152,
            density = 420,
            postures = emptyList(),
            skinName = null,
        )

        SkinMatcher.pick(foldVariants, hw, 540, 1212) shouldBe "pixel_9_pro_fold/closed"
    }
}
