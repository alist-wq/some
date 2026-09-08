package com.example.navigation.replay

import com.example.navigation.navigation.NavigationMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

/** Metadata describing the provenance and test conditions of one recorded session. */
data class DatasetMetadata(
    val sessionId: String,
    val date: LocalDate,
    val region: String,
    val mode: NavigationMode,
    val phoneModel: String,
    val obdAdapter: String,
    val route: String,
    val conditions: String,
    val knownAnomalies: List<String>,
    val groundTruthAvailable: Boolean
) {
    init {
        require(sessionId.isNotBlank())
        require(region.isNotBlank())
        require(mode != NavigationMode.UNKNOWN) { "Dataset metadata mode must be VEHICLE or PEDESTRIAN" }
        require(phoneModel.isNotBlank())
        require(obdAdapter.isNotBlank())
        require(route.isNotBlank())
        require(conditions.isNotBlank())
        require(knownAnomalies.none { it.isBlank() })
    }
}

/** Strict key/value parser for the metadata sidecar format documented by TASK 103. */
object DatasetMetadataParser {
    private val fields = setOf("sessionId", "date", "region", "mode", "phoneModel", "obdAdapter", "route", "conditions", "knownAnomalies", "groundTruthAvailable")

    fun parse(text: String): DatasetMetadata {
        val values = linkedMapOf<String, String>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isBlank()) return@forEachIndexed
            val separator = line.indexOf(':')
            require(separator > 0) { "Invalid metadata line ${index + 1}" }
            val key = line.substring(0, separator).trim()
            require(key in fields) { "Unknown metadata field '$key'" }
            require(values.put(key, line.substring(separator + 1).trim()) == null) { "Duplicate metadata field '$key'" }
        }
        require(values.keys == fields) { "Metadata must define exactly: ${fields.filterNot(values::containsKey).joinToString()}" }
        val mode = runCatching { NavigationMode.valueOf(values.getValue("mode").uppercase()) }.getOrElse { error("mode must be VEHICLE or PEDESTRIAN") }
        val anomalies = values.getValue("knownAnomalies").let { value ->
            if (value == "[]" || value.equals("none", ignoreCase = true)) emptyList() else value.split(',').map { unquote(it.trim()) }.filter { it.isNotEmpty() }
        }
        return DatasetMetadata(
            unquote(values.getValue("sessionId")),
            runCatching { LocalDate.parse(values.getValue("date")) }.getOrElse { error("date must be ISO-8601 yyyy-MM-dd") },
            unquote(values.getValue("region")), mode,
            unquote(values.getValue("phoneModel")), unquote(values.getValue("obdAdapter")),
            unquote(values.getValue("route")), unquote(values.getValue("conditions")), anomalies,
            values.getValue("groundTruthAvailable").toBooleanStrictOrNull() ?: error("groundTruthAvailable must be true or false")
        )
    }

    fun parse(path: Path): DatasetMetadata = parse(Files.readString(path))
    private fun unquote(value: String) = value.removeSurrounding("\"", "\"").removeSurrounding("'", "'")
}
