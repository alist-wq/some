#!/usr/bin/env bash
# Builds only the unsigned artifacts that TASK 152 later signs and publishes.
set -euo pipefail

readonly REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly LOCK_FILE="${RENAV_INPUT_LOCK:-${REPOSITORY_ROOT}/tools/brouter/locked-inputs/ua-kharkiv-pedestrian.env}"
readonly PROFILE_FILE="${REPOSITORY_ROOT}/tools/brouter/profiles/renav-pedestrian.brf"

PBF_PATH=""
WORK_DIR=""

usage() {
  echo "usage: $0 --pbf /absolute/path/ukraine-260901.osm.pbf --work /absolute/output-directory" >&2
  exit 64
}

while (($#)); do
  case "$1" in
    --pbf) PBF_PATH="${2:-}"; shift 2 ;;
    --work) WORK_DIR="${2:-}"; shift 2 ;;
    *) usage ;;
  esac
done

[[ -n "$PBF_PATH" && -n "$WORK_DIR" ]] || usage
[[ -f "$PBF_PATH" ]] || { echo "PBF input does not exist: $PBF_PATH" >&2; exit 65; }
[[ ! -e "$WORK_DIR" ]] || { echo "work directory must not already exist: $WORK_DIR" >&2; exit 65; }

# shellcheck disable=SC1090
python3 "$REPOSITORY_ROOT/tools/brouter/verify_locked_input.py" "$LOCK_FILE" "$PBF_PATH"
# shellcheck disable=SC1090
source "$LOCK_FILE"

for command in git java python3 sha256sum; do
  command -v "$command" >/dev/null || { echo "missing required command: $command" >&2; exit 69; }
done

mkdir -p "$WORK_DIR"
readonly SOURCE_DIR="$WORK_DIR/brouter-source"
readonly PIPELINE_DIR="$WORK_DIR/pipeline"
readonly ARTIFACT_DIR="$WORK_DIR/artifacts"
mkdir -p "$PIPELINE_DIR" "$ARTIFACT_DIR"

# Fetch precisely the approved revision into an empty workspace. No update
# script, previous planet file, SSH connection, or remote deployment is used.
git init --quiet "$SOURCE_DIR"
git -C "$SOURCE_DIR" remote add origin https://github.com/abrensch/brouter.git
git -C "$SOURCE_DIR" fetch --depth 1 origin "$BROUTER_REVISION"
git -C "$SOURCE_DIR" checkout --quiet --detach FETCH_HEAD
[[ "$(git -C "$SOURCE_DIR" rev-parse HEAD)" == "$BROUTER_REVISION" ]] || { echo "BRouter revision mismatch" >&2; exit 65; }

pushd "$SOURCE_DIR" >/dev/null
./gradlew --no-daemon :brouter-server:fatJar
popd >/dev/null
readonly BROUTER_JAR="$(find "$SOURCE_DIR/brouter-server/build/libs" -name '*-all.jar' -type f -print -quit)"
[[ -n "$BROUTER_JAR" ]] || { echo "BRouter fat jar was not created" >&2; exit 70; }

readonly PROFILE_DIR="$PIPELINE_DIR/profiles"
readonly MAP_DIR="$PIPELINE_DIR/mapcreator"
mkdir -p "$PROFILE_DIR" "$MAP_DIR"/{nodetiles,waytiles,nodes55,waytiles55,unodes55,segments,srtm}
cp "$SOURCE_DIR/misc/profiles2/lookups.dat" "$PROFILE_DIR/lookups.dat"
cp "$SOURCE_DIR/misc/profiles2/all.brf" "$PROFILE_DIR/all.brf"
cp "$PROFILE_FILE" "$PROFILE_DIR/renav-pedestrian.brf"

java -Xmx6144M -Xms6144M -Xmn256M -cp "$BROUTER_JAR" \
  -Ddeletetmpfiles=true -DuseDenseMaps=true \
  btools.mapcreator.OsmFastCutter "$PROFILE_DIR/lookups.dat" \
  "$MAP_DIR/nodetiles" "$MAP_DIR/waytiles" "$MAP_DIR/nodes55" "$MAP_DIR/waytiles55" \
  "$MAP_DIR/bordernids.dat" "$MAP_DIR/relations.dat" "$MAP_DIR/restrictions.dat" \
  "$PROFILE_DIR/all.brf" "$PROFILE_DIR/renav-pedestrian.brf" "$PROFILE_DIR/renav-pedestrian.brf" "$PBF_PATH"

