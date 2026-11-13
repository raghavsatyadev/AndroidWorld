package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

/** Host, port, and optional authentication token for connecting to an emulator's gRPC endpoint. */
data class EmulatorEndpoint(
    val host: String = "localhost",
    val port: Int,
    val token: String? = null,
) {
    override fun toString(): String =
        "EmulatorEndpoint(host=$host, port=$port, token=${if (token != null) "<redacted>" else "null"})"
}

/** Discovery information read from an emulator's `pid_*.ini` file. */
data class EmulatorDiscovery(
    val consolePort: Int?,
    val avdName: String?,
    val grpcPort: Int?,
    val grpcToken: String?,
) {
    /** Returns an [EmulatorEndpoint] if the gRPC port is present, or null otherwise. */
    fun endpoint(): EmulatorEndpoint? = grpcPort?.let {
        EmulatorEndpoint(port = it, token = grpcToken?.takeIf(String::isNotBlank))
    }
}

/**
 * Parses the emulator's runtime discovery `.ini` files (typically `pid_*.ini`).
 *
 * Recognized keys:
 * - `port.serial` -> console port (e.g. 5554)
 * - `avd.name` -> AVD name
 * - `grpc.port` -> gRPC port
 * - `grpc.token` -> gRPC authentication token
 */
object EmulatorDiscoveryParser {
    fun parse(text: String): EmulatorDiscovery {
        var consolePort: Int? = null
        var avdName: String? = null
        var grpcPort: Int? = null
        var grpcToken: String? = null

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#') || line.startsWith(';')) return@forEach
            val idx = line.indexOf('=')
            if (idx <= 0) return@forEach
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            when (key) {
                "port.serial" -> consolePort = value.toIntOrNull()
                "avd.name" -> avdName = value.ifEmpty { null }
                "grpc.port" -> grpcPort = value.toIntOrNull()
                "grpc.token" -> grpcToken = value.ifEmpty { null }
            }
        }

        return EmulatorDiscovery(
            consolePort = consolePort,
            avdName = avdName,
            grpcPort = grpcPort,
            grpcToken = grpcToken,
        )
    }
}

/** Extracts the numeric console port from an emulator serial like "emulator-5554", or null if not matching. */
fun consolePortOf(serial: String): Int? {
    if (!serial.startsWith("emulator-")) return null
    return serial.removePrefix("emulator-").toIntOrNull()
}
