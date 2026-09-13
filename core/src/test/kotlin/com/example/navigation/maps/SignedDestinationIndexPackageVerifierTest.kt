package com.example.navigation.maps

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SignedDestinationIndexPackageVerifierTest {
    @Test fun acceptsSignedCatalogMatchingPackage() {
        val fixture = fixture()
        assertIs<DestinationIndexPackageVerificationResult.Ready>(fixture.verifier.verify(fixture.descriptor, fixture.root))
    }

    @Test fun rejectsTamperedIndexAndWrongSigner() {
        val corrupted = fixture()
        Files.writeString(corrupted.root.resolve("destination-index.jsonl"), "corrupt\n")
        assertUnavailable(corrupted.verifier.verify(corrupted.descriptor, corrupted.root), "checksum")

        val untrusted = fixture()
        assertUnavailable(SignedDestinationIndexPackageVerifier("SHA256:not-the-signer").verify(untrusted.descriptor, untrusted.root), "not trusted")
    }

    private fun assertUnavailable(result: DestinationIndexPackageVerificationResult, text: String) {
        val unavailable = assertIs<DestinationIndexPackageVerificationResult.Unavailable>(result)
        assertTrue(unavailable.reason.contains(text, ignoreCase = true), unavailable.reason)
    }

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("signed-destination-index")
        val index = root.resolve("destination-index.jsonl").also { Files.writeString(it, "{\"stableId\":\"place-1\"}\n") }
        val indexSha = sha256(index)
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val blob = sshBytes("ssh-ed25519".toByteArray()) + sshBytes(pair.public.encoded.takeLast(32).toByteArray())
        val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
        Files.writeString(root.resolve("renav-destination-index-ed25519.pub"), "ssh-ed25519 ${Base64.getEncoder().encodeToString(blob)} fixture\n")
        val manifest = """{"artifacts":[{"bytes":${Files.size(index)},"name":"destination-index.jsonl","sha256":"$indexSha"}],"attribution":"\u00a9 OpenStreetMap contributors, ODbL 1.0","indexChecksumSha256":"$indexSha","mapCompatibilityVersion":"openfreemap-2026-09","packageVersion":"fixture-1","recordCount":1,"regionId":"ua-kharkiv-pedestrian","schemaVersion":1,"source":{"dateUtc":"2026-01-01","sha256":"${"a".repeat(64)}","url":"https://example.test/input.pbf"}}"""
        val manifestPath = root.resolve("manifest.json").also { Files.writeString(it, manifest) }
        Files.writeString(root.resolve("manifest.json.sig"), armor(sign(pair, blob, manifestPath)))
        val descriptor = DestinationIndexPackageDescriptor("ua-kharkiv-pedestrian", "fixture-1", "openfreemap-2026-09", "https://example.test/input.pbf", "a".repeat(64), "© OpenStreetMap contributors, ODbL 1.0", listOf(
            DestinationIndexArtifact("destination-index.jsonl", "https://example.test/index", indexSha),
            DestinationIndexArtifact("manifest.json", "https://example.test/manifest", sha256(manifestPath)),
            DestinationIndexArtifact("manifest.json.sig", "https://example.test/signature", sha256(root.resolve("manifest.json.sig"))),
            DestinationIndexArtifact("renav-destination-index-ed25519.pub", "https://example.test/key", sha256(root.resolve("renav-destination-index-ed25519.pub")))
        ))
        return Fixture(root, descriptor, SignedDestinationIndexPackageVerifier(fingerprint))
    }

    private fun sign(pair: KeyPair, blob: ByteArray, manifest: java.nio.file.Path): ByteArray {
        val namespace = DestinationIndexTrustAnchor.SIGNATURE_NAMESPACE.toByteArray(StandardCharsets.US_ASCII)
        val hashName = "sha512".toByteArray(StandardCharsets.US_ASCII)
        val hash = MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(manifest))
        val signed = "SSHSIG".toByteArray(StandardCharsets.US_ASCII) + sshBytes(namespace) + sshBytes(byteArrayOf()) + sshBytes(hashName) + sshBytes(hash)
        val raw = Signature.getInstance("Ed25519").run { initSign(pair.private); update(signed); sign() }
        val encoded = sshBytes("ssh-ed25519".toByteArray(StandardCharsets.US_ASCII)) + sshBytes(raw)
        return "SSHSIG".toByteArray(StandardCharsets.US_ASCII) + uint(1) + sshBytes(blob) + sshBytes(namespace) + sshBytes(byteArrayOf()) + sshBytes(hashName) + sshBytes(encoded)
    }
    private fun armor(value: ByteArray) = "-----BEGIN SSH SIGNATURE-----\n${Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(value)}\n-----END SSH SIGNATURE-----\n"
    private fun sha256(path: java.nio.file.Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun uint(value: Int) = ByteBuffer.allocate(4).putInt(value).array()
    private fun sshBytes(value: ByteArray) = uint(value.size) + value
    private data class Fixture(val root: java.nio.file.Path, val descriptor: DestinationIndexPackageDescriptor, val verifier: SignedDestinationIndexPackageVerifier)
}
