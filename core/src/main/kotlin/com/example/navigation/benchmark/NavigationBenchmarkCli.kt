package com.example.navigation.benchmark

import com.example.navigation.replay.ReplayClock
import com.example.navigation.replay.ReplayClockMode
import com.example.navigation.replay.ReplayEngine
import com.example.navigation.replay.SensorSessionReader
import com.example.navigation.replay.SensorSessionRecord
import com.example.navigation.replay.SensorType
import java.nio.file.Files
import java.nio.file.Path

data class NavigationBenchmarkCommand(val session: Path, val scenario: Path, val algorithmConfig: Path)

/** The deliberately minimal algorithm-config format required by the benchmark CLI. */
data class BenchmarkAlgorithmConfig(val algorithmId: String) {
    init { require(algorithmId == "replay_passthrough") { "Unsupported algorithm '$algorithmId'" } }
}

data class NavigationBenchmarkExecution(
    val scenario: BenchmarkScenario,
    val algorithm: BenchmarkAlgorithmConfig,
    val sessionId: String,
    val replayedRecordCount: Int,
    val recordCounts: Map<SensorType, Int>,
    val metrics: NavigationBenchmarkMetrics? = null
)

/** Executes a deterministic replay preflight and produces portable benchmark reports. */
object NavigationBenchmarkCli {
    fun parse(arguments: List<String>): NavigationBenchmarkCommand {
        require(arguments.size == 3 && arguments.all { it.isNotBlank() }) {
            "Usage: runNavigationBenchmark -PbenchmarkSession=<session> -PbenchmarkScenario=<scenario> -PbenchmarkAlgorithmConfig=<config>"
        }
        return NavigationBenchmarkCommand(Path.of(arguments[0]), Path.of(arguments[1]), Path.of(arguments[2]))
    }

    fun execute(command: NavigationBenchmarkCommand): NavigationBenchmarkExecution {
        require(Files.isRegularFile(command.session)) { "Session file does not exist: ${command.session}" }
        require(Files.isRegularFile(command.scenario)) { "Scenario file does not exist: ${command.scenario}" }
        require(Files.isRegularFile(command.algorithmConfig)) { "Algorithm config file does not exist: ${command.algorithmConfig}" }
        val scenario = BenchmarkScenarioParser.parse(command.scenario)
        val algorithm = parseAlgorithmConfig(Files.readString(command.algorithmConfig))
        SensorSessionReader(command.session).use { reader ->
            require(reader.header.sessionId == scenario.session) {
                "Scenario session '${scenario.session}' does not match recorded session '${reader.header.sessionId}'"
            }
            reader.records().forEach { }
            require(reader.isComplete) { "Recorded session is incomplete: ${command.session}" }
        }

        val counts = mutableMapOf<SensorType, Int>()
        var total = 0
        ReplayEngine(command.session, ReplayClock(ReplayClockMode.MAX_SPEED)).replay(consumer = { record ->
            total++
            val type = record.sensorType()
            counts[type] = (counts[type] ?: 0) + 1
        })
        return NavigationBenchmarkExecution(scenario, algorithm, scenario.session, total, counts.toSortedMap(compareBy { it.name }))
    }

    fun renderHumanReadable(execution: NavigationBenchmarkExecution): String = buildString {
        appendLine("Navigation benchmark results")
        appendLine("Session: ${execution.sessionId}")
        appendLine("Route: ${execution.scenario.route}")
        appendLine("Algorithm: ${execution.algorithm.algorithmId}")
        appendLine("Corruption: ${execution.scenario.corruption.type} at ${execution.scenario.corruption.startSeconds}s for ${execution.scenario.corruption.durationSeconds}s")
        appendLine("Replayed records: ${execution.replayedRecordCount}")
        appendLine("Record counts: ${execution.recordCounts.entries.joinToString { "${it.key.name}=${it.value}" }}")
        if (execution.metrics == null) appendLine("Navigation metrics: unavailable (the selected replay_passthrough algorithm emits no navigation estimates)")
        else append(renderHumanReadable(execution.metrics))
    }

    fun renderHumanReadable(metrics: NavigationBenchmarkMetrics): String = buildString {
        appendLine("Correct route: ${metrics.correctRoutePercentage ?: "unavailable"}%")
        appendLine("Mean route-progress error: ${metrics.meanRouteProgressErrorMeters ?: "unavailable"} m")
        appendLine("Maximum route-progress error: ${metrics.maxRouteProgressErrorMeters ?: "unavailable"} m")
        appendLine("Wrong branches: ${metrics.wrongBranchCount}")
        appendLine("GNSS false rejections: ${metrics.gnssFalseRejectionCount}")
        appendLine("Low-confidence duration: ${metrics.lowConfidenceDurationNanos} ns")
    }

