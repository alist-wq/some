package com.example.navigation.fusion

import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.GnssTrustScore
import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.gnss.GnssTrustEvidence
import com.example.navigation.gnss.ConservativeGnssRecoveryDecision
import com.example.navigation.navigation.PositionSource

fun interface GnssSampleRecorder {
    fun record(sample: GnssSample)
}

data class VehicleGnssIntegrationConfig(
    val questionableMaximumTrust: Double = 0.25,
    val recoveryObservationsForFullWeight: Int = 5
) {
    init {
        require(questionableMaximumTrust.isFinite() && questionableMaximumTrust in 0.0..1.0)
        require(recoveryObservationsForFullWeight > 1)
    }
}

data class VehicleGnssIntegrationResult(
    val state: VehicleMotionState,
    val correctionApplied: Boolean,
    val correctionWeight: Double,
    val gnssRecorded: Boolean,
    val continueDeadReckoning: Boolean = true,
    val continueRouteMatching: Boolean = true,
    val detail: String,
    val diagnosticEvidence: List<GnssTrustEvidence> = emptyList()
)

/** Integrates trust policy without suppressing recording, DR propagation, or route matching. */
class VehicleGnssIntegration(
    private val recorder: GnssSampleRecorder,
    private val correctionApplier: GnssCorrectionApplier = GnssCorrectionApplier(),
    private val config: VehicleGnssIntegrationConfig = VehicleGnssIntegrationConfig()
) {
    private var recoveringFromRejection = false
    private var recoveryObservations = 0

    fun process(
        state: VehicleMotionState,
        gnss: GnssSample,
        trustScore: GnssTrustScore,
        trustState: GnssTrustState,
        confidence: VehiclePositionConfidenceEstimate
    ): VehicleGnssIntegrationResult {
        return processInternal(state, gnss, trustScore, trustState, confidence, null, null)
    }

    /** Same recording-first correction path with a supplied catastrophic-innovation decision. */
    fun process(
        state: VehicleMotionState,
        gnss: GnssSample,
        trustScore: GnssTrustScore,
        trustState: GnssTrustState,
        confidence: VehiclePositionConfidenceEstimate,
        innovationGate: HardInnovationGate
    ): VehicleGnssIntegrationResult = processInternal(
        state, gnss, trustScore, trustState, confidence, innovationGate.evaluate(state, gnss), null
    )

    /** Enforces both catastrophic innovation rejection and conservative post-rejection re-entry. */
    fun process(
        state: VehicleMotionState,
        gnss: GnssSample,
        trustScore: GnssTrustScore,
        trustState: GnssTrustState,
        confidence: VehiclePositionConfidenceEstimate,
        innovationGate: HardInnovationGate,
        recoveryDecision: ConservativeGnssRecoveryDecision
    ): VehicleGnssIntegrationResult = processInternal(
        state, gnss, trustScore, trustState, confidence, innovationGate.evaluate(state, gnss), recoveryDecision
    )

    private fun processInternal(
        state: VehicleMotionState,
        gnss: GnssSample,
        trustScore: GnssTrustScore,
        trustState: GnssTrustState,
        confidence: VehiclePositionConfidenceEstimate,
        innovation: HardInnovationGateDecision?,
        recovery: ConservativeGnssRecoveryDecision?
    ): VehicleGnssIntegrationResult {
        recorder.record(gnss)
        val confidenceState = applyConfidence(state, confidence)
        if (innovation?.blockCorrection == true) {
            recoveringFromRejection = true
            recoveryObservations = 0
            return result(confidenceState, false, 0.0, "GNSS hard innovation blocked correction; ${innovation.evidence.detail}", innovation)
        }
        if (trustState == GnssTrustState.REJECTED) {
            recoveringFromRejection = true
            recoveryObservations = 0
            return result(confidenceState, false, 0.0, "GNSS correction rejected; DR and route matching remain active")
        }
        if (recovery != null && !recovery.allowCorrection) {
            recoveringFromRejection = true
            return result(confidenceState, false, 0.0, recovery.detail, innovation)
        }

        val effectiveTrust = when {
            trustState == GnssTrustState.QUESTIONABLE -> trustScore.value.coerceAtMost(config.questionableMaximumTrust)
            recoveringFromRejection -> {
                recoveryObservations++
                val recoveryFraction = (recoveryObservations.toDouble() / config.recoveryObservationsForFullWeight).coerceAtMost(1.0)
                if (recoveryFraction >= 1.0) recoveringFromRejection = false
                trustScore.value * recoveryFraction
            }
            else -> trustScore.value
        }.let { value -> recovery?.let { minOf(value, it.maximumCorrectionWeight) } ?: value }
        val correction = correctionApplier.apply(confidenceState, gnss, effectiveTrust)
        val detail = if (recoveringFromRejection || recoveryObservations in 1 until config.recoveryObservationsForFullWeight)
            "GNSS recovering gradually; ${correction.detail}" else correction.detail
        return result(correction.state, correction.applied, correction.correctionWeight, detail, innovation)
    }

    private fun applyConfidence(state: VehicleMotionState, estimate: VehiclePositionConfidenceEstimate): VehicleMotionState {
        val uncertainty = maxOf(state.uncertaintyMeters, estimate.uncertaintyMeters)
        return state.copy(
            position = state.position.copy(
                confidence = estimate.confidence,
                estimatedAccuracyMeters = uncertainty,
                source = PositionSource.VEHICLE_DEAD_RECKONING
            ),
            uncertaintyMeters = uncertainty
        )
    }

    private fun result(
        state: VehicleMotionState,
        applied: Boolean,
        weight: Double,
        detail: String,
        innovation: HardInnovationGateDecision? = null
    ) = VehicleGnssIntegrationResult(
        state, applied, weight, true, detail = detail,
        diagnosticEvidence = innovation?.let { listOf(it.evidence) } ?: emptyList()
    )
}
