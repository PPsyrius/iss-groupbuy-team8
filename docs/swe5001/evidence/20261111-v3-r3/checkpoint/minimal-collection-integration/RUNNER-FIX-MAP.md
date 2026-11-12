# R4 runner — offline-copy proposal / 20:53 SGT checkpoint

**No installation, original-runner edit, cutoff extension, launch authorization, HOLD change, Docker operation or cloud call.** `runner.original.ps1.txt` is an exact byte copy of the shared inert R4 runner. `runner.proposed.ps1.txt` is review text, not an installed `.ps1` script. The proposal also refuses a campaign root other than the exact original R4 root, so its temporary review location cannot serve as a launch directory. Do not execute it.

| Bytes | SHA-256 |
| --- | --- |
| Original shared runner and exact offline original copy | `8719136a46e8bcb732c920fcbe7cfb66b5f74230e2dfe0636a635bb5bdcbdae8` |
| Proposed offline text | `b66b14d0693104f7ccf8442683e322bc984be587d6c22a41c2f30f4f36aa3e04` |

`runner-review-receipt.json` records **zero PowerShell parser errors**, identical original/shared bytes, unchanged line-7 HOLD guard, three proposed authority checks, no stale `/r3` mount and no hardcoded old/new deadline. This is static parsing/text/hash inspection only: **no functional test and no runner execution**. A parser pass is not launch readiness.

## Concrete delta against original line numbers

| Original location | Proposed offline correction |
| --- | --- |
| Line 1 | Keep `ApproveOneV3`; add `LaunchDeadlineUtc`, `LaunchAuthorityPath`, `ApprovedAuthoritySha256` string parameters with **no default deadline/authority**. Missing parameters fail closed rather than retaining an expired timestamp or assuming 21:30. |
| Line 6 | Keep `$r3=Split-Path $PSScriptRoot -Parent`. For the shared original path `.../v3-20261111-distributed-r4/tooling/runner.ps1`, this resolves to **R4**, not R3. Preserve the local name to avoid gratuitous renaming; add an explicit explanatory comment. |
| Line 7 | Preserve the original HOLD guard **exactly and at the same line**. Additional authority checks also recheck HOLD before proceeding/start. Neither approval parameters nor external authority override a present HOLD file. |
| New block after line 7 | Add one `Assert-LaunchAuthority` function and call it before any Git/Docker action. Require exact reviewed campaign/run, approved future UTC launch cutoff, actual runner hash, actual source-lock hash, explicit boolean bounded authority, and independently pinned external authority bytes. Refuse if executing from the offline review folder. |
| Lines 37 and 57 | Replace each fixed `2026-11-11T12:00:00Z` comparison with `Assert-LaunchAuthority`. This rechecks deadline, HOLD, authority bytes and source binding at the existing checkpoints, including immediately before generator start. It does **not** supply or extend a cutoff. |
| Line 40 | Change only campaign bind target `/r3` → `/r4`; retain `readonly`. The existing observer argument `/r4/load` now resolves to the intended host `<R4>/load`. Repo mount `/repo` and collector-helper path stay unchanged. |
| Line 44 | Change only `$r3Mount` target `/r3` → `/r4`; retain read/write mode, because the unchanged sidecar writes `/r4/network-controls.jsonl`. |
| Line 59 | Keep `/r4/tooling/network-controls.py`, existing namespace/network/resource limits and corrected `$r3Mount`. No probe target/rate or sidecar behavior is changed. |
| Lines 61 and 72 | Correct human-readable `V3 r3` labels to `V3 r4`; no status/exit semantics changed. |

No workload/environment/HTTP/error/drop/latency/arrival/task-count gate, image identifier, resource allocation, policy, native-status handling, restoration behavior, no-retry rule, completion schema or acceptance claim was changed by this runner copy. The post-collection wrapper remains the separate plan in `INTEGRATION.md`, not silently appended to the load runner.

## Complete path-reference inspection

