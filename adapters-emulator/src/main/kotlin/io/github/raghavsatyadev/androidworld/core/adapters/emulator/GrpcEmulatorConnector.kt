package io.github.raghavsatyadev.androidworld.core.adapters.emulator

import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorConnection
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorConnector
import io.github.raghavsatyadev.androidworld.core.domain.emulator.screen.EmulatorEndpoint

/**
 * Creates [GrpcEmulatorConnection] instances connected to an emulator's gRPC service.
 */
class GrpcEmulatorConnector : EmulatorConnector {
    override fun connect(endpoint: EmulatorEndpoint): EmulatorConnection =
        GrpcEmulatorConnection(endpoint)
}
