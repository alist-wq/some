package com.example.navigation.routing

import com.example.navigation.navigation.NavigationMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.math.*

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init { require(latitude in -90.0..90.0 && longitude in -180.0..180.0) }
}

data class RoutingGraphManifest(
    val schemaVersion: Int,
    val regionId: String,
    val routingGraphVersion: String,
    val byteOrder: ByteOrder,
    val nodeCount: Int,
    val edgeCount: Int
)

data class RoutingNode(val id: Int, val point: GeoPoint)
data class RoutingEdge(val id: Int, val fromNodeId: Int, val toNodeId: Int, val distanceMeters: Double, val accessMask: Int) {
    init { require(accessMask in 1..3) { "Routing edge must allow at least one supported profile" } }
    val vehicleAllowed: Boolean get() = accessMask and VEHICLE_ACCESS != 0
    val pedestrianAllowed: Boolean get() = accessMask and PEDESTRIAN_ACCESS != 0
    val accessType: RouteAccessType get() = when (accessMask) {
        VEHICLE_ACCESS -> RouteAccessType.VEHICLE
        PEDESTRIAN_ACCESS -> RouteAccessType.PEDESTRIAN
        else -> RouteAccessType.BOTH
    }
    companion object { const val VEHICLE_ACCESS = 1; const val PEDESTRIAN_ACCESS = 2 }
}
data class TurnRestriction(
    val incomingEdgeId: Int,
    val outgoingEdgeId: Int,
    val restrictedModes: Set<NavigationMode> = setOf(NavigationMode.VEHICLE)
) {
    fun appliesTo(mode: NavigationMode): Boolean = mode in restrictedModes
}

class OfflineRoutingGraph internal constructor(
    val manifest: RoutingGraphManifest,
    internal val nodes: Map<Int, RoutingNode>,
    internal val edges: Map<Int, RoutingEdge>,
    internal val adjacency: Map<Int, List<Int>>,
    internal val turnRestrictions: Set<TurnRestriction>,
    internal val spatialEdgeIds: List<Int>
) {
    fun nearestNode(point: GeoPoint): Int {
        val candidates = spatialEdgeIds.flatMap { edgeId ->
            edges.getValue(edgeId).let { listOf(it.fromNodeId, it.toNodeId) }
        }.distinct()
        require(candidates.isNotEmpty()) { "Routing graph has no spatial candidates" }
        return candidates.minBy { nodeId -> distanceMeters(nodes.getValue(nodeId).point, point) }
    }

    internal fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val latitudeDelta = Math.toRadians(b.latitude - a.latitude)
        val longitudeDelta = Math.toRadians(b.longitude - a.longitude)
        val latitudeA = Math.toRadians(a.latitude)
        val latitudeB = Math.toRadians(b.latitude)
        val h = sin(latitudeDelta / 2).pow(2) + cos(latitudeA) * cos(latitudeB) * sin(longitudeDelta / 2).pow(2)
        return 6_371_000.0 * 2.0 * atan2(sqrt(h), sqrt(1.0 - h))
    }
}

class RoutingGraphLoadException(message: String) : IllegalArgumentException(message)

/** Strict loader for the versioned, app-owned regional graph directory approved in Task 035. */
class OfflineRoutingGraphLoader {
    fun load(regionDirectory: Path): OfflineRoutingGraph {
        require(Files.isDirectory(regionDirectory)) { "Routing graph directory does not exist: $regionDirectory" }
        val required = REQUIRED_FILES.associateWith { regionDirectory.resolve(it) }
        required.forEach { (name, path) -> if (!Files.isRegularFile(path)) throw RoutingGraphLoadException("Missing graph file: $name") }
        verifyChecksums(required, regionDirectory.resolve(CHECKSUMS_FILE))
        val manifest = parseManifest(Files.readString(regionDirectory.resolve(MANIFEST_FILE)))
        if (manifest.schemaVersion != SCHEMA_VERSION) throw RoutingGraphLoadException("Unsupported routing graph schema: ${manifest.schemaVersion}")
        if (manifest.byteOrder != ByteOrder.BIG_ENDIAN) throw RoutingGraphLoadException("Unsupported byte order: ${manifest.byteOrder}")

        val nodes = readNodes(required.getValue(NODES_FILE), manifest.byteOrder)
        val edges = readEdges(required.getValue(EDGES_FILE), manifest.byteOrder)
        val adjacency = readAdjacency(required.getValue(ADJACENCY_FILE), manifest.byteOrder)
        val restrictions = readRestrictions(required.getValue(RESTRICTIONS_FILE), manifest.byteOrder)
        val spatialEdges = readIntList(required.getValue(SPATIAL_INDEX_FILE), manifest.byteOrder)
        readIntList(required.getValue(GEOMETRY_FILE), manifest.byteOrder) // Validate the current empty/optional geometry payload.

        if (nodes.size != manifest.nodeCount || edges.size != manifest.edgeCount) throw RoutingGraphLoadException("Manifest counts do not match graph files")
        validateGraph(nodes, edges, adjacency, restrictions, spatialEdges)
        return OfflineRoutingGraph(manifest, nodes, edges, adjacency, restrictions, spatialEdges)
    }

