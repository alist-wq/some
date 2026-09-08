package com.example.navigation.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapDataVersionMetadataTest {
    @Test
    fun metadataCodecPreservesAllRecordedFields() {
        val expected = MapDataVersionMetadata("UA-KH-014", 1234L, "osm-2026-09", 9876L, "graph-4")
        assertEquals(expected, MapDataVersionMetadataStore.decode(MapDataVersionMetadataStore.encode(expected)))
    }

    @Test
    fun malformedMetadataIsIgnored() {
        assertNull(MapDataVersionMetadataStore.decode("not metadata"))
    }
}
