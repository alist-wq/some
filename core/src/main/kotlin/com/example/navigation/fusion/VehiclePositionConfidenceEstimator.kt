package com.example.navigation.fusion

import com.example.navigation.navigation.PositionConfidence

data class VehiclePositionConfidenceInput(
    val timeSinceTrustedGnssNanos: Long,
    val distanceSinceTrustedGnssMeters: Double,
    val gyroUncertaintyDegrees: Double,
    val obdAvailable: Boolean,
    val routeAmbiguity: Double,
    val candidateProbabilitySeparation: Double
) {
    init {
        require(timeSinceTrustedGnssNanos >= 0L)
        require(distanceSinceTrustedGnssMeters.isFinite() && distanceSinceTrustedGnssMeters >= 0.0)
        require(gyroUncertaintyDegrees.isFinite() && gyroUncertaintyDegrees >= 0.0)
        require(routeAmbiguity.isFinite() && routeAmbiguity in 0.0..1.0)
        require(candidateProbabilitySeparation.isFinite() && candidateProbabilitySeparation in 0.0..1.0)
    }
}

data class VehiclePositionConfidenceConfig(
    val baseUncertaintyMeters: Double = 5.0,
    val timeGrowthMetersPerSecond: Double = 0.1,
    val distanceGrowthMetersPerMeter: Double = 0.02,
    val gyroGrowthMetersPerDegree: Double = 0.5,
    val obdUnavailablePenaltyMeters: Double = 20.0,
    val routeAmbiguityPenaltyMeters: Double = 30.0,
    val candidateSeparationPenaltyMeters: Double = 25.0,
    val highThresholdMeters: Double = 15.0,
    val mediumThresholdMeters: Double = 40.0,
    val lowThresholdMeters: Double = 100.0
) {
    init {
        val values = listOf(baseUncertaintyMeters, timeGrowthMetersPerSecond, distanceGrowthMetersPerMeter,
            gyroGrowthMetersPerDegree, obdUnavailablePenaltyMeters, routeAmbiguityPenaltyMeters,
            candidateSeparationPenaltyMeters, highThresholdMeters, mediumThresholdMeters, lowThresholdMeters)
        require(values.all { it.isFinite() && it >= 0.0 })
        require(highThresholdMeters < mediumThresholdMeters && mediumThresholdMeters < lowThresholdMeters)
    }
}

data class VehiclePositionConfidenceEstimate(
    val uncertaintyMeters: Double,
    val confidence: PositionConfidence
)

class VehiclePositionConfidenceEstimator(
    private val config: VehiclePositionConfidenceConfig = VehiclePositionConfidenceConfig()
) {
    fun estimate(input: VehiclePositionConfidenceInput): VehiclePositionConfidenceEstimate {
        val seconds = input.timeSinceTrustedGnssNanos / NANOS_PER_SECOND
        val uncertainty = config.baseUncertaintyMeters +
            seconds * config.timeGrowthMetersPerSecond +
            input.distanceSinceTrustedGnssMeters * config.distanceGrowthMetersPerMeter +
            input.gyroUncertaintyDegrees * config.gyroGrowthMetersPerDegree +
            (if (input.obdAvailable) 0.0 else config.obdUnavailablePenaltyMeters) +
            input.routeAmbiguity * config.routeAmbiguityPenaltyMeters +
            (1.0 - input.candidateProbabilitySeparation) * config.candidateSeparationPenaltyMeters
        val confidence = when {
            uncertainty <= config.highThresholdMeters -> PositionConfidence.HIGH
            uncertainty <= config.mediumThresholdMeters -> PositionConfidence.MEDIUM
            uncertainty <= config.lowThresholdMeters -> PositionConfidence.LOW
            else -> PositionConfidence.LOST
        }
        return VehiclePositionConfidenceEstimate(uncertainty, confidence)
    }

    private companion object { const val NANOS_PER_SECOND = 1_000_000_000.0 }
}
