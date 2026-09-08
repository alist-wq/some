package com.example.navigation.replay

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingSessionControllerTest {
    @Test fun `start marker and stop produce a complete session`() {
        val directory = Files.createTempDirectory("recording-controller-test")
        var now = 1_000L
        val controller = RecordingSessionController(directory, RecordingSensorAvailability(true, true, true, true, true, false)) { now }
        val started = controller.start()
        now += 2_000_000_000L
        controller.markEvent("intersection")
        val path = controller.stop()
        assertTrue(Files.exists(path))
        assertFalse(controller.status().isRecording)
        SensorSessionReader(path).use { reader ->
            assertEquals(1, reader.records().toList().size)
            assertTrue(reader.isComplete)
            assertEquals(started.startedAt, reader.header.startedAt)
        }
    }

    @Test fun `unavailable inputs remain explicit`() {
        val controller = RecordingSessionController(Files.createTempDirectory("availability-test"), RecordingSensorAvailability(true, false, true, true, true, false))
        assertFalse(controller.availability().rawGnss)
        assertFalse(controller.availability().obd)
    }
}
