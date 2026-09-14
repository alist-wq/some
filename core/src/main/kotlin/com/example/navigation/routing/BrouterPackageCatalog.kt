package com.example.navigation.routing

/** Immutable, release-pinned description of one artifact required to verify a BRouter package. */
data class BrouterArtifactDescriptor(
    val name: String,
    val url: String,
    val sha256: String
) {
    init {
        require(name.matches(Regex("[A-Za-z0-9._-]+")))
        require(url.startsWith("https://"))
        require(sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

/**
 * An app-approved release, deliberately separate from MapLibre tile metadata.
 * URLs must name an immutable release version; mutable `latest` sources are not accepted.
 */
data class BrouterRegionalPackageDescriptor(
    val regionId: String,
    val packageVersion: String,
    val mapCompatibilityVersion: String,
    val brouterRevision: String,
    val profileSha256: String,
    val artifacts: List<BrouterArtifactDescriptor>
) {
    init {
        require(regionId.isNotBlank() && packageVersion.isNotBlank())
        require(mapCompatibilityVersion.isNotBlank() && brouterRevision.isNotBlank())
        require(profileSha256.matches(Regex("[0-9a-f]{64}")))
        require(artifacts.map { it.name }.distinct().size == artifacts.size)
        val byName = artifacts.associateBy { it.name }
        require(listOf("manifest.json", "manifest.json.sig", "renav-brouter-ed25519.pub", "lookups.dat", "renav-pedestrian.brf").all { it in byName })
        require(artifacts.any { it.name.matches(Regex("E\\d+_N\\d+\\.rd5")) })
        require(byName.getValue("renav-pedestrian.brf").sha256 == profileSha256)
        require(artifacts.none { it.url.contains("/releases/latest/") || it.url.contains("/releases/download/latest/") })
    }
}

data class BrouterRegionalPackageCatalog(val version: String, val packages: List<BrouterRegionalPackageDescriptor>) {
    init {
        require(version.isNotBlank())
        require(packages.map { it.regionId }.distinct().size == packages.size)
    }

    fun packageFor(regionId: String): BrouterRegionalPackageDescriptor? = packages.singleOrNull { it.regionId == regionId }
}

/** The first production fixture release, pinned by tag and checksum rather than a mutable release alias. */
object ProductionBrouterPackageCatalog {
    private const val RELEASE = "https://github.com/alist-wq/renav-brouter-fixtures/releases/download/brouter-ua-kharkiv-pedestrian-2026.09.10-1"
    const val REGION_ID = "ua-kharkiv-pedestrian"
    const val PACKAGE_VERSION = "2026.09.10-1"
    const val MAP_COMPATIBILITY_VERSION = "openfreemap-2026-09"
    const val BROUTER_REVISION = "d5c0a75d29b5a036525fd5e9466a6376ee54cc3e"

    private fun artifact(name: String, sha256: String) = BrouterArtifactDescriptor(name, "$RELEASE/$name", sha256)

    val catalog = BrouterRegionalPackageCatalog("2026-09-10-1", listOf(
        BrouterRegionalPackageDescriptor(
            regionId = REGION_ID,
            packageVersion = PACKAGE_VERSION,
            mapCompatibilityVersion = MAP_COMPATIBILITY_VERSION,
            brouterRevision = BROUTER_REVISION,
            profileSha256 = "ba22adb89a808223dda93b5b375bd26d90441fce703d380d85fce623c16c768b",
            artifacts = listOf(
                artifact("E35_N50.rd5", "69b8d72a649c1492beedb3d03569a0a7d90ca6c3b85e446d0da89276e016f127"),
                artifact("lookups.dat", "bf8fc3b2c052b7204e9c5a8d78e4d6ab6b125d3236a943f34583b838c477f419"),
                artifact("manifest.json", "d8355acde12d27cb0d7edb23f8f2b839561faa64b3f3a51e4a6bbc955b31b836"),
                artifact("manifest.json.sig", "c1dc0ce9e5dbab52ac87645afa9e6dd5159299fac3c8bae62509df90781b0710"),
                artifact("renav-brouter-ed25519.pub", "491f03a2b41b58dd22018649aba62dbb825405e96ef6b66b1eecc956d1156c17"),
                artifact("renav-pedestrian.brf", "ba22adb89a808223dda93b5b375bd26d90441fce703d380d85fce623c16c768b")
            )
        )
    ))
}

enum class BrouterPackageAvailability { NOT_INSTALLED, DOWNLOADING, INTERRUPTED, CORRUPT, STALE, INCOMPATIBLE, READY }

/** Persisted installation evidence. A later installer owns filesystem paths and atomic promotion. */
data class InstalledBrouterPackageEvidence(
    val regionId: String,
    val packageVersion: String,
    val mapCompatibilityVersion: String,
    val brouterRevision: String,
    val verified: Boolean,
    val active: Boolean
)

object BrouterPackageAvailabilityEvaluator {
    fun evaluate(
        expected: BrouterRegionalPackageDescriptor,
        installed: InstalledBrouterPackageEvidence?,
        downloading: Boolean = false,
        interrupted: Boolean = false
    ): BrouterPackageAvailability = when {
        downloading -> BrouterPackageAvailability.DOWNLOADING
        interrupted -> BrouterPackageAvailability.INTERRUPTED
        installed == null -> BrouterPackageAvailability.NOT_INSTALLED
        installed.regionId != expected.regionId || installed.packageVersion != expected.packageVersion -> BrouterPackageAvailability.STALE
        installed.mapCompatibilityVersion != expected.mapCompatibilityVersion || installed.brouterRevision != expected.brouterRevision -> BrouterPackageAvailability.INCOMPATIBLE
        !installed.verified || !installed.active -> BrouterPackageAvailability.CORRUPT
        else -> BrouterPackageAvailability.READY
    }
}
