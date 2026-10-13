# TASK 154 BRouter fixture builder

`build_fixture.sh` builds only unsigned, generated CI artifacts for TASK 152. It uses a dated input lock, a pinned BRouter checkout, and direct map-creator invocations; it never uses BRouter's update scripts or deploys to a server.

Run on Linux or a GitHub Actions runner:

```bash
tools/brouter/build_fixture.sh \
  --pbf /data/ukraine-260901.osm.pbf \
  --work /tmp/renav-brouter-build
```

The input must match [the committed lock](locked-inputs/ua-kharkiv-pedestrian.env) exactly. Output lives only beneath `--work/artifacts` and contains `lookups.dat`, `renav-pedestrian.brf`, `E35_N45.rd5`, `E35_N50.rd5`, deterministic pedestrian regression evidence for both grid cells, and canonical `build-evidence.json`. TASK 152 is the only task permitted to sign or publish those outputs.
