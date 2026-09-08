package com.example.navigation.routing

import com.example.navigation.navigation.NavigationMode
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals

class PedestrianRouteCalculatorTest {
    @Test
    fun pedestrianIgnoresVehicleOnlyRestrictionAndVehicleIgnoresPedestrianOnlyRestriction() {
        val graph = profileGraph()
        val pedestrian = PedestrianRouteCalculator(graph).calculate(request(NavigationMode.PEDESTRIAN))
        val vehicle = VehicleRouteCalculator(graph).calculate(request(NavigationMode.VEHICLE))

        assertEquals(listOf(10, 11), pedestrian.edgeIds)
        assertEquals(listOf(10, 14, 15), vehicle.edgeIds)
        assertEquals(RouteAccessType.BOTH, pedestrian.segments.first().accessType)
    }

    private fun request(mode: NavigationMode) = RouteRequest(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.02), mode)

    private fun profileGraph(): OfflineRoutingGraph {
        val nodes = mapOf(
            1 to RoutingNode(1, GeoPoint(0.0, 0.0)), 2 to RoutingNode(2, GeoPoint(0.0, 0.01)),
            3 to RoutingNode(3, GeoPoint(0.0, 0.02)), 4 to RoutingNode(4, GeoPoint(0.01, 0.01)),
            5 to RoutingNode(5, GeoPoint(-0.01, 0.01))
        )
        val edges = mapOf(
            10 to RoutingEdge(10, 1, 2, 1.0, 3), 11 to RoutingEdge(11, 2, 3, 1.0, 3),
            12 to RoutingEdge(12, 2, 4, 5.0, 1), 13 to RoutingEdge(13, 4, 3, 5.0, 1),
            14 to RoutingEdge(14, 2, 5, 1.0, 3), 15 to RoutingEdge(15, 5, 3, 1.0, 3)
        )
        return OfflineRoutingGraph(
            RoutingGraphManifest(1, "profile-fixture", "v1", ByteOrder.BIG_ENDIAN, nodes.size, edges.size),
            nodes, edges, mapOf(1 to listOf(10), 2 to listOf(11, 12, 14), 4 to listOf(13), 5 to listOf(15)),
            setOf(TurnRestriction(10, 11, setOf(NavigationMode.VEHICLE)), TurnRestriction(10, 14, setOf(NavigationMode.PEDESTRIAN))),
            edges.keys.toList()
        )
    }
}
