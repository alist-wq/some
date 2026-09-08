package com.example.navigation.app

import com.example.navigation.maps.DestinationIndexRecord
import com.example.navigation.maps.DestinationSearchResult
import com.example.navigation.maps.OfflineDestinationSearchIndex
import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.routing.GeoPoint

enum class DestinationSearchPhase { IDLE, SEARCHING, RESULTS, SELECTED }

data class ProposedDestination(
    val stableId: String,
    val label: String,
    val coordinate: GeoPoint,
    val sourcePackage: RegionalPackageVersion
)

data class DestinationSearchSelectionState(
    val phase: DestinationSearchPhase = DestinationSearchPhase.IDLE,
    val query: String = "",
    val searchResult: DestinationSearchResult? = null,
    val proposedDestination: ProposedDestination? = null
)

object DestinationSearchSelectionReducer {
    fun open(state: DestinationSearchSelectionState) = state.copy(phase = DestinationSearchPhase.SEARCHING, query = "", searchResult = null)

    fun query(state: DestinationSearchSelectionState, index: OfflineDestinationSearchIndex?, value: String): DestinationSearchSelectionState {
        val result = index?.search(value) ?: DestinationSearchResult.NoRegionInstalled
        val phase = if (result is DestinationSearchResult.Results) DestinationSearchPhase.RESULTS else DestinationSearchPhase.SEARCHING
        return state.copy(phase = phase, query = value, searchResult = result)
    }

    fun select(state: DestinationSearchSelectionState, record: DestinationIndexRecord): DestinationSearchSelectionState {
        val packageVersion = (state.searchResult as? DestinationSearchResult.Results)?.packageVersion
            ?: throw IllegalArgumentException("A destination can only be selected from current search results")
        require((state.searchResult as DestinationSearchResult.Results).matches.any { it.stableId == record.stableId })
        return state.copy(
            phase = DestinationSearchPhase.SELECTED,
            proposedDestination = ProposedDestination(record.stableId, record.displayLabel, record.coordinate, packageVersion)
        )
    }

    fun cancel(state: DestinationSearchSelectionState) = state.copy(phase = DestinationSearchPhase.IDLE, query = "", searchResult = null, proposedDestination = null)
}
