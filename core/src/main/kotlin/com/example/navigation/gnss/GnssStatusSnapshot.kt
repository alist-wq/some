package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

/** Snapshot of satellite visibility and signal evidence at one monotonic time. */
data class GnssStatusSnapshot(
    val timestamp: MonotonicTimestamp,
    val satellites: List<GnssSatelliteStatus>
) : Serializable

data class GnssSatelliteStatus(
    val constellation: GnssConstellation,
    val satelliteCount: Int,
    val usedInFix: Boolean,
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
    val cn0DbHz: Float,
    val svid: Int? = null,
    val carrierFrequencyHz: Float? = null,
    val hasEphemerisData: Boolean? = null,
    val hasAlmanacData: Boolean? = null
) : Serializable
