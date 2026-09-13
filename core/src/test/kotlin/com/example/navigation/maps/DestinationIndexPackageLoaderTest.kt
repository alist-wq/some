package com.example.navigation.maps

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DestinationIndexPackageLoaderTest {
    @Test fun loadsVerifiedJsonlWithUkrainianTokens() {
        val root = Files.createTempDirectory("destination-index-loader")
        val index = root.resolve("destination-index.jsonl")
        Files.writeString(index, "{\"category\":\"city\",\"displayLabel\":\"Харків\",\"latitude\":50.0,\"longitude\":36.2,\"searchTokens\":[\"харків\",\"місто\"],\"sourceType\":\"PLACE\",\"stableId\":\"node/1\"}\n")
        val result = assertIs<DestinationIndexLoadResult.Ready>(DestinationIndexPackageLoader.load(VerifiedDestinationIndexPackage("ua-kharkiv-pedestrian", "fixture-1", "openfreemap-2026-09", root, index)))
        val search = assertIs<DestinationSearchResult.Results>(result.index.search("хар"))
        assertEquals("Харків", search.matches.single().displayLabel)
    }
}
