# Persistent HTTP load collection

Refs #458/#591 / NFR-64. This is the collection correction after the [failed 10 November campaign](../agile/sprint-04/log/2026-11-10.md), not authorization to start an environment or another test. Keep the existing request rates, duration, error/drop/latency gates and separate linearity criterion. Original failures remain unchanged.

## 10 November decisions

- Replace detached CloudShell observation with a foreground process on the existing persistent generator workstation. AWS documents that running processes do not prevent CloudShell idle shutdown. No new EC2 is required or provisioned.
- Keep observation read-only. Restore the floor through a separate short, explicit operator action; do not rely on the observer's survival to restore runtime. SIGKILL or host loss cannot run a finally block.
- Require at least 55 minutes of observed temporary-credential validity, an initial real ECS sample and a heartbeat no older than 30 seconds before preparation. Recheck before publishing preflight; require at least 36 minutes of observation remaining for the five-minute start allowance, unchanged 30-minute workload and post-run sample. Preparation failure attempts the already-approved floor restoration and retains its result.
- Preserve incomplete data: read failures, interrupted collection and gaps >45 seconds cannot produce a successful collection receipt. Archive partial evidence even when final metric retrieval fails. A complete collector receipt is not a passing load result.
- Classify connection-limit versus pool-wait log events in memory before saving metadata; never retain application message bodies or exported credential values. Record observer/helper hashes separately from the driver commit.

## Before any later authorized session

Use reviewed, committed tooling and record its actual `TEST_SHA`; do not label these changed scripts/driver with the older release SHA. Verify tool paths match that commit even if unrelated report drafts remain in the working tree. Keep `IMAGE_SHA` independently bound to the reviewed release. The new preparation helper refuses a missing tooling SHA and a missing/stale observer. The legacy detached `observe` command is disabled.

The operator must first configure/authenticate a temporary AWS CLI profile normally on the persistent workstation. Credentials are not requested in chat. A separately authorized operator profile needs prepare/restore permissions; the observer needs only ECS/CloudWatch/log reads. A static or otherwise unknown credential expiration is refused. The `--persistent-host` flag is an operator assertion: do not use a container in CloudShell to bypass this requirement. Keep the workstation awake.

Docker runtime tested locally: `public.ecr.aws/aws-cli/aws-cli:2.39.7@sha256:73f81e380c7974c9f03ac7ccd520ef080f17f443e10582f024c828b970ac8b73` provides AWS CLI 2.39.7 and Python 3.9.25. Offline tests also use Python 3.13. The following are future-session examples, **not executed against AWS**. Use an existing results parent outside Git, mount only the selected AWS profile directory, and share the same results directory across terminals.

```powershell
$repo = (Get-Location).Path
$parent = 'C:\private-results' # Existing private results parent, outside the checkout.
$aws = Join-Path $HOME '.aws'
$sha = (git rev-parse HEAD).Trim() # Only after the changed tooling is committed/reviewed.
$image = 'public.ecr.aws/aws-cli/aws-cli:2.39.7@sha256:73f81e380c7974c9f03ac7ccd520ef080f17f443e10582f024c828b970ac8b73'
$common = @('--rm', '--entrypoint', 'python3', '--mount', "type=bind,source=$repo,target=/repo,readonly", '--mount', "type=bind,source=$parent,target=/results", '--mount', "type=bind,source=$aws,target=/root/.aws,readonly", '-e', 'AWS_REGION=ap-southeast-1', '-e', "TEST_SHA=$sha")
```

1. On an authorized running session, start observation in terminal A. `v3-new` must not exist; choose a new run name for each separately authorized attempt. Wait for the first-sample message and retain this process in the foreground.

   ```powershell
   docker run @common -e AWS_PROFILE=swe5001-observer $image /repo/infra/swe5001/scripts/observe-load.py /results/v3-new --persistent-host
   ```

2. In terminal B, prepare the approved one-task reference. This changes only the approved minimum/count; it does not send load. If observation stops or becomes stale during preparation, no ready preflight is published and restoration is attempted.

   ```powershell
   docker run @common -e AWS_PROFILE=swe5001-operator $image /repo/infra/swe5001/scripts/load-capture.py prepare /results/v3-new
   ```

3. Check the heartbeat again, then use the [k6 guide](../../perf/k6/README.md) with the new preflight, original summary/raw JSONL, named generator and native resource monitor. Start within five minutes. Monitor both processes; preserve failures rather than restarting. Normalize **all** actual successful scale-out activities, including CPU-guard activities when explicitly enabled, without treating unhealthy-task replacement as target-policy scale-out.
4. After the measurement and full observer window finish, independently restore/verify the normal floor. Reauthenticate normally if the operator profile expired; do not rerun the collector or load. The command preserves existing scaled counts above two, verifies minimum two/maximum six, writes a separate receipt and creates a new archive. It does not prove settled count, health or two-AZ placement.

   ```powershell
   docker run @common -e AWS_PROFILE=swe5001-operator $image /repo/infra/swe5001/scripts/load-capture.py restore /results/v3-new
   ```

## Opt-in performance mitigation — not deployed or proven

The retained ALB alarm evaluated the 19:51–19:53 minute counts at 19:56:38 UTC; commerce had already saturated and begun dropping requests. A new **default-off** `commerce_cpu_scale_out_enabled` guard provides a separate `AWS/ECS` CPU signal: one 60-second average at/above the proposed 70% threshold adds one commerce task, with a 60-second cooldown. It never scales in, is disabled during paused startup, and leaves the request policy, commerce maximum six and notification maximum four unchanged. Seventy percent is a planning headroom choice, not a calibrated capacity or acceptance threshold. This is an explicit extension beyond the earlier ALB-only scope; it requires reviewed plan/application and AWS validation before a future performance claim.

The unchanged default remains ALB-only. To review the opt-in, use `commerce_cpu_scale_out_enabled=true` and, if needed, an integer `commerce_cpu_scale_out_threshold` from 1 to 99 in the authorized runtime plan. Do not run Terraform during measurement or silently persist GitHub settings. With the guard deployed, k6 must explicitly receive `CONFIRM_CPU_HEADROOM=true` and the reviewed `CPU_HEADROOM_THRESHOLD` (default 70); fresh preflight must bind the exact alarm, positive-only adjustment, account, cluster and commerce service. Unknown extra policies remain rejected. Neither the guard nor a 600-RPS ramp proves the separate 80%-linearity criterion.

## Local Docker verification capture — 10 November

For Windows-hosted Docker verification, redirect Maven/Jansi output onto the existing named Linux build volume (`.local-verification-logs/`) and copy the completed log to the host only after Maven exits. Direct writes to the Windows bind mount delayed Surefire's exit acknowledgement and lost JaCoCo dumps despite exit zero; TCP reproduced it. Keep every log/test and the original POM, plugins, timeouts and coverage gates. Check for forced-JVM messages and inspect the fresh aggregate rather than trusting Maven success alone. Recreate parent repository fixture links and `../contracts/openapi.json` in each disposable verification container, retaining daemon-visible Testcontainers bind paths. The frozen pre-rebase verification bundle and capture diagnostics are preserved report-side, not on this fixes branch. That result does not certify the rebased tree, establish an AWS root cause or prove performance acceptance.
