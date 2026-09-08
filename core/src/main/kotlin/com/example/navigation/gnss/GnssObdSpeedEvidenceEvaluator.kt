package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import java.util.ArrayDeque
import kotlin.math.abs

data class GnssObdSpeedEvidenceConfig(
    val windowNanos: Long = 5_000_000_000L,
    val obdDropoutNanos: Long = 2_000_000_000L,
    val lowSpeedMetersPerSecond: Double = 1.0,
    val fullAgreementDifferenceMetersPerSecond: Double = 0.5,
    val fullDisagreementDifferenceMetersPerSecond: Double = 5.0
) {
    init {
        require(windowNanos > 0 && obdDropoutNanos > 0)
        require(lowSpeedMetersPerSecond >= 0.0 && lowSpeedMetersPerSecond.isFinite())
        require(fullAgreementDifferenceMetersPerSecond >= 0.0 && fullAgreementDifferenceMetersPerSecond.isFinite())
        require(fullDisagreementDifferenceMetersPerSecond > fullAgreementDifferenceMetersPerSecond)
    }
}

/** Rolling GNSS/OBD speed consistency evidence; it never rejects or substitutes measurements. */
class GnssObdSpeedEvidenceEvaluator(private val config: GnssObdSpeedEvidenceConfig = GnssObdSpeedEvidenceConfig()) {
    private val gnssSpeeds = ArrayDeque<Pair<MonotonicTimestamp, Double>>()
    private val obdSpeeds = ArrayDeque<Pair<MonotonicTimestamp, Double>>()
    private var latestGnssSpeedTimestamp: MonotonicTimestamp? = null
    private var latestGnssUnavailableTimestamp: MonotonicTimestamp? = null
    private var latestObdTimestamp: MonotonicTimestamp? = null

    fun observeGnss(sample: GnssSample) {
        if (sample.speedMetersPerSecond == null) latestGnssUnavailableTimestamp = sample.timestamp
        else {
            gnssSpeeds += sample.timestamp to sample.speedMetersPerSecond.toDouble()
            latestGnssSpeedTimestamp = sample.timestamp
        }
        trim(sample.timestamp)
    }

    fun observeObd(sample: VehicleSpeedSample) {
        if (sample.source == VehicleSpeedSource.OBD || sample.source == VehicleSpeedSource.REPLAY) {
            obdSpeeds += sample.timestamp to sample.metersPerSecond
            latestObdTimestamp = sample.timestamp
            trim(sample.timestamp)
        }
    }

    fun evaluate(timestamp: MonotonicTimestamp): GnssTrustEvidence {
        trim(timestamp)
        if (latestGnssUnavailableTimestamp != null && (latestGnssSpeedTimestamp == null || latestGnssUnavailableTimestamp!! >= latestGnssSpeedTimestamp!!)) {
            return unavailable(timestamp, "GNSS velocity is unavailable")
        }
        val latestObd = latestObdTimestamp ?: return unavailable(timestamp, "OBD speed is unavailable")
        if (timestamp.differenceNanos(latestObd) > config.obdDropoutNanos) return unavailable(timestamp, "OBD speed dropout exceeds configured limit")
        if (gnssSpeeds.isEmpty()) return unavailable(timestamp, "No GNSS velocity is available in the rolling window")
        if (obdSpeeds.isEmpty()) return unavailable(timestamp, "No OBD speed is available in the rolling window")
        val gnssMean = gnssSpeeds.map { it.second }.average()
        val obdMean = obdSpeeds.map { it.second }.average()
        val difference = abs(gnssMean - obdMean)
        val score = scoreFor(difference, minOf(gnssMean, obdMean) <= config.lowSpeedMetersPerSecond)
        return GnssTrustEvidence(
            GnssTrustEvidenceType.SPEED_CONSISTENCY,
            GnssTrustEvidenceAvailability.AVAILABLE,
            score,
            "Rolling GNSS speed=${format(gnssMean)}m/s; OBD speed=${format(obdMean)}m/s; difference=${format(difference)}m/s",
            timestamp
        )
    }

    private fun scoreFor(difference: Double, lowSpeed: Boolean): Double {
        val agreement = if (lowSpeed) config.fullAgreementDifferenceMetersPerSecond * 2.0 else config.fullAgreementDifferenceMetersPerSecond
        val disagreement = if (lowSpeed) config.fullDisagreementDifferenceMetersPerSecond * 2.0 else config.fullDisagreementDifferenceMetersPerSecond
        return ((disagreement - difference) / (disagreement - agreement)).coerceIn(0.0, 1.0)
    }

    private fun trim(timestamp: MonotonicTimestamp) {
        val cutoff = timestamp.nanos - config.windowNanos
        while (gnssSpeeds.isNotEmpty() && gnssSpeeds.first.first.nanos < cutoff) gnssSpeeds.removeFirst()
        while (obdSpeeds.isNotEmpty() && obdSpeeds.first.first.nanos < cutoff) obdSpeeds.removeFirst()
    }

    private fun unavailable(timestamp: MonotonicTimestamp, detail: String) = GnssTrustEvidence(
        GnssTrustEvidenceType.SPEED_CONSISTENCY, GnssTrustEvidenceAvailability.UNAVAILABLE, null, detail, timestamp
    )
    private fun format(value: Double) = "%.2f".format(java.util.Locale.ROOT, value)
}
