package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.gnss.GnssSample
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import com.example.navigation.sensors.AccelerometerSample
import com.example.navigation.sensors.GyroscopeSample
import java.io.BufferedOutputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatasetValidationToolTest {
    private val validator = DatasetValidationTool(DatasetValidationConfig(maximumGapNanos = 10, maximumVehicleSpeedMetersPerSecond = 50.0))

    @Test fun `accepts complete session with GNSS and core IMU`() {
        withSession(listOf(gnss(1), gyro(2), accelerometer(3), speed(4, 10.0, VehicleSpeedSource.OBD))) { path ->
            assertTrue(validator.validate(path).isValid)
        }
    }

    @Test fun `detects reversal gap missing sensors and invalid OBD`() {
        withSession(listOf(gyro(10), speed(5, -1.0, VehicleSpeedSource.UNKNOWN), accelerometer(30))) { path ->
            val types = validator.validate(path).issues.map { it.type }.toSet()
            assertEquals(setOf(DatasetValidationIssueType.TIMESTAMP_REVERSAL, DatasetValidationIssueType.LARGE_DATA_GAP, DatasetValidationIssueType.MISSING_GNSS, DatasetValidationIssueType.INVALID_OBD), types)
        }
    }

    @Test fun `detects missing IMU and truncated sessions`() {
        val path = Files.createTempFile("truncated-session", ".bin")
        try {
            ObjectOutputStream(BufferedOutputStream(Files.newOutputStream(path))).use { output ->
                output.writeObject(SensorSessionHeader(sessionId = "truncated", startedAt = MonotonicTimestamp(0)))
                output.writeObject(gnss(1))
            }
            val types = validator.validate(path).issues.map { it.type }.toSet()
            assertEquals(setOf(DatasetValidationIssueType.MISSING_IMU, DatasetValidationIssueType.TRUNCATED_FILE), types)
        } finally { Files.deleteIfExists(path) }
    }

    private fun withSession(records: List<SensorSessionRecord>, block: (java.nio.file.Path) -> Unit) {
        val path = Files.createTempFile("validated-session", ".bin")
        try {
            SensorSessionWriter(path, SensorSessionHeader(sessionId = "test", startedAt = MonotonicTimestamp(0))).use { writer ->
                records.forEach(writer::append)
                writer.close(MonotonicTimestamp(40))
            }
            block(path)
        } finally { Files.deleteIfExists(path) }
    }

    private fun gnss(t: Long) = GnssSessionRecord(GnssSample(50.0, 30.0, null, null, null, null, null, null, null, "gps", MonotonicTimestamp(t), null))
    private fun gyro(t: Long) = GyroscopeSessionRecord(GyroscopeSample(0f, 0f, 0f, MonotonicTimestamp(t)))
    private fun accelerometer(t: Long) = AccelerometerSessionRecord(AccelerometerSample(0f, 0f, 0f, MonotonicTimestamp(t)))
    private fun speed(t: Long, metersPerSecond: Double, source: VehicleSpeedSource) = VehicleSpeedSessionRecord(VehicleSpeedSample(metersPerSecond, MonotonicTimestamp(t), source))
}
