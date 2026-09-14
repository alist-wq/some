package com.example.navigation.maps

import com.example.navigation.routing.BrouterPackageVerificationResult
import com.example.navigation.routing.BrouterArtifactDescriptor
import com.example.navigation.routing.BrouterArtifactDownloader
import com.example.navigation.routing.BrouterPackageInstallResult
import com.example.navigation.routing.BrouterPackageInstaller
import com.example.navigation.routing.BrouterPackageVerifier
import com.example.navigation.routing.BrouterRegionalPackageCatalog
import com.example.navigation.routing.BrouterRegionalPackageDescriptor
import com.example.navigation.routing.BrouterRouteRequest
import com.example.navigation.routing.GeoPoint
import com.example.navigation.routing.ProductionBrouterPackageCatalog
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

class ProductionBrouterPackageRuntimeTest {
    @Test fun `does not expose BRouter when no matching active map exists`() {
        val runtime = ProductionBrouterPackageRuntime(Files.createTempDirectory("brouter-runtime"), { null })

        val unavailable = runtime.activePackage()

        assertTrue(unavailable is BrouterPackageVerificationResult.Unavailable)
        assertTrue((unavailable as BrouterPackageVerificationResult.Unavailable).reason.contains("No active map package"))
    }

    @Test fun `rejects map versions incompatible with the approved BRouter package`() {
        val incompatible = kharkivMap(mapVersion = "different-map-version")
        val runtime = ProductionBrouterPackageRuntime(Files.createTempDirectory("brouter-runtime"), { incompatible })

        val unavailable = runtime.activePackage()

        assertTrue(unavailable is BrouterPackageVerificationResult.Unavailable)
        assertTrue((unavailable as BrouterPackageVerificationResult.Unavailable).reason.contains("incompatible"))
    }

    @Test fun `matching active Kharkiv map remains explicitly unavailable until installed`() {
        val runtime = ProductionBrouterPackageRuntime(Files.createTempDirectory("brouter-runtime"), { kharkivMap() })

        val unavailable = runtime.activePackage()

        assertTrue(unavailable is BrouterPackageVerificationResult.Unavailable)
        assertTrue((unavailable as BrouterPackageVerificationResult.Unavailable).reason.contains("not installed"))
    }

    @Test fun `activated package is passed unchanged to the TASK 151 adapter request boundary`() {
        val fixture = fixture()
        val runtime = ProductionBrouterPackageRuntime(
            fixture.root,
            { fixture.map },
            BrouterRegionalPackageCatalog("fixture-catalog", listOf(fixture.descriptor)),
            fixture.downloader,
            BrouterPackageInstaller(fixture.root.resolve("routing").resolve("brouter")) {
                BrouterPackageVerifier(it.brouterRevision, fixture.fingerprint)
            }
        )

        val installed = (runtime.installForActiveMap() as BrouterPackageInstallResult.Activated).value
        val active = (runtime.activePackage() as BrouterPackageVerificationResult.Ready).value
        val request = BrouterRouteRequest(GeoPoint(50.0, 36.0), GeoPoint(50.01, 36.01), "renav-pedestrian", active)

        assertEquals(installed, active)
        assertEquals(active, request.verifiedPackage)
        assertEquals(fixture.descriptor.regionId, request.verifiedPackage.regionId)
        assertTrue(Files.isDirectory(request.verifiedPackage.root))
    }

