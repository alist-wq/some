package com.example.navigation.maps

import android.content.Context
import java.util.Base64

data class MapDataVersionMetadata(
    val region: String,
    val downloadDateEpochMillis: Long,
    val mapVersion: String,
    val sizeBytes: Long,
    val routingGraphVersion: String
)

/** Persists the metadata needed to identify each downloaded map dataset. */
class MapDataVersionMetadataStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(regionId: Long, metadata: MapDataVersionMetadata) {
        preferences.edit().putString(regionId.toString(), encode(metadata)).apply()
    }

    fun updateSize(regionId: Long, sizeBytes: Long) {
        val existing = get(regionId) ?: return
        save(regionId, existing.copy(sizeBytes = sizeBytes))
    }

    fun get(regionId: Long): MapDataVersionMetadata? = preferences.getString(regionId.toString(), null)?.let(::decode)

    fun list(): List<MapDataVersionMetadata> = preferences.all.values.mapNotNull { (it as? String)?.let(::decode) }

    fun remove(regionId: Long) {
        preferences.edit().remove(regionId.toString()).apply()
    }

    internal companion object {
        const val PREFERENCES = "map_data_version_metadata"

        fun encode(metadata: MapDataVersionMetadata): String = listOf(
            metadata.region,
            metadata.downloadDateEpochMillis.toString(),
            metadata.mapVersion,
            metadata.sizeBytes.toString(),
            metadata.routingGraphVersion
        ).joinToString("|") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray()) }

        fun decode(value: String): MapDataVersionMetadata? = runCatching {
            val fields = value.split('|').map {
                String(Base64.getUrlDecoder().decode(it))
            }
            require(fields.size == 5)
            MapDataVersionMetadata(
                region = fields[0],
                downloadDateEpochMillis = fields[1].toLong(),
                mapVersion = fields[2],
                sizeBytes = fields[3].toLong(),
                routingGraphVersion = fields[4]
            )
        }.getOrNull()
    }
}
