package com.example.navigation.routing

import kotlin.math.abs
import kotlin.math.exp

data class RouteHypothesisObservation(
    val distanceTraveledMeters: Double,
    val headingDegrees: Double,
    val observedTurn: RouteTurnDirection? = null
) {
    init {
        require(distanceTraveledMeters.isFinite() && distanceTraveledMeters >= 0.0)
        require(headingDegrees.isFinite() && headingDegrees in 0.0..<360.0)
    }
}

data class RouteHypothesisTrackerConfig(
    val headingStandardDeviationDegrees: Double = 30.0,
    val minimumTurnDegrees: Double = 30.0,
    val turnMismatchWeight: Double = 0.1,
    val uncertaintyGrowthPerMeter: Double = 0.05
) {
    init {
        require(headingStandardDeviationDegrees > 0.0 && headingStandardDeviationDegrees.isFinite())
        require(minimumTurnDegrees in 0.0..180.0)
        require(turnMismatchWeight in 0.0..1.0)
        require(uncertaintyGrowthPerMeter >= 0.0 && uncertaintyGrowthPerMeter.isFinite())
    }
}

/**
 * Maintains route-progress hypotheses using only traveled distance, turn observations, segment
 * headings, and the ordered planned-route topology. No GNSS observation is accepted by this API.
 */
class RouteConstrainedHypothesisTracker(
    private val route: Route,
    private val config: RouteHypothesisTrackerConfig = RouteHypothesisTrackerConfig()
) {
    private var candidates: List<RouteCandidateState> = emptyList()

    init { require(route.segments.isNotEmpty()) { "Hypothesis tracking requires route segments" } }

    fun initialize(initialCandidates: List<RouteCandidateState>): List<RouteCandidateState> {
        require(initialCandidates.isNotEmpty()) { "At least one route candidate is required" }
        initialCandidates.forEach { require(segmentIndex(it.segment) >= 0) { "Candidate segment is not on the planned route" } }
        candidates = normalize(initialCandidates)
        return candidates
    }

    fun update(observation: RouteHypothesisObservation): List<RouteCandidateState> {
        check(candidates.isNotEmpty()) { "Hypothesis tracker is not initialized" }
        val scored = candidates.mapNotNull { candidate -> propagate(candidate, observation) }
        check(scored.isNotEmpty()) { "All route hypotheses left the planned route" }
        candidates = normalize(scored)
        return candidates
    }

    fun currentCandidates(): List<RouteCandidateState> = candidates

    private fun propagate(candidate: RouteCandidateState, observation: RouteHypothesisObservation): RouteCandidateState? {
        var index = segmentIndex(candidate.segment)
        var along = candidate.metersAlongSegment
        var remaining = observation.distanceTraveledMeters
        val crossedTurns = mutableListOf<RouteTurnDirection>()
        while (remaining > route.segments[index].lengthMeters - along) {
            remaining -= route.segments[index].lengthMeters - along
            if (index == route.segments.lastIndex) return null
            val nextIndex = index + 1
            crossedTurns += turnDirection(segmentHeading(index), segmentHeading(nextIndex))
            index = nextIndex
            along = 0.0
        }
        along += remaining
        val segment = route.segments[index]
        val expectedHeading = segmentHeading(index)
        val headingError = angularDifferenceDegrees(expectedHeading, observation.headingDegrees)
        val headingWeight = exp(-0.5 * headingError * headingError /
            (config.headingStandardDeviationDegrees * config.headingStandardDeviationDegrees))
        val turnWeight = observation.observedTurn?.let { observed ->
            if (observed in crossedTurns || (observed == RouteTurnDirection.STRAIGHT && crossedTurns.isEmpty())) 1.0
            else config.turnMismatchWeight
        } ?: 1.0
        return RouteCandidateState(
            segment = segment,
            metersAlongSegment = along,
            totalRouteDistanceMeters = candidate.totalRouteDistanceMeters + observation.distanceTraveledMeters,
            headingDegrees = expectedHeading,
            probability = candidate.probability * headingWeight * turnWeight,
            uncertaintyMeters = candidate.uncertaintyMeters + observation.distanceTraveledMeters * config.uncertaintyGrowthPerMeter
        )
    }

    private fun normalize(values: List<RouteCandidateState>): List<RouteCandidateState> {
        val total = values.sumOf { it.probability }
        require(total > 0.0 && total.isFinite()) { "Route candidate probabilities must have positive finite mass" }
        return values.map { it.copy(probability = it.probability / total) }
    }

    private fun segmentIndex(segment: RouteSegment): Int {
        val identity = route.segments.indexOfFirst { it === segment }
        return if (identity >= 0) identity else route.segments.indexOf(segment)
    }

    private fun segmentHeading(index: Int): Double = route.segments[index].headingDegrees
        ?: geometryHeading(route.segments[index])

    private fun geometryHeading(segment: RouteSegment): Double {
        val start = segment.geometry.first(); val end = segment.geometry.last()
        val startLatitude = Math.toRadians(start.latitude)
        val endLatitude = Math.toRadians(end.latitude)
        val longitudeDelta = Math.toRadians(end.longitude - start.longitude)
        return (Math.toDegrees(kotlin.math.atan2(
            kotlin.math.sin(longitudeDelta) * kotlin.math.cos(endLatitude),
            kotlin.math.cos(startLatitude) * kotlin.math.sin(endLatitude) -
                kotlin.math.sin(startLatitude) * kotlin.math.cos(endLatitude) * kotlin.math.cos(longitudeDelta)
        )) + 360.0) % 360.0
    }

    private fun turnDirection(from: Double, to: Double): RouteTurnDirection {
        val angle = ((to - from + 540.0) % 360.0) - 180.0
        return when {
            abs(angle) < config.minimumTurnDegrees -> RouteTurnDirection.STRAIGHT
            abs(angle) >= 135.0 -> RouteTurnDirection.U_TURN
            angle > 0.0 -> RouteTurnDirection.RIGHT
            else -> RouteTurnDirection.LEFT
        }
    }

    private fun angularDifferenceDegrees(a: Double, b: Double): Double = abs(((a - b + 540.0) % 360.0) - 180.0)
}
