package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ManualOriginTrackerTest {
    @Test fun `confirmed manual point initializes exactly once with uncertainty`() {
        val tracker = ManualOriginTracker(ManualOriginTrackingConfig(80.0, 20.0))
        val state = tracker.start(confirmed(GeoPoint(50.0, 30.0)), MonotonicTimestamp(1))
        assertEquals(PositionSource.MANUAL_START, state.position?.source)
        assertEquals(PositionConfidence.LOW, state.position?.confidence)
        assertEquals(80.0, state.position?.estimatedAccuracyMeters)
        assertThrows(IllegalArgumentException::class.java) { tracker.start(confirmed(GeoPoint(51.0, 31.0)), MonotonicTimestamp(2)) }
    }

    @Test fun `synthetic replay transitions to trusted measurement then degrades when unavailable`() {
        val tracker = ManualOriginTracker(ManualOriginTrackingConfig(80.0, 20.0))
        val result = tracker.replay(confirmed(GeoPoint(50.0, 30.0)), MonotonicTimestamp(1), listOf(
            ManualTrackingObservation.Position(NavigationPosition(50.001, 30.001, PositionConfidence.HIGH, 5.0, PositionSource.GNSS, MonotonicTimestamp(2))),
            ManualTrackingObservation.Unavailable(MonotonicTimestamp(3))
        ))
        assertEquals(PositionSource.GNSS, result.position?.source)
        assertEquals(PositionConfidence.LOST, result.position?.confidence)
        assertEquals(25.0, result.position?.estimatedAccuracyMeters)
        assertEquals(1, result.acceptedMeasurementCount)
    }

    @Test fun `stop and restart replace origin and clear history`() {
        val tracker = ManualOriginTracker()
        tracker.start(confirmed(GeoPoint(50.0, 30.0)), MonotonicTimestamp(1))
        assertFalse(tracker.stop().active)
        val restarted = tracker.restart(confirmed(GeoPoint(49.0, 31.0)), MonotonicTimestamp(2))
        assertEquals(GeoPoint(49.0, 31.0), restarted.origin?.point)
        assertEquals(0, restarted.acceptedMeasurementCount)
    }

    private fun confirmed(point: GeoPoint): ManualStartSelectionState = ManualStartSelectionReducer.confirm(
        ManualStartSelectionReducer.select(ManualStartSelectionReducer.enter(ManualStartSelectionState()), point)
    )
}