java -Xmx6144M -Xms6144M -Xmn256M -cp "$BROUTER_JAR" \
  -Ddeletetmpfiles=true -DuseDenseMaps=true \
  btools.mapcreator.PosUnifier "$MAP_DIR/nodes55" "$MAP_DIR/unodes55" "$MAP_DIR/bordernids.dat" \
  "$MAP_DIR/bordernodes.dat" "$MAP_DIR/srtm"

java -Xmx6144M -Xms6144M -cp "$BROUTER_JAR" -DuseDenseMaps=true -DskipEncodingCheck=true \
  btools.mapcreator.WayLinker "$MAP_DIR/unodes55" "$MAP_DIR/waytiles55" "$MAP_DIR/bordernodes.dat" \
  "$MAP_DIR/restrictions.dat" "$PROFILE_DIR/lookups.dat" "$PROFILE_DIR/renav-pedestrian.brf" "$MAP_DIR/segments" rd5

read -r -a SEGMENT_NAMES <<< "${SEGMENTS:?input lock must declare SEGMENTS}"
(( ${#SEGMENT_NAMES[@]} > 0 )) || { echo "input lock did not declare any BRouter segments" >&2; exit 65; }
for segment in "${SEGMENT_NAMES[@]}"; do
  SEGMENT_PATH="$MAP_DIR/segments/$segment"
  [[ -s "$SEGMENT_PATH" ]] || { echo "expected Kharkiv segment was not created: $segment" >&2; exit 70; }
  python3 "$REPOSITORY_ROOT/tools/brouter/normalise_rd5_timestamp.py" "$SEGMENT_PATH" --epoch "$SOURCE_DATE_EPOCH"
done
cp "$PROFILE_DIR/lookups.dat" "$ARTIFACT_DIR/lookups.dat"
cp "$PROFILE_DIR/renav-pedestrian.brf" "$ARTIFACT_DIR/renav-pedestrian.brf"
for segment in "${SEGMENT_NAMES[@]}"; do
  cp "$MAP_DIR/segments/$segment" "$ARTIFACT_DIR/$segment"
done

# Deterministic pedestrian regressions cover both required cells. The first
# is the central Kharkiv route that previously exposed the N50-only defect;
# the second protects continuity above latitude 50.0. Outputs contain route
# geometry only, never generated timestamps or logs.
readonly REGRESSION_ROUTES=(
  '36.23040,49.99350|36.23706,49.99602'
  '36.2208,50.0035|36.2391,50.0084'
)
(( ${#REGRESSION_ROUTES[@]} == ${#SEGMENT_NAMES[@]} )) || { echo "locked segments and regression routes must have equal cardinality" >&2; exit 65; }
pushd "$ARTIFACT_DIR" >/dev/null
for index in "${!REGRESSION_ROUTES[@]}"; do
  java -Xmx512M -cp "$BROUTER_JAR" btools.server.BRouter . . 0 renav-pedestrian \
    "${REGRESSION_ROUTES[$index]}"
  # BRouter appends the alternative index to its CLI export base, producing
  # testtrack0.gpx for the primary route.
  readonly REGRESSION_GPX='testtrack0.gpx'
  [[ -s "$REGRESSION_GPX" ]] || { echo "pedestrian regression did not produce a GPX route" >&2; exit 70; }
  python3 "$REPOSITORY_ROOT/tools/brouter/write_regression.py" "$REGRESSION_GPX" "regression-$index.json"
  rm "$REGRESSION_GPX"
done
popd >/dev/null

evidence_arguments=(--lock "$LOCK_FILE" --lookup "$ARTIFACT_DIR/lookups.dat" --profile "$ARTIFACT_DIR/renav-pedestrian.brf")
for index in "${!SEGMENT_NAMES[@]}"; do
  evidence_arguments+=(--segment "$ARTIFACT_DIR/${SEGMENT_NAMES[$index]}" --regression "$ARTIFACT_DIR/regression-$index.json")
done
python3 "$REPOSITORY_ROOT/tools/brouter/write_build_evidence.py" "${evidence_arguments[@]}" --output "$ARTIFACT_DIR/build-evidence.json"

echo "TASK 154 unsigned artifacts: $ARTIFACT_DIR"
