# Minimal collection integration — proposal only

Prepared by static inspection at approximately 20:45–20:50 SGT, 11 November 2026. **Not installed, not tested, not canonical, not launch approval.** Repository and sealed campaigns were read only; no Git command, Docker operation, host Node/Java, network probe, AWS call, or runtime change was performed by this unit. Only files in this new directory were written.

## Recommendation / checkpoint

**20:53 SGT steering addendum:** [`RUNNER-FIX-MAP.md`](RUNNER-FIX-MAP.md) supplies a concrete non-executable offline runner copy, original/proposed SHA-256, full mount/path inspection, and a proposed no-default authority-bound future deadline interface. Original runner/HOLD/authority remain unchanged; zero parser errors is static evidence only, not a functional pass or approval.

Add one **offline post-collection wrapper**, outside the load runner, around the existing normalizer and unchanged four-input analyzer. This meaningfully improves the previous prototype's integration map without introducing another evidence schema or framework. Do not import `proposed-boundary-v1` into campaign receipts. Missing independently trusted configuration must refuse analysis, not generate a passing anchor from submitted evidence.

**Cannot currently certify safe integration and review within 25 minutes after mitigation validation.** The code path is small, but trusted configuration/custody, exact executable inventory, image digests, required supplemental status, fixtures and independent review remain unresolved. A 21:30 target cannot justify skipping them. Network mitigation validation is a separate prerequisite and was not assessed here. R4 remains held regardless of post-collection readiness.

## Actual integration map

Paths below are relative to the repository unless prefixed `prep/`; `prep/` means `C:/Users/sirip/AppData/Local/Temp/opencode/swe5001-rerun-prep-20261111`.

| Surface | Actual behavior / smallest future action |
| --- | --- |
| `infra/swe5001/scripts/observe-load.py:58–117,140` | Writes original `load/observer-context.json`, `load/observations.jsonl`, `load/observer-result.json`; process exits 1 on incomplete collection. Preserve unchanged. |
| `infra/swe5001/scripts/load-capture.py:57–77,209–237` | Live heartbeat gate before preparation; final readbacks include `scaling-activities.json`, `metrics.json`, and three `*-connection-errors.json`. Do not invoke prepare/restore/finish from the offline wrapper. |
| `prep/v3-local-tooling/review-v3-r2-coverage.py:8–88` | Existing uncommitted normalizer reads `/results`; writes `scale-outs.json` and `coverage-review.json` with exclusive creation, then asserts no normalization blockers. It does **not** assert complete collection. Run unchanged, with assertions enabled, only on bound immutable inputs after collection. |
| `prep/r3-network-diagnosis-20261111/review-r3-coverage.py` | Historical adapter changes root/output paths using text replacement and `exec`. Do not reuse this dynamic adapter for a future canonical path; mount inputs at the normalizer's existing `/results` paths instead. |
| `perf/k6/analyze-v3.mjs:6–20` → `scaling.mjs` → `lib.mjs` | Exactly four inputs: summary, raw samples, ECS observations, normalized events. Does not read collector outcome, coverage receipt, normalization blockers, or normalization process status. Leave all three source files unchanged. |
| Proposed `prep/<approved-campaign>/tooling/Invoke-V3CollectedAnalysis.ps1` | The only new implementation file, plus focused tests and a short operator instruction. Owns source/input binding, isolated normalizer invocation, strict receipt checks, and conditional analyzer invocation. No launch, polling, restore, retry, teardown, or acceptance override. This file does not exist yet. |
| `prep/v3-20261111-distributed-r4/tooling/runner.ps1` | Ends with `completion.json` and k6 exit, not observer completion/analysis. Keep analysis separate; do not attach it immediately after `docker wait` while the observer is still collecting. |

## Canonical existing field names — do not rename or synthesize

