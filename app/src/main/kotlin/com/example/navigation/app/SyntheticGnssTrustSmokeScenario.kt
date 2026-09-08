package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.*
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.replay.GnssSessionRecord
import com.example.navigation.replay.GnssStatusSessionRecord

/** Debug-build-only deterministic injection fixture used by device smoke tests. */
internal object SyntheticGnssTrustSmokeScenario {
    fun run(): GnssTrustNavigationSnapshot {
        val pipeline = GnssTrustNavigationPipeline(
            navigationMode = { NavigationMode.PEDESTRIAN },
            independentContext = { sample -> IndependentGnssContext(
                NavigationMode.PEDESTRIAN,
                positionInnovationMeters = if (sample.longitude < 0.0) 5_000_000.0 else 2.0,
                pdrConsistent = sample.longitude >= 0.0,
                gyroConsistent = sample.longitude >= 0.0,
                routeConsistent = sample.longitude >= 0.0
            ) }
        )
        fun emit(second: Int, latitude: Double, longitude: Double) {
            val timestamp = MonotonicTimestamp(second * 1_000_000_000L)
            pipeline.onRecord(GnssStatusSessionRecord(GnssStatusSnapshot(timestamp, listOf(
                GnssSatelliteStatus(GnssConstellation.GPS, 2, true, 20f, 45f, 38f, 3),
                GnssSatelliteStatus(GnssConstellation.GALILEO, 2, true, 120f, 50f, 35f, 8)
            ))))
            pipeline.onRecord(GnssSessionRecord(GnssSample(latitude, longitude, null, 5f, null, 1f,
                null, null, null, "synthetic", timestamp, null)))
        }
        emit(1, 50.45, 30.52)
        repeat(5) { emit(it + 2, -12.0464, -77.0428) }
        return pipeline.snapshot()
    }
}
