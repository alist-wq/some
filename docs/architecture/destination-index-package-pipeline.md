# RENAV regional destination-index package pipeline

Status: approved for the first `ua-kharkiv-pedestrian` package on 2026-09-10.

## Boundary

This pipeline produces an offline address and named-place search index only. It is not a routing
graph, does not infer a route from map tiles, and does not add vehicle routing. A valid index may
enable local destination search while route planning remains explicitly unavailable when no
compatible routing package exists.

The first package is tied to the active regional map identity:

| Field | Value |
|---|---|
| Region | `ua-kharkiv-pedestrian` |
| Map compatibility version | `openfreemap-2026-09` |
| Source | immutable Geofabrik `ukraine-260901.osm.pbf` |
| Source SHA-256 | `85b3630bc722fbd84352044c2f262609c04d36dcb3c7dc7c4b09fcb29f0133cc` |
| Attribution | `© OpenStreetMap contributors, ODbL 1.0` |

The source URL, timestamp, and checksum remain locked in
`tools/brouter/locked-inputs/ua-kharkiv-pedestrian.env`; the build must reject a mutable
`latest` source URL and a source checksum mismatch.

The CI-only generator uses PyOsmium 3.7.0 (LGPL-3.0) to read the PBF; it is selected instead of a
custom PBF parser to keep the release build bounded and deterministic. It indexes valid OSM nodes
with either `addr:housenumber` plus `addr:street`, or `name` plus `place`; ways and relations are
deliberately excluded until their geometry policy is separately approved.

## Release artifacts

An off-device/CI build emits these immutable release assets:

```text
destination-index.jsonl
manifest.json
manifest.json.sig
renav-destination-index-ed25519.pub
```

`destination-index.jsonl` is UTF-8, one canonical JSON object per line, sorted by stable ID. Each
record contains `stableId`, `sourceType` (`ADDRESS` or `PLACE`), optional `category`, `latitude`,
`longitude`, `displayLabel`, and non-empty normalized `searchTokens`. The on-device model remains
`DestinationIndexRecord`; it must reject duplicate IDs, invalid coordinates, malformed records,
or token-less entries.

The canonical `manifest.json` contains schema version, region ID, package version, map
compatibility version, locked-source URL/date/SHA-256, generation bounds, record count, the
canonical record checksum used by `OfflineDestinationSearchIndex`, an artifact SHA-256 inventory,
and ODbL attribution. It is signed as SSHSIG using the dedicated
`renav-destination-index-manifest` namespace. The application pins the corresponding public-key
fingerprint and accepts no unsigned, mutable, or map-version-mismatched package.

## Installation and recovery

After explicit user confirmation, the app downloads only a versioned immutable release into
`filesDir/maps/destination-indexes/staging`, verifies the manifest signature and every artifact,
then atomically activates it at:

```text
filesDir/maps/destination-indexes/active/<region-id>/<package-version>
```

Interrupted staging data is discarded on recovery. The prior valid index remains active until the
new package has passed every check. Removing an active regional map also removes its associated
destination-index package; an active-map or map-version mismatch must produce
`DestinationSearchResult.IndexUnavailable`, never results from stale data.

## Release gates

1. Build twice from the locked input and compare canonical manifest and index checksums.
2. Verify signature, artifact inventory, ODbL attribution, bounds, duplicate-ID rejection, and
   deterministic exact/prefix/normalized search fixtures.
3. Verify mismatched region/map versions, corrupt files, interrupted recovery, removal, and
   network-disabled search after activation.
4. Validate the installed package through the Android/JVM runtime. Device validation is required
   before claiming an end-to-end destination-search journey.

Vehicle graph generation, vehicle routing, alternative routes, and route-entry UI wiring remain
separate work and require their own approved artifacts and validation.
