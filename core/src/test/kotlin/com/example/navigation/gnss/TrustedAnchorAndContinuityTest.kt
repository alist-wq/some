package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrustedAnchorAndContinuityTest {
    @Test fun `trusted updates while questionable and rejected including remote spoof preserve anchor`() {
        val anchor = LastTrustedPositionAnchor()
        val first = position(0, 50.45, 30.52)
        val second = position(10, 50.451, 30.521)
        val remote = position(20, -12.0464, -77.0428)
        assertTrue(anchor.observe(first, GnssTrustState.TRUSTED).updated)
        assertTrue(anchor.observe(second, GnssTrustState.TRUSTED).updated)
        assertFalse(anchor.observe(remote, GnssTrustState.QUESTIONABLE).updated)
        assertFalse(anchor.observe(remote.copy(timestamp = MonotonicTimestamp(30)), GnssTrustState.REJECTED).updated)
        assertEquals(second, anchor.anchor)
    }

    @Test fun `questionable update policy is explicit`() {
        val anchor = LastTrustedPositionAnchor(LastTrustedPositionAnchorConfig(QuestionableAnchorPolicy.UPDATE))
        anchor.observe(position(0, 50.0, 30.0), GnssTrustState.TRUSTED)
        val questionable = position(1, 50.1, 30.1)
        assertTrue(anchor.observe(questionable, GnssTrustState.QUESTIONABLE).updated)
        assertEquals(questionable, anchor.anchor)
    }

    @Test fun `geographic continuity accepts local movement and rejects a remote teleport`() {
        val evaluator = GeographicContinuityEvidenceEvaluator()
        val anchor = position(0, 50.45, 30.52)
        val local = evaluator.evaluate(GeographicContinuityInput(anchor, gnss(10_000_000_000L, 50.4501, 30.5201), NavigationMode.VEHICLE, IndependentMotionEstimate(10.0, 1.0)))
        val remote = evaluator.evaluate(GeographicContinuityInput(anchor, gnss(10_000_000_000L, -12.0464, -77.0428), NavigationMode.VEHICLE, IndependentMotionEstimate(10.0, 1.0)))
        assertEquals(GnssTrustEvidenceAvailability.AVAILABLE, local.availability)
        assertTrue(local.trustScore!! > 0.99)
        assertTrue(remote.trustScore!! < 0.001)
    }

    @Test fun `geographic continuity requires independent motion rather than GNSS speed`() {
        val evidence = GeographicContinuityEvidenceEvaluator().evaluate(
            GeographicContinuityInput(position(0, 50.0, 30.0), gnss(1, 50.0, 30.0), NavigationMode.UNKNOWN, null)
        )
        assertEquals(GnssTrustEvidenceAvailability.UNAVAILABLE, evidence.availability)
    }

    private fun position(t: Long, latitude: Double, longitude: Double) = NavigationPosition(latitude, longitude, PositionConfidence.HIGH, 5.0, PositionSource.GNSS, MonotonicTimestamp(t))
    private fun gnss(t: Long, latitude: Double, longitude: Double) = GnssSample(latitude, longitude, null, 5f, null, 99f, null, null, null, "gps", MonotonicTimestamp(t), null)
}