    private fun parseManifest(value: String): RoutingGraphManifest {
        fun string(key: String) = Regex("\\\"$key\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(value)?.groupValues?.get(1)
            ?: throw RoutingGraphLoadException("Manifest is missing $key")
        fun integer(key: String) = Regex("\\\"$key\\\"\\s*:\\s*(\\d+)").find(value)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw RoutingGraphLoadException("Manifest has invalid $key")
        val byteOrder = when (string("byteOrder")) { "BIG_ENDIAN" -> ByteOrder.BIG_ENDIAN; else -> throw RoutingGraphLoadException("Manifest byteOrder must be BIG_ENDIAN") }
        return RoutingGraphManifest(integer("schemaVersion"), string("regionId"), string("routingGraphVersion"), byteOrder, integer("nodeCount"), integer("edgeCount"))
    }

    private fun verifyChecksums(files: Map<String, Path>, checksumFile: Path) {
        val expected = Files.readAllLines(checksumFile).associate { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) throw RoutingGraphLoadException("Invalid checksum record")
            parts[1] to parts[0]
        }
        files.filterKeys { it != CHECKSUMS_FILE }.forEach { (name, path) ->
            val actual = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
            if (expected[name] != actual) throw RoutingGraphLoadException("Checksum mismatch for $name")
        }
    }

    private fun readNodes(path: Path, order: ByteOrder): Map<Int, RoutingNode> = buffer(path, order).let { input ->
        val count = readCount(input, path); buildMap {
            repeat(count) { val id = input.int; if (put(id, RoutingNode(id, GeoPoint(input.double, input.double))) != null) throw RoutingGraphLoadException("Duplicate node $id") }
        }.also { ensureConsumed(input, path) }
    }
    private fun readEdges(path: Path, order: ByteOrder): Map<Int, RoutingEdge> = buffer(path, order).let { input ->
        val count = readCount(input, path); buildMap {
            repeat(count) { val id = input.int; val edge = try { RoutingEdge(id, input.int, input.int, input.double, input.get().toInt()) } catch (_: IllegalArgumentException) { throw RoutingGraphLoadException("Invalid access profile for edge $id") }; if (edge.distanceMeters < 0 || put(id, edge) != null) throw RoutingGraphLoadException("Invalid edge $id") }
        }.also { ensureConsumed(input, path) }
    }
    private fun readAdjacency(path: Path, order: ByteOrder): Map<Int, List<Int>> = buffer(path, order).let { input ->
        val count = readCount(input, path); val result = mutableMapOf<Int, MutableList<Int>>()
        repeat(count) { val node = input.int; val edge = input.int; result.getOrPut(node) { mutableListOf() }.add(edge) }
        result.mapValues { it.value.toList() }.also { ensureConsumed(input, path) }
    }
    private fun readRestrictions(path: Path, order: ByteOrder): Set<TurnRestriction> = buffer(path, order).let { input ->
        val count = readCount(input, path); buildSet { repeat(count) { add(TurnRestriction(input.int, input.int)) } }.also { ensureConsumed(input, path) }
    }
    private fun readIntList(path: Path, order: ByteOrder): List<Int> = buffer(path, order).let { input ->
        val count = readCount(input, path); List(count) { input.int }.also { ensureConsumed(input, path) }
    }
    private fun buffer(path: Path, order: ByteOrder): ByteBuffer = ByteBuffer.wrap(Files.readAllBytes(path)).order(order)
    private fun readCount(input: ByteBuffer, path: Path): Int = try { input.int.also { if (it < 0) throw RoutingGraphLoadException("Negative record count in $path") } } catch (_: java.nio.BufferUnderflowException) { throw RoutingGraphLoadException("Truncated graph file: $path") }
    private fun ensureConsumed(input: ByteBuffer, path: Path) { if (input.hasRemaining()) throw RoutingGraphLoadException("Unexpected trailing data in $path") }
    private fun validateGraph(nodes: Map<Int, RoutingNode>, edges: Map<Int, RoutingEdge>, adjacency: Map<Int, List<Int>>, restrictions: Set<TurnRestriction>, spatialEdges: List<Int>) {
        edges.values.forEach { edge ->
            if (edge.fromNodeId !in nodes || edge.toNodeId !in nodes || adjacency[edge.fromNodeId]?.contains(edge.id) != true) throw RoutingGraphLoadException("Invalid adjacency for edge ${edge.id}")
        }
        adjacency.values.flatten().forEach { if (it !in edges) throw RoutingGraphLoadException("Adjacency references missing edge $it") }
        restrictions.forEach { if (it.incomingEdgeId !in edges || it.outgoingEdgeId !in edges) throw RoutingGraphLoadException("Turn restriction references missing edge") }
        if (spatialEdges.isEmpty() || spatialEdges.any { it !in edges }) throw RoutingGraphLoadException("Invalid spatial index")
    }

