package com.example.navigation.routing

import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.navigation.NavigationMode

/**
 * Plans only pedestrian routes through the embedded BRouter adapter.  It accepts a package only
 * after the package runtime has verified it; this use case neither installs packages nor falls
 * back to the legacy routing graph.
 */
class PedestrianBrouterRoutePlanningUseCase(
    private val route: (BrouterRouteRequest) -> BrouterRouteResult = EmbeddedBrouterRoutingAdapter()::calculate
) {
    fun plan(
        origin: RoutePlanningOrigin?,
        destination: ConfirmedDestination,
        packageResult: BrouterPackageVerificationResult,
        activeRoute: Route? = null
    ): RoutePlanningState {
        fun unavailable(reason: String) = RoutePlanningState(
            RoutePlanningPhase.GRAPH_UNAVAILABLE,
            preservedActiveRoute = activeRoute,
            reason = reason
        )
        if (origin == null) return RoutePlanningState(
            RoutePlanningPhase.NO_ORIGIN,
            preservedActiveRoute = activeRoute,
            reason = "A reliable or manually selected pedestrian origin is required"
        )
        val verified = when (packageResult) {
            is BrouterPackageVerificationResult.Ready -> packageResult.value
            is BrouterPackageVerificationResult.Unavailable -> return unavailable(packageResult.reason)
        }
        if (destination.sourcePackage.regionId != verified.regionId) {
            return RoutePlanningState(RoutePlanningPhase.DESTINATION_OUTSIDE_REGION, preservedActiveRoute = activeRoute,
                reason = "Destination is outside the verified pedestrian routing region")
        }
        if (destination.sourcePackage.mapVersion != verified.mapCompatibilityVersion) {
            return unavailable("Destination index and verified pedestrian routing package map versions do not match")
        }
        return when (val result = route(BrouterRouteRequest(origin.point, destination.point, PEDESTRIAN_PROFILE_ID, verified))) {
            is BrouterRouteResult.Ready -> RoutePlanningState(
                phase = RoutePlanningPhase.ROUTE_READY,
                candidate = RoutePlanCandidate(
                    route = result.route.toRoute(),
                    origin = origin,
                    destination = destination,
                    mode = NavigationMode.PEDESTRIAN,
                    packageVersion = RegionalPackageVersion(verified.regionId, verified.mapCompatibilityVersion, verified.packageVersion),
                    estimatedDurationSeconds = result.route.estimatedDurationSeconds.toLong()
                ),
                preservedActiveRoute = activeRoute
            )
            is BrouterRouteResult.NoRoute -> RoutePlanningState(RoutePlanningPhase.NO_ROUTE, preservedActiveRoute = activeRoute, reason = result.reason)
            is BrouterRouteResult.Unavailable -> unavailable(result.reason)
        }
    }

    fun planning(activeRoute: Route? = null) = RoutePlanningState(RoutePlanningPhase.PLANNING, preservedActiveRoute = activeRoute)

    private fun BrouterRoute.toRoute() = Route(
        regionId = packageRegionId,
        nodeIds = listOf(0, 1),
        edgeIds = listOf(0),
        totalDistanceMeters = distanceMeters.toDouble(),
        segments = listOf(RouteSegment(geometry, distanceMeters.toDouble(), null, null, null, RouteAccessType.PEDESTRIAN))
    )

    private companion object {
        const val PEDESTRIAN_PROFILE_ID = "renav-pedestrian"
    }
}
