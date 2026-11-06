package io.github.raghavsatyadev.androidworld.desktop.mirror

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import org.jcodec.codecs.h264.H264Decoder
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.model.Picture
import org.jcodec.scale.ColorUtil
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/** What the mirror needs from the host: adb, and the `scrcpy-server` file shipped next to scrcpy. */
data class ScrcpySetup(val adb: String, val server: File, val version: String)

/**
 * Shows the device screen inside AndroidWorld: pushes `scrcpy-server` to the device, starts it over
 * `adb shell app_process`, reads its H.264 stream through an adb forward, and decodes frames with JCodec.
 * Touches and keys go back on scrcpy's control socket. Closing the flow stops the server and cleans up.
 */
class ScrcpyMirror {
    private val control = AtomicReference<ScrcpyControl?>()

    fun touch(action: Int, x: Int, y: Int, width: Int, height: Int) {
        control.get()?.touch(action, x, y, width, height)
    }

    fun key(code: Int) {
        control.get()?.key(code)
    }

    fun frames(setup: ScrcpySetup, serial: String, maxSize: Int = 1280, maxFps: Int = 30): Flow<ImageBitmap> = callbackFlow {
        val video = AtomicReference<Socket?>()
        val controlSocket = AtomicReference<Socket?>()
        val sessionControl = AtomicReference<ScrcpyControl?>()
        val server = AtomicReference<Process?>()
        val id = Random.nextInt(1, Int.MAX_VALUE).toString(16).padStart(8, '0')
        val remote = "/data/local/tmp/androidworld-mirror-$id.jar"
        var port: String? = null

        fun closeControl() {
            sessionControl.getAndSet(null)?.let {
                control.compareAndSet(it, null)
                it.close()
            }
        }

        fun adb(vararg args: String): String {
            val process = ProcessBuilder(listOf(setup.adb, "-s", serial) + args).redirectErrorStream(true).start()
            val result = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "Could not reach the device. ${result.trim()}" }
            return result.trim()
        }

        fun closeSockets() {
            runCatching { controlSocket.getAndSet(null)?.close() }
            runCatching { video.getAndSet(null)?.close() }
        }

        val worker = thread(isDaemon = true, name = "scrcpy-mirror-$id") {
            try {
                adb("push", setup.server.path, remote)
                port = adb("forward", "tcp:0", "localabstract:scrcpy_$id")
                server.set(
                    ProcessBuilder(
                        setup.adb, "-s", serial, "shell", "CLASSPATH=$remote", "app_process", "/",
                        "com.genymobile.scrcpy.Server", setup.version, "scid=$id", "tunnel_forward=true", "audio=false",
                        "control=true", "clipboard_autosync=false", "send_device_meta=false", "send_dummy_byte=false",
                        "max_size=$maxSize", "max_fps=$maxFps", "video_codec_options=profile=1,level=512", "cleanup=false",
                    ).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start(),
                )
                val input = connect(port!!.toInt(), video, controlSocket, sessionControl) { close(it) }
                    ?: error("The device did not start screen sharing.")
                if (isClosedForSend) closeControl() else control.set(sessionControl.get())
                decodeLoop(input)
            } catch (failure: Exception) {
                if (!isClosedForSend) close(failure)
            } finally {
                closeControl()
                closeSockets()
                server.getAndSet(null)?.destroyForcibly()
                port?.let { runCatching { adb("forward", "--remove", "tcp:$it") } }
                runCatching { adb("shell", "rm", "-f", remote) }
            }
        }

        awaitClose {
            closeControl()
            closeSockets()
            server.getAndSet(null)?.destroyForcibly()
            worker.interrupt()
        }
    }.buffer(Channel.CONFLATED)

    /** Opens the video then the control socket; the server needs a moment to listen, so retry for ~5 s. */
    private fun kotlinx.coroutines.channels.ProducerScope<ImageBitmap>.connect(
        port: Int,
        video: AtomicReference<Socket?>,
        controlSocket: AtomicReference<Socket?>,
        sessionControl: AtomicReference<ScrcpyControl?>,
        onFailure: (Exception) -> Unit,
    ): DataInputStream? {
        repeat(50) {
            if (isClosedForSend) return null
            try {
                val candidate = Socket("127.0.0.1", port).apply { soTimeout = 15_000; tcpNoDelay = true }
                video.set(candidate)
                val inputSocket = Socket("127.0.0.1", port).apply { tcpNoDelay = true }
                controlSocket.set(inputSocket)
                val input = DataInputStream(BufferedInputStream(candidate.getInputStream(), 64 * 1024))
                check(input.readInt() == H264) { "This device cannot share its screen as H.264." }
                candidate.soTimeout = 0 // A still screen sends no packets; that is not a failure.
                sessionControl.set(ScrcpyControl(inputSocket.getOutputStream(), onFailure))
                return input
            } catch (_: Exception) {
                runCatching { video.getAndSet(null)?.close() }
                runCatching { controlSocket.getAndSet(null)?.close() }
                Thread.sleep(100)
            }
        }
        return null
    }

    private fun kotlinx.coroutines.channels.ProducerScope<ImageBitmap>.decodeLoop(input: DataInputStream) {
        var width = 0
        var height = 0
        var decoder = H264Decoder()
        var pixels: Array<ByteArray>? = null
        var rgb: Picture? = null
        var rgba: ByteArray? = null
        while (!isClosedForSend) {
            val first = input.readInt()
            val second = input.readInt()
            val third = input.readInt()
            if (first < 0) { // Session packet: the video size changed (rotation, fold).
                width = second
                height = third
                check(width in 1..4096 && height in 1..4096) { "The device sent an invalid screen size." }
                pixels = Picture.create((width + 15) / 16 * 16, (height + 15) / 16 * 16, ColorSpace.YUV420).data
                decoder = H264Decoder()
                rgb = null
                rgba = null
                continue
            }
            check(third in 1..16_777_216) { "The device sent an invalid video packet." }
            val bytes = ByteArray(third).also(input::readFully)
            if (first and CONFIG_FLAG != 0) {
                decoder = H264Decoder.createH264DecoderFromCodecPrivate(ByteBuffer.wrap(bytes))
                continue
            }
            val decoded = decoder.decodeFrame(ByteBuffer.wrap(bytes), pixels ?: continue) ?: continue
            val color = rgb ?: Picture.create(decoded.width, decoded.height, ColorSpace.RGB).also { rgb = it }
            ColorUtil.getTransform(decoded.color, ColorSpace.RGB).transform(decoded, color)
            val source = color.getPlaneData(0)
            val output = rgba ?: ByteArray(width * height * 4).also { rgba = it }
            for (y in 0 until height) {
                var from = y * color.width * 3
                var to = y * width * 4
                for (x in 0 until width) {
                    output[to] = (source[from] + 128).toByte()
                    output[to + 1] = (source[from + 1] + 128).toByte()
                    output[to + 2] = (source[from + 2] + 128).toByte()
                    output[to + 3] = 255.toByte()
                    from += 3
                    to += 4
                }
            }
            val bitmap = Bitmap()
            bitmap.installPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE), output.copyOf(), width * 4)
            bitmap.setImmutable()
            trySend(bitmap.asComposeImageBitmap())
        }
    }

    private companion object {
        const val H264 = 0x68323634
        const val CONFIG_FLAG = 0x40000000
    }
}
