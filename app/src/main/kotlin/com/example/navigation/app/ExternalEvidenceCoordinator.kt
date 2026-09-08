package com.example.navigation.app

import android.os.SystemClock
import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.*
import com.example.navigation.replay.RecordingSessionController
import com.example.navigation.replay.ExternalEvidenceStatusSessionRecord
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.max

interface ExternalEvidenceStreamListener {
    fun onOpen()
    fun onMessage(text: String)
    fun onDisconnected()
}

interface ExternalEvidenceTransport : AutoCloseable {
    fun get(url: String, result: (Result<String>) -> Unit)
    fun openWebSocket(url: String, listener: ExternalEvidenceStreamListener): AutoCloseable
    override fun close() = Unit
}

class OkHttpExternalEvidenceTransport(private val client: OkHttpClient) : ExternalEvidenceTransport {
    override fun get(url: String, result: (Result<String>) -> Unit) {
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) = result(Result.failure(e))
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) result(Result.failure(java.io.IOException("HTTP ${it.code}")))
                    else result(Result.success(it.body?.string().orEmpty()))
                }
            }
        })
    }

    override fun openWebSocket(url: String, listener: ExternalEvidenceStreamListener): AutoCloseable {
        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onDisconnected()
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onDisconnected()
        })
        return AutoCloseable { socket.close(1000, "recording stopped") }
    }
}

data class ExternalEvidenceSnapshot(
    val spacecraft: ExternalEvidenceStatus? = null,
    val airAlert: ExternalEvidenceStatus? = null,
    val latestAirAlertMarker: AirAlertContextMarker? = null
)

data class ExternalEvidenceEndpoints(
    val celesTrakTle: String = ExternalEvidenceCoordinator.CELESTRAK_URL,
    val neptunAlerts: String = ExternalEvidenceCoordinator.NEPTUN_ALERTS_URL,
    val neptunRegions: String = ExternalEvidenceCoordinator.NEPTUN_RAIONS_URL,
    val neptunStream: String = ExternalEvidenceCoordinator.NEPTUN_STREAM_URL
)

internal data class RegionPolygon(val key: String, val rings: List<List<Pair<Double, Double>>>)

internal object NeptunRegionGeoJson {
    fun parse(body: String): List<RegionPolygon> {
        val features = JSONObject(body).getJSONArray("features")
        return buildList {
            for (index in 0 until features.length()) {
                val feature = features.getJSONObject(index)
                val key = feature.getJSONObject("properties").optString("key")
                if (key.isBlank()) continue
                val geometry = feature.getJSONObject("geometry")
                val coordinates = geometry.getJSONArray("coordinates")
                when (geometry.getString("type")) {
                    "Polygon" -> add(RegionPolygon(key, polygonRings(coordinates)))
                    "MultiPolygon" -> for (polygon in 0 until coordinates.length()) {
                        add(RegionPolygon(key, polygonRings(coordinates.getJSONArray(polygon))))
                    }
                }
            }
        }
    }

    fun containing(polygons: List<RegionPolygon>, latitude: Double, longitude: Double): String? =
        polygons.firstOrNull { polygon -> polygon.rings.firstOrNull()?.let { contains(it, longitude, latitude) } == true }?.key

    private fun polygonRings(value: JSONArray) = buildList {
        for (ringIndex in 0 until value.length()) {
            val ring = value.getJSONArray(ringIndex)
            add(buildList {
                for (pointIndex in 0 until ring.length()) {
                    val point = ring.getJSONArray(pointIndex)
                    add(point.getDouble(0) to point.getDouble(1))
                }
            })
        }
    }

    private fun contains(ring: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var inside = false
        var previous = ring.lastIndex
        for (current in ring.indices) {
            val (xi, yi) = ring[current]
            val (xj, yj) = ring[previous]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            previous = current
        }
        return inside
    }
}

