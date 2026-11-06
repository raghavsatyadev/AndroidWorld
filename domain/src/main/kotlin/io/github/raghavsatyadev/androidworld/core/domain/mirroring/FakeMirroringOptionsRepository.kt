package io.github.raghavsatyadev.androidworld.core.domain.mirroring

/** A deterministic [MirroringOptionsRepository] test double, mirroring
 * [io.github.raghavsatyadev.androidworld.core.domain.nav.FakeNavigationPersistence]. */
class FakeMirroringOptionsRepository(
    initial: MirroringOptions = MirroringOptions.DEFAULT,
) : MirroringOptionsRepository {

    private var stored: MirroringOptions = initial

    private val _writes = mutableListOf<MirroringOptions>()
    val writes: List<MirroringOptions> get() = _writes

    override suspend fun readOptions(): MirroringOptions = stored

    override suspend fun writeOptions(options: MirroringOptions) {
        _writes += options
        stored = options
    }
}
