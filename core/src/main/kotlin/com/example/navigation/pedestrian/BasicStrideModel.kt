package com.example.navigation.pedestrian

data class StrideUserProfile(
    val defaultStepLengthMeters: Double = 0.75,
    val defaultUncertaintyMeters: Double = 0.15
) {
    init {
        require(defaultStepLengthMeters.isFinite() && defaultStepLengthMeters > 0.0)
        require(defaultUncertaintyMeters.isFinite() && defaultUncertaintyMeters >= 0.0)
    }
}

data class StrideEstimate(
    val estimatedLengthMeters: Double,
    val uncertaintyMeters: Double
)

data class BasicStrideModelConfig(
    val referenceCadenceStepsPerMinute: Double = 100.0,
    val cadenceGainMetersPerStepPerMinute: Double = 0.002,
    val referenceAccelerationDeviationMetersPerSecondSquared: Double = 2.0,
    val accelerationGainMetersPerAccelerationUnit: Double = 0.03,
    val minimumStepLengthMeters: Double = 0.30,
    val maximumStepLengthMeters: Double = 1.50
) {
    init {
        require(referenceCadenceStepsPerMinute.isFinite() && referenceCadenceStepsPerMinute > 0.0)
        require(cadenceGainMetersPerStepPerMinute.isFinite())
        require(referenceAccelerationDeviationMetersPerSecondSquared.isFinite() && referenceAccelerationDeviationMetersPerSecondSquared >= 0.0)
        require(accelerationGainMetersPerAccelerationUnit.isFinite())
        require(minimumStepLengthMeters.isFinite() && maximumStepLengthMeters.isFinite() && minimumStepLengthMeters > 0.0 && maximumStepLengthMeters > minimumStepLengthMeters)
    }
}

/** Initial profile-based stride estimate; calibration against trusted GNSS is intentionally separate. */
class BasicStrideModel(private val config: BasicStrideModelConfig = BasicStrideModelConfig()) {
    fun estimate(profile: StrideUserProfile, cadenceStepsPerMinute: Double?, accelerationDeviationMetersPerSecondSquared: Double): StrideEstimate {
        require(cadenceStepsPerMinute == null || cadenceStepsPerMinute.isFinite() && cadenceStepsPerMinute > 0.0)
        require(accelerationDeviationMetersPerSecondSquared.isFinite() && accelerationDeviationMetersPerSecondSquared >= 0.0)
        val cadenceAdjustment = cadenceStepsPerMinute?.let { (it - config.referenceCadenceStepsPerMinute) * config.cadenceGainMetersPerStepPerMinute } ?: 0.0
        val accelerationAdjustment = (accelerationDeviationMetersPerSecondSquared - config.referenceAccelerationDeviationMetersPerSecondSquared) * config.accelerationGainMetersPerAccelerationUnit
        val estimated = (profile.defaultStepLengthMeters + cadenceAdjustment + accelerationAdjustment)
            .coerceIn(config.minimumStepLengthMeters, config.maximumStepLengthMeters)
        val uncertainty = profile.defaultUncertaintyMeters +
            (if (cadenceStepsPerMinute == null) 0.05 else 0.0) +
            kotlin.math.abs(cadenceAdjustment) * 0.25 + kotlin.math.abs(accelerationAdjustment) * 0.25
        return StrideEstimate(estimated, uncertainty)
    }
}
