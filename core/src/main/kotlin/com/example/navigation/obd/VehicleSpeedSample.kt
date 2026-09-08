package com.example.navigation.obd

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

/** Origin of a vehicle-speed observation. No source is silently substituted. */
enum class VehicleSpeedSource : Serializable {
    OBD,
    REPLAY,
    UNKNOWN
}

/** Vehicle speed telemetry sample, stored independently of transport/framework types. */
data class VehicleSpeedSample(
    val metersPerSecond: Double,
    val timestamp: MonotonicTimestamp,
    val source: VehicleSpeedSource,
    val rawResponse: String = ""
) : Serializable {
    private companion object { private const val serialVersionUID = 1L }
}
