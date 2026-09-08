package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.sensors.AccelerometerSample
import com.example.navigation.sensors.GameRotationVectorSample
import com.example.navigation.sensors.GyroscopeSample
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PedestrianSessionValidationTest {
    private fun metadata(route: String? = "anonymous-route", consent: Boolean = true) = PedestrianSessionMetadata(
        NavigationMode.PEDESTRIAN, PedestrianPoseLabel.UNKNOWN, "Старт", "Фініш", route,
        knownWalkingDistanceMeters = 640.0, deviceModel = "Synthetic Phone",
        carryConditionNotes = "Кишеня; дощ", knownAnomalies = listOf("brief stop"),
        consentConfirmed = consent, anonymized = true, wallClockStartedEpochMillis = 1234L
    )

    @Test fun `version two round trip preserves unicode metadata pose transitions and every event deterministically`() {
        val directory = Files.createTempDirectory("pedestrian-roundtrip")
        var now = 100L
        val controller = RecordingSessionController(directory, RecordingSensorAvailability(true, true, true, true, true, false)) { now }
        controller.start(metadata())
        PedestrianPoseLabel.entries.forEach { now++; controller.labelPose(it) }
        PedestrianEventLabel.entries.forEach { now++; controller.labelPedestrianEvent(it, "мітка") }
        now++
        val file = controller.stop()
        val first = SensorSessionReader(file).use { it.records().toList() }
        assertEquals(metadata(), first.filterIsInstance<PedestrianMetadataSessionRecord>().single().metadata)
        assertEquals(PedestrianPoseLabel.entries.toSet(), first.filterIsInstance<PedestrianPoseSessionRecord>().map { it.pose }.toSet())
        assertEquals(PedestrianEventLabel.entries.toList(), first.filterIsInstance<PedestrianEventSessionRecord>().map { it.event })
        val second = SensorSessionReader(file).use { it.records().toList() }
        assertEquals(first, second)
    }

    @Test fun `legacy version one remains readable and explicitly unlabelled`() {
        val file = Files.createTempFile("legacy-session", ".navsession")
        SensorSessionWriter(file, SensorSessionHeader(1, "legacy", MonotonicTimestamp(1))).use { it.close(MonotonicTimestamp(2)) }
        SensorSessionReader(file).use { reader ->
            assertTrue(reader.records().none { it is PedestrianMetadataSessionRecord })
            assertTrue(reader.isComplete)
        }
        assertEquals(PedestrianAcceptanceDisposition.REJECTED, PedestrianRecordingValidator().validate(file).disposition)
    }

    @Test fun `validator accepts complete OBD-free session and reports deterministic output`() {
        val file = syntheticSession()
        val validator = PedestrianRecordingValidator(PedestrianValidationThresholds(minimumDurationNanos = 100L, minimumGnssSamples = 2, minimumMotionSamples = 2, maximumGapNanos = 100L))
        val report = validator.validate(file)
        assertEquals(PedestrianAcceptanceDisposition.ACCEPTED, report.disposition)
        assertTrue("OBD" in report.optionalUnavailable)
        assertEquals(report.normalizedText(), validator.validate(file).normalizedText())
        assertEquals(report.normalizedText(), Files.readString(validator.writeReport(file)))
    }

    @Test fun `validator distinguishes limitations malformed order sparse streams missing pose reference and consent`() {
        val limited = syntheticSession(route = null, includeEvents = false)
        val relaxed = PedestrianRecordingValidator(PedestrianValidationThresholds(100, 2, 2, 100))
        assertEquals(PedestrianAcceptanceDisposition.ACCEPTED_WITH_LIMITATIONS, relaxed.validate(limited).disposition)
        val malformed = syntheticSession(consent = false, reverse = true, includePose = false, motionSamples = 1)
        val report = relaxed.validate(malformed)
        assertEquals(PedestrianAcceptanceDisposition.REJECTED, report.disposition)
        assertTrue(report.requiredFailures.any { it.contains("monotonic") })
        assertTrue(report.requiredFailures.any { it.contains("Consent") })
        assertTrue(report.requiredFailures.any { it.contains("pose") })
    }

    private fun syntheticSession(
        route: String? = "anonymous-route", consent: Boolean = true, reverse: Boolean = false,
        includePose: Boolean = true, includeEvents: Boolean = true, motionSamples: Int = 2
    ): java.nio.file.Path {
        val file = Files.createTempFile("synthetic-pedestrian-export", ".navsession")
        val start = MonotonicTimestamp(1_000L)
        val writer = SensorSessionWriter(file, SensorSessionHeader(sessionId = "synthetic", startedAt = start, initialNavigationMode = NavigationMode.PEDESTRIAN))
        writer.append(PedestrianMetadataSessionRecord(start, metadata(route, consent)))
        writer.append(NavigationModeSessionRecord(start, NavigationMode.PEDESTRIAN))
        if (includePose) writer.append(PedestrianPoseSessionRecord(MonotonicTimestamp(1_001L), PedestrianPoseLabel.HAND_FORWARD))
        repeat(2) { i -> writer.append(GnssSessionRecord(GnssSample(50.0, 30.0, null, 3f, null, null, null, null, null, "gps", MonotonicTimestamp(1_002L + i), null))) }
        repeat(motionSamples) { i ->
            val timestamp = MonotonicTimestamp(if (reverse && i == motionSamples - 1) 900L else 1_010L + i)
            writer.append(GyroscopeSessionRecord(GyroscopeSample(0f, 0f, 0f, timestamp)))
            writer.append(AccelerometerSessionRecord(AccelerometerSample(0f, 0f, 9.8f, timestamp)))
            writer.append(GameRotationVectorSessionRecord(GameRotationVectorSample(0f, 0f, 0f, 1f, timestamp)))
        }
        if (includeEvents) writer.append(PedestrianEventSessionRecord(MonotonicTimestamp(1_020L), PedestrianEventLabel.ARRIVAL))
        writer.close(MonotonicTimestamp(1_200L))
        return file
    }
}
