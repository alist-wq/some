package com.example.navigation.replay

import com.example.navigation.gnss.GnssEpochTrustDecision
import com.example.navigation.gnss.GnssTrustReasonCode
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Produces a local derived-evidence summary without modifying the source session. */
class GnssTrustSessionReportWriter {
    fun render(decisions: List<GnssEpochTrustDecision>): String {
        val classified = decisions.mapNotNull { it.classification }
        val reasons = decisions.flatMap { it.reasons }.groupingBy { it }.eachCount()
            .toSortedMap(compareBy(GnssTrustReasonCode::name))
        return buildString {
            appendLine("GNSS TRUST SUMMARY (derived evidence; raw session records preserved)")
            appendLine("epochs=${decisions.size}; unavailable=${decisions.count { it.classification == null }}")
            appendLine("classifications=${classified.groupingBy { it }.eachCount()}")
            appendLine("reasons=$reasons")
            append("privacy=local-only; no automatic upload")
        }
    }

    fun write(session: Path, report: Path = session.resolveSibling("${session.fileName}.trust.txt")): Path {
        val records = SensorSessionReader(session).use { reader -> reader.records().toList() }
        val decisions = records.filterIsInstance<GnssTrustSessionRecord>().map { it.decision }
        val statuses = records.filterIsInstance<ExternalEvidenceStatusSessionRecord>().map { it.value }
        val alerts = records.filterIsInstance<AirAlertSessionRecord>()
        val base = render(decisions)
        Files.newBufferedWriter(report, StandardCharsets.UTF_8).use { writer ->
            writer.write(base)
            val statusSummary = statuses.groupingBy { it.source to it.state }.eachCount().entries
                .sortedBy { it.key.toString() }.joinToString { "${it.key.first}/${it.key.second}=${it.value}" }
            writer.write("\nexternalEvidence=$statusSummary")
            writer.write("\nairAlertMarkers=${alerts.size}")
            writer.write("\nairAlertCoverageKnown=${statuses.any { it.source == com.example.navigation.gnss.ExternalEvidenceSource.AIR_ALERT && it.state == com.example.navigation.gnss.ExternalEvidenceState.AVAILABLE }}")
            writer.write("\nattribution=Air-alert context: NEPTUN (informational aggregator, not an official warning system)")
        }
        return report
    }
}
