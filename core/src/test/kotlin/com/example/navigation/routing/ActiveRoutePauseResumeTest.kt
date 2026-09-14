package com.example.navigation.routing

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class ActiveRoutePauseResumeTest {
    private val plan = ConfirmedRoutePlan("confirmed-pause", RoutePlanCandidate(
        route = Route("pause-fixture", listOf(1, 2), listOf(10), 100.0, listOf(
            RouteSegment(listOf(GeoPoint(50.0, 36.0), GeoPoint(50.0, 36.01)), 100.0, 90.0, 10, RouteTurnInformation.ARRIVAL, RouteAccessType.PEDESTRIAN)
        )),
        origin = RoutePlanningOrigin(GeoPoint(50.0, 36.0), PositionSource.GNSS, PositionConfidence.HIGH, 5.0),
        destination = ConfirmedDestination("destination", "Fixture destination", GeoPoint(50.0, 36.01), RegionalPackageVersion("fixture", "map-1", "graph-1")),
        mode = NavigationMode.PEDESTRIAN,
        packageVersion = RegionalPackageVersion("fixture", "map-1", "graph-1")
    ))

    @Test fun `pause retains confirmed route and blocks progress until fresh resume evidence`() {
        val lifecycle = ActiveRouteLifecycle()
        lifecycle.start(plan)
        val active = lifecycle.observe(observation(1, 20.0))

        assertEquals(ActiveRoutePhase.PAUSE_REQUESTED, lifecycle.requestPause().phase)
        val paused = lifecycle.confirmPause()
        assertEquals(ActiveRoutePhase.PAUSED, paused.phase)
        assertEquals(true, paused.guidancePresentationPaused)
        assertFalse(paused.guidanceOutputActive)
        assertSame(plan, paused.confirmedPlan)
        assertEquals(active.progress, paused.progress)
        assertSame(paused, lifecycle.observe(observation(2, 40.0)))

        assertEquals(ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS, lifecycle.requestResume().phase)
        assertFailsWith<IllegalArgumentException> { lifecycle.observeFreshLocation(MonotonicTimestamp(1)) }
        lifecycle.observeFreshLocation(MonotonicTimestamp(3))
        assertEquals(ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS, lifecycle.resumeWhenReady().phase)
        lifecycle.observeFreshSensor(MonotonicTimestamp(4))
        assertEquals(ActiveRoutePhase.RESUMED, lifecycle.resumeWhenReady().phase)
        assertEquals(true, lifecycle.state.guidanceOutputActive)
        assertEquals(ActiveRoutePhase.ACTIVE, lifecycle.observe(observation(5, 40.0)).phase)
    }

    @Test fun `invalid transitions and terminal states remain distinct`() {
        val lifecycle = ActiveRouteLifecycle()
        assertFailsWith<IllegalArgumentException> { lifecycle.requestPause() }
        lifecycle.start(plan)
        lifecycle.requestPause()
        assertFailsWith<IllegalArgumentException> { lifecycle.requestResume() }
        lifecycle.confirmPause()
        assertFailsWith<IllegalArgumentException> { lifecycle.confirmPause() }

        assertEquals(ActiveRoutePhase.CANCELLED, lifecycle.cancel().phase)
        assertEquals(plan, lifecycle.state.confirmedPlan)
        assertFailsWith<IllegalArgumentException> { lifecycle.cancel() }
        assertFailsWith<IllegalArgumentException> { lifecycle.requestResume() }
    }

    @Test fun `post interruption resume gate never retains old inertial state`() {
        val interrupted = ActiveRouteLifecycle()
        val restored = interrupted.resumeAfterInterruption(plan)
        assertEquals(ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS, restored.phase)
        assertEquals(plan, restored.confirmedPlan)
        assertEquals(null, restored.progress)
        assertEquals(null, restored.lastTimestamp)
        assertEquals(ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS, interrupted.resumeWhenReady().phase)
        interrupted.observeFreshLocation(MonotonicTimestamp(10))
        interrupted.observeFreshSensor(MonotonicTimestamp(11))
        assertEquals(ActiveRoutePhase.RESUMED, interrupted.resumeWhenReady().phase)
    }

    @Test fun `arrival acknowledgement is the only arrival cleanup path`() {
        val lifecycle = ActiveRouteLifecycle()
        lifecycle.start(plan)
        assertFailsWith<IllegalArgumentException> { lifecycle.acknowledgeArrival() }
        lifecycle.observe(observation(1, 90.0))
        lifecycle.observe(observation(2, 95.0))
        lifecycle.observe(observation(3, 100.0))
        assertEquals(ActiveRoutePhase.ARRIVED, lifecycle.state.phase)
        assertEquals(ActiveRoutePhase.STOPPED, lifecycle.acknowledgeArrival().phase)
    }

    private fun observation(timestamp: Long, progress: Double) = ActiveRouteObservation(
        timestamp = MonotonicTimestamp(timestamp),
        position = NavigationPosition(50.0, 36.0, PositionConfidence.HIGH, 5.0, PositionSource.GNSS, MonotonicTimestamp(timestamp)),
        routeProgress = RouteProgress(0, progress, progress),
        distanceToRouteMeters = 2.0,
        distanceToDestinationMeters = 100.0 - progress
    )
}
