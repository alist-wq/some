package com.example.navigation.routing

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence

enum class ActiveRoutePhase {
    ACTIVE,
    LOW_CONFIDENCE,
    OFF_ROUTE,
    REROUTE_REQUIRED,
    PAUSE_REQUESTED,
    PAUSED,
    RESUME_AWAITING_FRESH_OBSERVATIONS,
    RESUMED,
    ARRIVED,
    CANCELLED,
    STOPPED,
    UNAVAILABLE
}

data class ActiveRouteConfig(
    val arrivalDistanceMeters: Double = 15.0,
    val arrivalProgressToleranceMeters: Double = 20.0,
    val arrivalEvidenceCount: Int = 3,
    val offRouteDistanceMeters: Double = 40.0,
    val offRouteEvidenceCount: Int = 2,
    val rerouteEvidenceCount: Int = 3
) {
    init {
        require(arrivalDistanceMeters > 0.0 && arrivalProgressToleranceMeters >= 0.0 && offRouteDistanceMeters > 0.0)
        require(arrivalEvidenceCount > 1 && offRouteEvidenceCount > 0 && rerouteEvidenceCount >= offRouteEvidenceCount)
    }
}

data class ConfirmedRoutePlan(val id: String, val candidate: RoutePlanCandidate) { init { require(id.isNotBlank()) } }

data class ActiveRouteObservation(
    val timestamp: MonotonicTimestamp,
    val position: NavigationPosition?,
    val routeProgress: RouteProgress?,
    val distanceToRouteMeters: Double?,
    val distanceToDestinationMeters: Double?,
    val nextManeuver: String? = null
) {
    init {
        listOfNotNull(distanceToRouteMeters, distanceToDestinationMeters).forEach { require(it.isFinite() && it >= 0.0) }
    }
}

data class ActiveRouteState(
    val phase: ActiveRoutePhase = ActiveRoutePhase.STOPPED,
    val confirmedPlan: ConfirmedRoutePlan? = null,
    val progress: RouteProgress? = null,
    val remainingDistanceMeters: Double? = null,
    val nextManeuver: String? = null,
    val confidence: PositionConfidence? = null,
    val estimatedAccuracyMeters: Double? = null,
    val lastTimestamp: MonotonicTimestamp? = null,
    val reason: String? = null,
    internal val arrivalEvidence: Int = 0,
    internal val offRouteEvidence: Int = 0
) {
    val guidancePresentationPaused: Boolean get() = phase in setOf(
        ActiveRoutePhase.PAUSE_REQUESTED,
        ActiveRoutePhase.PAUSED,
        ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS
    )
    val guidanceOutputActive: Boolean get() = phase in setOf(
        ActiveRoutePhase.ACTIVE,
        ActiveRoutePhase.LOW_CONFIDENCE,
        ActiveRoutePhase.OFF_ROUTE,
        ActiveRoutePhase.REROUTE_REQUIRED,
        ActiveRoutePhase.RESUMED,
        ActiveRoutePhase.UNAVAILABLE
    )
}

/** Ephemeral evidence gate; it deliberately stores no prior estimator or route-progress state. */
class ActiveGuidanceFreshObservationGate {
    private var freshLocationTimestamp: MonotonicTimestamp? = null
    private var freshSensorTimestamp: MonotonicTimestamp? = null

    fun observeLocation(timestamp: MonotonicTimestamp) {
        if (freshLocationTimestamp == null || timestamp > freshLocationTimestamp!!) freshLocationTimestamp = timestamp
    }

    fun observeSensor(timestamp: MonotonicTimestamp) {
        if (freshSensorTimestamp == null || timestamp > freshSensorTimestamp!!) freshSensorTimestamp = timestamp
    }

    fun isReady(): Boolean = freshLocationTimestamp != null && freshSensorTimestamp != null
    fun clear() { freshLocationTimestamp = null; freshSensorTimestamp = null }
}

/** Lifecycle reducer over existing navigation estimates and route-progress output. */
class ActiveRouteLifecycle(private val config: ActiveRouteConfig = ActiveRouteConfig()) {
    var state = ActiveRouteState()
        private set
    private val freshObservationGate = ActiveGuidanceFreshObservationGate()
    private var resumeMinimumTimestamp: MonotonicTimestamp? = null

    fun start(plan: ConfirmedRoutePlan): ActiveRouteState {
        require(state.phase == ActiveRoutePhase.STOPPED) { "Stop the current route before starting another" }
        state = ActiveRouteState(phase = ActiveRoutePhase.ACTIVE, confirmedPlan = plan)
        return state
    }

