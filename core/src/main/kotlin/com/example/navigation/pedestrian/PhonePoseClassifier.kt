package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp

enum class PhonePose { HAND_FORWARD, HAND_SWINGING, POCKET, UNKNOWN }
data class PhonePoseFeatures(val accelerationVariance: Double, val rotationVariance: Double, val screenUpAlignment: Double, val timestamp: MonotonicTimestamp)
data class PhonePoseEstimate(val pose: PhonePose, val probabilities: Map<PhonePose, Double>, val confidence: Double, val timestamp: MonotonicTimestamp)

/** Conservative V1 classifier over windowed features; weak/ambiguous evidence remains UNKNOWN. */
class PhonePoseClassifier {
    fun classify(f: PhonePoseFeatures): PhonePoseEstimate {
        require(f.accelerationVariance >= 0 && f.rotationVariance >= 0 && f.screenUpAlignment in -1.0..1.0)
        val raw = mapOf(
            PhonePose.HAND_FORWARD to (f.screenUpAlignment.coerceAtLeast(0.0) * (1.0 / (1.0 + f.rotationVariance))),
            PhonePose.HAND_SWINGING to (f.rotationVariance.coerceAtMost(4.0) / 4.0 + f.accelerationVariance.coerceAtMost(4.0) / 8.0),
            PhonePose.POCKET to ((-f.screenUpAlignment).coerceAtLeast(0.0) + (f.accelerationVariance / (1.0 + f.accelerationVariance)) * 0.5),
            PhonePose.UNKNOWN to 0.35
        )
        val total = raw.values.sum(); val p = raw.mapValues { it.value / total }
        val best = p.maxBy { it.value }
        val pose = if (best.value >= 0.55) best.key else PhonePose.UNKNOWN
        return PhonePoseEstimate(pose, p, if (pose == PhonePose.UNKNOWN) p.getValue(PhonePose.UNKNOWN) else best.value, f.timestamp)
    }
}
