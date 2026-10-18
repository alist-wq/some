# RENAV V2 rendered-prototype acceptance

Date: 2026-10-18  
Scope: existing 35-state HTML design prototype only; no production Compose or Android acceptance.

## Visual-verification recovery update — 2026-10-18

**Current decision: rendered visual acceptance INSUFFICIENT_EVIDENCE; production Compose NO-GO.** The owner accepted the 35-state prototype at design-source level and authorized a prototype-only recovery. The following source corrections are complete; the screenshot portion is still blocked by the browser's local-file security restriction. No browser-rendered screenshot was captured, and no screenshot-based state is marked PASS.

### Prototype-only corrections

| Finding | Correction | Verification level |
| --- | --- | --- |
| Review controls were hardcoded to light colors. | Review controls, labels, select fields, separators and workbench now consume theme-aware tokens. The selected theme is also applied to the document root, so controls and mobile prototype share the light/dark palette. | Source-contract PASS; rendered appearance unverified. |
| Historical images contain obsolete turquoise menu/search controls, an old guidance card and position/route markers. | Original PNGs are unchanged. A decorative CSS grayscale/24 px blur and theme-aware veil masks the images behind the simulated UI. The treatment is deliberately noninteractive and does not represent a live map or route. | Raw-image inspection confirms the legacy elements; masking rule and original hashes PASS at source level; rendered masking effectiveness unverified. |
| Historical imagery could be mistaken for current UI evidence. | A separate, localized EN/UK notice above the mobile preview states that this is a simulated design using a historical map image. It sits outside the 390 × 844 mobile screen to avoid taking space from high-priority navigation controls at 200% text. | EN/UK mapping PASS at source level; visual clarity unverified. |

The unmodified original image hashes remain `E6AAF8C169272B3E7EDEE303370F29FD49F8191E159D837E5F77CECF4D9B7139` (map) and `EBD8F1D208A54EF1F71F4D25AE77C193C0E09D9DE8D0C445BA64EDF40CD3ED52` (route). No image-editing or production implementation occurred.

### Exact corrected source identity

Isolated worktree: `renav-ui-ux-audit`; base HEAD observed as `e63b4f8cb33caad262f82673de312d71ff8ab158`. The edited design files are worktree-local; identify this candidate by their SHA-256 hashes, not HEAD alone.

| File | SHA-256 after correction |
| --- | --- |
| `index.html` | `092C15D19458B139C2E1D447A433B3813D2787017E2C7DD59F2051690B67FF45` |
| `prototype.js` | `4004E09CB72141DDE86FFDD2FF6408BC32EDA29F944E3199D71FA0D850C26BDE` |
| `tokens.css` | `3C95CAFEDAFB9C22687891F29D6D0D020387F21886E9646A3682E5DABB6CDD10` |
| `verify.mjs` | `94E1FAD0D7587F0E1E0E53C8628347B1AAB66A3DCFFDF9C9EC357F7EA7FDE0D1` |

Fresh checks: `node --check prototype.js` **PASS (exit 0)**; `node --check verify.mjs` **PASS (exit 0)**; `node verify.mjs` **PASS (exit 0)**. The verifier covered 35 states × two languages, 166 paired EN/UK keys, seven semantic tokens, eight static color-contrast pairs, two unchanged historical images, 420 language/theme/scale source render contracts, theme inheritance, review-control token use, localized prototype notice, masking CSS, P0 transitions and safety assertions. It does **not** measure actual pixel contrast, element bounds, reachability, clipping or overflow.

### Permitted screenshot-export boundary

The agent's browser tool explicitly rejected the local `file:` page and prohibited indirect workarounds. Therefore no local server, alternate browser surface, headless renderer, raw browser protocol or security-setting change was used to make the same blocked page accessible. An automated local screenshot-export path is **not established** in this environment. The permitted evidence handoff is screenshots supplied by an authorized human operator from an independently permitted review session, or a future capture surface expressly allowed by the browser security policy. The operator should capture the complete preview **including its simulated-design notice**, not crop to the historical map image.

