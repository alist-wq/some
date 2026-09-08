package com.example.navigation.gnss

import com.example.navigation.core.MonotonicTimestamp
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ObserverFix(
    val timestamp: MonotonicTimestamp,
    val wallClockEpochMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double
)

data class CachedTleCatalog(val retrievedAtEpochMillis: Long, val entries: List<TleReferenceEntry>)
data class TleReferenceEntry(
    val constellation: GnssConstellation,
    val svid: Int,
    val noradId: Int,
    val version: String,
    val tle: TLE
)

object CelesTrakTleParser {
    fun parse(content: String, retrievedAtEpochMillis: Long): CachedTleCatalog {
        val lines = content.lineSequence().map(String::trimEnd).filter(String::isNotBlank).toList()
        val entries = lines.windowed(3).mapNotNull { group ->
            if (group[0].startsWith("1 ") || !group[1].startsWith("1 ") || !group[2].startsWith("2 ")) return@mapNotNull null
            val identity = identity(group[0]) ?: return@mapNotNull null
            runCatching {
                val tle = TLE(group.toTypedArray())
                TleReferenceEntry(identity.first, identity.second, tle.catnum, fingerprint(group), tle)
            }.getOrNull()
        }
        return CachedTleCatalog(retrievedAtEpochMillis, entries.distinctBy { it.constellation to it.svid })
    }

    private fun identity(name: String): Pair<GnssConstellation, Int>? {
        val constellation = when {
            name.contains("GAGAN", true) -> GnssConstellation.SBAS
            name.contains("GPS", true) || name.contains("NAVSTAR", true) -> GnssConstellation.GPS
            name.contains("GALILEO", true) || name.startsWith("GSAT", true) -> GnssConstellation.GALILEO
            name.contains("GLONASS", true) -> GnssConstellation.GLONASS
            name.contains("BEIDOU", true) || name.startsWith("BEIDOU", true) -> GnssConstellation.BEIDOU
            else -> return null
        }
        val value = when (constellation) {
            GnssConstellation.BEIDOU -> Regex("\\(C(\\d{1,3})\\)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)
            GnssConstellation.GALILEO -> Regex("\\(GALILEO[ -](\\d{1,3})\\)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)
            else -> Regex("(?:PRN|SVID|SLOT)[ -]?(\\d{1,3})", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)
        }?.toIntOrNull() ?: return null
        return constellation to value
    }

    private fun fingerprint(lines: List<String>): String = MessageDigest.getInstance("SHA-256")
        .digest(lines.joinToString("\n").toByteArray(StandardCharsets.US_ASCII))
        .take(8).joinToString("") { "%02x".format(it) }
}

class TleCatalogCache(private val path: Path) {
    fun write(retrievedAtEpochMillis: Long, body: String) {
        require(CelesTrakTleParser.parse(body, retrievedAtEpochMillis).entries.isNotEmpty()) { "No usable GNSS TLE entries" }
        Files.createDirectories(path.parent)
        Files.newBufferedWriter(path, StandardCharsets.UTF_8).use { writer ->
            writer.write(retrievedAtEpochMillis.toString())
            writer.newLine()
            writer.write(body)
        }
    }

    fun read(): CachedTleCatalog? = runCatching {
        val lines = Files.newBufferedReader(path, StandardCharsets.UTF_8).use { it.readLines() }
        val retrieved = lines.first().toLong()
        CelesTrakTleParser.parse(lines.drop(1).joinToString("\n"), retrieved).takeIf { it.entries.isNotEmpty() }
    }.getOrNull()
}

/** SGP4 reference geometry; receiver data is neither changed nor replaced. */
class CachedTleSpacecraftReferenceProvider(
    private val catalog: () -> CachedTleCatalog?,
    private val wallClockNowMillis: () -> Long,
    private val maximumAgeMillis: Long = 72L * 60 * 60 * 1000
) : SpacecraftReferenceProvider {
    @Volatile private var observer: ObserverFix? = null

    fun updateObserver(fix: ObserverFix) { observer = fix }

    override fun referenceAt(timestamp: MonotonicTimestamp, constellation: GnssConstellation, svid: Int): SpacecraftReferenceEvidence? {
        val currentCatalog = catalog() ?: return null
        val ageMillis = (wallClockNowMillis() - currentCatalog.retrievedAtEpochMillis).coerceAtLeast(0)
        if (ageMillis > maximumAgeMillis) return null
        val fix = observer ?: return null
        val entry = currentCatalog.entries.firstOrNull { it.constellation == constellation && it.svid == svid } ?: return null
        val observationMillis = fix.wallClockEpochMillis + timestamp.differenceNanos(fix.timestamp) / 1_000_000
        return runCatching {
            val satPos = SatelliteFactory.createSatellite(entry.tle).getPosition(
                GroundStationPosition(fix.latitude, fix.longitude, fix.altitudeMeters), Date(observationMillis)
            )
            val radiusMeters = 6_378_137.0 + satPos.altitude * 1_000.0
            val lat = satPos.latitude
            val lon = satPos.longitude
            SpacecraftReferenceEvidence(
                constellation, svid, entry.noradId, SOURCE, entry.version,
                radiusMeters * cos(lat) * cos(lon), radiusMeters * cos(lat) * sin(lon), radiusMeters * sin(lat),
                satPos.elevation * 180.0 / PI, normalizedDegrees(satPos.azimuth * 180.0 / PI),
                ageMillis / 1_000.0, uncertaintyDegrees = 5.0
            )
        }.getOrNull()
    }

    private fun normalizedDegrees(value: Double) = (value % 360.0 + 360.0) % 360.0
    companion object { const val SOURCE = "CelesTrak GP/TLE + predict4java SGP4" }
}
