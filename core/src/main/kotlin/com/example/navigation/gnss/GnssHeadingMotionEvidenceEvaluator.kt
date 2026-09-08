package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import kotlin.math.abs
import kotlin.math.exp

data class GnssHeadingMotionEvidenceConfig(
    val minimumSpeedMetersPerSecond: Double = 2.0,
    val fullAgreementDegrees: Double = 10.0,
    val fullDisagreementDegrees: Double = 90.0
) {
    init {
        require(minimumSpeedMetersPerSecond.isFinite() && minimumSpeedMetersPerSecond >= 0.0)
        require(fullAgreementDegrees in 0.0..<fullDisagreementDegrees)
        require(fullDisagreementDegrees <= 180.0)
    }
}

data class HeadingMotionEstimate(
    val gyroHeadingChangeDegrees: Double?,
    val roadHeadingDegrees: Double?
) {
    init {
        require(gyroHeadingChangeDegrees == null || gyroHeadingChangeDegrees.isFinite())
        require(roadHeadingDegrees == null || roadHeadingDegrees.isFinite())
    }
}

/** Compares a meaningful GNSS bearing with relative gyro turn and road heading evidence. */
class GnssHeadingMotionEvidenceEvaluator(
    private val config: GnssHeadingMotionEvidenceConfig = GnssHeadingMotionEvidenceConfig()
) {
    private var previousGnss: GnssSample? = null

    fun evaluate(current: GnssSample, motion: HeadingMotionEstimate): GnssTrustEvidence {
        val bearing = current.bearingDegrees?.toDouble()
            ?: return unavailable(current.timestamp, "GNSS bearing is unavailable")
        val speed = current.speedMetersPerSecond?.toDouble()
            ?: return unavailable(current.timestamp, "GNSS speed is unavailable; bearing is not meaningful")
        if (speed < config.minimumSpeedMetersPerSecond) {
            previousGnss = current
            return unavailable(current.timestamp, "GNSS speed is below the heading evaluation threshold")
        }
        val comparisons = mutableListOf<Double>()
        val previousBearing = previousGnss?.bearingDegrees?.toDouble()
        if (previousBearing != null && motion.gyroHeadingChangeDegrees != null) {
            comparisons += angularDifference(signedAngle(bearing - previousBearing), motion.gyroHeadingChangeDegrees)
        }
        if (motion.roadHeadingDegrees != null) comparisons += angularDifference(bearing, motion.roadHeadingDegrees)
        previousGnss = current
        if (comparisons.isEmpty()) return unavailable(current.timestamp, "No gyro heading-change or road-heading comparison is available")

        val error = comparisons.average()
        return GnssTrustEvidence(
            GnssTrustEvidenceType.HEADING_CONSISTENCY,
            GnssTrustEvidenceAvailability.AVAILABLE,
            score(error),
            "GNSS heading disagreement=${format(error)}deg across ${comparisons.size} independent motion comparison(s)",
            current.timestamp
        )
    }

    private fun score(error: Double) = exp(-((error - config.fullAgreementDegrees).coerceAtLeast(0.0)) /
        (config.fullDisagreementDegrees - config.fullAgreementDegrees))
    private fun angularDifference(a: Double, b: Double) = abs(signedAngle(a - b))
    private fun signedAngle(value: Double) = ((value + 540.0) % 360.0) - 180.0
    private fun unavailable(timestamp: MonotonicTimestamp, detail: String) = GnssTrustEvidence(
        GnssTrustEvidenceType.HEADING_CONSISTENCY, GnssTrustEvidenceAvailability.UNAVAILABLE, null, detail, timestamp
    )
    private fun format(value: Double) = "%.2f".format(java.util.Locale.ROOT, value)
}
