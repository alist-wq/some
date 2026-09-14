package com.example.navigation.routing

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EmbeddedBrouterRoutingAdapterTest {
    @Test
    @EnabledIfSystemProperty(named = "renav.brouter.fixture.dir", matches = ".+")
    fun `routes the published signed Kharkiv fixture in process`() {
        val root = Path.of(requireNotNull(System.getProperty("renav.brouter.fixture.dir")))
        val verified = BrouterPackageVerifier(PUBLISHED_CORE_REVISION).verify(root, "openfreemap-2026-09")
        val packageInfo = when (verified) {
            is BrouterPackageVerificationResult.Ready -> verified.value
            is BrouterPackageVerificationResult.Unavailable -> error(verified.reason)
        }
        val request = BrouterRouteRequest(
            origin = GeoPoint(50.0035, 36.2208),
            destination = GeoPoint(50.0084, 36.2391),
            profileId = "renav-pedestrian",
            verifiedPackage = packageInfo
        )

        val first = assertIs<BrouterRouteResult.Ready>(EmbeddedBrouterRoutingAdapter().calculate(request)).route
        val second = assertIs<BrouterRouteResult.Ready>(EmbeddedBrouterRoutingAdapter().calculate(request)).route

        assertEquals(first.routeId, second.routeId)
        assertEquals(67, first.geometry.size)
        assertEquals("2026.09.10-1", first.packageVersion)
        assertEquals("renav-pedestrian-1", first.profileVersion)
        assertTrue(first.distanceMeters > 0)
    }

    @Test
    fun `returns deterministic package-attributed route from embedded core`() {
        val core = RecordingCore(BrouterCoreRouteResult.Found(listOf(GeoPoint(50.0, 36.0), GeoPoint(50.001, 36.002)), 250, 180))
        val adapter = EmbeddedBrouterRoutingAdapter(core)
        val request = request()

        val first = assertIs<BrouterRouteResult.Ready>(adapter.calculate(request)).route
        val second = assertIs<BrouterRouteResult.Ready>(adapter.calculate(request)).route

        assertEquals(first.routeId, second.routeId)
        assertEquals("ua-kharkiv-pedestrian", first.packageRegionId)
        assertEquals("fixture-1", first.packageVersion)
        assertEquals("renav-pedestrian-1", first.profileVersion)
        assertEquals("renav-pedestrian", first.profileId)
        assertEquals(2, first.geometry.size)
        assertEquals(2, core.calls)
    }

    @Test
    fun `rejects unsupported profile and uncovered endpoint before calling core`() {
        val core = RecordingCore(BrouterCoreRouteResult.NoRoute("not used"))
        val adapter = EmbeddedBrouterRoutingAdapter(core)
        assertIs<BrouterRouteResult.Unavailable>(adapter.calculate(request(profile = "other")))
        assertIs<BrouterRouteResult.Unavailable>(adapter.calculate(request(destination = GeoPoint(46.0, 30.0))))
        assertEquals(0, core.calls)
    }

    @Test
    fun `returns explicit no route without fallback`() {
        val core = RecordingCore(BrouterCoreRouteResult.NoRoute("No pedestrian path"))
        val result = EmbeddedBrouterRoutingAdapter(core).calculate(request())

        assertEquals("No pedestrian path", assertIs<BrouterRouteResult.NoRoute>(result).reason)
        assertTrue(core.calls == 1)
    }

    private fun request(
        destination: GeoPoint = GeoPoint(50.001, 36.002),
        profile: String = "renav-pedestrian"
    ): BrouterRouteRequest = BrouterRouteRequest(GeoPoint(50.0, 36.0), destination, profile, verifiedPackage())

    private fun verifiedPackage(): VerifiedBrouterPackage {
        val root = Files.createTempDirectory("brouter-adapter")
        val profile = root.resolve("renav-pedestrian.brf").also { Files.writeString(it, "profile") }
        val lookup = root.resolve("lookups.dat").also { Files.writeString(it, "lookup") }
        val segment = root.resolve("E35_N50.rd5").also { Files.writeString(it, "segment") }
        return VerifiedBrouterPackage("ua-kharkiv-pedestrian", "fixture-1", "map-1", "core-1", root, profile, "renav-pedestrian-1", lookup, listOf(segment))
    }

    private class RecordingCore(private val result: BrouterCoreRouteResult) : BrouterRoutingCore {
        var calls = 0
        override fun route(request: BrouterRouteRequest): BrouterCoreRouteResult {
            calls += 1
            return result
        }
    }

    private companion object {
        const val PUBLISHED_CORE_REVISION = "d5c0a75d29b5a036525fd5e9466a6376ee54cc3e"
    }
}
