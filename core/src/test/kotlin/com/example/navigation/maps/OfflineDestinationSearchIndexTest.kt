package com.example.navigation.maps

import com.example.navigation.routing.GeoPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OfflineDestinationSearchIndexTest {
    private val packageVersion = RegionalPackageVersion("ua-kyiv", "map-1", "graph-1")
    private val records = listOf(
        DestinationIndexRecord("address-1", DestinationSourceType.ADDRESS, null, GeoPoint(50.4501, 30.5234), "1 Khreshchatyk Street", listOf("khreshchatyk", "1 khreshchatyk")),
        DestinationIndexRecord("place-1", DestinationSourceType.PLACE, "museum", GeoPoint(50.4520, 30.5140), "National Art Museum", listOf("national art museum", "art museum"))
    )
    private val index = OfflineDestinationSearchIndex.load(packageVersion,
        DestinationIndexMetadata("index-1", packageVersion, OfflineDestinationSearchIndex.checksum(records)), records).let { (it as DestinationIndexLoadResult.Ready).index }

    @Test fun `exact prefix and normalized address place searches retain exact coordinates`() {
        assertEquals("address-1", (index.search("1 Khreshchatyk") as DestinationSearchResult.Results).matches.single().stableId)
        assertEquals(GeoPoint(50.4501, 30.5234), (index.search("khre") as DestinationSearchResult.Results).matches.single().coordinate)
        assertEquals("place-1", (index.search("NATIONAL-ART") as DestinationSearchResult.Results).matches.single().stableId)
    }

    @Test fun `unavailable and invalid inputs are explicit`() {
        assertEquals(DestinationIndexLoadResult.NoRegionInstalled, OfflineDestinationSearchIndex.load(null, null, null))
        assertIs<DestinationIndexLoadResult.IndexUnavailable>(OfflineDestinationSearchIndex.load(packageVersion, null, null))
        assertIs<DestinationIndexLoadResult.IndexUnavailable>(OfflineDestinationSearchIndex.load(packageVersion,
            DestinationIndexMetadata("index-1", packageVersion, "0".repeat(64)), records))
        assertEquals(DestinationSearchResult.QueryTooShort, index.search("a"))
        assertEquals(DestinationSearchResult.NoMatch, index.search("hospital"))
    }

    @Test fun `duplicate ids and incompatible package cannot load`() {
        val duplicate = records + records.first()
        assertIs<DestinationIndexLoadResult.IndexUnavailable>(OfflineDestinationSearchIndex.load(packageVersion,
            DestinationIndexMetadata("index-1", packageVersion, OfflineDestinationSearchIndex.checksum(duplicate)), duplicate))
        assertIs<DestinationIndexLoadResult.IndexUnavailable>(OfflineDestinationSearchIndex.load(packageVersion,
            DestinationIndexMetadata("index-1", RegionalPackageVersion("other", "map-1", "graph-1"), OfflineDestinationSearchIndex.checksum(records)), records))
    }
}
