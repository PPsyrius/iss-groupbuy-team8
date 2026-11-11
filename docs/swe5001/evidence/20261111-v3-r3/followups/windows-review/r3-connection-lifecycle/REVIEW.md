# R3 TCP lifecycle and pinned k6 behavior

**Finding: R3 already warmed approximately 1,000 namespace TCP connections during startup and then overwhelmingly reused them. There is no evidence of a broad five-minute k6 connection renewal. The static cold-establishment failure remains a real discriminator, but is not established as the cause of R3. R4 remains on hold.**

## Counter coverage correction

The cited **605** ActiveOpens delta is exactly **1268 − 663**, from sample **1** at **10:39:45.667105 UTC** to sample **119** at **11:09:16.920241 UTC**. It omits the first 15.7 seconds after container start and therefore most startup connections. The first row is **10:39:30.652205**, not 10:39:30.667; `.667105` belongs to the row at **10:39:45**. Do not interpret 605 as the run's total connection attempts or evidence that fewer than 1,000 sockets existed.

| Event/sample | UTC | Namespace ActiveOpens | Namespace CurrEstab |
| --- | --- | ---: | ---: |
| Actual generator start | 10:39:29.954941565 | Not sampled | Not sampled |
| First control row, index 0 | 10:39:30.652205 | 1 | 1 |
| Second control row, index 1 | 10:39:45.667105 | 663 | 661 |
| Third control row, index 2 | 10:40:00.680322 | 1005 | 1001 |
| Last in-generator control row, index 119 | 11:09:16.920241 | 1268 | 1000 |
| Actual generator finish | 11:09:30.971145972 | Not sampled | Not sampled |
| First post-finish row, index 120 | 11:09:31.934293 | 1271 | 0 |
| Last control row, index 122 | 11:10:00.581446 | 1271 | 0 |

- First-to-last sampled delta is **1270**, with **1271** absolute cumulative ActiveOpens at the end; the first already contains one setup open. Peak observed CurrEstab is **1001**. The series remains around 1,000 established sockets through the failure interval. These are actual namespace socket counters, independent of the HTTP-interval reconstruction, not a per-VU or physical/WAN packet capture.
- The control container shares the generator namespace, confirmed by existing stopped-container metadata and the sealed runner source. Its script snapshots counters **before** launching two fresh sequential curl processes per approximately 15-second row. Their connections count in ActiveOpens; row timestamps are not exact control-completion timestamps. Both controls already succeed in row 0; their last successful row is 119.
- There are 246 control invocations across 123 rows, including six post-generator resolver failures. During generator lifetime there are 240 scheduled fresh-process control invocations. Exact attribution of every namespace open to k6 versus control address fallback/attempts is unavailable; do not simply subtract all invocations as if each were exactly one IPv4 open. Nevertheless, total growth after initial establishment is far too small to show wholesale 1,000-connection replacement.

## One-pass raw request evidence

The 2,623,435,228-byte sealed raw file was read once, read-only, in Docker Python 3.13.8 with **network none**. SHA-256 is **094ff696abaf913853bbbc7545d705db0bbce3ca027bc8c5ae3eddd48aac5bf1**, matching the sealed audit. The frozen adapter's strict ordered eight-point pairing was reused: **725,925 HTTP batches**, comprising **725,924 browse requests plus one setup request**. All **10 repeated completed timestamp/tag keys** were retained; there was no timestamp deduplication. All seven complete metric means match the original summary within 1e-6 ms. The three current perf sources also match their sealed source-lock hashes.

| Completion minute UTC | Positive connecting | Positive TLS | Status-zero requests |
| --- | ---: | ---: | ---: |
| 10:39 | 1001 | 1001 | 0 |
| 10:40–10:47, every minute | 0 | 0 | 0 |
| 10:48 | 3 | 3 | 2044 |
| 10:49 | 6 | 6 | 2966 |
| 10:50–10:52, every minute | 0 | 0 | 1542 total |
| 10:53 | 4 | 3 | 632 |
| 10:54 | 1 | 1 | 560 |
| 10:55–11:09, every minute | 0 | 0 | 685 total |
| **All requests/statuses, including setup** | **1015** | **1014** | **8429** |

