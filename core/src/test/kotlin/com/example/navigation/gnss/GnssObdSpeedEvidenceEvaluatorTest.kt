package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GnssObdSpeedEvidenceEvaluatorTest {
    @Test fun `matching rolling GNSS and OBD speeds are strongly supportive`() {
        val evaluator = GnssObdSpeedEvidenceEvaluator()
        evaluator.observeGnss(gnss(10f, 1)); evaluator.observeObd(obd(10.2, 1))
        assertTrue(evaluator.evaluate(MonotonicTimestamp(1_000_000_000)).trustScore!! > 0.9)
    }
    @Test fun `large speed disagreement is adverse`() {
        val evaluator = GnssObdSpeedEvidenceEvaluator()
        evaluator.observeGnss(gnss(2f, 1)); evaluator.observeObd(obd(20.0, 1))
        assertEquals(0.0, evaluator.evaluate(MonotonicTimestamp(1_000_000_000)).trustScore)
    }
    @Test fun `stops use a tolerant low speed comparison`() {
        val evaluator = GnssObdSpeedEvidenceEvaluator()
        evaluator.observeGnss(gnss(0.4f, 1)); evaluator.observeObd(obd(1.0, 1))
        assertTrue(evaluator.evaluate(MonotonicTimestamp(1_000_000_000)).trustScore!! > 0.9)
    }
    @Test fun `OBD dropout and missing GNSS velocity are explicit unavailable evidence`() {
        val evaluator = GnssObdSpeedEvidenceEvaluator()
        evaluator.observeGnss(gnss(5f, 1)); evaluator.observeObd(obd(5.0, 1))
        assertEquals(GnssTrustEvidenceAvailability.UNAVAILABLE, evaluator.evaluate(MonotonicTimestamp(4_000_000_000)).availability)
        evaluator.observeGnss(gnss(null, 5)); evaluator.observeObd(obd(5.0, 5))
        assertEquals(GnssTrustEvidenceAvailability.UNAVAILABLE, evaluator.evaluate(MonotonicTimestamp(5_000_000_000)).availability)
    }
    private fun gnss(speed: Float?, second: Long) = GnssSample(0.0, 0.0, null, null, null, speed, null, null, null, "gps", MonotonicTimestamp(second * 1_000_000_000), null)
    private fun obd(speed: Double, second: Long) = VehicleSpeedSample(speed, MonotonicTimestamp(second * 1_000_000_000), VehicleSpeedSource.OBD)
}
