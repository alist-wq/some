package com.example.navigation.obd

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VehicleSpeedProviderTest {
    @Test
    fun parsesPid0dAndPreservesRawResponse() {
        val raw = "010D\r41 0D 5A\r>"
        val sample = VehicleSpeedProvider(FakeTransport(raw)).read(MonotonicTimestamp(42L))

        assertEquals(90.0 / 3.6, sample.metersPerSecond)
        assertEquals(MonotonicTimestamp(42L), sample.timestamp)
        assertEquals(VehicleSpeedSource.OBD, sample.source)
        assertEquals(raw, sample.rawResponse)
    }

    @Test
    fun parsesCompactPidResponse() {
        val sample = VehicleSpeedProvider(FakeTransport("410D0A>" )).read(MonotonicTimestamp(1L))

        assertEquals(10.0 / 3.6, sample.metersPerSecond)
    }

    @Test
    fun rejectsMissingPidResponse() {
        assertFailsWith<ObdTransportException> {
            VehicleSpeedProvider(FakeTransport("NO DATA\r>" )).read(MonotonicTimestamp(1L))
        }
    }

    private class FakeTransport(private val response: String) : ObdTransport {
        override val isConnected = true
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun reconnect() = Unit
        override fun command(command: String, timeoutMillis: Long): String {
            assertEquals("010D", command)
            return response
        }
        override fun close() = Unit
    }
}
