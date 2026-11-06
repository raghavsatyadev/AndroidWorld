package io.github.raghavsatyadev.androidworld.core.domain.discovery

/** A deterministic [PathEnvironmentSource] test double: reports [directories] as-is, empty by
 * default. */
class FakePathEnvironmentSource(var directories: List<String> = emptyList()) : PathEnvironmentSource {
    override suspend fun directories(): List<String> = directories
}
