package com.example.navigation.benchmark

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.navigation.PositionConfidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationBenchmarkMetricsTest {
    private val engine = NavigationBenchmarkMetricsEngine()

    @Test fun `calculates route GNSS and confidence metrics`() {
        val result = engine.calculate(
            routeSamples = listOf(
                route(0, 1, 1, 0.0, 0.0, PositionConfidence.HIGH),
                route(10, 1, 2, 10.0, 30.0, PositionConfidence.LOW),
                route(20, 1, 2, 20.0, 40.0, PositionConfidence.LOST),
                route(30, 1, 1, 30.0, 40.0, PositionConfidence.HIGH),
                route(40, 1, 2, 40.0, 60.0, PositionConfidence.HIGH)
            ),
            gnssTrustSamples = listOf(
                trust(0, GnssTrustState.TRUSTED, true), trust(5, GnssTrustState.REJECTED, false),
                trust(10, GnssTrustState.REJECTED, false), trust(20, GnssTrustState.TRUSTED, true),
                trust(30, GnssTrustState.REJECTED, true), trust(40, GnssTrustState.TRUSTED, true)
            ),
            gnssFaultIntervals = listOf(GnssFaultInterval(time(0), time(10)))
        )

        assertEquals(40.0, result.correctRoutePercentage)
        assertEquals(14.0, result.meanRouteProgressErrorMeters)
        assertEquals(20.0, result.maxRouteProgressErrorMeters)
        assertEquals(2, result.wrongBranchCount)
        assertEquals(5, result.meanGnssRejectionDelayNanos)
        assertEquals(10, result.meanGnssRecoveryTimeNanos)
        assertEquals(1, result.gnssFalseRejectionCount)
        assertEquals(20, result.lowConfidenceDurationNanos)
    }

    @Test fun `leaves unavailable metrics null`() {
        val result = engine.calculate(emptyList(), emptyList(), emptyList())
        assertNull(result.correctRoutePercentage)
        assertNull(result.meanRouteProgressErrorMeters)
        assertNull(result.maxRouteProgressErrorMeters)
        assertNull(result.meanGnssRejectionDelayNanos)
    }

    private fun route(time: Long, expected: Long, estimated: Long, expectedProgress: Double, estimatedProgress: Double, confidence: PositionConfidence) =
        BenchmarkRouteSample(this.time(time), expected, estimated, expectedProgress, estimatedProgress, confidence)
    private fun trust(time: Long, state: GnssTrustState, trusted: Boolean) = BenchmarkGnssTrustSample(this.time(time), state, trusted)
    private fun time(nanos: Long) = MonotonicTimestamp(nanos)
}
