package com.example.navigation.gnss

import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Build

fun interface GnssStatusListener {
    fun onGnssStatus(status: GnssStatusSnapshot)
}

/** Collects Android satellite status and emits storage-safe internal snapshots. */
class AndroidGnssStatusCollector(
    private val locationManager: LocationManager,
    callbackLooper: Looper,
    private val listener: GnssStatusListener
) {
    private val callbackHandler = Handler(callbackLooper)
    private val lifecycle = GnssCollectorLifecycle()

    private val callback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val count = status.satelliteCount
            listener.onGnssStatus(GnssStatusSnapshot(
                timestamp = com.example.navigation.core.MonotonicTimestamp(SystemClock.elapsedRealtimeNanos()),
                satellites = (0 until count).map { index ->
                    GnssStatusFields(
                        constellation = status.getConstellationType(index).toConstellation(),
                        usedInFix = status.usedInFix(index),
                        azimuthDegrees = status.getAzimuthDegrees(index),
                        elevationDegrees = status.getElevationDegrees(index),
                        cn0DbHz = status.getCn0DbHz(index),
                        svid = status.getSvid(index),
                        carrierFrequencyHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && status.hasCarrierFrequencyHz(index)) status.getCarrierFrequencyHz(index) else null,
                        hasEphemerisData = status.hasEphemerisData(index),
                        hasAlmanacData = status.hasAlmanacData(index)
                    ).toDomain(count)
                }
            ))
        }
    }

    fun start(): Boolean {
        return lifecycle.start { locationManager.registerGnssStatusCallback(callback, callbackHandler) } == EvidenceCapability.AVAILABLE
    }

    fun stop() {
        lifecycle.stop { locationManager.unregisterGnssStatusCallback(callback) }
    }

    fun capability(): EvidenceCapability = lifecycle.capability()

    private fun Int.toConstellation() = when (this) {
        GnssStatus.CONSTELLATION_GPS -> GnssConstellation.GPS
        GnssStatus.CONSTELLATION_SBAS -> GnssConstellation.SBAS
        GnssStatus.CONSTELLATION_GLONASS -> GnssConstellation.GLONASS
        GnssStatus.CONSTELLATION_QZSS -> GnssConstellation.QZSS
        GnssStatus.CONSTELLATION_BEIDOU -> GnssConstellation.BEIDOU
        GnssStatus.CONSTELLATION_GALILEO -> GnssConstellation.GALILEO
        GnssStatus.CONSTELLATION_IRNSS -> GnssConstellation.IRNSS
        else -> GnssConstellation.UNKNOWN
    }
}