For each capture, record the corrected four-file hash set above, state, EN/UK language, light/dark theme, 100/150/200% scale, viewport dimensions, capture time and whether all actions are reachable. Keep images in the isolated design-evidence area; do not use the reserved BE-02 emulator. Source images and older Android screenshots are not acceptable substitutes for a fresh rendered prototype capture.

### Critical rendered-state disposition

| Priority layout family | Required configuration emphasis | Outcome |
| --- | --- | --- |
| Permission request/denial and offline preparing/repair | UK 200%, both themes; contextual nonblocking actions | INSUFFICIENT_EVIDENCE — no rendered screenshot |
| Ready map and destination search | EN/UK; 100/150/200%; controls versus decorative map | INSUFFICIENT_EVIDENCE — no rendered screenshot |
| Missing-origin/manual selection and route preview | UK 200%, light/dark; destination retention, MANUAL_START/LOW/±100 m | INSUFFICIENT_EVIDENCE — no rendered screenshot |
| Active/dense guidance, pause/background, degraded/LOST | UK 200%, dark; no false precision or obscured Stop | INSUFFICIENT_EVIDENCE — no rendered screenshot |
| Ambiguity, FreeSpace/departure, recovery, pending/confirmed arrival | EN/UK; trustworthy position distinct from unsupported progress; no premature arrival | INSUFFICIENT_EVIDENCE — no rendered screenshot |
| Menu/Advanced and incident consent/save/delete | UK 200%, dark; unchecked consent and action reachability | INSUFFICIENT_EVIDENCE — no rendered screenshot |

Confirm clipping, overlap, wrapping, contrast, touch-target bounds and action reachability only after inspecting real rendered captures and, where needed, interaction. The previously documented **PARTIAL secondary journeys remain acceptance debt**; this recovery neither silently completes them nor waives TalkBack, field, privacy or current-build Android validation.

**GO/NO-GO:** NO-GO for Compose implementation. Next: obtain actual rendered captures through an expressly permitted channel, inspect each critical family, log any confirmed defects with screenshot IDs/severity, and seek owner design approval. No visual defect in the corrected prototype is confirmed from source checks alone.

## Initial blocked visual attempt (historical record)

**INSUFFICIENT_EVIDENCE / NO-GO for the first Compose implementation slice.** The prototype could not be opened through the permitted browser workflow. The browser refused the local `file:` URL under its security policy and explicitly prohibited indirect workarounds. No rendered screenshot was captured or accepted in this run. This is an evidence blocker, not a confirmed visual defect in the design.

The prior P0 interaction-contract approval remains a **design-source-level** decision. It is not rendered visual acceptance. No production code, BE-02 environment, or validation artifact was changed.

## Initial candidate identity and checks performed

Candidate: isolated `renav-ui-ux-audit` worktree, source HEAD `e63b4f8cb33caad262f82673de312d71ff8ab158` (the design files are worktree-local; the HEAD alone is not a complete design identity).

| File | SHA-256 |
| --- | --- |
| `index.html` | `1C6BD08B6C8538040917CC4B08B6825CAFDE33931B93C941ED681A4427916A6F` |
| `prototype.js` | `E42C2F512246078C2EE6B8EDDCFC5FDCC4EB19962A75CB88E60E02C4D97390B8` |
| `tokens.css` | `2C24497EE6D523FC00AFDF2AEC06A65A5DB6E7A4A4250AE54D19B4BFD3C72D95` |
| `verify.mjs` | `FEFB17CABB6DA7DC208D8AF236552605DBDACEE0E85B44785B7B0A02F26D7FD2` |