    private companion object {
        const val SCHEMA_VERSION = 1
        const val MANIFEST_FILE = "manifest.json"; const val NODES_FILE = "nodes.bin"; const val EDGES_FILE = "edges.bin"; const val GEOMETRY_FILE = "geometry.bin"; const val ADJACENCY_FILE = "adjacency.bin"; const val RESTRICTIONS_FILE = "turn_restrictions.bin"; const val SPATIAL_INDEX_FILE = "spatial_index.bin"; const val CHECKSUMS_FILE = "checksums.sha256"
        val REQUIRED_FILES = setOf(MANIFEST_FILE, NODES_FILE, EDGES_FILE, GEOMETRY_FILE, ADJACENCY_FILE, RESTRICTIONS_FILE, SPATIAL_INDEX_FILE, CHECKSUMS_FILE)
    }
}

data class RouteRequest(val origin: GeoPoint, val destination: GeoPoint, val mode: NavigationMode)
data class Route(
    val regionId: String,
    val nodeIds: List<Int>,
    val edgeIds: List<Int>,
    val totalDistanceMeters: Double,
    val segments: List<RouteSegment> = emptyList()
) {
    init {
        require(nodeIds.size == edgeIds.size + 1) { "Route nodes and edges must be contiguous" }
        require(segments.isEmpty() || segments.size == edgeIds.size) { "Route segments must match route edges" }
    }
}
class RouteCalculationException(message: String) : IllegalStateException(message)

/** Bounded, deterministic edge-state A* over a loaded vehicle graph. */
class VehicleRouteCalculator(private val graph: OfflineRoutingGraph, private val maximumVisitedStates: Int = 100_000) {
    init { require(maximumVisitedStates > 0) }
    fun calculate(request: RouteRequest): Route {
        require(request.mode == NavigationMode.VEHICLE) { "Only vehicle routing is supported" }
        val start = graph.nearestNode(request.origin); val goal = graph.nearestNode(request.destination)
        data class State(val nodeId: Int, val incomingEdgeId: Int?)
        data class QueueEntry(val state: State, val cost: Double, val priority: Double)
        val queue = java.util.PriorityQueue<QueueEntry>(compareBy { it.priority })
        val startState = State(start, null); val cost = mutableMapOf(startState to 0.0); val previous = mutableMapOf<State, Pair<State, Int>>()
        queue += QueueEntry(startState, 0.0, 0.0); var visited = 0; var terminal: State? = null
        while (queue.isNotEmpty()) {
            val current = queue.remove(); if (current.cost != cost[current.state]) continue
            if (++visited > maximumVisitedStates) throw RouteCalculationException("Vehicle route search exceeded state limit")
            if (current.state.nodeId == goal) { terminal = current.state; break }
            graph.adjacency[current.state.nodeId].orEmpty().forEach { edgeId ->
                val edge = graph.edges.getValue(edgeId); if (!edge.vehicleAllowed || graph.turnRestrictions.any { it.incomingEdgeId == current.state.incomingEdgeId && it.outgoingEdgeId == edgeId && it.appliesTo(NavigationMode.VEHICLE) }) return@forEach
                val next = State(edge.toNodeId, edgeId); val nextCost = current.cost + edge.distanceMeters
                if (nextCost < (cost[next] ?: Double.POSITIVE_INFINITY)) { cost[next] = nextCost; previous[next] = current.state to edgeId; queue += QueueEntry(next, nextCost, nextCost + graph.distanceMeters(graph.nodes.getValue(next.nodeId).point, graph.nodes.getValue(goal).point)) }
            }
        }
        val end = terminal ?: throw RouteCalculationException("No vehicle route exists in region ${graph.manifest.regionId}")
        val reversedEdges = mutableListOf<Int>(); var state = end
        while (state != startState) { val prior = previous[state] ?: throw RouteCalculationException("Route reconstruction failed"); reversedEdges += prior.second; state = prior.first }
        val edgeIds = reversedEdges.asReversed(); val nodes = buildList { add(start); edgeIds.forEach { add(graph.edges.getValue(it).toNodeId) } }
        val segments = edgeIds.mapIndexed { index, edgeId ->
            val edge = graph.edges.getValue(edgeId)
            val startPoint = graph.nodes.getValue(edge.fromNodeId).point
            val endPoint = graph.nodes.getValue(edge.toNodeId).point
            RouteSegment(
                geometry = listOf(startPoint, endPoint),
                lengthMeters = edge.distanceMeters,
                headingDegrees = headingDegrees(startPoint, endPoint),
                roadPathId = edge.id.toLong(),
                turnInformation = if (index == 0) RouteTurnInformation.START else null,
                accessType = edge.accessType
            )
        }
        return Route(graph.manifest.regionId, nodes, edgeIds, cost.getValue(end), segments)
    }

