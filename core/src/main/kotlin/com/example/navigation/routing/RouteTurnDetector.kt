package com.example.navigation.routing

import com.example.navigation.core.MonotonicTimestamp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class RouteTurnDirection { STRAIGHT, RIGHT, LEFT, U_TURN }

data class RouteTurnDetectionConfig(
    val minimumTurnDegrees: Double = 30.0,
    val maximumTurnMismatchDegrees: Double = 45.0
) {
    init {
        require(minimumTurnDegrees in 0.0..180.0)
        require(maximumTurnMismatchDegrees in 0.0..180.0)
    }
}

sealed interface RouteTurnEvent {
    val timestamp: MonotonicTimestamp

    data class Expected(
        val direction: RouteTurnDirection,
        val angleDegrees: Double,
        override val timestamp: MonotonicTimestamp
    ) : RouteTurnEvent
    data class Observed(
        val direction: RouteTurnDirection,
        val angleDegrees: Double,
        override val timestamp: MonotonicTimestamp
    ) : RouteTurnEvent
    data class Mismatch(
        val expected: RouteTurnDirection,
        val observed: RouteTurnDirection,
        override val timestamp: MonotonicTimestamp
    ) : RouteTurnEvent
}

/** Compares one gyro-derived heading change with the planned route transition being entered. */
class RouteTurnDetector(
    private val route: Route,
    private val config: RouteTurnDetectionConfig = RouteTurnDetectionConfig()
) {
    init { require(route.segments.size >= 2) { "Route turn detection requires at least two segments" } }

    fun detect(
        enteredSegmentIndex: Int,
        headingBeforeDegrees: Double,
        headingAfterDegrees: Double,
        timestamp: MonotonicTimestamp
    ): List<RouteTurnEvent> {
        require(enteredSegmentIndex in 1 until route.segments.size) { "Entered route segment has no preceding transition" }
        require(headingBeforeDegrees in 0.0..<360.0 && headingAfterDegrees in 0.0..<360.0) {
            "Headings must be in [0, 360)"
        }
        val expectedAngle = signedTurnDegrees(segmentHeading(enteredSegmentIndex - 1), segmentHeading(enteredSegmentIndex))
        val observedAngle = signedTurnDegrees(headingBeforeDegrees, headingAfterDegrees)
        val expectedDirection = classify(expectedAngle)
        val observedDirection = classify(observedAngle)
        val events = mutableListOf<RouteTurnEvent>(
            RouteTurnEvent.Expected(expectedDirection, expectedAngle, timestamp),
            RouteTurnEvent.Observed(observedDirection, observedAngle, timestamp)
        )
        if (expectedDirection != observedDirection || abs(expectedAngle - observedAngle) > config.maximumTurnMismatchDegrees) {
            events += RouteTurnEvent.Mismatch(expectedDirection, observedDirection, timestamp)
        }
        return events
    }

    private fun segmentHeading(index: Int): Double = route.segments[index].headingDegrees ?: run {
        val geometry = route.segments[index].geometry
        val start = geometry.first(); val end = geometry.last()
        val startLatitude = Math.toRadians(start.latitude)
        val endLatitude = Math.toRadians(end.latitude)
        val longitudeDelta = Math.toRadians(end.longitude - start.longitude)
        (Math.toDegrees(atan2(sin(longitudeDelta) * cos(endLatitude), cos(startLatitude) * sin(endLatitude) - sin(startLatitude) * cos(endLatitude) * cos(longitudeDelta))) + 360.0) % 360.0
    }

    private fun signedTurnDegrees(from: Double, to: Double): Double = ((to - from + 540.0) % 360.0) - 180.0
    private fun classify(angle: Double): RouteTurnDirection = when {
        abs(angle) < config.minimumTurnDegrees -> RouteTurnDirection.STRAIGHT
        abs(angle) >= 135.0 -> RouteTurnDirection.U_TURN
        angle > 0.0 -> RouteTurnDirection.RIGHT
        else -> RouteTurnDirection.LEFT
    }
}
