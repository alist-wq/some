package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.sensors.AccelerometerSample
import kotlin.math.sqrt

data class StepDetectorConfig(
    val accelerationThresholdMetersPerSecondSquared: Double = 1.5,
    val releaseThresholdMetersPerSecondSquared: Double = 0.8,
    val refractoryPeriodNanos: Long = 250_000_000L,
    val minimumCadenceStepsPerMinute: Double = 60.0,
    val maximumCadenceStepsPerMinute: Double = 220.0
) {
    init {
        require(accelerationThresholdMetersPerSecondSquared.isFinite() && accelerationThresholdMetersPerSecondSquared > 0.0)
        require(releaseThresholdMetersPerSecondSquared.isFinite() && releaseThresholdMetersPerSecondSquared in 0.0..<accelerationThresholdMetersPerSecondSquared)
        require(refractoryPeriodNanos > 0L)
        require(minimumCadenceStepsPerMinute.isFinite() && minimumCadenceStepsPerMinute > 0.0)
        require(maximumCadenceStepsPerMinute.isFinite() && maximumCadenceStepsPerMinute > minimumCadenceStepsPerMinute)
    }
}

data class DetectedStep(
    val timestamp: MonotonicTimestamp,
    val accelerationDeviationMetersPerSecondSquared: Double,
    /** Null after a stationary gap, when there is no valid cadence interval yet. */
    val cadenceStepsPerMinute: Double?
)

/** Deterministic acceleration-magnitude step detector suitable for live collection and replay. */
class StepDetector(private val config: StepDetectorConfig = StepDetectorConfig()) {
    private var armed = true
    private var lastStepTimestamp: MonotonicTimestamp? = null

    fun process(sample: AccelerometerSample): DetectedStep? {
        val deviation = magnitude(sample) - STANDARD_GRAVITY
        if (!armed) {
            if (deviation <= config.releaseThresholdMetersPerSecondSquared) armed = true
            return null
        }
        if (deviation < config.accelerationThresholdMetersPerSecondSquared) return null
        armed = false
        val previous = lastStepTimestamp
        if (previous == null) return accept(sample.timestamp, deviation, null)
        val elapsed = sample.timestamp.differenceNanos(previous)
        if (elapsed < config.refractoryPeriodNanos) return null
        val cadence = MINUTE_NANOS / elapsed
        return when {
            cadence > config.maximumCadenceStepsPerMinute -> null
            cadence < config.minimumCadenceStepsPerMinute -> accept(sample.timestamp, deviation, null)
            else -> accept(sample.timestamp, deviation, cadence)
        }
    }

    fun reset() {
        armed = true
        lastStepTimestamp = null
    }

    private fun accept(timestamp: MonotonicTimestamp, deviation: Double, cadence: Double?): DetectedStep {
        lastStepTimestamp = timestamp
        return DetectedStep(timestamp, deviation, cadence)
    }

    private fun magnitude(sample: AccelerometerSample): Double = sqrt(
        sample.xMetersPerSecondSquared.toDouble() * sample.xMetersPerSecondSquared +
            sample.yMetersPerSecondSquared.toDouble() * sample.yMetersPerSecondSquared +
            sample.zMetersPerSecondSquared.toDouble() * sample.zMetersPerSecondSquared
    )

    private companion object {
        const val STANDARD_GRAVITY = 9.80665
        const val MINUTE_NANOS = 60_000_000_000.0
    }
}
