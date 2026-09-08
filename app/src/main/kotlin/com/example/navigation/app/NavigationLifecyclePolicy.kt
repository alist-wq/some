package com.example.navigation.app

/**
 * Documents the Activity/service boundary for lifecycle events that must not stop navigation.
 * The foreground service owns active collection and navigation state; the Activity only observes it.
 */
enum class NavigationLifecycleEvent {
    SCREEN_OFF,
    APP_BACKGROUND,
    SCREEN_ROTATION,
    ACTIVITY_RECREATION
}

data class NavigationLifecycleDecision(
    val keepForegroundServiceActive: Boolean,
    val preserveNavigationState: Boolean,
    val rebindActivityToService: Boolean
)

object NavigationLifecyclePolicy {
    fun on(event: NavigationLifecycleEvent): NavigationLifecycleDecision = when (event) {
        NavigationLifecycleEvent.SCREEN_OFF,
        NavigationLifecycleEvent.APP_BACKGROUND -> NavigationLifecycleDecision(true, true, false)
        NavigationLifecycleEvent.SCREEN_ROTATION,
        NavigationLifecycleEvent.ACTIVITY_RECREATION -> NavigationLifecycleDecision(true, true, true)
    }
}
