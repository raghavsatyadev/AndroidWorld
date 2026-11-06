package io.github.raghavsatyadev.androidworld.desktop.persistence

import io.github.raghavsatyadev.androidworld.core.application.deeplinks.RecentDeviceLink
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StoreAdaptersTest {

    @TempDir
    lateinit var dir: Path

    private fun file() = dir.resolve("state.json")

    @Test
    fun `proxy recents survive a restart in order`() = runTest {
        val first = StoreAdapters(AppStore(file()))
        val recents = listOf("10.0.2.2:8888", "proxy.local:3128").map { endpointOrNull(it)!! }
        first.proxyRecents.writeRecents(recents)

        StoreAdapters(AppStore(file())).proxyRecents.readRecents() shouldBe recents
    }

    @Test
    fun `hand-edited proxy entries that do not parse are dropped`() = runTest {
        val store = AppStore(file())
        store.update { it.copy(recentProxies = listOf("10.0.2.2:8888", "no-port", ":80", "host:99999", "host:")) }

        StoreAdapters(store).proxyRecents.readRecents().map { it.render() } shouldBe listOf("10.0.2.2:8888")
    }

    @Test
    fun `deep-link memory round-trips history and form options`() {
        val first = StoreAdapters(AppStore(file())).deepLinkMemory
        val entries = listOf(
            RecentDeviceLink("myapp://a", "com.example", 1_000, "com.example/.Main"),
            RecentDeviceLink("https://example.com/b"),
        )
        first.replace(entries)
        first.lastPackage = "com.example"
        first.coldStart = true
        first.commandExpanded = true

        val second = StoreAdapters(AppStore(file())).deepLinkMemory
        second.entries() shouldBe entries
        second.lastPackage shouldBe "com.example"
        second.coldStart shouldBe true
        second.commandExpanded shouldBe true
    }

    @Test
    fun `deep-link history is capped`() {
        val memory = StoreAdapters(AppStore(file())).deepLinkMemory
        memory.replace((1..80).map { RecentDeviceLink("myapp://$it") })

        memory.entries().size shouldBe 50
        memory.entries().first().uri shouldBe "myapp://1"
    }

    @Test
    fun `a corrupt state file starts empty instead of failing`() {
        Files.writeString(file(), "{ not json")
        val adapters = StoreAdapters(AppStore(file()))

        adapters.deepLinkMemory.entries().shouldBeEmpty()
        adapters.deepLinkMemory.coldStart shouldBe false
    }
}
