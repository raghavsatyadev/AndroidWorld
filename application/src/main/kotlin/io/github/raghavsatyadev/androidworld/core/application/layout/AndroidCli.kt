package io.github.raghavsatyadev.androidworld.core.application.layout

import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.layout.AndroidCliLayoutParser
import io.github.raghavsatyadev.androidworld.core.domain.layout.UiNode
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessCommand
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessExecutor
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessOutcome
import io.github.raghavsatyadev.androidworld.core.domain.process.ProcessRequest
import io.github.raghavsatyadev.androidworld.core.domain.process.executeBuffered
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Google's `android` CLI for the layout tree and screenshots. Every call names the device, because more
 * than one is often connected, and writes to a temp file, because stdout carries progress text.
 * A null result means "use the adb path instead": the CLI is missing, or the device refused it (a Pixel
 * with Play Protect rejects the CLI's layout helper app, for example).
 */
class AndroidCli(
    private val processes: ProcessExecutor,
    private val executable: () -> String?,
    private val timeout: Duration = 30.seconds,
) {
    /** Devices whose layout helper install failed; skipped for a while so each get_ui doesn't wait ~8 s first. */
    private val layoutRefused = java.util.concurrent.ConcurrentHashMap<DeviceSerial, Long>()

    suspend fun layout(serial: DeviceSerial): UiNode? {
        val refusedAt = layoutRefused[serial]
        if (refusedAt != null && System.currentTimeMillis() - refusedAt < RETRY_REFUSED_MS) return null
        val root = run(serial, listOf("layout", "--full"), ".json")?.let { AndroidCliLayoutParser.parse(it.decodeToString()) }
        if (root == null) layoutRefused[serial] = System.currentTimeMillis() else layoutRefused.remove(serial)
        return root
    }

    suspend fun screenshot(serial: DeviceSerial): ByteArray? =
        run(serial, listOf("screen", "capture"), ".png")?.takeIf { it.isNotEmpty() }

    private suspend fun run(serial: DeviceSerial, command: List<String>, suffix: String): ByteArray? {
        val exe = executable() ?: return null
        val out = File.createTempFile("androidworld-cli-", suffix)
        try {
            val result = processes.executeBuffered(
                ProcessRequest(
                    ProcessCommand(exe, command + listOf("--device=${serial.value}", "--output=${out.absolutePath}")),
                    timeout = timeout,
                ),
            )
            val ok = (result.outcome as? ProcessOutcome.Completed)?.exitCode == 0 && out.length() > 0
            return if (ok) out.readBytes() else null
        } finally {
            out.delete()
        }
    }

    private companion object {
        const val RETRY_REFUSED_MS = 10 * 60 * 1000L
    }
}
