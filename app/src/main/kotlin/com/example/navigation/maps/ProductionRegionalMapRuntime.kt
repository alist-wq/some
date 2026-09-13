package com.example.navigation.maps

import android.content.Context
import java.security.MessageDigest

/** Approved small starter package. Downloads are always initiated by an explicit UI confirmation. */
object ProductionRegionalMapCatalog {
    const val OPEN_FREE_MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
    const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles Data from OpenStreetMap"
    const val LICENSE_URL = "https://openfreemap.org/"

    private fun packageFor(id: String, name: String, bounds: RegionalMapBounds, estimate: Long) = RegionalMapPackage(
        id, name, bounds, OPEN_FREE_MAP_STYLE, 8.0, 14.0, estimate, "openfreemap-2026-09", "unavailable", manifestHash(id, bounds)
    )

    val catalog = RegionalMapCatalog("2026-09", listOf(
        packageFor("ua-kharkiv-pedestrian", "Kharkiv, Ukraine", RegionalMapBounds(49.80, 35.90, 50.20, 36.50), 120_000_000L),
        packageFor("ua-lviv", "Lviv, Ukraine", RegionalMapBounds(49.70, 23.75, 50.05, 24.25), 85_000_000L)
    ))

    fun manifestHash(id: String, bounds: RegionalMapBounds): String = MessageDigest.getInstance("SHA-256")
        .digest("$id|$OPEN_FREE_MAP_STYLE|${bounds.south}|${bounds.west}|${bounds.north}|${bounds.east}|8.0|14.0|openfreemap-2026-09".toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun isApproved(packageInfo: RegionalMapPackage) = packageInfo.styleUrl == OPEN_FREE_MAP_STYLE &&
        packageInfo.sha256 == manifestHash(packageInfo.id, packageInfo.bounds)
}

class ActiveRegionalMapStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("active_regional_map", Context.MODE_PRIVATE)
    fun active(catalog: RegionalMapCatalog): RegionalMapPackage? = preferences.getString("package", null)?.let { id -> catalog.packages.firstOrNull { it.id == id } }
    fun activate(packageInfo: RegionalMapPackage) { preferences.edit().putString("package", packageInfo.id).apply() }
    fun clear() { preferences.edit().clear().apply() }
}

class ProductionRegionalMapRuntime(context: Context, private val onStateChanged: (RegionalMapDownloadState) -> Unit) {
    private val metadata = MapDataVersionMetadataStore(context)
    private val manager = MapLibreOfflineRegionManager(context, metadata)
    private val activeStore = ActiveRegionalMapStore(context)
    private val stateStore = PreferencesRegionalMapDownloadStateStore(context)
    /** Destination search data is managed separately and never represents a routing graph. */
    val destinationIndexRuntime = ProductionDestinationIndexPackageRuntime(context.filesDir.toPath(), ::activePackage)
    val orchestrator = RegionalMapDownloadOrchestrator(
        MapLibreRegionalMapDownloadBackend(manager),
        RegionalMapPackageVerifier { packageInfo, regionId, bytes ->
            val record = metadata.get(regionId)
            InstalledRegionalMapEvidence(
                record?.mapVersion ?: "", bytes, if (ProductionRegionalMapCatalog.isApproved(packageInfo)) packageInfo.sha256 else "", bytes > 0L
            )
        },
        RegionalMapActivator { packageInfo, _ -> activeStore.activate(packageInfo) }, stateStore, onStateChanged
    )
    init {
        destinationIndexRuntime.recoverInterruptedDownloads()
    }
    fun activePackage(): RegionalMapPackage? = activeStore.active(ProductionRegionalMapCatalog.catalog)
    fun removeActive(onDone: () -> Unit, onError: (String) -> Unit) {
        val id = orchestrator.state.regionId ?: return onDone()
        manager.delete(id, {
            destinationIndexRuntime.removeForActiveMap()
            activeStore.clear(); stateStore.clear(); onStateChanged(RegionalMapDownloadState()); onDone()
        }, onError)
    }
}
