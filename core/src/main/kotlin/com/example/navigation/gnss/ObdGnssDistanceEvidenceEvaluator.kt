package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import java.util.ArrayDeque
import kotlin.math.*

data class ObdGnssDistanceEvidenceConfig(
    val windowNanos: Long = 10_000_000_000L,
    val absoluteToleranceMeters: Double = 5.0,
    val relativeTolerance: Double = 0.15,
    val scoreDecayMeters: Double = 20.0
) {
    init {
        require(windowNanos > 0) { "Distance consistency window must be positive" }
        require(absoluteToleranceMeters.isFinite() && absoluteToleranceMeters >= 0.0)
        require(relativeTolerance.isFinite() && relativeTolerance >= 0.0)
        require(scoreDecayMeters.isFinite() && scoreDecayMeters > 0.0)
    }
}

/** Compares integrated OBD distance with the full GNSS trajectory inside a rolling window. */
class ObdGnssDistanceEvidenceEvaluator(
    private val config: ObdGnssDistanceEvidenceConfig = ObdGnssDistanceEvidenceConfig()
) {
    private val gnssSamples = ArrayDeque<GnssSample>()
    private val obdSamples = ArrayDeque<VehicleSpeedSample>()

    fun observeGnss(sample: GnssSample) {
        require(gnssSamples.isEmpty() || sample.timestamp > gnssSamples.last.timestamp) {
            "GNSS samples must be strictly increasing"
        }
        gnssSamples += sample
        trim(sample.timestamp)
    }

    fun observeObd(sample: VehicleSpeedSample) {
        if (sample.source != VehicleSpeedSource.OBD && sample.source != VehicleSpeedSource.REPLAY) return
        require(sample.metersPerSecond.isFinite() && sample.metersPerSecond >= 0.0)
        require(obdSamples.isEmpty() || sample.timestamp > obdSamples.last.timestamp) {
            "OBD samples must be strictly increasing"
        }
        obdSamples += sample
        trim(sample.timestamp)
    }

    fun evaluate(timestamp: MonotonicTimestamp): GnssTrustEvidence {
        trim(timestamp)
        if (gnssSamples.size < 2) return unavailable(timestamp, "At least two GNSS samples are required in the rolling window")
        if (obdSamples.size < 2) return unavailable(timestamp, "At least two OBD samples are required in the rolling window")

        val cutoffNanos = timestamp.nanos - config.windowNanos
        val gnssDistance = trajectoryDistance(cutoffNanos, timestamp.nanos)
        val obdDistance = integratedObdDistance(cutoffNanos, timestamp.nanos)
        val difference = abs(gnssDistance - obdDistance)
        val tolerance = config.absoluteToleranceMeters + config.relativeTolerance * obdDistance
        val excess = (difference - tolerance).coerceAtLeast(0.0)
        val score = exp(-excess / config.scoreDecayMeters)

        return GnssTrustEvidence(
            type = GnssTrustEvidenceType.DISTANCE_CONSISTENCY,
            availability = GnssTrustEvidenceAvailability.AVAILABLE,
            trustScore = score,
            detail = "Rolling GNSS trajectory=${format(gnssDistance)}m; integrated OBD=${format(obdDistance)}m; difference=${format(difference)}m",
            timestamp = timestamp
        )
    }

    private fun trajectoryDistance(startNanos: Long, endNanos: Long): Double =
        gnssSamples.zipWithNext().sumOf { (first, second) ->
            val fraction = overlapFraction(first.timestamp.nanos, second.timestamp.nanos, startNanos, endNanos)
            distanceMeters(first.latitude, first.longitude, second.latitude, second.longitude) * fraction
        }

    private fun integratedObdDistance(startNanos: Long, endNanos: Long): Double =
        obdSamples.zipWithNext().sumOf { (first, second) ->
            val segmentStart = first.timestamp.nanos
            val segmentEnd = second.timestamp.nanos
            val overlapStart = max(segmentStart, startNanos)
            val overlapEnd = min(segmentEnd, endNanos)
            if (overlapEnd <= overlapStart) 0.0 else {
                val duration = (segmentEnd - segmentStart).toDouble()
                val startSpeed = lerp(first.metersPerSecond, second.metersPerSecond, (overlapStart - segmentStart) / duration)
                val endSpeed = lerp(first.metersPerSecond, second.metersPerSecond, (overlapEnd - segmentStart) / duration)
                (startSpeed + endSpeed) / 2.0 * (overlapEnd - overlapStart) / NANOS_PER_SECOND
            }
        }

    private fun trim(timestamp: MonotonicTimestamp) {
        val cutoff = timestamp.nanos - config.windowNanos
        while (gnssSamples.size > 1 && gnssSamples.elementAt(1).timestamp.nanos <= cutoff) gnssSamples.removeFirst()
        while (obdSamples.size > 1 && obdSamples.elementAt(1).timestamp.nanos <= cutoff) obdSamples.removeFirst()
    }

    private fun overlapFraction(segmentStart: Long, segmentEnd: Long, windowStart: Long, windowEnd: Long): Double {
        val overlap = (min(segmentEnd, windowEnd) - max(segmentStart, windowStart)).coerceAtLeast(0L)
        return if (segmentEnd <= segmentStart) 0.0 else overlap.toDouble() / (segmentEnd - segmentStart)
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun lerp(start: Double, end: Double, fraction: Double) = start + (end - start) * fraction
    private fun unavailable(timestamp: MonotonicTimestamp, detail: String) = GnssTrustEvidence(
        GnssTrustEvidenceType.DISTANCE_CONSISTENCY,
        GnssTrustEvidenceAvailability.UNAVAILABLE,
        null,
        detail,
        timestamp
    )
    private fun format(value: Double) = "%.2f".format(java.util.Locale.ROOT, value)

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
