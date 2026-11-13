package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

import kotlinx.coroutines.flow.Flow

/** Resolves an emulator's gRPC endpoint from its device serial (e.g. "emulator-5554"). */
fun interface EmulatorEndpointLocator {
    suspend fun find(serial: String): EmulatorEndpoint?
}

/** Factory creating an [EmulatorConnection] to a discovered gRPC endpoint. */
fun interface EmulatorConnector {
    fun connect(endpoint: EmulatorEndpoint): EmulatorConnection
}

/** Active connection to an emulator's gRPC service for screen streaming and control. */
interface EmulatorConnection : AutoCloseable {
    /** Streams screen frames scaled within [maxWidth] and [maxHeight]. */
    fun frames(maxWidth: Int, maxHeight: Int): Flow<EmulatorFrame>

    /** Injects a user input event (touch, key, or text). */
    suspend fun send(input: EmulatorInput)

    /** Rotates the device to the specified [rotation]. */
    suspend fun rotate(rotation: ScreenRotation)

    /** Sets the foldable device posture. */
    suspend fun setPosture(posture: FoldPosture)

    /** Reads the hardware configuration of the emulator. */
    suspend fun hardware(): EmulatorHardware
}
