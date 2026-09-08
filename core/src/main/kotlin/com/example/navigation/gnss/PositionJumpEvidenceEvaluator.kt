package com.example.navigation.gnss

import kotlin.math.*

data class IndependentMotionEstimate(
    val speedMetersPerSecond: Double,
    val speedUncertaintyMetersPerSecond: Double = 0.0
) {
    init {
        require(speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0)
        require(speedUncertaintyMetersPerSecond.isFinite() && speedUncertaintyMetersPerSecond >= 0.0)
    }
}

data class PositionJumpEvidenceConfig(
    val minimumToleranceMeters: Double = 10.0,
    val scoreDecayMeters: Double = 25.0
) {
    init {
        require(minimumToleranceMeters >= 0.0 && minimumToleranceMeters.isFinite())
        require(scoreDecayMeters > 0.0 && scoreDecayMeters.isFinite())
    }
}

/** Produces inspectable evidence for GNSS displacement exceeding independent motion capability. */
class PositionJumpEvidenceEvaluator(private val config: PositionJumpEvidenceConfig = PositionJumpEvidenceConfig()) {
    fun evaluate(
        previous: GnssSample,
        current: GnssSample,
        independentMotion: IndependentMotionEstimate
    ): GnssTrustEvidence {
        val elapsedNanos = current.timestamp.differenceNanos(previous.timestamp)
        if (elapsedNanos <= 0L) return unavailable(current, "GNSS timestamps are not strictly increasing")
        val elapsedSeconds = elapsedNanos / NANOS_PER_SECOND
        val displacement = distanceMeters(previous.latitude, previous.longitude, current.latitude, current.longitude)
        val accuracyAllowance = (previous.horizontalAccuracyMeters ?: 0f) + (current.horizontalAccuracyMeters ?: 0f)
        val motionAllowance = (independentMotion.speedMetersPerSecond + independentMotion.speedUncertaintyMetersPerSecond) * elapsedSeconds
        val permitted = motionAllowance + accuracyAllowance + config.minimumToleranceMeters
        val excess = (displacement - permitted).coerceAtLeast(0.0)
        val score = exp(-excess / config.scoreDecayMeters)
        return GnssTrustEvidence(
            GnssTrustEvidenceType.POSITION_JUMP,
            GnssTrustEvidenceAvailability.AVAILABLE,
            score,
            "GNSS displacement=${format(displacement)}m; independent allowance=${format(permitted)}m over ${format(elapsedSeconds)}s",
            current.timestamp
        )
    }

    private fun unavailable(sample: GnssSample, detail: String) = GnssTrustEvidence(
        GnssTrustEvidenceType.POSITION_JUMP, GnssTrustEvidenceAvailability.UNAVAILABLE, null, detail, sample.timestamp
    )

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun format(value: Double) = "%.2f".format(java.util.Locale.ROOT, value)
    private companion object { const val EARTH_RADIUS_METERS = 6_371_000.0; const val NANOS_PER_SECOND = 1_000_000_000.0 }
}
