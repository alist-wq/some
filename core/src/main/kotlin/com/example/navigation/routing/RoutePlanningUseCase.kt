package com.example.navigation.routing

import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource

enum class RoutePlanningPhase { IDLE, PLANNING, ROUTE_READY, NO_ORIGIN, GRAPH_UNAVAILABLE, DESTINATION_OUTSIDE_REGION, NO_ROUTE, FAILED }

data class RoutePlanningOrigin(
    val point: GeoPoint,
    val source: PositionSource,
    val confidence: PositionConfidence,
    val estimatedAccuracyMeters: Double
) { init { require(estimatedAccuracyMeters.isFinite() && estimatedAccuracyMeters >= 0.0) } }

data class ConfirmedDestination(
    val stableId: String,
    val label: String,
    val point: GeoPoint,
    val sourcePackage: RegionalPackageVersion
) { init { require(stableId.isNotBlank() && label.isNotBlank()) } }

data class VerifiedRoutingGraph(
    val graph: OfflineRoutingGraph,
    val mapVersion: String
) { init { require(mapVersion.isNotBlank()) } }

data class RoutePlanCandidate(
    val route: Route,
    val origin: RoutePlanningOrigin,
    val destination: ConfirmedDestination,
    val mode: NavigationMode,
    val packageVersion: RegionalPackageVersion,
    val estimatedDurationSeconds: Long? = null
)

data class RoutePlanningState(
    val phase: RoutePlanningPhase = RoutePlanningPhase.IDLE,
    val candidate: RoutePlanCandidate? = null,
    val preservedActiveRoute: Route? = null,
    val reason: String? = null
)

data class RoutePlanningConfig(val maximumDestinationSnapDistanceMeters: Double = 5_000.0) {
    init { require(maximumDestinationSnapDistanceMeters.isFinite() && maximumDestinationSnapDistanceMeters > 0.0) }
}

/** Plans locally against one verified graph; candidates never activate or replace routes. */
class RoutePlanningUseCase(private val config: RoutePlanningConfig = RoutePlanningConfig()) {
    fun plan(
        origin: RoutePlanningOrigin?,
        destination: ConfirmedDestination,
        mode: NavigationMode,
        verifiedGraph: VerifiedRoutingGraph?,
        activeRoute: Route? = null
    ): RoutePlanningState {
        fun state(phase: RoutePlanningPhase, reason: String) = RoutePlanningState(phase, preservedActiveRoute = activeRoute, reason = reason)
        if (origin == null) return state(RoutePlanningPhase.NO_ORIGIN, "A trusted or manually selected origin is required")
        if (mode == NavigationMode.UNKNOWN) return state(RoutePlanningPhase.FAILED, "Navigation mode must be selected explicitly")
        if (verifiedGraph == null) return state(RoutePlanningPhase.GRAPH_UNAVAILABLE, "A verified regional routing graph is required")
        val manifest = verifiedGraph.graph.manifest
        if (destination.sourcePackage.regionId != manifest.regionId) {
            return state(RoutePlanningPhase.DESTINATION_OUTSIDE_REGION, "Destination is outside the loaded routing region")
        }
        if (destination.sourcePackage.mapVersion != verifiedGraph.mapVersion ||
            destination.sourcePackage.routingGraphVersion != manifest.routingGraphVersion) {
            return state(RoutePlanningPhase.GRAPH_UNAVAILABLE, "Destination index and routing graph versions do not match")
        }
        val nearestDestination = verifiedGraph.graph.nearestNode(destination.point)
        if (verifiedGraph.graph.distanceMeters(verifiedGraph.graph.nodes.getValue(nearestDestination).point, destination.point) >
            config.maximumDestinationSnapDistanceMeters) {
            return state(RoutePlanningPhase.DESTINATION_OUTSIDE_REGION, "Destination is beyond the loaded routing graph")
        }
        return try {
            val request = RouteRequest(origin.point, destination.point, mode)
            val route = when (mode) {
                NavigationMode.VEHICLE -> VehicleRouteCalculator(verifiedGraph.graph).calculate(request)
                NavigationMode.PEDESTRIAN -> PedestrianRouteCalculator(verifiedGraph.graph).calculate(request)
                NavigationMode.UNKNOWN -> error("Handled above")
            }
            RoutePlanningState(
                phase = RoutePlanningPhase.ROUTE_READY,
                candidate = RoutePlanCandidate(route, origin, destination, mode, destination.sourcePackage),
                preservedActiveRoute = activeRoute
            )
        } catch (error: RouteCalculationException) {
            state(RoutePlanningPhase.NO_ROUTE, error.message ?: "No route is available")
        } catch (error: RuntimeException) {
            state(RoutePlanningPhase.FAILED, error.message ?: "Route planning failed")
        }
    }

    fun planning(activeRoute: Route? = null) = RoutePlanningState(RoutePlanningPhase.PLANNING, preservedActiveRoute = activeRoute)
}
