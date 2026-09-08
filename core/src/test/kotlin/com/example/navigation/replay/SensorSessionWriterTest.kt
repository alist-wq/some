package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.navigation.NavigationMode
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import com.example.navigation.sensors.GyroscopeSample
import java.io.BufferedInputStream
import java.io.ObjectInputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SensorSessionWriterTest {
    @Test
    fun writerRoundTripsAndRetainsEverySimulatedMultiSensorObservation() {
        val file = Files.createTempFile("sensor-session", ".bin")
        val header = SensorSessionHeader(
            sessionId = "UA-KH-001",
            startedAt = MonotonicTimestamp(100L),
            routeIdentifier = "route-001",
            initialNavigationMode = NavigationMode.VEHICLE
        )
        val records = listOf(
            GyroscopeSessionRecord(GyroscopeSample(0.1f, 0.2f, 0.3f, MonotonicTimestamp(101L))),
            VehicleSpeedSessionRecord(VehicleSpeedSample(12.5, MonotonicTimestamp(102L), VehicleSpeedSource.OBD)),
            SessionMarker(MonotonicTimestamp(103L), "intersection"),
            GyroscopeSessionRecord(GyroscopeSample(0.4f, 0.5f, 0.6f, MonotonicTimestamp(104L)))
        )

        try {
            SensorSessionWriter(file, header, flushIntervalMillis = 1).use { writer ->
                records.forEach(writer::append)
                writer.close(MonotonicTimestamp(105L))
            }

            ObjectInputStream(BufferedInputStream(Files.newInputStream(file))).use { input ->
                assertEquals(header, input.readObject())
                records.forEach { assertEquals(it, input.readObject()) }
                val footer = assertIs<SensorSessionFooter>(input.readObject())
                assertEquals(MonotonicTimestamp(105L), footer.completedAt)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
