package com.example.navigation.app

import com.example.navigation.maps.DestinationIndexLoadResult
import com.example.navigation.maps.DestinationIndexMetadata
import com.example.navigation.maps.DestinationIndexRecord
import com.example.navigation.maps.DestinationSourceType
import com.example.navigation.maps.OfflineDestinationSearchIndex
import com.example.navigation.maps.RegionalPackageVersion
import com.example.navigation.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationSearchSelectionTest {
    private val packageVersion = RegionalPackageVersion("fixture", "map-1", "graph-1")
    private val record = DestinationIndexRecord("place", DestinationSourceType.PLACE, "museum", GeoPoint(50.0, 30.0), "Art Museum", listOf("art museum"))
    private val index = OfflineDestinationSearchIndex.load(packageVersion,
        DestinationIndexMetadata("index-1", packageVersion, OfflineDestinationSearchIndex.checksum(listOf(record))), listOf(record))
        .let { (it as DestinationIndexLoadResult.Ready).index }

    @Test fun `query selection replacement and cancellation never create an active route`() {
        val queried = DestinationSearchSelectionReducer.query(DestinationSearchSelectionReducer.open(DestinationSearchSelectionState()), index, "art")
        val selected = DestinationSearchSelectionReducer.select(queried, record)
        assertEquals(DestinationSearchPhase.SELECTED, selected.phase)
        assertEquals(GeoPoint(50.0, 30.0), selected.proposedDestination?.coordinate)
        val cancelled = DestinationSearchSelectionReducer.cancel(selected)
        assertEquals(DestinationSearchPhase.IDLE, cancelled.phase)
        assertNull(cancelled.proposedDestination)
    }

    @Test fun `no installed index is an explicit search state`() {
        val state = DestinationSearchSelectionReducer.query(DestinationSearchSelectionReducer.open(DestinationSearchSelectionState()), null, "art")
        assertEquals(com.example.navigation.maps.DestinationSearchResult.NoRegionInstalled, state.searchResult)
        assertEquals(DestinationSearchPhase.SEARCHING, state.phase)
    }
}
