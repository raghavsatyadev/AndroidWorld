package io.github.raghavsatyadev.androidworld.core.domain.discovery

/** Reports the host [OperatingSystem]. The only place tool discovery reads JVM system properties —
 * always behind this port. */
interface HostPlatformProvider {
    fun current(): OperatingSystem
}
