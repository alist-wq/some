package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

enum class GnssTrustEvidenceType {
    POSITION_JUMP,
    SPEED_CONSISTENCY,
    DISTANCE_CONSISTENCY,
    GEOGRAPHIC_CONTINUITY,
    HARD_INNOVATION,
    INDEPENDENT_MOTION_CONTINUITY,
    HEADING_CONSISTENCY,
    ROUTE_CONSISTENCY,
    CN0,
    AGC,
    TIME_CONSISTENCY,
    OTHER
}

enum class GnssTrustEvidenceAvailability { AVAILABLE, UNAVAILABLE }

/** One inspectable trust observation. A score of 0 is adverse and 1 is supportive. */
data class GnssTrustEvidence(
    val type: GnssTrustEvidenceType,
    val availability: GnssTrustEvidenceAvailability,
    val trustScore: Double?,
    val detail: String,
    val timestamp: MonotonicTimestamp
) : Serializable {
    init {
        require(detail.isNotBlank()) { "GNSS trust evidence detail must not be blank" }
        when (availability) {
            GnssTrustEvidenceAvailability.AVAILABLE ->
                require(trustScore != null && trustScore.isFinite() && trustScore in 0.0..1.0) {
                    "Available GNSS trust evidence requires a score in [0, 1]"
                }
            GnssTrustEvidenceAvailability.UNAVAILABLE ->
                require(trustScore == null) { "Unavailable GNSS trust evidence must not contain a score" }
        }
    }

    private companion object { private const val serialVersionUID = 1L }
}

data class GnssTrustContribution(
    val evidence: GnssTrustEvidence,
    val configuredWeight: Double,
    val weightedScore: Double?,
    /** Fraction of the available configured weight used by this signal. */
    val normalizedWeight: Double?,
    /** Exact additive contribution from this signal to [GnssTrustScore.value]. */
    val scoreContribution: Double?
) : Serializable

/** Continuous trust result retaining every input and its exact aggregation contribution. */
data class GnssTrustScore(
    val value: Double,
    val contributions: List<GnssTrustContribution>
) : Serializable {
    init { require(value.isFinite() && value in 0.0..1.0) { "GNSS trust score must be in [0, 1]" } }
    val evidence: List<GnssTrustEvidence> get() = contributions.map { it.evidence }
    private companion object { private const val serialVersionUID = 1L }
}

data class GnssTrustEngineConfig(
    val weights: Map<GnssTrustEvidenceType, Double> = emptyMap(),
    val defaultWeight: Double = 1.0,
    val noAvailableEvidenceScore: Double = 0.5
) {
    init {
        require(defaultWeight.isFinite() && defaultWeight >= 0.0) { "Default evidence weight must be non-negative" }
        require(weights.values.all { it.isFinite() && it >= 0.0 }) { "Evidence weights must be finite and non-negative" }
        require(noAvailableEvidenceScore.isFinite() && noAvailableEvidenceScore in 0.0..1.0) {
            "No-evidence score must be in [0, 1]"
        }
    }
}

/** Aggregates typed evidence without performing any individual anomaly detection. */
class GnssTrustEngine(private val config: GnssTrustEngineConfig = GnssTrustEngineConfig()) {
    fun evaluate(evidence: List<GnssTrustEvidence>): GnssTrustScore {
        val initialContributions = evidence.map { item ->
            val weight = config.weights[item.type] ?: config.defaultWeight
            GnssTrustContribution(
                evidence = item,
                configuredWeight = weight,
                weightedScore = item.trustScore?.times(weight),
                normalizedWeight = null,
                scoreContribution = null
            )
        }
        val available = initialContributions.filter {
            it.evidence.availability == GnssTrustEvidenceAvailability.AVAILABLE && it.configuredWeight > 0.0
        }
        val totalWeight = available.sumOf { it.configuredWeight }
        val contributions = initialContributions.map { contribution ->
            if (contribution in available) {
                val normalizedWeight = contribution.configuredWeight / totalWeight
                contribution.copy(
                    normalizedWeight = normalizedWeight,
                    scoreContribution = contribution.evidence.trustScore!! * normalizedWeight
                )
            } else contribution
        }
        val value = if (totalWeight == 0.0) config.noAvailableEvidenceScore
        else contributions.sumOf { it.scoreContribution ?: 0.0 }
        return GnssTrustScore(value.coerceIn(0.0, 1.0), contributions)
    }
}
