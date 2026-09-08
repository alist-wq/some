package com.example.navigation.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RouteSegmentTest {
    @Test
    fun preservesRequiredSegmentFields() {
        val segment = RouteSegment(
            geometry = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)),
            lengthMeters = 100.0,
            headingDegrees = 90.0,
            roadPathId = 42L,
            turnInformation = RouteTurnInformation.START,
            accessType = RouteAccessType.VEHICLE
        )

        assertEquals(100.0, segment.lengthMeters)
        assertEquals(42L, segment.roadPathId)
        assertEquals(RouteTurnInformation.START, segment.turnInformation)
    }

    @Test
    fun rejectsInvalidGeometryLengthAndHeading() {
        assertFailsWith<IllegalArgumentException> { RouteSegment(listOf(GeoPoint(0.0, 0.0)), 1.0, null, null, null, RouteAccessType.BOTH) }
        assertFailsWith<IllegalArgumentException> { RouteSegment(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), -1.0, null, null, null, RouteAccessType.BOTH) }
        assertFailsWith<IllegalArgumentException> { RouteSegment(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), 1.0, 360.0, null, null, RouteAccessType.BOTH) }
    }
}
