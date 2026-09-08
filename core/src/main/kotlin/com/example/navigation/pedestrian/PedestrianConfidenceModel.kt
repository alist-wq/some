package com.example.navigation.pedestrian

import com.example.navigation.navigation.PositionConfidence
import kotlin.math.*

data class PedestrianConfidenceInput(val strideUncertaintyMeters: Double, val headingUncertaintyDegrees: Double, val poseConfidence: Double, val pathAmbiguity: Double, val distanceSinceTrustedGnssMeters: Double, val stepCountSinceTrustedGnss: Long, val mapQuality: Double, val matched: Boolean)
data class PedestrianConfidenceEstimate(val confidence: PositionConfidence, val estimatedAccuracyMeters: Double, val score: Double)
class PedestrianConfidenceModel {
    fun evaluate(i: PedestrianConfidenceInput): PedestrianConfidenceEstimate {
        require(i.poseConfidence in 0.0..1.0 && i.pathAmbiguity in 0.0..1.0 && i.mapQuality in 0.0..1.0)
        val accuracy = 3.0 + i.strideUncertaintyMeters * sqrt(i.stepCountSinceTrustedGnss.toDouble().coerceAtLeast(1.0)) + i.distanceSinceTrustedGnssMeters * 0.04 + i.headingUncertaintyDegrees / 8.0 + i.pathAmbiguity * 25.0 + (1-i.poseConfidence)*20.0 + (1-i.mapQuality)*20.0 + if(i.matched) 0.0 else 30.0
        val score=(1.0-accuracy/100.0).coerceIn(0.0,1.0); val c=when { score>=.8->PositionConfidence.HIGH; score>=.6->PositionConfidence.MEDIUM; score>=.3->PositionConfidence.LOW; else->PositionConfidence.LOST }
        return PedestrianConfidenceEstimate(c,accuracy,score)
    }
}
