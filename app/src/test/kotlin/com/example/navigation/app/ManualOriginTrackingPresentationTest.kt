package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssTrustState
import com.example.navigation.navigation.NavigationPosition
import com.example.navigation.navigation.PositionConfidence
import com.example.navigation.navigation.PositionSource
import com.example.navigation.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualOriginTrackingPresentationTest {
    @Test fun `manual origin is explicitly distinct from a live position`() {
        val state = ManualOriginTracker().start(confirmed(GeoPoint(50.0, 30.0)), MonotonicTimestamp(1))
        val presentation = ManualOriginTrackingPresentationFactory.create(state, GnssTrustState.TRUSTED)
        assertEquals("MANUAL ORIGIN ACTIVE", presentation.statusLabel)
        assertEquals("MANUAL START", presentation.sourceLabel)
        assertTrue(presentation.detail.contains("not a live measured position"))
    }

    @Test fun `subsequent estimate and lost measurement stay explicit`() {
        val tracker = ManualOriginTracker()
        tracker.start(confirmed(GeoPoint(50.0, 30.0)), MonotonicTimestamp(1))
        tracker.observe(ManualTrackingObservation.Position(
            NavigationPosition(50.001, 30.001, PositionConfidence.HIGH, 5.0, PositionSource.GNSS, MonotonicTimestamp(2))
        ))
        assertTrue(ManualOriginTrackingPresentationFactory.create(tracker.state, GnssTrustState.TRUSTED).detail.contains("subsequent navigation estimate"))
        tracker.observe(ManualTrackingObservation.Unavailable(MonotonicTimestamp(3)))
        val lost = ManualOriginTrackingPresentationFactory.create(tracker.state, GnssTrustState.REJECTED)
        assertEquals("LOST", lost.confidenceLabel)
        assertTrue(lost.detail.contains("Measurements are unavailable"))
    }

    private fun confirmed(point: GeoPoint): ManualStartSelectionState = ManualStartSelectionReducer.confirm(
        ManualStartSelectionReducer.select(ManualStartSelectionReducer.enter(ManualStartSelectionState()), point)
    )
}
