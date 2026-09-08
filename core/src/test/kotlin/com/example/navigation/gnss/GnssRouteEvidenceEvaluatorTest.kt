package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.routing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GnssRouteEvidenceEvaluatorTest {
    @Test fun `near reachable projection compatible with dominant hypothesis is supportive`() {
        val result = GnssRouteEvidenceEvaluator().evaluate(timestamp, GnssRouteProjection(2.0, 102.0), ReachableRouteProgress(90.0, 110.0), listOf(candidate(100.0, 1.0)))

        assertTrue(result.trustScore!! > 0.9)
    }

    @Test fun `distant unreachable projection is adverse`() {
        val result = GnssRouteEvidenceEvaluator().evaluate(timestamp, GnssRouteProjection(200.0, 500.0), ReachableRouteProgress(90.0, 110.0), listOf(candidate(100.0, 1.0)))

        assertTrue(result.trustScore!! < 0.05)
    }

    @Test fun `no hypotheses leaves route evidence unavailable`() {
        val result = GnssRouteEvidenceEvaluator().evaluate(timestamp, GnssRouteProjection(0.0, 100.0), ReachableRouteProgress(90.0, 110.0), emptyList())

        assertEquals(GnssTrustEvidenceAvailability.UNAVAILABLE, result.availability)
    }

    private fun candidate(progress: Double, probability: Double) = RouteCandidateState(segment, 0.0, progress, 0.0, probability, 5.0)
    private companion object {
        val timestamp = MonotonicTimestamp(1)
        val segment = RouteSegment(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001)), 100.0, 0.0, null, null, RouteAccessType.VEHICLE)
    }
}