| Existing artifact | Required boundary checks / identity binding |
| --- | --- |
| `source-lock.json` | Independently pinned exact file digest; `baseCommit`, `sourceState`, `files`, `sessionFiles`, `operatorWrapperSha256`. `stagedEntries`/`indexSha256` exist but offline analysis must not read/change Git/index. Verify executable bytes against the pinned lock and separately approved inventory, not against a freely replaceable lock. |
| `load/execution.json` | `runLabel`, `toolingSha`, `releasedSourceSha`, `sourceState`, `sourceLockSha256`, `orchestrationSha256`, `expectedImageDigest`. Bind to operator-approved run/source/release and actual lock/runner bytes. No `schema`, `completed`, or `sourceManifestSha256` is promised here. |
| `load/summary.json` | `schema`, `context.runLabel`, `context.testSha`, `context.imageSha`, `context.expectedImageDigest`, `finishedAt`, `summary.state.testRunDurationMs`. Bind run/source/release to execution/config; analyzer retains all report/preflight/HTTP validation. |
| `load/observer-context.json` | `session`, `account`, `region`, `cluster`, `persistentHostConfirmed`, `startedAt`, `seconds`, `sourceFileSha256`. Bind collector hash/target/session provenance to reviewed configuration and retained capture custody. The result itself has no run label/session field. |
| `load/observer-result.json` | Require literal `collectionComplete === true`, `timerCompleted === true`, explicitly empty array `errors`, integer `samples >= 2`, finite nonnegative `maximumSampleGapSeconds <= 45`. No coercion or missing-value defaults. |
| `load/observations.jsonl` | Recompute actual row count, `observedAt` ordering/gaps and execution bracketing; compare receipt count/max gap. Preserve full ECS rows for unchanged analyzer. |
| Derived `coverage-review.json` | Require literal true `collectionComplete`, `timerCompleted`, `sampleGapsWithin45Seconds`, `reportedGapMatchesCollector`, `bracketsExecution`, `sampleCountMatchesCollector`; explicit empty `collectorErrorsPreserved` and `normalizationBlockers`; verify `samples`, `first`, `last`, `maximumGapSeconds` against actual rows/collector. Do not treat unrelated diagnostic booleans as universal approval. |
| Derived `scale-outs.json` | Existing `schema: 1`, `toolingSha`, `runLabel`, `events`, `normalizationBlockers`. Require explicit empty blocker array and same run/tooling identity; unchanged analyzer still validates sourced events and task counts. |
| `load/coverage-review-exit.json` in R2 | Existing fields are `at`, `scope`, `diagnosticReviewExit`, **not** `completed`/`exitCode`. This receipt is not universally present: R3 review instead retained `evidence-review/coverage-cli.exit.txt`. Prefer the wrapper's directly observed native normalizer status for a future run; never fabricate a missing historical receipt. |
| `supplemental/result.json` | Existing `samples`, `errors`, `collectionComplete`, `runtimeMutationPerformed`; no timer/max-gap field. A required supplemental stream must have its original receipt, literal true completeness and empty errors. Whether it is required must be explicitly approved in the trusted collection profile; unknown requiredness blocks a complete-bound-input claim. Do not substitute it for the ECS observer or silently waive its failures. |

Recompute with actual timestamp precision rather than truncating successful-sample gaps. Existing normalizer compares maximum gap with tolerance `< 1e-6` seconds and uses **strict** before/after bracketing; unchanged analyzer permits equal coverage endpoints. Preserve and disclose that existing difference rather than silently changing either producer. A new nanosecond schema is not necessary to this minimal plan.

## Minimum independently trusted inputs

