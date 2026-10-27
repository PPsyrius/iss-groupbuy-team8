<!-- report
rubric: D
owner: Liu Jin, Panpakorn Siripanich (Pan)
plan: #681
sources: docs/ci/; build-quality CI run at the freeze SHA
owns: R38, R41
status: draft
-->

# Appendix D: Test Inventory, CI Timings and Green Rates {.appendix}

This appendix holds the detail behind section 4.2 and section 4.3: the test inventory per module and level, the executed test totals, coverage, the workflow timings and green rates, and the dev delivery metrics. Every figure carries its date and source. Static counts were taken with `git ls-tree` on `dev` at `61486f0e` on 26 October 2026. The backend and frontend trees are unchanged between `61486f0e` and the freeze commit (section 1.1), so the static counts hold at the freeze.

#### Test inventory

Table D.1: Test inventory by kind, level and module (26 October 2026, `61486f0e`).

| Kind and level | Tools | Count by module | Example classes | Runs in |
|---|---|---|---|---|
| Unit (backend) | JUnit 5, Mockito | 186 `*Test.java` classes with 1,339 test annotations: domain 42, application 41, infrastructure 47, bootstrap 47, adapter 4, platform 2, `commerce-svc` 2, client 1. 23 of them start Spring or Testcontainers, so 163 are unit level | `RefundServiceTest`, `PaidGroupDealTest`, `DealStatusTest` | `build-quality`, every PR (required) |
| Integration | Testcontainers: PostgreSQL 18, Redis 8.10, ElasticMQ | 49 `*IT.java` classes with 242 annotations: bootstrap 24, infrastructure 15, `commerce-svc` 5, `notification-svc` 3, `identity-svc` 1, platform 1. 45 start containers | `TeamExpirySchedulingIT`, `WriteRateLimitIT`, `StripeRolloutAcceptanceIT` (story acceptance, 8 test methods) | `build-quality`, every PR (required) |
| Architecture (unit) | ArchUnit 1.5.1 | 17 rules in `ArchitectureTest` | `domain_is_framework_free`, `controllers_are_validated` | `build-quality`, every PR (required) |
| API contract (integration) | springdoc export, `git diff`, openapi-diff 2.1.7 | 1 contract file | `contracts/openapi.json` | `contract-check`, backend PRs (required by policy only) |
| Component (frontend unit) | Vitest, Testing Library, MSW | 78 test files: features 59, routes 10, components 4, test helpers 3, api 2 | `account.test.tsx` | `frontend-check`, every PR (required) |
| End-to-end (system) | Playwright, Chromium | 8 specs with 24 test declarations, 5 of them skipped by default (signed-in cases) | `buyer-journey.spec.ts`, `join-refusal.spec.ts`, `smoke.spec.ts` | `e2e-preview` on PRs; `e2e-dev` after each frontend deploy |
| Performance (system) | k6 1.6.1 (SWE5001 ECS); Node closed-loop workload (local) | One k6 browse scenario; three local scenarios | `perf/k6/browse.js`, `backend/perf/` | By hand; the k6 tooling itself is tested in `perf-tooling.yml` |
| CI tooling | Python `unittest`, Node test runner | 13 test files in `.github/tests/`: 193 Python tests (189 passed, 4 opt-in ZAP tests skipped) and 22 Node tests, run on 22 Oct | `backend_quality_test.py`, `gitleaks_allowlist_test.py`, `dora_metrics_test.py` | `workflow-lint` in `security.yml` when tooling changes; the backend policy tests also in `build-quality` |

Table D.2: Executed test totals from CI logs. Runs 40200028697 and 40294726775 have the freeze commit's code `6d14e449` (`git diff ad2a73e6 6d14e449 -- backend` and `git diff 9135ad8b 6d14e449 -- frontend` are empty); pull-request run 40206547524 predates one frontend dependency bump (#691, `happy-dom`) and has the same test files.

| Suite | Run and commit | Date | Result |
|---|---|---|---|
| Backend `SonarCloud Backend` (`./mvnw verify -Pci`, Surefire and Failsafe totals of 14 module executions) | 40200028697, push `ad2a73e6` | 25 Oct 2026 | 2,066 tests run, 0 failures, 0 errors, 1 skipped (opt-in `PerformanceBaselineIT`) |
| Frontend `frontend-check` (Vitest) | 40206547524, pull request | 25 Oct 2026 | 78 files, 872 tests passed |
| Frontend `SonarCloud Frontend` (Vitest) | 40294726775, push `9135ad8b` | 26 Oct 2026 | 78 files, 872 tests passed |
| Frontend `e2e-preview` (Playwright, preview build) | 40206547524, pull request | 25 Oct 2026 | 19 passed, 5 skipped |
| Frontend `e2e-dev` (Playwright, through CloudFront) | 40294726775, push `9135ad8b` | 26 Oct 2026 | 21 passed, 3 skipped |

Earlier totals differ because tests were added between them: 1,958 backend tests (local benchmark, 20 Oct), 1,980 (local verify in the CI failure audit, 21 Oct) and 2,048 (local verify, 21 Oct). SonarCloud's `tests` measure is never used as a test count.

#### Coverage

Table D.3: SonarCloud coverage and quality gate, read from the SonarCloud API on 26 October 2026.

