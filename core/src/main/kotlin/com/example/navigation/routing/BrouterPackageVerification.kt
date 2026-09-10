package com.example.navigation.routing

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class VerifiedBrouterPackage(
    val regionId: String,
    val packageVersion: String,
    val mapCompatibilityVersion: String,
    val brouterRevision: String,
    val root: Path,
    val profile: Path,
    val profileVersion: String,
    val lookup: Path,
    val segments: List<Path>
)

sealed interface BrouterPackageVerificationResult {
    data class Ready(val value: VerifiedBrouterPackage) : BrouterPackageVerificationResult
    data class Unavailable(val reason: String) : BrouterPackageVerificationResult
}

/** Verifies a release-produced SSHSIG manifest and every declared BRouter artifact before use. */
class BrouterPackageVerifier(
    private val expectedBrouterRevision: String,
    private val trustedFingerprint: String = BrouterTrustAnchor.SSH_ED25519_SHA256_FINGERPRINT
) {
    fun verify(root: Path, expectedMapVersion: String): BrouterPackageVerificationResult = try {
        val manifest = root.resolve("manifest.json")
        val signature = root.resolve("manifest.json.sig")
        val publicKey = root.resolve("renav-brouter-ed25519.pub")
        if (!Files.isRegularFile(manifest) || !Files.isRegularFile(signature) || !Files.isRegularFile(publicKey)) unavailable("Signed BRouter manifest is incomplete")
        else {
            val publicBlob = parsePublicKey(publicKey)
            if (fingerprint(publicBlob) != trustedFingerprint) unavailable("BRouter package signer is not trusted")
            else if (!verifySshSignature(manifest, signature, publicBlob)) unavailable("BRouter manifest signature is invalid")
            else parseManifest(root, Files.readString(manifest), expectedMapVersion)
        }
    } catch (error: Exception) { unavailable("BRouter package is invalid: ${error.message}") }

    private fun parseManifest(root: Path, text: String, expectedMapVersion: String): BrouterPackageVerificationResult {
        fun string(name: String): String = Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(text)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("BRouter manifest is missing $name")
        val regionId = string("regionId")
        val packageVersion = string("packageVersion")
        val mapVersion = string("mapCompatibilityVersion")
        val revision = string("brouterRevision")
        if (mapVersion != expectedMapVersion) return unavailable("BRouter package map version does not match the active map")
        if (revision != expectedBrouterRevision) return unavailable("BRouter package revision does not match the embedded routing core")
        val artifacts = Regex("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"sha256\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"")
            .findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val byName = artifacts.toMap()
        val required = listOf("lookups.dat", "renav-pedestrian.brf") + Regex("E\\d+_N\\d+\\.rd5").findAll(text).map { it.value }.toList()
        if (required.size < 3 || required.any { it !in byName }) return unavailable("BRouter manifest has incomplete segment or profile evidence")
        required.forEach { name ->
            val path = root.resolve(name).normalize()
            if (!path.startsWith(root.toAbsolutePath().normalize()) || !Files.isRegularFile(path)) return unavailable("BRouter artifact is missing: $name")
            if (sha256(path) != byName.getValue(name)) return unavailable("BRouter artifact checksum is invalid: $name")
        }
        val profileVersion = Regex("\\\"profile\\\"\\s*:\\s*\\{[^}]*\\\"version\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
            .find(text)?.groupValues?.get(1) ?: throw IllegalArgumentException("BRouter manifest is missing profile version")
        return BrouterPackageVerificationResult.Ready(VerifiedBrouterPackage(regionId, packageVersion, mapVersion, revision, root,
            root.resolve("renav-pedestrian.brf"), profileVersion, root.resolve("lookups.dat"), required.filter { it.endsWith(".rd5") }.map(root::resolve)))
    }

    private fun parsePublicKey(path: Path): ByteArray {
        val fields = Files.readString(path).trim().split(Regex("\\s+"))
        require(fields.size >= 2 && fields[0] == "ssh-ed25519") { "unsupported BRouter public key" }
        return Base64.getDecoder().decode(fields[1])
    }

    private fun fingerprint(blob: ByteArray): String = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))

    private fun verifySshSignature(manifest: Path, signatureFile: Path, publicBlob: ByteArray): Boolean {
        val publicReader = SshReader(publicBlob)
        require(publicReader.string() == "ssh-ed25519") { "BRouter public key algorithm is unsupported" }
        val rawKey = publicReader.bytes()
        require(rawKey.size == 32) { "BRouter public key has an invalid length" }
        val signature = SshReader(readSignature(signatureFile))
        require(String(signature.take(6), StandardCharsets.US_ASCII) == "SSHSIG") { "BRouter manifest has an invalid SSHSIG header" }
        require(signature.uint() == 1L) { "BRouter manifest uses an unsupported SSHSIG version" }
        signature.bytes() // duplicated public key, intentionally ignored after trusted key validation
        val namespace = signature.bytes()
        require(String(namespace, StandardCharsets.US_ASCII) == BrouterTrustAnchor.SIGNATURE_NAMESPACE) { "BRouter manifest has an unexpected signature namespace" }
        val reserved = signature.bytes()
        val hashName = String(signature.bytes(), StandardCharsets.US_ASCII)
        require(hashName == "sha512") { "BRouter manifest has an unsupported signature hash" }
        // SSHSIG stores the signature blob immediately after the hash algorithm. The
        // hash of the signed content is part of the signed preimage, not a field in
        // the serialized SSHSIG envelope.
        val messageHash = MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(manifest))
        val encodedSignature = SshReader(signature.bytes())
        require(encodedSignature.string() == "ssh-ed25519") { "BRouter manifest has an unsupported signature algorithm" }
        val rawSignature = encodedSignature.bytes()
        val signed = SshWriter()
            // PROTOCOL.sshsig: the signed preamble is the literal six-byte magic;
            // the envelope version is deliberately not part of the signed data.
            .raw("SSHSIG".toByteArray(StandardCharsets.US_ASCII))
            .bytes(namespace)
            .bytes(reserved)
            .bytes(hashName.toByteArray(StandardCharsets.US_ASCII))
            .bytes(messageHash)
            .toByteArray()
        val x509 = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00) + rawKey
        return Signature.getInstance("Ed25519").run { initVerify(KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(x509))); update(signed); verify(rawSignature) }
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun unavailable(reason: String) = BrouterPackageVerificationResult.Unavailable(reason)

    private fun readSignature(path: Path): ByteArray {
        val bytes = Files.readAllBytes(path)
        val text = String(bytes, StandardCharsets.US_ASCII)
        if (!text.startsWith("-----BEGIN SSH SIGNATURE-----")) return bytes
        val base64 = text.lineSequence()
            .filterNot { it.startsWith("-----") || it.isBlank() }
            .joinToString("")
        require(base64.isNotEmpty()) { "BRouter manifest SSH signature is empty" }
        return Base64.getDecoder().decode(base64)
    }
}

private class SshReader(private val value: ByteArray) {
    private var offset = 0
    fun take(count: Int): ByteArray {
        require(count >= 0 && count <= value.size - offset) { "truncated SSH signature data at offset=$offset for length=$count" }
        val start = offset
        offset += count
        return value.copyOfRange(start, offset)
    }
    fun uint(): Long = ByteBuffer.wrap(take(4)).int.toLong() and 0xffffffffL
    fun bytes(): ByteArray = take(uint().toInt())
    fun string(): String = String(bytes(), StandardCharsets.US_ASCII)
}
private class SshWriter {
    private var value = ByteArray(0)
    fun uint(number: Int): SshWriter { value += ByteBuffer.allocate(4).putInt(number).array(); return this }
    fun raw(bytes: ByteArray): SshWriter { value += bytes; return this }
    fun bytes(bytes: ByteArray): SshWriter { uint(bytes.size); value += bytes; return this }
    fun toByteArray(): ByteArray = value
}
