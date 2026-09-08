package com.example.navigation.pedestrian

import com.example.navigation.navigation.*
import kotlin.math.*

class PedestrianDeadReckoner(initial: NavigationPosition) {
    var position = initial; private set
    fun propagate(state: PedestrianMotionState, stride: StrideEstimate): NavigationPosition {
        val heading = state.headingDegrees ?: return position.copy(confidence = PositionConfidence.LOST, estimatedAccuracyMeters = position.estimatedAccuracyMeters + stride.estimatedLengthMeters, source = PositionSource.PEDESTRIAN_DEAD_RECKONING, timestamp = state.timestamp).also { position = it }
        val d = stride.estimatedLengthMeters / 6_371_000.0; val b = Math.toRadians(heading); val lat1 = Math.toRadians(position.latitude); val lon1 = Math.toRadians(position.longitude)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(b)); val lon2 = lon1 + atan2(sin(b) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        val uncertainty = position.estimatedAccuracyMeters + stride.uncertaintyMeters + state.headingUncertaintyDegrees / 180.0 * stride.estimatedLengthMeters
        position = NavigationPosition(Math.toDegrees(lat2), Math.toDegrees(lon2), if (uncertainty < 15) PositionConfidence.MEDIUM else if (uncertainty < 50) PositionConfidence.LOW else PositionConfidence.LOST, uncertainty, PositionSource.PEDESTRIAN_DEAD_RECKONING, state.timestamp)
        return position
    }
}
