package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CelesTrakSpacecraftReferencesTest {
    private val tle = """GPS BIIR-5 (PRN 22)
1 25544U 98067A   26045.79523799  .00007779  00000+0  15107-3 0  9994
2 25544  51.6315 185.5279 0011056  98.8248 261.3993 15.48601910552787
"""

    @Test fun `parser maps PRN and rejects malformed groups`() {
        val catalog = CelesTrakTleParser.parse("noise\n$tle\nbroken", 1_000)
        assertEquals(1, catalog.entries.size)
        assertEquals(GnssConstellation.GPS, catalog.entries.single().constellation)
        assertEquals(22, catalog.entries.single().svid)
        assertEquals(25544, catalog.entries.single().noradId)
    }

    @Test fun `parser maps Galileo and BeiDou receiver identifiers without inventing GLONASS slots`() {
        val body = tle.replace("GPS BIIR-5 (PRN 22)", "GSAT0201 (GALILEO 5)") +
            tle.replace("GPS BIIR-5 (PRN 22)", "BEIDOU-2 IGSO-1 (C06)") +
            tle.replace("GPS BIIR-5 (PRN 22)", "COSMOS 2433 (720)")
        val entries = CelesTrakTleParser.parse(body, 1_000).entries
        assertTrue(entries.any { it.constellation == GnssConstellation.GALILEO && it.svid == 5 })
        assertTrue(entries.any { it.constellation == GnssConstellation.BEIDOU && it.svid == 6 })
        assertTrue(entries.none { it.constellation == GnssConstellation.GLONASS })
    }

    @Test fun `cache validates round trips and stale catalog produces no geometry`() {
        val path = Files.createTempDirectory("tle-cache").resolve("gnss.cache")
        val cache = TleCatalogCache(path)
        cache.write(1_000, tle)
        assertEquals(1_000, cache.read()?.retrievedAtEpochMillis)
        val stale = CachedTleSpacecraftReferenceProvider({ cache.read() }, { 10_000 }, maximumAgeMillis = 1_000)
        stale.updateObserver(ObserverFix(MonotonicTimestamp(1), 10_000, 50.45, 30.52, 180.0))
        assertNull(stale.referenceAt(MonotonicTimestamp(1), GnssConstellation.GPS, 22))
    }

    @Test fun `SGP4 produces labelled finite reference geometry deterministically`() {
        val catalog = CelesTrakTleParser.parse(tle, 1_000)
        val provider = CachedTleSpacecraftReferenceProvider({ catalog }, { 2_000 }, maximumAgeMillis = 10_000)
        provider.updateObserver(ObserverFix(MonotonicTimestamp(1), 1_772_000_000_000, 50.45, 30.52, 180.0))
        val first = provider.referenceAt(MonotonicTimestamp(2), GnssConstellation.GPS, 22)
        val second = provider.referenceAt(MonotonicTimestamp(2), GnssConstellation.GPS, 22)
        assertNotNull(first)
        assertEquals(first, second)
        assertTrue(first.expectedAzimuthDegrees!!.isFinite())
        assertTrue(first.expectedElevationDegrees!!.isFinite())
        assertEquals(CachedTleSpacecraftReferenceProvider.SOURCE, first.source)
    }
}
