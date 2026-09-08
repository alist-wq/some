package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GnssHeadingMotionEvidenceEvaluatorTest {
    @Test fun `matching gyro turn and road heading are supportive`() {
        val evaluator = GnssHeadingMotionEvidenceEvaluator()
        evaluator.evaluate(sample(0f, 0), HeadingMotionEstimate(null, 0.0))

        val result = evaluator.evaluate(sample(30f, 1), HeadingMotionEstimate(30.0, 30.0))

        assertEquals(GnssTrustEvidenceAvailability.AVAILABLE, result.availability)
        assertTrue(result.trustScore!! > 0.99)
    }

    @Test fun `large heading disagreement is adverse`() {
        val evaluator = GnssHeadingMotionEvidenceEvaluator()
        evaluator.evaluate(sample(0f, 0), HeadingMotionEstimate(null, null))

        assertTrue(evaluator.evaluate(sample(180f, 1), HeadingMotionEstimate(0.0, 0.0)).trustScore!! < 0.2)
    }

    @Test fun `low speed makes bearing evidence unavailable`() {
        val result = GnssHeadingMotionEvidenceEvaluator().evaluate(sample(90f, 0, 1f), HeadingMotionEstimate(null, 90.0))

        assertEquals(GnssTrustEvidenceAvailability.UNAVAILABLE, result.availability)
    }

    private fun sample(bearing: Float, seconds: Long, speed: Float = 10f) = GnssSample(
        0.0, 0.0, null, 1f, null, speed, null, bearing, null, "gps", MonotonicTimestamp(seconds * 1_000_000_000L), null
    )
}
