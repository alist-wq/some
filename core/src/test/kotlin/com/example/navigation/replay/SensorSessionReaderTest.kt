package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.BufferedOutputStream
import java.io.ObjectOutputStream
import java.nio.file.Files

class SensorSessionReaderTest {
    @Test
    fun readerStreamsRecordsWithTimeAndSensorFilters() {
        val file = Files.createTempFile("sensor-session-reader", ".bin")
        val header = SensorSessionHeader(sessionId = "reader-test", startedAt = MonotonicTimestamp(100L))
        val records = listOf(
            VehicleSpeedSessionRecord(VehicleSpeedSample(5.0, MonotonicTimestamp(101L), VehicleSpeedSource.OBD)),
            SessionMarker(MonotonicTimestamp(102L), "turn"),
            VehicleSpeedSessionRecord(VehicleSpeedSample(7.0, MonotonicTimestamp(103L), VehicleSpeedSource.OBD))
        )
        try {
            SensorSessionWriter(file, header).use { writer ->
                records.forEach(writer::append)
                writer.close(MonotonicTimestamp(104L))
            }

            SensorSessionReader(file).use { reader ->
                val selected = reader.records(
                    fromInclusive = MonotonicTimestamp(102L),
                    toExclusive = MonotonicTimestamp(104L),
                    sensorTypes = setOf(SensorType.VEHICLE_SPEED)
                ).toList()
                assertEquals(listOf(records[2]), selected)
                assertTrue(reader.isComplete)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun readerReportsMissingFooterAsIncomplete() {
        val file = Files.createTempFile("sensor-session-incomplete", ".bin")
        try {
            ObjectOutputStream(BufferedOutputStream(Files.newOutputStream(file))).use { output ->
                output.writeObject(SensorSessionHeader(sessionId = "incomplete", startedAt = MonotonicTimestamp(1L)))
                output.writeObject(NavigationModeSessionRecord(MonotonicTimestamp(2L), NavigationMode.VEHICLE))
            }

            SensorSessionReader(file).use { reader ->
                reader.records().toList()
                assertFalse(reader.isComplete)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
