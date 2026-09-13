package com.example.navigation.maps

/** Immutable catalog of approved destination-search-only releases. */
data class DestinationIndexPackageCatalog(
    val version: String,
    val packages: List<DestinationIndexPackageDescriptor>
) {
    init {
        require(version.isNotBlank())
        require(packages.map { it.regionId }.distinct().size == packages.size)
    }
    fun packageFor(regionId: String): DestinationIndexPackageDescriptor? = packages.singleOrNull { it.regionId == regionId }
}

/** Current production Kharkiv index release, pinned by tag and published content hashes. */
object ProductionDestinationIndexPackageCatalog {
    private const val RELEASE = "https://downloads.renav.me/renav/destination-index/ua-kharkiv-pedestrian/2026.09.13-1"
    const val REGION_ID = "ua-kharkiv-pedestrian"
    const val PACKAGE_VERSION = "2026.09.13-1"
    const val MAP_COMPATIBILITY_VERSION = "openfreemap-2026-09"
    private const val SOURCE_URL = "https://download.geofabrik.de/europe/ukraine-260901.osm.pbf"
    private const val SOURCE_SHA256 = "85b3630bc722fbd84352044c2f262609c04d36dcb3c7dc7c4b09fcb29f0133cc"

    private fun artifact(name: String, sha256: String) = DestinationIndexArtifact(name, "$RELEASE/$name", sha256)
    val catalog = DestinationIndexPackageCatalog("2026-09-13-1", listOf(
        DestinationIndexPackageDescriptor(
            regionId = REGION_ID,
            packageVersion = PACKAGE_VERSION,
            mapCompatibilityVersion = MAP_COMPATIBILITY_VERSION,
            lockedSourceUrl = SOURCE_URL,
            lockedSourceSha256 = SOURCE_SHA256,
            attribution = "© OpenStreetMap contributors, ODbL 1.0",
            artifacts = listOf(
                artifact("destination-index.jsonl", "5d02485159d18074a38d86f79f6a454bbb4235b9fa6de6f2a95dd47e52e1af23"),
                artifact("manifest.json", "c0e7c5bc484ca70b424e448753c1b769908215ada4f7b30c259615ddd994004a"),
                artifact("manifest.json.sig", "028da9c6f0fa11ab52296c6a1fa689d0b330807694c2f42e42c36efd0a5984f2"),
                artifact("renav-destination-index-ed25519.pub", "16d55e4985a8d242ace47808ace11740e028eea7ba220c6dc416f95514fd0e06")
            )
        )
    ))
}
