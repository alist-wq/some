package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.sensors.AccelerometerSample
import kotlin.test.Test
import kotlin.test.assertEquals

class StepDetectorBenchmarkTest {
    @Test fun `benchmark reports true detected false and missed steps`() {
        val recording = LabeledStepRecording(
            samples = listOf(sample(0, 12f), sample(100, 9.8f), sample(500, 12f), sample(600, 9.8f), sample(1_000, 12f)),
            trueStepTimestamps = listOf(timestamp(0), timestamp(500), timestamp(1_500))
        )

        val result = StepDetectorBenchmark().run(recording, StepDetector())

        assertEquals(3, result.trueSteps)
        assertEquals(3, result.detectedSteps)
        assertEquals(1, result.falseSteps)
        assertEquals(1, result.missedSteps)
    }

    @Test fun `benchmark aggregates recordings`() {
        val recording = LabeledStepRecording(listOf(sample(0, 12f)), listOf(timestamp(0)))
        val result = StepDetectorBenchmark().run(listOf(recording, recording), ::StepDetector)

        assertEquals(2, result.trueSteps); assertEquals(2, result.detectedSteps); assertEquals(0, result.falseSteps); assertEquals(0, result.missedSteps)
    }

    private fun sample(milliseconds: Long, z: Float) = AccelerometerSample(0f, 0f, z, timestamp(milliseconds))
    private fun timestamp(milliseconds: Long) = MonotonicTimestamp(milliseconds * 1_000_000L)
}
