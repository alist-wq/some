package com.example.navigation.gnss

data class ConservativeGnssRecoveryConfig(val consistentObservationsRequired: Int = 3, val gradualObservations: Int = 4, val minimumEvidenceScore: Double = 0.70) {
    init { require(consistentObservationsRequired > 0 && gradualObservations > 0 && minimumEvidenceScore in 0.0..1.0) }
}
data class ConservativeGnssRecoveryDecision(val allowCorrection: Boolean, val maximumCorrectionWeight: Double, val consecutiveConsistentObservations: Int, val detail: String)

/** Holds GNSS out until anchor, independent-motion, and route evidence are all repeatedly supportive. */
class ConservativeGnssRecoveryGate(private val config: ConservativeGnssRecoveryConfig = ConservativeGnssRecoveryConfig()) {
    private var consistent = 0
    fun observe(anchor: GnssTrustEvidence, independentMotion: GnssTrustEvidence, route: GnssTrustEvidence): ConservativeGnssRecoveryDecision {
        require(anchor.type == GnssTrustEvidenceType.GEOGRAPHIC_CONTINUITY) { "anchor evidence must be geographic continuity" }
        require(independentMotion.type == GnssTrustEvidenceType.INDEPENDENT_MOTION_CONTINUITY) { "independent-motion evidence has the wrong type" }
        require(route.type == GnssTrustEvidenceType.ROUTE_CONSISTENCY) { "route evidence has the wrong type" }
        val inputs = listOf(anchor, independentMotion, route)
        val good = inputs.all { it.availability == GnssTrustEvidenceAvailability.AVAILABLE && it.trustScore!! >= config.minimumEvidenceScore }
        consistent = if (good) consistent + 1 else 0
        if (consistent < config.consistentObservationsRequired) return ConservativeGnssRecoveryDecision(false, 0.0, consistent, "awaiting sustained anchor, independent-motion, and route consistency")
        val stage = consistent - config.consistentObservationsRequired + 1
        val weight = (stage.toDouble() / config.gradualObservations).coerceAtMost(1.0)
        return ConservativeGnssRecoveryDecision(true, weight, consistent, "GNSS re-entry is gradual after sustained independent consistency")
    }
}
