package com.example.navigation.navigation

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

/** Explicit confidence level for an estimated navigation position. */
enum class PositionConfidence : Serializable {
    HIGH,
    MEDIUM,
    LOW,
    LOST
}

/** Origin of a navigation position estimate. */
enum class PositionSource : Serializable {
    MANUAL_START,
    GNSS,
    HYBRID,
    VEHICLE_DEAD_RECKONING,
    PEDESTRIAN_DEAD_RECKONING,
    MAP_MATCHED_ESTIMATE
}

/** Navigation mode used to produce or interpret the current estimate. */
enum class NavigationMode : Serializable {
    VEHICLE,
    PEDESTRIAN,
    UNKNOWN
}

/** Position estimate that always exposes uncertainty, provenance, and monotonic time. */
data class NavigationPosition(
    val latitude: Double,
    val longitude: Double,
    val confidence: PositionConfidence,
    val estimatedAccuracyMeters: Double,
    val source: PositionSource,
    val timestamp: MonotonicTimestamp
) : Serializable {
    private companion object { private const val serialVersionUID = 1L }
}
