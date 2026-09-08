package com.example.navigation.benchmark

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BenchmarkRegressionEvaluatorTest {
    private val evaluator = BenchmarkRegressionEvaluator()

    @Test fun `fails configured route branch and progress regression thresholds`() {
        val result = evaluator.evaluate(
            metrics(94.0, 12.1, 1),
            BenchmarkRegressionBaseline(meanRouteProgressErrorMeters = 10.0),
            BenchmarkRegressionThresholds(95.0, 0, 0.20)
        )
        assertFalse(result.passed)
        assertTrue(result.failures.size == 3)
        assertFailsWith<IllegalStateException> { result.requirePassed() }
    }

    @Test fun `allows disabled thresholds`() {
        val result = evaluator.evaluate(
            metrics(null, null, 4), null,
            BenchmarkRegressionThresholds(null, null, null)
        )
        assertTrue(result.passed)
    }

    private fun metrics(correctRoute: Double?, meanProgressError: Double?, wrongBranches: Int) = NavigationBenchmarkMetrics(
        correctRoute, meanProgressError, null, wrongBranches, null, 0, null, 0, emptyList()
    )
}
