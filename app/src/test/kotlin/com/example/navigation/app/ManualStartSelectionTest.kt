package com.example.navigation.app

import com.example.navigation.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ManualStartSelectionTest {
    private val point = GeoPoint(50.0, 30.0)

    @Test fun `selection lifecycle retains an explicit manual source`() {
        val selecting = ManualStartSelectionReducer.enter(ManualStartSelectionState())
        val selected = ManualStartSelectionReducer.select(selecting, point)
        val confirmed = ManualStartSelectionReducer.confirm(selected)

        assertEquals(ManualStartSelectionPhase.SELECTING, selecting.phase)
        assertEquals(ManualStartSelectionPhase.SELECTED, selected.phase)
        assertEquals(point, selected.selectedStart?.point)
        assertEquals(ManualStartPointSource.MANUAL_SELECTION, selected.selectedStart?.source)
        assertEquals(ManualStartSelectionPhase.CONFIRMED, confirmed.phase)
    }

    @Test fun `cancel and reselection clear a pending or confirmed point`() {
        val selected = ManualStartSelectionReducer.select(ManualStartSelectionReducer.enter(ManualStartSelectionState()), point)
        val reselection = ManualStartSelectionReducer.reselect(ManualStartSelectionReducer.confirm(selected))

        assertEquals(ManualStartSelectionPhase.IDLE, ManualStartSelectionReducer.cancel().phase)
        assertEquals(ManualStartSelectionPhase.SELECTING, reselection.phase)
        assertNull(reselection.selectedStart)
    }

    @Test fun `missing selection cannot be confirmed`() {
        assertThrows(IllegalArgumentException::class.java) {
            ManualStartSelectionReducer.confirm(ManualStartSelectionReducer.enter(ManualStartSelectionState()))
        }
    }
}
