package io.github.raghavsatyadev.androidworld.desktop.mirror

import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Android key codes the mirror toolbar sends. */
object AndroidKey {
    const val HOME = 3
    const val BACK = 4
    const val POWER = 26
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val APP_SWITCH = 187
}

/** Touch actions, as Android's MotionEvent numbers them. */
object TouchAction {
    const val DOWN = 0
    const val UP = 1
    const val MOVE = 2
}

internal fun scrcpyTouchPacket(action: Int, x: Int, y: Int, width: Int, height: Int): ByteArray =
    ByteBuffer.allocate(32).put(2).put(action.toByte()).putLong(0)
        .putInt(x.coerceIn(0, width - 1)).putInt(y.coerceIn(0, height - 1))
        .putShort(width.toShort()).putShort(height.toShort())
        .putShort(if (action == TouchAction.UP) 0 else (-1).toShort()).putInt(0).putInt(0).array()

internal fun scrcpyKeyPacket(action: Int, code: Int): ByteArray =
    ByteBuffer.allocate(14).put(0).put(action.toByte()).putInt(code).putInt(0).putInt(0).array()

/**
 * Writes scrcpy control messages (touch and key) on their own thread, in order.
 */
internal class ScrcpyControl(private val output: OutputStream, onFailure: (Exception) -> Unit) : AutoCloseable {
    private data class Message(val bytes: ByteArray, val move: Boolean)

    private val queue = LinkedBlockingDeque<Message>()
    private val active = AtomicBoolean(true)

    private val writer = thread(isDaemon = true, name = "scrcpy-input") {
        try {
            while (active.get()) {
                val message = queue.takeFirst()
                output.write(message.bytes)
                output.flush()
            }
        } catch (failure: Exception) {
            if (active.get()) onFailure(failure)
        }
    }

    fun touch(action: Int, x: Int, y: Int, width: Int, height: Int) {
        if (!active.get()) return
        synchronized(queue) {
            // A newer move replaces a queued one; presses and releases keep their order.
            if (action == TouchAction.MOVE && queue.peekLast()?.move == true) queue.pollLast()
            queue.offerLast(Message(scrcpyTouchPacket(action, x, y, width, height), action == TouchAction.MOVE))
        }
    }

    fun key(code: Int) {
        if (!active.get()) return
        synchronized(queue) {
            queue.offerLast(Message(scrcpyKeyPacket(0, code), false))
            queue.offerLast(Message(scrcpyKeyPacket(1, code), false))
        }
    }

    override fun close() {
        active.set(false)
        writer.interrupt()
        runCatching { output.close() }
        queue.clear()
    }
}
