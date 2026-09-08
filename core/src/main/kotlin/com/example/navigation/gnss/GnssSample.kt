package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

/**
 * Immutable, processed GNSS observation suitable for recording and replay.
 *
 * The monotonic timestamp is the sensor-fusion time base. The optional wall
 * clock value is retained only as metadata and must not be used for ordering.
 * Optional measurements represent Android Location API values unavailable for
 * a particular observation; latitude, longitude, and provider are required.
 */
data class GnssSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val horizontalAccuracyMeters: Float?,
    val verticalAccuracyMeters: Float?,
    val speedMetersPerSecond: Float?,
    val speedAccuracyMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val bearingAccuracyDegrees: Float?,
    val provider: String,
    val timestamp: MonotonicTimestamp,
    val wallClockTimestampMillis: Long?
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}
