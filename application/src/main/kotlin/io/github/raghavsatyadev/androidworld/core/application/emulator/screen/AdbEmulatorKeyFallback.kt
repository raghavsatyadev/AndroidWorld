package io.github.raghavsatyadev.androidworld.core.application.emulator.screen

import io.github.raghavsatyadev.androidworld.core.domain.adb.AdbTransport
import io.github.raghavsatyadev.androidworld.core.domain.adb.DeviceSerial
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorInput
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorKeyFallback
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorKeys
import io.github.raghavsatyadev.androidworld.core.domain.input.InputCommands
import io.github.raghavsatyadev.androidworld.core.domain.input.SystemKey
import kotlinx.coroutines.CancellationException

/** Delivers keys and text via ADB shell commands when gRPC key input is unavailable. */
class AdbEmulatorKeyFallback(
    private val transport: AdbTransport,
) : EmulatorKeyFallback {

    override suspend fun send(serial: String, input: EmulatorInput): Boolean {
        return try {
            when (input) {
                is EmulatorInput.Key -> {
                    val systemKey = mapKey(input.key) ?: return false
                    transport.executeText(InputCommands.key(DeviceSerial.of(serial), systemKey))
                    true
                }
                is EmulatorInput.Text -> {
                    if (!InputCommands.canType(input.text)) return false
                    transport.executeText(InputCommands.text(DeviceSerial.of(serial), input.text))
                    true
                }
                is EmulatorInput.Touch,
                is EmulatorInput.Mouse -> false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun mapKey(key: String): SystemKey? = when (key) {
        EmulatorKeys.HOME -> SystemKey.Home
        EmulatorKeys.BACK -> SystemKey.Back
        EmulatorKeys.RECENTS -> SystemKey.Recents
        EmulatorKeys.POWER -> SystemKey.Power
        EmulatorKeys.VOLUME_UP -> SystemKey.VolumeUp
        EmulatorKeys.VOLUME_DOWN -> SystemKey.VolumeDown
        EmulatorKeys.ENTER -> SystemKey.Enter
        EmulatorKeys.BACKSPACE -> SystemKey.Delete
        EmulatorKeys.TAB -> SystemKey.Tab
        EmulatorKeys.ESCAPE -> SystemKey.Escape
        EmulatorKeys.ARROW_LEFT -> SystemKey.Left
        EmulatorKeys.ARROW_RIGHT -> SystemKey.Right
        EmulatorKeys.ARROW_UP -> SystemKey.Up
        EmulatorKeys.ARROW_DOWN -> SystemKey.Down
        else -> null
    }
}
