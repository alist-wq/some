package com.example.navigation.fusion

import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.GnssTrustScore
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionSource

data class GnssCorrectionConfig(
    val referenceGnssAccuracyMeters: Double = 10.0,
    val referenceDrUncertaintyMeters: Double = 20.0
) {
    init {
        require(referenceGnssAccuracyMeters.isFinite() && referenceGnssAccuracyMeters > 0.0)
        require(referenceDrUncertaintyMeters.isFinite() && referenceDrUncertaintyMeters > 0.0)
    }
}

data class GnssCorrectionResult(
    val state: VehicleMotionState,
    val applied: Boolean,
    val correctionWeight: Double,
    val detail: String
)

/**
 * Applies a bounded GNSS correction to a vehicle dead-reckoning state.
 *
 * It is deliberately independent of the trust state machine: callers supply
 * the continuous trust value, while rejection policy is integrated separately.
 */
class GnssCorrectionApplier(private val config: GnssCorrectionConfig = GnssCorrectionConfig()) {
    fun apply(state: VehicleMotionState, gnss: GnssSample, trust: GnssTrustScore): GnssCorrectionResult =
        apply(state, gnss, trust.value)

    fun apply(state: VehicleMotionState, gnss: GnssSample, trustScore: Double): GnssCorrectionResult {
        require(trustScore.isFinite() && trustScore in 0.0..1.0) { "GNSS trust must be in [0, 1]" }
        require(gnss.timestamp >= state.timestamp) { "GNSS correction cannot move vehicle state backwards in monotonic time" }
        val accuracy = gnss.horizontalAccuracyMeters?.toDouble()
        if (accuracy == null || !accuracy.isFinite() || accuracy <= 0.0) {
            return GnssCorrectionResult(state, false, 0.0, "GNSS horizontal accuracy is unavailable")
        }
        val weight = correctionWeight(trustScore, accuracy, state.uncertaintyMeters)
        if (weight == 0.0) return GnssCorrectionResult(state, false, 0.0, "GNSS trust or DR uncertainty yields zero correction weight")

        val correctedLatitude = interpolate(state.position.latitude, gnss.latitude, weight)
        val correctedLongitude = interpolate(state.position.longitude, gnss.longitude, weight)
        val correctedAccuracy = interpolate(state.uncertaintyMeters, accuracy, weight)
        val position = NavigationPosition(
            latitude = correctedLatitude,
            longitude = correctedLongitude,
            confidence = state.position.confidence,
            estimatedAccuracyMeters = correctedAccuracy,
            source = PositionSource.HYBRID,
            timestamp = gnss.timestamp
        )
        return GnssCorrectionResult(
            state = state.copy(position = position, uncertaintyMeters = correctedAccuracy, timestamp = gnss.timestamp),
            applied = true,
            correctionWeight = weight,
            detail = "GNSS correction weight=${format(weight)}; trust=${format(trustScore)}; GNSS accuracy=${format(accuracy)}m; DR uncertainty=${format(state.uncertaintyMeters)}m"
        )
    }

    private fun correctionWeight(trust: Double, gnssAccuracy: Double, drUncertainty: Double): Double {
        val accuracyReliability = (config.referenceGnssAccuracyMeters / gnssAccuracy).coerceAtMost(1.0)
        val drNeed = drUncertainty / (drUncertainty + config.referenceDrUncertaintyMeters)
        return (trust * accuracyReliability * drNeed).coerceIn(0.0, 1.0)
    }

    private fun interpolate(from: Double, to: Double, weight: Double) = from + (to - from) * weight
    private fun format(value: Double) = "%.3f".format(java.util.Locale.ROOT, value)
}
