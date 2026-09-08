package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.obd.VehicleSpeedSample
import com.example.navigation.obd.VehicleSpeedSource
import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files

class ReplayEngineTest {
    @Test
    fun engineDeliversRecordedObservationsInTimestampOrder() {
        val file = Files.createTempFile("replay-engine", ".bin")
        val records = listOf(
            VehicleSpeedSessionRecord(VehicleSpeedSample(2.0, MonotonicTimestamp(10L), VehicleSpeedSource.OBD)),
            SessionMarker(MonotonicTimestamp(20L), "turn"),
            VehicleSpeedSessionRecord(VehicleSpeedSample(3.0, MonotonicTimestamp(30L), VehicleSpeedSource.OBD))
        )
        try {
            SensorSessionWriter(file, SensorSessionHeader(sessionId = "replay", startedAt = MonotonicTimestamp(0L))).use { writer ->
                records.forEach(writer::append)
                writer.close(MonotonicTimestamp(31L))
            }
            val delivered = mutableListOf<SensorSessionRecord>()

            ReplayEngine(file, ReplayClock(ReplayClockMode.MAX_SPEED)).replay(delivered::add)

            assertEquals(records, delivered)
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
