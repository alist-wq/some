package com.example.navigation.maps

import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionDestinationIndexPackageRuntimeTest {
    @Test fun doesNotExposeAnIndexWithoutTheMatchingActiveMap() {
        val runtime = ProductionDestinationIndexPackageRuntime(Files.createTempDirectory("destination-index-runtime"), { null })
        val result = runtime.activePackage() as DestinationIndexPackageVerificationResult.Unavailable
        assertTrue(result.reason.contains("No active map package"))
        assertTrue(runtime.activeSearchIndex() is DestinationIndexLoadResult.IndexUnavailable)
    }

    @Test fun rejectsAnIncompatibleActiveMapVersion() {
        val runtime = ProductionDestinationIndexPackageRuntime(Files.createTempDirectory("destination-index-runtime"), { kharkivMap("other-map") })
        val result = runtime.activePackage() as DestinationIndexPackageVerificationResult.Unavailable
        assertTrue(result.reason.contains("incompatible"))
    }

    private fun kharkivMap(mapVersion: String) = RegionalMapPackage(
        id = ProductionDestinationIndexPackageCatalog.REGION_ID,
        displayName = "Kharkiv, Ukraine",
        bounds = RegionalMapBounds(49.80, 35.90, 50.20, 36.50),
        styleUrl = "asset://style.json", minimumZoom = 8.0, maximumZoom = 14.0,
        estimatedSizeBytes = 1L, mapVersion = mapVersion, routingGraphVersion = "unavailable", sha256 = "a".repeat(64)
    )
}
