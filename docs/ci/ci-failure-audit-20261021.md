# CI failure audit — 2026-10-21

Scope: read-only GitHub inspection and local fixes on `fix/ci-gate-reliability`, based on `4faa4abb`. The remote dev branch advanced during the session; a successful older run is not verification of this working tree. No push, rerun, deployment, billing change, ruleset edit or credential operation was authorized or performed.

## Observed failures

| Finding | Evidence | Disposition |
|---|---|---|
| SpotBugs engine/plugin incompatibility | [October 20 failure](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39463373272): missing BCEL `PermittedSubclasses`; repeated October 19–20 | Reproduced locally with plugin 4.10.4.1/engine 4.10.5; fixed with plugin 4.10.5.0/engine 4.10.5, grouped future updates. #559 was closed, not merged, to be superseded by a human PR. |
| Weekly Gitleaks failures | [October 19 report](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39217910471); October 12, 5 and September 28/21 schedules also failed Gitleaks | Twenty digest false positives are now narrowly ignored; four badge-token findings remain unresolved and the scan still fails. |
| CodeQL unavailable | [September 15 failure](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/34938330654): analysis completed but ingestion failed because Code Scanning is not enabled | Current `gh variable get ENABLE_CODEQL` is `false`; Code Scanning analyses API returns HTTP 403 with the same message. Not an available required gate; leave disabled. |
| Actions budget blocked an environment run | [October 20 run](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39442654274), check-run `118696282822` annotation: "The job was not started because an Actions budget is preventing further use." | No runner or steps started. This proves the historical rejection, not current account availability; present budget/balance was not checked. Any spending change is an owner decision, not a YAML fix or reason for automatic retries. |
| Maven Central connection reset | [Older Spotless PR failure](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39391477859) while resolving `spring-boot-starter-restclient` | [Later run](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39463178053) passed all backend jobs. No formatter change or blanket full-build retry added. |
| TanStack Intent manifest drift | [October 20 failure](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39366872534): router-core 1.171.34→1.171.37 and router-plugin 1.168.42→1.168.45 | Those values already match the committed manifest and lockfile on the inspected baseline. Not an outstanding defect in this working tree. |
| Historical dev smoke failure | [October 4 run](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37182996807): API health returned 502; eighteen UI tests passed | This was a backend availability failure, not a reason to loosen Playwright assertions. Later frontend dev runs passed; no current authenticated/live acceptance was inferred. |
| SWE5001 messaging readiness failure | [October 16 run](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/38898587249): readiness failed and services stayed stopped | An infrastructure/runtime readiness failure, not the CodeQL or SpotBugs problem. The fail-closed readiness gate is retained. No AWS operation performed. |

Twenty failed security runs from September 4–October 19 were inspected by job/failed step, including the actual September 15 CodeQL failure rather than inferring availability from a skipped result. Recent failed runs across frontend, backend and SWE5001 workflows were also inspected. No frontend dev cancellation was returned; the returned backend dev cancellations were from September 5/11, so there was no evidence to justify the first candidate's broad concurrency rewrite as a current operational repair.

## Gitleaks false-positive verification

The downloaded October 19 SARIF contains 24 `generic-api-key` findings. Twenty point to `scripts/stripe-dev-contract-baseline.json` in commit `983b401a62619f7d9b2087e92f8367bb88eeadba`; each value is a file-content SHA-256 digest. `scripts/check-stripe-dev-contracts.py` specifies SHA-256 for that map. Each reported source path was read from baseline commit `211cfb7d95f60bebdf322ab19a83f43cb70c07ad` and hashed again; all twenty values matched. No detected value is reproduced here.

`.gitleaksignore` contains only those twenty exact commit/path/rule/line fingerprints. It does not ignore the baseline file, a rule, an entire commit, or any README. The four Sonar badge-token findings in `backend/README.md` at `ba1df47a` remain visible; their URL location alone is not sufficient proof that they are harmless or revoked. No use of those values for authentication was attempted.

Local verification matches the action's `git log -p -U0 --full-history --all` scope, scanning the same 458 commits before and after. For an isolated before/after comparison, mount only Git metadata and the existing config, then add the fingerprint file for the after run; merely changing the CLI ignore-path flag did not disable the repository-root ignore file. Run from a main checkout whose `.git` is a directory and set `AUDIT` to an existing private absolute directory outside the repository. Reports remain in approved private temporary storage:

```bash
docker run --rm -v "$PWD/.git:/repo/.git:ro" \
  -v "$PWD/.gitleaks.toml:/config/gitleaks.toml:ro" \
  -v "$AUDIT:/out" -w /repo zricethezav/gitleaks:v8.24.3 detect \
  --config /config/gitleaks.toml --redact --exit-code=2 \
  --report-format json --report-path /out/before.json
# After: add -v "$PWD/.gitleaksignore:/repo/.gitleaksignore:ro" before the image
# and use /out/after.json. Repeat after with v8.28.0 and a distinct report path.
```

Without the fingerprint file, 8.24.3 reports 24 findings. With it, 8.24.3 and 8.28.0 each report four, all in the historical backend README, and exit 2 as configured by CI. This is partial remediation, not a green full-history scan or accepted risk. Both versions separately report two synthetic new secret-shaped values in the baseline file and backend README, demonstrating that neither path is ignored. Changed-file directory snapshots pass with zero findings. Unit tests reject broadening the fingerprint list or making the secret scan depend on optional scans.

## Local verification and runtime cost

- Java 21 `mvnw.cmd -B -T 2 verify -Pci` passed with the aligned SpotBugs POM: 1,980 tests passed, one opt-in skip, zero errors/failures, ten SpotBugs reports with zero findings and all five JaCoCo XML inputs present. The 6m58s duration is functional verification, not a controlled hosted benchmark.
- `python3 -m unittest discover -s .github/tests -p '*_test.py'` passed 21 tests; `node --test .github/tests/cleanup-pr-caches.test.cjs` passed 22. Pinned Actionlint/ShellCheck and `git diff --check` passed.
- No additional Maven invocation, scanner job or summary runner is added. The existing backend runner executes millisecond-scale POM policy tests; full Actionlint is not triggered merely by a POM update.
- Existing workflow concurrency and independent Gitleaks execution are retained. The rejected serialization, aggregate gate and custom GitHub-expression interpreter were removed; CodeQL and the live ruleset remain unchanged.
- Hosted verification of this working tree is pending. Local success cannot prove budget availability, deployed behavior or GitHub-required-check semantics.
