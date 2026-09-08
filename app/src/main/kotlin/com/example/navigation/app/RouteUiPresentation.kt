package com.example.navigation.app

import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.routing.ActiveRoutePhase
import com.example.navigation.routing.ActiveRouteState
import com.example.navigation.routing.GeoPoint
import com.example.navigation.routing.RoutePlanningPhase
import com.example.navigation.routing.RoutePlanningState

data class RoutePreviewPresentation(
    val visible: Boolean,
    val label: String,
    val detail: String,
    val routeGeometry: List<GeoPoint>,
    val destination: GeoPoint?,
    val canStartNavigation: Boolean,
    val activeRoutePreserved: Boolean
)

object RoutePreviewPresentationFactory {
    fun create(state: RoutePlanningState): RoutePreviewPresentation {
        val candidate = state.candidate
        val label = when (state.phase) {
            RoutePlanningPhase.IDLE -> ""
            RoutePlanningPhase.PLANNING -> "PLANNING ROUTE"
            RoutePlanningPhase.ROUTE_READY -> "ROUTE PREVIEW"
            RoutePlanningPhase.NO_ORIGIN -> "ROUTE NEEDS ORIGIN"
            RoutePlanningPhase.GRAPH_UNAVAILABLE -> "ROUTING GRAPH UNAVAILABLE"
            RoutePlanningPhase.DESTINATION_OUTSIDE_REGION -> "DESTINATION OUTSIDE REGION"
            RoutePlanningPhase.NO_ROUTE -> "NO ROUTE AVAILABLE"
            RoutePlanningPhase.FAILED -> "ROUTE PLANNING FAILED"
        }
        val detail = when (state.phase) {
            RoutePlanningPhase.ROUTE_READY -> candidate?.let {
                "${"%.0f".format(java.util.Locale.US, it.route.totalDistanceMeters)} m • ${it.mode.name.lowercase().replaceFirstChar(Char::uppercaseChar)} • ${it.packageVersion.regionId}"
            } ?: "Route candidate is unavailable"
            else -> state.reason ?: ""
        }
        return RoutePreviewPresentation(
            visible = state.phase != RoutePlanningPhase.IDLE,
            label = label,
            detail = detail,
            routeGeometry = candidate?.route?.segments.orEmpty().flatMap { it.geometry }.deduplicateAdjacent(),
            destination = candidate?.destination?.point,
            canStartNavigation = state.phase == RoutePlanningPhase.ROUTE_READY && candidate != null,
            activeRoutePreserved = state.preservedActiveRoute != null
        )
    }

    private fun List<GeoPoint>.deduplicateAdjacent() = filterIndexed { index, point -> index == 0 || point != this[index - 1] }
}

data class ActiveRouteGuidancePresentation(
    val visible: Boolean,
    val label: String,
    val detail: String,
    val maneuver: String?,
    val remainingDistanceLabel: String?,
    val progress: Float?,
    val sourceLabel: String?,
    val confidenceLabel: String?,
    val accuracyLabel: String?,
    val canStop: Boolean,
    val canChooseNewDestination: Boolean
)

object ActiveRouteGuidancePresentationFactory {
    fun create(state: ActiveRouteState): ActiveRouteGuidancePresentation {
        val hasRoute = state.confirmedPlan != null
        if (!hasRoute) return ActiveRouteGuidancePresentation(false, "", "", null, null, null, null, null, null, false, false)
        val phase = state.phase
        val confidence = state.confidence
        val sufficient = confidence in setOf(PositionConfidence.HIGH, PositionConfidence.MEDIUM) && phase == ActiveRoutePhase.ACTIVE
        val routeDistance = state.confirmedPlan!!.candidate.route.totalDistanceMeters
        val progress = state.progress?.totalMetersAlongRoute?.let { (it / routeDistance).toFloat().coerceIn(0f, 1f) }
        return ActiveRouteGuidancePresentation(
            visible = true,
            label = when (phase) {
                ActiveRoutePhase.ACTIVE -> if (sufficient) "NAVIGATION ACTIVE" else "GUIDANCE AWAITING RELIABLE POSITION"
                ActiveRoutePhase.LOW_CONFIDENCE -> "POSITION CONFIDENCE LOW"
                ActiveRoutePhase.OFF_ROUTE -> "OFF ROUTE"
                ActiveRoutePhase.REROUTE_REQUIRED -> "REROUTE REQUIRED"
                ActiveRoutePhase.ARRIVED -> "ARRIVED"
                ActiveRoutePhase.UNAVAILABLE -> "NAVIGATION POSITION UNAVAILABLE"
                ActiveRoutePhase.STOPPED -> "NAVIGATION STOPPED"
            },
            detail = when (phase) {
                ActiveRoutePhase.ARRIVED -> "You have reached the confirmed destination."
                else -> state.reason ?: "Position source and confidence remain visible."
            },
            maneuver = state.nextManeuver?.takeIf { sufficient },
            remainingDistanceLabel = state.remainingDistanceMeters?.takeIf { sufficient }?.let(::distanceLabel),
            progress = progress,
            sourceLabel = state.confirmedPlan!!.candidate.origin.source.name,
            confidenceLabel = confidence?.name,
            accuracyLabel = state.estimatedAccuracyMeters?.let { "±${"%.0f".format(java.util.Locale.US, it)} m" },
            canStop = phase != ActiveRoutePhase.STOPPED,
            canChooseNewDestination = phase in setOf(ActiveRoutePhase.ARRIVED, ActiveRoutePhase.STOPPED, ActiveRoutePhase.REROUTE_REQUIRED)
        )
    }

    private fun distanceLabel(distanceMeters: Double) = if (distanceMeters >= 1_000.0) {
        "${"%.1f".format(java.util.Locale.US, distanceMeters / 1_000.0)} km"
    } else "${distanceMeters.toInt()} m"
}
