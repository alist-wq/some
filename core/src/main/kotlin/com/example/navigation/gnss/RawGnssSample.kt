package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

/**
 * A storage-safe raw GNSS measurement epoch that can be replayed without
 * Android framework objects. [timestamp] uses elapsed-realtime semantics.
 */
data class RawGnssSample(
    val timestamp: MonotonicTimestamp,
    val clock: GnssClockInfo,
    val observations: List<RawSatelliteObservation>
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** Storage-safe subset of GNSS clock data reported for a measurement epoch. */
data class GnssClockInfo(
    val timeNanos: Long,
    val fullBiasNanos: Long?,
    val biasNanos: Double?,
    val biasUncertaintyNanos: Double?,
    val driftNanosPerSecond: Double?,
    val driftUncertaintyNanosPerSecond: Double?,
    val leapSecond: Int?,
    val hardwareClockDiscontinuityCount: Int?
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** Storage-safe raw measurement for one satellite. */
data class RawSatelliteObservation(
    val svid: Int,
    val constellation: GnssConstellation,
    val carrierFrequencyHz: Float?,
    val cn0DbHz: Float?,
    val automaticGainControlDb: Double?,
    val pseudorangeRateMetersPerSecond: Double?,
    val pseudorangeRateUncertaintyMetersPerSecond: Double?,
    val accumulatedDeltaRangeMeters: Double?,
    val accumulatedDeltaRangeUncertaintyMeters: Double?,
    val stateFlags: Long,
    val accumulatedDeltaRangeStateFlags: Long?,
    val multipathIndicator: GnssMultipathIndicator,
    val receivedSvTimeNanos: Long?,
    val receivedSvTimeUncertaintyNanos: Long?
) : Serializable {
    private companion object {
        private const val serialVersionUID = 1L
    }
}

/** Satellite constellation identifiers independent of Android constants. */
enum class GnssConstellation : Serializable {
    GPS,
    SBAS,
    GLONASS,
    QZSS,
    BEIDOU,
    GALILEO,
    IRNSS,
    UNKNOWN
}

/** Multipath status recorded independently from Android framework values. */
enum class GnssMultipathIndicator : Serializable {
    DETECTED,
    NOT_DETECTED,
    UNKNOWN
}
