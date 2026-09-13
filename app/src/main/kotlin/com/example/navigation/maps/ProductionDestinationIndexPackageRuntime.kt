package com.example.navigation.maps

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * App-private bridge for an explicit destination-index download. It never treats the index as a
 * routing graph and it permits installation only for the matching active map version.
 */
class ProductionDestinationIndexPackageRuntime(
    filesRoot: Path,
    private val activeMapPackage: () -> RegionalMapPackage?,
    private val catalog: DestinationIndexPackageCatalog = ProductionDestinationIndexPackageCatalog.catalog,
    private val downloader: DestinationIndexArtifactDownloader = OkHttpDestinationIndexArtifactDownloader(),
    private val installer: DestinationIndexPackageInstaller = DestinationIndexPackageInstaller(
        filesRoot.resolve("maps").resolve("destination-index"),
        SignedDestinationIndexPackageVerifier()
    )
) {
    fun installForActiveMap(): DestinationIndexPackageInstallResult {
        val expected = expectedForActiveMap() ?: return DestinationIndexPackageInstallResult.Unavailable(unavailableReason())
        return installer.install(expected, downloader)
    }

    fun activePackage(): DestinationIndexPackageVerificationResult {
        val expected = expectedForActiveMap() ?: return DestinationIndexPackageVerificationResult.Unavailable(unavailableReason())
        return installer.activePackage(expected)
    }

    /** Returns search data only after the app-private package has passed the release verifier. */
    fun activeSearchIndex(): DestinationIndexLoadResult = when (val active = activePackage()) {
        is DestinationIndexPackageVerificationResult.Ready -> DestinationIndexPackageLoader.load(active.value)
        is DestinationIndexPackageVerificationResult.Unavailable -> DestinationIndexLoadResult.IndexUnavailable(active.reason)
    }

    fun recoverInterruptedDownloads() = installer.recoverInterruptedDownloads()

    fun removeForActiveMap(): DestinationIndexPackageVerificationResult.Unavailable {
        val expected = expectedForActiveMap() ?: return DestinationIndexPackageVerificationResult.Unavailable(unavailableReason())
        installer.remove(expected.regionId)
        return DestinationIndexPackageVerificationResult.Unavailable("Destination index removed")
    }

    private fun expectedForActiveMap(): DestinationIndexPackageDescriptor? {
        val map = activeMapPackage() ?: return null
        return catalog.packageFor(map.id)?.takeIf { it.mapCompatibilityVersion == map.mapVersion }
    }

    private fun unavailableReason(): String {
        val map = activeMapPackage() ?: return "No active map package is available for the destination index"
        val expected = catalog.packageFor(map.id) ?: return "No approved destination index matches active map ${map.id}"
        return "Destination index ${expected.regionId} is incompatible with active map version ${map.mapVersion}"
    }
}

/** Downloads only catalog-pinned immutable HTTPS release assets. */
class OkHttpDestinationIndexArtifactDownloader(
    private val client: OkHttpClient = OkHttpClient()
) : DestinationIndexArtifactDownloader {
    override fun download(artifact: DestinationIndexArtifact, destination: Path) {
        client.newCall(Request.Builder().url(artifact.url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Destination-index artifact download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("Destination-index artifact download returned no body")
            Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
    }
}
