package com.example.navigation.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeptunRegionGeoJsonTest {
    private val fixture = """{"type":"FeatureCollection","features":[
      {"type":"Feature","properties":{"key":"test:test-raion"},"geometry":{"type":"Polygon","coordinates":[[[30.0,50.0],[31.0,50.0],[31.0,51.0],[30.0,51.0],[30.0,50.0]]]}}
    ]}"""

    @Test fun `region is resolved locally from provider polygon`() {
        val regions = NeptunRegionGeoJson.parse(fixture)
        assertEquals("test:test-raion", NeptunRegionGeoJson.containing(regions, 50.5, 30.5))
        assertNull(NeptunRegionGeoJson.containing(regions, 49.0, 30.5))
    }

    @Test(expected = org.json.JSONException::class)
    fun `malformed region response remains an error`() { NeptunRegionGeoJson.parse("{}") }
}
