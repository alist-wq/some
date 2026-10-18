# RENAV navigation-first redesign package

**Status:** P0 interaction contract resolved at design-source level; production-design approval remains **NO-GO pending actual visual validation and owner review**. **Date:** 2026-10-18. This package is isolated in `renav-ui-ux-audit`; it changes no production Compose/View screen, navigation service, route, positioning, package verifier, BE-02 artifact, or reserved emulator. RB-04/RB-05 remain IN PROGRESS; Gate B OPEN; release NOT_READY. The full traceability, journey and competitive-UX review is in [MVP_JOURNEY_AND_COMPETITIVE_COVERAGE.md](MVP_JOURNEY_AND_COMPETITIVE_COVERAGE.md).

## Open the designs

Where a browser's local-file policy permits it, [index.html](index.html) presents 35 proposed mobile screens/states (the original 17, ten full-journey branches and eight P0 decision states); language, light/dark theme and 100/150/200% text scale can be changed independently. The design-review controls now share theme-aware tokens with the mobile preview. Primary controls walk through map → search → missing-origin recovery → manual start → route calculation. Route success/failure and positioning recovery are **external service outcomes**, selectable on the design workbench, not user buttons that bypass verification. The prototype is static, offline and deliberately disconnected from Android services. Its buttons change design state only; they do not calculate routes, save reports, request location or send data. `tokens.css` contains the proposed palette, spacing and component geometry; `prototype.js` contains paired English/Ukrainian copy and state mapping.

The two map backdrops are **historical Android screenshots, not current-build evidence**. They are copied unchanged solely to ground the design in RENAV's actual Kharkiv map treatment. The prototype overlays must not be read as a fresh Android capture or proof of live route/position behavior:

The historical captures contain obsolete controls and markers. The prototype now applies a theme-aware decorative veil and blur to neutralize them without editing either original image, and a separate EN/UK notice above the preview identifies the simulated design. Whether the rendered masking is visually sufficient remains unverified until accepted screenshots are available; see [V2_RENDERED_PROTOTYPE_ACCEPTANCE_REPORT.md](V2_RENDERED_PROTOTYPE_ACCEPTANCE_REPORT.md).

| Package asset | Preserved source | SHA-256 |
| --- | --- | --- |
| `historical-map-backdrop.png` | `docs/qa/rb05-ux-assessment-2026-10-15/02-map.png` | `E6AAF8C169272B3E7EDEE303370F29FD49F8191E159D837E5F77CECF4D9B7139` |
| `historical-route-backdrop.png` | `docs/qa/rb05-3c-2026-10-15/67-final-guidance.png` | `EBD8F1D208A54EF1F71F4D25AE77C193C0E09D9DE8D0C445BA64EDF40CD3ED52` |

Design inputs: `RENAV_UI_UX_DESIGN_VALIDATION_AND_IMPROVEMENT_PLAN.md`, `design/DESIGN_SYSTEM.md`, the historical RB-05.3b/3c screenshots and current source-defined navigation states. The previously authorized **current-build Android visual evidence sweep is still pending** because the BE-02 reservation has not formally ended. This proposal cannot close that evidence gap or the deferred human TalkBack traversal.

## Design direction

One navigation task owns the main map. A persistent labelled destination action and compact, separate offline/position statuses sit above it. Contextual bottom sheets carry the next action and only the information needed for that step; Advanced contains trainer, recordings, detailed packages and diagnostics. High-stakes state copy stays visible: a manually chosen point is not measured; LOW and ±100 m are explicit; a lost position removes progress/arrival claims; an ambiguous route match may pause progress while a separately reliable position remains available. The prototype does not enable fused positioning in production.

This intentionally replaces the old equal-weight teal controls and long undifferentiated cards with a clearer hierarchy: filled primary action, outlined secondary action, quiet text action and explicit destructive Stop. It keeps the map usable outside interactive overlays. It does not copy the older concept boards' vehicle mode, fused-position activation button, or decorative precise markers because those would exceed or misstate the approved pedestrian MVP.

## Design system contract

| Role | Light | Dark | Usage |
| --- | --- | --- | --- |
| Navigation primary | `#12355B` | `#9BC9FF` | Primary actions, headings and focus; never a reliability verdict. |
| Ready | `#067463` / `#DFF5EC` container | `#7BDCC3` / `#153E38` | Verified offline readiness or trustworthy position, always with text. The light foreground is darkened from the older `#087E6B` design token to meet the proposed text-contrast floor (calculated 4.99:1 versus 4.38:1). |
| Caution | `#A45A00` / `#FFF0D4` | `#FFCF80` / `#49300D` | LOW, estimated, uncertain route progress and recovery states. |
| Danger | `#B3261E` / `#FCE8E6` | `#FFB4AB` / `#56241F` | LOST/unavailable or destructive action, with an explicit label. |
| Surface/text | `#FFFFFF` / `#172027` | `#101418` / `#F4F7FA` | Unified Compose, native overlay and Android window roles. |

