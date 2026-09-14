package com.example.navigation.app

import android.hardware.SensorManager
import com.example.navigation.core.MonotonicTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSensorQualityCapabilityFeedTest {
    @Test fun `hardware absence and provider start failure remain unavailable`() {
        val absent = LiveSensorQualityCapabilityTracker(capabilities(gyro = false))
        assertEquals(SensorObservationHealth.UNAVAILABLE, absent.snapshot(time(0)).stream(MotionSensorStream.GYROSCOPE).health)

        val failed = LiveSensorQualityCapabilityTracker(capabilities())
        failed.onProvidersStarted(startResult(gyro = false))
        assertEquals(SensorObservationHealth.UNAVAILABLE, failed.snapshot(time(0)).stream(MotionSensorStream.GYROSCOPE).health)
    }

    @Test fun `started provider without a callback remains unknown rather than healthy`() {
        val tracker = LiveSensorQualityCapabilityTracker(capabilities())
        tracker.onProvidersStarted(startResult())
        val feed = tracker.snapshot(time(1))

        assertEquals(SensorProviderLifecycle.ACTIVE, feed.stream(MotionSensorStream.GYROSCOPE).lifecycle)
        assertEquals(SensorObservationHealth.UNKNOWN, feed.stream(MotionSensorStream.GYROSCOPE).health)
        assertEquals(HeadingQualityState.UNKNOWN, feed.headingQuality)
    }

    @Test fun `reported good quality needs both heading streams and becomes stale after callback loss`() {
        val tracker = LiveSensorQualityCapabilityTracker(capabilities(), staleAfterNanos = 10L)
        tracker.onProvidersStarted(startResult())
        listOf(MotionSensorStream.GYROSCOPE, MotionSensorStream.GAME_ROTATION_VECTOR).forEach { stream ->
            tracker.onObservation(stream, time(5))
            tracker.onReportedAccuracy(stream, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, time(5))
        }
        assertEquals(HeadingQualityState.GOOD, tracker.snapshot(time(10)).headingQuality)

        val stale = tracker.snapshot(time(16))
        assertEquals(SensorObservationHealth.STALE, stale.stream(MotionSensorStream.GYROSCOPE).health)
        assertEquals(HeadingQualityState.STALE, stale.headingQuality)
    }

    @Test fun `explicit poor observation and lifecycle stop remain distinguishable`() {
        val tracker = LiveSensorQualityCapabilityTracker(capabilities())
        tracker.onProvidersStarted(startResult())
        listOf(MotionSensorStream.GYROSCOPE, MotionSensorStream.GAME_ROTATION_VECTOR).forEach { stream ->
            tracker.onObservation(stream, time(1))
            tracker.onReportedAccuracy(stream, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, time(1))
        }
        tracker.onReportedAccuracy(MotionSensorStream.GYROSCOPE, SensorManager.SENSOR_STATUS_ACCURACY_LOW, time(2))
        assertEquals(HeadingQualityState.POOR, tracker.snapshot(time(2)).headingQuality)

        tracker.onProvidersStopped()
        val stopped = tracker.snapshot(time(3))
        assertEquals(SensorProviderLifecycle.STOPPED, stopped.stream(MotionSensorStream.GYROSCOPE).lifecycle)
        assertEquals(SensorObservationHealth.UNKNOWN, stopped.stream(MotionSensorStream.GYROSCOPE).health)
    }

    @Test fun `live quality drives interruption presentation without mutating navigation state`() {
        val tracker = LiveSensorQualityCapabilityTracker(capabilities(), staleAfterNanos = 1L)
        tracker.onProvidersStarted(startResult())
        listOf(MotionSensorStream.GYROSCOPE, MotionSensorStream.GAME_ROTATION_VECTOR).forEach { stream ->
            tracker.onObservation(stream, time(1))
            tracker.onReportedAccuracy(stream, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, time(1))
        }
        val navigationBefore = NavigationMapState()
        val input = RuntimeInterruptionInput(
            permissions = RuntimePermissionCapability(RuntimePermissionState.GRANTED, RuntimePermissionState.GRANTED, false),
            sensorCapabilities = capabilities(),
            headingQuality = HeadingQualityState.GOOD,
            liveSensorQuality = tracker.snapshot(time(3))
        )

        val interruption = RuntimeInterruptionCapabilityFeedFactory.create(input)

        assertEquals(HeadingQualityState.STALE, interruption.headingQuality)
        assertTrue(interruption.interruptions.any { it.contains("stale") })
        assertEquals(navigationBefore, NavigationMapState())
        assertFalse(interruption.action(RuntimeRecoveryAction.PAUSE_GUIDANCE).available)
    }

    private fun time(nanos: Long) = MonotonicTimestamp(nanos)

    private fun capabilities(gyro: Boolean = true) = SensorCapabilityReport(
        gyroscope = gyro,
        accelerometer = true,
        gameRotationVector = true,
        magnetometer = true,
        barometer = false,
        rawGnss = true,
        agc = true,
        carrierFrequencies = true,
        androidVersion = 35,
        deviceModel = "fixture"
    )

    private fun startResult(gyro: Boolean = true) = LiveProviderStartResult(
        rawGnss = true,
        gnssStatus = true,
        gyroscope = gyro,
        accelerometer = true,
        gameRotationVector = true,
        barometer = false,
        obdConfigured = false
    )
}
