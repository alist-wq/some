# Offline routing architecture using GraphHopper preparation

Evaluated: 2026-09-02

## Decision

**Recommendation: use a custom extracted graph.**

Run a pinned GraphHopper release in an off-device graph-build pipeline, then export a compact,
versioned, read-only graph format owned by this repository. The Android application must load and
route over that exported format. It must not embed the current GraphHopper runtime, import OSM data,
or load GraphHopper's internal graph directory directly.

This is a firm choice among the Task 035 options. Direct embedding is rejected because GraphHopper
states that offline Android routing is no longer officially supported and that its Android demo only
existed through GraphHopper 1.0. The maintainers also identify virtual-memory and large-graph loading
as unresolved mobile concerns. [GraphHopper mobile documentation](https://github.com/graphhopper/graphhopper/blob/master/docs/index.md),
[offline-routing status](https://github.com/graphhopper/graphhopper/issues/1940)

Adapting GraphHopper core is rejected because it would create a long-lived fork tied to internal
storage formats and Java/runtime behavior. An online service is rejected because this application's
dead-reckoning and route-constrained navigation must work offline.

## Architecture boundary

### Off-device preparation

A reproducible desktop/CI tool will:

1. Pin the GraphHopper version, OSM extract checksum/date, vehicle profile, and exporter schema.
2. Import a bounded regional `.osm.pbf` with a vehicle profile that enables motor-vehicle turn
   restrictions.
3. Read GraphHopper's base graph, edge attributes, geometry, location index inputs, and turn costs.
4. Export the app-owned files described below.
5. Generate checksums and the map metadata required by Task 034.
6. Validate connectivity, restriction references, coordinate bounds, and deterministic output.

OSM import and GraphHopper preparation must never run on the phone. Contraction Hierarchies and
Landmarks are not exported in the first format: they increase format complexity and couple the app
to GraphHopper internals. GraphHopper documents CH, LM, and flexible A*/Dijkstra as distinct routing
modes; the first app implementation should use bounded A* over the extracted base graph.
[GraphHopper routing modes](https://github.com/graphhopper/graphhopper/blob/master/docs/core/routing.md)

### Android runtime

The routing implementation belongs behind interfaces in `core/routing`. Android storage code may
open graph files from app-private regional storage, but routing algorithms and graph models must not
depend on Android or MapLibre classes.

One region is mounted read-only at a time for the MVP. Cross-region routing and graph merging are
explicitly unsupported until a later task defines border-node stitching.

The exported region is an atomic directory:

```text
manifest.json
nodes.bin
edges.bin
geometry.bin
adjacency.bin
turn_restrictions.bin
spatial_index.bin
checksums.sha256
```

The manifest must include schema version, region ID and bounds, source date/checksum, GraphHopper
build version, profile version, routing-graph version, file sizes, counts, byte order, and checksums.
Installation writes to a staging directory, validates every file, then atomically activates the
region. Unknown schema versions or failed checksums are rejected; there is no best-effort loading.

## Android compatibility

Current GraphHopper is Java software, but Java language availability alone is not enough to make its
runtime an Android-supported library. Its own repository says current offline Android use is not
officially supported. Direct use would require ongoing verification of Android-compatible
dependencies, file locking, memory mapping, reflection, Java APIs, shrinking rules, and every graph
format migration.

The custom format limits Android compatibility work to primitive binary reads, bounded memory maps,
and repository-owned Kotlin. It also prevents an upstream GraphHopper update from silently making
downloaded graphs unreadable.

## Memory requirements

GraphHopper's current example configuration recommends RAM storage on well-equipped servers and
offers `FOREIGN_MMAP` selectively when heap is constrained. Its base graph also distinguishes
file-backed, memory-mapped geometry from on-heap data. These are useful design signals, but they are
not an Android memory guarantee. [GraphHopper storage configuration](https://github.com/graphhopper/graphhopper/blob/master/config-example.yml),
[BaseGraph storage](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/storage/BaseGraph.java)

Android requirements for the extracted graph:

- Memory-map immutable graph files in bounded windows; do not copy an entire region to heap.
- Keep decoded node/edge records ephemeral or in size-bounded caches.
- Target at most 64 MiB incremental Java/native resident memory for a mounted regional graph on the
  minimum supported device; fail loading if a device-specific safety check cannot reserve the
  required working set.
- Bound route search by visited nodes, elapsed time, and cancellability. Resource exhaustion returns
  an explicit routing failure.
- Measure cold load, warm load, peak Java heap, native/PSS growth, and route-search working memory on
  physical low-memory devices before accepting a region class.

The 64 MiB value is an application engineering budget, not a GraphHopper claim. It must be revised
from device measurements rather than treated as proof of compatibility.

## Graph preparation and regional graph sizes

Graph size cannot be inferred safely from geographic area alone. It varies with road density,
retained geometry, names/attributes, turn restrictions, spatial-index resolution, and any routing
acceleration data. Therefore no fixed city/region size is promised.

The build pipeline must report, for every candidate region:

- source PBF bytes;
- total exported bytes and bytes per file;
- nodes, directed edges, geometry points, restrictions, and index cells;
- bytes per node and edge;
- peak preparation memory and elapsed time;
- compressed download size and installed size;
- measured Android load memory and representative route latency.

Initial admission gates are: one independently downloadable bounded region, installed graph no
larger than 512 MiB, and the 64 MiB runtime-memory budget above. These are conservative project
limits, not estimates of what GraphHopper itself requires. Regions exceeding either gate must be
split or rejected; automatic graph merging is out of scope.

## Licensing

GraphHopper is Apache License 2.0. The off-device exporter must retain its required license and
NOTICE material, and modifications to copied GraphHopper code must be identified. No GraphHopper
code needs to ship in the APK unless a later review changes this architecture.
[GraphHopper repository and license](https://github.com/graphhopper/graphhopper)

OpenStreetMap data is licensed under ODbL. Distribution of extracted routing databases requires a
product/legal review for ODbL attribution, database notice, and share-alike/source-offer obligations;
the app must show OpenStreetMap attribution and each region manifest must retain source provenance.
[OpenStreetMap copyright and license](https://www.openstreetmap.org/copyright)

## Route calculation API

Task 037 should expose an internal, GraphHopper-independent API similar to:

```kotlin
interface RoutingEngine {
    fun calculate(request: RouteRequest): RouteResult
}

data class RouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val mode: NavigationMode,
    val initialHeadingDegrees: Double? = null
)
```

The loader and router return internal `Route`, segment, geometry, maneuver, and explicit error
models. No `GHRequest`, `GHResponse`, GraphHopper graph object, or Android object crosses this
boundary. For comparison, GraphHopper's Java API represents point-to-point requests with
`GHRequest` and routes after snapping through a location index; these semantics can inform adapter
tests without becoming public interfaces. [GHRequest](https://github.com/graphhopper/graphhopper/blob/master/web-api/src/main/java/com/graphhopper/GHRequest.java),
[Router](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/routing/Router.java)

## Spatial index availability

The exported graph must include a read-only spatial index from latitude/longitude cells to candidate
edge IDs. Querying returns multiple nearby directed-edge candidates plus projected position,
distance, and edge heading; it must not snap blindly to one nearest road.

GraphHopper's `LocationIndex` maps real-world coordinates to graph edges with an edge filter, so its
candidate/filter semantics should guide exporter validation. The on-device index format remains
repository-owned. [GraphHopper LocationIndex](https://github.com/graphhopper/graphhopper/blob/master/core/src/main/java/com/graphhopper/storage/index/LocationIndex.java)

## Turn restrictions

Vehicle routing must export OSM motorcar/motor-vehicle restrictions and evaluate them as transitions
between directed edges. The route-search state therefore includes the incoming edge, not only the
current node. Unsupported conditional restrictions must be identified in the manifest and surfaced
as a graph-quality limitation; they must not be silently treated as supported.

GraphHopper documents that turn restrictions require edge-based traversal and additional
preparation for CH. The custom format preserves the base transition restrictions and initially uses
edge-state A*, avoiding CH-specific structures. [GraphHopper turn restrictions](https://github.com/graphhopper/graphhopper/blob/master/docs/core/turn-restrictions.md)

## Map-matching reuse potential

The same node/edge IDs, geometry, adjacency, access flags, turn restrictions, and spatial index
should be shared by routing and route-constrained matching. This avoids duplicate road graphs and
lets matching score several candidates using legal network transitions.

Do not embed GraphHopper's map-matching module on Android in the first implementation. Its algorithm
is useful as a reference—GraphHopper describes Viterbi matching using candidate and routed
transition distances—but this project needs deterministic replay, GNSS trust weighting, OBD/IMU
distance, planned-route preference, and multiple explicit hypotheses. Those requirements belong in
the repository's matching module over the extracted graph.
[GraphHopper map matching](https://github.com/graphhopper/graphhopper/blob/master/map-matching/README.md)

## Risks and required proof before Task 036 is complete

- Build a tiny synthetic fixture and one real bounded region with the pinned exporter.
- Prove deterministic export and reject altered/truncated files.
- Verify directed connectivity and every exported restriction against GraphHopper reference routes.
- Benchmark graph load and index queries on a physical minimum-memory Android device.
- Confirm ODbL distribution obligations with the chosen map-download workflow.
- Keep the exporter schema independent of GraphHopper's internal file layout and version it from the
  first release.

Task 036 should implement only this loader boundary and one regional fixture. It should not add route
calculation, graph merging, or map matching.
