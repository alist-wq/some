package com.example.navigation.routing

import java.io.Serializable

/** One plausible route position retained for a future multi-hypothesis tracker. */
data class RouteCandidateState(
    val segment: RouteSegment,
    val metersAlongSegment: Double,
    val totalRouteDistanceMeters: Double,
    val headingDegrees: Double,
    val probability: Double,
    val uncertaintyMeters: Double
) : Serializable {
    init {
        require(metersAlongSegment.isFinite() && metersAlongSegment in 0.0..segment.lengthMeters) {
            "metersAlongSegment must be within the route segment"
        }
        require(totalRouteDistanceMeters.isFinite() && totalRouteDistanceMeters >= 0.0) {
            "totalRouteDistanceMeters must be finite and non-negative"
        }
        require(headingDegrees.isFinite() && headingDegrees in 0.0..<360.0) {
            "headingDegrees must be in [0, 360)"
        }
        require(probability.isFinite() && probability in 0.0..1.0) {
            "probability must be in [0, 1]"
        }
        require(uncertaintyMeters.isFinite() && uncertaintyMeters >= 0.0) {
            "uncertaintyMeters must be finite and non-negative"
        }
    }

    private companion object { private const val serialVersionUID = 1L }
}
