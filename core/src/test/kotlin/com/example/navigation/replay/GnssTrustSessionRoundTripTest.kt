package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.*
import com.example.navigation.navigation.PositionConfidence
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GnssTrustSessionRoundTripTest {
    @Test fun `schema 3 preserves every classification weights references markers and raw ordering`() {
        val path = Files.createTempFile("gnss-trust", ".navsession")
        val time = MonotonicTimestamp(100)
        val records = GnssMeasurementClassification.entries.mapIndexed { index, classification ->
            GnssTrustSessionRecord(GnssEpochTrustDecision(
                MonotonicTimestamp(time.nanos + index), index / 3.0, classification,
                listOf(GnssTrustReasonCode.SIGNAL_ANOMALY, GnssTrustReasonCode.EXPECTED_GEOMETRY_DISAGREEMENT),
                listOf(SatelliteTrustDecision(GnssConstellation.GPS, index + 1, index / 3.0, classification,
                    listOf(GnssTrustReasonCode.SIGNAL_ANOMALY), classification != GnssMeasurementClassification.REJECTED,
                    if (classification == GnssMeasurementClassification.REJECTED) 0.0 else index / 3.0,
                    SpacecraftReferenceEvidence(GnssConstellation.GPS, index + 1, 25_501 + index, "cached-tle", "v1", ageSeconds = 60.0))),
                "test-v1", EvidenceCapability.AVAILABLE
            ))
        }
        val raw = raw(time)
        SensorSessionWriter(path, SensorSessionHeader(sessionId = "fixture", startedAt = time), 1).use { writer ->
            writer.append(RawGnssSessionRecord(raw))
            records.forEach(writer::append)
            writer.append(WeightedGnssSolutionSessionRecord(WeightedGnssPositionSolution(time, 50.0, 30.0, 12.0,
                PositionConfidence.MEDIUM, WeightedPositionSource.WEIGHTED_GNSS, 1.25, listOf(GnssTrustReasonCode.SIGNAL_ANOMALY))))
            writer.append(AirAlertSessionRecord(AirAlertContextMarker(time, AirAlertTransition.AIR_ALERT_STARTED, "approved-provider", ContextSourceStatus.AVAILABLE)))
            writer.close(MonotonicTimestamp(200))
        }
        SensorSessionReader(path).use { reader ->
            assertEquals(3, reader.header.schemaVersion)
            val read = reader.records().toList()
            assertEquals(raw, (read.first() as RawGnssSessionRecord).sample)
            assertEquals(records, read.filterIsInstance<GnssTrustSessionRecord>())
            assertEquals(1, read.filterIsInstance<WeightedGnssSolutionSessionRecord>().size)
            assertEquals(1, read.filterIsInstance<AirAlertSessionRecord>().size)
        }
    }

    @Test fun `legacy schema remains readable and trust is explicitly absent`() {
        val path = Files.createTempFile("legacy", ".navsession")
        SensorSessionWriter(path, SensorSessionHeader(1, "legacy", MonotonicTimestamp(1)), 1).use { it.close(MonotonicTimestamp(2)) }
        SensorSessionReader(path).use { reader ->
            assertEquals(1, reader.header.schemaVersion)
            assertEquals(emptyList(), reader.records().filterIsInstance<GnssTrustSessionRecord>().toList())
        }
    }

    @Test fun `unavailable evidence cannot fabricate score or classification`() {
        val decision = GnssEpochTrustDecision(MonotonicTimestamp(1), null, null,
            listOf(GnssTrustReasonCode.EVIDENCE_UNAVAILABLE), emptyList(), "legacy", EvidenceCapability.UNAVAILABLE)
        assertNull(decision.score)
        assertNull(decision.classification)
    }

    @Test fun `identical records serialize deterministically`() {
        fun write(): ByteArray {
            val path = Files.createTempFile("deterministic", ".navsession")
            val time = MonotonicTimestamp(1)
            SensorSessionWriter(path, SensorSessionHeader(sessionId = "same", startedAt = time), 1).use { writer ->
                writer.append(RawGnssSessionRecord(raw(time)))
                writer.close(MonotonicTimestamp(2))
            }
            return Files.readAllBytes(path)
        }
        assertContentEquals(write(), write())
    }

    private fun raw(time: MonotonicTimestamp) = RawGnssSample(time,
        GnssClockInfo(10, -20, null, null, null, null, null, 0),
        listOf(RawSatelliteObservation(1, GnssConstellation.GPS, null, 30f, null, -1.0, 0.1,
            null, null, 1, null, GnssMultipathIndicator.UNKNOWN, 2, 3)))
}
