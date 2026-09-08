package com.example.navigation.gnss

internal data class GnssStatusFields(
    val constellation: GnssConstellation,
    val svid: Int,
    val usedInFix: Boolean,
    val azimuthDegrees: Float,
    val elevationDegrees: Float,
    val cn0DbHz: Float,
    val carrierFrequencyHz: Float?,
    val hasEphemerisData: Boolean,
    val hasAlmanacData: Boolean
) {
    fun toDomain(satelliteCount: Int) = GnssSatelliteStatus(
        constellation, satelliteCount, usedInFix, azimuthDegrees, elevationDegrees, cn0DbHz,
        svid, carrierFrequencyHz, hasEphemerisData, hasAlmanacData
    )
}

internal data class RawGnssFields(
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
) {
    fun toDomain() = RawSatelliteObservation(
        svid, constellation, carrierFrequencyHz, cn0DbHz, automaticGainControlDb,
        pseudorangeRateMetersPerSecond, pseudorangeRateUncertaintyMetersPerSecond,
        accumulatedDeltaRangeMeters, accumulatedDeltaRangeUncertaintyMeters, stateFlags,
        accumulatedDeltaRangeStateFlags, multipathIndicator, receivedSvTimeNanos,
        receivedSvTimeUncertaintyNanos
    )
}
