package com.example.navigation.maps

import android.content.Context
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.nio.charset.StandardCharsets

data class OfflineRegionRequest(
    val name: String,
    val bounds: LatLngBounds,
    val minimumZoom: Double,
    val maximumZoom: Double,
    val styleUrl: String,
    val mapVersion: String = "unknown",
    val routingGraphVersion: String = "unknown"
) {
    init {
        require(name.isNotBlank())
        require(minimumZoom >= 0.0)
        require(maximumZoom >= minimumZoom)
    }
}

data class OfflineRegionInfo(
    val id: Long,
    val name: String,
    val completedResources: Long,
    val requiredResources: Long,
    val downloadedBytes: Long,
    val isComplete: Boolean,
    val isDownloading: Boolean,
    val metadata: MapDataVersionMetadata? = null
)

/**
 * Thin MapLibre-backed region manager. Download progress and storage consumption come directly
 * from MapLibre OfflineRegionStatus; no navigation or routing behavior is introduced here.
 */
class MapLibreOfflineRegionManager(
    context: Context,
    private val metadataStore: MapDataVersionMetadataStore = MapDataVersionMetadataStore(context)
) {
    private val offlineManager = OfflineManager.getInstance(context.applicationContext)

    fun download(request: OfflineRegionRequest, onProgress: (OfflineRegionInfo) -> Unit, onError: (String) -> Unit) {
        val definition = OfflineTilePyramidRegionDefinition(
            request.styleUrl,
            request.bounds,
            request.minimumZoom,
            request.maximumZoom,
            1f
        )
        offlineManager.createOfflineRegion(definition, request.name.toByteArray(StandardCharsets.UTF_8),
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(region: OfflineRegion) {
                    metadataStore.save(
                        region.id,
                        MapDataVersionMetadata(
                            region = request.name,
                            downloadDateEpochMillis = System.currentTimeMillis(),
                            mapVersion = request.mapVersion,
                            sizeBytes = 0L,
                            routingGraphVersion = request.routingGraphVersion
                        )
                    )
                    region.setObserver(progressObserver(region, request.name, onProgress, onError))
                    region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }
                override fun onError(error: String) = onError(error)
            })
    }

    fun cancel(regionId: Long, onComplete: () -> Unit, onError: (String) -> Unit) = withRegion(regionId, onError) { region ->
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        onComplete()
    }

    fun resume(
        regionId: Long,
        onProgress: (OfflineRegionInfo) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = withRegion(regionId, onError) { region ->
        val name = metadataStore.get(regionId)?.region ?: region.metadata.toString(StandardCharsets.UTF_8)
        region.setObserver(progressObserver(region, name, onProgress, onError))
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        onComplete()
    }

    fun list(onComplete: (List<OfflineRegionInfo>) -> Unit, onError: (String) -> Unit) {
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(regions: Array<OfflineRegion>?) {
                val available = regions.orEmpty()
                if (available.isEmpty()) return onComplete(emptyList())
                val infos = MutableList<OfflineRegionInfo?>(available.size) { null }
                available.forEachIndexed { index, region ->
                    region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status == null) return onError("Offline region status was unavailable")
                            infos[index] = toInfo(region, status)
                            if (infos.all { it != null }) onComplete(infos.filterNotNull())
                        }
                        override fun onError(error: String?) = onError(error ?: "Offline region status failed")
                    })
                }
            }
            override fun onError(error: String) = onError(error)
        })
    }

    fun delete(regionId: Long, onComplete: () -> Unit, onError: (String) -> Unit) = withRegion(regionId, onError) { region ->
        region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                metadataStore.remove(regionId)
                onComplete()
            }
            override fun onError(error: String) = onError(error)
        })
    }

    private fun withRegion(id: Long, onError: (String) -> Unit, action: (OfflineRegion) -> Unit) {
        offlineManager.getOfflineRegion(id, object : OfflineManager.GetOfflineRegionCallback {
            override fun onRegion(region: OfflineRegion) = action(region)
            override fun onRegionNotFound() = onError("Offline region $id was not found")
            override fun onError(error: String) = onError(error)
        })
    }

    private fun progressObserver(
        region: OfflineRegion,
        name: String,
        onProgress: (OfflineRegionInfo) -> Unit,
        onError: (String) -> Unit
    ) = object : OfflineRegion.OfflineRegionObserver {
        override fun onStatusChanged(status: OfflineRegionStatus) {
            metadataStore.updateSize(region.id, status.completedResourceSize)
            onProgress(toInfo(region, status, name))
        }
        override fun onError(error: org.maplibre.android.offline.OfflineRegionError) = onError(error.reason)
        override fun mapboxTileCountLimitExceeded(limit: Long) = onError("Offline tile limit exceeded: $limit")
    }

    private fun toInfo(region: OfflineRegion, status: OfflineRegionStatus, name: String = region.metadata.toString(StandardCharsets.UTF_8)) =
        OfflineRegionInfo(
            id = region.id,
            name = name,
            completedResources = status.completedResourceCount,
            requiredResources = status.requiredResourceCount,
            downloadedBytes = status.completedResourceSize,
            isComplete = status.isComplete,
            isDownloading = status.downloadState == OfflineRegion.STATE_ACTIVE,
            metadata = metadataStore.get(region.id)
        )
}
