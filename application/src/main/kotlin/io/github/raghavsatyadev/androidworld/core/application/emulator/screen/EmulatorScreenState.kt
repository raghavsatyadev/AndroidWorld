package io.github.raghavsatyadev.androidworld.core.application.emulator.screen

import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorFrame
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorHardware
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.FitRect
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.FoldPosture
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.ScreenRotation

/** Connection and streaming status of the emulator screen. */
sealed interface EmulatorScreenStatus {
    data object Idle : EmulatorScreenStatus
    data object Connecting : EmulatorScreenStatus
    data object Streaming : EmulatorScreenStatus
    data class Failed(val message: String) : EmulatorScreenStatus
}

/** State of the active emulator screen session. */
data class EmulatorScreenState(
    val serial: String? = null,
    val status: EmulatorScreenStatus = EmulatorScreenStatus.Idle,
    val frame: EmulatorFrame? = null,
    val hardware: EmulatorHardware? = null,
    val rotation: ScreenRotation = ScreenRotation.Portrait,
    val posture: FoldPosture? = null,
    val framesPerSecond: Int = 0,
)

/** Intents accepted by [EmulatorScreenViewModel]. */
sealed interface EmulatorScreenIntent {
    /**
     * The box is square so landscape frames keep the same resolution as portrait (the emulator fits
     * the rotated frame inside the box).
     */
    data class Start(val serial: String, val maxWidth: Int = 1600, val maxHeight: Int = 1600) : EmulatorScreenIntent
    data object Stop : EmulatorScreenIntent
    data class Pointer(val viewX: Float, val viewY: Float, val shown: FitRect, val pressed: Boolean) : EmulatorScreenIntent
    data class Key(val key: String) : EmulatorScreenIntent
    data class Text(val text: String) : EmulatorScreenIntent
    data object RotateLeft : EmulatorScreenIntent
    data object RotateRight : EmulatorScreenIntent
    data class SetPosture(val posture: FoldPosture) : EmulatorScreenIntent
}
