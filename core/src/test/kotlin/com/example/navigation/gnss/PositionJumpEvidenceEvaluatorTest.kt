package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PositionJumpEvidenceEvaluatorTest {
    @Test fun `plausible motion retains high trust evidence`() {
        val result = PositionJumpEvidenceEvaluator().evaluate(sample(0.0, 0), sample(0.00005, 1_000_000_000), IndependentMotionEstimate(10.0))
        assertTrue(result.trustScore!! > 0.9)
    }
    @Test fun `large teleport produces low trust evidence without rejection`() {
        val result = PositionJumpEvidenceEvaluator().evaluate(sample(0.0, 0), sample(0.1, 1_000_000_000), IndependentMotionEstimate(10.0))
        assertTrue(result.trustScore!! < 0.01)
        assertEquals(GnssTrustEvidenceAvailability.AVAILABLE, result.availability)
    }
    @Test fun `nonmonotonic samples produce unavailable evidence`() {
        val result = PositionJumpEvidenceEvaluator().evaluate(sample(0.0, 10), sample(0.0, 10), IndependentMotionEstimate(0.0))
        assertEquals(GnssTrustEvidenceAvailability.UNAVAILABLE, result.availability)
    }
    private fun sample(latitude: Double, timestamp: Long) = GnssSample(latitude, 0.0, null, 1f, null, null, null, null, null, "gps", MonotonicTimestamp(timestamp), null)
}
