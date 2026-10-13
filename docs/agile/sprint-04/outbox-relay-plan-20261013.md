# #445 outbox relay: port PR #477 onto `dev` and align it with ADR 0007 and ADR 0008 — implementation plan

Date: 2026-10-13 · Branch: `feature/445-outbox-relay-20261013` from `dev` at `afec4e83` (#479 merged) · Issue: #445 (owner @Yorick-Yu, Sprint 4 #465, US40) · Source: PR #477 by @Yorick-Yu, relay commit `a4857660` · Related: [backend ADR 0007](../../../backend/docs/adr/0007-cross-service-events-sns-sqs.md), [backend ADR 0008](../../../backend/docs/adr/0008-dual-deployment-topology.md)

> **Status (2026-10-13): reviewed (all §7 defaults accepted) and implemented on `feature/445-outbox-relay-20261013`. Results: [day log](log/2026-10-13.md).**

## 1. Goal

Deliver #445 (outbox relay, SQS and SNS transports, ElasticMQ for local development and CI) on top of the current `dev`. Keep @Yorick-Yu's relay design and tests. Fix what conflicts with `dev`, and add what the two ADRs require. The relay stays **off** on EC2 `dev` and on ECS until #447 supplies the topic and IAM. This PR must leave every deployment's behaviour unchanged.

## 2. Baseline

| Item | State |
| --- | --- |
| PR #477 | Two commits: `6011b1ff` is the **pre-review** #444 outbox, already on `dev` in its reviewed form as #473; `a4857660` is the relay. Merging it into `dev` conflicts in 16 files |
| Relay commit alone on `dev` | Cherry-pick applies 36 files cleanly. 5 conflict: `backend/README.md`, `BlocBuyApplicationTest.java`, `services/commerce-svc/.../application-commerce.yml` (overlaps #479's `identity.base-url`), `docs/agile/sprint-04/log/2026-10-13.md`, `docs/quality/nfr-register.md` |
| What the relay does | `OutboxRelayJob` runs every 2 s under `LeasedJobRunner` and calls `OutboxRelayService.relayBatch()`. Each batch: up to 100 due rows, then the transport sends them in groups of 10 (`SendMessageBatch` or `PublishBatch`), then one `UPDATE … FROM (VALUES …)` records the results. Backoff is `2^attempts` seconds, capped at 300; `failed_at` is set after 10 attempts and logged at ERROR with `eventId` and `traceId`. Envelope and attributes `eventType`, `version`, `traceId` as ADR 0007 specifies |
| Switches | `blocbuy.events.relay.enabled` (default false, also false in `application-ec2.yml`), `blocbuy.events.transport` (`sqs` by default; commerce profile `sns`), `region` defaults to `us-east-1`, queue URL and topic ARN default to empty |
| Tests in the commit | ElasticMQ ITs (outage then recovery, attributes), lease test (two runners), unit tests for both transports, the message factory, transport configuration and repository bounds |

## 3. Findings and decisions

| # | Finding | Severity | Change |
| --- | --- | --- | --- |
| F1 | Branch stacked on the pre-review #473 | Must fix | Cherry-pick only `a4857660` onto `dev`. Every #473 file keeps `dev`'s version; the relay's mapper methods and XML statements are added to `dev`'s `OutboxMapper` |
| F2 | `region` defaults to `us-east-1`; our account and Region are `ap-southeast-1` | Must fix | No hard-coded default. Use `blocbuy.events.region` when set, otherwise the SDK default region provider chain (`AWS_REGION` on ECS) |
| F3 | Relay enabled with an empty queue URL or topic ARN starts fine, fails every send and marks rows `failed` after 10 attempts (about 17 min). That is silent loss until an operator recovers them | Must fix | Fail startup when the relay is enabled and the selected transport's target is blank, or the transport is neither `sqs` nor `sns` (validated configuration properties) |
| F4 | `MAX_ATTEMPTS = 10` exists in Java and again as `10` inside the SQL | Should fix | Pass `maxAttempts` into `recordResults` as a parameter |
| F5 | `@Transactional` on `OutboxRelayRepositoryImpl.recordResults` (infrastructure owns a transaction; it is a single statement) | Should fix | Remove; one `UPDATE` is atomic on its own. The service deliberately holds no transaction across I/O |
| F6 | `ArchitectureTest` has no owner for `com.blocbuy.integration..`, although ADR 0007's amendment makes it part of commerce | Should fix | Add `com.blocbuy.integration..` to the commerce contexts, so identity and notification can never depend on it |
| F7 | No test proves the distributed wiring with the relay on | Must fix | `CommerceServiceApplicationIT` case with events, relay, `sns` transport and a topic ARN: relay job and SNS transport present. Identity and notification ITs: no relay beans |
| F8 | Remote calls in a loop: up to 10 `PublishBatch` / `SendMessageBatch` calls per tick | Decision | This is the provider's batch API split at its 10-entry limit, bounded by the 100-row tick, not per-item calls. Record it as an explicit exception in an ADR 0007 amendment (backend-constraints hard gate) |
| F9 | `ecs.tf` and `plan.tftest.hcl` changes duplicate #473 | Drop | Already on `dev` |
| F10 | ECS enablement (relay flag, topic ARN, `sns:Publish` task policy) is absent | Out of scope | Belongs to #447. Its contract goes into the handoff and the README: `BLOCBUY_EVENTS_RELAY_ENABLED=true`, `BLOCBUY_EVENTS_SNS_TOPIC_ARN`, task policy `sns:Publish` on that topic, `commerce-svc` only |
| F11 | `backend/docker-compose.yaml` turns events and relay on with ElasticMQ by default | Keep | #445 scope item 4 (local only). `fullstack-localhost` and EC2 are untouched |

Unchanged: the lease model (single active relay, at-least-once on lease loss, consumers deduplicate on `eventId`), the backoff, the batch size, the envelope, the attributes and the recovery runbook.

## 4. Changes (one PR, about 45 files, mostly @Yorick-Yu's)

1. Cherry-pick `a4857660` with `-x`, resolving the 5 conflicts:
   - **README:** keep both sections.
   - **`BlocBuyApplicationTest`:** keep both changes.
   - **`application-commerce.yml`:** keep #479's `identity.base-url` and add `events.transport: sns`.
   - **Day log and NFR register:** keep both, renumbering the relay row to the next free NFR ID.
   - The commit keeps @Yorick-Yu as author.
2. Fixes F2–F7 as separate commits.
3. ADR 0007 amendment (F8 and F3), handoff D row, README event settings, day log entry.

Delivery: a new PR from this branch with `Closes #445`. It credits #477 and @Yorick-Yu, and asks him to review. #477 closes as superseded, with a comment. His branch is not force-pushed (§7 Q1).

## 5. Verification

| # | Check |
| --- | --- |
| V1 | `./mvnw -B clean verify` with Docker: all tests including the ElasticMQ ITs, ArchUnit, SpotBugs, Checkstyle, Spotless |
| V2 | JaCoCo aggregate: no missed line or branch in the new or changed relay files |
| V3 | Mutations: blank topic ARN with the relay on → startup fails; `integration` dependency from a notification class → ArchUnit fails |
| V4 | Monolith exec JAR with profile `ec2`: health `UP`, no relay or transport bean, no scheduler thread for the relay |
| V5 | Images: `commerce-svc` with the ECS environment and the relay off boots `UP` (#479 merged, so commerce now starts). With the relay on, `sqs` transport and local ElasticMQ, a committed event reaches the queue |
| V6 | `terraform test` unchanged (no infra edits) |

Not verifiable here: real SNS and IAM. That waits for #447 and an ECS session.

## 6. Risks

| Risk | Mitigation |
| --- | --- |
| Rewriting a teammate's work | Keep his commit and authorship; every change is a separate, reviewable commit; he reviews |
| Conflicting edits on the same files | #479 is merged; no other open PR touches these files (#424 is groupbuy only) |
| SWE5001 freeze 10-16 | About 4–6 h; merge target 10-14 |

## 7. Questions for the reviewer

| # | Question | Default |
| --- | --- | --- |
| Q1 | New PR superseding #477, or force-push the rebased result onto @Yorick-Yu's branch? | New PR; ask him before closing #477 |
| Q2 | F8 exception (chunked batch calls) recorded in ADR 0007? | Yes |
| Q3 | F3 fail-fast at startup, rather than a warning? | Fail fast |
| Q4 | F6 `integration` owned by commerce in `ArchitectureTest`? | Yes |
| Q5 | Do not enable the relay on ECS in this PR; leave it to #447? | Leave it to #447 |
