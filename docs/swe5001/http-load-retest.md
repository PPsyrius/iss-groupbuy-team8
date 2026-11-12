# SWE5001 post-merge HTTP load-test plan

Refs #458/#591 and NFR-64. Prepared on 9 November 2026; reconciled after the 10 November failed campaign and branch rebase. This is a future-session plan, not authorisation to rerun or evidence that the current branch passes. Retain the original October and November failures.

**10 November outcome:** after [successful promotion #770](evidence/20261109-promotion-770/README.md), the operator approved a released-image distributed session, pool cap five, temporary one-task reference and one 30-minute V3 campaign without assistant-dispatched teardown. [The campaign failed](../agile/sprint-04/log/2026-11-10.md): 2.4219% errors, 89,175 drops, p95 4,191 ms, exit 99. Partial observations recorded automatic 1→4; neither full-run stability nor settled restoration was proven. Scheduled down 42722097786 later removed runtime/logs. These are historical observations, not current runtime state, a fresh V2 baseline or linearity acceptance. Any new session needs separate approval and the [corrected persistent collector](http-load-collection.md).

## Start gate: merge and release first

Do not start a billable session or send load while preparing this document. Require passing gates for the selected post-rebase revision, review, merge and the intended release before a session. Historical verification or an older release does not validate later fixes/tooling.

- [x] Historical release: #769 fixed the immutable tag collision; #770 at `93984f61` completed scans, publication, startup, smoke, release and teardown in run 42653347818 ([record](evidence/20261109-promotion-770/README.md)). #757 and its failed retry remain failed evidence, not current blockers requiring the same fix again.
- [ ] The intended application fixes and tooling have passed fresh gates after rebase, been reviewed/merged and, where applicable, released. Record the actual tooling `TEST_SHA`, released `IMAGE_SHA`, promotion URL and every service digest; the historical #770 release does not contain this branch's fixes.
- [ ] No promotion, environment operation or queued teardown can overlap the session. Agree a no-promotion window with the team; do not merge into `prod` during measurement.
- [ ] Account `402178232802`, Region `ap-southeast-1`, GitHub environment `swe5001-demo`, and target `https://d3lvq02o2e7tcn.cloudfront.net` are verified. Never load-test shared SWE5006 dev.
- [ ] A short billable session, generator and explicit cleanup responsibility are authorised at execution time. Verify the current scheduled/queued teardown and leave enough time to collect evidence; the earlier schedule destroyed runtime before follow-up. A historical session approval is not permission for a new startup, load run or teardown.
- [ ] Use clean reviewed checkouts for the operator and generator. Record `TEST_SHA` separately from `IMAGE_SHA`; never label uncommitted tooling with its parent SHA.

### 11 November preparation checkpoint (no AWS operations)

- Implementation PR [#784](https://github.com/PPsyrius/iss-groupbuy-team8/pull/784) merged into `dev` as `b911ea63795c5ddb7db1f7918f7f7af03a2745ff`. That is the available reviewed tooling source, not the released SWE5001 image identity. Existing backend verification remains attributed to its recorded source; no new backend result is claimed here.
- Promotion [#785](https://github.com/PPsyrius/iss-groupbuy-team8/pull/785) merged at 02:37:23 UTC on 11 November as `3a893036d70d028b29ae13ebf820283571c1ae00`. Its three release checks passed in [run 42943926130](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42943926130); deployment [42947289936](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42947289936) subsequently passed every stage, including release, teardown and evidence. Fresh session preflight must still bind the actual deployed digest to this release; do not reuse #770's `IMAGE_SHA` for the fixes.
- After that completed promotion and empty queried queued/running workflow lists, the owner explicitly authorised a monolith V2 startup. [Session 42955188339](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42955188339) was dispatched on `prod` with `action=up`, `digests=release`, `topology=monolith`; its up job passed startup/smoke in 13m27s. Runtime remains up for the session. This authorises neither load nor teardown and does not establish fresh route/digest preflight or performance acceptance.
- Docker became available. Fresh offline checks on committed tooling inputs at `b911ea63` passed: 340 Python tests (four opt-in skips), 50 Node tests, 16 shell contracts and 100 tests on the pinned AWS CLI/Python runtime. Initial runner/image failures are retained in private preparation logs; no test was removed or weakened. This is not a fresh full backend build or AWS performance result.
- Report/evidence restoration conflicts were reconciled separately from executable code. The working checkout still contains staged report drafts. Preserve them, then obtain a clean reviewed checkout in this same repository before execution; do not bypass the clean-checkout guard or create an additional worktree. No AWS profile, credentials, current pool values or runtime placement were inspected during preparation.

The execution defaults remain unchanged: establish the new V2 baseline first, then distributed V3 with an explicitly approved one-task reference, `v4_demo_enabled=false` and the optional CPU guard off unless separately reviewed and authorised. Use the persistent workstation observer with sufficient credential validity and observation time; restore the two-task floor independently and save evidence before authorised teardown. Generator allocation, alarm-subscription confirmation and the session/cleanup owner remain execution-time gates, not assumptions satisfied by this checkpoint.

The operator prefers short copy-paste CloudShell checks rather than configuring workstation SSO. That does not waive V3's persistent authenticated observer requirement. Keep V2's short observation window separate from the 40-minute V3 collector; browser activity is not a guaranteed credential or recording keepalive. No SSO profile or AWS credential value was created or read by this preparation.

Read the [environment session runbook](runbook-environment-session.md), [HTTP driver guide](../../perf/k6/README.md) and [V3 analysis guide](../../perf/k6/SCALING.md) before execution. Those remain the canonical commands and safety controls; this page sequences the fresh campaign.

## What we will measure

| Phase | Target and workload | Required evidence |
| --- | --- | --- |
| New V2 baseline | New released monolith image, one stable task; ten one-minute plateaus from 10 to 200 requests/s, 70% list / 30% detail GETs. | Full per-plateau request counts, client p95 <500 ms, errors <1%, zero drops; task/revision stability and generator resources. If all rates pass, 200 RPS remains a tested lower bound, not a located saturation knee. |
| Updated subnet check | New distributed release after AD-15 is deployed; no load running. | [Subnet checklist](subnet-verification.md): actual application/data ENIs, routes, security groups and separate connectivity checks. Use the collector only from a reviewed checkout containing it. |
| Connection budget | Running distributed session, before HTTP load. | Actual database limits, all services' effective pool sizes and maximum task counts, owner/operator/reserved/headroom allowances; calculator passes. No assumed live values. |
| New V3 scale-out | New distributed commerce image, one stable starting task with scaling enabled; 30-minute ramp/hold/down schedule up to three times the reviewed V2 candidate, at most 600 RPS with the current driver. | Delivered workload, client/error/drop metrics, bracketing task/revision observations, actual scaling activities, ALB throughput/latency, database errors/connections, and generator resources. |

V2 and V3 use their own image digests and preflights. A passing baseline for an old monolith is not a fresh baseline for the newly released image, and a monolith baseline is not distributed-task capacity.

## Generator preparation (before paying for runtime)

- Use Node 24, Docker and the pinned k6 1.6.1 image from the driver guide. Run its local tooling/fixture checks first; label those results as tooling verification, not application performance.
- Prefer a sized generator near Singapore AWS to reduce the unresolved workstation/edge/network constraint from October. A new EC2 generator needs an explicit instance, access, cost and cleanup plan before creation; none is provisioned by this document. Do not run k6 in resource-constrained CloudShell.
- Record generator location, CPU, memory, Docker allocation, network path, image digest and free disk for raw samples. The historical 30-minute raw stream was about 2 GB; leave additional space for the fresh stream and diagnostics.
- Review VU allocation before execution. The old 400-VU ceiling was insufficient in the observed run. Calculated sizing illustration: 600 requests/s × 0.8 s active time ≈ 480 concurrently busy VUs before headroom; tail latency can require more. This is not proof that 480 VUs suffices. Choose and record a reviewed allocation within the driver's current maximum of 1,000, pass both VU variables explicitly, and monitor actual headroom.
- Preserve request rates, duration, response checks, timeouts and acceptance gates. Do not lower load, omit samples, filter out failures or silently increase latency/error limits to obtain a pass.

## Execution sequence (only after every start gate passes)

### 1. Verify identity and workflow state

In the authenticated operator shell:

```bash
aws sts get-caller-identity --query Account --output text --region ap-southeast-1
```

Require the expected account number. Do not paste credentials or full runtime configuration. Check for active/queued jobs, not just the last successful run:

```bash
gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-env.yml --limit 20
```

```bash
gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-deploy.yml --limit 20
```

Resolve concurrency with the operator rather than cancelling jobs blindly. Confirm the promotion's release and cleanup before following the session runbook's `up` procedure.

### 2. Baseline the new monolith release

Start the authorised `monolith` session with released digests using the session runbook. Verify one stable task, no active scaling, the deployed digest/source and the normal smoke checks. Use a new private results directory outside the checkout.

Follow the [V2 preflight and runner commands](../../perf/k6/README.md#v2-baseline). Start within five minutes of preflight, capture task identity/counts before/during/after and collect generator resources. Analyse the fresh summary:

```bash
node perf/k6/calibrate.mjs "$results/summary.json" > "$results/calibration.json"
```

Stop if calibration is invalid. Retain the first result even if a later corrected run passes. Review any candidate before applying it to distributed scaling; do not silently persist a new GitHub variable. Save evidence, then destroy and verify absence before switching topology.

### 3. Prepare the new distributed release

Follow the session runbook for an authorised distributed session and wait for messaging readiness and all service smoke checks. Require confirmed alarm subscriptions for the evidence session; a testing opt-out is not equivalent. Complete subnet verification before load, using the separately prepared checklist.

Run the connection-budget report and calculator from the driver guide before measurement; the report starts a billed one-off task. If the full maximum-task budget does not fit, stop for a reviewed configuration fix rather than ignoring it or silently reducing task maxima.

Review the V3 starting condition: one running/desired commerce task, active bounds 1–6, calibrated per-minute request target, all suspension flags false and `v4_demo_enabled=false`. A minimum of one does not itself reduce an existing scaled task count. Use the documented operator procedure and record any deliberate scaling action separately from automatic scale-out. Restore the normal two-task floor afterwards.

### 4. Capture and run V3

Follow the [V3 preparation guide](../../perf/k6/SCALING.md#v3-budget-and-observations-before-load) and [runner instructions](../../perf/k6/README.md#v3-and-evidence-closure). Use a fresh private results directory and run label, the reviewed VU allocation, new route/digest preflight and continuous raw k6 output.

Start the ECS observer before HTTP and retain a post-run observation. Start the generator monitor before its named k6 container and confirm the first resource sample. Preserve k6's actual exit status and original summary, including an all-run latency diagnostic failure. Do not run Terraform, a promotion, a messaging drill or another workload concurrently.

Retain original successful scaling activities and normalize their actual timestamps as documented; never infer an event time from a screenshot. After capture, run:

```bash
node perf/k6/analyze-v3.mjs \
  "$results/summary.json" \
  "$results/samples.jsonl" \
  "$results/observations.jsonl" \
  "$results/scale-outs.json" > "$results/analysis.json"
```

The analyzer requires complete delivered counts and zero drops, errors <1%, and client p95 <800 ms outside the three minutes after each scale-out. Its exit 0 covers its HTTP/scale-out gates only; `acceptanceComplete` still requires separate review of achieved-throughput linearity, database/generator headroom, deployment stability and dashboard evidence. ALB latency is not client end-to-end latency.

### 5. Keep linearity separate from the ramp result

The existing acceptance criterion is achieved throughput at four tasks at least 80% of ideal scaling from one task. Merely offering 600 RPS or observing three tasks does not prove it. At the historical target of 200 RPS per task, 600 RPS does not guarantee a fourth task. Review a valid one/four-task comparison design before attempting to close this criterion; do not lower the policy target or count a manual scale-up as automatic scale-out to manufacture it. If that evidence is missing, report the HTTP/scale-out results and the remaining limitation separately.

### 6. Save evidence and tear down

Save the deployment/source/digest identities, UTC interval, workload/VU settings, pre/post snapshots, budget, raw-data location, original summaries/exit codes, analysis, task/scaling timeline, ALB/client graphs and generator observations. Record pass/fail/not-verified per criterion, with the cause and corrective action for any repeat. Keep secrets, cookies, recipient addresses, raw state/plans and private application payloads out of committed evidence.

Restore the normal demo floor and verify both commerce task AZs separately if that post-test placement is being claimed. Then use the session runbook's `down` and independent absence check; also terminate and verify removal of any separately authorised generator resources. A failed test still requires evidence preservation and teardown. Do not rely solely on nightly cleanup.

Only after reviewing the fresh evidence should §4.1 and NFR-64 gain a new dated result. Preserve the October records as historical results, not overwritten failures. V4 queue-burst/drain and other AWS failure drills remain separate work.
