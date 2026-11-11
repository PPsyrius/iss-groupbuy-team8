# Warm-establishment follow-up — one diagnostic case, R4 HARD HOLD

**Outcome: gradual establishment retained the connection pool but did not prevent high-rate failure. Initial connection/SYN bursts are not necessary for the observed disruption. Exact component remains unisolated; this is neither an R4 fix nor acceptance evidence.** The executed case is preserved without retry, including instrumentation failures described below.

## Executed experiment

One k6 process, one `ramping-arrival-rate` scenario, preAllocatedVUs=maxVUs=1000: 60 RPS for 30 seconds, a validated zero-duration jump to 600 RPS, then 45 seconds at 600 RPS and up to 10 seconds grace. The only load operation was the original static-index GET, responseType `none`, timeout 10 seconds; no commerce/API/proxy/origin load or additional load targets. Image pinned to `grafana/k6:1.6.1@sha256:a5ad6bc089a08d77c3ec49f3db8c6fa7a148e4073efcac44c675dbaf3568d8e1`, 8 CPUs/8 GiB, default bridge and HTTP negotiation, no GODEBUG override. The added thresholds were tautologies used solely to expose phase submetrics, not acceptance gates. No host settings, elevation/security changes, AWS writes, fixtures or V3 gates were changed.

Actual scenario times from k6's `diagnostic_scenario_start_ms`: start **12:13:17.986Z**, rate jump **12:13:47.986Z**, arrivals stop **12:14:32.986Z**, 2026-11-11. Docker state receipts include actual creation/start/end; the container exited 0 around 12:14:43Z with zero interrupted iterations. Exit 0 does not signify success. Native controls span 15-second baseline, both phases, grace and 15-second post interval; all diagnostic workers/load have finished. Unrelated containers were left untouched.

## Phase results — do not substitute overall averages

Counters below are exact console integers; human-readable timing values are rounded because the JSON exporter failed. Requests are phase-assigned by **iteration start**, so high-phase requests include completions during grace. Phase count divided by arrival duration is not exact in-window completion throughput; k6's displayed per-phase rates use the whole-test denominator and must not be called 600-RPS phase throughput.

| Metric | Warm: 60 RPS / 30 s | High: 600 RPS / 45 s |
| --- | ---: | ---: |
| Requests | 1,799 | 25,984 |
| Requests / configured phase seconds | 59.97/s | 577.42/s |
| Errors | 0 | **2,961 (11.3955%)** |
| Timeouts / EOF | 0 / 0 | **2,934 / 27** |
| HTTP/2 responses / HTTP/1 responses | 1,799 / 0 | 23,023 / 0 |
| Status-zero / no-response protocol | 0 | 2,961 |
| VUs used | 1000 | 1000 |
| First-ever VU requests | 1000 | 0 |
| Requests from VUs already used in warm | — | **25,984 / 25,984** |
| HTTP duration mean / median | 11.48 / 9.68 ms | 1.55 s / 38.54 ms |
| HTTP duration p95 / p99 / max | 18.11 / 49.81 / 187.25 ms | **10 / 10 / 10 s** |
| Waiting mean | 11.30 ms | **1.55 s** |
| Connecting mean, including zeros | 4.38 ms | 2.33 ms |
| TLS mean, including zeros | 4.44 ms | 1.27 ms |
| Blocked mean | 9.32 ms | 3.21 ms |
| Requests with positive connect timing | 1000 | **32** |
| Positive connect median / p95 / max | 5.67 / 10.53 / 1010 ms | 1.06 / 4.21 / 7.02 s |
| Positive TLS median / p95 / max | 7.29 / 12.24 / 22.35 ms | 607.77 ms / 5.81 s / 7.45 s |
| k6 `data_received` count, rounded console | **20 MB** | **191 MB** |
| k6 `data_sent` count, rounded console | 1.9 MB | 2.3 MB |

Overall drops were **1013**; exact per-phase drop attribution was not captured. The 27,783 total completed requests plus drops sum to 28,796, not exactly the arithmetic 28,800 target, reflecting scheduler/boundary behavior. All 2,961 request failures match raw warning counts (2,934 timeouts, 27 EOF). The HTTP/2 protocol counter is for received responses only; no-response errors are not evidence of another negotiated protocol.

