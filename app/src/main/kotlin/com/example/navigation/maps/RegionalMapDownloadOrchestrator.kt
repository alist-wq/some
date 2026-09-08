package com.example.navigation.maps

import android.content.Context
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

enum class RegionalMapDownloadPhase {
    IDLE, PREPARING, DOWNLOADING, PAUSED, CANCELLED, FAILED, VERIFYING, READY, STALE, CORRUPT
}

data class RegionalMapDownloadState(
    val packageId: String? = null,
    val phase: RegionalMapDownloadPhase = RegionalMapDownloadPhase.IDLE,
    val regionId: Long? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val failureReason: String? = null,
    val retryable: Boolean = false
)

data class RegionalMapBackendProgress(
    val regionId: Long,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val complete: Boolean
)

interface RegionalMapDownloadBackend {
    fun start(packageInfo: RegionalMapPackage, onProgress: (RegionalMapBackendProgress) -> Unit, onError: (String) -> Unit)
    fun pause(regionId: Long, onComplete: () -> Unit, onError: (String) -> Unit)
    fun resume(packageInfo: RegionalMapPackage, regionId: Long, onProgress: (RegionalMapBackendProgress) -> Unit, onComplete: () -> Unit, onError: (String) -> Unit)
}

class MapLibreRegionalMapDownloadBackend(private val manager: MapLibreOfflineRegionManager) : RegionalMapDownloadBackend {
    override fun start(packageInfo: RegionalMapPackage, onProgress: (RegionalMapBackendProgress) -> Unit, onError: (String) -> Unit) {
        val bounds = LatLngBounds.Builder()
            .include(LatLng(packageInfo.bounds.south, packageInfo.bounds.west))
            .include(LatLng(packageInfo.bounds.north, packageInfo.bounds.east))
            .build()
        manager.download(
            OfflineRegionRequest(
                packageInfo.id, bounds, packageInfo.minimumZoom, packageInfo.maximumZoom,
                packageInfo.styleUrl, packageInfo.mapVersion, packageInfo.routingGraphVersion
            ),
            onProgress = { info ->
                onProgress(RegionalMapBackendProgress(
                    info.id, info.downloadedBytes, null, info.isComplete
                ))
            },
            onError = onError
        )
    }

    override fun pause(regionId: Long, onComplete: () -> Unit, onError: (String) -> Unit) = manager.cancel(regionId, onComplete, onError)
    override fun resume(
        packageInfo: RegionalMapPackage,
        regionId: Long,
        onProgress: (RegionalMapBackendProgress) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) = manager.resume(regionId, { info ->
        onProgress(RegionalMapBackendProgress(info.id, info.downloadedBytes, null, info.isComplete))
    }, onComplete, onError)
}

fun interface RegionalMapPackageVerifier {
    fun verify(packageInfo: RegionalMapPackage, regionId: Long, downloadedBytes: Long): InstalledRegionalMapEvidence
}

fun interface RegionalMapActivator {
    fun activate(packageInfo: RegionalMapPackage, regionId: Long)
}

interface RegionalMapDownloadStateStore {
    fun save(state: RegionalMapDownloadState)
    fun load(): RegionalMapDownloadState?
    fun clear()
}

class PreferencesRegionalMapDownloadStateStore(context: Context) : RegionalMapDownloadStateStore {
    private val preferences = context.applicationContext.getSharedPreferences("regional_map_download_state", Context.MODE_PRIVATE)
    override fun save(state: RegionalMapDownloadState) {
        preferences.edit().putString("packageId", state.packageId).putString("phase", state.phase.name)
            .putLong("regionId", state.regionId ?: -1L).putLong("downloadedBytes", state.downloadedBytes)
            .putLong("totalBytes", state.totalBytes ?: -1L).putString("failureReason", state.failureReason)
            .putBoolean("retryable", state.retryable).apply()
    }
    override fun load(): RegionalMapDownloadState? {
        val packageId = preferences.getString("packageId", null) ?: return null
        return runCatching { RegionalMapDownloadState(
            packageId, RegionalMapDownloadPhase.valueOf(requireNotNull(preferences.getString("phase", null))),
            preferences.getLong("regionId", -1L).takeIf { it >= 0L }, preferences.getLong("downloadedBytes", 0L),
            preferences.getLong("totalBytes", -1L).takeIf { it >= 0L }, preferences.getString("failureReason", null),
            preferences.getBoolean("retryable", false)
        ) }.getOrNull()
    }
    override fun clear() { preferences.edit().clear().apply() }
}

