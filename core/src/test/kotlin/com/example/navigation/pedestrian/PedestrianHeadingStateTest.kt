package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PedestrianHeadingStateTest {
    @Test fun `device orientation and movement direction are independently represented`() {
        val timestamp = MonotonicTimestamp(100)
        val state = PedestrianHeadingState(
            deviceOrientation = DeviceOrientation(90.0, 5.0, timestamp),
            movementDirection = PedestrianMovementDirection(180.0, 30.0, timestamp),
            timestamp = timestamp
        )

        assertEquals(90.0, state.deviceOrientation.headingDegrees)
        assertEquals(180.0, state.movementDirection.headingDegrees)
    }

    @Test fun `unknown heading remains explicit with uncertainty`() {
        val timestamp = MonotonicTimestamp(100)
        val state = PedestrianHeadingState(
            DeviceOrientation(null, 180.0, timestamp),
            PedestrianMovementDirection(null, 180.0, timestamp),
            timestamp
        )

        assertEquals(null, state.deviceOrientation.headingDegrees)
        assertEquals(null, state.movementDirection.headingDegrees)
    }

    @Test fun `headings validate range and state time ordering`() {
        val timestamp = MonotonicTimestamp(100)
        assertFailsWith<IllegalArgumentException> { DeviceOrientation(360.0, 1.0, timestamp) }
        assertFailsWith<IllegalArgumentException> { PedestrianMovementDirection(-1.0, 1.0, timestamp) }
        assertFailsWith<IllegalArgumentException> {
            PedestrianHeadingState(DeviceOrientation(0.0, 1.0, MonotonicTimestamp(101)),
                PedestrianMovementDirection(0.0, 1.0, timestamp), timestamp)
        }
    }
}
