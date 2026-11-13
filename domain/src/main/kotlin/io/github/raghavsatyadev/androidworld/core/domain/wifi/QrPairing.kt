package io.github.raghavsatyadev.androidworld.core.domain.wifi

import kotlin.random.Random

data class QrPairingCredentials(val serviceName: String, val password: String) {
    override fun toString(): String = "QrPairingCredentials(serviceName=$serviceName, password=******)"
}

object QrPairing {
    const val PAIRING_SERVICE_TYPE = "_adb-tls-pairing._tcp"
    const val CONNECT_SERVICE_TYPE = "_adb-tls-connect._tcp"

    private const val SERVICE_NAME_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
    private val ESCAPE_CHARS = setOf('\\', ';', ',', ':', '"')

    fun generate(random: Random = Random): QrPairingCredentials {
        val serviceName = buildString {
            append("studio-")
            repeat(10) {
                append(SERVICE_NAME_CHARS[random.nextInt(SERVICE_NAME_CHARS.length)])
            }
        }
        val password = buildString {
            repeat(6) {
                append(random.nextInt(10))
            }
        }
        return QrPairingCredentials(serviceName = serviceName, password = password)
    }

    fun escape(value: String): String = buildString {
        for (char in value) {
            if (char in ESCAPE_CHARS) {
                append('\\')
            }
            append(char)
        }
    }

    fun payload(credentials: QrPairingCredentials): String =
        "WIFI:T:ADB;S:${escape(credentials.serviceName)};P:${escape(credentials.password)};;"
}
