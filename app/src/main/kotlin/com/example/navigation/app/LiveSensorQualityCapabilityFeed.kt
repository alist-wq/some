package com.example.navigation.app

import android.hardware.SensorManager
import com.example.navigation.core.MonotonicTimestamp

/** Streams required to state whether live heading and motion observations are usable. */
enum class MotionSensorStream { GYROSCOPE, GAME_ROTATION_VECTOR, ACCELEROMETER }

/** Presentation health only; this is not an estimator input or calibration decision. */
enum class SensorObservationHealth { GOOD, POOR, UNKNOWN, STALE, UNAVAILABLE }

enum class SensorProviderLifecycle { HARDWARE_ABSENT, NOT_STARTED, ACTIVE, STOPPED, START_FAILED }

data class LiveSensorObservationState(
    val stream: MotionSensorStream,
    val lifecycle: SensorProviderLifecycle,
    val health: SensorObservationHealth,
    val lastObservationTimestamp: MonotonicTimestamp? = null,
    val lastReportedQualityTimestamp: MonotonicTimestamp? = null
)

data class LiveSensorQualityCapabilityFeed(
    val generatedAt: MonotonicTimestamp,
    val streams: Map<MotionSensorStream, LiveSensorObservationState>,
    val headingQuality: HeadingQualityState
) {
    fun stream(stream: MotionSensorStream): LiveSensorObservationState = requireNotNull(streams[stream])
}

/**
 * Tracks provider lifecycle and supplied Android observation facts for presentation. It deliberately
 * does not estimate a heading, recalibrate sensors, or write into navigation/trust state.
 */
class LiveSensorQualityCapabilityTracker(
    capabilities: SensorCapabilityReport,
    private val staleAfterNanos: Long = DEFAULT_STALE_AFTER_NANOS
) {
    private data class MutableStreamState(
        var lifecycle: SensorProviderLifecycle,
        var lastObservationTimestamp: MonotonicTimestamp? = null,
        var reportedHealth: SensorObservationHealth = SensorObservationHealth.UNKNOWN,
        var lastReportedQualityTimestamp: MonotonicTimestamp? = null
    )

    private val states = linkedMapOf(
        MotionSensorStream.GYROSCOPE to MutableStreamState(if (capabilities.gyroscope) SensorProviderLifecycle.NOT_STARTED else SensorProviderLifecycle.HARDWARE_ABSENT),
        MotionSensorStream.GAME_ROTATION_VECTOR to MutableStreamState(if (capabilities.gameRotationVector) SensorProviderLifecycle.NOT_STARTED else SensorProviderLifecycle.HARDWARE_ABSENT),
        MotionSensorStream.ACCELEROMETER to MutableStreamState(if (capabilities.accelerometer) SensorProviderLifecycle.NOT_STARTED else SensorProviderLifecycle.HARDWARE_ABSENT)
    )

    init { require(staleAfterNanos > 0L) }

    fun onProvidersStarted(result: LiveProviderStartResult) {
        updateStart(MotionSensorStream.GYROSCOPE, result.gyroscope)
        updateStart(MotionSensorStream.GAME_ROTATION_VECTOR, result.gameRotationVector)
        updateStart(MotionSensorStream.ACCELEROMETER, result.accelerometer)
    }

    fun onProvidersStopped() {
        states.values.filter { it.lifecycle == SensorProviderLifecycle.ACTIVE }.forEach {
            it.lifecycle = SensorProviderLifecycle.STOPPED
        }
    }

    fun onObservation(stream: MotionSensorStream, timestamp: MonotonicTimestamp) {
        val state = state(stream)
        if (state.lifecycle != SensorProviderLifecycle.ACTIVE) return
        if (state.lastObservationTimestamp == null || timestamp >= state.lastObservationTimestamp!!) {
            state.lastObservationTimestamp = timestamp
        }
    }

    /** Android reports only an accuracy class; LOW and UNRELIABLE remain explicitly poor. */
    fun onReportedAccuracy(stream: MotionSensorStream, accuracy: Int, timestamp: MonotonicTimestamp) {
        val state = state(stream)
        if (state.lifecycle != SensorProviderLifecycle.ACTIVE) return
        state.reportedHealth = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM, SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorObservationHealth.GOOD
            SensorManager.SENSOR_STATUS_UNRELIABLE, SensorManager.SENSOR_STATUS_ACCURACY_LOW -> SensorObservationHealth.POOR
            else -> SensorObservationHealth.UNKNOWN
        }
        state.lastReportedQualityTimestamp = timestamp
    }

    fun snapshot(now: MonotonicTimestamp): LiveSensorQualityCapabilityFeed {
        val observations = states.mapValues { (stream, state) ->
            LiveSensorObservationState(
                stream = stream,
                lifecycle = state.lifecycle,
                health = health(state, now),
                lastObservationTimestamp = state.lastObservationTimestamp,
                lastReportedQualityTimestamp = state.lastReportedQualityTimestamp
            )
        }
        val gyro = requireNotNull(observations[MotionSensorStream.GYROSCOPE])
        val rotation = requireNotNull(observations[MotionSensorStream.GAME_ROTATION_VECTOR])
        return LiveSensorQualityCapabilityFeed(now, observations, headingQuality(gyro.health, rotation.health))
    }

    private fun updateStart(stream: MotionSensorStream, started: Boolean) {
        val state = state(stream)
        if (state.lifecycle == SensorProviderLifecycle.HARDWARE_ABSENT) return
        state.lifecycle = if (started) SensorProviderLifecycle.ACTIVE else SensorProviderLifecycle.START_FAILED
    }

    private fun health(state: MutableStreamState, now: MonotonicTimestamp): SensorObservationHealth = when (state.lifecycle) {
        SensorProviderLifecycle.HARDWARE_ABSENT, SensorProviderLifecycle.START_FAILED -> SensorObservationHealth.UNAVAILABLE
        SensorProviderLifecycle.NOT_STARTED, SensorProviderLifecycle.STOPPED -> SensorObservationHealth.UNKNOWN
        SensorProviderLifecycle.ACTIVE -> when {
            state.lastObservationTimestamp == null -> SensorObservationHealth.UNKNOWN
            now.differenceNanos(state.lastObservationTimestamp!!) > staleAfterNanos -> SensorObservationHealth.STALE
            else -> state.reportedHealth
        }
    }

    private fun headingQuality(gyro: SensorObservationHealth, rotation: SensorObservationHealth): HeadingQualityState = when {
        SensorObservationHealth.UNAVAILABLE in setOf(gyro, rotation) -> HeadingQualityState.UNAVAILABLE
        SensorObservationHealth.STALE in setOf(gyro, rotation) -> HeadingQualityState.STALE
        SensorObservationHealth.POOR in setOf(gyro, rotation) -> HeadingQualityState.POOR
        gyro == SensorObservationHealth.GOOD && rotation == SensorObservationHealth.GOOD -> HeadingQualityState.GOOD
        else -> HeadingQualityState.UNKNOWN
    }

    private fun state(stream: MotionSensorStream): MutableStreamState = requireNotNull(states[stream])

    private companion object { const val DEFAULT_STALE_AFTER_NANOS = 3_000_000_000L }
}
