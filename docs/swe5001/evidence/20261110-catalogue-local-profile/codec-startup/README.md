# Cache codec/startup local diagnostic evidence

These are local diagnostics, not AWS acceptance, capacity, or a production-readiness claim.

**Rejected candidate:** this directory measures `commerce-cache-codec.jar` (SHA-256 `0cdf2416f7784331542f21d977d80d9c9208863965d4948716ab2b9814090bff`), not the current restored source. Both candidate cold cases and both healthy controls failed; candidate A fault cases were refused. Main rejected this candidate and restored main/test/IT exactly to the reviewed-v3 snapshot, recorded in [`../codec-rejected-source/restoration.json`](../codec-rejected-source/restoration.json).

The rejected candidate separately passed its clean 2,074-test verification, coverage and contract checks, retained under [`../verification-codec/`](../verification-codec/); that verification success does not supersede the failed runtime measurements or reverse rejection. The current restored reviewed-v3 source is covered by the prior 2,073-test verification, not by this rejected artifact's runtime or separate verification results. These source-restoration and verification attributions are the main session's supplied handoff; this evidence-only task did not rerun those checks.

The released baseline JAR is compared in ABBA order with the separately hashed eager cache codec/immutable startup profile plus reviewed v3 backoff; candidate attribution is not baseline source or a previous measured candidate JAR.

| Case | Exit | Requests | Errors % | Drops | p95 ms |
|---|---:|---:|---:|---:|---:|
| codec-base-a-warmup | 99 | 14678 | 55.3413 | 9323 | 10001 |
| codec-base-a-control | 0 | 12001 | 0.0000 | 0 | 94 |
| codec-base-a-flush | 0 | 12001 | 0.0000 | 0 | 4 |
| codec-base-a-slow | 99 | 11889 | 12.9195 | 2112 | 10001 |
| codec-base-a-down | 99 | 10554 | 25.5069 | 3447 | 10001 |
| codec-base-a-recovery | 0 | 12001 | 0.0000 | 0 | 4 |
| codec-candidate-a-warmup | 99 | 12321 | 97.5732 | 11679 | 10001 |
| codec-candidate-a-control | 99 | 12001 | 0.9166 | 0 | 3713 |
| codec-candidate-b-warmup | 99 | 12406 | 92.5117 | 11595 | 10001 |
| codec-candidate-b-control | 99 | 12001 | 0.5750 | 0 | 2717 |
| codec-base-b-warmup | 99 | 12362 | 92.0887 | 11638 | 10001 |
| codec-base-b-control | 99 | 12000 | 0.0333 | 0 | 856 |

Controller completed: `True`. Complete independent audit and JFR collection: `True`. Missing or partial data are listed in `outcomes.json`, never treated as successful or zero-valued measurements. Failed threshold cases remain failures.

`scripts/run-codec-startup.mjs` and `scripts/driver-resources.js` are the original frozen runtime scripts as used; `scripts/analyze-codec-startup.py` is the audit script as used, retaining corrected maximum aggregation for `*_max` while sums/counts are unchanged. `JfrCodecSummary.java` differs from the original cold helper only in its accepted codec-prefix regex; its package-private class name is intentionally unchanged for Java source-file launch.

`receipts.json` is condensed and excludes SQL/query text and proxy configuration. Per-case contexts omit proxy bodies; summaries, exit codes, actions and independent audits are retained when present. JFR outputs contain aggregated symbols/durations only. `private-artifacts.json` hashes external raw files and recordings; no raw samples, JFR binaries, log bodies, properties, local.env, TLS keys or credentials are copied.

JFR first-five-second comparisons are in `outcomes.json`. Monitor event durations are inclusive, overlap across threads, and are assigned by event start without clipping; they are not five-second wall time or CPU usage. A present bracket without matching events is not proof that contention is impossible. No failed arm was rerun. Full application verification and living documentation are outside this evidence-only task.

The original `retained-files.json` and all measurement/analysis receipts remain unchanged. `README-as-initially-retained.md` preserves the README bytes identified by that original manifest; `retained-files-addendum.json` records this attribution-only README amendment without overwriting the original receipt.
