package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationGuidancePresentationTest {
    @Test fun `route-ready and navigation states map to their explicit transitions`() {
        val routeReady = NavigationGuidancePresentationFactory.create(true, NavigationGuidanceState(
            phase = NavigationGuidancePhase.ROUTE_READY,
            routeConfidence = RouteGuidanceConfidence.SUFFICIENT,
            nextManeuver = "Turn right"
        ))
        val navigating = NavigationGuidancePresentationFactory.create(true, NavigationGuidanceState(
            phase = NavigationGuidancePhase.NAVIGATING,
            routeConfidence = RouteGuidanceConfidence.SUFFICIENT,
            nextManeuver = "Turn right",
            distanceToManeuverMeters = 250.0,
            routeProgress = 0.25f
        ))

        assertEquals("ROUTE READY", routeReady.stateLabel)
        assertEquals("First maneuver: Turn right", routeReady.turnPreview)
        assertFalse(routeReady.isManeuverConfirmed)
        assertEquals("NAVIGATION ACTIVE", navigating.stateLabel)
        assertEquals("Turn right", navigating.maneuverLabel)
        assertEquals("250 m", navigating.distanceLabel)
        assertEquals(0.25f, navigating.routeProgress)
        assertTrue(navigating.isManeuverConfirmed)
    }

    @Test fun `rerouting and arrival remain understandable without motion`() {
        val rerouting = NavigationGuidancePresentationFactory.create(true, NavigationGuidanceState(
            phase = NavigationGuidancePhase.REROUTING,
            routeProgress = 0.5f
        ))
        val arrived = NavigationGuidancePresentationFactory.create(true, NavigationGuidanceState(
            phase = NavigationGuidancePhase.ARRIVED
        ))

        assertEquals("REROUTING", rerouting.stateLabel)
        assertEquals("Finding a new route", rerouting.turnPreview)
        assertEquals("ARRIVED", arrived.stateLabel)
        assertEquals(1f, arrived.routeProgress)
    }

    @Test fun `missing route hides guidance and insufficient confidence never confirms a maneuver`() {
        val missingRoute = NavigationGuidancePresentationFactory.create(false, NavigationGuidanceState(
            phase = NavigationGuidancePhase.NAVIGATING,
            routeConfidence = RouteGuidanceConfidence.SUFFICIENT,
            nextManeuver = "Turn right"
        ))
        val uncertainRoute = NavigationGuidancePresentationFactory.create(true, NavigationGuidanceState(
            phase = NavigationGuidancePhase.NAVIGATING,
            routeConfidence = RouteGuidanceConfidence.INSUFFICIENT,
            nextManeuver = "Turn right",
            distanceToManeuverMeters = 20.0
        ))

        assertFalse(missingRoute.visible)
        assertEquals("GUIDANCE AWAITING RELIABLE ROUTE", uncertainRoute.stateLabel)
        assertNull(uncertainRoute.maneuverLabel)
        assertNull(uncertainRoute.distanceLabel)
        assertFalse(uncertainRoute.isManeuverConfirmed)
    }
}
