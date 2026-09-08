package com.example.navigation.pedestrian

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.sensors.AccelerometerSample
import kotlin.math.abs

data class LabeledStepRecording(
    val samples: List<AccelerometerSample>,
    val trueStepTimestamps: List<MonotonicTimestamp>
) {
    init {
        require(samples.zipWithNext().all { (first, second) -> second.timestamp > first.timestamp }) { "Accelerometer samples must be ordered" }
        require(trueStepTimestamps.zipWithNext().all { (first, second) -> second > first }) { "Reference steps must be ordered" }
    }
}

data class StepDetectorBenchmarkResult(
    val trueSteps: Int,
    val detectedSteps: Int,
    val falseSteps: Int,
    val missedSteps: Int
)

/** One-to-one timestamp matching benchmark for deterministic recordings and replay fixtures. */
class StepDetectorBenchmark(private val matchToleranceNanos: Long = 150_000_000L) {
    init { require(matchToleranceNanos >= 0L) }

    fun run(recording: LabeledStepRecording, detector: StepDetector): StepDetectorBenchmarkResult {
        detector.reset()
        val detected = recording.samples.mapNotNull(detector::process).map { it.timestamp }
        val unmatchedTruth = recording.trueStepTimestamps.toMutableList()
        var matched = 0
        detected.forEach { detection ->
            val matchIndex = unmatchedTruth.indices.minByOrNull { index -> abs(unmatchedTruth[index].differenceNanos(detection)) }
            if (matchIndex != null && abs(unmatchedTruth[matchIndex].differenceNanos(detection)) <= matchToleranceNanos) {
                unmatchedTruth.removeAt(matchIndex)
                matched++
            }
        }
        return StepDetectorBenchmarkResult(
            trueSteps = recording.trueStepTimestamps.size,
            detectedSteps = detected.size,
            falseSteps = detected.size - matched,
            missedSteps = unmatchedTruth.size
        )
    }

    fun run(recordings: List<LabeledStepRecording>, detectorFactory: () -> StepDetector): StepDetectorBenchmarkResult =
        recordings.map { run(it, detectorFactory()) }.fold(StepDetectorBenchmarkResult(0, 0, 0, 0)) { total, result ->
            StepDetectorBenchmarkResult(total.trueSteps + result.trueSteps, total.detectedSteps + result.detectedSteps,
                total.falseSteps + result.falseSteps, total.missedSteps + result.missedSteps)
        }
}
