package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import kotlin.math.*

data class PedestrianHeadingInput(val previousHeadingDegrees: Double?, val gyroYawDeltaDegrees: Double, val pose: PhonePoseEstimate, val trustedGnssCourseDegrees: Double?, val gnssTrust: Double, val routeHeadingDegrees: Double?, val timestamp: MonotonicTimestamp)

class PedestrianHeadingEstimator {
    fun estimate(i: PedestrianHeadingInput): PedestrianMovementDirection {
        require(i.gnssTrust in 0.0..1.0)
        val gyro = i.previousHeadingDegrees?.let { normalize(it + i.gyroYawDeltaDegrees) }
        val values = mutableListOf<Pair<Double, Double>>()
        gyro?.let { values += it to (0.65 * i.pose.confidence.coerceAtLeast(0.2)) }
        i.trustedGnssCourseDegrees?.takeIf { i.gnssTrust >= 0.7 }?.let { values += it to i.gnssTrust }
        i.routeHeadingDegrees?.let { values += it to 0.25 }
        if (values.isEmpty()) return PedestrianMovementDirection(null, 180.0, i.timestamp)
        val x = values.sumOf { cos(Math.toRadians(it.first)) * it.second }
        val y = values.sumOf { sin(Math.toRadians(it.first)) * it.second }
        val heading = normalize(Math.toDegrees(atan2(y, x)))
        val posePenalty = if (i.pose.pose == PhonePose.UNKNOWN) 55.0 else (1.0 - i.pose.confidence) * 45.0
        return PedestrianMovementDirection(heading, (15.0 + posePenalty + (1.0 - i.gnssTrust) * 25.0).coerceAtMost(180.0), i.timestamp)
    }
    private fun normalize(v: Double) = ((v % 360.0) + 360.0) % 360.0
}
