package com.mattschoe.smarthome.data

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReconnectPolicyTest {

    @Test
    fun unavailableNetwork_blocksPastMaximumBackoffUntilRestored() = runTest {
        val network = FakeNetworkMonitor(false)
        val policy = ReconnectPolicy(network)
        val waiting = launch { policy.awaitRetry() }

        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(waiting.isActive)

        network.available.value = true
        runCurrent()
        assertTrue(waiting.isCompleted)
        assertEquals(1_000L, policy.nextDelayMillis)
    }

    @Test
    fun restorationWakesCurrentBackoffImmediatelyAndResetsIt() = runTest {
        val network = FakeNetworkMonitor(true)
        val policy = ReconnectPolicy(network)
        val waiting = launch { policy.awaitRetry() }

        advanceTimeBy(500)
        network.available.value = false
        runCurrent()
        network.available.value = true
        runCurrent()

        assertTrue(waiting.isCompleted)
        assertEquals(500L, testScheduler.currentTime)
        assertEquals(1_000L, policy.nextDelayMillis)
    }

    @Test
    fun continuouslyAvailableNetworkUsesTimedRetryAndDoublesDelay() = runTest {
        val policy = ReconnectPolicy(FakeNetworkMonitor(true))
        val waiting = launch { policy.awaitRetry() }

        advanceTimeBy(999)
        runCurrent()
        assertFalse(waiting.isCompleted)

        advanceTimeBy(1)
        runCurrent()
        assertTrue(waiting.isCompleted)
        assertEquals(2_000L, policy.nextDelayMillis)
    }

    @Test
    fun timedFailuresDoubleAndCapAtThirtySeconds() = runTest {
        val policy = ReconnectPolicy(FakeNetworkMonitor(true))
        val expectedDelays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L)

        expectedDelays.forEach { delay ->
            assertEquals(delay, policy.nextDelayMillis)
            val waiting = launch { policy.awaitRetry() }
            advanceTimeBy(delay)
            runCurrent()
            assertTrue(waiting.isCompleted)
        }
        assertEquals(30_000L, policy.nextDelayMillis)
    }

    @Test
    fun successfulConnectionResetsDelay() = runTest {
        val policy = ReconnectPolicy(FakeNetworkMonitor(true))
        val waiting = launch { policy.awaitRetry() }
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(waiting.isCompleted)
        assertEquals(2_000L, policy.nextDelayMillis)

        policy.reset()

        assertEquals(1_000L, policy.nextDelayMillis)
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        val policy = ReconnectPolicy(FakeNetworkMonitor(false))
        val waiting = launch { policy.awaitRetry() }
        runCurrent()

        waiting.cancelAndJoin()

        assertTrue(waiting.isCancelled)
    }
}

private class FakeNetworkMonitor(initiallyAvailable: Boolean) : NetworkMonitor {
    val available = MutableStateFlow(initiallyAvailable)
    override val isAvailable: StateFlow<Boolean> = available
}
