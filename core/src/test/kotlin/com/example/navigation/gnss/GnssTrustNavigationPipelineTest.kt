package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.navigation.PositionSource
import com.example.navigation.replay.*
import java.nio.file.Files
import kotlin.test.*

class GnssTrustNavigationPipelineTest {
    @Test fun `live integration anchors fallback and recovers gradually`() {
        var latest: GnssTrustPipelineOutput? = null
        val pipeline = pipeline()
        pipeline.observe { latest = it }
        statusAndLocation(pipeline, 1, 50.45, 30.52)
        assertEquals(PositionSource.GNSS, latest!!.snapshot.navigationPosition!!.source)
        repeat(5) { index -> statusAndLocation(pipeline, index + 2, -12.0464, -77.0428) }
        assertEquals(GnssTrustState.REJECTED, latest!!.snapshot.trustState)
        assertEquals(PositionSource.PEDESTRIAN_DEAD_RECKONING, latest!!.snapshot.navigationPosition!!.source)
        assertEquals(0.0, latest!!.snapshot.decision!!.satellites.single().estimatorWeight)
        repeat(3) { index -> statusAndLocation(pipeline, index + 7, 50.45, 30.52) }
        assertEquals(GnssTrustState.RECOVERING, latest!!.snapshot.trustState)
        assertEquals(PositionSource.HYBRID, latest!!.snapshot.navigationPosition!!.source)
    }

    @Test fun `replay retains raw suspicious data and deterministically adds derived records`() {
        val path = Files.createTempFile("trust-replay", ".navsession")
        SensorSessionWriter(path, SensorSessionHeader(sessionId = "synthetic", startedAt = time(0)), 1).use { writer ->
            writer.append(status(1))
            writer.append(GnssSessionRecord(location(1, 50.45, 30.52)))
            writer.append(status(2))
            writer.append(GnssSessionRecord(location(2, -12.0464, -77.0428)))
            writer.close(time(3))
        }
        val first = GnssTrustReplayProcessor(pipeline()).process(path)
        val second = GnssTrustReplayProcessor(pipeline()).process(path)
        assertEquals(first, second)
        assertEquals(2, first.filterIsInstance<GnssSessionRecord>().size)
        assertEquals(2, first.filterIsInstance<GnssTrustSessionRecord>().size)
        assertTrue(first.filterIsInstance<GnssSessionRecord>().any { it.sample.longitude < 0.0 })
    }

    private fun pipeline() = GnssTrustNavigationPipeline(
        navigationMode = { NavigationMode.PEDESTRIAN },
        independentContext = { sample -> IndependentGnssContext(
            NavigationMode.PEDESTRIAN,
            positionInnovationMeters = if (sample.longitude < 0.0) 5_000_000.0 else 2.0,
            pdrConsistent = sample.longitude >= 0.0,
            gyroConsistent = sample.longitude >= 0.0,
            routeConsistent = sample.longitude >= 0.0
        ) }
    )

    private fun statusAndLocation(pipeline: GnssTrustNavigationPipeline, second: Int, lat: Double, lon: Double) {
        pipeline.onRecord(status(second))
        pipeline.onRecord(GnssSessionRecord(location(second, lat, lon)))
    }
    private fun status(second: Int) = GnssStatusSessionRecord(GnssStatusSnapshot(time(second), listOf(
        GnssSatelliteStatus(GnssConstellation.GPS, 1, true, 20f, 45f, 38f, 3)
    )))
    private fun location(second: Int, lat: Double, lon: Double) = GnssSample(lat, lon, null, 5f, null, 1f, null, null, null, "gps", time(second), null)
    private fun time(second: Int) = MonotonicTimestamp(second * 1_000_000_000L)
}
