package com.example.navigation.maps

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DestinationIndexPackageInstallationTest {
    @Test fun `verified immutable package activates atomically and replaces an older version`() {
        val fixture = fixture()
        val installer = DestinationIndexPackageInstaller(fixture.root, fixture.verifier)

        val activated = installer.install(fixture.descriptor, fixture.downloader)

        assertIs<DestinationIndexPackageInstallResult.Activated>(activated)
        assertIs<DestinationIndexPackageVerificationResult.Ready>(installer.activePackage(fixture.descriptor))
        assertTrue(Files.isRegularFile(fixture.root.resolve("active/ua-kharkiv-pedestrian/2026.09.10-1/destination-index.jsonl")))
    }

    @Test fun `corrupt staging data never becomes active and recovery preserves verified package`() {
        val fixture = fixture()
        val installer = DestinationIndexPackageInstaller(fixture.root, fixture.verifier)
        assertIs<DestinationIndexPackageInstallResult.Activated>(installer.install(fixture.descriptor, fixture.downloader))
        val corrupt = DestinationIndexArtifactDownloader { artifact, destination ->
            Files.write(destination, if (artifact.name == "manifest.json") "tampered".toByteArray() else fixture.files.getValue(artifact.name))
        }

        val result = installer.install(fixture.descriptor.copy(packageVersion = "2026.09.10-2"), corrupt)
        installer.recoverInterruptedDownloads()

        assertIs<DestinationIndexPackageInstallResult.Unavailable>(result)
        assertIs<DestinationIndexPackageVerificationResult.Ready>(installer.activePackage(fixture.descriptor))
    }

    private fun fixture(): Fixture {
        val files = linkedMapOf(
            "destination-index.jsonl" to "{\"stableId\":\"kharkiv-place-1\"}\n".toByteArray(),
            "manifest.json" to "{\"schemaVersion\":1}".toByteArray(),
            "manifest.json.sig" to "signature".toByteArray(),
            "renav-destination-index-ed25519.pub" to "ssh-ed25519 fixture".toByteArray()
        )
        val descriptor = DestinationIndexPackageDescriptor(
            "ua-kharkiv-pedestrian", "2026.09.10-1", "openfreemap-2026-09",
            "https://download.geofabrik.de/europe/ukraine-260901.osm.pbf", "a".repeat(64),
            "© OpenStreetMap contributors, ODbL 1.0", files.map { (name, bytes) ->
                DestinationIndexArtifact(name, "https://fixtures.example/2026.09.10-1/$name", sha256(bytes))
            }
        )
        val verifier = DestinationIndexPackageVerifier { expected, root ->
            val index = root.resolve("destination-index.jsonl")
            if (Files.isRegularFile(index)) DestinationIndexPackageVerificationResult.Ready(VerifiedDestinationIndexPackage(expected.regionId, expected.packageVersion, expected.mapCompatibilityVersion, root, index))
            else DestinationIndexPackageVerificationResult.Unavailable("Fixture index missing")
        }
        return Fixture(Files.createTempDirectory("destination-index"), descriptor, files, DestinationIndexArtifactDownloader { artifact, destination -> Files.write(destination, files.getValue(artifact.name)) }, verifier)
    }

    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
    private data class Fixture(val root: Path, val descriptor: DestinationIndexPackageDescriptor, val files: Map<String, ByteArray>, val downloader: DestinationIndexArtifactDownloader, val verifier: DestinationIndexPackageVerifier)
}