    private fun kharkivMap(mapVersion: String = ProductionBrouterPackageCatalog.MAP_COMPATIBILITY_VERSION) = RegionalMapPackage(
        id = ProductionBrouterPackageCatalog.REGION_ID,
        displayName = "Kharkiv, Ukraine",
        bounds = RegionalMapBounds(49.80, 35.90, 50.20, 36.50),
        styleUrl = "asset://style.json",
        minimumZoom = 8.0,
        maximumZoom = 14.0,
        estimatedSizeBytes = 1L,
        mapVersion = mapVersion,
        routingGraphVersion = "unavailable",
        sha256 = "a".repeat(64)
    )

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("brouter-runtime")
        val files = linkedMapOf(
            "lookups.dat" to "lookup".toByteArray(),
            "renav-pedestrian.brf" to "profile".toByteArray(),
            "E35_N50.rd5" to "segment".toByteArray()
        )
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val publicBlob = sshBytes("ssh-ed25519".toByteArray()) + sshBytes(pair.public.encoded.takeLast(32).toByteArray())
        val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(publicBlob))
        files["renav-brouter-ed25519.pub"] = "ssh-ed25519 ${Base64.getEncoder().encodeToString(publicBlob)} fixture\n".toByteArray()
        val manifest = """{"brouterRevision":"fixture-core","lookup":{"name":"lookups.dat","sha256":"${sha256(files.getValue("lookups.dat"))}"},"mapCompatibilityVersion":"fixture-map","packageVersion":"fixture-1","profile":{"name":"renav-pedestrian.brf","sha256":"${sha256(files.getValue("renav-pedestrian.brf"))}","version":"renav-pedestrian-1"},"regionId":"fixture-region","segments":[{"name":"E35_N50.rd5","sha256":"${sha256(files.getValue("E35_N50.rd5"))}"}]}"""
        files["manifest.json"] = manifest.toByteArray()
        files["manifest.json.sig"] = armor(signature(pair, publicBlob, files.getValue("manifest.json"))).toByteArray()
        val descriptor = BrouterRegionalPackageDescriptor("fixture-region", "fixture-1", "fixture-map", "fixture-core", sha256(files.getValue("renav-pedestrian.brf")), files.map { (name, bytes) ->
            BrouterArtifactDescriptor(name, "https://fixtures.example/$name", sha256(bytes))
        })
        val map = RegionalMapPackage("fixture-region", "Fixture", RegionalMapBounds(49.8, 35.9, 50.2, 36.5), "asset://style", 8.0, 14.0, 1L, "fixture-map", "unavailable", "a".repeat(64))
        val downloader = BrouterArtifactDownloader { artifact, destination -> Files.write(destination, files.getValue(artifact.name)) }
        return Fixture(root, descriptor, map, downloader, fingerprint)
    }

    private fun signature(pair: KeyPair, publicBlob: ByteArray, manifest: ByteArray): ByteArray {
        val namespace = "renav-brouter-manifest".toByteArray(StandardCharsets.US_ASCII)
        val hashName = "sha512".toByteArray(StandardCharsets.US_ASCII)
        val hash = MessageDigest.getInstance("SHA-512").digest(manifest)
        val signed = "SSHSIG".toByteArray(StandardCharsets.US_ASCII) + sshBytes(namespace) + sshBytes(byteArrayOf()) + sshBytes(hashName) + sshBytes(hash)
        val raw = Signature.getInstance("Ed25519").run { initSign(pair.private); update(signed); sign() }
        val encoded = sshBytes("ssh-ed25519".toByteArray(StandardCharsets.US_ASCII)) + sshBytes(raw)
        return "SSHSIG".toByteArray(StandardCharsets.US_ASCII) + uint(1) + sshBytes(publicBlob) + sshBytes(namespace) + sshBytes(byteArrayOf()) + sshBytes(hashName) + sshBytes(encoded)
    }

    private fun armor(value: ByteArray) = "-----BEGIN SSH SIGNATURE-----\n${Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(value)}\n-----END SSH SIGNATURE-----\n"
    private fun sshBytes(value: ByteArray) = uint(value.size) + value
    private fun uint(value: Int) = ByteBuffer.allocate(4).putInt(value).array()
    private fun sha256(value: ByteArray) = MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }

    private data class Fixture(
        val root: java.nio.file.Path,
        val descriptor: BrouterRegionalPackageDescriptor,
        val map: RegionalMapPackage,
        val downloader: BrouterArtifactDownloader,
        val fingerprint: String
    )
}
