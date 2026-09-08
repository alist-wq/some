package com.example.navigation.app

internal enum class MapLoadState { LOADING, READY, UNAVAILABLE }

internal data class AsyncFeedback(val label: String, val actionLabel: String?)

internal fun mapLoadFeedback(state: MapLoadState): AsyncFeedback = when (state) {
    MapLoadState.LOADING -> AsyncFeedback("LOADING OFFLINE MAP…", null)
    MapLoadState.READY -> AsyncFeedback("OFFLINE MAP READY", null)
    MapLoadState.UNAVAILABLE -> AsyncFeedback("OFFLINE MAP UNAVAILABLE", "RETRY MAP")
}
