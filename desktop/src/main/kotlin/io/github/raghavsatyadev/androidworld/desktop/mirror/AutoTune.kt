package io.github.raghavsatyadev.androidworld.desktop.mirror

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * What Auto measured: how long this PC takes to decode one key frame per megapixel (frames are decoded
 * in pure Java, so this is what limits a smooth stream), and how fast the adb link carries data.
 */
data class StreamMeasure(val keyframeMsPerMegapixel: Double, val linkMbps: Double?)

/** Picks resolution, frame rate and bitrate from a [StreamMeasure] and the size the stream is shown at. */
object AutoTune {
    /** Stream a little above the shown size, so text stays sharp after scaling and compression. */
    private const val SHARPNESS = 1.2

    /** Extra decode-time margin a new pick must clear; stops flipping between two presets. */
    private const val MARGIN = 1.15

    /** Below this frame rate the mirror stops feeling responsive, so resolution gives way first. */
    private const val MIN_SMOOTH_FPS = 30

    /** Bits per pixel per frame that keep text crisp while the screen moves. */
    private const val BITS_PER_PIXEL = 0.15

    /** The video may use this share of the measured link. */
    private const val LINK_SHARE = 0.4

    /**
     * [viewLongSide] is the long side, in pixels, the stream is drawn at; [aspect] is short side / long side.
     * Takes the sharpest preset the view can show that still decodes at [MIN_SMOOTH_FPS] or better, then the
     * highest frame rate whose frame time covers a key frame decode, then a bitrate the link can carry.
     */
    fun pick(viewLongSide: Int, aspect: Double, measure: StreamMeasure): ShareOptions {
        val sizes = ShareOptions.MAX_SIZES.filter { it > 0 }.sorted()
        val sharp = sizes.firstOrNull { it >= viewLongSide * SHARPNESS } ?: sizes.last()
        fun pixels(size: Int) = size.toDouble() * size * aspect
        fun fps(size: Int): Int? {
            val decodeMs = measure.keyframeMsPerMegapixel * pixels(size) / 1e6
            return ShareOptions.FPS.lastOrNull { decodeMs <= 1000.0 / it }
        }
        val size = sizes.lastOrNull { it <= sharp && (fps(it) ?: 0) >= MIN_SMOOTH_FPS } ?: sizes.first()
        val maxFps = fps(size) ?: ShareOptions.FPS.first()
        val wanted = pixels(size) * maxFps * BITS_PER_PIXEL
        val cap = measure.linkMbps?.let { it * 1e6 * LINK_SHARE } ?: Double.MAX_VALUE
        val rates = ShareOptions.BIT_RATES
        val bitRate = (rates.firstOrNull { it >= wanted } ?: rates.last()).let { ideal ->
            rates.lastOrNull { it <= ideal && it <= cap } ?: rates.first()
        }
        return ShareOptions(size, maxFps, bitRate)
    }

    /** The settings to use now: keeps [current] while it still fits, otherwise a pick with [MARGIN] to spare. */
    fun next(current: ShareOptions, viewLongSide: Int, aspect: Double, measure: StreamMeasure): ShareOptions {
        if (pick(viewLongSide, aspect, measure) == current) return current
        return pick(viewLongSide, aspect, measure.copy(keyframeMsPerMegapixel = measure.keyframeMsPerMegapixel * MARGIN))
    }

    /** Wireless adb serials are `ip:port` or an mDNS `adb-…._adb-tls-connect._tcp` name. */
    fun isWireless(serial: String): Boolean =
        "_adb-tls-" in serial || Regex("""^[\w.\-\[\]:]+:\d+$""").matches(serial)

    /** How long to wait before measuring again: Wi-Fi changes often, a weak link more so; USB barely does. */
    fun recheckSeconds(serial: String, linkMbps: Double?): Long = when {
        !isWireless(serial) -> 300
        linkMbps != null && linkMbps < 20 -> 30
        else -> 60
    }

    /** One line for the toast, like "800p, 60 fps, 4 Mbps (decode 9 ms a frame, link 180 Mbps)". */
    fun describe(options: ShareOptions, aspect: Double, measure: StreamMeasure): String {
        val decodeMs = measure.keyframeMsPerMegapixel * options.maxSize.toDouble() * options.maxSize * aspect / 1e6
        val link = measure.linkMbps?.let { ", link ${it.toInt()} Mbps" }.orEmpty()
        return "${ShareOptions.sizeLabel(options.maxSize)}, ${ShareOptions.fpsLabel(options.maxFps)}, " +
            "${ShareOptions.bitRateLabel(options.bitRate)} (decode ${decodeMs.toInt().coerceAtLeast(1)} ms a frame$link)"
    }
}

/** Times how fast the device can send bytes over adb, the same path the video takes. */
object LinkSpeed {
    private const val BYTES = 2 * 1024 * 1024
    private const val TIMEOUT_SECONDS = 10L

    /** Megabits per second, or null when the device did not answer. Blocks for up to [TIMEOUT_SECONDS]. */
    fun measureMbps(adb: String, serial: String): Double? = runCatching {
        val process = ProcessBuilder(adb, "-s", serial, "exec-out", "head -c $BYTES /dev/zero")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        thread(isDaemon = true, name = "link-speed-timeout") {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        var started = 0L
        process.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                // The clock starts at the first bytes, so adb's own start-up time is not counted.
                if (started == 0L) started = System.nanoTime() else total += read
            }
        }
        val seconds = (System.nanoTime() - started) / 1e9
        if (started == 0L || total < BYTES / 2 || seconds <= 0) null else total * 8 / seconds / 1e6
    }.getOrNull()
}

/** Key frame decode times of the running stream, as milliseconds per megapixel. Thread-safe. */
internal class DecodeMeter {
    private val samples = ArrayDeque<Double>()
    private var total = 0

    /** How many key frames have been timed since the app started. */
    val count: Int get() = synchronized(samples) { total }

    fun record(nanos: Long, pixels: Int) {
        if (pixels <= 0) return
        synchronized(samples) {
            samples.addLast(nanos / 1e6 / (pixels / 1e6))
            if (samples.size > KEPT) samples.removeFirst()
            total++
        }
    }

    /** Median of the key frames timed after [since] (a [count] value), or of the last few when there are none. */
    fun median(since: Int): Double? = synchronized(samples) {
        val fresh = samples.toList().takeLast((total - since).coerceIn(0, samples.size))
        // The first key frame after a reset also pays for the new decoder, so leave it out when others exist.
        val used = when {
            fresh.size > 2 -> fresh.drop(1)
            fresh.isNotEmpty() -> fresh
            else -> samples.toList().takeLast(3)
        }
        used.sorted().getOrNull(used.size / 2)
    }

    private companion object {
        const val KEPT = 64
    }
}