    private fun headingDegrees(start: GeoPoint, end: GeoPoint): Double {
        val latitudeStart = Math.toRadians(start.latitude)
        val latitudeEnd = Math.toRadians(end.latitude)
        val longitudeDelta = Math.toRadians(end.longitude - start.longitude)
        return (Math.toDegrees(atan2(sin(longitudeDelta) * cos(latitudeEnd), cos(latitudeStart) * sin(latitudeEnd) - sin(latitudeStart) * cos(latitudeEnd) * cos(longitudeDelta))) + 360.0) % 360.0
    }
}

/** Pedestrian-only profile; vehicle-only access and turn restrictions are never applied here. */
class PedestrianRouteCalculator(private val graph: OfflineRoutingGraph, private val maximumVisitedStates: Int = 100_000) {
    init { require(maximumVisitedStates > 0) }

    fun calculate(request: RouteRequest): Route {
        require(request.mode == NavigationMode.PEDESTRIAN) { "Only pedestrian routing is supported" }
        data class State(val nodeId: Int, val incomingEdgeId: Int?)
        data class QueueEntry(val state: State, val cost: Double)
        val start = graph.nearestNode(request.origin); val goal = graph.nearestNode(request.destination)
        val startState = State(start, null); val cost = mutableMapOf(startState to 0.0); val previous = mutableMapOf<State, Pair<State, Int>>()
        val queue = java.util.PriorityQueue<QueueEntry>(compareBy { it.cost }); queue += QueueEntry(startState, 0.0)
        var visited = 0; var terminal: State? = null
        while (queue.isNotEmpty()) {
            val current = queue.remove(); if (current.cost != cost[current.state]) continue
            if (++visited > maximumVisitedStates) throw RouteCalculationException("Pedestrian route search exceeded state limit")
            if (current.state.nodeId == goal) { terminal = current.state; break }
            graph.adjacency[current.state.nodeId].orEmpty().forEach { edgeId ->
                val edge = graph.edges.getValue(edgeId)
                if (!edge.pedestrianAllowed || graph.turnRestrictions.any { it.incomingEdgeId == current.state.incomingEdgeId && it.outgoingEdgeId == edgeId && it.appliesTo(NavigationMode.PEDESTRIAN) }) return@forEach
                val next = State(edge.toNodeId, edgeId); val nextCost = current.cost + edge.distanceMeters
                if (nextCost < (cost[next] ?: Double.POSITIVE_INFINITY)) { cost[next] = nextCost; previous[next] = current.state to edgeId; queue += QueueEntry(next, nextCost) }
            }
        }
        val end = terminal ?: throw RouteCalculationException("No pedestrian route exists in region ${graph.manifest.regionId}")
        val reversedEdges = mutableListOf<Int>(); var state = end
        while (state != startState) { val prior = previous[state] ?: throw RouteCalculationException("Route reconstruction failed"); reversedEdges += prior.second; state = prior.first }
        val edgeIds = reversedEdges.asReversed(); val nodes = buildList { add(start); edgeIds.forEach { add(graph.edges.getValue(it).toNodeId) } }
        val segments = edgeIds.mapIndexed { index, edgeId ->
            val edge = graph.edges.getValue(edgeId)
            RouteSegment(listOf(graph.nodes.getValue(edge.fromNodeId).point, graph.nodes.getValue(edge.toNodeId).point), edge.distanceMeters, null, edge.id.toLong(), if (index == 0) RouteTurnInformation.START else null, edge.accessType)
        }
        return Route(graph.manifest.regionId, nodes, edgeIds, cost.getValue(end), segments)
    }
}
