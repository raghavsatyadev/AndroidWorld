package io.github.raghavsatyadev.androidworld.desktop

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread

/**
 * Keeps AndroidWorld to one running copy. The first copy holds a file lock and listens on a
 * loopback port; a later launch finds the lock taken, asks the first copy to show its window, and
 * exits. The OS releases the lock when the process ends, even after a crash.
 */
object SingleInstance {
    private const val SHOW = "show"
    private val dir = File(System.getProperty("user.home"), ".androidworld")
    private val lockFile = File(dir, "instance.lock")
    private val portFile = File(dir, "instance.port")

    // Held for the life of the process; a dropped reference could let the lock be collected.
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /**
     * Returns `true` when this is the only copy (call [onShow] whenever another launch wants the
     * window), or `false` after handing off to the copy already running.
     */
    fun acquire(onShow: () -> Unit): Boolean {
        dir.mkdirs()
        val opened = FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val held = runCatching { opened.tryLock() }.getOrNull()
        if (held == null) {
            opened.close()
            return !askRunningCopyToShow()
        }
        channel = opened
        lock = held
        listen(onShow)
        return true
    }

    private fun listen(onShow: () -> Unit) {
        val server = runCatching { ServerSocket(0, 10, InetAddress.getLoopbackAddress()) }.getOrNull() ?: return
        portFile.writeText(server.localPort.toString())
        thread(isDaemon = true, name = "single-instance") {
            while (true) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                client.use {
                    it.soTimeout = 2_000
                    val line = runCatching { it.getInputStream().bufferedReader().readLine() }.getOrNull()
                    if (line == SHOW) onShow()
                }
            }
        }
    }

    /**
     * Tells the running copy to show itself. It may still be starting and not have written its port
     * yet, so retry for about 3 s. Returns `false` if it never answered (then this copy starts anyway).
     */
    private fun askRunningCopyToShow(): Boolean {
        repeat(30) {
            val port = runCatching { portFile.readText().trim().toInt() }.getOrNull()
            if (port != null) {
                val sent = runCatching {
                    Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
                        socket.getOutputStream().write("$SHOW\n".toByteArray())
                    }
                }.isSuccess
                if (sent) return true
            }
            Thread.sleep(100)
        }
        return false
    }
}
