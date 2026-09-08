package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.PositionConfidence
import kotlin.math.abs
import kotlin.math.cos

data class SatelliteTrustConfig(
    val trustedThreshold: Double = 0.75,
    val uncertainThreshold: Double = 0.45,
    val rejectedThreshold: Double = 0.15,
    val maximumPositionInnovationMeters: Double = 250.0,
    val maximumGeometryResidualDegrees: Double = 35.0,
    val staleReferenceAgeSeconds: Double = 14_400.0,
    val recoveryEpochs: Int = 3
) {
    init {
        require(rejectedThreshold < uncertainThreshold && uncertainThreshold < trustedThreshold)
        require(maximumPositionInnovationMeters > 0.0 && maximumGeometryResidualDegrees > 0.0)
        require(staleReferenceAgeSeconds > 0.0 && recoveryEpochs > 0)
    }
}

data class SatelliteTrustInput(
    val status: GnssSatelliteStatus,
    val previousEpochContainedSatellite: Boolean? = null,
    val reference: SpacecraftReferenceEvidence? = null
)

data class IndependentGnssContext(
    val navigationMode: NavigationMode,
    val positionInnovationMeters: Double? = null,
    val pdrConsistent: Boolean? = null,
    val gyroConsistent: Boolean? = null,
    val obdSpeedConsistent: Boolean? = null,
    val routeConsistent: Boolean? = null,
    val clockConsistent: Boolean? = null,
    val mockLocation: Boolean? = null,
    val airAlertActive: Boolean = false
)

/** Deterministic evidence scoring. Air-alert context is deliberately not an input to trust. */
class SatelliteTrustScorer(private val config: SatelliteTrustConfig = SatelliteTrustConfig()) {
    fun score(
        timestamp: MonotonicTimestamp,
        satellites: List<SatelliteTrustInput>,
        context: IndependentGnssContext
    ): GnssEpochTrustDecision {
        if (satellites.isEmpty()) return GnssEpochTrustDecision(
            timestamp, null, null, listOf(GnssTrustReasonCode.EVIDENCE_UNAVAILABLE), emptyList(), VERSION,
            EvidenceCapability.UNAVAILABLE
        )
        val decisions = satellites.map { scoreSatellite(it, context) }
        val totalWeight = decisions.sumOf { it.estimatorWeight }
        val epochScore = if (totalWeight > 0.0) {
            decisions.sumOf { it.score * it.estimatorWeight } / totalWeight
        } else decisions.map { it.score }.average()
        val reasons = (decisions.flatMap { it.reasons } + contextReasons(context)).distinct()
        val adjusted = applyContext(epochScore, context)
        return GnssEpochTrustDecision(
            timestamp, adjusted, classify(adjusted), reasons, decisions, VERSION, EvidenceCapability.AVAILABLE
        )
    }

    private fun scoreSatellite(input: SatelliteTrustInput, context: IndependentGnssContext): SatelliteTrustDecision {
        var score = 1.0
        val reasons = mutableListOf<GnssTrustReasonCode>()
        if (input.status.cn0DbHz < 18f) { score -= 0.20; reasons += GnssTrustReasonCode.SIGNAL_ANOMALY }
        if (input.previousEpochContainedSatellite == false) { score -= 0.10; reasons += GnssTrustReasonCode.SATELLITE_CONTINUITY }
        input.reference?.let { reference ->
            val stale = (reference.ageSeconds ?: 0.0) > config.staleReferenceAgeSeconds
            if (!stale) {
                val elevationResidual = reference.expectedElevationDegrees?.let { abs(it - input.status.elevationDegrees) }
                val azimuthResidual = reference.expectedAzimuthDegrees?.let { angularDifference(it, input.status.azimuthDegrees.toDouble()) }
                if (listOfNotNull(elevationResidual, azimuthResidual).any { it > config.maximumGeometryResidualDegrees + (reference.uncertaintyDegrees ?: 0.0) }) {
                    score -= 0.55
                    reasons += GnssTrustReasonCode.EXPECTED_GEOMETRY_DISAGREEMENT
                }
            }
        }
        score = applyContext(score, context)
        val classification = classify(score)
        val weight = when (classification) {
            GnssMeasurementClassification.TRUSTED -> score
            GnssMeasurementClassification.UNCERTAIN -> score * 0.5
            GnssMeasurementClassification.SUSPICIOUS -> score * 0.1
            GnssMeasurementClassification.REJECTED -> 0.0
        }
        return SatelliteTrustDecision(
            input.status.constellation, input.status.svid ?: 0, score, classification,
            (reasons + contextReasons(context)).distinct(), weight > 0.0, weight, input.reference
        )
    }

