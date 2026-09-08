package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.sensors.BarometerSample
import kotlin.math.pow

/**
 * Relative vertical displacement derived from pressure changes within one sensor session.
 * It deliberately has no absolute-elevation interpretation.
 */
data class RelativeAltitudeEstimate(
    val relativeAltitudeMeters: Double,
    val baselinePressureHectopascals: Double,
    val timestamp: MonotonicTimestamp
) {
    init {
        require(relativeAltitudeMeters.isFinite())
        require(baselinePressureHectopascals.isFinite() && baselinePressureHectopascals > 0.0)
    }
}

/** Converts barometer samples to displacement relative to the first sample after construction or [reset]. */
class RelativeAltitudeEstimator {
    private var baselinePressureHectopascals: Double? = null
    private var previousTimestamp: MonotonicTimestamp? = null

    fun process(sample: BarometerSample): RelativeAltitudeEstimate {
        val pressure = sample.pressureHectopascals.toDouble()
        require(pressure.isFinite() && pressure > 0.0) { "Barometric pressure must be finite and positive." }
        previousTimestamp?.let { previous ->
            require(sample.timestamp > previous) { "Barometer timestamps must increase monotonically." }
        }

        val baseline = baselinePressureHectopascals ?: pressure.also { baselinePressureHectopascals = it }
        previousTimestamp = sample.timestamp
        return RelativeAltitudeEstimate(
            relativeAltitudeMeters = SEA_LEVEL_SCALE_METERS * (1.0 - (pressure / baseline).pow(PRESSURE_EXPONENT)),
            baselinePressureHectopascals = baseline,
            timestamp = sample.timestamp
        )
    }

    fun reset() {
        baselinePressureHectopascals = null
        previousTimestamp = null
    }

    private companion object {
        const val SEA_LEVEL_SCALE_METERS = 44_330.0
        const val PRESSURE_EXPONENT = 0.19029495718363465
    }
}
