package com.mattschoe.smarthome.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select

/**
 * Exponential reconnect backoff that also understands platform connectivity transitions.
 *
 * A known-offline platform never burns through timer-driven attempts. While online, either the
 * current delay or a complete loss/restoration transition wakes the caller; restoration is
 * immediate and resets the backoff. Server failures still retry on the timer because having a
 * network path does not imply that the server itself is reachable.
 */
internal class ReconnectPolicy(
    private val networkMonitor: NetworkMonitor,
    private val initialDelayMillis: Long = 1_000L,
    private val maximumDelayMillis: Long = 30_000L,
) {
    internal var nextDelayMillis: Long = initialDelayMillis
        private set

    init {
        require(initialDelayMillis > 0) { "initialDelayMillis must be positive" }
        require(maximumDelayMillis >= initialDelayMillis) {
            "maximumDelayMillis must not be less than initialDelayMillis"
        }
    }

    /** Suspend connection attempts while the platform knows there is no usable network. */
    suspend fun awaitNetwork() {
        networkMonitor.isAvailable.first { it }
    }

    /** Wait for the next timed retry, unless connectivity restoration should wake it sooner. */
    suspend fun awaitRetry() {
        if (!networkMonitor.isAvailable.value) {
            awaitNetwork()
            reset()
            return
        }

        val wake = coroutineScope {
            val timer = async {
                delay(nextDelayMillis)
                Wake.Timer
            }
            val restoration = async {
                awaitRestoration()
                Wake.Restoration
            }
            try {
                select {
                    timer.onAwait { it }
                    restoration.onAwait { it }
                }
            } finally {
                timer.cancel()
                restoration.cancel()
            }
        }

        when (wake) {
            Wake.Restoration -> reset()
            Wake.Timer -> {
                // If the timer and a network loss raced, do not return a doomed attempt. Waiting here
                // also gives that restoration the same backoff-reset semantics as the watcher path.
                if (!networkMonitor.isAvailable.value) {
                    awaitNetwork()
                    reset()
                } else {
                    nextDelayMillis = (nextDelayMillis * 2).coerceAtMost(maximumDelayMillis)
                }
            }
        }
    }

    /** A fully initialized session starts any later outage back at the short retry delay. */
    fun reset() {
        nextDelayMillis = initialDelayMillis
    }

    private suspend fun awaitRestoration() {
        var sawLoss = !networkMonitor.isAvailable.value
        networkMonitor.isAvailable.first { available ->
            if (!available) sawLoss = true
            available && sawLoss
        }
    }

    private enum class Wake { Timer, Restoration }
}
