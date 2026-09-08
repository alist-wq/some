package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

data class IndependentMotionRemoteGnssObservation(
    val gnss: GnssSample,
    val cumulativeObdDistanceMeters: Double? = null,
    val cumulativePdrDistanceMeters: Double? = null,
    val cumulativeGyroTurnDegrees: Double? = null,
    val cumulativePdrTurnDegrees: Double? = null,
    val routeProgressMeters: Double? = null
)

data class IndependentMotionRemoteGnssConfig(
    val distanceToleranceMeters: Double = 30.0,
    val turnToleranceDegrees: Double = 25.0,
    val scoreDecay: Double = 40.0
) { init { require(distanceToleranceMeters >= 0 && turnToleranceDegrees >= 0 && scoreDecay > 0) } }

/** Compares GNSS geometry, not GNSS-reported speed/bearing, with accumulated independent motion. */
class IndependentMotionRemoteGnssEvidenceEvaluator(private val config: IndependentMotionRemoteGnssConfig = IndependentMotionRemoteGnssConfig()) {
    private var previous: IndependentMotionRemoteGnssObservation? = null
    private var previousTrajectoryBearing: Double? = null

    fun observe(current: IndependentMotionRemoteGnssObservation): GnssTrustEvidence {
        val prior = previous
        previous = current
        if (prior == null || current.gnss.timestamp <= prior.gnss.timestamp) return unavailable(current.gnss.timestamp, "a strictly later GNSS observation is required")
        val gnssDistance = distance(prior.gnss, current.gnss)
        val currentBearing = bearing(prior.gnss, current.gnss)
        val gnssTurn = previousTrajectoryBearing?.let { angularDifference(it, currentBearing) }
        val mismatches = buildList {
            delta(prior.cumulativeObdDistanceMeters, current.cumulativeObdDistanceMeters)?.let { add(abs(gnssDistance - it) - config.distanceToleranceMeters) }
            delta(prior.cumulativePdrDistanceMeters, current.cumulativePdrDistanceMeters)?.let { add(abs(gnssDistance - it) - config.distanceToleranceMeters) }
            delta(prior.routeProgressMeters, current.routeProgressMeters)?.let { add(abs(gnssDistance - abs(it)) - config.distanceToleranceMeters) }
            if (gnssTurn != null) delta(prior.cumulativeGyroTurnDegrees, current.cumulativeGyroTurnDegrees)?.let { add(abs(gnssTurn - abs(it)) - config.turnToleranceDegrees) }
            if (gnssTurn != null) delta(prior.cumulativePdrTurnDegrees, current.cumulativePdrTurnDegrees)?.let { add(abs(gnssTurn - abs(it)) - config.turnToleranceDegrees) }
        }.map { it.coerceAtLeast(0.0) }
        previousTrajectoryBearing = currentBearing
        if (mismatches.isEmpty()) return unavailable(current.gnss.timestamp, "independent OBD, PDR, gyro, and route-progress deltas are unavailable")
        val mismatch = mismatches.average()
        return GnssTrustEvidence(GnssTrustEvidenceType.INDEPENDENT_MOTION_CONTINUITY, GnssTrustEvidenceAvailability.AVAILABLE,
            exp(-mismatch / config.scoreDecay), "GNSS geometry mismatch=${format(mismatch)} across ${mismatches.size} independent motion channel(s)", current.gnss.timestamp)
    }
    private fun delta(a: Double?, b: Double?) = if (a != null && b != null && a.isFinite() && b.isFinite()) b - a else null
    private fun unavailable(time: MonotonicTimestamp, detail: String) = GnssTrustEvidence(GnssTrustEvidenceType.INDEPENDENT_MOTION_CONTINUITY, GnssTrustEvidenceAvailability.UNAVAILABLE, null, detail, time)
    private fun distance(a: GnssSample, b: GnssSample): Double { val x = Math.toRadians(b.latitude-a.latitude); val y = Math.toRadians(b.longitude-a.longitude); val q=sin(x/2)*sin(x/2)+cos(Math.toRadians(a.latitude))*cos(Math.toRadians(b.latitude))*sin(y/2)*sin(y/2); return 6_371_000.0*2*atan2(sqrt(q),sqrt(1-q)) }
    private fun bearing(a: GnssSample, b: GnssSample): Double = Math.toDegrees(atan2(
        sin(Math.toRadians(b.longitude - a.longitude)) * cos(Math.toRadians(b.latitude)),
        cos(Math.toRadians(a.latitude)) * sin(Math.toRadians(b.latitude)) - cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * cos(Math.toRadians(b.longitude - a.longitude))
    )).let { (it + 360.0) % 360.0 }
    private fun angularDifference(a: Double, b: Double): Double = abs(((b - a + 540.0) % 360.0) - 180.0)
    private fun format(value: Double) = "%.1f".format(java.util.Locale.ROOT, value)
}
