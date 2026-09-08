package com.example.navigation.maps

import com.example.navigation.routing.GeoPoint

data class RegionalMapBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    init {
        require(south in -90.0..90.0 && north in -90.0..90.0 && south <= north)
        require(west in -180.0..180.0 && east in -180.0..180.0 && west <= east)
    }
    fun contains(point: GeoPoint): Boolean = point.latitude in south..north && point.longitude in west..east
}

data class RegionalMapPackage(
    val id: String,
    val displayName: String,
    val bounds: RegionalMapBounds,
    val styleUrl: String,
    val minimumZoom: Double,
    val maximumZoom: Double,
    val estimatedSizeBytes: Long,
    val mapVersion: String,
    val routingGraphVersion: String,
    val sha256: String
) {
    init {
        require(id.isNotBlank() && displayName.isNotBlank() && styleUrl.isNotBlank())
        require(minimumZoom >= 0.0 && maximumZoom >= minimumZoom)
        require(estimatedSizeBytes > 0L && mapVersion.isNotBlank() && routingGraphVersion.isNotBlank())
        require(sha256.matches(Regex("[0-9a-fA-F]{64}")))
    }
}

data class RegionalMapCatalog(val version: String, val packages: List<RegionalMapPackage>) {
    init {
        require(version.isNotBlank())
        require(packages.map { it.id }.distinct().size == packages.size)
    }
}

sealed interface RegionDiscoveryResult {
    data class Found(val packageInfo: RegionalMapPackage) : RegionDiscoveryResult
    data object PermissionDenied : RegionDiscoveryResult
    data object LocationUnavailable : RegionDiscoveryResult
    data object CatalogUnavailable : RegionDiscoveryResult
    data object UnsupportedRegion : RegionDiscoveryResult
    data class Ambiguous(val packageIds: List<String>) : RegionDiscoveryResult
}

object RegionalMapResolver {
    fun resolve(catalog: RegionalMapCatalog?, locationPermissionGranted: Boolean, location: GeoPoint?): RegionDiscoveryResult {
        if (!locationPermissionGranted) return RegionDiscoveryResult.PermissionDenied
        if (location == null) return RegionDiscoveryResult.LocationUnavailable
        if (catalog == null) return RegionDiscoveryResult.CatalogUnavailable
        val matches = catalog.packages.filter { it.bounds.contains(location) }
        return when (matches.size) {
            0 -> RegionDiscoveryResult.UnsupportedRegion
            1 -> RegionDiscoveryResult.Found(matches.single())
            else -> RegionDiscoveryResult.Ambiguous(matches.map { it.id }.sorted())
        }
    }
}

enum class RegionalMapAvailability { NOT_DOWNLOADED, DOWNLOADING, READY, STALE, CORRUPT }

data class InstalledRegionalMapEvidence(
    val mapVersion: String,
    val sizeBytes: Long,
    val sha256: String,
    val complete: Boolean
)

object RegionalMapAvailabilityEvaluator {
    fun evaluate(
        expected: RegionalMapPackage,
        installed: InstalledRegionalMapEvidence?,
        downloading: Boolean = false
    ): RegionalMapAvailability = when {
        downloading -> RegionalMapAvailability.DOWNLOADING
        installed == null -> RegionalMapAvailability.NOT_DOWNLOADED
        !installed.complete || installed.sizeBytes <= 0L || !installed.sha256.equals(expected.sha256, ignoreCase = true) -> RegionalMapAvailability.CORRUPT
        installed.mapVersion != expected.mapVersion -> RegionalMapAvailability.STALE
        else -> RegionalMapAvailability.READY
    }
}