- **Host campaign paths:** all original `$r3` uses (`load`, `prechecks`, `source-lock.json`, `sessionFiles`, execution source-lock hashing) correctly refer to the R4 parent computed from the original runner's location. No references to the sealed R3 campaign directory were found in that runner.
- **Observer container:** `/repo` maps the checkout read only; `/repo/infra/swe5001/scripts/load-capture.py` loads its sibling `cap-pools.py`; corrected read-only `/r4` maps the R4 campaign and resolves `/r4/load` for `require_observer`. No credentials are needed by that specific local heartbeat check.
- **Preflight/k6 containers:** `/scripts` maps `perf/k6`; `/results` maps `<R4>/load`. `PREFLIGHT_PATH`, `SUMMARY_PATH`, raw stream path, `/scripts/lib.mjs` and `/scripts/browse.js` are coherent and unchanged. The preflight-validation results mount remains read only.
- **Generator monitor:** `/scripts/monitor-generator.mjs`, `/results/generator.jsonl` and the existing Docker socket mount are internally coherent and unchanged. This review did not use the socket or start a container.
- **Network sidecar:** corrected writable `/r4` exposes both `/r4/tooling/network-controls.py` and its exclusive-create `/r4/network-controls.jsonl` output; `--network container:<generator>` is unchanged. Its `/proc/net/snmp` read is local to that network namespace. No sidecar was executed.
- **Other inert R4 surfaces:** `r4-operator.ps1` already mounts the campaign at `/r4`; R4 supplemental/readiness/scaling-margin/fresh-prerequisite/ALB tools use `/r4` consistently. Do not route the offline acceptance wrapper through this broad operational wrapper.
- **Not a stale filesystem mount:** the `r3/AWSLogs/...` S3 prefix in R4 `alb-timing-projection.py:14`, `overlays/runtime-alb.tf:95`, and `offline-controls-test.py:15` is consistent across copied logging declaration/reader/test. Do **not** blindly rename it to `r4`: it is a remote storage prefix, not the campaign mount. Whether it matches future deployed logging configuration still needs separately authorized readback; this unit did not query AWS or alter logging.
- **Existing test update required:** `offline-controls-test.py:29` currently demands the literal expired timestamp. It would reject this future proposal for that reason. Before integration, change that assertion in a separately reviewed future copy to require the explicit authority-bound deadline path and negative deadline/HOLD cases, retaining every unrelated workload/no-retry/source assertion. No original test was changed or run here.

## Explicit future authority contract — proposed, not approved or created

The existing inert `authority.json` says the prior scope was by 20:00 and that load is not authorized by that receipt alone. It deliberately does **not** satisfy this proposal. No replacement authority file, concrete future cutoff, sample green approval or launch command is supplied.

The new external authority, if separately authorized and its contract reviewed, must contain typed strings `runLabel`, `campaignDirectory`, `launchDeadlineUtc`, `approvalReference`, `runnerSha256`, `sourceLockSha256`, plus literal booleans `launchAllowed:true`, `noRetry:true`, `noTeardown:true`. Run label/root must match exactly; deadline must exactly equal the independently supplied `LaunchDeadlineUtc` parameter in `yyyy-MM-ddTHH:mm:ssZ` form and still be in the future; hashes must match actual reviewed bytes. Read/hash/parse failure yields fixed `LAUNCH_AUTHORITY_REFUSED` without echoing private contents.

`LaunchAuthorityPath` must resolve lexically outside the campaign and checkout, and the proposal hashes the same byte buffer it parses. **Path exclusion and matching hashes do not authenticate a human or protect against reparse-point/concurrent-writer attacks.** `ApprovedAuthoritySha256` must be supplied by a separately reviewed operator-controlled launcher/configuration with established access control and immutable custody, not chosen by the evidence submitter. That canonical root/custody is still unresolved. This parameter interface is only an integration hook, not proof that authorization exists.

The source-lock digest is approved independently through that authority; its actual `files`/`sessionFiles` checks remain in the original flow. A future source lock must bind the reviewed runner copy and complete executable inventory; the base commit is not full source identity. No old lock or receipt may be edited to accommodate the proposal. Authority and its exact hash must be retained with any later authorized campaign evidence through an explicitly reviewed path; this copy does not alter execution-receipt schemas.

The deadline means **latest permitted launch instant**, not workload completion time or a promised start target. The user's 21:30 request supplies neither a concrete approved cutoff nor authority to remove HOLD. The owner must explicitly authorize a future window and independently clear HOLD after all prerequisites; this unit does neither.

## Required review before any use

1. Owner approves the new authority semantics, latest launch instant, one-attempt scope, and the separately controlled trust root; HOLD remains until explicitly cleared by that owner.
2. Reviewer approves the exact copy/hash, complete source/runtime pins and mount map; refresh the future source lock without mutating historical records.
3. Main authorizes offline functional tests after the sole active-flow experiment. Cover missing/wrong/self-supplied authority, malformed flags/strings, wrong runner/lock/run/root, expired/mismatched deadline, HOLD at entry and appearing before start, and no default 21:30. Test authority function with mocked clock/files/child dispatcher; no live load/cloud call is needed. Updating the old literal-timestamp test must not weaken its unrelated controls.
4. Independent review also covers unchanged residuals: pre-start observer freshness, sidecar failure after generator start, mutable-tag runtime pins, Docker creation before the final deadline check, monitor's existing 30-second startup window, and no automatic teardown on refusal. This small proposal does not claim to resolve those operational behaviors or the incomplete acceptance boundary.

**25-minute safe-integration ETA remains unsubstantiated.** Concrete path corrections are straightforward; authority/custody decisions and negative functional verification are still prerequisites, not paperwork that can be skipped to hit a requested target.
