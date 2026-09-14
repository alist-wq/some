package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PedestrianRouteEntryTest {
    @Test fun `uses an active manual origin even before a live estimate exists`() {
        val manual = ManualOriginTrackingState(true, ManualStartPoint(GeoPoint(50.0, 36.0)),
            NavigationPosition(50.0, 36.0, PositionConfidence.LOW, 100.0, PositionSource.MANUAL_START, MonotonicTimestamp(1)))
        assertEquals(PositionSource.MANUAL_START, pedestrianRoutePlanningOrigin(manual, null)?.source)
    }

    @Test fun `does not plan from a lost manual or low confidence live position`() {
        val lost = ManualOriginTrackingState(true, position = NavigationPosition(50.0, 36.0, PositionConfidence.LOST, 200.0, PositionSource.MANUAL_START, MonotonicTimestamp(1)))
        val low = NavigationPosition(50.0, 36.0, PositionConfidence.LOW, 50.0, PositionSource.GNSS, MonotonicTimestamp(2))
        assertNull(pedestrianRoutePlanningOrigin(lost, low))
    }
}
