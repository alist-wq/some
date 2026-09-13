package com.example.navigation.maps

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest

/** Immutable release inventory for a destination-search-only regional package. */
data class DestinationIndexArtifact(
    val name: String,
    val url: String,
    val sha256: String
) {
    init {
        require(name.matches(Regex("[A-Za-z0-9._-]+")))
        require(url.startsWith("https://"))
        require(!url.contains("/latest/"))
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

/**
 * Catalog metadata deliberately contains no routing-graph version: this package enables local
 * destination search only and cannot be used as evidence that a route can be calculated.
 */
data class DestinationIndexPackageDescriptor(
    val regionId: String,
    val packageVersion: String,
    val mapCompatibilityVersion: String,
    val lockedSourceUrl: String,
    val lockedSourceSha256: String,
    val attribution: String,
    val artifacts: List<DestinationIndexArtifact>
) {
    init {
        require(regionId.matches(Regex("[A-Za-z0-9._-]+")) && packageVersion.isNotBlank())
        require(mapCompatibilityVersion.isNotBlank())
        require(lockedSourceUrl.startsWith("https://") && !lockedSourceUrl.contains("latest"))
        require(lockedSourceSha256.matches(Regex("[0-9a-f]{64}")))
        require(attribution.contains("OpenStreetMap") && attribution.contains("ODbL"))
        require(artifacts.map { it.name }.distinct().size == artifacts.size)
        require(setOf("destination-index.jsonl", "manifest.json", "manifest.json.sig", "renav-destination-index-ed25519.pub").all { required -> artifacts.any { it.name == required } })
    }
}

fun interface DestinationIndexArtifactDownloader {
    fun download(artifact: DestinationIndexArtifact, destination: Path)
}

data class VerifiedDestinationIndexPackage(
    val regionId: String,
    val packageVersion: String,
    val mapCompatibilityVersion: String,
    val root: Path,
    val index: Path
)

sealed interface DestinationIndexPackageVerificationResult {
    data class Ready(val value: VerifiedDestinationIndexPackage) : DestinationIndexPackageVerificationResult
    data class Unavailable(val reason: String) : DestinationIndexPackageVerificationResult
}

fun interface DestinationIndexPackageVerifier {
    fun verify(expected: DestinationIndexPackageDescriptor, root: Path): DestinationIndexPackageVerificationResult
}

sealed interface DestinationIndexPackageInstallResult {
    data class Activated(val value: VerifiedDestinationIndexPackage) : DestinationIndexPackageInstallResult
    data class Unavailable(val reason: String) : DestinationIndexPackageInstallResult
}

/**
 * Owns only app-private destination-index package data. The caller supplies explicit user intent
 * and a signature/manifest verifier; interrupted staging data is never made searchable.
 */
class DestinationIndexPackageInstaller(
    root: Path,
    private val verifier: DestinationIndexPackageVerifier
) {
    private val root = root.toAbsolutePath().normalize()
    private val stagingRoot = this.root.resolve("staging")
    private val activeRoot = this.root.resolve("active")

    fun install(expected: DestinationIndexPackageDescriptor, downloader: DestinationIndexArtifactDownloader): DestinationIndexPackageInstallResult {
        val staging = stagingRoot.resolve("${expected.regionId}-${expected.packageVersion}")
        try {
            discard(staging)
            Files.createDirectories(staging)
            expected.artifacts.forEach { artifact ->
                val destination = staging.resolve(artifact.name).normalize()
                require(destination.startsWith(staging) && destination.parent == staging) { "Invalid destination-index artifact path" }
                downloader.download(artifact, destination)
                if (!Files.isRegularFile(destination) || sha256(destination) != artifact.sha256) return fail(staging, "Destination-index artifact checksum is invalid: ${artifact.name}")
            }
            val verified = verify(expected, staging) ?: return fail(staging, "Destination-index package is invalid")
            val target = activePath(expected)
            Files.createDirectories(target.parent)
            if (Files.exists(target)) return verify(expected, target)?.let(DestinationIndexPackageInstallResult::Activated)
                ?: fail(staging, "An invalid destination-index package occupies the activation path")
            try {
                Files.move(staging, target, ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                return fail(staging, "Destination-index activation requires atomic filesystem moves")
            }
            discardOtherVersions(target)
            return DestinationIndexPackageInstallResult.Activated(requireNotNull(verify(expected, target)))
        } catch (error: Exception) {
            return fail(staging, "Destination-index installation failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun activePackage(expected: DestinationIndexPackageDescriptor): DestinationIndexPackageVerificationResult {
        val target = activePath(expected)
        if (!Files.isDirectory(target)) return DestinationIndexPackageVerificationResult.Unavailable("Destination index is not installed")
        return verifier.verify(expected, target)
    }

    fun recoverInterruptedDownloads() = discard(stagingRoot)

    fun remove(regionId: String) {
        require(regionId.matches(Regex("[A-Za-z0-9._-]+")))
        discard(activeRoot.resolve(regionId))
        if (Files.isDirectory(stagingRoot)) Files.list(stagingRoot).use { paths -> paths.filter { it.fileName.toString().startsWith("$regionId-") }.forEach(::discard) }
    }

    private fun verify(expected: DestinationIndexPackageDescriptor, root: Path): VerifiedDestinationIndexPackage? =
        (verifier.verify(expected, root) as? DestinationIndexPackageVerificationResult.Ready)?.value?.takeIf {
            it.regionId == expected.regionId && it.packageVersion == expected.packageVersion &&
                it.mapCompatibilityVersion == expected.mapCompatibilityVersion && it.root == root && Files.isRegularFile(it.index)
        }

    private fun activePath(expected: DestinationIndexPackageDescriptor) = activeRoot.resolve(expected.regionId).resolve(expected.packageVersion)
    private fun discardOtherVersions(current: Path) = Files.list(current.parent).use { paths -> paths.filter { it != current }.forEach(::discard) }
    private fun fail(staging: Path, reason: String): DestinationIndexPackageInstallResult.Unavailable { discard(staging); return DestinationIndexPackageInstallResult.Unavailable(reason) }
    private fun discard(path: Path) { if (Files.exists(path)) Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
