# RENAV BRouter package pipeline

Status: approved 2026-09-08 for the pedestrian MVP.

## Decisions

- RENAV embeds BRouter's non-UI Java core at the pinned upstream revision `d5c0a75d29b5a036525fd5e9466a6376ee54cc3e`. The BRouter Android app/service is not used.
- The source data is the immutable dated Geofabrik `ukraine-260901.osm.pbf` extract. Its URL, source timestamp, and SHA-256 are committed in `tools/brouter/locked-inputs/ua-kharkiv-pedestrian.env`; a mutable `latest` URL is rejected before map creation. Generated packages retain OpenStreetMap attribution and applicable ODbL notices.
- The first artifact is `ua-kharkiv-pedestrian`. Its geographic coverage includes Kharkiv and the BRouter 5-by-5-degree segment `E35_N50.rd5`.
- The package contains generated `.rd5` data, the matching BRouter lookup data required by that build, and a RENAV-controlled pedestrian profile. It is not bundled in the APK.
- A release pipeline, not the device, builds the artifact. The pipeline downloads the dated PBF input, records its SHA-256, runs the pinned BRouter map creator, creates SHA-256 hashes for every file, and emits a canonical JSON manifest.
- TASK 154 invokes `OsmFastCutter`, `PosUnifier`, and `WayLinker` directly in an empty workspace. It deliberately does not invoke BRouter's update, SSH, or deployment scripts. Its only retained outputs are the matching lookup data, RENAV pedestrian profile, `E35_N50.rd5`, a geometry-only regression result, and canonical unsigned build evidence. The generated `.rd5` creation timestamp is normalized to the locked source epoch so two clean builds can be compared byte-for-byte.
- The release pipeline signs the canonical manifest with Ed25519. The private key is stored only as a protected CI secret; the corresponding public key is compiled into RENAV. Neither key material nor generated regional data belongs in source control.
- Artifacts and manifest are published together as an immutable GitHub Release. A later mirror is acceptable only when it serves byte-identical artifacts and manifest.

## Manifest contract

`manifest.json` contains: schema version, region ID, package version, bounds, OSM input URL/date/SHA-256, BRouter revision, lookup-data SHA-256, pedestrian-profile version/SHA-256, sorted segment file names/SHA-256/byte sizes, MapLibre regional-package compatibility version, creation timestamp, attribution, and Ed25519 signature.

RENAV accepts a package only when the manifest signature verifies, all declared hashes and sizes match, the BRouter revision and map compatibility match, and requested origin/destination coverage is fully represented by declared segments. Map tile availability is never package evidence.

## Install and recovery

After explicit user confirmation, RENAV downloads to a staging directory under `filesDir/routing/brouter/staging`, verifies the manifest and every file, then atomically promotes it to `filesDir/routing/brouter/active/<region>/<package-version>`. Failed or interrupted staging directories are discarded on recovery. The previously active verified package remains active until promotion succeeds.

## Release gates

1. Rebuild the same package twice and compare sorted manifest hashes and route regression outputs.
2. Run deterministic pedestrian routes covering the Kharkiv fixture, including unavailable coverage and corrupt-artifact tests.
3. Verify offline routing on an Android test device with network disabled after installation.
4. Publish only after signature verification and attribution/license review pass.
