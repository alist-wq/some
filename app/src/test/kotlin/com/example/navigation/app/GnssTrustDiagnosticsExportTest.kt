package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssTrustDiagnosticsExportTest {
    @Test fun `report includes trust reasons privacy and no raw mutation claim`() {
        val report = GnssTrustExportReport.render(listOf(GnssEpochTrustDecision(
            MonotonicTimestamp(1), 0.2, GnssMeasurementClassification.SUSPICIOUS,
            listOf(GnssTrustReasonCode.PHYSICAL_CONTINUITY_VIOLATION), emptyList(), "v1", EvidenceCapability.AVAILABLE
        )))
        assertTrue(report.contains("SUSPICIOUS"))
        assertTrue(report.contains("PHYSICAL_CONTINUITY_VIOLATION"))
        assertTrue(report.contains("raw session records preserved"))
        assertTrue(report.contains("no automatic upload"))
        assertFalse(report.contains("genuine"))
    }

    @Test fun `unavailable diagnostics remain explicit`() {
        val report = GnssTrustExportReport.render(listOf(GnssEpochTrustDecision(
            MonotonicTimestamp(1), null, null, listOf(GnssTrustReasonCode.EVIDENCE_UNAVAILABLE),
            emptyList(), "legacy", EvidenceCapability.UNAVAILABLE
        )))
        assertTrue(report.contains("unavailable=1"))
    }

    @Test fun `external evidence diagnostics preserve uncertainty and attribution`() {
        val diagnostics = GnssTrustNavigationSnapshot().toDiagnostics(ExternalEvidenceSnapshot(
            spacecraft = ExternalEvidenceStatus(MonotonicTimestamp(1), ExternalEvidenceSource.SPACECRAFT_REFERENCE, ExternalEvidenceState.STALE, "CelesTrak", 300_000.0),
            airAlert = ExternalEvidenceStatus(MonotonicTimestamp(2), ExternalEvidenceSource.AIR_ALERT, ExternalEvidenceState.DISCONNECTED, "NEPTUN")
        ))
        assertTrue(diagnostics.spacecraftReferenceStatus?.state == ExternalEvidenceState.STALE)
        assertTrue(diagnostics.airAlertFeedStatus?.state == ExternalEvidenceState.DISCONNECTED)
    }
}
