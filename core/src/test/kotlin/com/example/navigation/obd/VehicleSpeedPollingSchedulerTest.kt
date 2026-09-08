package com.example.navigation.obd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleSpeedPollingSchedulerTest {
    @Test
    fun recordsLatencyPollRateAndTimeoutRate() {
        val listener = RecordingListener()
        var now = 0L
        val scheduler = VehicleSpeedPollingScheduler(
            VehicleSpeedProvider(SequenceTransport(listOf("41 0D 24", "timeout"))),
            listener = listener,
            timestampNanos = { now.also { now += 1_000_000_000L } }
        )

        scheduler.pollOnceForTest()
        scheduler.pollOnceForTest()

        val metrics = scheduler.metrics()
        assertEquals(2, metrics.pollCount)
        assertEquals(1, metrics.successfulPollCount)
        assertEquals(1, metrics.timeoutCount)
        assertEquals(0.5, metrics.timeoutRate)
        assertEquals(VehicleSpeedAvailability.UNAVAILABLE, listener.availability.last().first)
        scheduler.close()
    }

    @Test
    fun acceptsConfiguredAdapterRatesOnly() {
        assertTrue(runCatching { VehicleSpeedPollingScheduler(VehicleSpeedProvider(SequenceTransport(emptyList())), 4) }.isFailure)
        assertTrue(runCatching { VehicleSpeedPollingScheduler(VehicleSpeedProvider(SequenceTransport(emptyList())), 21) }.isFailure)
    }

    private class RecordingListener : VehicleSpeedPollingListener {
        val availability = mutableListOf<Pair<VehicleSpeedAvailability, String?>>()
        override fun onSpeedAvailabilityChanged(availability: VehicleSpeedAvailability, reason: String?) {
            this.availability += availability to reason
        }
    }

    private class SequenceTransport(private val responses: List<String>) : ObdTransport {
        private var index = 0
        override val isConnected = true
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun reconnect() = Unit
        override fun close() = Unit
        override fun command(command: String, timeoutMillis: Long): String {
            val response = responses.getOrElse(index++) { "timeout" }
            if (response == "timeout") throw ObdTimeoutException(command, timeoutMillis)
            return response
        }
    }
}
