# Dev delivery proxies (DORA), snapshot 22 October 2026

*DevSecOps Engineering and Automation*, module 16 (Business Metrics), PDF pp. 3–4, defines deployment frequency, commit-to-production lead time, incident recovery time and change success rate. This report measures **dev Actions delivery proxies**, not those production outcomes. No performance targets have been agreed; no incident MTTR, service-availability or production-health claim is made.

## Reproduce offline

From the repository root, one command reproduces the whole-window table and coverage counts without `gh`, credentials or network requests:

```bash
python -B .github/scripts/dora-metrics.py PPsyrius/iss-groupbuy-team8 2026-09-01 2026-10-23 --snapshot docs/ci/evidence/dora-metrics-20261022-inputs.json
```

The [sanitized frozen inputs](evidence/dora-metrics-20261022-inputs.json) retain run/job/attempt IDs, SHAs, timestamps, selected job outcomes, exact merged-PR commit metadata and ancestry comparisons, including rollback inputs. Raw API responses, people, messages, logs, secrets and job steps are not retained in the repository. Add `--json` for exact values and measured lead-sample identities. See the [runbook](backend-pipeline.md#delivery-metrics-dora) for explicit recollection, cutoff and cache-failure behavior.

Observation cutoff: **2026-10-22 13:54:34 UTC**. Read-only collection ran from 13:54:34 to 14:15:53 UTC; two truncated GitHub responses stopped earlier passes, and successful responses were reused locally at the same cutoff. Listings covered 154 backend runs, 177 frontend runs and no rollback dispatches; all attempts were inspected. The retained inputs contain 517 selected job records, 275 exact merged-PR records and 20 ancestry comparisons (477,688 bytes).

Input SHA-256: `1adcdc8ffa070eb832499a9aab60d928792fb8164af766c0d0c6bf2d35c19867`. The evidence file is pinned to LF in `.gitattributes` so checkout line-ending conversion cannot change that byte hash.

## Definitions and decision — 2026-10-22

| Term | Meaning in this report |
|---|---|
| Scope | Pushes to `dev`, backend `deploy-existing-ec2` in `ci-backend.yml` and frontend `deploy-s3-dev` in `ci-frontend.yml`; every retained rerun attempt has its own job ID. Collection inspects all retained runs created at or before the cutoff, without a lower creation-date bound, so old runs rerun or finishing inside the window are not lost. |
| Window | UTC interval `[SINCE, UNTIL)`, using job completion time. Its effective end is the earlier of UNTIL and the frozen observation cutoff; frequency uses only that observed duration. Skipped/other terminal outcomes are counted by completion time; unfinished jobs created before the effective end are shown as incomplete at that end, even if fetched after they finish. |
| Deploy-job frequency | Completed successful deploy jobs per observed seven calendar days, including successful retries. Not unique changes per week. |
| Lead-time proxy | One sample per distinct head SHA whose **first retained success/failure deploy-job completion** for that part lies inside the window. Start: earliest author timestamp among commits in the exact merged PR (`merge_commit_sha` equals that SHA, same repository, base `dev`). End: first successful deployment at or after that first completion, before the effective window end, proven to carry the change by SHA equality or a frozen GitHub comparison whose merge base equals the change SHA. Retries do not resample a change; a later unrelated/older SHA is not proof. |
| Lead coverage | This is a first-attempt cohort, not all merged PRs or all successes in a window. Previously attempted changes are not resampled in sub-windows. Missing/ambiguous PR metadata, missing author dates, negative timestamp intervals and no proven success by the window end are shown separately. API failures are errors, not missing metadata. |
| Deploy-job success rate | Successful deploy jobs / jobs explicitly concluding success or failure. Cancelled, timed-out, skipped, other terminal and unfinished jobs are visible outside that denominator. This is an **attempt-level proxy**, not a rate of unique changes without defects. |
| Failure-to-next-success proxy | A failure episode starts at the first failed deployment after the preceding successful one; consecutive failures remain one episode. It ends at the next successful job for that part, irrespective of SHA. Only episodes starting inside the window enter duration statistics. Open episodes are right-censored at the effective end; episodes already open at the window start are counted as carried in, not silently reclassified as new failures. Neither measure establishes downtime or incident recovery. |
| Frontend smoke | For each successful publish attempt, inspect `e2e-dev` from the **same run and run attempt**. Success, failure, skipped, cancelled/other, incomplete, missing and out-of-order evidence stay separate. Smoke-only reruns are reported as unpaired, including unfinished or boundary-crossing jobs as `incomplete_at_end`, rather than overwriting the original attempt's outcome. |
| Rollbacks | Only dev `rollback-existing-ec2` jobs from `rollback-backend.yml` dispatches are counted. Prod jobs are not retained. No incident/change association is available, so dev rollback outcomes are reported separately and never added to the deploy-job denominator or treated as recovery events. |
| Summary statistics | Hours, with mean, median and nearest-rank p90 (`ceil(0.9 × n)`). Duration statistics use completed, attributable samples only; always read them with the eligible, missing and censored counts. `n=0` means unavailable, not zero recovery time or “none needed.” |

**Decision:** retain a manual, standard-library, report-only tool and a small sanitized evidence snapshot, rather than adding a schedule, dashboard or service. Repository/schema/history-scope/window/cutoff mismatches, incomplete pagination, GitHub's 1,000-run search cap, missing ancestry/start timestamps and API errors fail before replacing a snapshot. Collection validates, computes and formats before atomic publication and rechecks original file bytes; replay never writes, even if its input is deleted during analysis. Use one writer per snapshot (the byte recheck is not a lock). Delivery targets remain for the team to agree, separately from the evidence-integrity requirement [NFR-73](../quality/nfr-register.md).

## Whole-window results

Repository: PPsyrius/iss-groupbuy-team8

Requested UTC window: [2026-09-01T00:00:00Z, 2026-10-23T00:00:00Z)

Observed through 2026-10-22T13:54:34Z (exclusive; 51.579560 days). Frozen cutoff: 2026-10-22T13:54:34Z.

| Metric | Backend | Frontend |
|---|---|---|
| Deploy jobs (successful / success+failure) | 125 / 150 | 161 / 161 |
| Deploy-job frequency (successes/week) | 16.96 | 21.85 |
| Lead-time proxy (median) | 7.63 h (mean 22.01, p90 62.57, n=143) | 6.23 h (mean 13.34, p90 33.83, n=161) |
| Lead cohort (eligible; censored; missing; invalid) | 143; 0; 0; 0 | 161; 0; 0; 0 |
| Missing lead metadata | none | none |
| Deploy-job success rate (proxy) | 83.33 % | 100.00 % |
| Failure-to-next-success proxy (median) | 1.34 h (mean 17.60, p90 64.94, n=7) | n/a (n=0) |
| Failure episodes (opened; censored; carried in) | 7; 0; 0 | 0; 0; 0 |
| Other deploy-job outcomes (outside rate) | cancelled=5, skipped=6 | skipped=17 |
| Selected-job timestamp anomalies (outside durations) | 2 | 11 |
| Runs without a deploy-job record | 1 | 0 |

Frontend post-deploy smoke: failure=4, success=157; unpaired smoke jobs=17 (skipped=17).

Unattributed dev rollback jobs: success=0, failure=0; other outcomes: none. Not added to deploy-job rates.

The four failed post-publish smoke checks belong to runs [36215889478](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/36215889478), [37102432667](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37102432667), [37122348459](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37122348459) and [37182996807](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37182996807), all attempt 1. Their job IDs and successful publish jobs are retained in the input file. The 17 unpaired records were skipped, not additional executed checks. Backend [run 33952366644](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/33952366644) has no deploy-job record; no deployment outcome is inferred for it.

## Backend sub-windows

Reproduce the bring-up window with the same command and `2026-09-05 2026-09-18`, and the later window with `2026-09-18 2026-10-23`. Both use the same frozen input file and cutoff; they do not fetch GitHub again. A failure episode crossing 18 September remains carried in for the later window, while its first-window duration is censored at that boundary.

| Metric | 5–17 September | 18 September–cutoff |
|---|---|---|
| Observed days | 13.000000 | 34.579560 |
| Deploy jobs (successful / success+failure) | 22 / 47 | 103 / 103 |
| Deploy-job frequency (successes/week) | 11.85 | 20.85 |
| Lead-time proxy (median; p90; n) | 3.09 h; 65.13 h; 33 | 6.88 h; 61.43 h; 102 |
| Lead cohort (eligible; censored; missing; invalid) | 41; 8; 0; 0 | 102; 0; 0; 0 |
| Deploy-job success rate (proxy) | 46.81 % | 100.00 % |
| Failure-to-next-success proxy (median; mean; n) | 0.94 h; 13.07 h; 6 | n/a (n=0) |
| Failure episodes (opened; censored; carried in) | 7; 1; 0 | 0; 0; 1 |

All 25 recorded backend deploy-job failures completed in the first sub-window. Eight lead-cohort changes and one failure episode are censored at its end and reach success later in the whole window. They are not new cohorts in the later sub-window, which explains why duration sample counts do not add up to the whole-window counts. The later window's 103/103 deploy-job successes do not establish absence of incidents or defects.

## Current values in the course layout

Whole-window proxies are the current values, not a selectively chosen healthy sub-window. The course's requested production lead time, incident MTTR and actual change success rate remain unmeasured here.

| Course metric | Unit | Current dev proxy, backend / frontend | Target |
|---|---|---|---|
| Deployment frequency | successful jobs/week | 16.96 / 21.85 | not agreed |
| Deployment lead time | hours, median | 7.63 / 6.23 | not agreed |
| Change success rate | % success/failure jobs | 83.33 / 100.00 | not agreed |
| Mean time to recover | hours, mean | Incident MTTR unavailable; next-success proxy 17.60 (n=7) / unavailable (n=0) | not agreed |

## Measurement limits

- **Publication is not verified application health.** Frontend `deploy-s3-dev` publishes to S3 and submits a CloudFront invalidation; that does not prove that the new revision is serving correctly. Even a passing anonymous smoke does not prove the served SHA, authenticated/payment acceptance or absence of a service incident. A failed smoke may be a test/infrastructure problem; it is not automatically an incident.
- **Backend job failure is not downtime.** A job can fail before modifying the host; a failed health check may restore a previous image where one exists. Next-job success is not proof of incident resolution or automatic-rollback completion. No incident record or rollback-to-change mapping is supplied.
- **Author dates and cohort selection matter.** Author-controlled dates can precede a rebase or branch's merge by weeks. This tool does not time every commit, path-filtered-out PR or direct push without an exact merged-PR match. Lead/recovery cohort statistics are not additive across window boundaries; deployment counts are.
- **Retained GitHub history is the observation source.** Count-checked pagination establishes completeness of the returned listings, not recovery of deleted/expired runs or missing historical records. GitHub is not a transactional history database: job records and metadata are retrieved over a collection interval, then projected to the declared cutoff by timestamps. No live AWS state, runtime health or application incident was queried.
- **Source timestamp anomalies are preserved.** GitHub returned some skipped/cancelled job completions one second before their recorded start/creation. Only that bounded inversion in those excluded outcomes is tolerated and counted (frontend counts include smoke jobs); raw times are not repaired. Success/failure timestamp inversions and larger excluded-job inversions fail validation. These records never enter lead/recovery duration statistics.
- **Dev only.** SWE5001 ECS sessions and the production release/deployment paths are outside scope. No application code, workflow, deployment, maturity rating or presentation number is changed by this tool. Those documents should link a verified snapshot in their own follow-up.

## Correction to the original snapshot

This snapshot replaces the original latest-attempt-only collection; its old cache was not retained and its published values are not reproducible from repository inputs. The replacement includes all attempts, fixes completion-window consistency, deduplicates/attributes lead samples, exposes incomplete/censored outcomes and separates dev rollbacks and frontend smoke. The original author’s sprint-log entry remains historical; the [follow-up entry](../agile/sprint-04/log/2026-10-22.md#2026-10-22---ppsyrius---harden-and-replay-dev-delivery-metrics-613-pr-629) records this correction and verification.

Verification: `python -B -m unittest discover -s .github/tests -p 'dora_metrics_test.py'` passed 83 checks, including frozen-evidence table/hash, rerun, incomplete-smoke, publication and sub-window regressions. The complete `*_test.py` suite discovered 193 tests on Windows and Linux (189 passed, four opt-in native ZAP tests skipped); Node tooling-policy tests passed 22/22. Whole-window and both sub-window CLI replays produced identical JSON on Windows/Linux with `gh` absent from PATH and GitHub token variables removed; final-pass outputs and frozen input bytes are unchanged. Thirteen real CLI failure cases returned no report and preserved existing input bytes; all 130 documentation-relative links/anchors and `git diff --check` passed. No application tests, AWS/runtime health checks or hosted scans were performed for this report-only change.
