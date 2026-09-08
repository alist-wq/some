package com.example.navigation.gnss

data class Cn0ConstellationFeatures(
    val satelliteCount: Int,
    val meanDbHz: Double,
    val medianDbHz: Double,
    val varianceDbHzSquared: Double
)

data class SynchronizedSignalChange(
    val signedMeanChange: Double,
    val constellationCount: Int
)

data class Cn0FeatureSnapshot(
    val byConstellation: Map<GnssConstellation, Cn0ConstellationFeatures>,
    val synchronizedChange: SynchronizedSignalChange?
)

/** Extracts raw C/N0 statistics only; it makes no spoofing or trust conclusion. */
class Cn0FeatureExtractor(private val minimumSynchronizedChangeDbHz: Double = 3.0) {
    private var previousMeans: Map<GnssConstellation, Double> = emptyMap()

    init { require(minimumSynchronizedChangeDbHz.isFinite() && minimumSynchronizedChangeDbHz >= 0.0) }

    fun extract(sample: RawGnssSample): Cn0FeatureSnapshot {
        val features = sample.observations.mapNotNull { observation ->
            observation.cn0DbHz?.toDouble()?.takeIf { it.isFinite() }?.let { observation.constellation to it }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.features() }
        val changes = features.mapNotNull { (constellation, feature) ->
            previousMeans[constellation]?.let { constellation to (feature.meanDbHz - it) }
        }.filter { (_, change) -> kotlin.math.abs(change) >= minimumSynchronizedChangeDbHz }
        val synchronized = if (changes.size >= 2 && changes.all { it.second > 0.0 } || changes.size >= 2 && changes.all { it.second < 0.0 }) {
            SynchronizedSignalChange(changes.map { it.second }.average(), changes.size)
        } else null
        previousMeans = features.mapValues { it.value.meanDbHz }
        return Cn0FeatureSnapshot(features, synchronized)
    }

    private fun List<Double>.features(): Cn0ConstellationFeatures {
        val sorted = sorted()
        val mean = average()
        val median = if (size % 2 == 0) (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0 else sorted[size / 2]
        val variance = sumOf { (it - mean) * (it - mean) } / size
        return Cn0ConstellationFeatures(size, mean, median, variance)
    }
}
