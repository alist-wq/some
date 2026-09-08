package com.example.navigation.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RouteCandidateStateTest {
    @Test fun `candidate retains all route position and confidence fields`() {
        val segment = segment()
        val candidate = RouteCandidateState(segment, 25.0, 125.0, 90.0, 0.75, 12.0)

        assertEquals(segment, candidate.segment)
        assertEquals(25.0, candidate.metersAlongSegment)
        assertEquals(125.0, candidate.totalRouteDistanceMeters)
        assertEquals(90.0, candidate.headingDegrees)
        assertEquals(0.75, candidate.probability)
        assertEquals(12.0, candidate.uncertaintyMeters)
    }

    @Test fun `candidate rejects invalid bounds`() {
        val segment = segment()
        assertFailsWith<IllegalArgumentException> { RouteCandidateState(segment, 101.0, 100.0, 0.0, 0.5, 1.0) }
        assertFailsWith<IllegalArgumentException> { RouteCandidateState(segment, 10.0, 100.0, 360.0, 0.5, 1.0) }
        assertFailsWith<IllegalArgumentException> { RouteCandidateState(segment, 10.0, 100.0, 0.0, 1.1, 1.0) }
        assertFailsWith<IllegalArgumentException> { RouteCandidateState(segment, 10.0, 100.0, 0.0, 0.5, -1.0) }
    }

    private fun segment() = RouteSegment(
        geometry = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)),
        lengthMeters = 100.0,
        headingDegrees = 90.0,
        roadPathId = 7L,
        turnInformation = null,
        accessType = RouteAccessType.VEHICLE
    )
}