**Byte-count limitation:** actual k6 `data_received` metrics are retained only in human-formatted form: warm 20 MB, high 191 MB, overall 211 MB. Exact integer k6 bytes are **unavailable**, not reconstructed or invented. Dividing the rounded high count by 45 seconds gives only an approximate 34 Mbps count/time proxy, including grace completions; it is not precise wire bandwidth. Raw namespace `IpExt.InOctets/OutOctets` provide separate exact sampled counters, not a substitute for k6 bytes or a full NIC packet capture.

## Pool retention and TCP phase samples

Before the jump, all 1000 VUs had executed and the last warm TCP sample contained **1000 established connections, 1000 active opens and zero SYN retransmits**. Socket inode sets were captured without addresses; their overlap checks retention, not just an equal count of replacement connections.

| Approximate seconds into high phase | Retained warm established socket inodes | Current established | Cumulative active opens | Cumulative SYN retransmits |
| --- | ---: | ---: | ---: | ---: |
| +0.85 | **1000 / 1000** | 1000 | 1000 | 0 |
| +4.88 | **1000 / 1000** | 1000 | 1000 | 0 |
| +9.92 | **1000 / 1000** | 1000 | 1000 | 0 |
| +19.96 | 996 / 1000 | 996 | 1001 | 0 |
| +29.99 | 974 / 1000 | 997 | 1026 | 46 |
| +40.02 | 973 / 1000 | 1004 | 1033 | 67 |

The first additional active-open sample is 12:14:07.942Z, about +19.96 seconds; the first nonzero SYN-retransmit sample is 12:14:08.957Z, about +20.97 seconds. Native disruption begins well before either. Thus **failure cannot be explained solely by failure to retain the warmed pool or an initial burst of new k6 TCP connections at the jump**. Later EOF/reconnections do occur, but are not required to precede the initial native failures.

Last warm namespace sample: active opens 1000, established 1000, SYN retransmits 0. Last high sample: opens 1035, established 1005, SYN retransmits 72. Last grace sample: opens 1036, established 1002, SYN retransmits 74, one connection-attempt failure. Samples do not land exactly on boundaries, represent namespace-wide cumulative counters, and are not atomic across `/proc` reads. Socket inode retention identifies surviving sockets, not their application-level throughput or path health. Raw sample start/end times and inode sets are in `tcp-samples.jsonl`; derived checks are in `analysis.json`.

High-phase first-to-last sampled IPv4 octet deltas are 240,279,431 received and 11,927,147 transmitted between samples beginning +0.85 s and +44.04 s; these are incomplete-window network-namespace totals with different accounting than k6. Warm sampled deltas are 24,505,247 received and 3,053,889 transmitted. These are explicitly **not** precise phase-boundary wire totals.

## Native-control correlation

Same low-rate controls as the prior matrix: gateway and external ping at approximately 1 Hz each with a 600-ms reply timeout; native IPv4/HTTP1.1 regional STS and non-AWS HTTPS each at no more than approximately once per two seconds, 2-second connect/3-second total timeout, body discarded, no redirects/retries. Destination addresses/SSID/MAC are omitted. STS 302 is a successful transport response. HTTPS failures retain curl exit codes independently of received status, including quoted `"000"` JSON.

| Probe-start interval | Gateway failures | External ping failures | Regional HTTPS failures | Non-AWS HTTPS failures |
| --- | --- | --- | --- | --- |
| Idle before | 0/15 | 0/15 | 0/8 | 0/8 |
| Warm | **0/30** | **0/30** | **0/15** | **0/15** |
| First 10 seconds of high | **2/10** | **7/10** | **2/5** | **2/5** |
| Entire high | **15/45** | **29/45** | **10/22** | **11/21** |
| Grace | 0/10 | 0/10 | 0/4 | 0/5 |
| Idle after | 0/15 | 0/15 | 0/7 | 0/7 |

First high-phase regional/non-AWS failing probes start at 12:13:48.589Z / 12:13:48.609Z (about +0.60 s), external ping failure at 12:13:49.657Z (+1.67 s), and gateway failure at 12:13:55.723Z (+7.74 s). All occur while samples still retain the original 1000 sockets and no extra opens/SYN retransmits. First k6 timeout warning is 12:13:58Z, consistent with its 10-second timeout. Native probes recover during grace, despite outstanding k6 requests draining. Counts assign by probe start, not completion; crossing probes are explicitly counted in `analysis.json`. Native timeout lengths differ from k6, so failure percentages are not directly comparable.

