package com.example.navigation.app

/** Explicit navigation states drive guidance UI; they are not inferred from map rendering. */
enum class NavigationGuidancePhase {
    NO_ROUTE,
    ROUTE_READY,
    NAVIGATING,
    REROUTING,
    ARRIVED
}

enum class RouteGuidanceConfidence {
    SUFFICIENT,
    INSUFFICIENT
}

data class NavigationGuidanceState(
    val phase: NavigationGuidancePhase = NavigationGuidancePhase.NO_ROUTE,
    val routeConfidence: RouteGuidanceConfidence = RouteGuidanceConfidence.INSUFFICIENT,
    val nextManeuver: String? = null,
    val distanceToManeuverMeters: Double? = null,
    val routeProgress: Float? = null
) {
    init {
        require(distanceToManeuverMeters == null ||
            (distanceToManeuverMeters.isFinite() && distanceToManeuverMeters >= 0.0))
        require(routeProgress == null || (routeProgress.isFinite() && routeProgress in 0f..1f))
    }
}

data class NavigationGuidancePresentation(
    val visible: Boolean,
    val stateLabel: String,
    val maneuverLabel: String?,
    val turnPreview: String?,
    val distanceLabel: String?,
    val routeProgress: Float?,
    val isManeuverConfirmed: Boolean
)

/** Produces safe, deterministic guidance copy from the explicit navigation state. */
object NavigationGuidancePresentationFactory {
    fun create(routeAvailable: Boolean, state: NavigationGuidanceState): NavigationGuidancePresentation {
        if (!routeAvailable || state.phase == NavigationGuidancePhase.NO_ROUTE) return hidden()

        val maneuverIsConfirmed = state.phase == NavigationGuidancePhase.NAVIGATING &&
            state.routeConfidence == RouteGuidanceConfidence.SUFFICIENT &&
            !state.nextManeuver.isNullOrBlank()
        return when (state.phase) {
            NavigationGuidancePhase.ROUTE_READY -> NavigationGuidancePresentation(
                visible = true,
                stateLabel = "ROUTE READY",
                maneuverLabel = null,
                turnPreview = state.nextManeuver?.let { "First maneuver: $it" },
                distanceLabel = null,
                routeProgress = 0f,
                isManeuverConfirmed = false
            )
            NavigationGuidancePhase.NAVIGATING -> NavigationGuidancePresentation(
                visible = true,
                stateLabel = if (maneuverIsConfirmed) "NAVIGATION ACTIVE" else "GUIDANCE AWAITING RELIABLE ROUTE",
                maneuverLabel = state.nextManeuver?.takeIf { maneuverIsConfirmed },
                turnPreview = state.nextManeuver?.takeIf { maneuverIsConfirmed }?.let { "Next: $it" },
                distanceLabel = state.distanceToManeuverMeters?.takeIf { maneuverIsConfirmed }?.let(::distanceLabel),
                routeProgress = state.routeProgress,
                isManeuverConfirmed = maneuverIsConfirmed
            )
            NavigationGuidancePhase.REROUTING -> NavigationGuidancePresentation(
                visible = true,
                stateLabel = "REROUTING",
                maneuverLabel = null,
                turnPreview = "Finding a new route",
                distanceLabel = null,
                routeProgress = state.routeProgress,
                isManeuverConfirmed = false
            )
            NavigationGuidancePhase.ARRIVED -> NavigationGuidancePresentation(
                visible = true,
                stateLabel = "ARRIVED",
                maneuverLabel = null,
                turnPreview = "You have reached your destination",
                distanceLabel = null,
                routeProgress = 1f,
                isManeuverConfirmed = false
            )
            NavigationGuidancePhase.NO_ROUTE -> hidden()
        }
    }

    private fun distanceLabel(distanceMeters: Double): String = when {
        distanceMeters >= 1_000.0 -> "${"%.1f".format(java.util.Locale.US, distanceMeters / 1_000.0)} km"
        else -> "${distanceMeters.toInt()} m"
    }

    private fun hidden() = NavigationGuidancePresentation(false, "", null, null, null, null, false)
}
