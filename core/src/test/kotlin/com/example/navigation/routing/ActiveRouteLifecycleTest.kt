package com.example.navigation.routing

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.navigation.*
import kotlin.test.*

class ActiveRouteLifecycleTest {
    private val route = Route("fixture", listOf(1, 2, 3), listOf(10, 11), 200.0, listOf(
        RouteSegment(listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)), 100.0, 90.0, 10, RouteTurnInformation.START, RouteAccessType.VEHICLE),
        RouteSegment(listOf(GeoPoint(0.0, 0.01), GeoPoint(0.0, 0.02)), 100.0, 90.0, 11, RouteTurnInformation.ARRIVAL, RouteAccessType.VEHICLE)
    ))
    private val version = RegionalPackageVersion("fixture", "map-1", "graph-1")
    private val candidate = RoutePlanCandidate(route,
        RoutePlanningOrigin(GeoPoint(0.0, 0.0), PositionSource.GNSS, PositionConfidence.HIGH, 5.0),
        ConfirmedDestination("destination", "Museum", GeoPoint(0.0, 0.02), version), NavigationMode.VEHICLE, version)
    private val plan = ConfirmedRoutePlan("confirmed-1", candidate)

    @Test fun `lifecycle preserves monotonic progress and requires sustained arrival evidence`() {
        val lifecycle = ActiveRouteLifecycle()
        assertEquals(ActiveRoutePhase.ACTIVE, lifecycle.start(plan).phase)
        assertFails { lifecycle.start(plan) }
        assertEquals(ActiveRoutePhase.ACTIVE, lifecycle.observe(observation(1, 100.0, 100.0, 2.0)).phase)
        assertEquals(ActiveRoutePhase.UNAVAILABLE, lifecycle.observe(observation(2, 90.0, 110.0, 2.0)).phase)
        assertEquals(100.0, lifecycle.state.progress?.totalMetersAlongRoute)
        assertEquals(ActiveRoutePhase.ACTIVE, lifecycle.observe(observation(3, 190.0, 10.0, 2.0)).phase)
        assertEquals(ActiveRoutePhase.ACTIVE, lifecycle.observe(observation(4, 195.0, 5.0, 2.0)).phase)
        assertEquals(ActiveRoutePhase.ARRIVED, lifecycle.observe(observation(5, 200.0, 2.0, 2.0)).phase)
        assertEquals(0.0, lifecycle.state.remainingDistanceMeters)
    }

    @Test fun `low confidence unavailable and sustained off route evidence stay explicit`() {
        val lifecycle = ActiveRouteLifecycle(); lifecycle.start(plan)
        assertEquals(ActiveRoutePhase.LOW_CONFIDENCE, lifecycle.observe(observation(1, 20.0, 180.0, 0.0, PositionConfidence.LOW)).phase)
        assertEquals(ActiveRoutePhase.UNAVAILABLE, lifecycle.observe(ActiveRouteObservation(MonotonicTimestamp(2), null, null, null, null)).phase)
        assertEquals(ActiveRoutePhase.ACTIVE, lifecycle.observe(observation(3, 30.0, 170.0, 50.0)).phase)
        assertEquals(ActiveRoutePhase.OFF_ROUTE, lifecycle.observe(observation(4, 40.0, 160.0, 50.0)).phase)
        assertEquals(ActiveRoutePhase.REROUTE_REQUIRED, lifecycle.observe(observation(5, 50.0, 150.0, 50.0)).phase)
    }

    @Test fun `threshold boundaries and stale observations do not fabricate state`() {
        val lifecycle = ActiveRouteLifecycle(); lifecycle.start(plan)
        assertEquals(ActiveRoutePhase.ACTIVE, lifecycle.observe(observation(2, 180.0, 15.0, 40.0)).phase)
        assertEquals(ActiveRoutePhase.UNAVAILABLE, lifecycle.observe(observation(1, 190.0, 10.0, 0.0)).phase)
        assertEquals(MonotonicTimestamp(2), lifecycle.state.lastTimestamp)
        assertEquals(180.0, lifecycle.state.progress?.totalMetersAlongRoute)
    }

    @Test fun `synthetic replay covers degradation recovery off route and arrival then explicit restart`() {
        val lifecycle = ActiveRouteLifecycle()
        val observations = listOf(
            observation(1, 20.0, 180.0, 2.0),
            observation(2, 20.0, 180.0, 2.0, PositionConfidence.LOW),
            observation(3, 80.0, 120.0, 50.0), observation(4, 90.0, 110.0, 50.0),
            observation(5, 100.0, 100.0, 2.0),
            observation(6, 190.0, 10.0, 2.0), observation(7, 195.0, 5.0, 2.0), observation(8, 200.0, 2.0, 2.0)
        )
        val phases = lifecycle.replay(plan, observations).map { it.phase }
        assertEquals(listOf(ActiveRoutePhase.ACTIVE, ActiveRoutePhase.ACTIVE, ActiveRoutePhase.LOW_CONFIDENCE,
            ActiveRoutePhase.ACTIVE, ActiveRoutePhase.OFF_ROUTE, ActiveRoutePhase.ACTIVE,
            ActiveRoutePhase.ACTIVE, ActiveRoutePhase.ACTIVE, ActiveRoutePhase.ARRIVED), phases)
        assertEquals(ActiveRoutePhase.STOPPED, lifecycle.stop().phase)
        assertNull(lifecycle.state.confirmedPlan)
        assertEquals(ActiveRoutePhase.ACTIVE, lifecycle.start(plan.copy(id = "confirmed-2")).phase)
        assertNull(lifecycle.state.progress)
    }

    private fun observation(time: Long, progress: Double, remaining: Double, offRoute: Double,
                            confidence: PositionConfidence = PositionConfidence.HIGH) = ActiveRouteObservation(
        MonotonicTimestamp(time), NavigationPosition(0.0, progress / 10_000.0, confidence, 5.0, PositionSource.GNSS, MonotonicTimestamp(time)),
        RouteProgress(if (progress < 100.0) 0 else 1, if (progress < 100.0) progress else progress - 100.0, progress),
        offRoute, remaining, "Continue"
    )
}
