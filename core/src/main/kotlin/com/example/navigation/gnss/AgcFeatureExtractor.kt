package com.example.navigation.gnss

data class AgcFeatureSnapshot(
    val supported: Boolean,
    val byConstellation: Map<GnssConstellation, Cn0ConstellationFeatures>,
    val synchronizedChange: SynchronizedSignalChange?
)

/** Extracts AGC patterns when supplied by device hardware; unsupported epochs remain explicit. */
class AgcFeatureExtractor(private val minimumSynchronizedChangeDb: Double = 1.0) {
    private var previousMeans: Map<GnssConstellation, Double> = emptyMap()

    init { require(minimumSynchronizedChangeDb.isFinite() && minimumSynchronizedChangeDb >= 0.0) }

    fun extract(sample: RawGnssSample): AgcFeatureSnapshot {
        val features = sample.observations.mapNotNull { observation ->
            observation.automaticGainControlDb?.takeIf { it.isFinite() }?.let { observation.constellation to it }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.agcFeatures() }
        if (features.isEmpty()) return AgcFeatureSnapshot(false, emptyMap(), null)
        val changes = features.mapNotNull { (constellation, feature) ->
            previousMeans[constellation]?.let { constellation to (feature.meanDbHz - it) }
        }.filter { (_, change) -> kotlin.math.abs(change) >= minimumSynchronizedChangeDb }
        val synchronized = if (changes.size >= 2 && (changes.all { it.second > 0.0 } || changes.all { it.second < 0.0 })) {
            SynchronizedSignalChange(changes.map { it.second }.average(), changes.size)
        } else null
        previousMeans = features.mapValues { it.value.meanDbHz }
        return AgcFeatureSnapshot(true, features, synchronized)
    }

    private fun List<Double>.agcFeatures(): Cn0ConstellationFeatures {
        val sorted = sorted(); val mean = average()
        val median = if (size % 2 == 0) (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0 else sorted[size / 2]
        val variance = sumOf { (it - mean) * (it - mean) } / size
        return Cn0ConstellationFeatures(size, mean, median, variance)
    }
}
