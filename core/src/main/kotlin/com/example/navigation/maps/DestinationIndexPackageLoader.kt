package com.example.navigation.maps

import com.example.navigation.routing.GeoPoint
import java.nio.file.Files

/** Parses an already signature- and file-checksum-verified JSONL release for offline search. */
object DestinationIndexPackageLoader {
    fun load(verified: VerifiedDestinationIndexPackage): DestinationIndexLoadResult = try {
        val records = Files.readAllLines(verified.index).filter { it.isNotBlank() }.map(::record)
        val version = RegionalPackageVersion(verified.regionId, verified.mapCompatibilityVersion, "unavailable")
        OfflineDestinationSearchIndex.load(version, DestinationIndexMetadata(verified.packageVersion, version, OfflineDestinationSearchIndex.checksum(records)), records)
    } catch (error: Exception) {
        DestinationIndexLoadResult.IndexUnavailable("Destination-index package cannot be loaded: ${error.message ?: error.javaClass.simpleName}")
    }

    private fun record(line: String): DestinationIndexRecord {
        fun string(name: String): String = Regex("\\\"$name\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(line)?.groupValues?.get(1)?.let(::unescape)
            ?: throw IllegalArgumentException("record is missing $name")
        fun nullableString(name: String): String? {
            if (Regex("\\\"$name\\\"\\s*:\\s*null").containsMatchIn(line)) return null
            return string(name)
        }
        fun number(name: String): Double = Regex("\\\"$name\\\"\\s*:\\s*(-?(?:\\d+)(?:\\.\\d+)?)").find(line)?.groupValues?.get(1)?.toDouble()
            ?: throw IllegalArgumentException("record is missing $name")
        val tokens = Regex("\\\"searchTokens\\\"\\s*:\\s*\\[([^]]*)]").find(line)?.groupValues?.get(1)
            ?.let { Regex("\\\"((?:\\\\.|[^\\\"])*)\\\"").findAll(it).map { match -> unescape(match.groupValues[1]) }.toList() }
            ?: throw IllegalArgumentException("record is missing searchTokens")
        return DestinationIndexRecord(string("stableId"), DestinationSourceType.valueOf(string("sourceType")), nullableString("category"), GeoPoint(number("latitude"), number("longitude")), string("displayLabel"), tokens)
    }

    private fun unescape(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val character = value[index++]
            if (character != '\\') append(character) else {
                require(index < value.length) { "unterminated escape" }
                when (val escaped = value[index++]) {
                    '"', '\\', '/' -> append(escaped)
                    'b' -> append('\b'); 'f' -> append('\u000c'); 'n' -> append('\n'); 'r' -> append('\r'); 't' -> append('\t')
                    'u' -> { require(index + 4 <= value.length) { "truncated unicode escape" }; append(value.substring(index, index + 4).toInt(16).toChar()); index += 4 }
                    else -> throw IllegalArgumentException("unsupported escape: $escaped")
                }
            }
        }
    }
}
