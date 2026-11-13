package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SkinLayoutParserTest {

    private val singleLine = "parts { device { display { width 1080 height 2424 x 0 y 0 corner_radius 87 } } " +
        "portrait { background { image back.webp } foreground { mask mask.webp cutout hole } } } " +
        "layouts { portrait { width 1198 height 2531 event EV_SW:0:1 part1 { name portrait x 0 y 0 } part2 { name device x 55 y 58 } } }"

    private val multiLine = """
        parts {
            device {
                display {
                    width 1080
                    height 2424
                    x 0
                    y 0
                    corner_radius 87
                }
            }
            portrait {
                background {
                    image back.webp
                }
                foreground {
                    mask mask.webp
                    cutout hole
                }
            }
        }
        layouts {
            portrait {
                width 1198
                height 2531
                event EV_SW:0:1
                part1 {
                    name portrait
                    x 0
                    y 0
                }
                part2 {
                    name device
                    x 55
                    y 58
                }
            }
        }
    """.trimIndent()

    @Test
    fun `single line pixel 9 layout parses expected dimensions and offsets`() {
        SkinLayoutParser.parse(singleLine) shouldBe SkinLayout(
            frameWidth = 1198,
            frameHeight = 2531,
            displayX = 55,
            displayY = 58,
            displayWidth = 1080,
            displayHeight = 2424,
            cornerRadius = 87,
            background = "back.webp",
            mask = "mask.webp",
        )
    }

    @Test
    fun `multi line pixel 9 layout parses expected dimensions and offsets`() {
        SkinLayoutParser.parse(multiLine) shouldBe SkinLayout(
            frameWidth = 1198,
            frameHeight = 2531,
            displayX = 55,
            displayY = 58,
            displayWidth = 1080,
            displayHeight = 2424,
            cornerRadius = 87,
            background = "back.webp",
            mask = "mask.webp",
        )
    }

    @Test
    fun `missing display block or dimensions yields null`() {
        SkinLayoutParser.parse("parts { device { other { foo bar } } }") shouldBe null
        SkinLayoutParser.parse("parts { device { display { x 0 y 0 } } }") shouldBe null
        SkinLayoutParser.parse("") shouldBe null
    }

    @Test
    fun `missing layouts portrait falls back to display dimensions`() {
        val noLayout = "parts { device { display { width 1080 height 2400 x 10 y 20 corner_radius 40 } } }"
        SkinLayoutParser.parse(noLayout) shouldBe SkinLayout(
            frameWidth = 1080,
            frameHeight = 2400,
            displayX = 10,
            displayY = 20,
            displayWidth = 1080,
            displayHeight = 2400,
            cornerRadius = 40,
            background = null,
            mask = null,
        )
    }
}
