package com.example.navigation.app

import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmedRouteRecoveryPolicyTest {
    private val packageVersion = RegionalPackageVersion("ua-kharkiv", "map-1", "graph-1")
    private val policy = ConfirmedRouteRecoveryPolicy(maximumCheckpointAgeMillis = 1_000L)

    @Test fun `compatible checkpoint retains route metadata but requires fresh observations`() {
        val decision = policy.evaluate(checkpoint(), packageVersion, 10_500L)
        assertEquals(ConfirmedRouteRecoveryDisposition.RESUME_REQUIRES_FRESH_OBSERVATIONS, decision.disposition)
        assertEquals(1_500L, decision.routeAgeMillis)
        assertEquals(500L, decision.checkpointAgeMillis)
        assertEquals("route-7", decision.checkpoint?.confirmedRouteId)
        assertTrue(decision.requiresFreshLocation)
        assertTrue(decision.requiresFreshSensor)
        assertFalse(decision.resumesGuidance)
    }

    @Test fun `stale missing and incompatible packages remain review only`() {
        assertEquals(ConfirmedRouteRecoveryDisposition.REVIEW_ONLY_STALE, policy.evaluate(checkpoint(), packageVersion, 12_000L).disposition)
        assertEquals(ConfirmedRouteRecoveryDisposition.REVIEW_ONLY_MISSING_PACKAGE, policy.evaluate(checkpoint(), null, 10_500L).disposition)
        assertEquals(ConfirmedRouteRecoveryDisposition.REVIEW_ONLY_INCOMPATIBLE_PACKAGE,
            policy.evaluate(checkpoint(), packageVersion.copy(routingGraphVersion = "graph-2"), 10_500L).disposition)
    }

    @Test fun `low confidence checkpoint remains review only`() {
        assertEquals(ConfirmedRouteRecoveryDisposition.REVIEW_ONLY_LOW_CONFIDENCE,
            policy.evaluate(checkpoint(lastConfidence = PositionConfidence.LOW), packageVersion, 10_500L).disposition)
    }

    @Test fun `fresh observation gate cannot activate until both post restart inputs arrive`() {
        val gate = RouteRecoveryFreshObservationGate()
        assertFalse(gate.mayActivateGuidance())
        gate.observeLocation()
        assertFalse(gate.mayActivateGuidance())
        gate.observeSensor()
        assertTrue(gate.mayActivateGuidance())
    }

    @Test fun `checkpoint constructor is a minimal storage round trip fixture`() {
        val restored = checkpoint().copy()
        assertEquals(checkpoint(), restored)
        assertEquals(1_000L, restored.routeAgeMillis)
        assertEquals(PositionSource.HYBRID, restored.lastSource)
    }

    @Test fun `discarded decision is terminal and contains no persisted route`() {
        val decision = policy.discarded()
        assertEquals(ConfirmedRouteRecoveryDisposition.DISCARDED, decision.disposition)
        assertEquals(null, decision.checkpoint)
    }

    private fun checkpoint(lastConfidence: PositionConfidence? = PositionConfidence.MEDIUM) = ConfirmedRouteRecoveryCheckpoint(
        confirmedRouteId = "route-7", destinationStableId = "destination-1", destinationLabel = "Fixture destination",
        destination = GeoPoint(50.0, 36.0), navigationMode = NavigationMode.PEDESTRIAN, packageVersion = packageVersion,
        routeStartedWallClockMillis = 9_000L, checkpointWallClockMillis = 10_000L,
        lastConfidence = lastConfidence, lastSource = PositionSource.HYBRID
    )
}
