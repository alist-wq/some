package com.example.navigation.app

data class RenavSplashFrame(
    val rotationDegrees: Float,
    val artworkAlpha: Float,
    val nameAlpha: Float,
    val complete: Boolean
)

/** Deterministic launch choreography: 1 to 3 revolutions/second, then fade/name/hand-off. */
object RenavSplashMotion {
    const val spinDurationMillis = 3_000L
    const val fadeDurationMillis = 600L
    const val handoffMillis = 5_000L

    fun frameAt(elapsedMillis: Long): RenavSplashFrame {
        require(elapsedMillis >= 0L)
        val seconds = elapsedMillis.coerceAtMost(spinDurationMillis) / 1_000.0
        val spinDurationSeconds = spinDurationMillis / 1_000.0
        val rotation = 360.0 * (seconds + seconds * seconds / spinDurationSeconds)
        val fade = ((elapsedMillis - spinDurationMillis).toDouble() / fadeDurationMillis).coerceIn(0.0, 1.0)
        return RenavSplashFrame(rotation.toFloat(), (1.0 - fade).toFloat(), fade.toFloat(), elapsedMillis >= handoffMillis)
    }
}
