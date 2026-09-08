package com.example.navigation.obd

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObdReconnectionControllerTest {
    @Test
    fun reportsSpeedUnavailableThenAvailableAfterReconnectAndInitialization() {
        val listener = RecordingListener()
        val transport = ReconnectingTransport()
        val executor = Executors.newSingleThreadScheduledExecutor()
        val controller = ObdReconnectionController(
            transport,
            Elm327Initializer(transport),
            listener,
            ObdReconnectPolicy(1, 2),
            executor
        )

        controller.onSpeedUnavailable("Bluetooth disconnected")
        executor.shutdown()
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))

        assertEquals(1, transport.reconnects)
        assertEquals(VehicleSpeedAvailability.UNAVAILABLE, listener.availability.first().first)
        assertEquals(VehicleSpeedAvailability.AVAILABLE, listener.availability.last().first)
        controller.close()
    }

    private class RecordingListener : VehicleSpeedPollingListener {
        val availability = mutableListOf<Pair<VehicleSpeedAvailability, String?>>()
        override fun onSpeedAvailabilityChanged(availability: VehicleSpeedAvailability, reason: String?) {
            this.availability += availability to reason
        }
    }

    private class ReconnectingTransport : ObdTransport {
        var reconnects = 0
        override val isConnected = true
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun reconnect() { reconnects++ }
        override fun close() = Unit
        override fun command(command: String, timeoutMillis: Long): String = when (command) {
            "ATZ" -> "ELM327 v1.5"
            "ATI" -> "ELM327"
            else -> "OK"
        }
    }
}
