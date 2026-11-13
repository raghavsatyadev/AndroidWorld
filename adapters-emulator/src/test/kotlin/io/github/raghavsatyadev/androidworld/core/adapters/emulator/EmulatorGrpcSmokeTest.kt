package io.github.raghavsatyadev.androidworld.core.adapters.emulator

import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorFrame
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorInput
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorKeys
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.ScreenRotation
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

class EmulatorGrpcSmokeTest {

    @Test
    fun emulatorGrpcSmoke() {
        val serial = System.getProperty("androidWorld.emulatorSmoke")
        assumeTrue(!serial.isNullOrBlank(), "Skipping smoke test: androidWorld.emulatorSmoke is not set")

        runBlocking {
            withTimeout(20_000) {
                val rawEndpoint = DiscoveryFileEndpointLocator().find(serial)
                println("[smoke] endpoint=$rawEndpoint")
                assertNotNull(rawEndpoint)
                val endpoint = checkNotNull(rawEndpoint)

                GrpcEmulatorConnector().connect(endpoint).use { conn ->
                    val hardware = conn.hardware()
                    println("[smoke] hardware=$hardware")
                    assertTrue(hardware.width > 0 && hardware.height > 0)

                    val frame = conn.frames(540, 1212).first()
                    val nonZeroBytes = frame.rgba.count { it.toInt() != 0 }
                    println(
                        "[smoke] frame width=${frame.width} height=${frame.height} " +
                            "seq=${frame.seq} rotation=${frame.rotation} nonZeroBytes=$nonZeroBytes",
                    )
                    assertTrue(frame.rgba.isNotEmpty())

                    val img = saveFrame(frame, File("build/emulator-smoke/frame-portrait.png"))
                    val firstPixel = img.getRGB(0, 0)
                    var notBlank = false
                    for (y in 0 until frame.height) {
                        for (x in 0 until frame.width) {
                            if (img.getRGB(x, y) != firstPixel) {
                                notBlank = true
                                break
                            }
                        }
                        if (notBlank) break
                    }
                    assertTrue(notBlank, "Frame must not be blank")

                    println("[smoke] sending HOME")
                    conn.send(EmulatorInput.Key(EmulatorKeys.HOME))
                    delay(1500)

                    val cx = hardware.width / 2
                    val cy = hardware.height / 2
                    println("[smoke] tapping center ($cx, $cy)")
                    conn.send(EmulatorInput.Touch(cx, cy, pressed = true))
                    conn.send(EmulatorInput.Touch(cx, cy, pressed = false))
                    delay(1500)

                    val tapFrame = conn.frames(540, 1212).first()
                    println("[smoke] tap frame width=${tapFrame.width} height=${tapFrame.height} seq=${tapFrame.seq}")
                    saveFrame(tapFrame, File("build/emulator-smoke/frame-after-tap.png"))

                    if (System.getProperty("androidWorld.emulatorSmokeRotate") == "true") {
                        println("[smoke] rotating to Landscape")
                        conn.rotate(ScreenRotation.Landscape)
                        delay(3000)

                        val landscapeFrame = conn.frames(540, 1212).first()
                        println(
                            "[smoke] landscape width=${landscapeFrame.width} height=${landscapeFrame.height} " +
                                "rotation=${landscapeFrame.rotation}",
                        )
                        saveFrame(landscapeFrame, File("build/emulator-smoke/frame-landscape.png"))

                        println("[smoke] rotating to Portrait")
                        conn.rotate(ScreenRotation.Portrait)
                        delay(2000)
                    }
                }
            }
        }
    }

    private fun saveFrame(frame: EmulatorFrame, file: File): BufferedImage {
        file.parentFile?.mkdirs()
        val img = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_ARGB)
        var i = 0
        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val r = frame.rgba[i++].toInt() and 0xFF
                val g = frame.rgba[i++].toInt() and 0xFF
                val b = frame.rgba[i++].toInt() and 0xFF
                val a = frame.rgba[i++].toInt() and 0xFF
                img.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        ImageIO.write(img, "png", file)
        return img
    }
}
