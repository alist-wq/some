package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLifecyclePolicyTest {
    @Test fun `screen off and background keep service and navigation state active`() {
        listOf(NavigationLifecycleEvent.SCREEN_OFF, NavigationLifecycleEvent.APP_BACKGROUND).forEach { event ->
            val decision = NavigationLifecyclePolicy.on(event)
            assertTrue(decision.keepForegroundServiceActive)
            assertTrue(decision.preserveNavigationState)
            assertEquals(false, decision.rebindActivityToService)
        }
    }

    @Test fun `rotation and activity recreation preserve state and rebind`() {
        listOf(NavigationLifecycleEvent.SCREEN_ROTATION, NavigationLifecycleEvent.ACTIVITY_RECREATION).forEach { event ->
            val decision = NavigationLifecyclePolicy.on(event)
            assertTrue(decision.keepForegroundServiceActive)
            assertTrue(decision.preserveNavigationState)
            assertTrue(decision.rebindActivityToService)
        }
    }
}
