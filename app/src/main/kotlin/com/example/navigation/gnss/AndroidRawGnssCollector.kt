package com.example.navigation.gnss

import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.LocationManager
import android.os.SystemClock
import com.example.navigation.core.MonotonicTimestamp

fun interface RawGnssSampleListener {
    fun onRawGnssSample(sample: RawGnssSample)
}

/** Collects raw GNSS epochs and immediately converts Android objects to storage-safe models. */
class AndroidRawGnssCollector(
    private val locationManager: LocationManager,
    private val listener: RawGnssSampleListener
) {
    private val lifecycle = GnssCollectorLifecycle()

    private val callback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            listener.onRawGnssSample(eventArgs.toRawGnssSample(MonotonicTimestamp(SystemClock.elapsedRealtimeNanos())))
        }
    }

    /** Returns false when the device declines raw GNSS measurement registration. */
    fun start(): Boolean {
        return lifecycle.start { locationManager.registerGnssMeasurementsCallback(callback) } == EvidenceCapability.AVAILABLE
    }

    fun stop() {
        lifecycle.stop { locationManager.unregisterGnssMeasurementsCallback(callback) }
    }

    fun capability(): EvidenceCapability = lifecycle.capability()

    private fun GnssMeasurementsEvent.toRawGnssSample(timestamp: MonotonicTimestamp): RawGnssSample = RawGnssSample(
        timestamp = timestamp,
        clock = clock.toGnssClockInfo(),
        observations = measurements.map { it.toRawSatelliteObservation() }
    )

    private fun android.location.GnssClock.toGnssClockInfo() = GnssClockInfo(
        timeNanos = timeNanos,
        fullBiasNanos = fullBiasNanos.takeIf { hasFullBiasNanos() },
        biasNanos = biasNanos.takeIf { hasBiasNanos() },
        biasUncertaintyNanos = biasUncertaintyNanos.takeIf { hasBiasUncertaintyNanos() },
        driftNanosPerSecond = driftNanosPerSecond.takeIf { hasDriftNanosPerSecond() },
        driftUncertaintyNanosPerSecond = driftUncertaintyNanosPerSecond.takeIf { hasDriftUncertaintyNanosPerSecond() },
        leapSecond = leapSecond.takeIf { hasLeapSecond() },
        hardwareClockDiscontinuityCount = hardwareClockDiscontinuityCount
    )

    private fun GnssMeasurement.toRawSatelliteObservation() = RawGnssFields(
        svid = svid,
        constellation = constellationType.toConstellation(),
        carrierFrequencyHz = carrierFrequencyHz.takeIf { hasCarrierFrequencyHz() },
        cn0DbHz = cn0DbHz.toFloat(),
        automaticGainControlDb = automaticGainControlLevelDb.takeIf { hasAutomaticGainControlLevelDb() }?.toDouble(),
        pseudorangeRateMetersPerSecond = pseudorangeRateMetersPerSecond,
        pseudorangeRateUncertaintyMetersPerSecond = pseudorangeRateUncertaintyMetersPerSecond,
        accumulatedDeltaRangeMeters = accumulatedDeltaRangeMeters,
        accumulatedDeltaRangeUncertaintyMeters = accumulatedDeltaRangeUncertaintyMeters,
        stateFlags = state.toLong(),
        accumulatedDeltaRangeStateFlags = accumulatedDeltaRangeState.toLong(),
        multipathIndicator = multipathIndicator.toMultipathIndicator(),
        receivedSvTimeNanos = receivedSvTimeNanos,
        receivedSvTimeUncertaintyNanos = receivedSvTimeUncertaintyNanos
    ).toDomain()

    private fun Int.toConstellation() = when (this) {
        GnssStatus.CONSTELLATION_GPS -> GnssConstellation.GPS
        GnssStatus.CONSTELLATION_SBAS -> GnssConstellation.SBAS
        GnssStatus.CONSTELLATION_GLONASS -> GnssConstellation.GLONASS
        GnssStatus.CONSTELLATION_QZSS -> GnssConstellation.QZSS
        GnssStatus.CONSTELLATION_BEIDOU -> GnssConstellation.BEIDOU
        GnssStatus.CONSTELLATION_GALILEO -> GnssConstellation.GALILEO
        GnssStatus.CONSTELLATION_IRNSS -> GnssConstellation.IRNSS
        else -> GnssConstellation.UNKNOWN
    }

    private fun Int.toMultipathIndicator() = when (this) {
        GnssMeasurement.MULTIPATH_INDICATOR_DETECTED -> GnssMultipathIndicator.DETECTED
        GnssMeasurement.MULTIPATH_INDICATOR_NOT_DETECTED -> GnssMultipathIndicator.NOT_DETECTED
        else -> GnssMultipathIndicator.UNKNOWN
    }
}
