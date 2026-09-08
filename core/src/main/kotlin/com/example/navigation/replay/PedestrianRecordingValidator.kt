package com.example.navigation.replay

import com.example.navigation.navigation.NavigationMode
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

enum class PedestrianAcceptanceDisposition { ACCEPTED, ACCEPTED_WITH_LIMITATIONS, REJECTED }

data class PedestrianAcceptanceReport(
    val disposition: PedestrianAcceptanceDisposition,
    val schemaVersion: Int?,
    val durationNanos: Long?,
    val counts: Map<String, Int>,
    val requiredFailures: List<String>,
    val limitations: List<String>,
    val optionalUnavailable: List<String>
) {
    fun normalizedText(): String = buildString {
        appendLine("disposition=$disposition")
        appendLine("schemaVersion=${schemaVersion ?: "unreadable"}")
        appendLine("durationNanos=${durationNanos ?: "unavailable"}")
        counts.toSortedMap().forEach { (key, value) -> appendLine("count.$key=$value") }
        requiredFailures.sorted().forEach { appendLine("failure=$it") }
        limitations.sorted().forEach { appendLine("limitation=$it") }
        optionalUnavailable.sorted().forEach { appendLine("optionalUnavailable=$it") }
    }
}

data class PedestrianValidationThresholds(
    val minimumDurationNanos: Long = 30_000_000_000L,
    val minimumGnssSamples: Int = 2,
    val minimumMotionSamples: Int = 100,
    val maximumGapNanos: Long = 5_000_000_000L
)

/** Read-only acceptance check. It never changes or fills gaps in a recording. */
class PedestrianRecordingValidator(
    private val thresholds: PedestrianValidationThresholds = PedestrianValidationThresholds()
) {
    fun validate(path: Path): PedestrianAcceptanceReport {
        val records = mutableListOf<SensorSessionRecord>()
        var version: Int? = null
        var complete = false
        var endNanos: Long? = null
        var startNanos: Long? = null
        try {
            SensorSessionReader(path).use { reader ->
                version = reader.header.schemaVersion
                startNanos = reader.header.startedAt.nanos
                records += reader.records().toList()
                complete = reader.isComplete
                endNanos = reader.completedAt?.nanos
            }
        } catch (error: Throwable) {
            return PedestrianAcceptanceReport(PedestrianAcceptanceDisposition.REJECTED, version, null, emptyMap(),
                listOf("Session is unreadable: ${error::class.simpleName}"), emptyList(), listOf("OBD"))
        }
        val counts = records.groupingBy { it::class.simpleName ?: "Unknown" }.eachCount()
        val failures = mutableListOf<String>()
        val limitations = mutableListOf<String>()
        if (!complete) failures += "Session has no clean completion footer"
        if (records.zipWithNext().any { (a, b) -> b.timestamp < a.timestamp }) failures += "Record timestamps are not monotonic"
        val duration = endNanos?.minus(startNanos ?: endNanos!!)
        if (duration == null || duration < thresholds.minimumDurationNanos) limitations += "Recording duration is below the recommended minimum"
        val metadata = records.filterIsInstance<PedestrianMetadataSessionRecord>().singleOrNull()?.metadata
        val hasPedestrianMode = records.filterIsInstance<NavigationModeSessionRecord>().any { it.mode == NavigationMode.PEDESTRIAN }
        if (metadata == null || !hasPedestrianMode) failures += "Explicit pedestrian mode and metadata are required"
        if (metadata != null && !metadata.consentConfirmed) failures += "Consent is not confirmed"
        if (metadata != null && !metadata.anonymized) limitations += "Recording is not marked anonymized"
        fun count(name: String) = counts[name] ?: 0
        if (count("GnssSessionRecord") < thresholds.minimumGnssSamples) failures += "GNSS stream is missing or too sparse"
        if (count("GyroscopeSessionRecord") < thresholds.minimumMotionSamples) failures += "Gyroscope stream is missing or too sparse"
        if (count("AccelerometerSessionRecord") < thresholds.minimumMotionSamples) failures += "Accelerometer stream is missing or too sparse"
        if (count("GameRotationVectorSessionRecord") < thresholds.minimumMotionSamples) failures += "Rotation-vector stream is missing or too sparse"
        if (records.none { it is PedestrianPoseSessionRecord }) failures += "Phone-pose labels are missing"
        if (records.none { it is PedestrianEventSessionRecord }) limitations += "No pedestrian events were labelled"
        if (metadata != null && metadata.routeIdentifier == null && metadata.routeGeometryReference == null) limitations += "Reference route is unavailable"
        val maxGap = records.zipWithNext().maxOfOrNull { (a, b) -> b.timestamp.nanos - a.timestamp.nanos } ?: 0L
        if (maxGap > thresholds.maximumGapNanos) limitations += "A timing gap exceeds the configured limit"
        val disposition = when {
            failures.isNotEmpty() -> PedestrianAcceptanceDisposition.REJECTED
            limitations.isNotEmpty() -> PedestrianAcceptanceDisposition.ACCEPTED_WITH_LIMITATIONS
            else -> PedestrianAcceptanceDisposition.ACCEPTED
        }
        return PedestrianAcceptanceReport(disposition, version, duration, counts, failures, limitations,
            buildList { if (count("VehicleSpeedSessionRecord") == 0) add("OBD"); if (count("RawGnssSessionRecord") == 0) add("Raw GNSS") })
    }

    fun writeReport(sessionPath: Path, reportPath: Path = sessionPath.resolveSibling("${sessionPath.fileName}.acceptance.txt")): Path {
        Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8).use { writer ->
            writer.write(validate(sessionPath).normalizedText())
        }
        return reportPath
    }
}
