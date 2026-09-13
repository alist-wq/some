package com.example.navigation.maps

import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertIs

/**
 * Opt-in verification for an externally hosted production artifact. CI stays deterministic and
 * offline unless a trusted fixture directory is explicitly supplied by the release verifier.
 */
class ProductionDestinationIndexReleaseIntegrationTest {
    @Test fun installsAndLoadsThePublishedImmutablePackage() {
        val fixturePath = System.getProperty("renav.destination.index.fixture.dir")
        assumeTrue(!fixturePath.isNullOrBlank(), "requires an explicitly downloaded production fixture")
        val fixture = java.nio.file.Path.of(requireNotNull(fixturePath))
        assumeTrue(Files.isDirectory(fixture), "production fixture directory is unavailable")

        val expected = requireNotNull(
            ProductionDestinationIndexPackageCatalog.catalog.packageFor(
                ProductionDestinationIndexPackageCatalog.REGION_ID
            )
        )
        when (val verification = SignedDestinationIndexPackageVerifier().verify(expected, fixture)) {
            is DestinationIndexPackageVerificationResult.Ready -> Unit
            is DestinationIndexPackageVerificationResult.Unavailable -> error("Published package did not verify: ${verification.reason}")
        }
        val installation =
            DestinationIndexPackageInstaller(Files.createTempDirectory("production-destination-index"), SignedDestinationIndexPackageVerifier())
                .install(expected, DestinationIndexArtifactDownloader { artifact, destination ->
                    Files.copy(fixture.resolve(artifact.name), destination, REPLACE_EXISTING)
                })
        val installed = when (installation) {
            is DestinationIndexPackageInstallResult.Activated -> installation.value
            is DestinationIndexPackageInstallResult.Unavailable -> error("Published package did not install: ${installation.reason}")
        }
        assertIs<DestinationIndexLoadResult.Ready>(DestinationIndexPackageLoader.load(installed))
    }
}
