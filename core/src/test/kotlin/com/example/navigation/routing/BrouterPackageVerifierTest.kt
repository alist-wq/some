package com.example.navigation.routing

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BrouterPackageVerifierTest {
    @Test
    fun acceptsCompleteSignedPackageWithMatchingCoreAndMapVersions() {
        val fixture = fixture()

        val result = fixture.verifier.verify(fixture.root, MAP_VERSION)

        assertTrue(result is BrouterPackageVerificationResult.Ready, result.toString())
        val ready = result
        assertEquals("ua-kharkiv-pedestrian", ready.value.regionId)
        assertEquals(listOf("E35_N50.rd5"), ready.value.segments.map { it.fileName.toString() })
    }

    @Test
    fun rejectsChecksumMapAndCoreVersionMismatchesWithoutRouting() {
        val fixture = fixture()
        Files.writeString(fixture.root.resolve("E35_N50.rd5"), "corrupt")
        assertUnavailable(fixture.verifier.verify(fixture.root, MAP_VERSION), "checksum")

        val mapFixture = fixture()
        assertUnavailable(mapFixture.verifier.verify(mapFixture.root, "other-map"), "map version")

        val revisionFixture = fixture()
        val incompatible = BrouterPackageVerifier("other-core", revisionFixture.fingerprint)
        assertUnavailable(incompatible.verify(revisionFixture.root, MAP_VERSION), "revision")
    }

    @Test
    fun rejectsUntrustedSignerAndMissingSegment() {
        val fixture = fixture()
        assertUnavailable(BrouterPackageVerifier(CORE_REVISION, "SHA256:not-the-signer").verify(fixture.root, MAP_VERSION), "not trusted")

        val missing = fixture()
        Files.delete(missing.root.resolve("E35_N50.rd5"))
        assertUnavailable(missing.verifier.verify(missing.root, MAP_VERSION), "missing")
    }

    @Test
    fun rejectsManifestWithoutAnExplicitProfileVersion() {
        val fixture = fixture(profileVersion = null)

        assertUnavailable(fixture.verifier.verify(fixture.root, MAP_VERSION), "profile version")
    }

    private fun assertUnavailable(result: BrouterPackageVerificationResult, expected: String) {
        val unavailable = assertIs<BrouterPackageVerificationResult.Unavailable>(result)
        assertTrue(unavailable.reason.contains(expected, ignoreCase = true), unavailable.reason)
    }

    private fun fixture(profileVersion: String? = "renav-pedestrian-1"): Fixture {
        val root = Files.createTempDirectory("brouter-verified-package")
        val lookup = root.resolve("lookups.dat").also { Files.writeString(it, "lookup") }
        val profile = root.resolve("renav-pedestrian.brf").also { Files.writeString(it, "profile") }
        val segment = root.resolve("E35_N50.rd5").also { Files.writeString(it, "segment") }
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val publicBlob = sshBytes("ssh-ed25519".toByteArray()) + sshBytes(keyPair.public.encoded.takeLast(32).toByteArray())
        val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(publicBlob))
        Files.writeString(root.resolve("renav-brouter-ed25519.pub"), "ssh-ed25519 ${Base64.getEncoder().encodeToString(publicBlob)} fixture\n")
        val versionJson = profileVersion?.let { ",\"version\":\"$it\"" }.orEmpty()
        val manifest = """{"brouterRevision":"$CORE_REVISION","lookup":{"bytes":${Files.size(lookup)},"name":"lookups.dat","sha256":"${sha256(lookup)}"},"mapCompatibilityVersion":"$MAP_VERSION","packageVersion":"fixture-1","profile":{"bytes":${Files.size(profile)},"name":"renav-pedestrian.brf","sha256":"${sha256(profile)}"$versionJson},"regionId":"ua-kharkiv-pedestrian","segments":[{"bytes":${Files.size(segment)},"name":"E35_N50.rd5","sha256":"${sha256(segment)}"}]}"""
        val manifestPath = root.resolve("manifest.json").also { Files.writeString(it, manifest) }
        Files.writeString(root.resolve("manifest.json.sig"), sshArmor(sshSignature(keyPair, publicBlob, manifestPath)))
        return Fixture(root, BrouterPackageVerifier(CORE_REVISION, fingerprint), fingerprint)
    }

    private fun sshSignature(keyPair: KeyPair, publicBlob: ByteArray, manifest: java.nio.file.Path): ByteArray {
        val namespace = BrouterTrustAnchor.SIGNATURE_NAMESPACE.toByteArray(StandardCharsets.US_ASCII)
        val hashName = "sha512".toByteArray(StandardCharsets.US_ASCII)
        val hash = MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(manifest))
        val signed = "SSHSIG".toByteArray(StandardCharsets.US_ASCII) + sshBytes(namespace) + sshBytes(byteArrayOf()) + sshBytes(hashName) + sshBytes(hash)
        val rawSignature = Signature.getInstance("Ed25519").run { initSign(keyPair.private); update(signed); sign() }
        val encodedSignature = sshBytes("ssh-ed25519".toByteArray(StandardCharsets.US_ASCII)) + sshBytes(rawSignature)
        return "SSHSIG".toByteArray(StandardCharsets.US_ASCII) + uint(1) + sshBytes(publicBlob) + sshBytes(namespace) + sshBytes(byteArrayOf()) + sshBytes(hashName) + sshBytes(encodedSignature)
    }

    private fun sshArmor(signature: ByteArray): String = "-----BEGIN SSH SIGNATURE-----\n" +
        Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(signature) +
        "\n-----END SSH SIGNATURE-----\n"

    private fun sha256(path: java.nio.file.Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun uint(value: Int): ByteArray = ByteBuffer.allocate(4).putInt(value).array()
    private fun sshBytes(value: ByteArray): ByteArray = uint(value.size) + value

    private data class Fixture(val root: java.nio.file.Path, val verifier: BrouterPackageVerifier, val fingerprint: String)

    private companion object {
        const val CORE_REVISION = "d5c0a75d29b5a036525fd5e9466a6376ee54cc3e"
        const val MAP_VERSION = "openfreemap-2026-09"
    }
}
