package com.example.navigation.app

import com.example.navigation.maps.RegionDiscoveryResult
import com.example.navigation.maps.RegionalMapBounds
import com.example.navigation.maps.RegionalMapDownloadPhase
import com.example.navigation.maps.RegionalMapDownloadState
import com.example.navigation.maps.RegionalMapPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegionalMapDownloadPresentationTest {
    @Test fun `permission denial offers permission and manual region recovery`() {
        val presentation = RegionalMapDownloadPresentationFactory.create(
            RegionDiscoveryResult.PermissionDenied, RegionalMapDownloadState()
        )
        assertEquals(RegionalMapPrimaryAction.REQUEST_LOCATION_PERMISSION, presentation.primaryAction)
        assertEquals(RegionalMapPrimaryAction.SELECT_REGION_MANUALLY, presentation.secondaryAction)
        assertFalse(presentation.ready)
    }

    @Test fun `download states never claim readiness before verification`() {
        val found = RegionDiscoveryResult.Found(packageInfo())
        val downloading = RegionalMapDownloadPresentationFactory.create(found, RegionalMapDownloadState(
            packageId = "ua", phase = RegionalMapDownloadPhase.DOWNLOADING, downloadedBytes = 2_000_000
        ))
        val verifying = RegionalMapDownloadPresentationFactory.create(found, RegionalMapDownloadState(
            packageId = "ua", phase = RegionalMapDownloadPhase.VERIFYING, downloadedBytes = 5_000_000
        ))
        assertFalse(downloading.ready)
        assertEquals(RegionalMapPrimaryAction.PAUSE, downloading.primaryAction)
        assertFalse(verifying.ready)
        assertEquals("VERIFYING", verifying.availabilityLabel)
    }

    @Test fun `verified package is the only ready state`() {
        val presentation = RegionalMapDownloadPresentationFactory.create(
            RegionDiscoveryResult.Found(packageInfo()), RegionalMapDownloadState(packageId = "ua", phase = RegionalMapDownloadPhase.READY)
        )
        assertTrue(presentation.ready)
        assertEquals(RegionalMapPrimaryAction.MANAGE_DOWNLOADED_MAPS, presentation.primaryAction)
    }

    private fun packageInfo() = RegionalMapPackage(
        "ua", "Ukraine", RegionalMapBounds(44.0, 22.0, 53.0, 41.0), "https://example.invalid/style.json",
        5.0, 14.0, 5_000_000, "2026.09", "2026.09", "a".repeat(64)
    )
}
