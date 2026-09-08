package com.example.navigation.app

import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.routing.GeoPoint
import com.example.navigation.routing.Route

/** Immutable map input; absent values remain absent rather than being inferred from stale UI data. */
data class NavigationMapState(
    val route: Route? = null,
    val estimatedPosition: NavigationPosition? = null,
    val movementHeadingDegrees: Double? = null,
    val destination: GeoPoint? = null,
    val gnssTrustState: GnssTrustState = GnssTrustState.TRUSTED,
    val guidance: NavigationGuidanceState = NavigationGuidanceState(),
    val diagnostics: NavigationDiagnostics = NavigationDiagnostics()
) {
    init { require(movementHeadingDegrees == null || movementHeadingDegrees in 0.0..<360.0) }
}

data class NavigationMapPresentation(
    val routeGeometry: List<GeoPoint>,
    val estimatedPosition: GeoPoint?,
    val directionEnd: GeoPoint?,
    val destination: GeoPoint?,
    val uncertaintyCenter: GeoPoint?,
    val uncertaintyRadiusMeters: Double?,
    val showPrecisePositionMarker: Boolean
)

/** Maps navigation state to honest visual primitives without assigning a location when none is known. */
object NavigationMapPresentationFactory {
    fun create(state: NavigationMapState): NavigationMapPresentation {
        val route = state.route?.segments.orEmpty().flatMap { it.geometry }.deduplicateAdjacent()
        val position = state.estimatedPosition?.let { GeoPoint(it.latitude, it.longitude) }
        val directionEnd = position?.let { point -> state.movementHeadingDegrees?.let { heading -> project(point, heading, 25.0) } }
        val destination = state.destination ?: route.lastOrNull()
        val accuracy = state.estimatedPosition?.estimatedAccuracyMeters
            ?.takeIf { it.isFinite() && it >= 0.0 }
        val showPreciseMarker = state.estimatedPosition?.confidence !in setOf(PositionConfidence.LOW, PositionConfidence.LOST)
        return NavigationMapPresentation(route, position, directionEnd, destination, position, accuracy, showPreciseMarker)
    }

    private fun List<GeoPoint>.deduplicateAdjacent() = filterIndexed { index, point -> index == 0 || point != this[index - 1] }

    private fun project(start: GeoPoint, headingDegrees: Double, distanceMeters: Double): GeoPoint {
        val angularDistance = distanceMeters / EARTH_RADIUS_METERS
        val heading = Math.toRadians(headingDegrees)
        val latitude = Math.toRadians(start.latitude)
        val longitude = Math.toRadians(start.longitude)
        val targetLatitude = kotlin.math.asin(kotlin.math.sin(latitude) * kotlin.math.cos(angularDistance) +
            kotlin.math.cos(latitude) * kotlin.math.sin(angularDistance) * kotlin.math.cos(heading))
        val targetLongitude = longitude + kotlin.math.atan2(
            kotlin.math.sin(heading) * kotlin.math.sin(angularDistance) * kotlin.math.cos(latitude),
            kotlin.math.cos(angularDistance) - kotlin.math.sin(latitude) * kotlin.math.sin(targetLatitude)
        )
        return GeoPoint(Math.toDegrees(targetLatitude), ((Math.toDegrees(targetLongitude) + 540.0) % 360.0) - 180.0)
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
