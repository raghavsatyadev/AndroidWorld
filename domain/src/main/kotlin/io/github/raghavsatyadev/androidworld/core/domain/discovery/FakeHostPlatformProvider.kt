package io.github.raghavsatyadev.androidworld.core.domain.discovery

/** A deterministic [HostPlatformProvider] test double: reports [operatingSystem] as-is, defaulting
 * to [OperatingSystem.Linux]. */
class FakeHostPlatformProvider(
    var operatingSystem: OperatingSystem = OperatingSystem.Linux,
) : HostPlatformProvider {
    override fun current(): OperatingSystem = operatingSystem
}