- **Only 14 positive-connect intervals occur after startup**, and only 13 positive TLS intervals. First later positive-connect completion is in second **10:48:44**; last is at **10:54:00.267464**. No broad renewal spike appears around the approximately five-minute boundary or first slowing.
- Of **25,620 HTTP requests over 800 ms**, **25,612** have both connecting and TLS timing zero; only eight have positive intervals. Of **8,429 status-zero requests**, **8,426** have both zero, three have positive connecting and two positive TLS. Thus completed fresh-handshake intervals cannot explain the bulk of slow/error requests.
- **Zero timing is not by itself proof of reuse.** Pinned tracer source records connecting/TLS only when successful completion callbacks exist; failed/cancelled attempts can produce zero, reused connections explicitly zero the intervals, and abandoned or retried dials are not counted one-for-one. Positive timing counts are not SYN counts, nor a complete socket identity/lifecycle trace. Combined with the namespace series and low post-start ActiveOpens growth, the evidence favors established-connection stalls rather than mass renewal.
- Earliest inferred slow-request start is **10:45:00.036818**, about **330 seconds** after container start. Earliest inferred status-zero request start is **10:48:02.825920**; first zero-status completion is **10:48:12.826062**, and last is **11:01:07.473789**. These are request timing reconstructions, not captured packet or exact socket-start timestamps. The separately reported **11:00:30 Spot stopping** event is a late confound, not an explanation for the earlier degradation.

## Pinned runtime and source behavior

The existing public image was exported locally without executing k6. Parsing `/usr/bin/k6` Go build information gives **Go 1.25.7**, **module v1.6.1+dirty**, **golang.org/x/net v0.49.0**, **linux/amd64**, CGO disabled, and VCS revision **2ac2bb560e70b33735692642231c8bd53acbe4fd**. This matches the v1.6.1 source commit. The binary reports **vcs.modified=true**; retain that provenance caveat rather than claiming the packaged binary is a byte-for-byte pristine source build. Binary SHA-256 is recorded in `runtime-build-info.json`.

| Question | Pinned source evidence | Consequence |
| --- | --- | --- |
| Is one HTTP transport shared across all VUs? | `internal__js__runner.go:128–205,218–238` creates a new dialer and HTTP transport inside each `newVU`; each VU's state references its own transport. The resolver reference is shared from the runner. | HTTP/2 multiplexing does not collapse all VUs onto one global connection pool. |
| Reuse between requests/iterations? | `lib__options.go:299–305`; runner sets `DisableKeepAlives` from `NoConnectionReuse`, and closes idle connections after an iteration only if `NoVUConnectionReuse` is true (`runner.go:963–965`). Neither option is set by locked workload source or the allowlisted actual container environment. | HTTP keep-alive and reuse across iterations remain enabled. |
| Client idle timeout? | Runner constructs a transport literal and leaves `IdleConnTimeout` zero. Vendored HTTP/2 `transport.go:3350–3362` inherits that zero; zero means no idle timeout (`:150–154`). Go 1.25.7 `net/http/transport.go:220–224` agrees. | The **90-second `http.DefaultTransport` idle timeout does not apply** to this separate literal. No fixed idle expiry is configured here; peer/middlebox limits can still exist. |
| TCP keepalive or maximum age? | Runner's base `net.Dialer` has `KeepAlive:30s` and dial timeout 30s (`:94–97`). No maximum connection age is set in the inspected transport paths. Workload requests have their own 10s deadline. | TCP keepalive probes are not HTTP connection retirement, and not a 30s or five-minute maximum age. Do not confuse dial timeout with the request timeout. |
| DNS TTL and refresh? | `lib__types__dns.go:27–32`: default **5m**, random address selection, prefer IPv4. `lib__netext__resolver.go:83–104` refreshes lazily when `LookupIP` sees expiry; `dialer.go:59–64,158` invokes resolution for a dial. | Expiry neither starts a periodic timer nor closes an existing connection. A new dial after expiry can refresh DNS; a reused connection does not need to redial for TTL. |
| HTTP/2 response timeout? | Vendored HTTP/2 `transport.go:1373–1418,1663–1710` has stream cancellation/reset paths. | A request timeout need not destroy and recreate its TCP connection. Persistent sockets across many request errors are not contradictory. |
| What does the VU gauge count? | Arrival executor's `ramping_arrival_rate.go:515–549` dispatches to an available VU and increments active-VU count around the iteration function. | An iteration includes body parsing/checking and other work, not just an HTTP interval. Preallocation is not a socket count; a sampled VU gauge is not exact active HTTP or a connection identity. |

