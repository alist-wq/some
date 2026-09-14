package com.example.navigation.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

enum class SettingsRecoveryDestination { LOCATION, MOTION, APP_DETAILS }

data class SettingsRecoveryLaunchPlan(
    val packageName: String,
    val destination: SettingsRecoveryDestination,
    val fallbackDestination: SettingsRecoveryDestination
) {
    val primaryIntent: Intent get() = intentFor(packageName, destination)
    val fallbackIntent: Intent get() = intentFor(packageName, fallbackDestination)
}

data class SettingsRecoveryCapability(
    val launchPlan: SettingsRecoveryLaunchPlan?,
    val disabledReason: String?
) {
    val isAvailable: Boolean get() = launchPlan != null

    init {
        require(isAvailable || !disabledReason.isNullOrBlank())
    }

    fun accessibilityLabel(): String = if (isAvailable) {
        "Open Android permission settings"
    } else {
        "Android permission settings unavailable: $disabledReason"
    }
}

/**
 * Plans an explicit, user-owned Android Settings recovery action. This class only resolves and
 * returns intents; the activity owns launching the selected intent and rechecking permissions.
 */
class AndroidSettingsRecoveryLauncher(
    private val packageName: String,
    private val canResolve: (SettingsRecoveryDestination) -> Boolean
) {
    fun capability(permissions: RuntimePermissionCapability): SettingsRecoveryCapability {
        val plan = planFor(permissions)
            ?: return SettingsRecoveryCapability(null, "Location and motion permissions are already granted")
        val resolved = when {
            canResolve(plan.destination) -> plan
            canResolve(plan.fallbackDestination) -> plan.copy(destination = plan.fallbackDestination)
            else -> null
        }
        return if (resolved != null) {
            SettingsRecoveryCapability(resolved, null)
        } else {
            SettingsRecoveryCapability(null, "No supported Android Settings page is available on this device")
        }
    }

    private fun planFor(permissions: RuntimePermissionCapability): SettingsRecoveryLaunchPlan? {
        return when {
            permissions.location != RuntimePermissionState.GRANTED -> SettingsRecoveryLaunchPlan(
                packageName = packageName,
                destination = SettingsRecoveryDestination.LOCATION,
                fallbackDestination = SettingsRecoveryDestination.APP_DETAILS
            )
            permissions.motion != RuntimePermissionState.GRANTED -> SettingsRecoveryLaunchPlan(
                packageName = packageName,
                destination = SettingsRecoveryDestination.MOTION,
                fallbackDestination = SettingsRecoveryDestination.APP_DETAILS
            )
            else -> null
        }
    }

    companion object {
        fun from(context: Context): AndroidSettingsRecoveryLauncher = AndroidSettingsRecoveryLauncher(
            packageName = context.packageName,
            canResolve = { destination -> intentFor(context.packageName, destination).resolveActivity(context.packageManager) != null }
        )
    }
}

private fun intentFor(packageName: String, destination: SettingsRecoveryDestination): Intent = when (destination) {
    SettingsRecoveryDestination.LOCATION -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    SettingsRecoveryDestination.MOTION, SettingsRecoveryDestination.APP_DETAILS -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
}
