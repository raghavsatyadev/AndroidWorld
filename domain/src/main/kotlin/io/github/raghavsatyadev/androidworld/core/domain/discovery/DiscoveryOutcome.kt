package io.github.raghavsatyadev.androidworld.core.domain.discovery

/** The result of one [ToolLocator.locate] call. */
sealed interface DiscoveryOutcome {
    data class Found(val tool: DiscoveredTool) : DiscoveryOutcome

    data class Failed(val error: DiscoveryError) : DiscoveryOutcome
}