| Project | Analysed commit | Overall coverage | New-code coverage | Bugs, vulnerabilities, hotspots | Ratings | Gate |
|---|---|---|---|---|---|---|
| BlocBuy Backend | `ad2a73e6` (25 Oct) | 100.0 % | 99.97 % | 0, 0, 0 | A, A, A | Passed |
| BlocBuy Frontend | `9135ad8b` (26 Oct) | 99.3 % | 99.25 % | 0, 0, 0 | A, A, A | Passed |

The gate conditions on new code are: coverage at least 80 %, reliability, security and maintainability ratings A, duplicated lines at most 3 % and all security hotspots reviewed. No file is excluded from the backend analysis: `backend/pom.xml` sets only the project key and the JaCoCo report paths, and the SonarCloud settings API holds no exclusions for either project (read 27 October). The frontend analysis excludes test files, `src/test/**`, the generated API client and the generated route tree, and its coverage also excludes the entry files `src/main.tsx` and `src/router.tsx` and the development-only `src/dev/**` (`ci-frontend.yml`). The deck's figures of 24 October (backend 99.4 %, frontend 96.1 %) predate two test pull requests merged on 25 October.

#### Workflow timings and green rates

Table D.4: Workflow timings, medians of successful runs (last 100 runs per workflow and event, read with `gh run list` and the jobs API on 19 October 2026).

| Workflow and trigger | Jobs (required in bold) and median job times | Median end to end | Green in the sample |
|---|---|---|---|
| `ci-backend`, pull request | **build-quality** 13.1 min, **docker** 2.8 min, **SonarCloud Backend**; contract-check 12 s | 16.1 min to the end of `docker` (18 runs, 16–19 Oct) | 18 of 20 finished runs; 16 cancelled by a newer push |
| `ci-backend`, push to `dev` | package-dev 46 s, publish-ecr-dev 73 s, deploy-existing-ec2 40 s | 3.1 min to deployed (88 runs) | Deploy 88 of 97; the 9 failures fell on 14–17 Sep |
| `ci-frontend`, pull request | **frontend-check** 3.4 min, **SonarCloud Frontend**; e2e-preview 69 s | 4.8 min to the end of `e2e-preview` (19 runs) | 19 of 20 finished runs |
| `ci-frontend`, push to `dev` | package-dev 35 s, deploy-s3-dev 21 s, e2e-dev 82 s | 2.7 min to a green `e2e-dev` (94 runs) | Deploy 97 of 97; `e2e-dev` 94 of 97 |
| `security`, pull request and push | **gitleaks** 10 s; trivy-deps 72 s, trivy-config, workflow-lint, deployment-bundle when their paths change | 12 s on most PRs | PR 97 of 98 finished; push 100 of 100 |

Pull-request durations count only runs in which the gated jobs ran; a documentation-only pull request skips them in about 10 s. Push medians run from the start of the push run, not from the merge click.

Table D.5: All-time run conclusions per workflow, read from the GitHub API on 27 October 2026 at 05:23 UTC (since 24 August 2026; same read as Table 4.13).

| Workflow | Success | Failure | Cancelled | Success among finished (success + failure) |
|---|---|---|---|---|
| `ci-backend.yml` | 1,473 | 111 | 548 | 93.0 % |
| `ci-frontend.yml` | 1,449 | 64 | 76 | 95.8 % |
| `security.yml` | 1,847 | 56 | 45 | 97.1 % |

Calculated: success among finished = success / (success + failure). Cancelled runs are mostly runs superseded by a newer push to the same branch, which the workflows' concurrency groups cancel on purpose. The scheduled weekly `security.yml` run fails every week on the four historical secret-scan findings (section 4.2).

#### Dev delivery metrics (DORA proxies)

The course's DORA metrics (DSOEA 16, Business Metrics, pp. 3–4) concern production. BlocBuy measures dev Actions delivery proxies instead, with a manual, report-only tool that replays a frozen, hashed snapshot of the run history (`docs/ci/dora-metrics-20261022.md`). Observation window: 1 September to 22 October 2026, 13:54:34 UTC. No targets were agreed, and none of these figures is an incident or availability measure.

Table D.6: Dev delivery proxies, whole window.

| Metric | Backend | Frontend |
|---|---|---|
| Deploy jobs, successful of success plus failure | 125 of 150 | 161 of 161 |
| Deployment frequency (successful deploy jobs a week) | 16.96 | 21.85 |
| Lead-time proxy, first commit of the merged PR to its first successful dev deploy (median; mean; p90; n) | 7.63 h; 22.01 h; 62.57 h; 143 | 6.23 h; 13.34 h; 33.83 h; 161 |
| Deploy-job success rate | 83.33 % | 100.00 % |
| Failure to next success (median; mean; n) | 1.34 h; 17.60 h; 7 | not applicable (no failures) |
| Rollback jobs | 0 | not applicable |
| Post-deploy smoke | not applicable | 157 passed, 4 failed |

Table D.7: Backend deploy jobs before and after the dev environment was corrected (section 4.3).

| Window | Successful of success plus failure | Success rate | Frequency (a week) |
|---|---|---|---|
| 5–17 September 2026 | 22 of 47 | 46.81 % | 11.85 |
| 18 September–22 October 2026 | 103 of 103 | 100.00 % | 20.85 |
