# AWS generator preparation — separate variant, not R4 acceptance

## Decision and scope

After local static-only diagnostics narrowed the trigger without identifying the faulty component, the owner approved a temporary AWS generator as a bypass of the laptop/Wi-Fi path, not a fix or a retrospective pass for r3. This supersedes the same-laptop requirement only for the generator. Commerce remains on ECS Fargate at 1 vCPU/2 GiB, not on a C-series or T-series EC2 host; RDS small, pools, timeouts, images, scaling target 6,000, slow start 120 seconds and 50% CPU guard remain unchanged. Full V3 still requires 1,000 preallocated/maximum VUs, the original 30-minute workload peaking at 600 RPS, one-task start, maximum six tasks and every existing acceptance gate.

An EC2 dry-run for C7i was refused by an explicit organisation-policy instance-family restriction; no instance was created by that attempt. After the owner approved the alternative, one `t3a.2xlarge` launched in Singapore with Unlimited mode, eight vCPUs/32 GiB host memory and the original eight-CPU/eight-GiB Docker allocation. The verified public AWS base price was US$0.3776/hour, excluding Unlimited CPU charges, storage, public IPv4 and transfer/request charges. A 60-minute auto-stop bounds runtime; stopped encrypted storage and retained artifacts still incur charges until separately authorised cleanup. No organisation policy or existing application role/boundary was changed.

The generator uses a separate default-VPC subnet, no inbound rules, HTTPS-only egress, IMDSv2 with hop limit one, an encrypted 40-GiB gp3 root disk, private encrypted evidence storage and generator-only management/artifact permissions. The new permissions boundary excludes broad Parameter Store reads from the managed SSM core policy. Normal SSO sign-in and intended account/region verification passed before provisioning; prior failed/expired sign-in attempts remain preserved privately. The existing 23:00 SGT application safety schedule was not changed.

## Completed static-only diagnostic

The one diagnostic used the pinned k6 image and 1,000 preallocated/maximum VUs at 600 static-root GETs/s for 60 seconds, with the original 10-second request timeout. It made no commerce API requests and is not the full V3 workload. Bootstrap/SSM readiness, actual Unlimited mode, official x86 AMI, encrypted storage and no-inbound readbacks passed before the diagnostic. The auto-stop timer was checked active and its original properties retained privately.

| Check | Observed result |
| --- | --- |
| HTTP requests | 36,001; one boundary arrival above nominal 36,000, within the disclosed diagnostic tolerance |
| HTTP errors / dropped iterations | 0 / 0; explicit drop-summary field retained, not inferred from a missing raw point |
| Protocol / status | All HTTP/2, all 200 |
| HTTP p95 / p99 / maximum | 5.804234 / 55.434110 / 218.783366 ms |
| k6 / runner / resource-monitor exits | 0 / 0 / 0; no OOM |
| Actual running resource samples | 29, with maximum overall sample gap 2.298413 s |
| Preservation | Private S3 upload/download completed; main independently checked 32 artifact-manifest hashes |
| Archive SHA-256 | `b553a9e95d6634cdd2dcb8b7d8861292d126fcf37411a972d56eacdcf8cf919c` |

The diagnostic finished and was downloaded by 21:32 SGT. The first credit/CPU metric read returned only one point per series: positive credit balance, zero observed surplus balance/charges and 12.1941% instance CPU. This is sparse sampled evidence, not proof of sustained headroom or a guarantee of zero later credit charges. Private raw data, endpoint values, identifiers, input configuration, SSO caches, command output and IAM/private operator material are intentionally not copied here.

## Verification and remaining launch gates

The T3a bundle passed 11 offline tests plus Bash/JavaScript syntax, pinned-k6 inspection and 13 asset checksums; its manifest SHA-256 is `50b2e6c53cd1e33595e4a6af9cfeadebefdef1dced5d91eaa8308a36438d3c4e`. The original non-T3a bundle was preserved. The separate implemented collection guard passed 53 offline tests and an independent main rerun of all 53; real normalizer tests use synthetic receipts and analyzer dispatch is mocked. This is not installation, complete end-to-end acceptance or launch authorisation by the guard.

The owner subsequently conditionally authorised exactly one AWS-generator R4 as soon as the static diagnostic, fresh mandatory readiness and reviewed runner/collection controls pass. That permission does not override a failed check or permit retries. **R4 has not started at this checkpoint.** Fresh subnet/routing/settings/budget/source/credential/preflight checks, actual one-task readiness, complete observation and adequate time before generator auto-stop remain required. The local fault remains unresolved, prior campaigns remain failed, and distributed capacity, linearity and recovery are not established by this short static pass. The earlier 21:30 no-go was not retroactively changed.
