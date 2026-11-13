package io.github.raghavsatyadev.androidworld.desktop.ui

import androidx.compose.runtime.Composable
import io.github.raghavsatyadev.androidworld.desktop.di.AppGraph
import io.github.raghavsatyadev.androidworld.desktop.ui.apps.AppsScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.commands.CommandsScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.commands.DumpsysScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.commands.TerminalScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.files.FilesScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.content.ContentScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.DeveloperScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.DisplayScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.LinkLauncherScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.NetworkScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.controls.RegionScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.device.DeviceScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.emulators.EmulatorsScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.inspector.LayoutInspectorScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.logcat.LogcatScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.emulator.EmulatorScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.mirror.MirrorScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Location
import io.github.raghavsatyadev.androidworld.desktop.ui.nav.Section
import io.github.raghavsatyadev.androidworld.desktop.ui.settings.SettingsScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.system.DeviceSettingsScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.system.PropertiesScreen
import io.github.raghavsatyadev.androidworld.desktop.ui.system.ServicesScreen

/** Maps a [Location] to its screen. The only place that knows every screen. */
@Composable
fun Destinations(graph: AppGraph, location: Location, darkTheme: Boolean?, onDarkTheme: (Boolean?) -> Unit) {
    when (location.section) {
        Section.Overview -> DeviceScreen(graph)
        Section.Installed -> AppsScreen(graph.apps)
        Section.Display -> DisplayScreen(graph.controls)
        Section.Developer -> DeveloperScreen(graph.controls)
        Section.Region -> RegionScreen(graph.controls)
        Section.Network -> NetworkScreen(graph.controls)
        Section.Links -> LinkLauncherScreen(graph.controls)
        Section.Share -> MirrorScreen(graph)
        Section.Inspector -> LayoutInspectorScreen(graph.inspector)
        Section.Emulator -> EmulatorScreen(graph)
        Section.Logcat -> LogcatScreen(graph.logcat)
        Section.Files -> FilesScreen(graph.files)
        Section.Avds -> EmulatorsScreen(graph.emulators)
        Section.Commands -> CommandsScreen(graph.commands)
        Section.Dumpsys -> DumpsysScreen(graph.commands)
        Section.Terminal -> TerminalScreen(graph.commands)
        Section.Properties -> PropertiesScreen(graph.system)
        Section.DeviceSettings -> DeviceSettingsScreen(graph.system)
        Section.Services -> ServicesScreen(graph.system)
        Section.PersonalData -> ContentScreen(graph.content)
        Section.General -> SettingsScreen(graph, darkTheme, onDarkTheme)
    }
}
