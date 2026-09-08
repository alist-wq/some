package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.sensors.BarometerSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RelativeAltitudeEstimatorTest {
    @Test
    fun `uses first pressure sample as the relative baseline`() {
        val estimator = RelativeAltitudeEstimator()

        val baseline = estimator.process(sample(1_000L, 1013.25f))
        val ascent = estimator.process(sample(2_000L, 1001.30f))
        val descent = estimator.process(sample(3_000L, 1025.34f))

        assertEquals(0.0, baseline.relativeAltitudeMeters, absoluteTolerance = 0.000001)
        assertTrue(ascent.relativeAltitudeMeters > 90.0)
        assertTrue(descent.relativeAltitudeMeters < -90.0)
        assertEquals(1013.25, ascent.baselinePressureHectopascals, absoluteTolerance = 0.000001)
    }

    @Test
    fun `requires positive pressure and increasing timestamps`() {
        val estimator = RelativeAltitudeEstimator()
        estimator.process(sample(1_000L, 1013.25f))

        assertFailsWith<IllegalArgumentException> { estimator.process(sample(2_000L, 0f)) }
        assertFailsWith<IllegalArgumentException> { estimator.process(sample(1_000L, 1013.0f)) }
    }

    private fun sample(timestampNanos: Long, pressureHectopascals: Float) = BarometerSample(
        pressureHectopascals = pressureHectopascals,
        timestamp = MonotonicTimestamp(timestampNanos)
    )
}
