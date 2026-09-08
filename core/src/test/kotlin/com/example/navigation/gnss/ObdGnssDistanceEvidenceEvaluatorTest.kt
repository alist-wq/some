package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObdGnssDistanceEvidenceEvaluatorTest {
    @Test fun `matching rolling distances produce supportive evidence`() {
        val evaluator = ObdGnssDistanceEvidenceEvaluator()
        evaluator.observeGnss(gnss(0.0, 0.0, 0))
        evaluator.observeGnss(gnss(0.0, 0.0008993, 10))
        evaluator.observeObd(obd(10.0, 0))
        evaluator.observeObd(obd(10.0, 10))

        val result = evaluator.evaluate(timestamp(10))

        assertEquals(GnssTrustEvidenceAvailability.AVAILABLE, result.availability)
        assertTrue(result.trustScore!! > 0.99)
    }

    @Test fun `trajectory distance avoids false disagreement on a curve`() {
        val evaluator = ObdGnssDistanceEvidenceEvaluator()
        evaluator.observeGnss(gnss(0.0, 0.0, 0))
        evaluator.observeGnss(gnss(0.00045, 0.00045, 5))
        evaluator.observeGnss(gnss(0.0, 0.0009, 10))
        evaluator.observeObd(obd(14.15, 0))
        evaluator.observeObd(obd(14.15, 10))

        assertTrue(evaluator.evaluate(timestamp(10)).trustScore!! > 0.99)
    }

    @Test fun `large distance disagreement produces adverse evidence`() {
        val evaluator = ObdGnssDistanceEvidenceEvaluator()
        evaluator.observeGnss(gnss(0.0, 0.0, 0))
        evaluator.observeGnss(gnss(0.0, 0.00009, 10))
        evaluator.observeObd(obd(20.0, 0))
        evaluator.observeObd(obd(20.0, 10))

        assertTrue(evaluator.evaluate(timestamp(10)).trustScore!! < 0.01)
    }

    @Test fun `configured window clips both trajectories consistently`() {
        val evaluator = ObdGnssDistanceEvidenceEvaluator(ObdGnssDistanceEvidenceConfig(windowNanos = seconds(5)))
        evaluator.observeGnss(gnss(0.0, 0.0, 0))
        evaluator.observeGnss(gnss(0.0, 0.0008993, 10))
        evaluator.observeObd(obd(10.0, 0))
        evaluator.observeObd(obd(10.0, 10))

        assertTrue(evaluator.evaluate(timestamp(10)).trustScore!! > 0.99)
    }

    @Test fun `insufficient samples produce unavailable evidence`() {
        val evaluator = ObdGnssDistanceEvidenceEvaluator()
        evaluator.observeGnss(gnss(0.0, 0.0, 0))
        evaluator.observeObd(obd(0.0, 0))

        assertEquals(GnssTrustEvidenceAvailability.UNAVAILABLE, evaluator.evaluate(timestamp(1)).availability)
    }

    private fun gnss(latitude: Double, longitude: Double, seconds: Long) = GnssSample(
        latitude, longitude, null, 1f, null, null, null, null, null, "gps", timestamp(seconds), null
    )
    private fun obd(speed: Double, seconds: Long) = VehicleSpeedSample(speed, timestamp(seconds), VehicleSpeedSource.OBD)
    private fun timestamp(seconds: Long) = MonotonicTimestamp(seconds(seconds))
    private companion object { fun seconds(value: Long) = value * 1_000_000_000L }
}
