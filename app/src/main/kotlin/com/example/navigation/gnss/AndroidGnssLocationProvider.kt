package com.example.navigation.gnss

import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.Build
import com.example.navigation.gnss.AndroidLocationEvidence
import com.example.navigation.gnss.EvidenceCapability
import com.example.navigation.core.MonotonicTimestamp

fun interface GnssSampleListener {
    fun onGnssSample(sample: GnssSample)
}

fun interface AndroidLocationEvidenceListener {
    fun onLocationEvidence(evidence: AndroidLocationEvidence)
}

/** Collects GPS-provider locations and immediately converts them to internal GNSS models. */
class AndroidGnssLocationProvider(
    private val locationManager: LocationManager,
    private val callbackLooper: Looper,
    private val listener: GnssSampleListener,
    private val minIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val minDistanceMeters: Float = DEFAULT_MIN_DISTANCE_METERS,
    private val evidenceListener: AndroidLocationEvidenceListener = AndroidLocationEvidenceListener { }
) {
    private val lifecycle = GnssCollectorLifecycle()

    private val locationListener = LocationListener { location ->
        listener.onGnssSample(location.toGnssSample())
        evidenceListener.onLocationEvidence(location.toAndroidLocationEvidence())
    }

    init {
        require(minIntervalMillis >= 0) { "minIntervalMillis must not be negative" }
        require(minDistanceMeters >= 0f) { "minDistanceMeters must not be negative" }
    }

    /** Starts collection. The caller must have granted ACCESS_FINE_LOCATION. */
    fun start(): EvidenceCapability = lifecycle.start {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER, minIntervalMillis, minDistanceMeters, locationListener, callbackLooper
        )
        true
    }

    /** Stops collection and releases the Android listener registration. */
    fun stop() {
        lifecycle.stop { locationManager.removeUpdates(locationListener) }
    }

    fun capability(): EvidenceCapability = lifecycle.capability()

}

/** Converts Android locations at the GNSS boundary; Android types do not leave this package. */
internal fun Location.toGnssSample(): GnssSample = GnssSample(
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitude.takeIf { hasAltitude() },
        horizontalAccuracyMeters = accuracy.takeIf { hasAccuracy() },
        verticalAccuracyMeters = verticalAccuracyMeters.takeIf { hasVerticalAccuracy() },
        speedMetersPerSecond = speed.takeIf { hasSpeed() },
        speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond.takeIf { hasSpeedAccuracy() },
        bearingDegrees = bearing.takeIf { hasBearing() },
        bearingAccuracyDegrees = bearingAccuracyDegrees.takeIf { hasBearingAccuracy() },
        provider = provider ?: LocationManager.GPS_PROVIDER,
        timestamp = MonotonicTimestamp(elapsedRealtimeNanos),
        wallClockTimestampMillis = time
    )

internal fun Location.toAndroidLocationEvidence(): AndroidLocationEvidence = AndroidLocationEvidence(
    timestamp = MonotonicTimestamp(elapsedRealtimeNanos),
    mockLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider,
    mockCapability = EvidenceCapability.AVAILABLE
)

private const val DEFAULT_MIN_INTERVAL_MILLIS = 1_000L
private const val DEFAULT_MIN_DISTANCE_METERS = 0f
