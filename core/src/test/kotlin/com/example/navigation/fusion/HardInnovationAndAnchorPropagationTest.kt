package com.example.navigation.fusion

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.*
import com.example.navigation.navigation.*
import com.example.navigation.replay.GnssSessionRecord
import com.example.navigation.replay.Lima001Scenario
import com.example.navigation.replay.RemoteLocationSpoofSimulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HardInnovationAndAnchorPropagationTest {
    @Test fun `noise multipath and tunnel exit are not hard rejected but a remote teleport is`() {
        val gate = HardInnovationGate()
        val state = state(50.45, 30.52, 20.0, 0)
        assertFalse(gate.evaluate(state, gnss(50.4502, 30.5202, 1_000_000_000L, 10f)).blockCorrection)
        assertFalse(gate.evaluate(state, gnss(50.46, 30.52, 1_000_000_000L, 250f)).blockCorrection)
        assertFalse(gate.evaluate(state, gnss(50.455, 30.52, 30_000_000_000L, 50f)).blockCorrection)
        assertTrue(gate.evaluate(state, gnss(-12.0464, -77.0428, 1_000_000_000L, 5f)).blockCorrection)
    }

    @Test fun `hard gate records the remote sample before blocking correction`() {
        val recorded = mutableListOf<GnssSample>()
        val result = VehicleGnssIntegration(GnssSampleRecorder(recorded::add)).process(
            state(50.45, 30.52, 10.0, 0), gnss(-12.0464, -77.0428, 1_000_000_000L, 5f), score(1.0),
            GnssTrustState.TRUSTED, confidence(), HardInnovationGate()
        )
        assertEquals(1, recorded.size); assertFalse(result.correctionApplied)
        assertEquals(GnssTrustEvidenceType.HARD_INNOVATION, result.diagnosticEvidence.single().type)
    }

    @Test fun `rejected Lima state starts and continues from local trusted anchor`() {
        val start = TrustedAnchorPropagationStart()
        val anchor = position(50.45, 30.52, 0)
        val remoteState = state(-12.0464, -77.0428, 10.0, 1)
        val first = start.apply(remoteState, anchor, GnssTrustState.REJECTED)
        assertTrue(first.startedFromAnchor); assertEquals(anchor.latitude, first.state.position.latitude)
        val propagated = first.state.copy(position = first.state.position.copy(latitude = 50.451))
        assertEquals(50.451, start.apply(propagated, anchor, GnssTrustState.REJECTED).state.position.latitude)
    }

    @Test fun `LIMA 001 replay observation cannot move propagation away from local anchor`() {
        val local = state(50.45, 30.52, 10.0, 0)
        val simulator = RemoteLocationSpoofSimulator(
            Lima001Scenario.defaultRemoteLocation(MonotonicTimestamp(1_000_000_000L), 10_000_000_000L)
        )
        val spoofed = (simulator.transform(GnssSessionRecord(
            gnss(50.4501, 30.5201, 1_000_000_000L, 5f)
        )).single() as GnssSessionRecord).sample
        val gate = HardInnovationGate().evaluate(local, spoofed)
        val anchored = TrustedAnchorPropagationStart().apply(
            local.copy(position = local.position.copy(latitude = spoofed.latitude, longitude = spoofed.longitude)),
            local.position,
            GnssTrustState.REJECTED
        )
        assertTrue(gate.blockCorrection)
        assertEquals(50.45, anchored.state.position.latitude)
        assertEquals(30.52, anchored.state.position.longitude)
    }

    @Test fun `first plausible returning fix cannot correct and gradual reentry remains bounded`() {
        val integration = VehicleGnssIntegration(GnssSampleRecorder { })
        val original = state(50.45, 30.52, 10.0, 0)
        val returning = gnss(50.4501, 30.5201, 1_000_000_000L, 5f)
        val held = integration.process(
            original, returning, score(1.0), GnssTrustState.RECOVERING, confidence(), HardInnovationGate(),
            ConservativeGnssRecoveryDecision(false, 0.0, 1, "awaiting sustained consistency")
        )
        val gradual = integration.process(
            original, returning, score(1.0), GnssTrustState.RECOVERING, confidence(), HardInnovationGate(),
            ConservativeGnssRecoveryDecision(true, 0.25, 3, "gradual re-entry")
        )
        assertFalse(held.correctionApplied)
        assertTrue(gradual.correctionApplied)
        assertTrue(gradual.correctionWeight < 0.25)
    }
    private fun score(value: Double) = GnssTrustScore(value, emptyList())
    private fun confidence() = VehiclePositionConfidenceEstimate(30.0, PositionConfidence.LOW)
    private fun position(lat: Double, lon: Double, time: Long) = NavigationPosition(lat, lon, PositionConfidence.HIGH, 5.0, PositionSource.GNSS, MonotonicTimestamp(time))
    private fun state(lat: Double, lon: Double, speed: Double, time: Long) = VehicleMotionState(position(lat, lon, time), 0.0, speed, 0.0, 0.0, 10.0, MonotonicTimestamp(time))
    private fun gnss(lat: Double, lon: Double, time: Long, accuracy: Float) = GnssSample(lat, lon, null, accuracy, null, null, null, null, null, "gps", MonotonicTimestamp(time), null)
}
