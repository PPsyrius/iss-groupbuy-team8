# Trivy image database cache benchmark — 2026-10-08

## Scope and reproduction

Local Docker Desktop 29.8.1 exposed 16 CPUs and 15.4 GiB RAM. The scanner was Trivy 0.74.0, pinned as `aquasec/trivy@sha256:62b1e65e8869bc4b4c6aa4fa2b21595256c7c2f6018a9d9ad61caf87187c1969`. The target was the pre-existing local `blocbuy-upstream-check:ci` image, ID `sha256:7d7c119af03e1db6ed84f0b7bb55f432f7ee8f9fdf43d76da7356053824589d3`; its source SHA was not established, so these results are a representative performance benchmark, not application verification of the checked-out `38b30a7b` commit.

```powershell
python -B .github/scripts/benchmark-trivy.py `
  --image blocbuy-upstream-check:ci `
  --output-dir "$env:LOCALAPPDATA/Temp/opencode/trivy-benchmark-20261008-new" `
  --iterations 3 --archive-cache
```

The script requires an existing local image and a new output directory. It creates uniquely named Docker volumes, scans through the local Docker socket and removes only its own volumes/temporary container afterward. Logs, JSON reports and the optional archive remain outside the repository. Scanner/image installation and image build/pull are excluded. Every scan retains database freshness checks, `scanners=vuln`, `severity=HIGH,CRITICAL`, `ignore-unfixed` and exit code 1 for findings; neither database updates nor Java scanning were skipped. All 15 scans across the two invocations returned exit 0 with zero gated findings.

## Measurements

| Scenario | Run A | Run B | Meaning |
|---|---:|---:|---|
| Empty cache, full scan | 99.272 s | 103.913 s | Includes OS/Java DB download and image analysis |
| DB-only warm cache, median of 3 fresh runners | 5.262 s | 4.985 s | No prior image-analysis cache; relevant to CI jobs |
| Full-cache repeat scan, median of 3 | 0.497 s | 0.452 s | Same-runner reuse, not a CI fresh-runner forecast |
| Zstd level 3 archive creation | Not measured | 9.212 s | Local CPU/filesystem cost; no upload |
| Archive extraction | Not measured | 9.509 s | Local CPU/filesystem cost; no download |
| Scan after archive extraction | Not measured | 5.395 s | Normal freshness checks still enabled |

Run B's database directories occupied approximately 2.78 GiB uncompressed; the zstd archive was 1,031,451,606 bytes (0.961 GiB). Its log timestamps attribute approximately 14 s to the OS DB download and 85 s to the Java DB download, making Java DB reuse the dominant opportunity. The copy-only benchmark improved scan wall time by 94.7–95.2%, but that excludes actual cache transfer/extraction. The more relevant archive extraction plus scan cost was 14.904 s, before transfer and GitHub service overhead. Initial DB metadata had OS `NextUpdate=2026-10-08T07:38:55Z` and Java `NextUpdate=2026-10-10T01:10:24Z`; older restored data must still be refreshed by Trivy when due.

## Cost interpretation and decision

Using Run B, warm restore beats its 103.913 s cold scan only if transfer plus service overhead is below approximately 89 s on equivalent hardware. Pure-transfer models, not observed GitHub timings, are:

| Effective download rate | Transfer + local extraction + scan | Difference from Run B cold |
|---|---:|---:|
| 100 Mbit/s | 97.4 s | 6.5 s faster |
| 250 Mbit/s | 47.9 s | 56.0 s faster |
| 500 Mbit/s | 31.4 s | 72.5 s faster |

Actual GitHub runners have different CPU/network characteristics and per-job minute rounding. A shared baseline also costs download, compression and upload on its producer, so several consuming scans may be needed to amortize publication. No net runner-minute or dollar saving was measured locally. A cache miss or an expired DB can still download fresh data; no scan gate is relaxed.

Decision: seed one isolated daily image-DB cache during an already-running trusted default-branch backend coverage job, not an unconditional scheduled warmup. A lookup-only hit skips installation and bulk restore on subsequent producer calls. The producer restores a preceding compatible baseline on a new-day miss, downloads/checks both databases, validates their payload/metadata structure and saves immediately, independently of later scan findings. Seeding is advisory: failure leaves consumers on the normal cold/freshness-checked path and does not block Sonar. PR, merge-group and tag consumers restore only; binary caching remains separate. This removes new per-PR image-DB writes without assuming the DBs are always fresh or that all Trivy caches are globally shared.

Post-merge verification must compare producer and consumer cache summaries, restore/save/scan durations, rounding-adjusted total runner time and cache inventory. Retain the policy only if repeated consumption amortizes its producer/transfer cost. [The runbook](backend-pipeline.md#actions-storage-controls-2026-10-08) owns the current controls; this page records the dated evidence.

## Fresh-image revalidation after PRs #428 and #429

Revalidated branch `chore/actions-storage-optimization` at `9dfa14cc2a9b99135eddf790283671826c5cac78`, which includes both backend merges. With Java 21.0.12 and Docker available, `backend/mvnw.cmd --batch-mode --no-transfer-progress clean verify` passed all seven modules in 5 min 7 s: Surefire reported 1,398 tests and Failsafe 231, zero failures/errors and one skipped integration test. Spotless, Checkstyle (zero violations), SpotBugs and ArchUnit gates passed; the JaCoCo aggregate was generated. Checkstyle emitted non-blocking warnings; this is not a claim of warning-free output.

Built the resulting JAR with `docker build --file backend/Dockerfile.ci --label org.opencontainers.image.revision=9dfa14cc2a9b99135eddf790283671826c5cac78 --tag blocbuy-storage-check:9dfa14cc backend`. The inspected image ID was `sha256:9880798b835c44b29c347bb92ca0714e96a256d344c2a65a0b8542d315b1c713`. Repeated the script above with `--image blocbuy-storage-check:9dfa14cc`, three iterations and archive timing; private outputs are under `trivy-benchmark-20261008-9dfa14cc` in the approved OpenCode temporary directory.

| Scenario | Fresh-image result |
|---|---:|
| Empty cache, full scan | 101.782 s |
| DB-only warm scan, median of 3 | 4.516 s |
| Full-cache repeat scan, median of 3 | 0.456 s |
| Zstd archive creation | 9.368 s |
| Archive extraction | 6.723 s |
| Scan after extraction | 4.676 s |
| Extraction plus scan, excluding transfer | 11.399 s |

All eight scans exited 0 with zero gated findings and freshness checks enabled. The archive remained 1,031,451,606 bytes (0.961 GiB); DB-only scan reduction was 95.56%. On this local hardware, transfer/service overhead must be below 90.383 s to beat the cold scan, before amortizing producer costs. These results corroborate the earlier performance range on a source-traceable image, but do not measure GitHub transport, billing, deployed acceptance or application latency benefits from either backend merge.

Revalidation also passed 22 Node tests, 10 Python tests, pinned Docker actionlint, parsed PR/deployment handoff checks and six actual Docker publication-guard cases. No frontend code changed, so frontend tests were not rerun. Historical storage figures above and in the sprint log remain dated snapshots, not assertions of current usage.
