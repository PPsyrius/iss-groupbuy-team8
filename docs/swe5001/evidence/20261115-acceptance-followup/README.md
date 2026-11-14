# 15 November acceptance follow-up — PR #808

Refs #448/#459/#460. One combined delivery, not an automatic issue closure, new deployment or whole-campaign pass.

- [Qualified normal team-success R2](normal-team-success-r2.md): two real paid settlements and exact production outbox/consumer/recipient effects, with independently reconciled complete initial/repeat buyer APIs. Retains first failure/refund, admin provisioning, two-hour fixture and OAuth/Valkey/cross-date parity limits. The #459 identity fault did not run.
- [Hosted backend CI 43619694588](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/43619694588) passed exact code head **be5e7c41580ba2bbd0fac97a5978d3951278d4fc**, before this documentation publication. That result does **not** verify a later head; the publication commit needs its own required checks.
- Build-quality job **132522374148** ran real hosted JDK 21/Docker-backed Maven verification: downloaded Surefire/Failsafe XML independently totals **241 suites, 2,144 tests, zero failures/errors, two skipped opt-in benchmarks** (2,142 non-skipped tests). Skips are `PerformanceBaselineIT.recordOneHundredUserBaselineAndExpiryLag` and `TeamExpiryShardingBenchmarkIT.recordOriginalSingleFlightAndSixShardSourceThroughput`; do not describe the full run as skip-free.
- Contract-check, SonarCloud Backend including quality gate, and Docker including smoke/Trivy passed. Deployment jobs were skipped, not live acceptance. No new benchmark, passing cloud-drill rerun or topology restart was performed by this publication.

## Selected hosted suites

| Suite | Tests | Failures / errors / skips | Downloaded XML SHA-256 |
| --- | --- | --- | --- |
| OutboxRollbackElasticMqIT | 1 | 0 / 0 / 0 | `ed670c47ac6be3c0043aae834b32b4522789970417d00ebf04f01c843ca16cae` |
| NotificationConsumerElasticMqIT | 3 | 0 / 0 / 0 | `e96cf6857e4f248bf02f31ebebc7ae88318547df8d4b32e5b651e21ecc8630bc` |
| OutboxRelayFlowIT | 1 | 0 / 0 / 0 | `11a16f2a20d4de0dfd3ce4496cdac005bf35027c4d7c79b77800e286e63522c6` |
| TeamExpirySchedulingIT | 10 | 0 / 0 / 0 | `cb590628b86695674891243a967a443869241f3bf2df689e337e33f65bf0271e` |

Rollback coverage proves a real team-expiry transaction rolls back business/outbox state with no ElasticMQ publication; its committed retry delivers one matching event. Duplicate/poison coverage includes the same event delivered three times and poison dead-lettering after five receives while controls proceed. These selected suites total **15 tests with no skips**; they do not replace the qualified live #460 receipts or establish #459 outage behavior.

## Publication and privacy

[Publication manifest](publication-manifest.json) hashes the exact published UTF-8 files, not a claimed private-cloud archive. Raw CI XML and business/API evidence remain retained privately; no cookies, identity/payment/team/event IDs, traces, provider URLs or private paths are published. The private CI review receipt hash is `dcad2b581e27cc120b4af4ac53906c2f04b239b2a1b26c66ffcf38258456c20d`; selected business-receipt hashes are in the R2 record.

Publication checks cover all 50 backend source-artifact hashes, six independent API source hashes, selected/overall CI XML counts, local Markdown links, public byte hashes and sensitive-value screening. These checks are offline evidence validation, not another application test or cloud run. [Same-author daily log](../../../agile/sprint-04/log/2026-11-15.md) and [NFR register](../../../quality/nfr-register.md) retain the work/remaining gates.
