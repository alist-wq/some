package com.example.navigation.maps

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DestinationIndexPackageCatalogTest {
    @Test fun productionReleaseIsImmutableAndComplete() {
        val packageInfo = assertNotNull(ProductionDestinationIndexPackageCatalog.catalog.packageFor(ProductionDestinationIndexPackageCatalog.REGION_ID))
        assertEquals("2026.09.13-1", packageInfo.packageVersion)
        assertEquals("openfreemap-2026-09", packageInfo.mapCompatibilityVersion)
        assertEquals(setOf("destination-index.jsonl", "manifest.json", "manifest.json.sig", "renav-destination-index-ed25519.pub"), packageInfo.artifacts.map { it.name }.toSet())
        assertTrue(packageInfo.artifacts.all { artifact ->
            artifact.url.startsWith("https://downloads.renav.me/renav/destination-index/ua-kharkiv-pedestrian/2026.09.13-1/") &&
                !artifact.url.contains("latest") && !artifact.url.contains("github.com")
        })
    }
}