1. **Operator-owned entrypoint/configuration and custody:** configuration outside the submitted campaign's writable tree, selected by the authorized reviewer, pinned/approved out of band, mounted read only. A caller-provided config path/hash is not an authenticated trust root by itself. Approval of this custody model is still missing; no example approval file is supplied here.
2. **Pre-run source identity:** approved run label, target identity, tooling base `b911ea63795c5ddb7db1f7918f7f7af03a2745ff`, explicit uncommitted source state, independently pinned source-lock digest, and reviewed executable inventory. Release source `3a893036d70d028b29ae13ebf820283571c1ae00` remains separate. The base SHA is never the full identity of the tooling bytes.
3. **Executable closure:** analyzer → `scaling.mjs` → `lib.mjs`; collector → `load-capture.py` → `cap-pools.py`; workload `browse.js` → `lib.mjs`; exact normalizer source; runner/operator wrapper; and new analysis wrapper plus any dependency. The existing lock covers many files but does not pin the external normalizer, R3 coverage adapter, or proposed wrapper. Approve/pin these separately or extend a future lock before use; do not edit old locks. Runtime image digests/entrypoints and mount/argument inventories also require approval. Repository-wide application closure is not claimed.
4. **Post-run input custody:** reviewer-bound exact bytes/hashes for execution, observer context/result/rows, summary/raw samples, original scaling activities/metrics/log projections, and each required supplemental input. Reuse the actual sealed `evidence-manifest.json` `files[].path/bytes/sha256` shape if available, with its digest independently pinned; otherwise independently reviewed per-input hashes are required. The evidence cannot supply its own sole expected digest. Preserve original raw audit/sealing requirements.

Hashes prove byte consistency, not truthful execution. A read-only Docker mount does not prevent another host process rewriting its backing files: use a quiescent sealed snapshot with approved custody and verify exact bytes used; do not claim race freedom from `readonly` alone. Never copy the multi-GB raw stream into memory; stream hashes/analyzer input. Missing anchor, absent required receipt, ambiguous inventory, source drift or concurrent writer means refusal.

## Smallest future control flow

1. After full observer completion, use a new private analysis output directory; refuse existing output artifacts. Do not use the broad `r4-operator.ps1` (it mounts AWS profiles and writable operational areas).
2. Authenticate approved operator configuration/custody first; verify exact source-lock/executable/input hashes and all run/release bindings. Reject missing/malformed collector receipts and any recorded read errors **before** invoking the analyzer. Do not reinterpret native exit zero as proof of completeness.
3. Run the existing normalizer once in an offline Python container against immutable inputs. Mount each required input file read only at `/results/<original-name>`, with `/results` otherwise a new writable derived-output directory. Required inputs are `summary.json`, `observer-result.json`, `observations.jsonl`, `scaling-activities.json`, `metrics.json`, `identity-svc-connection-errors.json`, `commerce-svc-connection-errors.json`, `notification-svc-connection-errors.json`. Mount only normalizer code read only; no credentials, Docker socket, campaign write mount, or AWS environment.
4. Capture normalizer native exit/stdout/stderr even on failure; failed process, partial outputs, absent blockers arrays, any blocker, incomplete collector/coverage or row inconsistency refuses analyzer invocation. Successful normalization uses unchanged code with Python assertions **enabled**; `-I`, no `-O`/`-OO`, protects against `PYTHONOPTIMIZE` environment bypass. Both outputs are required because the normalizer may write events before a later metrics/log read fails.
5. Recheck source/input snapshot identity, bind hashes of freshly created outputs to this invocation, then run only the same hashed analyzer with the exact existing four arguments (below). Derived outputs are read only to the analyzer; retain its stdout/stderr/native status privately.
6. Return 1 for missing/invalid/incomplete boundary evidence, normalization failure, unknown/signal/spawn status or source mismatch; **do not invoke analyzer**. Once invoked, preserve analyzer 0/1/2 exactly (0 limited gates passed; 1 invalid/incomplete; 2 HTTP/scale-out gates failed). No pipeline/last-command masking. Keep k6 exit, observer exit, normalization exit and analyzer exit distinct. Boundary pass never sets `acceptanceComplete` or `launchAllowed` true.

### Exact child commands, future only — not executed

These are the fixed commands *inside* the proposed wrapper's separately approved, digest-pinned, `--pull=never --network=none --read-only` runtime containers; `/results` is the controlled mount layout above, `/derived` is normalized output, and `/campaign`/`/repo` are immutable inputs. Node and Python image identities are intentionally not invented. Missing approved runtime/configuration blocks execution.

