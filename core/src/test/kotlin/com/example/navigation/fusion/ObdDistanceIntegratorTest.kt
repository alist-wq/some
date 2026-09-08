package com.example.navigation.fusion

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ObdDistanceIntegratorTest {
    private val integrator = ObdDistanceIntegrator()

    @Test
    fun `integrates variable OBD speed using actual nonuniform sample intervals`() {
        val afterOneSecond = integrator.integrate(state(), speed(metersPerSecond = 10.0, seconds = 1))
        val afterThreeSeconds = integrator.integrate(afterOneSecond, speed(metersPerSecond = 20.0, seconds = 3))

        assertEquals(35.0, afterThreeSeconds.distanceMeters, TOLERANCE)
        assertEquals(20.0, afterThreeSeconds.speedMetersPerSecond, TOLERANCE)
        assertEquals(MonotonicTimestamp(3 * NANOS_PER_SECOND), afterThreeSeconds.timestamp)
    }

    private fun speed(metersPerSecond: Double, seconds: Long): VehicleSpeedSample = VehicleSpeedSample(
        metersPerSecond = metersPerSecond,
        timestamp = MonotonicTimestamp(seconds * NANOS_PER_SECOND),
        source = VehicleSpeedSource.OBD
    )

    private fun state(): VehicleMotionState = VehicleMotionState(
        position = NavigationPosition(
            latitude = 0.0,
            longitude = 0.0,
            confidence = PositionConfidence.HIGH,
            estimatedAccuracyMeters = 1.0,
            source = PositionSource.GNSS,
            timestamp = MonotonicTimestamp(0)
        ),
        headingDegrees = 0.0,
        speedMetersPerSecond = 0.0,
        gyroBiasRadiansPerSecond = 0.0,
        distanceMeters = 0.0,
        uncertaintyMeters = 1.0,
        timestamp = MonotonicTimestamp(0)
    )

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val TOLERANCE = 0.0001
    }
}
