package com.example.navigation.fusion

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.sensors.GyroscopeSample
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class GyroHeadingPropagatorTest {
    private val propagator = GyroHeadingPropagator()

    @Test
    fun `straight motion preserves heading`() {
        val result = propagator.propagate(state(headingDegrees = 27.0), gyro(zRadiansPerSecond = 0f, seconds = 2))

        assertEquals(27.0, result.headingDegrees, TOLERANCE)
    }

    @Test
    fun `right turn integrates ninety degrees`() {
        val result = propagator.propagate(state(), gyro(zRadiansPerSecond = (PI / 2.0).toFloat(), seconds = 1))

        assertEquals(90.0, result.headingDegrees, TOLERANCE)
    }

    @Test
    fun `left turn integrates ninety degrees and normalizes heading`() {
        val result = propagator.propagate(
            state(headingDegrees = 45.0),
            gyro(zRadiansPerSecond = (-PI / 2.0).toFloat(), seconds = 1)
        )

        assertEquals(315.0, result.headingDegrees, TOLERANCE)
    }

    @Test
    fun `one hundred eighty degree turn integrates correctly`() {
        val result = propagator.propagate(state(), gyro(zRadiansPerSecond = PI.toFloat(), seconds = 1))

        assertEquals(180.0, result.headingDegrees, TOLERANCE)
    }

    @Test
    fun `constant gyro bias is removed before heading propagation`() {
        val result = propagator.propagate(
            state(headingDegrees = 10.0, gyroBiasRadiansPerSecond = 0.25),
            gyro(zRadiansPerSecond = 0.25f, seconds = 4)
        )

        assertEquals(10.0, result.headingDegrees, TOLERANCE)
    }

    private fun gyro(zRadiansPerSecond: Float, seconds: Long): GyroscopeSample = GyroscopeSample(
        xRadiansPerSecond = 0f,
        yRadiansPerSecond = 0f,
        zRadiansPerSecond = zRadiansPerSecond,
        timestamp = MonotonicTimestamp(seconds * NANOS_PER_SECOND)
    )

    private fun state(
        headingDegrees: Double = 0.0,
        gyroBiasRadiansPerSecond: Double = 0.0
    ): VehicleMotionState = VehicleMotionState(
        position = NavigationPosition(
            latitude = 0.0,
            longitude = 0.0,
            confidence = PositionConfidence.HIGH,
            estimatedAccuracyMeters = 1.0,
            source = PositionSource.GNSS,
            timestamp = MonotonicTimestamp(0)
        ),
        headingDegrees = headingDegrees,
        speedMetersPerSecond = 0.0,
        gyroBiasRadiansPerSecond = gyroBiasRadiansPerSecond,
        distanceMeters = 0.0,
        uncertaintyMeters = 1.0,
        timestamp = MonotonicTimestamp(0)
    )

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000L
        const val TOLERANCE = 0.0001
    }
}
