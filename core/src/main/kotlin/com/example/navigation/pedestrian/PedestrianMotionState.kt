package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp

data class PedestrianMotionState(val stepCount: Long, val distanceMeters: Double, val headingDegrees: Double?, val strideEstimateMeters: Double, val strideUncertaintyMeters: Double, val headingUncertaintyDegrees: Double, val poseConfidence: Double, val timestamp: MonotonicTimestamp) {
    init { require(stepCount >= 0 && distanceMeters >= 0 && strideEstimateMeters > 0 && strideUncertaintyMeters >= 0 && headingUncertaintyDegrees >= 0 && poseConfidence in 0.0..1.0) }
    fun onStep(stride: StrideEstimate, heading: PedestrianMovementDirection, pose: PhonePoseEstimate) = copy(stepCount = stepCount + 1, distanceMeters = distanceMeters + stride.estimatedLengthMeters, headingDegrees = heading.headingDegrees, strideEstimateMeters = stride.estimatedLengthMeters, strideUncertaintyMeters = stride.uncertaintyMeters, headingUncertaintyDegrees = heading.uncertaintyDegrees, poseConfidence = pose.confidence, timestamp = heading.timestamp)
}