Typography: Android system Roboto/Noto Sans, at most two fonts; body 16 sp, supporting text 13–14 sp, action 16 sp semibold, section title 21 sp semibold, screen title 24 sp. Text uses system font scaling rather than fixed bitmap labels. Spacing follows the existing 4/8/12/16/24/32 dp rhythm. Corners are 8/12/16 dp; sheets have 16 dp corners. Actionable areas are at least 48×48 dp and grow with text; do not truncate safety copy. Focus has a visible 3 px equivalent outline and a semantic label. These are design values, not measured Android compliance results.

### Component specifications

| Component | Structure and states | Accessibility / interaction rule |
| --- | --- | --- |
| Map top row | Text-labelled Menu and flexible “Where to?” action; 16 dp side margins. | Each button ≥48 dp, distinct focus order, no transparent gesture interception outside bounds. |
| Status pair | Separate offline readiness and position reliability chips. | Both have text; color is redundant. Readiness never implies a reliable location. LOW/LOST must not inherit a green status. |
| Context sheet | Destination/header → explanation or safety disclosure → one primary action → secondary/quiet actions. | Max-height is inset/IME-aware and scrollable at 200%; back/cancel preserve selected destination when safe. |
| Safety disclosure | Caution/danger tinted block with title, provenance, confidence and uncertainty. | Literal text survives layout changes and screen-reader reading order; no color-only meaning. |
| Search results | One list surface with title and result type, light row separators. | Local search only; keyboard cannot cover first result or Cancel; result uses accessible name and role. |
| Map point choice | One labelled map-centre target with explicit manual source. | Confirmation cannot turn manual start into verified position; cancellation returns to selected destination. |
| Guidance | Primary current instruction or truthful waiting/hold state; clear Pause/Stop. | Progress/arrival hidden when unsupported; route and destination may remain visible. |
| Incident review | Allowlisted event categories, local-retention explanation, unchecked consent, Save and Cancel. | Save unavailable until affirmative consent; Cancel/Back do not persist; no share/upload control in MVP. |
| Advanced | Trainer, diagnostics, recordings and detailed offline data behind one menu entry. | No technical package/sensor controls in the everyday navigation journey. |

Specify normal, pressed, focused, disabled, loading and error states during implementation. Iconography should use the existing Android/vector icon library where possible; icons never replace labels or warnings. No icon asset is introduced by this design-only package.

## Screen inventory and intended transitions

The table below retains the original 17-state proposal for traceability. The 18 additional journey/P0 branches and current COVERED / PARTIAL / MISSING / DEFERRED dispositions are specified in [MVP_JOURNEY_AND_COMPETITIVE_COVERAGE.md](MVP_JOURNEY_AND_COMPETITIVE_COVERAGE.md); neither list is Android acceptance evidence.

| Design state | Main content | Next action / safety invariant |
| --- | --- | --- |
| Ready map | Offline ready, position independently unavailable, destination search. | Search remains visible; no precise user marker merely because packages are ready. |
| Preparing | One preparation message and progress treatment. | No READY or route action before verified packages. |
| Repair needed | Truthful unavailable state and one repair action. | No technical package controls in normal flow. |
| Position unavailable | Map and search usable without pretending a live fix. | Route planning may use deliberate manual start. |
| Search | Local query, descriptive result row, Cancel. | Select result once; preserve it through origin recovery. |
| Missing origin | Selected destination, reason and manual-position warning. | “Choose starting point” primary; Cancel/Back retain appropriate route context. |
| Manual selection | Map-centre target, unmeasured-location warning. | Confirmation records MANUAL_START/LOW; Cancel returns to the same destination. |
| Route preview | Destination, distance, walking mode, manual/LOW/±100 m disclosure. | Start guidance does not promote manual position to trusted current position. |
| Guidance | Waiting state, preserved route, provenance and Stop. | No progress-dependent turn or arrival without eligible current evidence. |
| Degraded estimate | Estimated/LOW with uncertainty cue. | No falsely precise marker; this is a **design-only** future state, not production fused activation. |
| LOST | No reliable current position; route remains. | No precise marker, progress or arrival claim. |
| Route progress uncertain | Position remains independently reliable, association is held. | Do not conflate route-progress unavailability with position loss. |
| Arrival | Clearly worded destination reached state. | Only fresh, integrity-eligible, sufficiently bounded positioning can trigger this in production. |
| Menu | Search, Report a problem, saved reports, one Advanced entry. | No diagnostics/trainer controls at top level. |
| Advanced | Diagnostics, trainer, recording and offline details. | Technical capabilities remain discoverable without crowding the map. |
| Incident review | Coordinate-free categories and unchecked local-save consent. | No automatic persistence, export or upload. |
| Saved reports | Local retention and individual deletion entry. | Production deletion must use confirmation and truthful failure handling. |

