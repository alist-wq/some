package com.example.navigation.gnss

import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.NavigationPosition
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeographicContinuityEnvelope(
    val maximumSpeedMetersPerSecond: Double,
    val fixedAllowanceMeters: Double,
    val scoreDecayMeters: Double
) {
    init {
        require(maximumSpeedMetersPerSecond.isFinite() && maximumSpeedMetersPerSecond > 0.0)
        require(fixedAllowanceMeters.isFinite() && fixedAllowanceMeters >= 0.0)
        require(scoreDecayMeters.isFinite() && scoreDecayMeters > 0.0)
    }
}

data class GeographicContinuityConfig(
    val vehicle: GeographicContinuityEnvelope = GeographicContinuityEnvelope(70.0, 15.0, 100.0),
    val pedestrian: GeographicContinuityEnvelope = GeographicContinuityEnvelope(3.5, 10.0, 25.0),
    val unknown: GeographicContinuityEnvelope = GeographicContinuityEnvelope(20.0, 25.0, 75.0)
)

data class GeographicContinuityInput(
    val anchor: NavigationPosition,
    val candidate: GnssSample,
    val mode: NavigationMode,
    val independentMotion: IndependentMotionEstimate?
)

/** Scores physical reachability from the trusted anchor without applying any navigation correction. */
class GeographicContinuityEvidenceEvaluator(private val config: GeographicContinuityConfig = GeographicContinuityConfig()) {
    fun evaluate(input: GeographicContinuityInput): GnssTrustEvidence {
        val elapsedNanos = input.candidate.timestamp.differenceNanos(input.anchor.timestamp)
        if (elapsedNanos <= 0L) return unavailable(input, "candidate timestamp is not after trusted anchor")
        val motion = input.independentMotion ?: return unavailable(input, "independent motion speed is unavailable")
        val envelope = when (input.mode) {
            NavigationMode.VEHICLE -> config.vehicle
            NavigationMode.PEDESTRIAN -> config.pedestrian
            NavigationMode.UNKNOWN -> config.unknown
        }
        val elapsedSeconds = elapsedNanos / NANOS_PER_SECOND
        val independentlyPermittedSpeed = motion.speedMetersPerSecond + motion.speedUncertaintyMetersPerSecond
        val permitted = minOf(independentlyPermittedSpeed, envelope.maximumSpeedMetersPerSecond) * elapsedSeconds +
            input.anchor.estimatedAccuracyMeters + (input.candidate.horizontalAccuracyMeters ?: 0f) + envelope.fixedAllowanceMeters
        val displacement = distanceMeters(input.anchor.latitude, input.anchor.longitude, input.candidate.latitude, input.candidate.longitude)
        val excess = (displacement - permitted).coerceAtLeast(0.0)
        return GnssTrustEvidence(
            GnssTrustEvidenceType.GEOGRAPHIC_CONTINUITY,
            GnssTrustEvidenceAvailability.AVAILABLE,
            exp(-excess / envelope.scoreDecayMeters),
            "anchor displacement=${format(displacement)}m; physical allowance=${format(permitted)}m over ${format(elapsedSeconds)}s",
            input.candidate.timestamp
        )
    }

    private fun unavailable(input: GeographicContinuityInput, detail: String) = GnssTrustEvidence(
        GnssTrustEvidenceType.GEOGRAPHIC_CONTINUITY, GnssTrustEvidenceAvailability.UNAVAILABLE, null, detail, input.candidate.timestamp
    )

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun format(value: Double) = "%.2f".format(java.util.Locale.ROOT, value)
    private companion object { const val EARTH_RADIUS_METERS = 6_371_000.0; const val NANOS_PER_SECOND = 1_000_000_000.0 }
}
