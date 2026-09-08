package com.example.navigation.obd

import com.example.navigation.core.MonotonicTimestamp

/** Reads Mode 01 PID 0D (vehicle speed) and converts km/h to metres per second. */
class VehicleSpeedProvider(
    private val transport: ObdTransport,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {
    init { require(timeoutMillis > 0) { "timeoutMillis must be positive" } }

    /** Sends the PID request and retains the unmodified adapter response for diagnostics. */
    fun read(timestamp: MonotonicTimestamp): VehicleSpeedSample {
        val rawResponse = transport.command(PID_REQUEST, timeoutMillis)
        val speedByte = SPEED_RESPONSE.find(rawResponse)?.groupValues?.get(1)
            ?: throw ObdTransportException("Invalid vehicle-speed response: $rawResponse")
        val kilometersPerHour = speedByte.toIntOrNull(16)
            ?: throw ObdTransportException("Invalid vehicle-speed byte: $speedByte")
        return VehicleSpeedSample(
            metersPerSecond = kilometersPerHour / KILOMETERS_PER_HOUR_PER_METERS_PER_SECOND,
            timestamp = timestamp,
            source = VehicleSpeedSource.OBD,
            rawResponse = rawResponse
        )
    }

    private companion object {
        const val PID_REQUEST = "010D"
        const val DEFAULT_TIMEOUT_MILLIS = 2_000L
        const val KILOMETERS_PER_HOUR_PER_METERS_PER_SECOND = 3.6
        val SPEED_RESPONSE = Regex("(?i)41\\s*0D\\s*([0-9A-F]{2})")
    }
}