English/Ukrainian copy is paired in `prototype.js` for every listed state and action. Ukrainian labels are intentionally allowed to wrap; no screen relies on fixed character counts. Actual translated Android resources remain authoritative until separately approved replacement. The prototype does not claim human linguistic review.

**Preview limitation:** browser control refused the local `file:` prototype under its URL security policy on both attempted browser surfaces and explicitly prohibited workarounds. I did not route around that restriction. Source syntax, all 70 EN/UK state render strings, 420 language/theme/scale combinations, key parity, asset hashes and selected palette contrast are testable through `verify.mjs`; actual visual rendering and overflow still require owner inspection in a permitted local browser or a separately authorized preview route. This is not a current-build Android visual sweep.

## Responsive, theme and accessibility acceptance proposal

Before production acceptance, compare the **same real Android state** in EN/UK × light/dark × 100/150/200% font scale, on a compact portrait and a compact landscape/resized window. Each screenshot needs APK/source identity, Android/API, viewport, language, scale, system theme, state and reproduction path. Test window status/navigation bars as part of the same theme: icons and backgrounds must remain legible and no overlay may extend under an inset without safe padding. At 200%, all safety text and actions must remain reachable by scrolling, with no clipped, overlapped, or obscured control. The map remains gesture-available outside interactive bounds.

Measure normal text contrast at ≥4.5:1 and large text/essential control boundaries at ≥3:1; do not infer these values from palette tokens alone. Every action needs ≥48 dp touch bounds, a meaningful label/role, deterministic Back/cancel focus destination and no repeated announcement storm. A real human TalkBack traversal, including map zoom and secondary cancellation/focus paths, remains deferred/evidence pending; hierarchy screenshots do not substitute for it. Safety-copy parity tests must detect missing LOW/LOST, manual and uncertainty wording in both languages.

## Proposed bounded implementation slices — approval required for each

1. **Evidence bridge:** after BE-02 reservation closure, perform the already approved current-build Android screenshot sweep on a separate qualified target. Record confirmed defects versus this proposal; do not infer present defects from older captures.
2. **Token/theme foundation:** reconcile `RenavTheme.kt`, Android `styles.xml`, status/navigation bars and native top-row color roles with the token system. Keep service and map behavior unchanged. Accept only with EN/UK light/dark screenshots, measured contrast and focused theme regressions.
3. **Map entry and search:** restyle the labelled destination action, status pair and local result list, preserving package/readiness and touch dispatch. Verify first launch, preparing/repair, keyboard, Back and 200% reflow on Android.
4. **Route sheets and guidance:** restyle missing-origin, manual selection, preview, active/paused/degraded/LOST/progress-hold/arrival cards. Preserve service-owned presentation, MANUAL_START/LOW/uncertainty, route/session fencing and arrival gating. Verify route/destination retention, real map gestures and no false precision.
5. **Menu and local incident:** apply shared surfaces to primary menu, Advanced boundary and IC-03 review/list/delete without changing privacy contracts. Verify consent default unchecked, Back/Cancel behavior, no share control and EN/UK semantics.

Do not cherry-pick this design worktree wholesale into the primary checkout. Reconcile each approved UI slice against the newer service/lifecycle and notification-localization work, transfer only reviewed presentation/resource changes, then run relevant JVM and Android acceptance. No slice may bypass RB-04 field calibration, BE-02 endurance, human TalkBack, physical battery, final-build privacy, Gate B or Gate C. Production fused positioning remains disabled until its separate evidence and approval.

## Design approval questions

Approve or revise the **overall visual direction**, the status-chip separation of offline readiness from position reliability, the sheet hierarchy, and the EN/UK safety language before any production Compose/View implementation. Design approval is not a claim that this HTML prototype passed Android visual, accessibility or release acceptance.

