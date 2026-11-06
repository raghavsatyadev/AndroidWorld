package io.github.raghavsatyadev.androidworld.desktop.ui.nav

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NavigatorTest {
    @Test
    fun `decode reads the stored form`() {
        Location.decode("Logcat/Logcat") shouldBe Location(Area.Logcat, Section.Logcat)
        Location.decode(Location(Area.Apps, Section.Installed).encode()) shouldBe Location(Area.Apps, Section.Installed)
    }

    @Test
    fun `decode maps values written by older builds`() {
        Location.decode("ScreenShare") shouldBe Location(Area.Screen, Section.Share)
        Location.decode("Device") shouldBe Location(Area.Device, Section.Overview)
        Location.decode("Settings") shouldBe Location(Area.Settings, Section.General)
    }

    @Test
    fun `decode falls back to the start for missing or unknown values`() {
        Location.decode(null) shouldBe Location.Start
        Location.decode("") shouldBe Location.Start
        Location.decode("Nope/Nothing") shouldBe Location.Start
    }

    @Test
    fun `decode keeps the area when only the section is stale`() {
        Location.decode("Apps/Gone") shouldBe Location(Area.Apps, Area.Apps.sections.first())
    }

    @Test
    fun `persist fires once per change and not for a no-op`() {
        val saved = mutableListOf<Location>()
        val nav = Navigator(Location.Start) { saved += it }
        nav.open(Area.Device)
        nav.open(Area.Logcat)
        nav.open(Area.Logcat)
        saved shouldContainExactly listOf(Location(Area.Logcat, Section.Logcat))
        nav.location.value shouldBe Location(Area.Logcat, Section.Logcat)
    }

    @Test
    fun `returning to an area restores its last section`() {
        val area = Area.entries.firstOrNull { it.sections.size > 1 } ?: return
        val nav = Navigator(Location.Start)
        nav.open(area, area.sections.last())
        nav.open(Area.Settings)
        nav.open(area)
        nav.location.value shouldBe Location(area, area.sections.last())
    }
}
