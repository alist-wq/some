package com.example.navigation.routing

import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PedestrianBrouterRoutePlanningUseCaseTest {
    @Test fun `creates a pedestrian candidate attributed to the verified BRouter package`() {
        val planner = PedestrianBrouterRoutePlanningUseCase { BrouterRouteResult.Ready(route()) }
        val result = planner.plan(origin(), destination(), BrouterPackageVerificationResult.Ready(packageInfo()))

        assertEquals(RoutePlanningPhase.ROUTE_READY, result.phase)
        assertEquals(NavigationMode.PEDESTRIAN, result.candidate?.mode)
        assertEquals("package-2", result.candidate?.packageVersion?.routingGraphVersion)
        assertEquals(RouteAccessType.PEDESTRIAN, result.candidate?.route?.segments?.single()?.accessType)
        assertEquals(240L, result.candidate?.estimatedDurationSeconds)
    }

    @Test fun `does not call BRouter when the verified package is unavailable`() {
        var called = false
        val result = PedestrianBrouterRoutePlanningUseCase { called = true; error("must not route") }
            .plan(origin(), destination(), BrouterPackageVerificationResult.Unavailable("Package signer is not trusted"))

        assertEquals(RoutePlanningPhase.GRAPH_UNAVAILABLE, result.phase)
        assertEquals("Package signer is not trusted", result.reason)
        assertTrue(!called)
    }

    @Test fun `rejects mismatched destination provenance before routing`() {
        var called = false
        val mismatched = destination().copy(sourcePackage = RegionalPackageVersion("other-region", "map-1", "index-1"))
        val result = PedestrianBrouterRoutePlanningUseCase { called = true; error("must not route") }
            .plan(origin(), mismatched, BrouterPackageVerificationResult.Ready(packageInfo()))

        assertEquals(RoutePlanningPhase.DESTINATION_OUTSIDE_REGION, result.phase)
        assertTrue(!called)
    }

    @Test fun `keeps the active route when BRouter returns no route`() {
        val active = Route("region", listOf(0, 1), listOf(0), 10.0)
        val result = PedestrianBrouterRoutePlanningUseCase { BrouterRouteResult.NoRoute("No pedestrian path") }
            .plan(origin(), destination(), BrouterPackageVerificationResult.Ready(packageInfo()), active)

        assertEquals(RoutePlanningPhase.NO_ROUTE, result.phase)
        assertEquals(active, result.preservedActiveRoute)
        assertNull(result.candidate)
    }

    private fun origin() = RoutePlanningOrigin(GeoPoint(50.0, 36.0), PositionSource.MANUAL_START, PositionConfidence.LOW, 100.0)
    private fun destination() = ConfirmedDestination("place-1", "Place", GeoPoint(50.001, 36.002), RegionalPackageVersion("ua-kharkiv-pedestrian", "map-1", "index-1"))
    private fun packageInfo(): VerifiedBrouterPackage {
        val root = Files.createTempDirectory("pedestrian-planner")
        return VerifiedBrouterPackage("ua-kharkiv-pedestrian", "package-2", "map-1", "core", root,
            root.resolve("renav-pedestrian.brf"), "profile-1", root.resolve("lookups.dat"), emptyList())
    }
    private fun route() = BrouterRoute("route-1", "ua-kharkiv-pedestrian", "package-2", "renav-pedestrian", "profile-1", "checksum",
        listOf(GeoPoint(50.0, 36.0), GeoPoint(50.001, 36.002)), 320, 240)
}
