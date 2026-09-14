package com.example.navigation.maps

import com.example.navigation.routing.BrouterArtifactDescriptor
import com.example.navigation.routing.BrouterArtifactDownloader
import com.example.navigation.routing.BrouterPackageInstallResult
import com.example.navigation.routing.BrouterPackageInstaller
import com.example.navigation.routing.BrouterPackageVerificationResult
import com.example.navigation.routing.BrouterRegionalPackageCatalog
import com.example.navigation.routing.BrouterRegionalPackageDescriptor
import com.example.navigation.routing.ProductionBrouterPackageCatalog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * App-private persistence bridge for explicitly requested BRouter package installation.
 *
 * It only permits the descriptor whose ID and map version match the currently active map. The
 * caller is responsible for invoking [installForActiveMap] off the main thread and only after a
 * user has explicitly requested the associated map download.
 */
class ProductionBrouterPackageRuntime(
    filesRoot: Path,
    private val activeMapPackage: () -> RegionalMapPackage?,
    private val catalog: BrouterRegionalPackageCatalog = ProductionBrouterPackageCatalog.catalog,
    private val downloader: BrouterArtifactDownloader = OkHttpBrouterArtifactDownloader(),
    private val installer: BrouterPackageInstaller = BrouterPackageInstaller(filesRoot.resolve("routing").resolve("brouter"))
) {
    fun installForActiveMap(): BrouterPackageInstallResult {
        val expected = expectedForActiveMap()
            ?: return BrouterPackageInstallResult.Unavailable(unavailableReason())
        return installer.install(expected, downloader)
    }

    fun activePackage(): BrouterPackageVerificationResult {
        val expected = expectedForActiveMap()
            ?: return BrouterPackageVerificationResult.Unavailable(unavailableReason())
        return installer.activePackage(expected)
    }

    fun recoverInterruptedDownloads() = installer.recoverInterruptedDownloads()

    fun removeForActiveMap(): BrouterPackageVerificationResult.Unavailable {
        val expected = expectedForActiveMap() ?: return BrouterPackageVerificationResult.Unavailable(unavailableReason())
        installer.remove(expected.regionId)
        return BrouterPackageVerificationResult.Unavailable("BRouter package removed")
    }

    private fun expectedForActiveMap(): BrouterRegionalPackageDescriptor? {
        val map = activeMapPackage() ?: return null
        val expected = catalog.packageFor(map.id) ?: return null
        return expected.takeIf { it.mapCompatibilityVersion == map.mapVersion }
    }

    private fun unavailableReason(): String {
        val map = activeMapPackage() ?: return "No active map package is available for BRouter"
        val expected = catalog.packageFor(map.id)
            ?: return "No approved BRouter package matches active map ${map.id}"
        return "BRouter package ${expected.regionId} is incompatible with active map version ${map.mapVersion}"
    }
}

/** Downloads only the immutable HTTPS URLs already accepted by the core catalog contract. */
class OkHttpBrouterArtifactDownloader(
    private val client: OkHttpClient = OkHttpClient()
) : BrouterArtifactDownloader {
    override fun download(artifact: BrouterArtifactDescriptor, destination: Path) {
        val request = Request.Builder().url(artifact.url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("BRouter artifact download failed: HTTP ${response.code}")
            val body = response.body ?: throw IllegalStateException("BRouter artifact download returned no body")
            Files.newOutputStream(destination, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
    }
}
