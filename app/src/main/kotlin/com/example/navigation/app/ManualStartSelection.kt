package com.example.navigation.app

import com.example.navigation.routing.GeoPoint

enum class ManualStartSelectionPhase { IDLE, SELECTING, SELECTED, CONFIRMED }

enum class ManualStartPointSource { MANUAL_SELECTION }

data class ManualStartPoint(
    val point: GeoPoint,
    val source: ManualStartPointSource = ManualStartPointSource.MANUAL_SELECTION
)

data class ManualStartSelectionState(
    val phase: ManualStartSelectionPhase = ManualStartSelectionPhase.IDLE,
    val selectedStart: ManualStartPoint? = null
) {
    init {
        require((phase == ManualStartSelectionPhase.IDLE || phase == ManualStartSelectionPhase.SELECTING) == (selectedStart == null))
    }
}

/** Explicit, UI-independent selection lifecycle; confirmation never creates a measured position. */
object ManualStartSelectionReducer {
    fun enter(state: ManualStartSelectionState): ManualStartSelectionState =
        state.copy(phase = ManualStartSelectionPhase.SELECTING, selectedStart = null)

    fun select(state: ManualStartSelectionState, point: GeoPoint): ManualStartSelectionState {
        require(state.phase == ManualStartSelectionPhase.SELECTING || state.phase == ManualStartSelectionPhase.SELECTED)
        return ManualStartSelectionState(ManualStartSelectionPhase.SELECTED, ManualStartPoint(point))
    }

    fun confirm(state: ManualStartSelectionState): ManualStartSelectionState {
        require(state.phase == ManualStartSelectionPhase.SELECTED && state.selectedStart != null)
        return state.copy(phase = ManualStartSelectionPhase.CONFIRMED)
    }

    fun cancel(): ManualStartSelectionState = ManualStartSelectionState()

    fun reselect(state: ManualStartSelectionState): ManualStartSelectionState {
        require(state.phase == ManualStartSelectionPhase.SELECTED || state.phase == ManualStartSelectionPhase.CONFIRMED)
        return ManualStartSelectionState(ManualStartSelectionPhase.SELECTING)
    }
}
