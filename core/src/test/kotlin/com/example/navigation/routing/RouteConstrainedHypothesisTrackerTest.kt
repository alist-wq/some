package com.example.navigation.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteConstrainedHypothesisTrackerTest {
    @Test fun `distance and right turn observation favor matching route hypothesis`() {
        val route = route()
        val tracker = RouteConstrainedHypothesisTracker(route)
        tracker.initialize(listOf(
            candidate(route.segments[0], 90.0, 90.0),
            candidate(route.segments[1], 90.0, 190.0)
        ))

        val updated = tracker.update(RouteHypothesisObservation(20.0, 90.0, RouteTurnDirection.RIGHT))

        assertEquals(2, updated.size)
        assertEquals(route.segments[1], updated.maxBy { it.probability }.segment)
        assertTrue(updated[0].probability > updated[1].probability)
        assertEquals(1.0, updated.sumOf { it.probability }, 1e-9)
    }

    @Test fun `tracker retains multiple candidates and grows uncertainty from traveled distance`() {
        val route = route()
        val tracker = RouteConstrainedHypothesisTracker(route)
        tracker.initialize(listOf(
            candidate(route.segments[0], 10.0, 10.0),
            candidate(route.segments[1], 10.0, 110.0)
        ))

        val updated = tracker.update(RouteHypothesisObservation(10.0, 45.0))

        assertEquals(2, updated.size)
        assertTrue(updated.all { it.uncertaintyMeters == 5.5 })
        assertEquals(20.0, updated[0].metersAlongSegment)
        assertEquals(120.0, updated[1].totalRouteDistanceMeters)
    }

    private fun candidate(segment: RouteSegment, along: Double, total: Double) =
        RouteCandidateState(segment, along, total, segment.headingDegrees!!, 0.5, 5.0)

    private fun route(): Route {
        val segments = listOf(segment(0.0), segment(90.0), segment(0.0))
        return Route("test", listOf(1, 2, 3, 4), listOf(11, 12, 13), 300.0, segments)
    }

    private fun segment(heading: Double) = RouteSegment(
        listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)),
        100.0,
        heading,
        null,
        null,
        RouteAccessType.VEHICLE
    )
}
