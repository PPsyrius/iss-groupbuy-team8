# AWS validation preparation — 13 November 2026

Refs #458/#459/#448. **Generator preparation only, not a V4/V5/identity/V7 application run.** The owner approved recreating the retired generator, installing/checking tools, then stopping it before sleeping. CLI SSO authenticated the expected account/operator role. No application environment was started or inspected, no application traffic/fault/provider action was performed, and no workflow was dispatched.

## Prepared generator

The [safe receipt](generator-preparation.json) records the fresh AWS readback at **02:30 SGT on 13 November**: one `t3a.2xlarge` (eight vCPUs, 32 GiB), **stopped**, encrypted 40 GiB gp3 root disk with delete-on-termination, no SSH key or inbound rules, TCP-443 egress, required IMDSv2/hop limit one, and no assigned public IPv4 after stop. The instance was bootstrapped and tested through SSM, then explicitly stopped; an EC2 stopped waiter and independent state/disk/security/IAM readbacks followed. No stop/start persistence test was run.

Only a new temporary generator role/profile/boundary/security group and one instance/root disk were created. Existing default network resources and archive-bucket controls were not changed. The role allows SSM managed-instance channels and only this preparation's new bootstrap/results object prefix; the boundary excludes Parameter Store reads and application control permissions. Six IAM simulations denied parameter reads, application task/service changes, instance start and role passing. This is simulation/configuration evidence, not a live secret-denial or application fault test.

The host has Docker 25.0.14, Python 3.13.18, Git, jq, the pinned k6 1.6.1 image, AWS CLI, Node 24.22.3 and PostgreSQL 18 fixture images. A metadata-isolation systemd unit was enabled for subsequent boots; the original host receipt's persistence flag is a configuration assertion, **not** a tested stop/start result. Source and fixture-result archives were written only to a new private prefix and readback/hash-verified; retained V3 results/snapshots were neither restored nor overwritten. Resource identifiers and SSO/cache material remain outside Git.

Preparation used **standard CPU credits**. AWS's Singapore Linux shared-tenancy on-demand list price was **US$0.3776/hour** for running compute; public IPv4, disk, transfer and existing archive storage are separate. The stopped generator incurs no running compute charge, but its disk and retained evidence remain billable. No invoice or new account spending total was measured. Do not assume the runtime `down` workflow manages this separate host.

## Tooling and verification

Working branch: `test/459-aws-session-preparation`, based on `ae3a32a3943b7952abdf71e5c85ffd27a24bf704`. All new preparation changes remain uncommitted/unstaged. The EC2 source snapshot is identified by its own archive/per-file hashes, **not** by the parent commit alone. It includes the browse/observer/poison and preflight changes at packaging time; the later local operator Dockerfile, identity-state report and optional valid-control payload addition still need transfer with the final reviewed tooling. The staged partial source snapshot is not a clean full execution checkout.

| Check | Observed result | Boundary |
| --- | --- | --- |
| Node 24: `node --test perf/drills/lib.test.mjs perf/k6/lib.test.mjs perf/k6/scaling.test.mjs` | **62 passed** locally and on the new generator | Configuration, route/digest/two-task guards, exact steady budgets, zero-error/drop analysis, offline poison files and existing V2/V3/V4 regressions |
| Docker: `node --test perf/k6/integration.test.mjs perf/k6/seed.integration.test.mjs` | **10 passed, one opt-in full-duration V2 skipped**, locally and on the generator | Real pinned k6 against disposable internal-network HTTP fixtures; PostgreSQL seed/report contracts. No BlocBuy/AWS target traffic |
| `python -B -m unittest discover -s perf/drills -p '*_test.py' -v` | **Six passed**, host/Python 3.13 generator and local operator container | Read-only allowlist, missing observations, zero identity tasks and CloudShell refusal |
| `test-http-preflight.sh`, `test-v4-expiry-seed.sh`, `test-v4-expiry-tools.sh` | **18 + 19 + 18 shell contracts passed**, locally and on the generator | Stubbed cloud/task calls; no AWS acceptance |
| `docker build -f perf/drills/operator.Dockerfile -t blocbuy-swe5001-drill-operator:20261113 perf/drills` | Passed; AWS CLI 2.39.7 and checksum-verified Terraform 1.16.4 | Local image only; no credentials baked in, no remote init/plan/apply |
| `bash infra/swe5001/scripts/test-identity-drill-report.sh` | **11 shell contracts passed** in network-disabled local operator container | Fixed read-only/bounded db-init command, input guards, exact marker and unknown-versus-zero event count |
| `node --test perf/drills/report.integration.test.mjs` | **One passed** against disposable PostgreSQL 18 with actual migrations | Fixed state SQL resolves real schema; missing fixtures and unbound event receipts remain distinguishable. Direct test SQL is query-contract evidence, not payment/outage acceptance |

Two initial verification mistakes were retained privately and corrected without weakening a security or application gate: the IAM checker expected only `implicitDeny`, although five safe denials were `explicitDeny`; the identity-report test initially lost psql backslashes in a JavaScript string, corrected in the test harness. No permission was expanded and the report SQL was not changed to work around that fixture error.

Final local combined run of the six Node test files listed above: **73 passed, one opt-in full-duration V2 skipped, zero failures**. The network-disabled operator rerun passed all **66 shell contracts and six Python tests**. File checks found all **184 local Markdown target paths**; the safe receipt's raw fact fields and both private archive hashes match the original readbacks. Gitleaks 8.30.1 found no leaks in the **24 changed/new source files** (checksum-verified scanner, redacted report retained privately); this is not a Git-history scan, private-cache/archive audit or hosted CI result. `git diff --check` passes.

## Morning handoff

Use the [shared AWS plan](../../aws-validation-session-plan.md) and [drill guide](../../../../perf/drills/README.md). The remaining live gates are owner approval for application startup/load/faults/provider actions/teardown, review/signing and transfer of the final tooling, fresh SSO/credential-window checks as needed, current released digests/runtime settings and cleanup-role checks, confirmed alarm recipients and authenticated Sandbox fixtures. Neither #458/#459 nor #448 is complete from this preparation. V6 remains excluded; V8's existing timing table is unchanged.

Exact generator identity, SSO profile location, private artifact paths, source manifest and command/readback receipts are retained in the operator's private preparation folder. Do not publish them or put tokens in a command line. Starting this stopped generator or the application is a later explicit action, not an unattended 09:00 schedule.
