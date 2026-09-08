package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.sensors.AccelerometerSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepDetectorTest {
    @Test fun `detects walking peaks with valid cadence`() {
        val detector = StepDetector()
        val first = detector.process(sample(0, 12f))
        detector.process(sample(100, 9.8f))
        val second = detector.process(sample(500, 12f))

        assertEquals(0L, first!!.timestamp.nanos)
        assertEquals(120.0, second!!.cadenceStepsPerMinute!!, 0.01)
    }

    @Test fun `refractory and maximum cadence reject rapid peaks`() {
        val detector = StepDetector()
        detector.process(sample(0, 12f))
        detector.process(sample(100, 9.8f))

        assertNull(detector.process(sample(150, 12f)))
    }

    @Test fun `low acceleration does not create a step`() {
        assertNull(StepDetector().process(sample(0, 10.5f)))
    }

    @Test fun `stationary gap restarts cadence instead of reporting invalid cadence`() {
        val detector = StepDetector()
        detector.process(sample(0, 12f))
        detector.process(sample(100, 9.8f))

        val afterGap = detector.process(sample(2_000, 12f))

        assertNull(afterGap!!.cadenceStepsPerMinute)
        assertTrue(afterGap.accelerationDeviationMetersPerSecondSquared > 1.5)
    }

    private fun sample(milliseconds: Long, z: Float) = AccelerometerSample(0f, 0f, z, MonotonicTimestamp(milliseconds * 1_000_000L))
}