    /**
     * Restores only a previously confirmed route after an interruption. Progress, navigation
     * position, inertial state, and timestamps are intentionally absent until fresh evidence arrives.
     */
    fun resumeAfterInterruption(plan: ConfirmedRoutePlan): ActiveRouteState {
        require(state.phase == ActiveRoutePhase.STOPPED) { "Stop the current route before restoring it" }
        freshObservationGate.clear()
        resumeMinimumTimestamp = null
        state = ActiveRouteState(
            phase = ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS,
            confirmedPlan = plan,
            reason = "Route restored for review; fresh location and sensor observations are required"
        )
        return state
    }

    fun observe(observation: ActiveRouteObservation): ActiveRouteState {
        val plan = requireNotNull(state.confirmedPlan) { "A confirmed route must be started first" }
        if (state.phase == ActiveRoutePhase.ARRIVED) return state
        if (state.phase in setOf(ActiveRoutePhase.PAUSE_REQUESTED, ActiveRoutePhase.PAUSED, ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS,
                ActiveRoutePhase.CANCELLED, ActiveRoutePhase.STOPPED)) return state
        val previousTimestamp = state.lastTimestamp
        if (previousTimestamp != null && observation.timestamp <= previousTimestamp) return unavailable("Stale route observation", previousTimestamp)
        val position = observation.position ?: return unavailable("Navigation position unavailable", observation.timestamp)
        if (position.timestamp != observation.timestamp || !position.estimatedAccuracyMeters.isFinite() || position.estimatedAccuracyMeters < 0.0) {
            return unavailable("Navigation position metadata is invalid", observation.timestamp)
        }
        if (position.confidence == PositionConfidence.LOST) return unavailable("Navigation position lost", observation.timestamp)
        if (position.confidence == PositionConfidence.LOW) return state.copy(
            phase = ActiveRoutePhase.LOW_CONFIDENCE, confidence = position.confidence,
            estimatedAccuracyMeters = position.estimatedAccuracyMeters, lastTimestamp = observation.timestamp,
            reason = "Route progress held because position confidence is low", arrivalEvidence = 0, offRouteEvidence = 0
        ).also { state = it }
        val progress = observation.routeProgress ?: return unavailable("Route progress unavailable", observation.timestamp)
        if (progress.segmentIndex !in plan.candidate.route.segments.indices || progress.metersAlongSegment < 0.0 ||
            progress.metersAlongSegment > plan.candidate.route.segments[progress.segmentIndex].lengthMeters) {
            return unavailable("Route progress is invalid", observation.timestamp)
        }
        val previousProgress = state.progress?.totalMetersAlongRoute ?: 0.0
        if (progress.totalMetersAlongRoute < previousProgress) return unavailable("Route progress moved backwards", observation.timestamp)
        val routeLength = plan.candidate.route.totalDistanceMeters
        if (progress.totalMetersAlongRoute > routeLength + 0.01) return unavailable("Route progress exceeds route length", observation.timestamp)

        val offEvidence = if ((observation.distanceToRouteMeters ?: 0.0) > config.offRouteDistanceMeters) state.offRouteEvidence + 1 else 0
        val remaining = (routeLength - progress.totalMetersAlongRoute).coerceAtLeast(0.0)
        val arrivalCandidate = remaining <= config.arrivalProgressToleranceMeters &&
            (observation.distanceToDestinationMeters ?: Double.POSITIVE_INFINITY) <= config.arrivalDistanceMeters && offEvidence == 0
        val arrivalEvidence = if (arrivalCandidate) state.arrivalEvidence + 1 else 0
        val phase = when {
            arrivalEvidence >= config.arrivalEvidenceCount -> ActiveRoutePhase.ARRIVED
            offEvidence >= config.rerouteEvidenceCount -> ActiveRoutePhase.REROUTE_REQUIRED
            offEvidence >= config.offRouteEvidenceCount -> ActiveRoutePhase.OFF_ROUTE
            else -> ActiveRoutePhase.ACTIVE
        }
        state = state.copy(
            phase = phase, progress = progress, remainingDistanceMeters = remaining,
            nextManeuver = observation.nextManeuver, confidence = position.confidence,
            estimatedAccuracyMeters = position.estimatedAccuracyMeters, lastTimestamp = observation.timestamp,
            reason = when (phase) {
                ActiveRoutePhase.OFF_ROUTE -> "Sustained off-route evidence"
                ActiveRoutePhase.REROUTE_REQUIRED -> "Reroute is required; no route was calculated automatically"
                ActiveRoutePhase.ARRIVED -> "Sustained arrival evidence"
                else -> null
            }, arrivalEvidence = arrivalEvidence, offRouteEvidence = offEvidence
        )
        return state
    }

    fun stop(): ActiveRouteState {
        freshObservationGate.clear()
        resumeMinimumTimestamp = null
        state = ActiveRouteState(phase = ActiveRoutePhase.STOPPED)
        return state
    }

