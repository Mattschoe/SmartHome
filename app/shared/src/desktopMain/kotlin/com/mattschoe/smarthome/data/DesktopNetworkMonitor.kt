package com.mattschoe.smarthome.data

import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Process-lifetime desktop monitor. Interface polling catches local link changes without contacting
 * a connectivity-check service; adapter backoff remains the fallback for upstream outages.
 */
class DesktopNetworkMonitor(
    private val pollIntervalMillis: Long = 2_000L,
) : NetworkMonitor {
    private val _isAvailable = MutableStateFlow(hasUsableInterface())
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        require(pollIntervalMillis > 0) { "pollIntervalMillis must be positive" }
        scope.launch {
            while (isActive) {
                val available = hasUsableInterface()
                if (_isAvailable.value != available) _isAvailable.value = available
                delay(pollIntervalMillis)
            }
        }
    }

    private fun hasUsableInterface(): Boolean = runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching false
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            if (!networkInterface.isUp || networkInterface.isLoopback) continue

            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                if (!addresses.nextElement().isLoopbackAddress) return@runCatching true
            }
        }
        false
    }.getOrDefault(false)
}
