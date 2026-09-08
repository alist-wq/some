package com.example.navigation.benchmark

import java.nio.file.Files
import java.nio.file.Path

enum class GnssCorruptionType { LOSS, CONSTANT_FALSE_LOCATION, LARGE_POSITION_JUMP, GRADUAL_DRIFT, FALSE_VELOCITY, FALSE_HEADING, DELAYED, NOISY, INTERMITTENT }

data class GnssCorruption(
    val type: GnssCorruptionType,
    val startSeconds: Long,
    val durationSeconds: Long
) {
    init {
        require(startSeconds >= 0) { "corruption.start must be non-negative" }
        require(durationSeconds > 0) { "corruption.duration must be positive" }
    }
}

data class BenchmarkScenario(
    val session: String,
    val route: String,
    val corruption: GnssCorruption
) {
    init {
        require(session.isNotBlank()) { "session must not be blank" }
        require(route.isNotBlank()) { "route must not be blank" }
    }
}

/** Strict parser for the deliberately small YAML scenario format documented by TASK 088. */
object BenchmarkScenarioParser {
    fun parse(text: String): BenchmarkScenario {
        val values = linkedMapOf<String, String>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trimEnd()
            if (line.isBlank()) return@forEachIndexed
            val indent = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val separator = line.indexOf(':')
            require(separator > 0) { "Invalid scenario line ${index + 1}" }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            val qualified = when (indent) {
                0 -> key
                2 -> "corruption.$key"
                else -> error("Unsupported indentation on scenario line ${index + 1}")
            }
            require(value.isNotEmpty() || qualified == "corruption") { "Missing value for $qualified" }
            require(qualified in setOf("session", "route", "corruption", "corruption.type", "corruption.start", "corruption.duration")) {
                "Unknown scenario field '$qualified'"
            }
            require(values.put(qualified, value) == null) { "Duplicate scenario field '$qualified'" }
        }
        val required = listOf("session", "route", "corruption.type", "corruption.start", "corruption.duration")
        require(required.all(values::containsKey)) { "Scenario must define: ${required.filterNot(values::containsKey).joinToString()}" }
        val type = values.getValue("corruption.type").uppercase().let {
            runCatching { GnssCorruptionType.valueOf(it) }.getOrElse { error("Unsupported corruption.type '$it'") }
        }
        return BenchmarkScenario(
            session = unquote(values.getValue("session")),
            route = unquote(values.getValue("route")),
            corruption = GnssCorruption(type, parseSeconds(values.getValue("corruption.start"), "start"), parseSeconds(values.getValue("corruption.duration"), "duration"))
        )
    }

    fun parse(path: Path): BenchmarkScenario = parse(Files.readString(path))

    private fun parseSeconds(value: String, field: String): Long = value.toLongOrNull() ?: error("corruption.$field must be an integer number of seconds")
    private fun unquote(value: String) = value.removeSurrounding("\"", "\"").removeSurrounding("'", "'")
}
