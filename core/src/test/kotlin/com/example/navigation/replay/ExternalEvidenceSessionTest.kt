package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalEvidenceSessionTest {
    @Test fun `feed states markers report and replay round trip without alert history`() {
        val path = Files.createTempFile("external-evidence", ".navsession")
        val records = listOf(
            ExternalEvidenceStatusSessionRecord(ExternalEvidenceStatus(MonotonicTimestamp(1), ExternalEvidenceSource.SPACECRAFT_REFERENCE, ExternalEvidenceState.STALE, "CelesTrak", 300_000.0)),
            ExternalEvidenceStatusSessionRecord(ExternalEvidenceStatus(MonotonicTimestamp(2), ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.AVAILABLE, "NEPTUN")),
            AirAlertSessionRecord(AirAlertContextMarker(MonotonicTimestamp(3), AirAlertTransition.AIR_ALERT_STARTED, "NEPTUN", ContextSourceStatus.AVAILABLE)),
            ExternalEvidenceStatusSessionRecord(ExternalEvidenceStatus(MonotonicTimestamp(4), ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.DISCONNECTED, "NEPTUN"))
        )
        SensorSessionWriter(path, SensorSessionHeader(sessionId = "external", startedAt = MonotonicTimestamp(0)), 1).use { writer ->
            records.forEach(writer::append)
            writer.close(MonotonicTimestamp(5))
        }
        SensorSessionReader(path).use { assertEquals(records, it.records().toList()) }
        val replayed = mutableListOf<SensorSessionRecord>()
        ReplayEngine(path, ReplayClock(ReplayClockMode.MAX_SPEED)).replay(consumer = replayed::add)
        assertEquals(records, replayed)
        val report = Files.readString(GnssTrustSessionReportWriter().write(path))
        assertTrue(report.contains("SPACECRAFT_REFERENCE/STALE=1"))
        assertTrue(report.contains("AIR_ALERT/DISCONNECTED=1"))
        assertTrue(report.contains("airAlertCoverageKnown=true"))
        assertTrue(report.contains("NEPTUN"))
        assertTrue(!report.contains("startedAt"))
    }

    @Test fun `available alert coverage with no marker is not reported as unavailable`() {
        val path = Files.createTempFile("no-alert-covered", ".navsession")
        SensorSessionWriter(path, SensorSessionHeader(sessionId = "covered", startedAt = MonotonicTimestamp(0)), 1).use { writer ->
            writer.append(ExternalEvidenceStatusSessionRecord(ExternalEvidenceStatus(
                MonotonicTimestamp(1), ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.AVAILABLE, "NEPTUN"
            )))
            writer.close(MonotonicTimestamp(2))
        }
        val report = Files.readString(GnssTrustSessionReportWriter().write(path))
        assertTrue(report.contains("airAlertMarkers=0"))
        assertTrue(report.contains("airAlertCoverageKnown=true"))
    }
}
