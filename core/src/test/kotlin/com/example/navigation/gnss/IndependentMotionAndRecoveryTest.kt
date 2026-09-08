package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndependentMotionAndRecoveryTest {
    @Test fun `remote geometry is adverse despite plausible GNSS-reported fields`() {
        val evaluator = IndependentMotionRemoteGnssEvidenceEvaluator()
        evaluator.observe(observation(50.45, 30.52, 0, 0.0))
        val evidence = evaluator.observe(observation(-12.0464, -77.0428, 1_000_000_000L, 10.0))
        assertTrue(evidence.trustScore!! < 0.01)
    }
    @Test fun `recovery needs repeated anchor motion and route agreement then increases weight`() {
        val gate = ConservativeGnssRecoveryGate(ConservativeGnssRecoveryConfig(consistentObservationsRequired = 3, gradualObservations = 4))
        val anchor = evidence(GnssTrustEvidenceType.GEOGRAPHIC_CONTINUITY, 0.9)
        val motion = evidence(GnssTrustEvidenceType.INDEPENDENT_MOTION_CONTINUITY, 0.9)
        val route = evidence(GnssTrustEvidenceType.ROUTE_CONSISTENCY, 0.9)
        assertFalse(gate.observe(anchor, motion, route).allowCorrection)
        assertFalse(gate.observe(anchor, motion, route).allowCorrection)
        val first = gate.observe(anchor, motion, route)
        val second = gate.observe(anchor, motion, route)
        assertTrue(first.allowCorrection); assertTrue(first.maximumCorrectionWeight < second.maximumCorrectionWeight)
        assertFalse(gate.observe(evidence(GnssTrustEvidenceType.GEOGRAPHIC_CONTINUITY, 0.2), motion, route).allowCorrection)
    }
    private fun observation(lat: Double, lon: Double, time: Long, distance: Double) = IndependentMotionRemoteGnssObservation(
        GnssSample(lat, lon, null, 3f, null, 20f, null, 90f, null, "gps", MonotonicTimestamp(time), null),
        cumulativeObdDistanceMeters = distance, cumulativePdrDistanceMeters = distance, routeProgressMeters = distance
    )
    private fun evidence(type: GnssTrustEvidenceType, score: Double) = GnssTrustEvidence(type, GnssTrustEvidenceAvailability.AVAILABLE, score, "test", MonotonicTimestamp(1))
}
