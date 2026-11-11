# Static-only rate/concurrency matrix — diagnostic, not V3 acceptance

**Outcome: connection-establishment pressure is the leading discriminator; exact host/Wi-Fi/AP/router component remains unisolated. R4 HARD HOLD remains.** All three authorized cases ran once, sequentially on the same laptop Docker Desktop between 2026-11-11 11:58:02Z and 12:01:00Z; no commerce API requests, extra generator/server, cloud changes, host setting/security changes, or retries occurred. Baseline/inter-case/post controls ran throughout 11:57:46Z–12:01:17Z. Original campaigns and repository sources were not modified by this unit.

## Scope and reproducibility

- `static-rate-concurrency.js` is an expressly disclosed diagnostic adaptation of `../static-concurrency.js`: only fixed rate/VU count is parameterized, with additional observational metrics. Every iteration is one GET to the original static index with response body discarded, 10-second timeout, constant-arrival-rate executor, 45-second arrival period and up to 10-second grace. No redirects or other load targets were added by this adaptation.
- Exact image: `grafana/k6:1.6.1@sha256:a5ad6bc089a08d77c3ec49f3db8c6fa7a148e4073efcac44c675dbaf3568d8e1`; each container has 8 CPUs and 8 GiB, default bridge network, no GODEBUG/protocol override. Docker VM reports 16 logical CPUs and 16,510,504,960 bytes RAM (`docker-runtime.json`). Default bridge options are separately retained.
- `run-matrix.ps1` is the single foreground controller: independent low-rate native ping/HTTPS workers span baseline, cases and recovery; blocking `docker wait` completes each case before the next. No tool polling or concurrent load cases. Each unique case folder preserves one `original-summary.json`, `original-k6.log` and actual created/started/finished Docker states; containers remain exited, not removed. Docker had zero running containers before and after (`matrix-receipt.json`).
- Native controls: one gateway ping and one external ping per second maximum, 600 ms reply deadline; regional STS and non-AWS HTTPS GET each approximately once per two seconds, 2-second connect/3-second total timeout, native Windows curl, IPv4/HTTP1.1, bodies discarded, no redirects followed. STS 302 is a successful transport response, not a failed health check. Destination addresses, SSID and MAC are omitted from control receipts. Quoted `"status":"000"` was validated as JSON before launch; transport failures preserve curl exit codes even if HTTP status 200 arrived before a body timeout.
- Repository `perf/k6/browse.js`, `lib.mjs`, `scaling.mjs`, and the original static diagnostic have matching before/after SHA-256 values in `analysis.json`; no perf acceptance gate was edited by this unit. Existing user/main-session Git changes were left untouched. All new files are confined to this folder. `analyze-matrix.py` is offline-only.

## Load results

| Fixed VUs / configured RPS | Requests | k6 reported RPS | Errors | Timeouts / EOF / other | Dropped arrivals | HTTP/2 / HTTP/1 / no response protocol | HTTP duration p95 / p99 / max (ms) | Receive Mbps |
| --- | ---: | ---: | ---: | --- | ---: | --- | --- | ---: |
| 100 / 600 | 26,780 | 594.98 | 0 (0%) | 0 / 0 / 0 | 221 (0.819%) | 26,780 / 0 / 0 | 13.55 / 41.68 / 1,255.77 | 37.13 |
| 1000 / 60 | 2,701 | 60.01 | 0 (0%) | 0 / 0 / 0 | 0 | 2,701 / 0 / 0 | 18.97 / 47.73 / 100.13 | 4.84 |
| 1000 / 300 | 13,347 | 242.72 | 2,842 (21.293%) | 2,831 / 9 / 2 | 153 (1.133%) | 10,505 / 0 / 2,842 | 10,000.42 / 10,000.92 / 10,002.90 | 14.48 |

The two other errors are `http2: client conn could not be established`; all 2,842 failing requests have status zero. All received HTTP responses were HTTP/2 and status 200. No-response protocol counts must not be interpreted as HTTP/1 or an actual negotiated protocol. All containers exit 0 because this diagnostic intentionally has no acceptance thresholds; exit 0 does **not** mean the case passed. Logs report zero interrupted iterations.

k6 reported RPS/receive Mbps use k6's elapsed denominator, including grace where present. `count/45` is 595.11, 60.02 and 296.60, respectively, but includes requests completing in grace and is **not** exact in-window completion throughput; the 300-RPS case ran approximately 55 seconds in k6. Scheduled counts are 27,001, 2,701 and 13,500 due to executor boundary timing; drops are divided by requests plus drops. Raw counts, full latency/blocked/connect/TLS/iteration trends and derived denominators are retained in `analysis.json`.

## Connection establishment and resource headroom

| VUs / RPS | VUs ever used | Requests with positive connect timing | Positive connect median / p95 / max (ms) | Positive TLS p95 / max (ms) | First-request p95 / max (ms) | Last sampled active opens / established / SYN retransmits |
| --- | ---: | ---: | --- | --- | --- | --- |
| 100 / 600 | 100 | 100 | 1,020.16 / 3,020.06 / 3,030.38 | 971.76 / 6,136.92 | 3,162.48 / 7,192.54 | 100 / 100 / 72 |
| 1000 / 60 | 1000 | 1000 | 5.75 / 15.47 / 83.47 | 13.60 / 69.12 | 56.80 / 135.81 | 1000 / 1000 / 0 |
| 1000 / 300 | 1000 | 1227 | 146.71 / 7,043.55 / 9,181.63 | 4,638.95 / 9,795.49 | 10,000.90 / 10,001.57 | 1396 / 1081 / 2871 |

