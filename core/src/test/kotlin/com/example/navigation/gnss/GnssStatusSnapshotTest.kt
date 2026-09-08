package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals

class GnssStatusSnapshotTest {
    @Test
    fun snapshotRetainsSatelliteEvidenceAndTimestamp() {
        val satellite = GnssSatelliteStatus(GnssConstellation.GPS, 3, true, 180f, 45f, 38f)
        val snapshot = GnssStatusSnapshot(MonotonicTimestamp(12L), listOf(satellite))

        assertEquals(MonotonicTimestamp(12L), snapshot.timestamp)
        assertEquals(satellite, snapshot.satellites.single())
    }
}
