package com.example.navigation.maps

import com.example.navigation.routing.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionalMapCatalogTest {
    private val hash = "a".repeat(64)
    private val ukraine = packageInfo("ua", RegionalMapBounds(44.0, 22.0, 53.0, 41.0))

    @Test fun `local fixture resolves supported edge and unsupported positions deterministically`() {
        val catalog = RegionalMapCatalog("1", listOf(ukraine))
        assertEquals(RegionDiscoveryResult.Found(ukraine), RegionalMapResolver.resolve(catalog, true, GeoPoint(50.0, 30.0)))
        assertEquals(RegionDiscoveryResult.Found(ukraine), RegionalMapResolver.resolve(catalog, true, GeoPoint(44.0, 22.0)))
        assertEquals(RegionDiscoveryResult.UnsupportedRegion, RegionalMapResolver.resolve(catalog, true, GeoPoint(0.0, 0.0)))
    }

    @Test fun `permission location catalog and overlapping bounds have explicit outcomes`() {
        val overlap = packageInfo("overlap", RegionalMapBounds(49.0, 29.0, 51.0, 31.0))
        val catalog = RegionalMapCatalog("1", listOf(ukraine, overlap))
        assertEquals(RegionDiscoveryResult.PermissionDenied, RegionalMapResolver.resolve(catalog, false, GeoPoint(50.0, 30.0)))
        assertEquals(RegionDiscoveryResult.LocationUnavailable, RegionalMapResolver.resolve(catalog, true, null))
        assertEquals(RegionDiscoveryResult.CatalogUnavailable, RegionalMapResolver.resolve(null, true, GeoPoint(50.0, 30.0)))
        assertEquals(RegionDiscoveryResult.Ambiguous(listOf("overlap", "ua")), RegionalMapResolver.resolve(catalog, true, GeoPoint(50.0, 30.0)))
    }

    @Test fun `package readiness distinguishes missing downloading ready stale and corrupt`() {
        assertEquals(RegionalMapAvailability.NOT_DOWNLOADED, RegionalMapAvailabilityEvaluator.evaluate(ukraine, null))
        assertEquals(RegionalMapAvailability.DOWNLOADING, RegionalMapAvailabilityEvaluator.evaluate(ukraine, null, true))
        assertEquals(RegionalMapAvailability.READY, RegionalMapAvailabilityEvaluator.evaluate(ukraine, InstalledRegionalMapEvidence("v1", 10, hash, true)))
        assertEquals(RegionalMapAvailability.STALE, RegionalMapAvailabilityEvaluator.evaluate(ukraine, InstalledRegionalMapEvidence("old", 10, hash, true)))
        assertEquals(RegionalMapAvailability.CORRUPT, RegionalMapAvailabilityEvaluator.evaluate(ukraine, InstalledRegionalMapEvidence("v1", 10, "b".repeat(64), true)))
    }

    private fun packageInfo(id: String, bounds: RegionalMapBounds) = RegionalMapPackage(
        id, id.uppercase(), bounds, "asset://style.json", 1.0, 14.0, 1_000L, "v1", "g1", hash
    )
}
