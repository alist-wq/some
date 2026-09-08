package com.example.navigation.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.ExternalEvidenceSource
import com.example.navigation.gnss.ExternalEvidenceState
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.replay.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalEvidenceRecordingSmokeTest {
    @Test fun deterministicFeedsRecordCoverageReferenceAlertAndTeardown() {
        val transport = object : ExternalEvidenceTransport {
            var closed = false
            lateinit var streamListener: ExternalEvidenceStreamListener
            override fun get(url: String, result: (Result<String>) -> Unit) = result(Result.success(when (url) {
                "tle" -> TLE
                "regions" -> REGIONS
                "alerts" -> ALERTS
                else -> error("Unexpected URL $url")
            }))
            override fun openWebSocket(url: String, listener: ExternalEvidenceStreamListener): AutoCloseable {
                streamListener = listener
                listener.onOpen()
                listener.onMessage("""{"type":"heartbeat","data":{}}""")
                return AutoCloseable { closed = true }
            }
            override fun close() { closed = true }
        }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val output = context.cacheDir.toPath().resolve("external-smoke-${System.nanoTime()}")
        val controller = RecordingSessionController(output, RecordingSensorAvailability(true, true, true, true, true, false))
        controller.start(PedestrianSessionMetadata(
            navigationMode = NavigationMode.PEDESTRIAN, initialPose = PedestrianPoseLabel.HAND_FORWARD,
            intendedStart = "anonymized start", intendedDestination = "anonymized destination",
            routeIdentifier = "external-smoke-route", deviceModel = "RenavSmokeApi35",
            consentConfirmed = true, anonymized = true
        ))
        controller.labelPose(PedestrianPoseLabel.POCKET)
        controller.labelPedestrianEvent(PedestrianEventLabel.WALKING_START)
        val observed = mutableListOf<ExternalEvidenceSnapshot>()
        val coordinator = ExternalEvidenceCoordinator(
            context.cacheDir.resolve("external-smoke-cache-${System.nanoTime()}"), controller, observed::add,
            endpoints = ExternalEvidenceEndpoints("tle", "alerts", "regions", "stream"), transport = transport
        )
        coordinator.updateLocation(MonotonicTimestamp(10), System.currentTimeMillis(), 50.5, 30.5, 180.0)
        coordinator.start()
        transport.streamListener.onMessage("""{"type":"alerts","data":{"raions":[],"oblasts":[]}}""")
        transport.streamListener.onMessage("""{"type":"alerts","data":{"raions":[],"oblasts":[]}}""")
        transport.streamListener.onMessage("malformed")
        transport.streamListener.onDisconnected()
        coordinator.close()
        controller.labelPedestrianEvent(PedestrianEventLabel.WALKING_STOP)
        val session = controller.stop()
        val records = SensorSessionReader(session).use { it.records().toList() }
        assertTrue("spacecraft unavailable; records=$records observed=$observed", records.filterIsInstance<ExternalEvidenceStatusSessionRecord>().any {
            it.value.source == ExternalEvidenceSource.SPACECRAFT_REFERENCE && it.value.state == ExternalEvidenceState.AVAILABLE
        })
        assertTrue(records.filterIsInstance<ExternalEvidenceStatusSessionRecord>().any {
            it.value.source == ExternalEvidenceSource.AIR_ALERT && it.value.state == ExternalEvidenceState.AVAILABLE
        })
        assertTrue(records.filterIsInstance<AirAlertSessionRecord>().size == 3)
        assertTrue(records.filterIsInstance<ExternalEvidenceStatusSessionRecord>().any {
            it.value.source == ExternalEvidenceSource.AIR_ALERT && it.value.state == ExternalEvidenceState.ERROR
        })
        assertTrue(records.filterIsInstance<ExternalEvidenceStatusSessionRecord>().any {
            it.value.source == ExternalEvidenceSource.AIR_ALERT && it.value.state == ExternalEvidenceState.DISCONNECTED
        })
        assertTrue(observed.any { it.spacecraft?.state == ExternalEvidenceState.AVAILABLE && it.airAlert?.state == ExternalEvidenceState.AVAILABLE })
        assertTrue(transport.closed)
        val replayed = mutableListOf<SensorSessionRecord>()
        ReplayEngine(session, ReplayClock(ReplayClockMode.MAX_SPEED)).replay(consumer = replayed::add)
        assertTrue(replayed.any { it is PedestrianPoseSessionRecord })
        val second = session.resolveSibling("session-external-smoke-copy.navsession")
        java.nio.file.Files.copy(session, second)
        listOf(session, second).forEach {
            assertTrue(java.nio.file.Files.exists(GnssTrustSessionReportWriter().write(it)))
        }
        assertTrue(listRecordedSessions(output).size == 2)
    }

    private companion object {
        const val TLE = """GPS BIIR-5 (PRN 22)
1 25544U 98067A   26045.79523799  .00007779  00000+0  15107-3 0  9994
2 25544  51.6315 185.5279 0011056  98.8248 261.3993 15.48601910552787
"""
        const val REGIONS = """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"key":"test:test-raion"},"geometry":{"type":"Polygon","coordinates":[[[30.0,50.0],[31.0,50.0],[31.0,51.0],[30.0,51.0],[30.0,50.0]]]}}]}"""
        const val ALERTS = """{"raions":[{"key":"test:test-raion","name":"Test","oblast":"Test"}],"oblasts":[]}"""
    }
}
