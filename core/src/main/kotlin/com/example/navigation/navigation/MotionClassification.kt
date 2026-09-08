package com.example.navigation.navigation

import com.example.navigation.core.MonotonicTimestamp

/** The mutually exclusive movement hypotheses used by navigation-mode transitions. */
enum class MotionClassificationState {
    VEHICLE,
    PEDESTRIAN,
    STATIONARY,
    UNKNOWN
}

/**
 * A normalized, timestamped distribution over movement hypotheses.
 * This is a representation only; sensor inference and transition decisions are separate tasks.
 */
data class MotionClassification(
    val vehicleProbability: Double,
    val pedestrianProbability: Double,
    val stationaryProbability: Double,
    val unknownProbability: Double,
    val timestamp: MonotonicTimestamp
) {
    init {
        probabilities().forEach { probability ->
            require(probability.isFinite() && probability in 0.0..1.0) {
                "Motion-classification probabilities must be finite and in [0, 1]"
            }
        }
        require(kotlin.math.abs(probabilities().sum() - 1.0) <= NORMALIZATION_TOLERANCE) {
            "Motion-classification probabilities must sum to 1"
        }
    }

    fun probabilityOf(state: MotionClassificationState): Double = when (state) {
        MotionClassificationState.VEHICLE -> vehicleProbability
        MotionClassificationState.PEDESTRIAN -> pedestrianProbability
        MotionClassificationState.STATIONARY -> stationaryProbability
        MotionClassificationState.UNKNOWN -> unknownProbability
    }

    private fun probabilities(): List<Double> = listOf(
        vehicleProbability,
        pedestrianProbability,
        stationaryProbability,
        unknownProbability
    )

    companion object {
        private const val NORMALIZATION_TOLERANCE = 1e-9

        fun unknown(timestamp: MonotonicTimestamp) = MotionClassification(
            vehicleProbability = 0.0,
            pedestrianProbability = 0.0,
            stationaryProbability = 0.0,
            unknownProbability = 1.0,
            timestamp = timestamp
        )
    }
}
