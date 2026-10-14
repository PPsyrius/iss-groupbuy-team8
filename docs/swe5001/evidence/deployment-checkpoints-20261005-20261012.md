# SWE5001 historical deployment checkpoints — October 5 and 12, 2026

Archived from [the deployment handoff](../HANDOFF.md) on 2026-10-14 with user approval. The dated assessments below retain their original meaning; relative links were rebased. They are historical evidence, not current deployment instructions or status. Current status: [handoff](../HANDOFF.md); current scheduling: [Sprint 4 calendar](../../agile/sprint-04/plan.md#1-calendar).

## Delivery checkpoint — 2026-10-12

| Deliverable | Date |
| --- | --- |
| SWE5001 code freeze (split and scaling merged into `dev`) | Fri 2026-10-16 |
| Sprint 4 promotion and evidence session | Mon 2026-10-19 |
| SWE5001 rehearsal | Tue 2026-10-20 |
| SWE5001 presentation | Wed 2026-10-21, 16:15–16:55 SGT (confirmed 2026-10-11) |
| SWE5001 written examination | Tue 2026-11-03, 18:30 |
| SWE5001 final report and final `terraform destroy` | Mon 2026-11-16 |

State on `dev` at `3a0f6908`: Phase A complete as recorded above; Phase B not started apart from ADR 0007 (Proposed) and the A1 plan. The SWE5006 dates in the 2026-10-05 table below are superseded: the SWE5006 presentation is on 2026-10-26 and its report on 2026-11-10 ([Sprint 4 plan §1](../../agile/sprint-04/plan.md#1-calendar)).

## Delivery checkpoint — 2026-10-05 (#393)

The Team 8 scheduling notification moves the SWE5001 presentation to **21 October 2026, 16:15–16:55 SGT (UTC+08)**, two days earlier. The submitted proposal is retained as historical evidence; its 23 October presentation and 22 October rehearsal are superseded by the living [timeline](../microservice-refactoring-plan.md#4-effort-estimates-and-timeline) and [presentation checklist](../impl-verification-observability.md). Confirm a rehearsal slot before 21 October. The existing 20 October promotion target now allows one day for recovery and needs a tested fallback recording.

| Deliverable | Current date | Calendar days from 2026-10-05 | Basis |
| --- | --- | --- | --- |
| SWE5001 presentation | 2026-10-21, 16:15–16:55 SGT | 16 | Team 8 notification supplied on 2026-10-05 |
| SWE5006 presentation | 2026-11-03 / 2026-11-04 | 29 / 30 | [Sprint 3 calendar](../../agile/sprint-03/plan.md#1-calendar) |
| SWE5006 final report | 2026-11-10 | 36 | Sprint 3 calendar |
| SWE5001 final report | 2026-11-16 | 42 | [Submitted proposal](../project-proposal.typ), milestone table |

Readiness is assessed from `dev` at `a55afa5` and explicit GitHub reads on 2026-10-05. GitHub now lists `swe5001-plan` and `swe5001-demo`; their protection and secrets settings were not verified, and [#397](https://github.com/PPsyrius/iss-groupbuy-team8/issues/397) remains open. `branches/prod` still returns 404; only `dev-require-review` and `main-no-forcepush` rulesets exist; `swe5001-deploy.yml` has no runs. AWS state was not independently checked.

| Area | Implemented or evidenced | Remaining delivery work |
| --- | --- | --- |
| SWE5006 buyer chain | Quote-aware Checkout, orders, team resolution, local refund/recovery tests, invites and admin APIs are on `dev` | [#223](https://github.com/PPsyrius/iss-groupbuy-team8/issues/223) shared-dev expiry and real Sandbox refund; [#224](https://github.com/PPsyrius/iss-groupbuy-team8/issues/224) deployed crash/replay proof; [#229](https://github.com/PPsyrius/iss-groupbuy-team8/issues/229) authenticated buyer acceptance |
| SWE5006 release and security | CI/contract/security gates and governed deployment code; local 100-VU baseline | [#226](https://github.com/PPsyrius/iss-groupbuy-team8/issues/226) production apply/release/rollback; [#227](https://github.com/PPsyrius/iss-groupbuy-team8/issues/227) origin TLS decision; deployed performance remains unmeasured |
| SWE5001 foundation | Terraform foundation/runtime/edge, monolith ECS topology, smoke suite and release/up/down workflows | Operator bootstrap, release configuration, first successful create/validate/destroy, and a failed-promotion teardown proof ([#376](https://github.com/PPsyrius/iss-groupbuy-team8/issues/376)); a planned promotion date is not completion evidence |
| SWE5001 distributed runtime | Plans describe identity, commerce and notification services | Three service assemblies/platform starter, remote identity/RBAC, outbox/relay, SNS/SQS/DLQ, idempotent consumer and per-service data grants are absent from the audited tree; runtime supports only `none`/`monolith` |
| SWE5001 demonstration | `perf/smoke/smoke.sh` exists | Queue/HTTP scaling, dashboard/alarms, k6 and resilience drill tools, live results and fallback recording; no `perf/k6/`, `perf/drills/` or SWE5001 evidence directory exists in the audited tree |
| Architecture discipline | Existing COLA/DDD rules and backend ADR index govern the monolith | Approve the distributed exceptions and synchronize constraints before implementation; the backend plan's proposed ADR 0005/0006 numbers are already occupied by payment records and must not be reused |

The immediate sequence is operator bootstrap and monolith ECS up/down proof (7 October checkpoint), then the 9 October promotion, service/message/scaling implementation and acceptance, and a full rehearsal before 21 October. The 9 October milestone proves strangler step 0; it cannot prove three-service behavior whose identity assembly is planned for 12–18 October. Existing owners keep their assigned work; the new independent frontend issues #392 and #394 do not close this critical path. Notification functionality remains a separate, assigned scope decision under [#215](https://github.com/PPsyrius/iss-groupbuy-team8/issues/215).
