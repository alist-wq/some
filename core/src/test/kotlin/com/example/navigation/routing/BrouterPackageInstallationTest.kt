package com.example.navigation.routing

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BrouterPackageInstallationTest {
    @Test
    fun `verified staged package is atomically activated and adapter-ready`() {
        val fixture = fixture()
        val installer = fixture.installer()

        val result = installer.install(fixture.descriptor, fixture.downloader())

        val activated = assertIs<BrouterPackageInstallResult.Activated>(result).value
        assertEquals(fixture.descriptor.packageVersion, activated.packageVersion)
        val ready = assertIs<BrouterPackageVerificationResult.Ready>(installer.activePackage(fixture.descriptor)).value
        assertEquals(activated.root, ready.root)
        assertTrue(Files.isDirectory(ready.root))
        assertTrue(!Files.exists(fixture.root.resolve("staging").resolve("${fixture.descriptor.regionId}-${fixture.descriptor.packageVersion}")))
    }

    @Test
    fun `failed download preserves prior active package and clears staging`() {
        val fixture = fixture()
        val installer = fixture.installer()
        assertIs<BrouterPackageInstallResult.Activated>(installer.install(fixture.descriptor, fixture.downloader()))

        val failed = installer.install(fixture.descriptor.copy(packageVersion = "fixture-2"), fixture.downloader(corrupt = "lookups.dat"))

        assertIs<BrouterPackageInstallResult.Unavailable>(failed)
        assertIs<BrouterPackageVerificationResult.Ready>(installer.activePackage(fixture.descriptor))
        assertTrue(!Files.exists(fixture.root.resolve("staging").resolve("${fixture.descriptor.regionId}-fixture-2")))
    }

    @Test
    fun `recovery removes interrupted staging and removal makes package unavailable`() {
        val fixture = fixture()
        val installer = fixture.installer()
        val interrupted = fixture.root.resolve("staging").resolve("${fixture.descriptor.regionId}-${fixture.descriptor.packageVersion}")
        Files.createDirectories(interrupted)
        Files.writeString(interrupted.resolve("partial"), "partial")

        installer.recoverInterruptedDownloads()
        assertTrue(!Files.exists(interrupted))
        assertIs<BrouterPackageInstallResult.Activated>(installer.install(fixture.descriptor, fixture.downloader()))
        installer.remove(fixture.descriptor.regionId)

        assertIs<BrouterPackageVerificationResult.Unavailable>(installer.activePackage(fixture.descriptor))
    }

    private fun Fixture.installer() = BrouterPackageInstaller(root) { BrouterPackageVerifier(it.brouterRevision, fingerprint) }
    private fun Fixture.downloader(corrupt: String? = null) = BrouterArtifactDownloader { artifact, destination ->
        Files.createDirectories(destination.parent)
        Files.write(destination, if (artifact.name == corrupt) "corrupt".toByteArray() else files.getValue(artifact.name))
    }

    private fun fixture(): Fixture {
        val root = Files.createTempDirectory("brouter-install")
        val files = linkedMapOf("lookups.dat" to "lookup".toByteArray(), "renav-pedestrian.brf" to "profile".toByteArray(), "E35_N50.rd5" to "segment".toByteArray())
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val publicBlob = sshBytes("ssh-ed25519".toByteArray()) + sshBytes(pair.public.encoded.takeLast(32).toByteArray())
        val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(publicBlob))
        files["renav-brouter-ed25519.pub"] = "ssh-ed25519 ${Base64.getEncoder().encodeToString(publicBlob)} fixture\n".toByteArray()
        val manifest = """{"brouterRevision":"$REVISION","lookup":{"name":"lookups.dat","sha256":"${sha256(files.getValue("lookups.dat"))}"},"mapCompatibilityVersion":"$MAP","packageVersion":"fixture-1","profile":{"name":"renav-pedestrian.brf","sha256":"${sha256(files.getValue("renav-pedestrian.brf"))}","version":"renav-pedestrian-1"},"regionId":"fixture-region","segments":[{"name":"E35_N50.rd5","sha256":"${sha256(files.getValue("E35_N50.rd5"))}"}]}"""
        files["manifest.json"] = manifest.toByteArray()
        files["manifest.json.sig"] = armor(signature(pair, publicBlob, files.getValue("manifest.json"))).toByteArray()
        val descriptor = BrouterRegionalPackageDescriptor("fixture-region", "fixture-1", MAP, REVISION, sha256(files.getValue("renav-pedestrian.brf")), files.map { (name, bytes) ->
            BrouterArtifactDescriptor(name, "https://fixtures.example/$name", sha256(bytes))
        })
        return Fixture(root, descriptor, files, fingerprint)
    }

    private fun signature(pair: KeyPair, publicBlob: ByteArray, manifest: ByteArray): ByteArray {
        val namespace = BrouterTrustAnchor.SIGNATURE_NAMESPACE.toByteArray(StandardCharsets.US_ASCII)
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

    private data class Fixture(val root: Path, val descriptor: BrouterRegionalPackageDescriptor, val files: Map<String, ByteArray>, val fingerprint: String)
    private companion object { const val REVISION = "fixture-core"; const val MAP = "fixture-map" }
}
