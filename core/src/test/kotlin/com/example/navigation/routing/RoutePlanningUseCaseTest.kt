package com.example.navigation.routing

import com.example.navigation.maps.*
import com.example.navigation.navigation.*
import java.nio.ByteOrder
import kotlin.test.*

class RoutePlanningUseCaseTest {
    private val version = RegionalPackageVersion("fixture", "map-1", "graph-1")
    private val origin = RoutePlanningOrigin(GeoPoint(0.0, 0.0), PositionSource.MANUAL_START, PositionConfidence.LOW, 100.0)
    private val destination = ConfirmedDestination("poi", "Museum", GeoPoint(0.0, 0.02), version)

    @Test fun `plans deterministic vehicle and pedestrian candidates without replacing active route`() {
        val graph = graph(access = RoutingEdge.VEHICLE_ACCESS or RoutingEdge.PEDESTRIAN_ACCESS)
        val active = Route("old", listOf(1), emptyList(), 0.0)
        val planner = RoutePlanningUseCase()
        val vehicle = planner.plan(origin, destination, NavigationMode.VEHICLE, VerifiedRoutingGraph(graph, "map-1"), active)
        val pedestrian = planner.plan(origin, destination, NavigationMode.PEDESTRIAN, VerifiedRoutingGraph(graph, "map-1"), active)
        assertEquals(RoutePlanningPhase.ROUTE_READY, vehicle.phase)
        assertEquals(listOf(10, 11), vehicle.candidate?.route?.edgeIds)
        assertEquals(200.0, vehicle.candidate?.route?.totalDistanceMeters)
        assertEquals(PositionSource.MANUAL_START, vehicle.candidate?.origin?.source)
        assertEquals(active, vehicle.preservedActiveRoute)
        assertEquals(vehicle.candidate?.route, planner.plan(origin, destination, NavigationMode.VEHICLE, VerifiedRoutingGraph(graph, "map-1"), active).candidate?.route)
        assertEquals(RoutePlanningPhase.ROUTE_READY, pedestrian.phase)
    }

    @Test fun `reports every planning boundary explicitly`() {
        val planner = RoutePlanningUseCase()
        val graph = graph(RoutingEdge.VEHICLE_ACCESS)
        assertEquals(RoutePlanningPhase.PLANNING, planner.planning().phase)
        assertEquals(RoutePlanningPhase.NO_ORIGIN, planner.plan(null, destination, NavigationMode.VEHICLE, VerifiedRoutingGraph(graph, "map-1")).phase)
        assertEquals(RoutePlanningPhase.GRAPH_UNAVAILABLE, planner.plan(origin, destination, NavigationMode.VEHICLE, null).phase)
        assertEquals(RoutePlanningPhase.FAILED, planner.plan(origin, destination, NavigationMode.UNKNOWN, VerifiedRoutingGraph(graph, "map-1")).phase)
        assertEquals(RoutePlanningPhase.DESTINATION_OUTSIDE_REGION, planner.plan(origin, destination.copy(sourcePackage = version.copy(regionId = "other")), NavigationMode.VEHICLE, VerifiedRoutingGraph(graph, "map-1")).phase)
        assertEquals(RoutePlanningPhase.DESTINATION_OUTSIDE_REGION, planner.plan(origin, destination.copy(point = GeoPoint(10.0, 10.0)), NavigationMode.VEHICLE, VerifiedRoutingGraph(graph, "map-1")).phase)
        assertEquals(RoutePlanningPhase.GRAPH_UNAVAILABLE, planner.plan(origin, destination.copy(sourcePackage = version.copy(mapVersion = "stale")), NavigationMode.VEHICLE, VerifiedRoutingGraph(graph, "map-1")).phase)
        assertEquals(RoutePlanningPhase.NO_ROUTE, planner.plan(origin, destination, NavigationMode.PEDESTRIAN, VerifiedRoutingGraph(graph, "map-1")).phase)
    }

    @Test fun `task 123 search result retains package provenance through planning`() {
        val record = DestinationIndexRecord("poi", DestinationSourceType.PLACE, "museum", destination.point, "Museum", listOf("museum"))
        val records = listOf(record)
        val index = (OfflineDestinationSearchIndex.load(version, DestinationIndexMetadata("index-1", version,
            OfflineDestinationSearchIndex.checksum(records)), records) as DestinationIndexLoadResult.Ready).index
        val match = (index.search("museum") as DestinationSearchResult.Results).matches.single()
        val confirmed = ConfirmedDestination(match.stableId, match.displayLabel, match.coordinate, version)
        val result = RoutePlanningUseCase().plan(origin, confirmed, NavigationMode.VEHICLE, VerifiedRoutingGraph(graph(RoutingEdge.VEHICLE_ACCESS), "map-1"))
        assertEquals(RoutePlanningPhase.ROUTE_READY, result.phase)
        assertEquals(version, result.candidate?.packageVersion)
    }

    private fun graph(access: Int): OfflineRoutingGraph {
        val nodes = mapOf(1 to RoutingNode(1, GeoPoint(0.0, 0.0)), 2 to RoutingNode(2, GeoPoint(0.0, 0.01)), 3 to RoutingNode(3, GeoPoint(0.0, 0.02)))
        val edges = mapOf(10 to RoutingEdge(10, 1, 2, 100.0, access), 11 to RoutingEdge(11, 2, 3, 100.0, access))
        return OfflineRoutingGraph(RoutingGraphManifest(1, "fixture", "graph-1", ByteOrder.BIG_ENDIAN, 3, 2), nodes, edges,
            mapOf(1 to listOf(10), 2 to listOf(11)), emptySet(), listOf(10, 11))
    }
}
