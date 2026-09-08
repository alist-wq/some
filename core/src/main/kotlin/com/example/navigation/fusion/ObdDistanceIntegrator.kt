package com.example.navigation.fusion

import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource

/**
 * Integrates OBD vehicle speed over monotonic elapsed time.
 *
 * The previous speed is held in [VehicleMotionState]; consecutive speeds are integrated with the
 * trapezoidal rule, so elapsed time is derived from timestamps rather than an assumed poll rate.
 */
class ObdDistanceIntegrator {
    fun integrate(
        state: VehicleMotionState,
        sample: VehicleSpeedSample
    ): VehicleMotionState {
        require(sample.source == VehicleSpeedSource.OBD || sample.source == VehicleSpeedSource.REPLAY) {
            "Vehicle speed must originate from OBD or deterministic replay"
        }
        require(sample.metersPerSecond.isFinite() && sample.metersPerSecond >= 0.0) {
            "Vehicle speed must be finite and non-negative"
        }
        require(sample.timestamp > state.timestamp) {
            "Vehicle speed sample timestamp must be after the vehicle state timestamp"
        }

        val elapsedSeconds = sample.timestamp.differenceNanos(state.timestamp) / NANOS_PER_SECOND
        val distanceDeltaMeters =
            ((state.speedMetersPerSecond + sample.metersPerSecond) / 2.0) * elapsedSeconds

        return state.copy(
            speedMetersPerSecond = sample.metersPerSecond,
            distanceMeters = state.distanceMeters + distanceDeltaMeters,
            timestamp = sample.timestamp
        )
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
