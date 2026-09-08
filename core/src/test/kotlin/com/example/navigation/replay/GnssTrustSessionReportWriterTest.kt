package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.*
import java.nio.file.Files
import kotlin.test.*

class GnssTrustSessionReportWriterTest {
    @Test fun `report is deterministic local companion and source bytes stay unchanged`() {
        val path = Files.createTempFile("trust-export", ".navsession")
        val time = MonotonicTimestamp(1)
        SensorSessionWriter(path, SensorSessionHeader(sessionId = "export", startedAt = time), 1).use { writer ->
            writer.append(GnssTrustSessionRecord(GnssEpochTrustDecision(time, 0.1, GnssMeasurementClassification.REJECTED,
                listOf(GnssTrustReasonCode.PHYSICAL_CONTINUITY_VIOLATION), emptyList(), "v1", EvidenceCapability.AVAILABLE)))
            writer.close(MonotonicTimestamp(2))
        }
        val before = Files.readAllBytes(path)
        val report = GnssTrustSessionReportWriter().write(path)
        val first = Files.readString(report)
        val second = Files.readString(GnssTrustSessionReportWriter().write(path))
        assertEquals(first, second)
        assertContentEquals(before, Files.readAllBytes(path))
        assertContains(first, "REJECTED")
        assertContains(first, "no automatic upload")
    }
}