The low-rate case genuinely exercised every VU and held 1000 established TCP connections in the final sample: **preallocating 1000 VUs or merely holding 1000 connections is not sufficient to reproduce failure**. The 300-RPS case built connections faster and developed reconnection/SYN retransmission pressure: last sampled namespace totals include 99 connection attempt failures, 2,872 retransmitted TCP segments and 2,871 SYN retransmits. The 100-VU case shows bounded startup degradation (72 SYN retransmits, slow first connects and 221 drops) but no continuing request failure. Namespace counters are sampled cumulative values, not exact final totals or a packet capture; a positive connect timing count does not include every failed SYN attempt.

Resource sampling found substantial generator quota headroom, not quota exhaustion: sampled peak CPU was 50.77%, 25.84%, and 41.35% of one core (6.35%, 3.23%, and 5.17% of the eight-core limit). Peak cgroup memory was 119.11, 300.28, and 383.10 MiB of 8192 MiB. All sampled CPU throttling, memory-limit events, OOM events and full CPU-pressure avg10 were zero; Docker final states show no OOM kills. Docker backend aggregate process CPU peaked at approximately 61.97%, 32.80%, and 50.51% of one core. These snapshots do not prove every host subsystem had headroom: unprivileged host process visibility was limited, and no kernel/NIC/driver/AP telemetry or host-wide packet capture was collected.

## Native-control correlation

Counts below assign each probe by its start timestamp inside actual Docker StartedAt..FinishedAt, including initialization/grace; no case probes completed after its Docker interval. Native controls use much shorter timeouts than k6, so percentages are not directly comparable.

| Interval | Gateway ping failures | External ping failures | Regional AWS curl failures | Non-AWS curl failures |
| --- | --- | --- | --- | --- |
| Idle before, 15 s | 0/15 | 0/15 | 0/8 | 0/8 |
| 100 VUs / 600 RPS | 0/45 | 0/45 | 0/23 | 0/23 |
| Idle after first case, 15 s | 0/14 | 0/15 | 0/8 | 0/8 |
| 1000 VUs / 60 RPS | 0/45 | 0/45 | 0/22 | 0/22 |
| Idle after second case, 15 s | 0/15 | **1/15** | 0/7 | 0/7 |
| 1000 VUs / 300 RPS | **19/55** | **20/55** | **9/27** | **11/26** |
| Idle after final case, 15 s | 0/15 | 0/14 | 0/7 | 0/7 |

All native HTTPS failures return curl exit 28; the non-AWS failures comprise eight status `000` plus three status `200` responses whose transfers still timed out. The final failing case is not merely Docker/k6 having trouble with a specific application endpoint: native Windows gateway ping and unrelated AWS/non-AWS HTTPS degraded at the same time. The isolated external ping timeout in the idle interval is explicitly retained; it is not treated as a perfectly clean entire matrix baseline.

During the failing case, the first external ping timeout starts at 12:00:05.293Z, regional HTTPS failure at 12:00:07.141Z, non-AWS HTTPS failure at 12:00:09.158Z, and gateway timeout at 12:00:12.473Z. k6 starts reporting request timeouts at 12:00:14Z (consistent with its 10-second timeout after case start). Native failures recur throughout the arrival period, not only at launch. Recovery is evident during the grace period: after roughly 50 seconds from Docker start, native probes are clean while outstanding k6 requests drain. Five-second bins, exact failure timestamps, connect/TLS/TTFB/total timings and raw JSONL receipts are retained; resource timestamps denote worker tick start, preceding the subsequent Docker stats/proc reads by roughly a few seconds.

## Findings and limits

1. **Simple sustained request rate or receive bandwidth alone is not sufficient:** approximately 595 completed requests/s and 37.13 Mbps with a 100-VU pool produced zero request failures, whereas a 300-RPS/1000-VU case failed at only 14.48 reported Mbps. Packet rate was not captured directly, so a packet-rate or burst-related mechanism remains possible.
2. **Faster establishment/retry pressure plus the larger pool is the strongest supported trigger:** 1000 connections established gradually at 60 RPS were clean, while 300 RPS produced thousands of SYN retransmits and correlated native path loss. This supports, but does not prove, a connection/SYN-burst-sensitive host/Wi-Fi/AP/shared-path fault or feedback loop. It does not isolate a numerical threshold, distinguish initial SYN loss from later retransmission feedback, or identify a specific component/driver defect.
3. **Not an application-capacity acceptance result:** static traffic alone reproduced broad native disruption. No new origin/API/ALB observations were collected here, and these receipts cannot establish that all earlier V3 failures had the same cause or exclude Docker Desktop as a trigger affecting shared networking. Every successful response still used HTTP/2; this matrix itself is not an HTTP/1 A/B test.
4. Each condition ran once in fixed order for 45 seconds; no randomized crossover, packet capture, connection warm-up intervention, network substitution, host setting change or retry was authorized/performed. Instrumentation adds some overhead relative to the source diagnostic. Exact host/Wi-Fi/AP/router fault isolation remains open; no mitigation is proven.
5. **Do not adopt fewer VUs as a V3 fix.** The 100-VU case also dropped arrivals and is noncomparable diagnostic evidence only. Final V3 must retain preAllocatedVUs=maxVUs=1000, 600 RPS, full 30 minutes and every existing acceptance gate. V3/R4 must not resume based on these results.

Primary receipts: `matrix-receipt.json`, each uniquely named case folder, `control-*.jsonl`, `resource-samples.jsonl`, and `analysis.json`. Worker jobs completed without recorded errors. New receipts remain separate from sealed campaigns; no canonical sprint/NFR/report record was written because this subtask was expressly limited to this temporary folder.
