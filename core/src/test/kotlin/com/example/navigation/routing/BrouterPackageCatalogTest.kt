package com.example.navigation.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrouterPackageCatalogTest {
    private val descriptor = requireNotNull(ProductionBrouterPackageCatalog.catalog.packageFor(ProductionBrouterPackageCatalog.REGION_ID))

    @Test
    fun `production catalog pins every required artifact to the immutable replacement release`() {
        assertEquals("2026.09.10-1", descriptor.packageVersion)
        assertEquals(6, descriptor.artifacts.size)
        assertTrue(descriptor.artifacts.all { it.url.contains("brouter-ua-kharkiv-pedestrian-2026.09.10-1") })
        assertFalse(descriptor.artifacts.any { it.url.contains("latest") })
        assertEquals(descriptor.profileSha256, descriptor.artifacts.single { it.name == "renav-pedestrian.brf" }.sha256)
    }

    @Test
    fun `availability never reports removed corrupt stale or incompatible evidence as ready`() {
        val ready = InstalledBrouterPackageEvidence(descriptor.regionId, descriptor.packageVersion, descriptor.mapCompatibilityVersion, descriptor.brouterRevision, verified = true, active = true)
        assertEquals(BrouterPackageAvailability.READY, BrouterPackageAvailabilityEvaluator.evaluate(descriptor, ready))
        assertEquals(BrouterPackageAvailability.CORRUPT, BrouterPackageAvailabilityEvaluator.evaluate(descriptor, ready.copy(active = false)))
        assertEquals(BrouterPackageAvailability.CORRUPT, BrouterPackageAvailabilityEvaluator.evaluate(descriptor, ready.copy(verified = false)))
        assertEquals(BrouterPackageAvailability.STALE, BrouterPackageAvailabilityEvaluator.evaluate(descriptor, ready.copy(packageVersion = "old")))
        assertEquals(BrouterPackageAvailability.INCOMPATIBLE, BrouterPackageAvailabilityEvaluator.evaluate(descriptor, ready.copy(mapCompatibilityVersion = "other-map")))
        assertEquals(BrouterPackageAvailability.NOT_INSTALLED, BrouterPackageAvailabilityEvaluator.evaluate(descriptor, null))
    }

    @Test
    fun `download and recovery states remain explicitly unavailable`() {
        assertEquals(BrouterPackageAvailability.DOWNLOADING, BrouterPackageAvailabilityEvaluator.evaluate(descriptor, null, downloading = true))
        assertEquals(BrouterPackageAvailability.INTERRUPTED, BrouterPackageAvailabilityEvaluator.evaluate(descriptor, null, interrupted = true))
    }
}
