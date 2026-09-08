package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.routing.GeoPoint
import com.example.navigation.routing.Route
import com.example.navigation.routing.RouteAccessType
import com.example.navigation.routing.RouteSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationMapPresentationTest {
    @Test fun `route estimate heading and destination map to distinct honest primitives`() {
        val route = Route("fixture", listOf(1, 2), listOf(1), 100.0, listOf(
            RouteSegment(listOf(GeoPoint(50.0, 30.0), GeoPoint(50.0, 30.01)), 100.0, 90.0, 1L, null, RouteAccessType.VEHICLE)
        ))
        val presentation = NavigationMapPresentationFactory.create(NavigationMapState(
            route, NavigationPosition(50.0, 30.0, PositionConfidence.MEDIUM, 15.0, PositionSource.HYBRID, MonotonicTimestamp(1)), 90.0, null
        ))
        assertEquals(2, presentation.routeGeometry.size)
        assertEquals(GeoPoint(50.0, 30.01), presentation.destination)
        assertNotNull(presentation.estimatedPosition)
        assertNotNull(presentation.directionEnd)
        assertEquals(15.0, presentation.uncertaintyRadiusMeters)
        assertEquals(true, presentation.showPrecisePositionMarker)
    }

    @Test fun `missing navigation state does not invent a position or destination`() {
        val presentation = NavigationMapPresentationFactory.create(NavigationMapState())
        assertEquals(emptyList<GeoPoint>(), presentation.routeGeometry)
        assertNull(presentation.estimatedPosition)
        assertNull(presentation.directionEnd)
        assertNull(presentation.destination)
        assertNull(presentation.uncertaintyCenter)
        assertNull(presentation.uncertaintyRadiusMeters)
        assertEquals(true, presentation.showPrecisePositionMarker)
    }

    @Test fun `low confidence uses an uncertainty area instead of a precise marker`() {
        val position = NavigationPosition(50.0, 30.0, PositionConfidence.LOW, 35.0, PositionSource.VEHICLE_DEAD_RECKONING, MonotonicTimestamp(1))
        val presentation = NavigationMapPresentationFactory.create(NavigationMapState(estimatedPosition = position))
        assertEquals(GeoPoint(50.0, 30.0), presentation.uncertaintyCenter)
        assertEquals(35.0, presentation.uncertaintyRadiusMeters)
        assertEquals(false, presentation.showPrecisePositionMarker)
    }

    @Test fun `lost confidence continues to hide the precise marker`() {
        val position = NavigationPosition(50.0, 30.0, PositionConfidence.LOST, 80.0, PositionSource.VEHICLE_DEAD_RECKONING, MonotonicTimestamp(1))
        val presentation = NavigationMapPresentationFactory.create(NavigationMapState(estimatedPosition = position))
        assertEquals(GeoPoint(50.0, 30.0), presentation.uncertaintyCenter)
        assertEquals(80.0, presentation.uncertaintyRadiusMeters)
        assertEquals(false, presentation.showPrecisePositionMarker)
    }
}
