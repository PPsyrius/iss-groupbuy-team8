# NFR: security - repair APT and Brotli vulnerabilities in backend images

- Quality attribute: security (NFR-03).
- Context: [PR #750's Trivy image scan](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42232842597/job/127947569355) reported CVE-2026-109518 in `apt`/`libapt-pkg6.0t64` and CVE-2026-111497 in `libbrotli1`; these are inherited Ubuntu packages, not documentation changes.
- Scope: keep `backend/Dockerfile` and `backend/Dockerfile.ci` synchronized on Java 21 / Ubuntu Noble; upgrade only the affected packages until upstream catches up; retain the existing Trivy gate.

## Acceptance criteria

- [ ] Both runtime images contain `apt` and `libapt-pkg6.0t64` ≥ `2.8.4ubuntu0.1`, and `libbrotli1` ≥ `1.1.0-2ubuntu0.1`.
- [ ] Both Dockerfiles build successfully and EC2-profile health returns UP with a non-root runtime user.
- [ ] The full image scan reports no fixable HIGH/CRITICAL findings, without suppressions or relaxed gates.
- [ ] The owning CI documentation, NFR-03 evidence and daily sprint log record the repair, verification scope and upstream-removal condition.

Issue draft only; filing and board changes require user approval. See the [daily log](../log/2026-11-06.md#2026-11-06---ppsyrius---repair-apt-and-brotli-image-vulnerabilities) for observed local results; acceptance above remains unticked until issue filing/review.
