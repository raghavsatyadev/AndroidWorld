package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EmulatorHardwareParserTest {

    @Test
    fun `plain phone is not foldable and retains skin name`() {
        val config = mapOf(
            "hw.lcd.width" to "1080",
            "hw.lcd.height" to "2400",
            "hw.lcd.density" to "420",
            "skin.name" to "pixel_8",
        )
        val hardware = EmulatorHardwareParser.parse(config)

        hardware shouldBe EmulatorHardware(
            width = 1080,
            height = 2400,
            density = 420,
            postures = emptyList(),
            skinName = "pixel_8",
        )
        hardware.foldable shouldBe false
    }

    @Test
    fun `hinge sensor with posture list parses postures and marks foldable`() {
        val config = mapOf(
            "hw.lcd.width" to "1840",
            "hw.lcd.height" to "2208",
            "hw.sensor.hinge" to "yes",
            "hw.sensor.posture_list" to "1, 2, 3, 5",
        )
        val hardware = EmulatorHardwareParser.parse(config)

        hardware.foldable shouldBe true
        hardware.postures shouldBe listOf(
            FoldPosture.Closed,
            FoldPosture.HalfOpened,
            FoldPosture.Opened,
            FoldPosture.Tent,
        )
    }

    @Test
    fun `hinge sensor without posture list defaults to closed half open and open`() {
        val config = mapOf(
            "hw.lcd.width" to "1840",
            "hw.lcd.height" to "2208",
            "hw.sensor.hinge" to "yes",
        )
        val hardware = EmulatorHardwareParser.parse(config)

        hardware.foldable shouldBe true
        hardware.postures shouldBe listOf(
            FoldPosture.Closed,
            FoldPosture.HalfOpened,
            FoldPosture.Opened,
        )
    }

    @Test
    fun `skin name formatted as resolution is ignored`() {
        val config = mapOf(
            "hw.lcd.width" to "1080",
            "hw.lcd.height" to "2424",
            "skin.name" to "1080x2424",
        )
        val hardware = EmulatorHardwareParser.parse(config)

        hardware.skinName shouldBe null
    }
}
