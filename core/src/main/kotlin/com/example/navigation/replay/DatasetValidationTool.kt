package com.example.navigation.replay

import com.example.navigation.core.MonotonicTimestamp
import com.example.navigation.obd.VehicleSpeedSource
import java.nio.file.Path

enum class DatasetValidationIssueType {
    TIMESTAMP_REVERSAL,
    LARGE_DATA_GAP,
    MISSING_GNSS,
    MISSING_IMU,
    INVALID_OBD,
    TRUNCATED_FILE
}

data class DatasetValidationIssue(
    val type: DatasetValidationIssueType,
    val detail: String,
    val timestamp: MonotonicTimestamp? = null
)

data class DatasetValidationReport(val issues: List<DatasetValidationIssue>) {
    val isValid: Boolean get() = issues.isEmpty()
}

data class DatasetValidationConfig(
    val maximumGapNanos: Long = 10_000_000_000L,
    val maximumVehicleSpeedMetersPerSecond: Double = 100.0
) {
    init {
        require(maximumGapNanos > 0)
        require(maximumVehicleSpeedMetersPerSecond > 0.0 && maximumVehicleSpeedMetersPerSecond.isFinite())
    }
}

/** Read-only recorded-session validation for replay and benchmark eligibility. */
class DatasetValidationTool(private val config: DatasetValidationConfig = DatasetValidationConfig()) {
    fun validate(path: Path): DatasetValidationReport {
        val issues = mutableListOf<DatasetValidationIssue>()
        var previous: MonotonicTimestamp? = null
        var hasGnss = false
        var hasGyroscope = false
        var hasAccelerometer = false
        try {
            SensorSessionReader(path).use { reader ->
                reader.records().forEach { record ->
                    previous?.let { earlier ->
                        val delta = record.timestamp.differenceNanos(earlier)
                        when {
                            delta < 0 -> issues += DatasetValidationIssue(DatasetValidationIssueType.TIMESTAMP_REVERSAL, "Record timestamp precedes previous record", record.timestamp)
                            delta > config.maximumGapNanos -> issues += DatasetValidationIssue(DatasetValidationIssueType.LARGE_DATA_GAP, "Gap of $delta ns exceeds ${config.maximumGapNanos} ns", record.timestamp)
                        }
                    }
                    previous = record.timestamp
                    when (record) {
                        is GnssSessionRecord -> hasGnss = true
                        is GyroscopeSessionRecord -> hasGyroscope = true
                        is AccelerometerSessionRecord -> hasAccelerometer = true
                        is VehicleSpeedSessionRecord -> {
                            val sample = record.sample
                            if (!sample.metersPerSecond.isFinite() || sample.metersPerSecond < 0.0 ||
                                sample.metersPerSecond > config.maximumVehicleSpeedMetersPerSecond || sample.source == VehicleSpeedSource.UNKNOWN
                            ) issues += DatasetValidationIssue(DatasetValidationIssueType.INVALID_OBD, "Invalid vehicle speed telemetry", sample.timestamp)
                        }
                        else -> Unit
                    }
                }
                if (!reader.isComplete) issues += DatasetValidationIssue(DatasetValidationIssueType.TRUNCATED_FILE, "Session has no completion footer")
            }
        } catch (error: Throwable) {
            issues += DatasetValidationIssue(DatasetValidationIssueType.TRUNCATED_FILE, "Unable to read complete session: ${error::class.simpleName}")
        }
        if (!hasGnss) issues += DatasetValidationIssue(DatasetValidationIssueType.MISSING_GNSS, "No processed GNSS observations")
        if (!hasGyroscope || !hasAccelerometer) issues += DatasetValidationIssue(DatasetValidationIssueType.MISSING_IMU, "Gyroscope and accelerometer observations are both required")
        return DatasetValidationReport(issues)
    }
}