Actual stopped-container metadata has no `K6_NO_CONNECTION_REUSE`, `K6_NO_VU_CONNECTION_REUSE`, `K6_DNS`, `GODEBUG`, `K6_BATCH` or `K6_BATCH_PER_HOST` overrides. No full environment was printed or retained. Official reference snapshots in `official-source/` come only from grafana/k6 at the pinned commit/tag and golang/go at go1.25.7; no unverified third-party explanation is used.

## Interpretation and next discriminator

1. **Static matrix:** the 1000-VU/60-RPS case truly established 1,000 connections cleanly; 1000/300 had more opens and SYN retransmits; 100/600 confined startup degradation with no request errors. This supports sensitivity to establishment rate/retries in that static experiment, not a universal failure from holding 1,000 connections or from VU allocation alone.
2. **R3:** measured startup establishment was already gradual and clean, and the large later error episode occurred with almost no new completed handshakes. A client-forced five-minute renewal or recurring 1,000-socket SYN burst is **not supported**. DNS expiry coinciding approximately with elapsed time is not causal evidence; the first R3 stage boundary is also around five minutes and changes the offered-rate slope.
3. **Still possible:** load-dependent degradation on existing TCP flows, shared host/backend/offload/RF/AP/gateway handling, or server/edge flow behavior. A small replacement/retry episode may be an effect rather than a trigger. No DNS-query history, per-socket identity, HTTP/2 GOAWAY/frame trace, host per-process connection history, AP counter or packet capture was present to prove the component.
4. **Windows corroboration has a different scope:** existing host-wide IPv4 counters span **10:40:02–11:11:52**, with **3,334 more ActiveOpens and 146,066 retransmitted segments**; established count is around 1,024–1,043 in most load samples. They include all host processes, miss the pre-start baseline and extend after the generator. Namespace retransmits are only 58, but Docker Desktop's shared networking path and different counter scopes mean that this does **not** absolve the host/access path or locate retransmissions on the wire.
5. **Use only the separately authorized warm-establishment static test.** Verify it actually establishes and retains approximately 1,000 sockets during its 60-RPS warm-up before interpreting the 600-RPS phase. If it removes static errors, that discriminates cold-establishment sensitivity in the static case; it does not by itself explain or fix R3, whose existing socket pool was already warm. If it fails, pure cold establishment is insufficient. Do not reduce acceptance VUs, change timeouts/DNS/reuse settings or resume R4 based solely on that test. No additional traffic or test was run by this unit.

## Receipts and limitations

Primary outputs are `lifecycle-analysis.json`, `namespace-counter-series.json`, `namespace-counter-projection.json`, `positive-connection-seconds.json`, `windows-host-counter-projection.json`, `runtime-build-info.json`, `runtime-options-projection.json`, and `review-receipt.json`. The final counter projection's request-completion tallies were synchronized from the completed one-pass result after collection; no second raw scan occurred. The original raw file, sealed campaign, existing diagnosis outputs, host/cloud/Git settings and repository contents were not modified. Only the expressly authorized offline Python container was created; it had network disabled, read-only input mounts, no capabilities and a read-only root filesystem. k6 itself was not executed. No new connectivity/load probe, capture session, host Node/Java process or elevated action was used.
