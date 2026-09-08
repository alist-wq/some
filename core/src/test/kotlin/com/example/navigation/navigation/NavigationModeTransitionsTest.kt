package com.example.navigation.navigation

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class NavigationModeTransitionsTest {
    @Test
    fun `vehicle transition requires stop disconnect steps and pedestrian evidence`() {
        val detector = VehicleToPedestrianTransitionDetector()
        val position = position()

        assertEquals(ModeTransitionDecision.NO_CHANGE, detector.evaluate(vehicleObservation(position, 1, true, 0.0, 0)).decision)
        assertEquals(ModeTransitionDecision.NO_CHANGE, detector.evaluate(vehicleObservation(position, 2, false, null, 0)).decision)
        val result = detector.evaluate(vehicleObservation(position, 3, false, null, 3))

        assertEquals(ModeTransitionDecision.TRANSITION, result.decision)
        assertEquals(NavigationMode.PEDESTRIAN, result.transition?.toMode)
        assertSame(position, result.transition?.initialPosition)
    }

    @Test
    fun `bluetooth disconnect alone never triggers pedestrian transition`() {
        val result = VehicleToPedestrianTransitionDetector().evaluate(
            vehicleObservation(position(), 1, obdConnected = false, speed = null, steps = 0)
        )

        assertEquals(ModeTransitionDecision.NO_CHANGE, result.decision)
        assertNull(result.transition)
        assertEquals(setOf(ModeTransitionEvidence.OBD_DISCONNECTED, ModeTransitionEvidence.PEDESTRIAN_CLASSIFICATION), result.evidence)
    }

    @Test
    fun `pedestrian transition requires moving obd evidence and user confirmation`() {
        val detector = PedestrianToVehicleTransitionDetector()
        val position = position()

        val confirmation = detector.evaluate(pedestrianObservation(position, 1, true, 8.0, false, 0.8))
        assertEquals(ModeTransitionDecision.USER_CONFIRMATION_REQUIRED, confirmation.decision)

        val result = detector.evaluate(pedestrianObservation(position, 2, true, 8.0, true, 0.8))
        assertEquals(ModeTransitionDecision.TRANSITION, result.decision)
        assertEquals(NavigationMode.VEHICLE, result.transition?.toMode)
        assertSame(position, result.transition?.initialPosition)
    }

    @Test
    fun `walking stop public transit and accidental reconnect do not trigger vehicle transition`() {
        val detector = PedestrianToVehicleTransitionDetector()
        assertEquals(ModeTransitionDecision.NO_CHANGE,
            detector.evaluate(pedestrianObservation(position(), 1, false, null, false, 0.0)).decision)
        assertEquals(ModeTransitionDecision.NO_CHANGE,
            detector.evaluate(pedestrianObservation(position(), 2, false, null, true, 0.9)).decision)
        assertEquals(ModeTransitionDecision.NO_CHANGE,
            detector.evaluate(pedestrianObservation(position(), 3, true, 0.0, true, 0.9)).decision)
    }

    private fun vehicleObservation(
        position: NavigationPosition,
        second: Long,
        obdConnected: Boolean,
        speed: Double?,
        steps: Int
    ) = VehicleToPedestrianObservation(
        currentPosition = position,
        obdConnected = obdConnected,
        obdSpeedMetersPerSecond = speed,
        consecutiveWalkingSteps = steps,
        motion = motion(second, vehicle = 0.05, pedestrian = 0.8, stationary = 0.1, unknown = 0.05)
    )

    private fun pedestrianObservation(
        position: NavigationPosition,
        second: Long,
        obdConnected: Boolean,
        speed: Double?,
        userConfirmed: Boolean,
        vehicleProbability: Double
    ) = PedestrianToVehicleObservation(
        currentPosition = position,
        obdConnected = obdConnected,
        obdSpeedMetersPerSecond = speed,
        userConfirmed = userConfirmed,
        motion = motion(second, vehicleProbability, pedestrian = 0.0, stationary = 1.0 - vehicleProbability, unknown = 0.0)
    )

    private fun motion(
        second: Long,
        vehicle: Double,
        pedestrian: Double,
        stationary: Double,
        unknown: Double
    ) = MotionClassification(vehicle, pedestrian, stationary, unknown, MonotonicTimestamp(second * 1_000_000_000L))

    private fun position() = NavigationPosition(
        latitude = 50.45,
        longitude = 30.52,
        confidence = PositionConfidence.MEDIUM,
        estimatedAccuracyMeters = 20.0,
        source = PositionSource.HYBRID,
        timestamp = MonotonicTimestamp(500_000_000L)
    )
}
