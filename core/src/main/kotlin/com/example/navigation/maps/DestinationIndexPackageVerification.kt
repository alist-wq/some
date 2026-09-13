package com.example.navigation.maps

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Fail-closed verifier for the signed, destination-search-only release package. */
class SignedDestinationIndexPackageVerifier(
    private val trustedFingerprint: String = DestinationIndexTrustAnchor.SSH_ED25519_SHA256_FINGERPRINT
) : DestinationIndexPackageVerifier {
    override fun verify(expected: DestinationIndexPackageDescriptor, root: Path): DestinationIndexPackageVerificationResult = try {
        val manifest = root.resolve("manifest.json")
        val signature = root.resolve("manifest.json.sig")
        val publicKey = root.resolve("renav-destination-index-ed25519.pub")
        val index = root.resolve("destination-index.jsonl")
        if (listOf(manifest, signature, publicKey, index).any { !Files.isRegularFile(it) }) unavailable("Signed destination-index package is incomplete")
        else {
            val publicBlob = parsePublicKey(publicKey)
            if (fingerprint(publicBlob) != trustedFingerprint) unavailable("Destination-index package signer is not trusted")
            else if (!verifySshSignature(manifest, signature, publicBlob)) unavailable("Destination-index manifest signature is invalid")
            else parseManifest(expected, root, Files.readString(manifest), index)
        }
    } catch (error: Exception) { unavailable("Destination-index package is invalid: ${error.message}") }

    private fun parseManifest(expected: DestinationIndexPackageDescriptor, root: Path, text: String, index: Path): DestinationIndexPackageVerificationResult {
        fun string(name: String): String = Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(text)?.groupValues?.get(1)
            ?.let(::decodeJsonString) ?: throw IllegalArgumentException("Destination-index manifest is missing $name")
        fun number(name: String): Long = Regex("\\\"$name\\\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toLong()
            ?: throw IllegalArgumentException("Destination-index manifest is missing $name")
        if (number("schemaVersion") != 1L) return unavailable("Destination-index manifest schema is unsupported")
        if (string("regionId") != expected.regionId || string("packageVersion") != expected.packageVersion) return unavailable("Destination-index manifest identity does not match the catalog")
        if (string("mapCompatibilityVersion") != expected.mapCompatibilityVersion) return unavailable("Destination-index map version does not match the catalog")
        val source = Regex("\\\"source\\\"\\s*:\\s*\\{([^}]*)}").find(text)?.groupValues?.get(1)
            ?: return unavailable("Destination-index manifest lacks locked source evidence")
        if (!source.contains("\"url\":\"${expected.lockedSourceUrl}\"") ||
            !source.contains("\"sha256\":\"${expected.lockedSourceSha256}\"")) return unavailable("Destination-index locked source does not match the catalog")
        if (string("attribution") != expected.attribution) return unavailable("Destination-index attribution does not match the catalog")
        if (number("recordCount") <= 0) return unavailable("Destination-index manifest has no records")
        val indexArtifact = expected.artifacts.singleOrNull { it.name == "destination-index.jsonl" }
            ?: return unavailable("Destination-index catalog is incomplete")
        val artifacts = Regex("\\\"artifacts\\\"\\s*:\\s*\\[\\{([^}]*)}").find(text)?.groupValues?.get(1)
            ?: return unavailable("Destination-index manifest lacks index checksum")
        if (!artifacts.contains("\"name\":\"destination-index.jsonl\"") ||
            !artifacts.contains("\"sha256\":\"${indexArtifact.sha256}\"") ||
            string("indexChecksumSha256") != indexArtifact.sha256 || sha256(index) != indexArtifact.sha256) return unavailable("Destination-index artifact checksum is invalid")
        return DestinationIndexPackageVerificationResult.Ready(VerifiedDestinationIndexPackage(expected.regionId, expected.packageVersion, expected.mapCompatibilityVersion, root, index))
    }

    private fun parsePublicKey(path: Path): ByteArray {
        val fields = Files.readString(path).trim().split(Regex("\\s+"))
        require(fields.size >= 2 && fields[0] == "ssh-ed25519") { "unsupported destination-index public key" }
        return Base64.getDecoder().decode(fields[1])
    }
    private fun decodeJsonString(value: String): String = Regex("\\\\u([0-9a-fA-F]{4})").replace(value) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }.replace("\\\\\"", "\"").replace("\\\\/", "/").replace("\\\\\\\\", "\\")
    private fun fingerprint(blob: ByteArray): String = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    private fun unavailable(reason: String) = DestinationIndexPackageVerificationResult.Unavailable(reason)

    private fun verifySshSignature(manifest: Path, signatureFile: Path, publicBlob: ByteArray): Boolean {
        val publicReader = DestinationIndexSshReader(publicBlob)
        require(publicReader.string() == "ssh-ed25519") { "destination-index public key algorithm is unsupported" }
        val rawKey = publicReader.bytes(); require(rawKey.size == 32) { "destination-index public key has an invalid length" }
        val signature = DestinationIndexSshReader(readSignature(signatureFile))
        require(String(signature.take(6), StandardCharsets.US_ASCII) == "SSHSIG") { "destination-index manifest has an invalid SSHSIG header" }
        require(signature.uint() == 1L) { "destination-index manifest uses an unsupported SSHSIG version" }
        signature.bytes()
        val namespace = signature.bytes(); require(String(namespace, StandardCharsets.US_ASCII) == DestinationIndexTrustAnchor.SIGNATURE_NAMESPACE) { "destination-index manifest has an unexpected signature namespace" }
        val reserved = signature.bytes()
        val hashName = String(signature.bytes(), StandardCharsets.US_ASCII); require(hashName == "sha512") { "destination-index manifest has an unsupported signature hash" }
        val encoded = DestinationIndexSshReader(signature.bytes()); require(encoded.string() == "ssh-ed25519") { "destination-index manifest has an unsupported signature algorithm" }
        val signed = DestinationIndexSshWriter().raw("SSHSIG".toByteArray(StandardCharsets.US_ASCII)).bytes(namespace).bytes(reserved).bytes(hashName.toByteArray(StandardCharsets.US_ASCII)).bytes(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(manifest))).toByteArray()
        val x509 = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00) + rawKey
        return Signature.getInstance("Ed25519").run { initVerify(KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(x509))); update(signed); verify(encoded.bytes()) }
    }
    private fun readSignature(path: Path): ByteArray {
        val value = Files.readAllBytes(path); val text = String(value, StandardCharsets.US_ASCII)
        if (!text.startsWith("-----BEGIN SSH SIGNATURE-----")) return value
        return Base64.getDecoder().decode(text.lineSequence().filterNot { it.startsWith("-----") || it.isBlank() }.joinToString(""))
    }
}

private class DestinationIndexSshReader(private val value: ByteArray) {
    private var offset = 0
    fun take(count: Int): ByteArray { require(count >= 0 && count <= value.size - offset) { "truncated SSH signature data" }; val start = offset; offset += count; return value.copyOfRange(start, offset) }
    fun uint(): Long = ByteBuffer.wrap(take(4)).int.toLong() and 0xffffffffL
    fun bytes(): ByteArray = take(uint().toInt())
    fun string(): String = String(bytes(), StandardCharsets.US_ASCII)
}
private class DestinationIndexSshWriter {
    private var value = ByteArray(0)
    fun raw(bytes: ByteArray): DestinationIndexSshWriter { value += bytes; return this }
    fun bytes(bytes: ByteArray): DestinationIndexSshWriter { value += ByteBuffer.allocate(4).putInt(bytes.size).array() + bytes; return this }
    fun toByteArray(): ByteArray = value
}
