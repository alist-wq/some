package com.example.navigation.routing

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RouteTurnDetectorTest {
    @Test fun `right route turn and right gyro turn produce expected and observed events`() {
        val events = RouteTurnDetector(route()).detect(1, 0.0, 90.0, MonotonicTimestamp(1))
        assertEquals(RouteTurnDirection.RIGHT, assertIs<RouteTurnEvent.Expected>(events[0]).direction)
        assertEquals(RouteTurnDirection.RIGHT, assertIs<RouteTurnEvent.Observed>(events[1]).direction)
        assertEquals(2, events.size)
    }

    @Test fun `opposite gyro turn produces a turn mismatch`() {
        val events = RouteTurnDetector(route()).detect(1, 0.0, 270.0, MonotonicTimestamp(2))
        val mismatch = assertIs<RouteTurnEvent.Mismatch>(events[2])
        assertEquals(RouteTurnDirection.RIGHT, mismatch.expected)
        assertEquals(RouteTurnDirection.LEFT, mismatch.observed)
    }

    private fun route() = Route("test", listOf(1, 2, 3), listOf(11, 12), 20.0, listOf(segment(0.0), segment(90.0)))
    private fun segment(heading: Double) = RouteSegment(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), 10.0, heading, null, null, RouteAccessType.VEHICLE)
}
