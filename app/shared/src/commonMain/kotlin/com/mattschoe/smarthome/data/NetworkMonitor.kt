package com.mattschoe.smarthome.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-lifetime view of whether the platform currently has a usable network path.
 *
 * Availability is deliberately only a connection gate, not a promise that either home server is
 * reachable. The adapters retain their timed retry for DNS, routing and server failures.
 */
interface NetworkMonitor {
    val isAvailable: StateFlow<Boolean>
}

/** Default for previews, tests and headless callers that do not own a platform network monitor. */
object AlwaysAvailableNetworkMonitor : NetworkMonitor {
    override val isAvailable: StateFlow<Boolean> = MutableStateFlow(true)
}
