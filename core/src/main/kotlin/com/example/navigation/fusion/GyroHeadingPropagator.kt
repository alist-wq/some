package com.example.navigation.fusion

import com.example.navigation.sensors.GyroscopeSample
import kotlin.math.PI

/**
 * Propagates vehicle heading from the gyroscope's relative yaw rate using monotonic elapsed time.
 *
 * This class is intentionally stateless so a replayed sequence produces the same result as the
 * corresponding live sequence. The supplied sample's Z-axis rate is treated as the rate over the
 * interval since [VehicleMotionState.timestamp].
 */
class GyroHeadingPropagator {
    fun propagate(
        state: VehicleMotionState,
        sample: GyroscopeSample
    ): VehicleMotionState {
        require(sample.timestamp > state.timestamp) {
            "Gyroscope sample timestamp must be after the vehicle state timestamp"
        }

        val elapsedSeconds = sample.timestamp.differenceNanos(state.timestamp) / NANOS_PER_SECOND
        val correctedYawRateRadiansPerSecond =
            sample.zRadiansPerSecond.toDouble() - state.gyroBiasRadiansPerSecond
        val headingDeltaDegrees = correctedYawRateRadiansPerSecond * elapsedSeconds * DEGREES_PER_RADIAN

        return state.copy(
            headingDegrees = normalizeDegrees(state.headingDegrees + headingDeltaDegrees),
            timestamp = sample.timestamp
        )
    }

    private fun normalizeDegrees(degrees: Double): Double =
        ((degrees % FULL_CIRCLE_DEGREES) + FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val FULL_CIRCLE_DEGREES = 360.0
        const val DEGREES_PER_RADIAN = FULL_CIRCLE_DEGREES / (2.0 * PI)
    }
}