    fun renderMachineReadable(execution: NavigationBenchmarkExecution): String = buildString {
        append("{\"session\":\"").append(execution.sessionId.jsonEscaped()).append("\",")
        append("\"route\":\"").append(execution.scenario.route.jsonEscaped()).append("\",")
        append("\"algorithm\":\"").append(execution.algorithm.algorithmId.jsonEscaped()).append("\",")
        append("\"replayedRecordCount\":").append(execution.replayedRecordCount).append(',')
        append("\"recordCounts\":{")
        append(execution.recordCounts.entries.joinToString(",") { "\"${it.key.name}\":${it.value}" })
        append("},\"metrics\":").append(execution.metrics?.let(::renderMachineReadable) ?: "null").append('}')
    }

    fun renderMachineReadable(metrics: NavigationBenchmarkMetrics): String = """{"correctRoutePercentage":${metrics.correctRoutePercentage.json()},"meanRouteProgressErrorMeters":${metrics.meanRouteProgressErrorMeters.json()},"maxRouteProgressErrorMeters":${metrics.maxRouteProgressErrorMeters.json()},"wrongBranchCount":${metrics.wrongBranchCount},"meanGnssRejectionDelayNanos":${metrics.meanGnssRejectionDelayNanos.json()},"gnssFalseRejectionCount":${metrics.gnssFalseRejectionCount},"meanGnssRecoveryTimeNanos":${metrics.meanGnssRecoveryTimeNanos.json()},"lowConfidenceDurationNanos":${metrics.lowConfidenceDurationNanos}}"""

    @JvmStatic fun main(arguments: Array<String>) {
        val execution = execute(parse(arguments.toList()))
        println(renderHumanReadable(execution))
        println(renderMachineReadable(execution))
    }

    private fun parseAlgorithmConfig(text: String): BenchmarkAlgorithmConfig {
        val lines = text.lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toList()
        require(lines.size == 1 && lines.single().startsWith("algorithm:")) { "Algorithm config must contain exactly: algorithm: replay_passthrough" }
        return BenchmarkAlgorithmConfig(lines.single().substringAfter(':').trim())
    }

    private fun SensorSessionRecord.sensorType(): SensorType = when (this) {
        is com.example.navigation.replay.GnssSessionRecord, is com.example.navigation.replay.GnssStatusSessionRecord -> SensorType.GNSS
        is com.example.navigation.replay.RawGnssSessionRecord -> SensorType.RAW_GNSS
        is com.example.navigation.replay.GyroscopeSessionRecord -> SensorType.GYROSCOPE
        is com.example.navigation.replay.AccelerometerSessionRecord -> SensorType.ACCELEROMETER
        is com.example.navigation.replay.GameRotationVectorSessionRecord -> SensorType.GAME_ROTATION_VECTOR
        is com.example.navigation.replay.MagnetometerSessionRecord -> SensorType.MAGNETOMETER
        is com.example.navigation.replay.BarometerSessionRecord -> SensorType.BAROMETER
        is com.example.navigation.replay.VehicleSpeedSessionRecord -> SensorType.VEHICLE_SPEED
        is com.example.navigation.replay.NavigationModeSessionRecord -> SensorType.NAVIGATION_MODE
        is com.example.navigation.replay.SessionMarker -> SensorType.MARKER
        is com.example.navigation.replay.PedestrianMetadataSessionRecord -> SensorType.PEDESTRIAN_METADATA
        is com.example.navigation.replay.PedestrianPoseSessionRecord -> SensorType.PEDESTRIAN_POSE
        is com.example.navigation.replay.PedestrianEventSessionRecord -> SensorType.PEDESTRIAN_EVENT
        is com.example.navigation.replay.GnssTrustSessionRecord -> SensorType.GNSS_TRUST
        is com.example.navigation.replay.WeightedGnssSolutionSessionRecord -> SensorType.WEIGHTED_GNSS_SOLUTION
        is com.example.navigation.replay.AirAlertSessionRecord -> SensorType.AIR_ALERT
        is com.example.navigation.replay.AndroidLocationEvidenceSessionRecord -> SensorType.ANDROID_LOCATION_EVIDENCE
        is com.example.navigation.replay.GnssCollectorCapabilitySessionRecord -> SensorType.GNSS_COLLECTOR_CAPABILITY
        is com.example.navigation.replay.ExternalEvidenceStatusSessionRecord -> SensorType.EXTERNAL_EVIDENCE_STATUS
    }

    private fun Number?.json(): String = this?.toString() ?: "null"
    private fun String.jsonEscaped(): String = replace("\\", "\\\\").replace("\"", "\\\"")
}
