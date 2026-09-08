package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ElevationEventDetectorTest {
    private val config = ElevationEventDetectorConfig(
        minimumSustainedDurationNanos = 5_000_000_000L,
        minimumVerticalChangeMeters = 1.5
    )

    @Test
    fun `reports sustained ascent and descent`() {
        val ascent = ElevationEventDetector(config)
        assertNull(ascent.process(estimate(0L, 0.0)))
        assertNull(ascent.process(estimate(2_000_000_000L, 1.0)))
        assertEquals(ElevationMotion.UP, ascent.process(estimate(6_000_000_000L, 2.0))?.motion)

        val descent = ElevationEventDetector(config)
        assertNull(descent.process(estimate(0L, 3.0)))
        assertEquals(ElevationMotion.DOWN, descent.process(estimate(6_000_000_000L, 1.0))?.motion)
    }

    @Test
    fun `reports level only after the configured duration`() {
        val detector = ElevationEventDetector(config)

        assertNull(detector.process(estimate(0L, 0.0)))
        assertNull(detector.process(estimate(4_000_000_000L, 0.2)))
        assertEquals(ElevationMotion.LEVEL, detector.process(estimate(6_000_000_000L, 0.3))?.motion)
    }

    @Test
    fun `does not repeat an unchanged sustained state`() {
        val detector = ElevationEventDetector(config)
        detector.process(estimate(0L, 0.0))
        assertEquals(ElevationMotion.UP, detector.process(estimate(6_000_000_000L, 2.0))?.motion)

        assertNull(detector.process(estimate(7_000_000_000L, 2.4)))
    }

    private fun estimate(timestampNanos: Long, relativeAltitudeMeters: Double) = RelativeAltitudeEstimate(
        relativeAltitudeMeters = relativeAltitudeMeters,
        baselinePressureHectopascals = 1013.25,
        timestamp = MonotonicTimestamp(timestampNanos)
    )
}