```text
python3 -I /normalizer/review-v3-r2-coverage.py
node /repo/perf/k6/analyze-v3.mjs /campaign/load/summary.json /campaign/load/samples.jsonl /campaign/load/observations.jsonl /derived/scale-outs.json
```

Do not publish raw child stderr as a CLI diagnostic: JSON parser errors may quote offending input; normalizer assertion errors/tracebacks can reveal private paths or records. Keep private exclusive-create output files and print only fixed codes such as `BOUNDARY_TRUST_REQUIRED`, `BOUNDARY_SOURCE_MISMATCH`, `BOUNDARY_COLLECTOR_INCOMPLETE`, `BOUNDARY_NORMALIZATION_REFUSED`, `BOUNDARY_RECEIPT_MISSING`, `BOUNDARY_ANALYZER_INVALID`, with a fixed artifact role and numeric status/count where appropriate. Do not echo tokens, runtime environments, AWS responses, event message bodies, arbitrary paths or attacker-controlled error strings.

## Existing requirements versus proposed hardening

- **Existing:** failed reads/incomplete observer cannot establish successful collection; timer/sample/gap semantics; original normalization blockers/allowed policy handling; original analyzer preflight, complete timing/arrivals, explicit zero drops, raw-count consistency, service-count/revision, all-run error and event-relative latency/scale-out gates. Keep error `<1%`, p95 `<800 ms`, zero drops, original arrival tolerance, task bounds and 180-second exclusions unchanged. All-run k6 outcomes remain retained, never replaced by analyzer outcome.
- **New enforcement/hardening, requires review:** automatically connecting collector/coverage/blocker refusal to analyzer invocation; authenticated independent configuration; complete executable/runtime/input binding; immutable snapshot custody; malformed/missing schema handling; controlled native exit capture and privacy-safe CLI. Existing receipt fields are reused, but this automatic boundary was not historically installed.
- **Not added:** new HTTP thresholds, launch freshness rules, sample duration thresholds, workload/policy changes, linearity redefinitions, required all-green diagnostics, approval bypass, source attestation claims, or backfilled successful receipts. Supplemental requiredness must not become a silent new historical criterion.

## R4 launch blockers discovered by static inspection (do not repair here)

- `LAUNCH-HOLD.json` exists with `launchAllowed:false`; runner line 7 refuses its existence. Only the owner may explicitly clear the hold; all-green analysis cannot do that.
- Runner lines 37/57 still use `2026-11-11T12:00:00Z` (**20:00 SGT**), already expired at inspection. A later checkpoint requires new explicit owner authorization and reviewed guards, not an inferred deadline extension.
- Runner line 40 mounts campaign at `/r3` but asks `require_observer(Path('/r4/load'))`; line 44 also mounts at `/r3`, while line 59 invokes `/r4/tooling/network-controls.py`. These path inconsistencies respectively cause observer refusal and failed network-control startup. They are separate from post-collection integration and must be tested/reviewed before any possible launch.
- R4 presently lacks `source-lock.json` and a load directory, as expected for inert controls. Do not copy stale R3 receipts or treat this proposal as creating them.

## Historical outcome / remaining approvals

R3 stays incomplete: 157 observer samples, four read errors, `collectionComplete:false`, maximum successful-sample gap 41.878859 s; the supplemental stream has five read errors and a reported maximum gap 117.816525 s. Successful spacing and empty normalization blockers do not erase collection failure. Original analyzer exit 1 due to 75 generator drops is unchanged; no alternate analyzer or acceptance result was computed here.

Before implementation/use: owner authorizes the code-change scope; reviewer approves trust custody, complete inventory/runtime pins and supplemental profile; focused rejection/dispatch tests pass after main session clears Docker use; independent reviewer checks the exact diff/commands and immutable-input model; future campaign authorization/fresh readiness/network mitigation are handled separately. No source or evidence rewrite, AWS write, on-demand migration, or new V3 belongs to this unit. For any later repository PR, append issue-linked sprint evidence and NFR-64 evidence under the repository's normal review process; do not backdate a pass for this static proposal.
