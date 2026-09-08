package com.example.navigation.app

import com.example.navigation.replay.PedestrianEventLabel
import com.example.navigation.replay.PedestrianPoseLabel
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class PedestrianFieldRecordingTest {
    @Test fun `setup requires route reference and consent but never OBD`() {
        assertFalse(PedestrianRecordingSetupState().canStart)
        assertFalse(PedestrianRecordingSetupState(routeReference = "route").canStart)
        assertTrue(PedestrianRecordingSetupState(routeReference = "route", consentConfirmed = true).canStart)
    }

    @Test fun `pose and event selections produce visible confirmations`() {
        val started = PedestrianRecordingPresentation(true, PedestrianPoseLabel.UNKNOWN)
        val posed = PedestrianRecordingReducer.pose(started, PedestrianPoseLabel.POCKET)
        val labelled = PedestrianRecordingReducer.event(posed, PedestrianEventLabel.TURN_LEFT)
        assertEquals(PedestrianPoseLabel.POCKET, labelled.currentPose)
        assertEquals(PedestrianEventLabel.TURN_LEFT, labelled.lastEvent)
        assertEquals("Event saved: TURN_LEFT", labelled.confirmation)
    }
}
