package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import java.io.Serializable

/** Heading of the phone/device in the navigation reference frame. */
data class DeviceOrientation(
    val headingDegrees: Double?,
    val uncertaintyDegrees: Double,
    val timestamp: MonotonicTimestamp
) : Serializable {
    init {
        require(headingDegrees == null || headingDegrees.isFinite() && headingDegrees in 0.0..<360.0)
        require(uncertaintyDegrees.isFinite() && uncertaintyDegrees >= 0.0)
    }
}

/** Direction in which the pedestrian is moving; intentionally distinct from phone orientation. */
data class PedestrianMovementDirection(
    val headingDegrees: Double?,
    val uncertaintyDegrees: Double,
    val timestamp: MonotonicTimestamp
) : Serializable {
    init {
        require(headingDegrees == null || headingDegrees.isFinite() && headingDegrees in 0.0..<360.0)
        require(uncertaintyDegrees.isFinite() && uncertaintyDegrees >= 0.0)
    }
}

/** Combined heading state retaining independent device and pedestrian-direction estimates. */
data class PedestrianHeadingState(
    val deviceOrientation: DeviceOrientation,
    val movementDirection: PedestrianMovementDirection,
    val timestamp: MonotonicTimestamp
) : Serializable {
    init {
        require(timestamp >= deviceOrientation.timestamp) { "State timestamp cannot precede device orientation" }
        require(timestamp >= movementDirection.timestamp) { "State timestamp cannot precede movement direction" }
    }
}
