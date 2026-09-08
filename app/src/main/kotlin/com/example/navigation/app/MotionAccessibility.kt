package com.example.navigation.app

import android.content.Context
import android.provider.Settings

internal data class MotionAccessibilityPreference(val reducedMotion: Boolean)

internal fun motionAccessibilityPreference(animatorDurationScale: Float): MotionAccessibilityPreference =
    MotionAccessibilityPreference(reducedMotion = !animatorDurationScale.isFinite() || animatorDurationScale <= 0f)

internal fun Context.motionAccessibilityPreference(): MotionAccessibilityPreference =
    motionAccessibilityPreference(
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    )
