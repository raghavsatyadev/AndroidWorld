package io.github.raghavsatyadev.androidworld.desktop.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A tab inside an [Area]. Areas with a single section show no tab row. */
enum class Section(val label: String) {
    Overview("Overview"),
    Installed("Installed"),
    Display("Display"),
    Developer("Developer"),
    Region("Region"),
    Network("Network"),
    Links("Deep links"),
    Share("Share"),
    Logcat("Logcat"),
    Avds("Emulators"),
    Commands("Commands"),
    Dumpsys("dumpsys"),
    General("General"),
}

/** A top-level rail destination, grouping related [Section]s so the rail stays short. */
enum class Area(val label: String, val icon: ImageVector, val sections: List<Section>) {
    Device("Device", Icons.Filled.PhoneAndroid, listOf(Section.Overview)),
    Apps("Apps", Icons.Filled.Apps, listOf(Section.Installed)),
    Controls(
        "Controls", Icons.Filled.Tune,
        listOf(Section.Display, Section.Developer, Section.Region, Section.Network, Section.Links),
    ),
    Screen("Screen", Icons.Filled.ScreenShare, listOf(Section.Share)),
    Logcat("Logcat", Icons.AutoMirrored.Filled.Notes, listOf(Section.Logcat)),
    Emulators("Emulators", Icons.Filled.Devices, listOf(Section.Avds)),
    Commands("Commands", Icons.Filled.Terminal, listOf(Section.Commands, Section.Dumpsys)),
    Settings("Settings", Icons.Filled.Settings, listOf(Section.General)),
}

/** Where the user is: an area and the section shown in it. */
data class Location(val area: Area, val section: Section) {
    init {
        require(section in area.sections) { "$section is not in $area" }
    }

    /** Stored as `Area/Section`, e.g. `Apps/Installed`. */
    fun encode(): String = "${area.name}/${section.name}"

    companion object {
        val Start = Location(Area.Device, Section.Overview)

        /** Older builds stored `ScreenShare`; unknown or stale values fall back to [Start]. */
        fun decode(value: String?): Location {
            if (value == null) return Start
            val areaName = value.substringBefore('/').let { if (it == "ScreenShare") Area.Screen.name else it }
            val area = Area.entries.firstOrNull { it.name == areaName } ?: return Start
            val section = area.sections.firstOrNull { it.name == value.substringAfter('/', "") } ?: area.sections.first()
            return Location(area, section)
        }
    }
}

/**
 * Navigation state, kept out of composables. Each area remembers its last section, so coming back
 * to an area lands where the user left it. [persist] receives every change.
 */
class Navigator(initial: Location, private val persist: (Location) -> Unit = {}) {
    private val _location = MutableStateFlow(initial)
    val location: StateFlow<Location> = _location.asStateFlow()

    private val lastSection = mutableMapOf(initial.area to initial.section)

    fun open(area: Area) = go(Location(area, lastSection[area] ?: area.sections.first()))

    fun open(area: Area, section: Section) = go(Location(area, section))

    fun select(section: Section) = go(Location(_location.value.area, section))

    private fun go(next: Location) {
        if (next == _location.value) return
        lastSection[next.area] = next.section
        _location.update { next }
        persist(next)
    }
}
