package com.example.navigation.replay

import com.example.navigation.navigation.NavigationMode
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatasetMetadataTest {
    @Test fun `parses all required dataset metadata fields`() {
        val metadata = DatasetMetadataParser.parse("""
            sessionId: UA-KH-014
            date: 2026-09-02
            region: Kyiv, Ukraine
            mode: vehicle
            phoneModel: Test Phone
            obdAdapter: ELM327-A
            route: route-014
            conditions: dry daylight
            knownAnomalies: GNSS multipath, temporary OBD loss
            groundTruthAvailable: false
        """.trimIndent())
        assertEquals(DatasetMetadata("UA-KH-014", LocalDate.of(2026, 9, 2), "Kyiv, Ukraine", NavigationMode.VEHICLE, "Test Phone", "ELM327-A", "route-014", "dry daylight", listOf("GNSS multipath", "temporary OBD loss"), false), metadata)
    }

    @Test fun `rejects missing unknown invalid and duplicate fields`() {
        assertFailsWith<IllegalArgumentException> { DatasetMetadataParser.parse("sessionId: x") }
        assertFailsWith<IllegalArgumentException> { DatasetMetadataParser.parse(valid().replace("mode: VEHICLE", "mode: UNKNOWN")) }
        assertFailsWith<IllegalArgumentException> { DatasetMetadataParser.parse(valid().replace("sessionId: x", "unexpected: x\nsessionId: x")) }
        assertFailsWith<IllegalArgumentException> { DatasetMetadataParser.parse(valid().replace("sessionId: x", "sessionId: x\nsessionId: y")) }
    }

    private fun valid() = """
        sessionId: x
        date: 2026-09-02
        region: test
        mode: VEHICLE
        phoneModel: phone
        obdAdapter: none
        route: route
        conditions: test
        knownAnomalies: none
        groundTruthAvailable: true
    """.trimIndent()
}