/** Owns optional network evidence for one active recording. */
class ExternalEvidenceCoordinator(
    cacheDirectory: File,
    private val controller: RecordingSessionController,
    private val onSnapshot: (ExternalEvidenceSnapshot) -> Unit,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build(),
    private val endpoints: ExternalEvidenceEndpoints = ExternalEvidenceEndpoints(),
    private val monotonicNow: () -> MonotonicTimestamp = { MonotonicTimestamp(SystemClock.elapsedRealtimeNanos()) },
    private val wallClockNow: () -> Long = System::currentTimeMillis,
    private val transport: ExternalEvidenceTransport = OkHttpExternalEvidenceTransport(client)
) : AutoCloseable {
    private val tleCache = TleCatalogCache(cacheDirectory.toPath().resolve("celestrak-gnss.tlecache"))
    private var catalog = tleCache.read()
    val referenceProvider = CachedTleSpacecraftReferenceProvider({ catalog }, wallClockNow)
    private val mapper = AirAlertMarkerMapper(monotonicNow)
    private var socket: AutoCloseable? = null
    private var active = false
    private var latestLocation: Triple<Double, Double, Double>? = null
    private var regions = emptyList<RegionPolygon>()
    private var alertKeys = emptySet<String>()
    private var localAlert: Boolean? = null
    private var reconnectAttempt = 0
    private var airReceiptGeneration = 0L
    private val reconnectExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    private var snapshot = ExternalEvidenceSnapshot()

    fun start() {
        if (active) return
        active = true
        publishSpacecraftCacheState()
        fetchTles()
        fetchRegionsAndAlertSnapshot()
        openSocket()
    }

    fun updateLocation(timestamp: MonotonicTimestamp, wallClockEpochMillis: Long, latitude: Double, longitude: Double, altitudeMeters: Double) {
        latestLocation = Triple(latitude, longitude, altitudeMeters)
        referenceProvider.updateObserver(ObserverFix(timestamp, wallClockEpochMillis, latitude, longitude, altitudeMeters))
        evaluateLocalAlert()
    }

    private fun fetchTles() {
        request(endpoints.celesTrakTle) { body ->
            val fetched = runCatching {
                tleCache.write(wallClockNow(), body)
                tleCache.read() ?: error("cache validation failed")
            }
            if (fetched.isSuccess) {
                catalog = fetched.getOrThrow()
                status(ExternalEvidenceSource.SPACECRAFT_REFERENCE, ExternalEvidenceState.AVAILABLE, "CelesTrak", 0.0)
            } else status(ExternalEvidenceSource.SPACECRAFT_REFERENCE, ExternalEvidenceState.ERROR, "CelesTrak", catalogAgeSeconds(), "refresh failed")
        }
    }

    private fun publishSpacecraftCacheState() {
        val age = catalogAgeSeconds()
        status(ExternalEvidenceSource.SPACECRAFT_REFERENCE, when {
            age == null -> ExternalEvidenceState.UNAVAILABLE
            age <= TLE_MAX_AGE_SECONDS -> ExternalEvidenceState.AVAILABLE
            else -> ExternalEvidenceState.STALE
        }, "CelesTrak", age)
    }

    private fun fetchRegionsAndAlertSnapshot() {
        request(endpoints.neptunRegions) { body ->
            regions = runCatching { NeptunRegionGeoJson.parse(body) }.getOrElse {
                status(ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.ERROR, "NEPTUN", detail = "region data malformed")
                emptyList()
            }
            fetchAlertSnapshot()
        }
    }

    private fun fetchAlertSnapshot() = request(endpoints.neptunAlerts) { applyAlerts(it) }

    private fun openSocket() {
        if (!active) return
        socket = transport.openWebSocket(endpoints.neptunStream, object : ExternalEvidenceStreamListener {
            override fun onOpen() {
                reconnectAttempt = 0
                status(ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.AVAILABLE, "NEPTUN")
            }
            override fun onMessage(text: String) {
                runCatching {
                    val envelope = JSONObject(text)
                    if (envelope.optString("type") == "alerts") applyAlerts(envelope.getJSONObject("data").toString())
                }.onFailure { status(ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.ERROR, "NEPTUN", detail = "malformed stream frame") }
            }
            override fun onDisconnected() = disconnected()
        })
    }

    private fun disconnected() {
        if (!active) return
        status(ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.DISCONNECTED, "NEPTUN")
        fetchAlertSnapshot()
        val delay = minOf(60_000L, 1_000L shl minOf(reconnectAttempt++, 6))
        reconnectExecutor.schedule({ openSocket() }, delay, TimeUnit.MILLISECONDS)
    }

    private fun applyAlerts(body: String) {
        val root = JSONObject(body)
        alertKeys = buildSet {
            listOf("raions", "oblasts").forEach { field ->
                val values = root.optJSONArray(field) ?: JSONArray()
                for (index in 0 until values.length()) add(values.getJSONObject(index).getString("key"))
            }
        }
        status(ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.AVAILABLE, "NEPTUN")
        val generation = ++airReceiptGeneration
        reconnectExecutor.schedule({
            if (active && airReceiptGeneration == generation) {
                status(ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.STALE, "NEPTUN", ALERT_STALE_MILLIS / 1_000.0)
            }
        }, ALERT_STALE_MILLIS, TimeUnit.MILLISECONDS)
        evaluateLocalAlert()
    }

    private fun evaluateLocalAlert() {
        val location = latestLocation ?: return
        val key = NeptunRegionGeoJson.containing(regions, location.first, location.second) ?: return
        val activeNow = alertKeys.any { it == key || key.startsWith("$it:") || it.startsWith("$key:") }
        val previous = localAlert
        localAlert = activeNow
        if (previous != null && previous != activeNow || previous == null && activeNow) {
            val marker = mapper.map(AirAlertSourceEvent(
                if (activeNow) AirAlertTransition.AIR_ALERT_STARTED else AirAlertTransition.AIR_ALERT_ENDED,
                "NEPTUN", ContextSourceStatus.AVAILABLE
            ))
            controller.append(marker)
            snapshot = snapshot.copy(latestAirAlertMarker = marker)
            onSnapshot(snapshot)
        }
    }

    private fun request(url: String, success: (String) -> Unit) {
        transport.get(url) { result ->
            result.onFailure {
                if (url == endpoints.celesTrakTle) publishSpacecraftCacheState()
                else status(ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.DISCONNECTED, "NEPTUN")
            }.onSuccess { body ->
                runCatching { success(body) }.onFailure { error ->
                    if (url == endpoints.celesTrakTle) {
                        status(ExternalEvidenceSource.SPACECRAFT_REFERENCE, ExternalEvidenceState.ERROR, "CelesTrak", catalogAgeSeconds(), error::class.simpleName)
                    } else {
                        status(ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.ERROR, "NEPTUN", detail = error::class.simpleName)
                    }
                }
            }
        }
    }

    private fun status(source: ExternalEvidenceSource, state: ExternalEvidenceState, provider: String, age: Double? = null, detail: String? = null) {
        if (!active) return
        val value = ExternalEvidenceStatus(monotonicNow(), source, state, provider, age, detail)
        controller.append(ExternalEvidenceStatusSessionRecord(value))
        snapshot = if (source == ExternalEvidenceSource.SPACECRAFT_REFERENCE) snapshot.copy(spacecraft = value) else snapshot.copy(airAlert = value)
        onSnapshot(snapshot)
    }

    private fun catalogAgeSeconds() = catalog?.let { max(0L, wallClockNow() - it.retrievedAtEpochMillis) / 1_000.0 }

    override fun close() {
        active = false
        socket?.close()
        socket = null
        reconnectExecutor.shutdownNow()
        transport.close()
    }

    companion object {
        const val CELESTRAK_URL = "https://celestrak.org/NORAD/elements/gp.php?GROUP=GNSS&FORMAT=TLE"
        const val NEPTUN_ALERTS_URL = "https://neptun.in.ua/api/v1/alerts"
        const val NEPTUN_RAIONS_URL = "https://neptun.in.ua/raions.geojson"
        const val NEPTUN_STREAM_URL = "wss://neptun.in.ua/api/v1/stream"
        const val TLE_MAX_AGE_SECONDS = 72.0 * 60 * 60
        const val ALERT_STALE_MILLIS = 90_000L
    }
}
