package com.example.navigation.routing

/** A trusted location expressed along the ordered planned route. */
data class TrustedRoutePosition(
    val segmentIndex: Int,
    val metersAlongSegment: Double,
    val totalMetersAlongRoute: Double
)

/** Current estimated location expressed along the ordered planned route. */
data class RouteProgress(
    val segmentIndex: Int,
    val metersAlongSegment: Double,
    val totalMetersAlongRoute: Double
)

/**
 * Propagates route progress from cumulative vehicle distance after an explicit trusted-route
 * initialization. Progress is constrained to the planned route; it does not perform map matching.
 */
class RouteProgressEstimator(private val route: Route) {
    private val segmentLengths = route.segments.map { it.lengthMeters }
    private val routeLengthMeters = segmentLengths.sum()
    private var initialVehicleDistanceMeters: Double? = null
    private var initialRouteMeters: Double? = null

    init { require(route.segments.isNotEmpty()) { "Route progress requires ordered route segments" } }

    fun initialize(trustedPosition: TrustedRoutePosition, vehicleDistanceMeters: Double): RouteProgress {
        require(vehicleDistanceMeters.isFinite() && vehicleDistanceMeters >= 0.0) { "Vehicle distance must be non-negative" }
        require(trustedPosition.segmentIndex in route.segments.indices) { "Trusted route segment is invalid" }
        require(trustedPosition.metersAlongSegment in 0.0..segmentLengths[trustedPosition.segmentIndex]) {
            "Trusted distance along segment is invalid"
        }
        require(trustedPosition.totalMetersAlongRoute in 0.0..routeLengthMeters) { "Trusted route distance is invalid" }
        val expectedTotal = segmentLengths.take(trustedPosition.segmentIndex).sum() + trustedPosition.metersAlongSegment
        require(kotlin.math.abs(expectedTotal - trustedPosition.totalMetersAlongRoute) <= POSITION_TOLERANCE_METERS) {
            "Trusted route position is internally inconsistent"
        }
        initialVehicleDistanceMeters = vehicleDistanceMeters
        initialRouteMeters = trustedPosition.totalMetersAlongRoute
        return progressAt(trustedPosition.totalMetersAlongRoute)
    }

    fun estimate(vehicleDistanceMeters: Double): RouteProgress {
        val baselineVehicleDistance = initialVehicleDistanceMeters ?: error("Route progress is not initialized")
        val baselineRouteMeters = initialRouteMeters ?: error("Route progress is not initialized")
        require(vehicleDistanceMeters.isFinite() && vehicleDistanceMeters >= baselineVehicleDistance) {
            "Vehicle distance must not move backwards"
        }
        return progressAt((baselineRouteMeters + vehicleDistanceMeters - baselineVehicleDistance).coerceAtMost(routeLengthMeters))
    }

    private fun progressAt(totalMeters: Double): RouteProgress {
        var remaining = totalMeters
        segmentLengths.forEachIndexed { index, length ->
            if (remaining < length || index == segmentLengths.lastIndex) {
                return RouteProgress(index, remaining.coerceIn(0.0, length), totalMeters)
            }
            remaining -= length
        }
        error("Route has no segments")
    }

    private companion object { const val POSITION_TOLERANCE_METERS = 0.01 }
}
