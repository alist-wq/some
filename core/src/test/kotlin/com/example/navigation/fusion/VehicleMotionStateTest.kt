package com.example.navigation.fusion

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VehicleMotionStateTest {
    @Test
    fun preservesVehicleStateFields() {
        val position = NavigationPosition(50.0, 30.0, PositionConfidence.MEDIUM, 12.0, PositionSource.VEHICLE_DEAD_RECKONING, MonotonicTimestamp(100L))
        val state = VehicleMotionState(position, 90.0, 13.5, 0.002, 250.0, 18.0, MonotonicTimestamp(120L))

        assertEquals(position, state.position)
        assertEquals(90.0, state.headingDegrees)
        assertEquals(13.5, state.speedMetersPerSecond)
        assertEquals(0.002, state.gyroBiasRadiansPerSecond)
        assertEquals(250.0, state.distanceMeters)
        assertEquals(18.0, state.uncertaintyMeters)
        assertEquals(MonotonicTimestamp(120L), state.timestamp)
    }

    @Test
    fun rejectsInvalidMotionValues() {
        val position = NavigationPosition(50.0, 30.0, PositionConfidence.LOW, 20.0, PositionSource.VEHICLE_DEAD_RECKONING, MonotonicTimestamp(1L))
        assertFailsWith<IllegalArgumentException> { VehicleMotionState(position, 360.0, 0.0, 0.0, 0.0, 1.0, MonotonicTimestamp(1L)) }
        assertFailsWith<IllegalArgumentException> { VehicleMotionState(position, 0.0, -1.0, 0.0, 0.0, 1.0, MonotonicTimestamp(1L)) }
        assertFailsWith<IllegalArgumentException> { VehicleMotionState(position, 0.0, 0.0, 0.0, -1.0, 1.0, MonotonicTimestamp(1L)) }
        assertFailsWith<IllegalArgumentException> { VehicleMotionState(position, 0.0, 0.0, 0.0, 0.0, -1.0, MonotonicTimestamp(1L)) }
    }
}
