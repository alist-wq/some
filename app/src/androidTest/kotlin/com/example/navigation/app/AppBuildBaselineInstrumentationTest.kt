package com.example.navigation.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppBuildBaselineInstrumentationTest {
    @Test
    fun appModuleCanReferenceCoreModels() {
        assertEquals(0L, appBuildBaselineTimestamp().nanos)
    }
}
