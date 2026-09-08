package com.example.navigation.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RouteProgressEstimatorTest {
    @Test fun `initializes from trusted position and advances using vehicle distance`() {
        val estimator = RouteProgressEstimator(route())
        assertEquals(RouteProgress(0, 60.0, 60.0), estimator.initialize(TrustedRoutePosition(0, 60.0, 60.0), 1_000.0))
        assertEquals(RouteProgress(1, 30.0, 130.0), estimator.estimate(1_070.0))
    }

    @Test fun `progress stops at route end and rejects backwards distance`() {
        val estimator = RouteProgressEstimator(route())
        estimator.initialize(TrustedRoutePosition(0, 0.0, 0.0), 0.0)
        assertEquals(RouteProgress(1, 80.0, 180.0), estimator.estimate(300.0))
        assertFailsWith<IllegalArgumentException> { estimator.estimate(-1.0) }
    }

    private fun route() = Route("test", listOf(1, 2, 3), listOf(11, 12), 180.0, listOf(segment(100.0, 0.0), segment(80.0, 90.0)))
    private fun segment(length: Double, heading: Double) = RouteSegment(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), length, heading, null, null, RouteAccessType.VEHICLE)
}
