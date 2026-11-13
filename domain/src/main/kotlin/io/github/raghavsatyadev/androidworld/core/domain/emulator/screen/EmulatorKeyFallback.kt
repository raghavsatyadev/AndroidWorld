package io.github.raghavsatyadev.androidworld.core.domain.emulator.screen

/** Delivers keys and text another way when the emulator drops gRPC keys (AVDs with hw.keyboard=no have no key device). */
fun interface EmulatorKeyFallback {
    /** True when it handled [input]. */
    suspend fun send(serial: String, input: EmulatorInput): Boolean
}
