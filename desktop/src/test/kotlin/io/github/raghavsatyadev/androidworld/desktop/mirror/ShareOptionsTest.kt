package io.github.raghavsatyadev.androidworld.desktop.mirror

import io.github.raghavsatyadev.androidworld.desktop.persistence.StoredState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ShareOptionsTest {

    @Test
    fun `server args carry size, frame rate and bitrate`() {
        ShareOptions(maxSize = 1024, maxFps = 60, bitRate = 4_000_000).serverArgs() shouldBe
            listOf("max_size=1024", "max_fps=60", "video_bit_rate=4000000")
    }

    @Test
    fun `defaults match the stream the share used before the controls existed`() {
        ShareOptions().serverArgs() shouldBe listOf("max_size=1280", "max_fps=30", "video_bit_rate=8000000")
    }

    @Test
    fun `stored defaults are the share defaults`() {
        val stored = StoredState()
        ShareOptions(stored.shareMaxSize, stored.shareMaxFps, stored.shareBitRate) shouldBe ShareOptions()
    }

    @Test
    fun `every default is one of the presets`() {
        ShareOptions().normalized() shouldBe ShareOptions()
    }

    @Test
    fun `hand-edited values snap to the nearest preset`() {
        ShareOptions(maxSize = 1300, maxFps = 59, bitRate = 3_000_001).normalized() shouldBe
            ShareOptions(maxSize = 1280, maxFps = 60, bitRate = 4_000_000)
        ShareOptions(maxSize = -5, maxFps = 0, bitRate = Int.MAX_VALUE).normalized() shouldBe
            ShareOptions(maxSize = 0, maxFps = 15, bitRate = 24_000_000)
    }

    @Test
    fun `labels read like the pickers show them`() {
        ShareOptions.sizeLabel(0) shouldBe "Native"
        ShareOptions.sizeLabel(1920) shouldBe "1920p"
        ShareOptions.fpsLabel(30) shouldBe "30 fps"
        ShareOptions.bitRateLabel(8_000_000) shouldBe "8 Mbps"
        ShareOptions.bitRateLabel(500_000) shouldBe "500 kbps"
    }

    @Test
    fun `rotate is scrcpy's one-byte ROTATE_DEVICE message`() {
        scrcpyRotatePacket().toList() shouldBe listOf<Byte>(11)
    }

    @Test
    fun `rotate goes out on the control socket after earlier keys`() {
        val out = java.io.ByteArrayOutputStream()
        val control = ScrcpyControl(out) {}
        control.key(AndroidKey.HOME)
        control.rotate()
        val expected = scrcpyKeyPacket(0, AndroidKey.HOME) + scrcpyKeyPacket(1, AndroidKey.HOME) + scrcpyRotatePacket()
        val deadline = System.currentTimeMillis() + 2_000
        while (out.size() < expected.size && System.currentTimeMillis() < deadline) Thread.sleep(10)
        control.close()
        out.toByteArray().toList() shouldBe expected.toList()
    }
}
