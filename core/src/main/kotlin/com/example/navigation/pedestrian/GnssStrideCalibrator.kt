package com.example.navigation.pedestrian

import com.example.navigation.gnss.GnssSample
import com.example.navigation.gnss.GnssTrustState
import kotlin.math.*

data class GnssStrideCalibrationConfig(
    val referenceCadenceStepsPerMinute: Double = 100.0,
    val minimumStepsPerWindow: Int = 4,
    val maximumCalibrationWindows: Int = 30,
    val minimumStepLengthMeters: Double = 0.30,
    val maximumStepLengthMeters: Double = 1.50,
    val maximumCadenceGainMetersPerStepPerMinute: Double = 0.01,
    val minimumUncertaintyMeters: Double = 0.03
) {
    init {
        require(referenceCadenceStepsPerMinute.isFinite() && referenceCadenceStepsPerMinute > 0.0)
        require(minimumStepsPerWindow > 1)
        require(maximumCalibrationWindows > 1)
        require(minimumStepLengthMeters.isFinite() && maximumStepLengthMeters.isFinite() &&
            minimumStepLengthMeters > 0.0 && maximumStepLengthMeters > minimumStepLengthMeters)
        require(maximumCadenceGainMetersPerStepPerMinute.isFinite() && maximumCadenceGainMetersPerStepPerMinute > 0.0)
        require(minimumUncertaintyMeters.isFinite() && minimumUncertaintyMeters >= 0.0)
    }
}

data class StrideCalibration(
    val distancePerStepAtReferenceCadenceMeters: Double,
    val cadenceGainMetersPerStepPerMinute: Double,
    val uncertaintyMeters: Double,
    val calibrationWindowCount: Int,
    val frozen: Boolean,
    val referenceCadenceStepsPerMinute: Double
) {
    fun estimatedStepLengthMeters(cadenceStepsPerMinute: Double): Double =
        distancePerStepAtReferenceCadenceMeters +
            (cadenceStepsPerMinute - referenceCadenceStepsPerMinute) * cadenceGainMetersPerStepPerMinute
}

/** Calibrates stride only from windows explicitly marked as trusted GNSS. */
class GnssStrideCalibrator(
    private val profile: StrideUserProfile = StrideUserProfile(),
    private val config: GnssStrideCalibrationConfig = GnssStrideCalibrationConfig()
) {
    private val observations = ArrayDeque<CalibrationObservation>()
    private var calibration = StrideCalibration(
        profile.defaultStepLengthMeters,
        0.0,
        profile.defaultUncertaintyMeters,
        0,
        frozen = true,
        referenceCadenceStepsPerMinute = config.referenceCadenceStepsPerMinute
    )

    fun observeWindow(
        gnssTrajectory: List<GnssSample>,
        steps: List<DetectedStep>,
        trustState: GnssTrustState
    ): StrideCalibration {
        if (trustState != GnssTrustState.TRUSTED) return calibration.copy(frozen = true)
        require(gnssTrajectory.size >= 2) { "Stride calibration requires at least two GNSS samples" }
        require(gnssTrajectory.zipWithNext().all { (first, second) -> second.timestamp > first.timestamp }) {
            "GNSS calibration samples must be strictly increasing"
        }
        require(steps.size >= config.minimumStepsPerWindow) { "Stride calibration window has too few steps" }
        require(steps.all { it.timestamp >= gnssTrajectory.first().timestamp && it.timestamp <= gnssTrajectory.last().timestamp }) {
            "Calibration steps must fall within the GNSS window"
        }
        val cadence = meanCadence(steps)
        val distance = gnssTrajectory.zipWithNext().sumOf { (first, second) -> distanceMeters(first, second) }
        val stepLength = distance / steps.size
        require(stepLength.isFinite() && stepLength in config.minimumStepLengthMeters..config.maximumStepLengthMeters) {
            "Trusted GNSS window produces an implausible step length"
        }
        observations += CalibrationObservation(cadence, stepLength)
        while (observations.size > config.maximumCalibrationWindows) observations.removeFirst()
        calibration = fit().copy(frozen = false)
        return calibration
    }

    fun currentCalibration(): StrideCalibration = calibration

    private fun fit(): StrideCalibration {
        val meanCadence = observations.map { it.cadence }.average()
        val meanLength = observations.map { it.stepLength }.average()
        val cadenceVariance = observations.sumOf { (it.cadence - meanCadence).pow(2) }
        val rawGain = if (cadenceVariance <= MIN_CADENCE_VARIANCE) 0.0 else
            observations.sumOf { (it.cadence - meanCadence) * (it.stepLength - meanLength) } / cadenceVariance
        val gain = rawGain.coerceIn(-config.maximumCadenceGainMetersPerStepPerMinute,
            config.maximumCadenceGainMetersPerStepPerMinute)
        val atReference = (meanLength + (config.referenceCadenceStepsPerMinute - meanCadence) * gain)
            .coerceIn(config.minimumStepLengthMeters, config.maximumStepLengthMeters)
        val residualRms = sqrt(observations.sumOf {
            val predicted = atReference + (it.cadence - config.referenceCadenceStepsPerMinute) * gain
            (it.stepLength - predicted).pow(2)
        } / observations.size)
        val uncertainty = max(config.minimumUncertaintyMeters,
            max(residualRms, profile.defaultUncertaintyMeters / sqrt(observations.size.toDouble())))
        return StrideCalibration(atReference, gain, uncertainty, observations.size, frozen = false,
            referenceCadenceStepsPerMinute = config.referenceCadenceStepsPerMinute)
    }

    private fun meanCadence(steps: List<DetectedStep>): Double {
        val reported = steps.mapNotNull { it.cadenceStepsPerMinute }
        if (reported.isNotEmpty()) return reported.average()
        val elapsed = steps.last().timestamp.differenceNanos(steps.first().timestamp)
        require(elapsed > 0L) { "Step timestamps cannot determine cadence" }
        return (steps.size - 1) * MINUTE_NANOS / elapsed
    }

    private fun distanceMeters(first: GnssSample, second: GnssSample): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val a = sin(latitudeDelta / 2).pow(2) + cos(Math.toRadians(first.latitude)) *
            cos(Math.toRadians(second.latitude)) * sin(longitudeDelta / 2).pow(2)
        return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private data class CalibrationObservation(val cadence: Double, val stepLength: Double)

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val MINUTE_NANOS = 60_000_000_000.0
        const val MIN_CADENCE_VARIANCE = 1e-9
    }
}
