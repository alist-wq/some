package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.sensors.GyroscopeSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PedestrianGyroTurnDetectorTest {
    @Test fun `positive relative yaw is detected as left turn`() {
        val event = detectTurn(90.0)

        assertEquals(PedestrianTurnDirection.LEFT, event.direction)
        assertEquals(90.0, event.relativeTurnDegrees, 0.01)
    }

    @Test fun `negative relative yaw is detected as right turn`() {
        val event = detectTurn(-90.0)

        assertEquals(PedestrianTurnDirection.RIGHT, event.direction)
    }

    @Test fun `large relative yaw is detected as u turn`() {
        val event = detectTurn(180.0)

        assertEquals(PedestrianTurnDirection.U_TURN, event.direction)
    }

    @Test fun `small settled movement is classified straight`() {
        val event = detectTurn(15.0)

        assertEquals(PedestrianTurnDirection.STRAIGHT, event.direction)
    }

    @Test fun `detector waits for a settled turn`() {
        val detector = PedestrianGyroTurnDetector()
        assertNull(detector.process(sample(0, 1f)))
        assertNull(detector.process(sample(1_000, 1f)))

        assertEquals(PedestrianTurnDirection.LEFT, detector.process(sample(1_100, 0f))!!.direction)
    }

    private fun detectTurn(degrees: Double): PedestrianTurnEvent {
        val detector = PedestrianGyroTurnDetector()
        val rate = Math.toRadians(degrees).toFloat()
        detector.process(sample(0, rate))
        detector.process(sample(1_000, rate))
        return detector.process(sample(1_100, 0f))!!
    }

    private fun sample(milliseconds: Long, yawRate: Float) = GyroscopeSample(0f, 0f, yawRate,
        MonotonicTimestamp(milliseconds * 1_000_000L))
}
