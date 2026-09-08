# External evidence dependencies

## predict4java 1.2.2

- Purpose: standards-based SGP4/SDP4 propagation of CelesTrak TLE reference data.
- License: MIT.
- Module: `core`.
- Why not custom: orbital propagation is numerically sensitive and a custom implementation would add avoidable correctness and validation risk.

## OkHttp 4.12.0

- Purpose: lifecycle-safe HTTPS and WebSocket transport for the CelesTrak and NEPTUN read-only feeds.
- License: Apache License 2.0.
- Module: `app`.
- Why not custom: Android has no uniform platform WebSocket API across the supported API range; custom framing/reconnection code would add protocol and lifecycle risk.

Both feeds are optional evidence sources. Neither dependency uploads session data or participates directly in navigation decisions.

`org.json:json:20240303` is test-only (public-domain implementation) so JVM tests exercise the same JSON API supplied by Android at runtime.
OkHttp MockWebServer 4.12.0 is test-only and shares OkHttp's Apache-2.0 license; it provides deterministic local HTTP/WebSocket lifecycle fixtures without contacting a live alert service.
