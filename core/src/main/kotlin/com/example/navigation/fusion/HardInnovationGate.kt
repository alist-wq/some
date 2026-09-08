package com.example.navigation.fusion

import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.GnssTrustEvidence
import com.example.navigation.gnss.GnssTrustEvidenceAvailability
import com.example.navigation.gnss.GnssTrustEvidenceType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class HardInnovationGateConfig(
    val catastrophicExcessMeters: Double = 10_000.0,
    val uncertaintyMultiplier: Double = 3.0
) {
    init { require(catastrophicExcessMeters > 0.0 && uncertaintyMultiplier >= 1.0) }
}

data class HardInnovationGateDecision(val blockCorrection: Boolean, val evidence: GnssTrustEvidence)

/** Blocks only a remote-scale innovation; ordinary accuracy degradation remains trust evidence, not a hard reject. */
class HardInnovationGate(private val config: HardInnovationGateConfig = HardInnovationGateConfig()) {
    fun evaluate(state: VehicleMotionState, candidate: GnssSample): HardInnovationGateDecision {
        val elapsedNanos = candidate.timestamp.differenceNanos(state.timestamp)
        if (elapsedNanos < 0L) return decision(false, candidate, "candidate timestamp precedes estimator state")
        val displacement = distance(state.position.latitude, state.position.longitude, candidate.latitude, candidate.longitude)
        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        val accuracy = candidate.horizontalAccuracyMeters?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val allowance = state.speedMetersPerSecond * elapsedSeconds +
            (state.uncertaintyMeters + accuracy) * config.uncertaintyMultiplier
        val excess = (displacement - allowance).coerceAtLeast(0.0)
        return decision(excess >= config.catastrophicExcessMeters, candidate,
            "innovation=${format(displacement)}m; uncertainty-aware allowance=${format(allowance)}m; excess=${format(excess)}m")
    }

    private fun decision(block: Boolean, sample: GnssSample, detail: String) = HardInnovationGateDecision(
        block,
        GnssTrustEvidence(GnssTrustEvidenceType.HARD_INNOVATION, GnssTrustEvidenceAvailability.AVAILABLE,
            if (block) 0.0 else 1.0, if (block) "catastrophic $detail" else detail, sample.timestamp)
    )
    private fun distance(a: Double, b: Double, c: Double, d: Double): Double {
        val lat = Math.toRadians(c - a); val lon = Math.toRadians(d - b)
        val q = sin(lat / 2) * sin(lat / 2) + cos(Math.toRadians(a)) * cos(Math.toRadians(c)) * sin(lon / 2) * sin(lon / 2)
        return 6_371_000.0 * 2.0 * atan2(sqrt(q), sqrt(1.0 - q))
    }
    private fun format(value: Double) = "%.1f".format(java.util.Locale.ROOT, value)
}
