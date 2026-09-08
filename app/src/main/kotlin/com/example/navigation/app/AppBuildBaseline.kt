package com.example.navigation.app

import com.example.navigation.core.MonotonicTimestamp

/** Verifies the Android module can consume shared, Android-free core models. */
internal fun appBuildBaselineTimestamp(): MonotonicTimestamp = MonotonicTimestamp(0L)
