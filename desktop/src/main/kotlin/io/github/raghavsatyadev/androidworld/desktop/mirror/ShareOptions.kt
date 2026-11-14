package io.github.raghavsatyadev.androidworld.desktop.mirror

/**
 * Stream settings for the screen share, sent to scrcpy-server as `max_size`, `max_fps` and
 * `video_bit_rate`. Changing any of them restarts the stream. [maxSize] 0 keeps the device's own size.
 */
data class ShareOptions(
    val maxSize: Int = 1280,
    val maxFps: Int = 30,
    val bitRate: Int = 8_000_000,
) {
    /** The scrcpy-server arguments these options map to. */
    fun serverArgs(): List<String> = listOf("max_size=$maxSize", "max_fps=$maxFps", "video_bit_rate=$bitRate")

    /** Snaps stored or hand-edited values to the nearest preset, so the pickers always show one. */
    fun normalized(): ShareOptions = ShareOptions(
        maxSize = MAX_SIZES.nearest(maxSize),
        maxFps = FPS.nearest(maxFps),
        bitRate = BIT_RATES.nearest(bitRate),
    )

    companion object {
        /** 0 = native. Frames are decoded in pure Java (JCodec), so the presets stay modest. */
        val MAX_SIZES = listOf(0, 1920, 1600, 1280, 1024, 800, 640)
        val FPS = listOf(15, 24, 30, 45, 60)
        val BIT_RATES = listOf(1_000_000, 2_000_000, 4_000_000, 8_000_000, 16_000_000, 24_000_000)

        fun sizeLabel(size: Int): String = if (size == 0) "Native" else "${size}p"

        fun fpsLabel(fps: Int): String = "$fps fps"

        fun bitRateLabel(bitRate: Int): String =
            if (bitRate % 1_000_000 == 0) "${bitRate / 1_000_000} Mbps" else "${bitRate / 1000} kbps"

        private fun List<Int>.nearest(value: Int): Int = minBy { kotlin.math.abs(it.toLong() - value) }
    }
}
