# V2 baseline selection — 12 November 2026

The report now uses the **11 November released-monolith run** as its primary V2 baseline, alongside selected V3 R4. The 16 October runs remain historical corroboration, with their original detailed tables and receipts preserved.

- Updated §4.1's workload/provenance row and ten-plateau results table from the retained [calibration](evidence/20261111-load-reruns/v2/calibration.json): 57,600 requests, zero errors/drops, overall p95 30 ms and 200-RPS plateau p95 18 ms. Generator allocation was 1,000 preallocated/maximum VUs, not October's 100/400.
- Retained the [observer limitation](evidence/20261111-load-reruns/v2/observer-resolution.json): 47 stable-task samples bracket the workload, but the planned collector ended early with exit 255 and server-metric corroboration is missing. Neither campaign establishes maximum capacity or a controlled comparison with R4.
- November is selected for current-release relevance, not uniformly better latency. October's repeat p95 was 25 ms overall and 23 ms at 200 RPS; differing revisions/configurations prevent inferring an improvement or regression.
- [Verification receipt](v2-baseline-selection-20261112.json) records passing source/table checks. Combined-chapter compilation and a fresh secret scan were attempted but blocked by the stopped Docker daemon; no updated PDF or fresh scan pass is claimed. Earlier receipts retain their original scopes.
- Peer integration: carry this small V2 table/provenance change into the editor's latest `04-1-performance.typ` during the already-required reconciliation. The older 23-file peer patch predates this selection; do not describe it as containing the new table.

No k6/application code, acceptance threshold, raw evidence, historical manifest or cloud resource changed. No load test was repeated.
