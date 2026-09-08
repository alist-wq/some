package com.example.navigation.app

import com.example.navigation.maps.RegionDiscoveryResult
import com.example.navigation.maps.RegionalMapDownloadPhase
import com.example.navigation.maps.RegionalMapDownloadState
import com.example.navigation.maps.RegionalMapPackage

enum class RegionalMapPrimaryAction {
    REQUEST_LOCATION_PERMISSION,
    SELECT_REGION_MANUALLY,
    CONFIRM_DOWNLOAD,
    PAUSE,
    RESUME,
    CANCEL,
    RETRY,
    MANAGE_DOWNLOADED_MAPS,
    NONE
}

data class RegionalMapDownloadPresentation(
    val title: String,
    val detail: String,
    val packageInfo: RegionalMapPackage?,
    val availabilityLabel: String,
    val progressLabel: String?,
    val primaryAction: RegionalMapPrimaryAction,
    val secondaryAction: RegionalMapPrimaryAction = RegionalMapPrimaryAction.NONE,
    val ready: Boolean
)

/** Maps backend discovery/download states to explicit, non-misleading UI copy and actions. */
object RegionalMapDownloadPresentationFactory {
    fun create(discovery: RegionDiscoveryResult, download: RegionalMapDownloadState): RegionalMapDownloadPresentation = when (discovery) {
        RegionDiscoveryResult.PermissionDenied -> simple(
            "LOCATION PERMISSION NEEDED", "Allow location to find a regional map, or choose a region manually.",
            "LOCATION UNKNOWN", RegionalMapPrimaryAction.REQUEST_LOCATION_PERMISSION, RegionalMapPrimaryAction.SELECT_REGION_MANUALLY
        )
        RegionDiscoveryResult.LocationUnavailable -> simple(
            "LOCATION UNAVAILABLE", "Your location is not available. Choose a region manually to continue.",
            "LOCATION UNKNOWN", RegionalMapPrimaryAction.SELECT_REGION_MANUALLY
        )
        RegionDiscoveryResult.CatalogUnavailable -> simple(
            "MAP CATALOG UNAVAILABLE", "No downloadable regional map catalog is configured. Offline readiness cannot be confirmed.",
            "OFFLINE MAP UNAVAILABLE", RegionalMapPrimaryAction.NONE
        )
        RegionDiscoveryResult.UnsupportedRegion -> simple(
            "REGION NOT AVAILABLE", "A map package is not available for this location. Choose another region manually.",
            "UNSUPPORTED REGION", RegionalMapPrimaryAction.SELECT_REGION_MANUALLY
        )
        is RegionDiscoveryResult.Ambiguous -> simple(
            "CHOOSE A REGION", "More than one regional map matches this location. Select the correct region manually.",
            "REGION SELECTION NEEDED", RegionalMapPrimaryAction.SELECT_REGION_MANUALLY
        )
        is RegionDiscoveryResult.Found -> forPackage(discovery.packageInfo, download)
    }

    private fun forPackage(packageInfo: RegionalMapPackage, download: RegionalMapDownloadState): RegionalMapDownloadPresentation {
        val bytes = "${formatBytes(download.downloadedBytes)} downloaded"
        return when (download.phase) {
            RegionalMapDownloadPhase.IDLE, RegionalMapDownloadPhase.PREPARING -> packageState(
                packageInfo, "DOWNLOAD ${packageInfo.displayName}", "Version ${packageInfo.mapVersion} • ${formatBytes(packageInfo.estimatedSizeBytes)} estimated",
                "NOT DOWNLOADED", null, RegionalMapPrimaryAction.CONFIRM_DOWNLOAD
            )
            RegionalMapDownloadPhase.DOWNLOADING -> packageState(
                packageInfo, "DOWNLOADING ${packageInfo.displayName}", "Keep navigating while the map downloads.",
                "DOWNLOADING", bytes, RegionalMapPrimaryAction.PAUSE, RegionalMapPrimaryAction.CANCEL
            )
            RegionalMapDownloadPhase.PAUSED -> packageState(
                packageInfo, "DOWNLOAD PAUSED", "${download.failureReason ?: bytes}. Resume when ready.",
                "PAUSED", bytes, RegionalMapPrimaryAction.RESUME, RegionalMapPrimaryAction.CANCEL
            )
            RegionalMapDownloadPhase.CANCELLED -> packageState(
                packageInfo, "DOWNLOAD CANCELLED", "The map is not ready for offline use.",
                "NOT READY", bytes, RegionalMapPrimaryAction.RETRY
            )
            RegionalMapDownloadPhase.FAILED -> packageState(
                packageInfo, "DOWNLOAD FAILED", download.failureReason ?: "The download failed. Retry to continue.",
                "NOT READY", bytes, RegionalMapPrimaryAction.RETRY
            )
            RegionalMapDownloadPhase.VERIFYING -> packageState(
                packageInfo, "VERIFYING MAP", "Checking the downloaded package before it is used offline.",
                "VERIFYING", bytes, RegionalMapPrimaryAction.NONE
            )
            RegionalMapDownloadPhase.READY -> packageState(
                packageInfo, "OFFLINE MAP READY", "${packageInfo.displayName} is verified and ready for offline navigation.",
                "READY", null, RegionalMapPrimaryAction.MANAGE_DOWNLOADED_MAPS, ready = true
            )
            RegionalMapDownloadPhase.STALE -> packageState(
                packageInfo, "MAP UPDATE NEEDED", download.failureReason ?: "The installed package is stale and not ready.",
                "STALE", null, RegionalMapPrimaryAction.RETRY
            )
            RegionalMapDownloadPhase.CORRUPT -> packageState(
                packageInfo, "MAP VERIFICATION FAILED", download.failureReason ?: "The package is corrupt and is not ready.",
                "CORRUPT", null, RegionalMapPrimaryAction.RETRY
            )
        }
    }

    private fun simple(title: String, detail: String, availability: String, primary: RegionalMapPrimaryAction, secondary: RegionalMapPrimaryAction = RegionalMapPrimaryAction.NONE) =
        RegionalMapDownloadPresentation(title, detail, null, availability, null, primary, secondary, false)

    private fun packageState(
        packageInfo: RegionalMapPackage, title: String, detail: String, availability: String, progress: String?,
        primary: RegionalMapPrimaryAction, secondary: RegionalMapPrimaryAction = RegionalMapPrimaryAction.NONE, ready: Boolean = false
    ) = RegionalMapDownloadPresentation(title, detail, packageInfo, availability, progress, primary, secondary, ready)

    private fun formatBytes(value: Long): String = when {
        value >= 1_000_000_000L -> "%.1f GB".format(java.util.Locale.ROOT, value / 1_000_000_000.0)
        value >= 1_000_000L -> "%.1f MB".format(java.util.Locale.ROOT, value / 1_000_000.0)
        else -> "${value / 1_000} KB"
    }
}
