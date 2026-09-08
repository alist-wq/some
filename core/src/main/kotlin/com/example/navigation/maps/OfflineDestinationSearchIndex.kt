package com.example.navigation.maps

import com.example.navigation.routing.GeoPoint
import java.security.MessageDigest
import java.text.Normalizer

enum class DestinationSourceType { ADDRESS, PLACE }

data class RegionalPackageVersion(val regionId: String, val mapVersion: String, val routingGraphVersion: String) {
    init { require(regionId.isNotBlank() && mapVersion.isNotBlank() && routingGraphVersion.isNotBlank()) }
}

data class DestinationIndexMetadata(
    val indexVersion: String,
    val packageVersion: RegionalPackageVersion,
    val checksumSha256: String
) {
    init { require(indexVersion.isNotBlank() && checksumSha256.matches(Regex("[0-9a-f]{64}"))) }
}

data class DestinationIndexRecord(
    val stableId: String,
    val sourceType: DestinationSourceType,
    val category: String?,
    val coordinate: GeoPoint,
    val displayLabel: String,
    val searchTokens: List<String>
) {
    init {
        require(stableId.isNotBlank() && displayLabel.isNotBlank())
        require(category == null || category.isNotBlank())
        require(searchTokens.isNotEmpty() && searchTokens.all { it.isNotBlank() })
    }
}

sealed interface DestinationIndexLoadResult {
    data object NoRegionInstalled : DestinationIndexLoadResult
    data class IndexUnavailable(val reason: String) : DestinationIndexLoadResult
    data class Ready(val index: OfflineDestinationSearchIndex) : DestinationIndexLoadResult
}

sealed interface DestinationSearchResult {
    data object NoRegionInstalled : DestinationSearchResult
    data class IndexUnavailable(val reason: String) : DestinationSearchResult
    data object QueryTooShort : DestinationSearchResult
    data object NoMatch : DestinationSearchResult
    data class Results(val packageVersion: RegionalPackageVersion, val matches: List<DestinationIndexRecord>) : DestinationSearchResult
}

/** Immutable offline address/place index; it never issues network requests. */
class OfflineDestinationSearchIndex private constructor(
    val metadata: DestinationIndexMetadata,
    private val records: List<DestinationIndexRecord>
) {
    fun search(query: String, maximumResults: Int = DEFAULT_MAXIMUM_RESULTS): DestinationSearchResult {
        require(maximumResults > 0)
        val normalizedQuery = normalize(query)
        if (normalizedQuery.length < MINIMUM_QUERY_LENGTH) return DestinationSearchResult.QueryTooShort
        val terms = normalizedQuery.split(' ')
        val matches = records.mapNotNull { record ->
            val searchable = listOf(normalize(record.displayLabel)) + record.searchTokens.map(::normalize)
            val rank = when {
                searchable.any { it == normalizedQuery } -> 0
                searchable.any { it.startsWith(normalizedQuery) } -> 1
                terms.all { term -> searchable.any { it.split(' ').any { token -> token.startsWith(term) } } } -> 2
                else -> return@mapNotNull null
            }
            rank to record
        }.sortedWith(compareBy<Pair<Int, DestinationIndexRecord>> { it.first }
            .thenBy { normalize(it.second.displayLabel) }
            .thenBy { it.second.stableId })
            .take(maximumResults)
            .map { it.second }
        return if (matches.isEmpty()) DestinationSearchResult.NoMatch else DestinationSearchResult.Results(metadata.packageVersion, matches)
    }

    companion object {
        const val MINIMUM_QUERY_LENGTH = 2
        const val DEFAULT_MAXIMUM_RESULTS = 20

        fun load(
            installedPackage: RegionalPackageVersion?,
            metadata: DestinationIndexMetadata?,
            records: List<DestinationIndexRecord>?
        ): DestinationIndexLoadResult {
            if (installedPackage == null) return DestinationIndexLoadResult.NoRegionInstalled
            if (metadata == null || records == null) return DestinationIndexLoadResult.IndexUnavailable("Destination index is unavailable")
            if (metadata.packageVersion != installedPackage) return DestinationIndexLoadResult.IndexUnavailable("Destination index does not match the installed regional package")
            if (records.map { it.stableId }.toSet().size != records.size) return DestinationIndexLoadResult.IndexUnavailable("Destination index contains duplicate stable IDs")
            val checksum = checksum(records)
            if (checksum != metadata.checksumSha256) return DestinationIndexLoadResult.IndexUnavailable("Destination index checksum mismatch")
            return DestinationIndexLoadResult.Ready(OfflineDestinationSearchIndex(metadata, records.sortedBy { it.stableId }))
        }

        fun checksum(records: List<DestinationIndexRecord>): String = MessageDigest.getInstance("SHA-256").digest(
            records.sortedBy { it.stableId }.joinToString("\n") { record ->
                listOf(record.stableId, record.sourceType, record.category.orEmpty(), record.coordinate.latitude, record.coordinate.longitude,
                    record.displayLabel, record.searchTokens.joinToString("|")) .joinToString("\u001f")
            }.toByteArray(Charsets.UTF_8)
        ).joinToString("") { "%02x".format(it) }

        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex(" +"), " ")
    }
}
