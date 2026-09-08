package com.example.navigation.fusion

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import java.io.Serializable

/** Snapshot of the vehicle estimator state; propagation is implemented by later fusion tasks. */
data class VehicleMotionState(
    val position: NavigationPosition,
    val headingDegrees: Double,
    val speedMetersPerSecond: Double,
    val gyroBiasRadiansPerSecond: Double,
    val distanceMeters: Double,
    val uncertaintyMeters: Double,
    val timestamp: MonotonicTimestamp
) : Serializable {
    init {
        require(headingDegrees.isFinite() && headingDegrees >= 0.0 && headingDegrees < 360.0) {
            "headingDegrees must be in [0, 360)"
        }
        require(speedMetersPerSecond.isFinite() && speedMetersPerSecond >= 0.0) {
            "speedMetersPerSecond cannot be negative"
        }
        require(gyroBiasRadiansPerSecond.isFinite()) { "gyroBiasRadiansPerSecond must be finite" }
        require(distanceMeters.isFinite() && distanceMeters >= 0.0) { "distanceMeters cannot be negative" }
        require(uncertaintyMeters.isFinite() && uncertaintyMeters >= 0.0) { "uncertaintyMeters cannot be negative" }
    }

    private companion object { private const val serialVersionUID = 1L }
}
