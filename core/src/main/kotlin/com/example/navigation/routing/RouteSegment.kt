package com.example.navigation.routing

enum class RouteAccessType {
    VEHICLE,
    PEDESTRIAN,
    BOTH,
    RESTRICTED
}

enum class RouteTurnInformation {
    START,
    STRAIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    LEFT,
    RIGHT,
    U_TURN,
    ARRIVAL
}

/** One ordered piece of a calculated route. Geometry is ordered from segment start to segment end. */
data class RouteSegment(
    val geometry: List<GeoPoint>,
    val lengthMeters: Double,
    val headingDegrees: Double?,
    val roadPathId: Long?,
    val turnInformation: RouteTurnInformation?,
    val accessType: RouteAccessType
) {
    init {
        require(geometry.size >= 2) { "A route segment needs at least two geometry points" }
        require(lengthMeters >= 0.0) { "Segment length cannot be negative" }
        require(headingDegrees == null || headingDegrees in 0.0..<360.0) { "Segment heading must be in [0, 360)" }
    }
}