## Instrumentation failures and interference — not a fully instrumented result

1. **JSON summary export failed:** `handleSummary` accesses `options.scenarios.static_path.stages`, which was undefined in that context, and logs `TypeError: Cannot read property 'stages' of undefined`. The original executed script and exception are preserved; no `original-summary.json` exists. k6 falls back to its phase-specific console summary. Exact counters are usable; timing/byte formatting has lost precision. The export failure occurs after load completion, not at the rate jump. No load rerun was performed to repair this.
2. **CPU/cgroup sampler never ran during load:** six `Start-ThreadJob` workers were submitted under the default five-worker throttle; the sixth resource worker was queued until shutdown, when `Stop=true`, and produced no samples. A Completed job state did not establish that it had sampled. Consequently **CPU/cgroup headroom was not measured for this case**; no prior case's headroom is substituted as proof. Docker states verify configured quotas/no OOM kill, not actual headroom. TCP namespace and all four native-control workers did run and retained raw receipts.
3. **Other offline Docker work:** the first controller invocation aborted before starting any controls/load because an unrelated Blender container was running. It disappeared before read-only inspect/wait; that container's exit code is unknown. The one actual case then began with Docker idle. A separate `python:3.13.8-slim` offline analyzer started at 12:14:16.706Z (high +28.72 s), and Blender work is observed after the k6 container exited. Final Docker was therefore **not idle**; these unrelated containers were not modified/stopped. Native failures and k6 timeouts began before the retained Python start event, so that specific analyzer cannot explain their onset, but exclusive resources for the entire run cannot be claimed. Retrospective daemon event history is bounded and not exhaustive. See `resource-blocker*.json`, `other-container-events.json`, and the unaltered run receipt.

## Comparison and conclusion

| Separate diagnostic | Requests | Errors | Drops | HTTP duration p95 |
| --- | ---: | ---: | ---: | ---: |
| Prior cold 1000 VUs / 300 RPS / 45 s | 13,347 | 2842 (21.293%) | 153 | 10,000.42 ms |
| Prior cold 1000 VUs / 600 RPS / 45 s, default HTTP2 | 26,255 | 3087 (11.758%) | 745 | 10,000.07 ms |
| This warmed high phase, 1000 VUs / 600 RPS / 45 s | 25,984 | 2961 (11.395%) | 1013 overall only | ~10 s |

The cold 300-RPS case had thousands of SYN retransmits, but this warmed high phase fails with the original sockets retained and no initial SYN retransmits. **The earlier leading establishment-burst explanation must be narrowed:** initial SYN bursts are not necessary; an interaction of high request activity across the larger connection pool with the shared host/Wi-Fi/path remains plausible. Low-rate established connection count alone was already insufficient in the prior matrix. This single result does not distinguish active-flow fan-out, request/packet bursts, driver/AP queues, local Docker transport interaction or other shared-path mechanisms, and it identifies no safe mitigation.

Comparisons are unrandomized and noncontemporaneous, the cold 300 case has a different rate, this full session is 75 seconds plus grace rather than a cold 45-second session, and instrumentation/resource interference differs. The high-phase duration matches the cold 45-second arrival period, but the cases are not equivalent trials or statistical evidence of improvement. Exact bytes/full headroom requirements are incomplete, as explicitly recorded above.

**Do not suggest warming or altering V3 on this basis.** Actual V3 already has a five-minute 40→200 ramp and its transport pool/workload can behave differently. R4 remains HARD HOLD; final 1000 preallocated/max VUs, 600 RPS, full 30 minutes and all acceptance/fixture gates remain unchanged. This unit generated one authorized follow-up load case, no automatic retry, no final V3 launch, and no host/AWS changes to revert. Repository/source before/after hashes match; original campaigns and previous matrix receipts remain untouched.

Evidence: `original-k6.log`, `created-state.json`, `started-state.json`, `finished-state.json`, `control-*.jsonl`, `tcp-samples.jsonl`, `analysis.json`, the original scripts, and `retained-files.json` (SHA-256 manifest for this new folder only). `analysis.json` records successful consistency checks **and** failed completeness checks; do not describe verification as wholly passed.
