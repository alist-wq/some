package com.example.navigation.routing

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** Retrieves one immutable catalog artifact into an installer-owned staging location. */
fun interface BrouterArtifactDownloader {
    fun download(artifact: BrouterArtifactDescriptor, destination: Path)
}

sealed interface BrouterPackageInstallResult {
    data class Activated(val value: VerifiedBrouterPackage) : BrouterPackageInstallResult
    data class Unavailable(val reason: String) : BrouterPackageInstallResult
}

/**
 * Owns only app-private BRouter package files. A caller must invoke [install] after an explicit
 * map-download action; this class deliberately has no Android lifecycle or network policy.
 */
class BrouterPackageInstaller(
    private val root: Path,
    private val verifierFor: (BrouterRegionalPackageDescriptor) -> BrouterPackageVerifier = {
        BrouterPackageVerifier(it.brouterRevision)
    }
) {
    private val normalizedRoot = root.toAbsolutePath().normalize()
    private val stagingRoot = normalizedRoot.resolve("staging")
    private val activeRoot = normalizedRoot.resolve("active")

    /** Stages every catalog artifact, verifies it, then atomically promotes the complete package. */
    fun install(expected: BrouterRegionalPackageDescriptor, downloader: BrouterArtifactDownloader): BrouterPackageInstallResult {
        val staging = stagingPath(expected)
        try {
            discard(staging)
            Files.createDirectories(staging)
            expected.artifacts.forEach { artifact ->
                val destination = staging.resolve(artifact.name).normalize()
                require(destination.parent == staging && destination.startsWith(staging)) { "Invalid BRouter artifact destination" }
                downloader.download(artifact, destination)
                if (!Files.isRegularFile(destination) || sha256(destination) != artifact.sha256) {
                    return failed(staging, "BRouter artifact checksum is invalid: ${artifact.name}")
                }
            }
            val verified = verify(expected, staging) ?: return failed(staging, "Downloaded BRouter package is unavailable")
            val target = activePath(expected)
            Files.createDirectories(target.parent)
            if (Files.exists(target)) {
                val existing = verify(expected, target)
                if (existing != null) return BrouterPackageInstallResult.Activated(existing)
                return failed(staging, "An invalid BRouter package already occupies the activation path")
            }
            try {
                Files.move(staging, target, ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                return failed(staging, "BRouter package activation requires atomic filesystem moves")
            }
            discardOtherVersions(expected, target)
            return BrouterPackageInstallResult.Activated(requireNotNull(verify(expected, target)))
        } catch (error: Exception) {
            return failed(staging, "BRouter package installation failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    /** Discards incomplete staging data. It never removes a verified active package. */
    fun recoverInterruptedDownloads() {
        discard(stagingRoot)
    }

    /** Returns an adapter-ready package only when its catalog, manifest, and all artifacts verify. */
    fun activePackage(expected: BrouterRegionalPackageDescriptor): BrouterPackageVerificationResult {
        val target = activePath(expected)
        if (!Files.isDirectory(target)) return BrouterPackageVerificationResult.Unavailable("BRouter package is not installed")
        return verify(expected, target)?.let(BrouterPackageVerificationResult::Ready)
            ?: BrouterPackageVerificationResult.Unavailable("Installed BRouter package is invalid or incompatible")
    }

    /** Removes all active and staged data for one region, making subsequent lookup explicitly unavailable. */
    fun remove(regionId: String) {
        require(regionId.matches(Regex("[A-Za-z0-9._-]+")))
        discard(activeRoot.resolve(regionId))
        if (Files.isDirectory(stagingRoot)) {
            Files.list(stagingRoot).use { paths -> paths.filter { it.fileName.toString().startsWith("$regionId-") }.forEach(::discard) }
        }
    }

    private fun verify(expected: BrouterRegionalPackageDescriptor, directory: Path): VerifiedBrouterPackage? {
        if (!Files.isDirectory(directory) || expected.artifacts.any { artifact ->
                val file = directory.resolve(artifact.name)
                !Files.isRegularFile(file) || sha256(file) != artifact.sha256
            }) return null
        val result = verifierFor(expected).verify(directory, expected.mapCompatibilityVersion)
        val verified = (result as? BrouterPackageVerificationResult.Ready)?.value ?: return null
        return verified.takeIf {
            it.regionId == expected.regionId && it.packageVersion == expected.packageVersion &&
                it.mapCompatibilityVersion == expected.mapCompatibilityVersion && it.brouterRevision == expected.brouterRevision
        }
    }

    private fun stagingPath(expected: BrouterRegionalPackageDescriptor) = stagingRoot.resolve("${expected.regionId}-${expected.packageVersion}")
    private fun activePath(expected: BrouterRegionalPackageDescriptor) = activeRoot.resolve(expected.regionId).resolve(expected.packageVersion)

    private fun discardOtherVersions(expected: BrouterRegionalPackageDescriptor, current: Path) {
        val regionRoot = current.parent
        Files.list(regionRoot).use { paths -> paths.filter { it != current }.forEach(::discard) }
    }

    private fun failed(staging: Path, reason: String): BrouterPackageInstallResult.Unavailable {
        discard(staging)
        return BrouterPackageInstallResult.Unavailable(reason)
    }

    private fun discard(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
