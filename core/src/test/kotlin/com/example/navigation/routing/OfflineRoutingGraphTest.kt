package com.example.navigation.routing

import com.example.navigation.navigation.NavigationMode
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OfflineRoutingGraphTest {
    @Test fun loadsOneRegionalGraphAndCalculatesVehicleRouteRespectingRestriction() {
        val directory = fixture(); val graph = OfflineRoutingGraphLoader().load(directory)
        val route = VehicleRouteCalculator(graph).calculate(RouteRequest(GeoPoint(0.0, 0.0), GeoPoint(0.01, 0.01), NavigationMode.VEHICLE))
        assertEquals("fixture-region", route.regionId); assertEquals(listOf(1, 2, 4, 3), route.nodeIds); assertEquals(listOf(10, 12, 13), route.edgeIds); assertEquals(3.0, route.totalDistanceMeters)
        assertEquals(listOf(10L, 12L, 13L), route.segments.map { it.roadPathId })
        assertEquals(listOf(1.0, 1.0, 1.0), route.segments.map { it.lengthMeters })
        assertEquals(RouteTurnInformation.START, route.segments.first().turnInformation)
        assertEquals(RouteAccessType.VEHICLE, route.segments.last().accessType)
    }
    @Test fun rejectsChangedGraphFile() {
        val directory = fixture(); Files.write(directory.resolve("nodes.bin"), byteArrayOf(0))
        assertFailsWith<RoutingGraphLoadException> { OfflineRoutingGraphLoader().load(directory) }
    }
    @Test fun rejectsNonVehicleCalculation() {
        val graph = OfflineRoutingGraphLoader().load(fixture())
        assertFailsWith<IllegalArgumentException> { VehicleRouteCalculator(graph).calculate(RouteRequest(GeoPoint(0.0, 0.0), GeoPoint(0.01, 0.01), NavigationMode.PEDESTRIAN)) }
    }
    private fun fixture() = Files.createTempDirectory("routing-fixture").also { dir ->
        Files.writeString(dir.resolve("manifest.json"), "{\"schemaVersion\":1,\"regionId\":\"fixture-region\",\"routingGraphVersion\":\"v1\",\"byteOrder\":\"BIG_ENDIAN\",\"nodeCount\":4,\"edgeCount\":4}")
        write(dir, "nodes.bin", intArrayOf(4), doubles = doubleArrayOf()) { b -> b.putInt(4).putInt(1).putDouble(0.0).putDouble(0.0).putInt(2).putDouble(0.0).putDouble(0.01).putInt(3).putDouble(0.01).putDouble(0.01).putInt(4).putDouble(0.01).putDouble(0.0) }
        write(dir, "edges.bin", intArrayOf(4)) { b -> b.putInt(4).putInt(10).putInt(1).putInt(2).putDouble(1.0).put(1).putInt(11).putInt(2).putInt(3).putDouble(1.0).put(1).putInt(12).putInt(2).putInt(4).putDouble(1.0).put(1).putInt(13).putInt(4).putInt(3).putDouble(1.0).put(1) }
        write(dir, "adjacency.bin", intArrayOf(4, 1, 10, 2, 11, 2, 12, 4, 13)) { b -> intArrayOf(4, 1, 10, 2, 11, 2, 12, 4, 13).forEach(b::putInt) }
        write(dir, "turn_restrictions.bin", intArrayOf(1, 10, 11)) { b -> b.putInt(1).putInt(10).putInt(11) }
        write(dir, "spatial_index.bin", intArrayOf(4, 10, 11, 12, 13)) { b -> intArrayOf(4, 10, 11, 12, 13).forEach(b::putInt) }
        write(dir, "geometry.bin", intArrayOf(0)) { b -> b.putInt(0) }
        val files = listOf("manifest.json", "nodes.bin", "edges.bin", "geometry.bin", "adjacency.bin", "turn_restrictions.bin", "spatial_index.bin")
        Files.writeString(dir.resolve("checksums.sha256"), files.joinToString("\n") { name -> "${sha(dir.resolve(name))} $name" })
    }
    private fun write(dir: java.nio.file.Path, name: String, ignored: IntArray, doubles: DoubleArray = doubleArrayOf(), body: (ByteBuffer) -> Unit) { val buffer = ByteBuffer.allocate(512); body(buffer); Files.write(dir.resolve(name), buffer.array().copyOf(buffer.position())) }
    private fun sha(path: java.nio.file.Path) = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}
