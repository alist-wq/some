package com.example.navigation.fusion

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GnssCorrectionApplierTest {
    @Test fun `trusted accurate GNSS strongly corrects uncertain DR state`() {
        val result = GnssCorrectionApplier().apply(state(uncertainty = 100.0), gnss(latitude = 10.0, accuracy = 5f), 1.0)

        assertTrue(result.applied)
        assertTrue(result.correctionWeight > 0.8)
        assertTrue(result.state.position.latitude > 8.0)
        assertEquals(PositionSource.HYBRID, result.state.position.source)
        assertTrue(result.state.uncertaintyMeters < 100.0)
    }

    @Test fun `low trust reduces correction weight`() {
        val applier = GnssCorrectionApplier()
        val trusted = applier.apply(state(uncertainty = 100.0), gnss(latitude = 10.0, accuracy = 5f), 1.0)
        val lowTrust = applier.apply(state(uncertainty = 100.0), gnss(latitude = 10.0, accuracy = 5f), 0.2)

        assertTrue(lowTrust.correctionWeight < trusted.correctionWeight)
        assertTrue(lowTrust.state.position.latitude < trusted.state.position.latitude)
    }

    @Test fun `poor GNSS accuracy and low DR uncertainty reduce correction weight`() {
        val applier = GnssCorrectionApplier()
        val favorable = applier.apply(state(uncertainty = 100.0), gnss(latitude = 10.0, accuracy = 5f), 1.0)
        val unfavorable = applier.apply(state(uncertainty = 2.0), gnss(latitude = 10.0, accuracy = 100f), 1.0)

        assertTrue(unfavorable.correctionWeight < favorable.correctionWeight)
    }

    @Test fun `missing GNSS accuracy explicitly skips correction`() {
        val original = state(uncertainty = 50.0)
        val result = GnssCorrectionApplier().apply(original, gnss(latitude = 10.0, accuracy = null), 1.0)

        assertFalse(result.applied)
        assertEquals(original, result.state)
        assertEquals(0.0, result.correctionWeight)
    }

    private fun state(uncertainty: Double) = VehicleMotionState(
        position = NavigationPosition(0.0, 0.0, PositionConfidence.MEDIUM, uncertainty, PositionSource.VEHICLE_DEAD_RECKONING, MonotonicTimestamp(0)),
        headingDegrees = 0.0,
        speedMetersPerSecond = 10.0,
        gyroBiasRadiansPerSecond = 0.0,
        distanceMeters = 10.0,
        uncertaintyMeters = uncertainty,
        timestamp = MonotonicTimestamp(0)
    )

    private fun gnss(latitude: Double, accuracy: Float?) = GnssSample(
        latitude, 0.0, null, accuracy, null, null, null, null, null, "gps", MonotonicTimestamp(1), null
    )
}
