package com.example.navigation.benchmark

/** CI policy. Null values disable the associated threshold. */
data class BenchmarkRegressionThresholds(
    val minimumCorrectRoutePercentage: Double? = 95.0,
    val maximumWrongBranchCount: Int? = 0,
    val maximumMeanProgressErrorIncreaseFraction: Double? = 0.20
) {
    init {
        require(minimumCorrectRoutePercentage == null || minimumCorrectRoutePercentage in 0.0..100.0)
        require(maximumWrongBranchCount == null || maximumWrongBranchCount >= 0)
        require(maximumMeanProgressErrorIncreaseFraction == null || maximumMeanProgressErrorIncreaseFraction >= 0.0)
    }
}

data class BenchmarkRegressionBaseline(val meanRouteProgressErrorMeters: Double?)

data class BenchmarkRegressionEvaluation(val passed: Boolean, val failures: List<String>) {
    /** Throws a non-zero-task failure when invoked by a CI benchmark runner. */
    fun requirePassed() {
        check(passed) { "Navigation benchmark regression failed: ${failures.joinToString("; ")}" }
    }
}

class BenchmarkRegressionEvaluator {
    fun evaluate(
        metrics: NavigationBenchmarkMetrics,
        baseline: BenchmarkRegressionBaseline?,
        thresholds: BenchmarkRegressionThresholds
    ): BenchmarkRegressionEvaluation {
        val failures = mutableListOf<String>()
        thresholds.minimumCorrectRoutePercentage?.let { minimum ->
            val actual = metrics.correctRoutePercentage
            if (actual == null || actual < minimum) failures += "correct route percentage must be at least $minimum (was ${actual ?: "unavailable"})"
        }
        thresholds.maximumWrongBranchCount?.let { maximum ->
            if (metrics.wrongBranchCount > maximum) failures += "wrong branch count must be at most $maximum (was ${metrics.wrongBranchCount})"
        }
        thresholds.maximumMeanProgressErrorIncreaseFraction?.let { maximumIncrease ->
            val previous = baseline?.meanRouteProgressErrorMeters
            val current = metrics.meanRouteProgressErrorMeters
            when {
                previous == null || current == null -> failures += "mean route-progress baseline and result are required for regression checking"
                previous == 0.0 && current > 0.0 -> failures += "mean route-progress error increased from zero"
                previous > 0.0 && (current - previous) / previous > maximumIncrease ->
                    failures += "mean route-progress error increased by more than ${maximumIncrease * 100}%"
            }
        }
        return BenchmarkRegressionEvaluation(failures.isEmpty(), failures)
    }
}