/** Download callbacks update this state only; navigation and rendering remain independent consumers. */
class RegionalMapDownloadOrchestrator(
    private val backend: RegionalMapDownloadBackend,
    private val verifier: RegionalMapPackageVerifier,
    private val activator: RegionalMapActivator,
    private val store: RegionalMapDownloadStateStore,
    private val onStateChanged: (RegionalMapDownloadState) -> Unit = {}
) {
    var state = RegionalMapDownloadState()
        private set
    private var selectedPackage: RegionalMapPackage? = null

    fun prepare(packageInfo: RegionalMapPackage): RegionalMapDownloadState {
        selectedPackage = packageInfo
        return update(RegionalMapDownloadState(packageInfo.id, RegionalMapDownloadPhase.PREPARING))
    }

    fun start(): RegionalMapDownloadState {
        val packageInfo = requireNotNull(selectedPackage)
        update(state.copy(phase = RegionalMapDownloadPhase.DOWNLOADING, failureReason = null, retryable = false))
        backend.start(packageInfo, ::progress, ::fail)
        return state
    }

    fun pause(): RegionalMapDownloadState {
        val id = requireNotNull(state.regionId)
        backend.pause(id, { update(state.copy(phase = RegionalMapDownloadPhase.PAUSED)) }, ::fail)
        return state
    }

    fun cancel(): RegionalMapDownloadState {
        val id = state.regionId
        if (id == null) return update(state.copy(phase = RegionalMapDownloadPhase.CANCELLED, retryable = true))
        backend.pause(id, { update(state.copy(phase = RegionalMapDownloadPhase.CANCELLED, retryable = true)) }, ::fail)
        return state
    }

    fun retry(): RegionalMapDownloadState {
        require(state.phase in setOf(RegionalMapDownloadPhase.PAUSED, RegionalMapDownloadPhase.CANCELLED, RegionalMapDownloadPhase.FAILED, RegionalMapDownloadPhase.CORRUPT, RegionalMapDownloadPhase.STALE))
        val id = state.regionId
        if (id == null) return start()
        update(state.copy(phase = RegionalMapDownloadPhase.DOWNLOADING, failureReason = null, retryable = false))
        backend.resume(requireNotNull(selectedPackage), id, ::progress, {}, ::fail)
        return state
    }

    fun recover(packageInfo: RegionalMapPackage): RegionalMapDownloadState {
        selectedPackage = packageInfo
        val saved = store.load()?.takeIf { it.packageId == packageInfo.id } ?: return update(RegionalMapDownloadState())
        return if (saved.phase == RegionalMapDownloadPhase.READY && saved.regionId != null) verify(saved)
        else update(saved.copy(phase = RegionalMapDownloadPhase.PAUSED, retryable = true, failureReason = "Download interrupted"))
    }

    private fun progress(progress: RegionalMapBackendProgress) {
        val next = state.copy(regionId = progress.regionId, downloadedBytes = progress.downloadedBytes, totalBytes = progress.totalBytes)
        if (progress.complete) verify(next.copy(phase = RegionalMapDownloadPhase.VERIFYING)) else update(next)
    }

    private fun verify(verifying: RegionalMapDownloadState): RegionalMapDownloadState {
        update(verifying.copy(phase = RegionalMapDownloadPhase.VERIFYING))
        val packageInfo = requireNotNull(selectedPackage)
        val id = requireNotNull(verifying.regionId)
        val evidence = verifier.verify(packageInfo, id, verifying.downloadedBytes)
        return when (RegionalMapAvailabilityEvaluator.evaluate(packageInfo, evidence)) {
            RegionalMapAvailability.READY -> {
                activator.activate(packageInfo, id)
                update(verifying.copy(phase = RegionalMapDownloadPhase.READY, failureReason = null, retryable = false))
            }
            RegionalMapAvailability.STALE -> update(verifying.copy(phase = RegionalMapDownloadPhase.STALE, failureReason = "Downloaded map version is stale", retryable = true))
            else -> update(verifying.copy(phase = RegionalMapDownloadPhase.CORRUPT, failureReason = "Downloaded map verification failed", retryable = true))
        }
    }

    private fun fail(reason: String) { update(state.copy(phase = RegionalMapDownloadPhase.FAILED, failureReason = reason, retryable = true)) }
    private fun update(next: RegionalMapDownloadState): RegionalMapDownloadState { state = next; store.save(next); onStateChanged(next); return next }
}
