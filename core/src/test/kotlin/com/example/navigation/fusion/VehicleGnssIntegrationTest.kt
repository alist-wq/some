package com.example.navigation.fusion

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.*
import com.example.navigation.navigation.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VehicleGnssIntegrationTest {
    @Test fun `rejected GNSS is recorded without correction while DR and matching continue`() {
        val recorded = mutableListOf<GnssSample>()
        val integration = VehicleGnssIntegration(GnssSampleRecorder(recorded::add))
        val original = state()

        val result = integration.process(original, gnss(10.0), score(0.1), GnssTrustState.REJECTED, confidence(50.0))

        assertEquals(1, recorded.size)
        assertFalse(result.correctionApplied)
        assertEquals(original.position.latitude, result.state.position.latitude)
        assertTrue(result.state.uncertaintyMeters >= 50.0)
        assertTrue(result.continueDeadReckoning)
        assertTrue(result.continueRouteMatching)
    }

    @Test fun `questionable GNSS cannot make a strong correction`() {
        val integration = VehicleGnssIntegration(GnssSampleRecorder { })

        val result = integration.process(state(), gnss(10.0), score(1.0), GnssTrustState.QUESTIONABLE, confidence(50.0))

        assertTrue(result.correctionWeight < 0.25)
    }

    @Test fun `GNSS recovery weight grows gradually after rejection`() {
        val integration = VehicleGnssIntegration(GnssSampleRecorder { })
        integration.process(state(), gnss(10.0), score(0.1), GnssTrustState.REJECTED, confidence(50.0))

        val first = integration.process(state(), gnss(10.0), score(1.0), GnssTrustState.RECOVERING, confidence(50.0))
        val second = integration.process(state(), gnss(10.0), score(1.0), GnssTrustState.RECOVERING, confidence(50.0))

        assertTrue(first.correctionApplied)
        assertTrue(first.correctionWeight < second.correctionWeight)
        assertTrue(first.state.position.latitude < second.state.position.latitude)
    }

    private fun confidence(uncertainty: Double) = VehiclePositionConfidenceEstimate(uncertainty, PositionConfidence.LOW)
    private fun state() = VehicleMotionState(
        NavigationPosition(0.0, 0.0, PositionConfidence.HIGH, 10.0, PositionSource.VEHICLE_DEAD_RECKONING, MonotonicTimestamp(0)),
        0.0, 10.0, 0.0, 0.0, 10.0, MonotonicTimestamp(0)
    )
    private fun gnss(latitude: Double) = GnssSample(latitude, 0.0, null, 5f, null, null, null, null, null, "gps", MonotonicTimestamp(1), null)
    private fun score(value: Double) = GnssTrustScore(value, emptyList())
}
