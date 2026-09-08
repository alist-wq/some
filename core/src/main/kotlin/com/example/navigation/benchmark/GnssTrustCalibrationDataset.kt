package com.example.navigation.benchmark

enum class GnssCalibrationScenario { NORMAL, URBAN_MULTIPATH, WEAK_SIGNAL, GNSS_LOSS, AUTHORIZED_INJECTION }

data class GnssCalibrationRecording(
    val fingerprint: String,
    val scenario: GnssCalibrationScenario,
    val deviceModel: String,
    val chipset: String?,
    val apiLevel: Int,
    val consentConfirmed: Boolean,
    val anonymized: Boolean,
    val heldOut: Boolean,
    val hasAirAlertMarkers: Boolean
) {
    init {
        require(fingerprint.isNotBlank() && deviceModel.isNotBlank() && apiLevel > 0)
        require(consentConfirmed && anonymized) { "Calibration recordings require consent and anonymization" }
    }
}

data class GnssCalibrationDataset(val recordings: List<GnssCalibrationRecording>) {
    fun readinessIssues(): List<String> = buildList {
        if (recordings.size < 2) add("multiple recordings required")
        GnssCalibrationScenario.entries.forEach { scenario ->
            if (recordings.none { it.scenario == scenario }) add("missing $scenario recording")
        }
        if (recordings.none { it.heldOut }) add("held-out recording required")
        if (recordings.none { it.hasAirAlertMarkers }) add("air-alert-marked recording required")
        if (recordings.none { !it.hasAirAlertMarkers }) add("unmarked comparison recording required")
    }
}
