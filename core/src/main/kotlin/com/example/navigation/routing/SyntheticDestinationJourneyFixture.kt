package com.example.navigation.routing

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.maps.*
import com.example.navigation.navigation.*
import java.nio.ByteOrder

/** Small deterministic fixture for debug smoke tests; it is not a downloadable map region. */
class SyntheticDestinationJourneyFixture {
    val packageVersion = RegionalPackageVersion("debug-fixture", "debug-map-1", "debug-graph-1")
    val origin = RoutePlanningOrigin(GeoPoint(50.4500, 30.5230), PositionSource.MANUAL_START, PositionConfidence.LOW, 100.0)
    val destinationRecord = DestinationIndexRecord(
        "debug-museum", DestinationSourceType.PLACE, "museum", GeoPoint(50.4500, 30.5250),
        "RENAV Test Museum", listOf("renav test museum", "test museum", "museum")
    )
    val index = (OfflineDestinationSearchIndex.load(
        packageVersion, DestinationIndexMetadata("debug-index-1", packageVersion, OfflineDestinationSearchIndex.checksum(listOf(destinationRecord))),
        listOf(destinationRecord)
    ) as DestinationIndexLoadResult.Ready).index
    val verifiedGraph = VerifiedRoutingGraph(graph(), packageVersion.mapVersion)

    fun observations(): List<ActiveRouteObservation> = listOf(50.0, 190.0, 195.0, 200.0).mapIndexed { index, progress ->
        ActiveRouteObservation(
            timestamp = MonotonicTimestamp((index + 1).toLong()),
            position = NavigationPosition(50.4500, 30.5230 + progress / 100_000.0, PositionConfidence.HIGH, 5.0, PositionSource.GNSS, MonotonicTimestamp((index + 1).toLong())),
            routeProgress = RouteProgress(if (progress < 100.0) 0 else 1, if (progress < 100.0) progress else progress - 100.0, progress),
            distanceToRouteMeters = 2.0,
            distanceToDestinationMeters = 200.0 - progress,
            nextManeuver = if (progress < 100.0) "Continue straight" else "Arrive at destination"
        )
    }

    private fun graph(): OfflineRoutingGraph {
        val nodes = mapOf(
            1 to RoutingNode(1, origin.point),
            2 to RoutingNode(2, GeoPoint(50.4500, 30.5240)),
            3 to RoutingNode(3, destinationRecord.coordinate)
        )
        val edges = mapOf(
            10 to RoutingEdge(10, 1, 2, 100.0, RoutingEdge.VEHICLE_ACCESS or RoutingEdge.PEDESTRIAN_ACCESS),
            11 to RoutingEdge(11, 2, 3, 100.0, RoutingEdge.VEHICLE_ACCESS or RoutingEdge.PEDESTRIAN_ACCESS)
        )
        return OfflineRoutingGraph(
            RoutingGraphManifest(1, packageVersion.regionId, packageVersion.routingGraphVersion, ByteOrder.BIG_ENDIAN, 3, 2),
            nodes, edges, mapOf(1 to listOf(10), 2 to listOf(11)), emptySet(), listOf(10, 11)
        )
    }
}
