package com.example.navigation.app

import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.navigation.*
import com.example.navigation.routing.*
import org.junit.Assert.*
import org.junit.Test

class RouteUiPresentationTest {
    private val version = RegionalPackageVersion("fixture", "map-1", "graph-1")
    private val route = Route("fixture", listOf(1, 2), listOf(10), 100.0, listOf(
        RouteSegment(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), 100.0, 90.0, 10, RouteTurnInformation.ARRIVAL, RouteAccessType.BOTH)
    ))
    private val candidate = RoutePlanCandidate(route,
        RoutePlanningOrigin(GeoPoint(0.0, 0.0), PositionSource.MANUAL_START, PositionConfidence.LOW, 100.0),
        ConfirmedDestination("museum", "Museum", GeoPoint(0.0, 0.01), version), NavigationMode.PEDESTRIAN, version)

    @Test fun `preview distinguishes a candidate from planning failures and active routes`() {
        val ready = RoutePreviewPresentationFactory.create(RoutePlanningState(RoutePlanningPhase.ROUTE_READY, candidate))
        assertTrue(ready.visible); assertTrue(ready.canStartNavigation); assertEquals(2, ready.routeGeometry.size)
        assertEquals(GeoPoint(0.0, 0.01), ready.destination)
        val failed = RoutePreviewPresentationFactory.create(RoutePlanningState(RoutePlanningPhase.GRAPH_UNAVAILABLE, reason = "Graph missing"))
        assertTrue(failed.visible); assertFalse(failed.canStartNavigation); assertEquals("Graph missing", failed.detail)
        assertFalse(RoutePreviewPresentationFactory.create(RoutePlanningState()).visible)
    }

    @Test fun `active guidance remains honest for every lifecycle state`() {
        val plan = ConfirmedRoutePlan("confirmed", candidate)
        val active = ActiveRouteGuidancePresentationFactory.create(ActiveRouteState(
            phase = ActiveRoutePhase.ACTIVE, confirmedPlan = plan, progress = RouteProgress(0, 50.0, 50.0),
            remainingDistanceMeters = 50.0, nextManeuver = "Turn right", confidence = PositionConfidence.HIGH, estimatedAccuracyMeters = 8.0
        ))
        assertEquals("NAVIGATION ACTIVE", active.label); assertEquals("Turn right", active.maneuver); assertEquals(0.5f, active.progress)
        val low = ActiveRouteGuidancePresentationFactory.create(ActiveRouteState(ActiveRoutePhase.LOW_CONFIDENCE, plan, confidence = PositionConfidence.LOW))
        assertEquals("POSITION CONFIDENCE LOW", low.label); assertNull(low.maneuver)
        val arrived = ActiveRouteGuidancePresentationFactory.create(ActiveRouteState(ActiveRoutePhase.ARRIVED, plan, confidence = PositionConfidence.HIGH))
        assertEquals("ARRIVED", arrived.label); assertTrue(arrived.canChooseNewDestination)
        val paused = ActiveRouteGuidancePresentationFactory.create(ActiveRouteState(ActiveRoutePhase.PAUSED, plan))
        assertEquals("GUIDANCE PAUSED", paused.label); assertNull(paused.maneuver)
        val awaiting = ActiveRouteGuidancePresentationFactory.create(ActiveRouteState(ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS, plan))
        assertTrue(awaiting.label.contains("FRESH")); assertNull(awaiting.maneuver)
        val cancelled = ActiveRouteGuidancePresentationFactory.create(ActiveRouteState(ActiveRoutePhase.CANCELLED, plan))
        assertEquals("NAVIGATION CANCELLED", cancelled.label); assertFalse(cancelled.canStop)
    }
}