    private fun applyContext(initial: Double, context: IndependentGnssContext): Double {
        var score = initial
        if (context.mockLocation == true) score = 0.0
        if (context.positionInnovationMeters != null && context.positionInnovationMeters > config.maximumPositionInnovationMeters) score -= 0.65
        if (context.pdrConsistent == false || context.gyroConsistent == false) score -= 0.20
        if (context.navigationMode == NavigationMode.VEHICLE && context.obdSpeedConsistent == false) score -= 0.20
        if (context.routeConsistent == false) score -= 0.15
        if (context.clockConsistent == false) score -= 0.20
        return score.coerceIn(0.0, 1.0)
    }

    private fun contextReasons(context: IndependentGnssContext) = buildList {
        if (context.mockLocation == true) add(GnssTrustReasonCode.MOCK_LOCATION_REPORTED)
        if (context.positionInnovationMeters != null && context.positionInnovationMeters > config.maximumPositionInnovationMeters) add(GnssTrustReasonCode.PHYSICAL_CONTINUITY_VIOLATION)
        if (context.pdrConsistent == false || context.gyroConsistent == false ||
            (context.navigationMode == NavigationMode.VEHICLE && context.obdSpeedConsistent == false)) add(GnssTrustReasonCode.INDEPENDENT_MOTION_DISAGREEMENT)
        if (context.routeConsistent == false) add(GnssTrustReasonCode.ROUTE_DISAGREEMENT)
        if (context.clockConsistent == false) add(GnssTrustReasonCode.CLOCK_INCONSISTENCY)
    }

    private fun classify(score: Double) = when {
        score >= config.trustedThreshold -> GnssMeasurementClassification.TRUSTED
        score >= config.uncertainThreshold -> GnssMeasurementClassification.UNCERTAIN
        score > config.rejectedThreshold -> GnssMeasurementClassification.SUSPICIOUS
        else -> GnssMeasurementClassification.REJECTED
    }

    private fun angularDifference(a: Double, b: Double): Double = abs((a - b + 540.0) % 360.0 - 180.0)

    private companion object { const val VERSION = "satellite-trust-1" }
}

data class WeightedPositionCandidate(val latitude: Double, val longitude: Double, val weight: Double)

/** Robust weighted candidate fusion with a deterministic innovation gate. */
class WeightedGnssPositionEstimator(private val maximumResidualMeters: Double = 150.0) {
    fun estimate(
        timestamp: MonotonicTimestamp,
        candidates: List<WeightedPositionCandidate>,
        decision: GnssEpochTrustDecision
    ): WeightedGnssPositionSolution? {
        val usable = candidates.filter { it.weight > 0.0 && it.weight.isFinite() }
        if (usable.isEmpty() || decision.score == null) return null
        val seedLat = weightedAverage(usable) { it.latitude }
        val seedLon = weightedAverage(usable) { it.longitude }
        val robust = usable.map { candidate ->
            val residual = distanceMeters(seedLat, seedLon, candidate.latitude, candidate.longitude)
            candidate.copy(weight = candidate.weight * (maximumResidualMeters / residual.coerceAtLeast(maximumResidualMeters)))
        }
        val lat = weightedAverage(robust) { it.latitude }
        val lon = weightedAverage(robust) { it.longitude }
        val accuracy = robust.maxOfOrNull { distanceMeters(lat, lon, it.latitude, it.longitude) } ?: 0.0
        val confidence = when {
            decision.score >= 0.75 -> PositionConfidence.HIGH
            decision.score >= 0.45 -> PositionConfidence.MEDIUM
            decision.score > 0.15 -> PositionConfidence.LOW
            else -> PositionConfidence.LOST
        }
        return WeightedGnssPositionSolution(timestamp, lat, lon, accuracy, confidence,
            WeightedPositionSource.WEIGHTED_GNSS, robust.sumOf { it.weight }, decision.reasons)
    }

    private fun weightedAverage(values: List<WeightedPositionCandidate>, selector: (WeightedPositionCandidate) -> Double): Double =
        values.sumOf { selector(it) * it.weight } / values.sumOf { it.weight }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val north = (lat2 - lat1) * 111_320.0
        val east = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2.0))
        return kotlin.math.hypot(north, east)
    }
}
