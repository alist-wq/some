package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSettingsRecoveryLauncherTest {
    @Test fun `location recovery resolves location settings before app details fallback`() {
        val launcher = AndroidSettingsRecoveryLauncher("com.example.renav") { intent ->
            intent == SettingsRecoveryDestination.LOCATION
        }

        val capability = launcher.capability(revokedLocation())

        assertTrue(capability.isAvailable)
        assertEquals(SettingsRecoveryDestination.LOCATION, capability.launchPlan?.destination)
        assertEquals(SettingsRecoveryDestination.LOCATION, capability.launchPlan?.destination)
    }

    @Test fun `location recovery safely falls back to app details when location page is unresolved`() {
        val launcher = AndroidSettingsRecoveryLauncher("com.example.renav") { intent ->
            intent == SettingsRecoveryDestination.APP_DETAILS
        }

        val plan = launcher.capability(revokedLocation()).launchPlan

        assertNotNull(plan)
        assertEquals(SettingsRecoveryDestination.APP_DETAILS, plan?.destination)
        assertEquals("com.example.renav", plan?.packageName)
    }

    @Test fun `motion recovery uses app details and no recovery is offered once permissions are granted`() {
        val launcher = AndroidSettingsRecoveryLauncher("com.example.renav") { true }

        val motion = launcher.capability(RuntimePermissionCapability(RuntimePermissionState.GRANTED, RuntimePermissionState.REVOKED, true))
        val complete = launcher.capability(RuntimePermissionCapability(RuntimePermissionState.GRANTED, RuntimePermissionState.GRANTED, false))

        assertEquals(SettingsRecoveryDestination.MOTION, motion.launchPlan?.destination)
        assertEquals(SettingsRecoveryDestination.MOTION, motion.launchPlan?.destination)
        assertFalse(complete.isAvailable)
        assertTrue(complete.accessibilityLabel().contains("already granted"))
    }

    @Test fun `unsupported settings keeps recovery unavailable with an accessible reason after return`() {
        val launcher = AndroidSettingsRecoveryLauncher("com.example.renav") { false }

        val capabilityBefore = launcher.capability(revokedLocation())
        val capabilityAfterUnchangedReturn = launcher.capability(revokedLocation())

        assertFalse(capabilityBefore.isAvailable)
        assertFalse(capabilityAfterUnchangedReturn.isAvailable)
        assertTrue(capabilityAfterUnchangedReturn.accessibilityLabel().contains("No supported Android Settings page"))
    }

    private fun revokedLocation() = RuntimePermissionCapability(
        location = RuntimePermissionState.REVOKED,
        motion = RuntimePermissionState.GRANTED,
        settingsRecoveryEligible = true
    )
}