Fresh source-contract check: `node docs/ux/renav-redesign/verify.mjs`, exit 0. It reported 35 states × two languages, 165 paired English/Ukrainian keys, seven semantic tokens, eight text-contrast pairs, two unchanged historical assets, and 420 language/theme/text-scale render contracts with P0 transition and safety assertions. The script itself states that visual overflow remains unverified. These are static/deterministic checks, **not** inspected browser renders. The two backdrops remain historical Android images, not current prototype screenshots.

## Requested visual acceptance matrix

| Check | Result | Evidence needed |
| --- | --- | --- |
| Representative journey screens and transitions | INSUFFICIENT_EVIDENCE | Rendered screenshots from launch through guidance, errors, recovery, arrival, menu, Advanced and incident review. |
| English and Ukrainian layout | INSUFFICIENT_EVIDENCE | Same-state paired captures, including long Ukrainian safety/action copy. Source key parity alone is insufficient. |
| Light and dark themes | INSUFFICIENT_EVIDENCE | Paired captures with legible controls, surfaces, status colors and map treatment. |
| 100%, 150% and 200% text scaling | INSUFFICIENT_EVIDENCE | Same-state captures at all three scales, especially scrollable sheets and bottom actions. |
| Clipping, overlap, obscured or unreachable controls | INSUFFICIENT_EVIDENCE | Full-viewport captures plus confirmation that sheet content and actions can be reached. |
| Historical backdrop legacy controls | INSUFFICIENT_EVIDENCE | Rendered map and route-backdrop states; inspect for visible competing buttons, cards or status text. |
| MANUAL_START, LOW/LOST and uncertainty | INSUFFICIENT_EVIDENCE | Manual-origin, preview, degraded and unavailable states at maximum scale in both languages. |
| Ambiguity, FreeSpace, pending arrival | INSUFFICIENT_EVIDENCE | Separate rendered states showing position reliability distinct from route-progress availability and no definitive arrival claim. |
| Visual system consistency | INSUFFICIENT_EVIDENCE | Side-by-side ready, search, recovery, preview, guidance, menu, Advanced and incident screens. |
| Screenshot capture | FAIL — blocked | Zero accepted screenshots; local-file navigation was denied by browser security policy. |

No clipping or accessibility defect is claimed from unrendered source. Severity classification: **P0 validation blocker** for missing rendered evidence; **no confirmed visual defect**; all individual visual risks above remain **unverified**. Screenshot inspection cannot replace the deferred human TalkBack traversal or current-build Android visual acceptance.

## Safe evidence handoff to complete the review

Provide screenshots captured by the owner in a permitted local browser, or an already-permitted browser tab explicitly made available for this review. Do not serve the blocked local file through an alternate route merely to circumvent the browser policy. Every capture should identify the exact four file hashes above, viewport dimensions, state name, language, theme and text scale. Preserve the full viewport, including top/bottom controls and any open sheet; do not crop away problematic edges.

Minimum representative set:

1. Ready map, permission recovery, preparing, repair-needed and unavailable packages.
2. Search with keyboard/results, missing-origin recovery, manual selection and cancellation.
3. Route calculation/error, preview, active guidance, pause/resume and background return.
4. Position degraded, LOST, route ambiguity/progress hold, FreeSpace/departure, recovery and pending/confirmed arrival.
5. Menu, Advanced, incident review, saved reports and deletion confirmation.

Capture the safety-critical states in **EN and UK × light and dark × 100%, 150% and 200%**; capture the remaining representative states at least once and repeat any layout that changes materially with language, theme or scale. Record whether the next action remains reachable and the expected Back/Cancel exit. Preserve screenshots as design evidence, not Android runtime evidence.

## Approval gate

**NO-GO** for production Compose implementation until the rendered prototype has accepted screenshots, a state-by-state defect disposition, and owner design approval. The smallest next action is a secure screenshot handoff or a genuinely permitted browser view of this exact candidate; then complete the visual matrix without changing the prototype or production code under this authorization.

RB-04 and RB-05 remain IN PROGRESS. Gate B remains OPEN. Release remains NOT_READY. No field, TalkBack, privacy or endurance requirement is waived.
