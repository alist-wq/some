package com.example.navigation.app

/** Shared local-frame policy for navigation motion. It deliberately has no network dependency. */
internal object NavigationMotionPerformance {
    const val mapInterpolationDurationMillis = 300L
    const val remoteAnimationDependencies = 0

    fun assess(sample: NavigationMotionProfileSample): NavigationMotionProfileAssessment =
        NavigationMotionProfileAssessment(
            maximumFrameIntervalMillis = sample.frameIntervalsMillis.maxOrNull(),
            maximumAnimationLatencyMillis = sample.animationLatenciesMillis.maxOrNull(),
            maximumMapUpdateCostMillis = sample.mapUpdateCostsMillis.maxOrNull(),
            maximumNavigationStateLatencyMillis = sample.navigationStateLatenciesMillis.maxOrNull(),
            memoryDeltaBytes = sample.memoryDeltaBytes,
            batteryDrainPercent = sample.batteryDrainPercent,
            offlineAnimationDependencies = remoteAnimationDependencies,
            reducedMotion = sample.reducedMotion
        )
}

/** Raw profiling observations. Missing device-only metrics remain explicitly unavailable. */
data class NavigationMotionProfileSample(
    val frameIntervalsMillis: List<Double> = emptyList(),
    val animationLatenciesMillis: List<Double> = emptyList(),
    val mapUpdateCostsMillis: List<Double> = emptyList(),
    val navigationStateLatenciesMillis: List<Double> = emptyList(),
    val memoryDeltaBytes: Long? = null,
    val batteryDrainPercent: Double? = null,
    val reducedMotion: Boolean
) {
    init {
        (frameIntervalsMillis + animationLatenciesMillis + mapUpdateCostsMillis + navigationStateLatenciesMillis)
            .forEach { require(it.isFinite() && it >= 0.0) }
        require(memoryDeltaBytes == null || memoryDeltaBytes >= 0L)
        require(batteryDrainPercent == null || (batteryDrainPercent.isFinite() && batteryDrainPercent >= 0.0))
    }
}

data class NavigationMotionProfileAssessment(
    val maximumFrameIntervalMillis: Double?,
    val maximumAnimationLatencyMillis: Double?,
    val maximumMapUpdateCostMillis: Double?,
    val maximumNavigationStateLatencyMillis: Double?,
    val memoryDeltaBytes: Long?,
    val batteryDrainPercent: Double?,
    val offlineAnimationDependencies: Int,
    val reducedMotion: Boolean
)