    /** Requests pause without changing route selection, trust, sensor collection, or progress. */
    fun requestPause(): ActiveRouteState {
        require(state.phase in PAUSABLE_PHASES) { "Guidance cannot be paused from ${state.phase}" }
        state = state.copy(phase = ActiveRoutePhase.PAUSE_REQUESTED, reason = "Guidance pause requested")
        return state
    }

    fun canRequestPause(): Boolean = state.phase in PAUSABLE_PHASES

    /** Confirms the requested pause; the confirmed route remains available for review. */
    fun confirmPause(): ActiveRouteState {
        require(state.phase == ActiveRoutePhase.PAUSE_REQUESTED) { "Guidance pause was not requested" }
        freshObservationGate.clear()
        state = state.copy(phase = ActiveRoutePhase.PAUSED, reason = "Guidance paused; route retained for review")
        return state
    }

    /** Starts a resume attempt but keeps guidance inactive until fresh independent observations arrive. */
    fun requestResume(): ActiveRouteState {
        require(state.phase == ActiveRoutePhase.PAUSED) { "Guidance can resume only from a paused state" }
        freshObservationGate.clear()
        resumeMinimumTimestamp = state.lastTimestamp
        state = state.copy(
            phase = ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS,
            reason = "Fresh location and sensor observations are required before guidance resumes"
        )
        return state
    }

    fun observeFreshLocation(timestamp: MonotonicTimestamp): ActiveRouteState {
        require(state.phase == ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS) { "Fresh location is not required in ${state.phase}" }
        requireAfterPause(timestamp)
        freshObservationGate.observeLocation(timestamp)
        return state
    }

    fun observeFreshSensor(timestamp: MonotonicTimestamp): ActiveRouteState {
        require(state.phase == ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS) { "Fresh sensor evidence is not required in ${state.phase}" }
        requireAfterPause(timestamp)
        freshObservationGate.observeSensor(timestamp)
        return state
    }

    /** Activates the retained route only after fresh post-pause or post-restart evidence is present. */
    fun resumeWhenReady(): ActiveRouteState {
        require(state.phase == ActiveRoutePhase.RESUME_AWAITING_FRESH_OBSERVATIONS) { "Guidance is not awaiting resume evidence" }
        if (!freshObservationGate.isReady()) return state
        state = state.copy(phase = ActiveRoutePhase.RESUMED, reason = "Guidance resumed from fresh observations")
        freshObservationGate.clear()
        resumeMinimumTimestamp = null
        return state
    }

    /** Explicit cancellation remains distinct from arrival and retains no active guidance state. */
    fun cancel(): ActiveRouteState {
        require(state.phase !in setOf(ActiveRoutePhase.ARRIVED, ActiveRoutePhase.CANCELLED, ActiveRoutePhase.STOPPED)) {
            "Guidance cannot be cancelled from ${state.phase}"
        }
        freshObservationGate.clear()
        resumeMinimumTimestamp = null
        state = state.copy(phase = ActiveRoutePhase.CANCELLED, reason = "Guidance cancelled")
        return state
    }

    /** Arrival is cleared only after an explicit acknowledgement, never by pause or resume. */
    fun acknowledgeArrival(): ActiveRouteState {
        require(state.phase == ActiveRoutePhase.ARRIVED) { "Only an arrived route can be acknowledged" }
        freshObservationGate.clear()
        resumeMinimumTimestamp = null
        state = ActiveRouteState(phase = ActiveRoutePhase.STOPPED)
        return state
    }

    fun replay(plan: ConfirmedRoutePlan, observations: List<ActiveRouteObservation>): List<ActiveRouteState> {
        stop(); val states = mutableListOf(start(plan)); observations.forEach { states += observe(it) }; return states
    }

    private fun unavailable(reason: String, timestamp: MonotonicTimestamp): ActiveRouteState {
        state = state.copy(phase = ActiveRoutePhase.UNAVAILABLE, lastTimestamp = timestamp, reason = reason, arrivalEvidence = 0, offRouteEvidence = 0)
        return state
    }

    private fun requireAfterPause(timestamp: MonotonicTimestamp) {
        require(resumeMinimumTimestamp == null || timestamp > resumeMinimumTimestamp!!) {
            "Fresh observation must be newer than the last pre-pause route observation"
        }
    }

    private companion object {
        val PAUSABLE_PHASES = setOf(
            ActiveRoutePhase.ACTIVE,
            ActiveRoutePhase.LOW_CONFIDENCE,
            ActiveRoutePhase.OFF_ROUTE,
            ActiveRoutePhase.REROUTE_REQUIRED,
            ActiveRoutePhase.UNAVAILABLE,
            ActiveRoutePhase.RESUMED
        )
    }
}
