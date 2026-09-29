# Sprint 3 plan (2026-09-28 to 2026-10-09)

Status: draft for Sprint Planning on 2026-09-28. Numbers, owners and dates change only at that meeting; after it, this file mirrors the Sprint 3 container issue.

Sources read on 2026-09-26: `origin/dev` at `c30ed5a`, the Sprint 2 progress report, open issues (#30, #31, #96, #130, #134, #180, #183, #201), open PRs (#140, #199, #200, #202), `docs/quality/nfr-register.md`, `backend/docs/payment/stripe-release-boundary.md`, backend ADRs 0001–0003, frontend `docs/plan.md` Tier 3 and #48.

## 1. Calendar

| Item | Date |
|---|---|
| Sprint 3 | Mon 2026-09-28 to Fri 2026-10-09, ten working days |
| Mid-sprint check (owner vs actual, rebalance) | Fri 2026-10-02 |
| Code freeze | Thu 2026-10-08 EOD |
| Sprint Review, Retrospective, Sprint 3 progress report | Fri 2026-10-09 |
| Sprint 4 (planned) | Mon 2026-10-12 to Fri 2026-10-23 |
| Presentation preparation and hardening | 2026-10-26 to 2026-11-02 |
| Project presentation | 2026-11-03 and 2026-11-04 |
| Final report submission | 2026-11-10 |

Sprint 3 is the last sprint with room for new features. Sprint 4 is reserved for the remaining backlog stories (US15 reporting, US16/US17 closure, notifications) and demo hardening. Anything that must appear in the presentation should be running on dev by the end of Sprint 3.

## 2. Sprint goal

By 2026-10-09 a buyer on the **deployed dev environment** can open an invite link, start or join a bloc on a real listing, pay through Stripe Sandbox for the price they were shown, see the order and the bloc on `/account`, and be refunded automatically when the bloc expires. Key actions have a measured p95 at 100 concurrent users.

Sprint 2 proved this chain in PostgreSQL integration tests and a local Sandbox run. Sprint 3 turns it on where the lecturers can see it, closes the gaps that stop it working on real data, and measures it.

## 3. Starting position: what Sprint 2 left

| Area | State on `dev` at `c30ed5a` | Sprint 3 obligation |
|---|---|---|
| Checkout on dev | `stripe-sprint2` profile off on EC2; `StripeFeatureSwitch` rejects intent, Checkout and receipt routes | Enable on dev after the pre-live checks (US13) |
| Scheduling on dev | `BLOCBUY_SCHEDULING_ENABLED` unset; sweeper and refund worker idle | Enable after the overdue-team and due-refund backlog counts (US13) |
| Group terms | `team_target_size` / `team_lifetime_seconds` null on every product; no admin surface (NFR-29, admin-plan BA-7) | START is unavailable on real data until an admin API sets them (US25) |
| Price shown vs charged | START re-prices from the latest policy; the quote is only a preview (#183) | Decide and implement `IntentRequest.quoteId` (US26) |
| Payment model | Two models: `CheckoutPayment` (real) and the id-only `Payment` placeholder behind `DevPaymentRepositoryAdapter` ("REMOVE BEFORE LIVE TRAFFIC") | Consolidate and retire the adapter (US29) |
| Purchase intents | A START reserves a `deal_id` before the team exists; unpaid intents are never cleaned up (ADR 0001 "owed in Sprint 3") | Leased cleanup job (US30) |
| My Teams | `GET /api/v1/group-deals/me` merged (#182); no page (T3-03) | Build the page (US24) |
| Join refusals | PR #200 (T3-04) open | Review and merge (US27) |
| Share attribution | Backend merged (#198); no share UI; no invite resolve endpoint (#201) | T3-05 and #201 (US15) |
| Receipt | Generic card; no link back to the listing (needs `productId`) | T3-06 restyle and back-link (US27) |
| Performance | No script, no baseline; NFR-15 "target undefined" (#180) | Measure (US23) |
| Security | NFR-01 unmet (`Secure` cookie off on EC2, #96); NFR-33 old source maps still in the dev bucket; NFR-37 origin HTTP | Close NFR-01 and NFR-33; decide NFR-37 (US28) |
| Payment follow-ups | #130 sandbox gate scattered; #134 `expireDueSessions` can be starved by failing rows | Fix (US28) |
| Production | #48 closed with §6–§10 open: `terraform apply`, post-apply checks, variables, release drill | First governed release (US31) |
| Signed-in E2E | Playwright `@auth` cases skipped; no seeded storage state | Signed-in journey on dev (US32) |
| Evidence | Burndown had to be reconstructed; estimate field empty | Hours in the board estimate field daily |

## 4. Sprint 3 backlog

Capacity is 150 h: five members at 30 h each, the same as Sprint 2. **The planned owner is the person who writes the code.** If that changes, the board card and this table change the same day.

| ID | Story | Epic | Owner | Reviewer | h | Issue |
|---|---|---|---|---|---|---|
| US13 | As a buyer, I want my payment refunded automatically when my bloc expires, on the deployed environment. | Buyer | Yu Qianshuo | Panpakorn | 12 | #29 follow-up |
| US14 | As the system, I want refunds proven idempotent under replay and crash on dev, so retries never double-refund. | Back office | Yu Qianshuo | Liu Jin | 8 | new |
| US15 | As a member, I want to share an invite link that names me, so friends join my bloc and I get the credit. | Buyer | Lim Jiechao (FE), Liu Jin (API) | Liu Jin, Lim Jiechao | 10 + 6 | #30, #201 |
| US23 | As an operator, I want p95 measured at 100 concurrent users for browse, join intent and my orders. *(carry-over)* | Integration | Wang Ziang (lead), Panpakorn (environment) | Panpakorn, Wang Ziang | 12 + 2 | #180 |
| US24 | As a buyer, I want a My Blocs view on `/account`, so I can track open, completed and refunded blocs. *(carry-over)* | Buyer | Lim Jiechao | Liu Jin | 6 | #31 |
| US25 | As an admin, I want to set a listing's team size, lifetime and team price policy, so buyers can start blocs on real listings. | Admin | Liu Jin | Wang Ziang | 12 | new (BA-7, NFR-29) |
| US26 | As a buyer, I want to be charged the price I was quoted, so the checkout never surprises me. | Buyer | Liu Jin (API), Lim Jiechao (FE) | Yu Qianshuo | 6 + 2 | #183 |
| US27 | As a buyer, I want clear join refusals and a receipt that matches the design and links back to the listing. | Buyer | Lim Jiechao (FE), Liu Jin (`Receipt.productId`) | Wang Ziang | 10 + 2 | #200, T3-06 |
| US28 | As a team, we want the open security and payment findings closed, so every blocking gate stays green on dev. | Integration | Panpakorn (NFR), Yu Qianshuo (#130, #134), Liu Jin (#96) | rotating | 6 + 8 + 2 | #96, #130, #134 |
| US29 | As a developer, I want one payment model, so the dev-only adapter and its profile are gone before live traffic. | Back office | Wang Ziang | Yu Qianshuo | 8 | new |
| US30 | As the platform, I want unpaid purchase intents to expire and release their reserved team id. | Back office | Wang Ziang | Liu Jin | 8 | new (ADR 0001) |
| US31 | As an approver, I want the first production release cut from a tag through the governed pipeline, with a rehearsed rollback. | Integration | Panpakorn | Liu Jin | 14 | #48 follow-up |
| US32 | As a team, we want the signed-in buyer journey covered by E2E on dev, so the demo path is regression-tested. | Integration | Panpakorn | Lim Jiechao | 6 | new |
| EV | Evidence: daily hours, day logs, review, retro, Sprint 3 progress report | Process | all | — | 10 | container |
| | **Total** | | | | **150** | |

Per member: Lim Jiechao 10 (US15) + 6 (US24) + 2 (US26) + 10 (US27) + 2 (EV) = 30; Liu Jin 6 (US15) + 12 (US25) + 6 (US26) + 2 (US27) + 2 (US28) + 2 (EV) = 30; Panpakorn 2 (US23) + 6 (US28) + 14 (US31) + 6 (US32) + 2 (EV) = 30; Wang Ziang 12 (US23) + 8 (US29) + 8 (US30) + 2 (EV) = 30; Yu Qianshuo 12 (US13) + 8 (US14) + 8 (US28) + 2 (EV) = 30.

Out of scope for Sprint 3 (see §10): the admin console UI (#202, Q1 = no), notifications, keyset paging, live Stripe keys, and origin TLS (NFR-37 needs a hostname first).

## 5. Acceptance criteria

| Story | Acceptance criteria |
|---|---|
| US13 | The overdue-team and due-refund counts on dev are recorded in the day log before any switch changes. `stripe-sprint2` and `BLOCBUY_SCHEDULING_ENABLED=true` are set on dev through SSM, not in the image. A bloc seeded to expire on dev becomes `expired` within one sweep interval, its orders become `cancelled`, and each paid payment gets exactly one refund that reaches `succeeded` in Stripe Sandbox. The receipt shows the refund status. The operator runbook names the recovery owner and the disable-and-drain steps (pre-live checklist items 2 and 6). |
| US14 | On dev: a replayed `charge.refunded` / refund webhook changes nothing; killing the worker between the Stripe call and the database write, then restarting, produces no second provider refund (same idempotency key); a permanent failure is parked for review, not retried. Evidence goes in a dated `backend/docs/payment/` acceptance record with Sandbox ids and no secrets. |
| US15 | `GET /api/v1/invites/{ref}` (option (a) on #201) is public, returns `{dealId, productId, sharerNickname, expiresAt}`, never an email or user id, and is added to `contracts/openapi.json` additively. The listing shows a Share block for members (link, Copy, Share again). `/products/$productId?deal=…&ref=…` shows "*Name* started this bloc" and carries `ref` into the JOIN intent. A join through the link writes one `biz_share_credit` row; a self-referral is refused. Vitest and a Playwright `page.route` journey cover open, full and ended blocs. |
| US23 | A committed k6 (or equivalent) script with browse, join intent and my-orders scenarios; a 100 virtual-user run with p50, p95, p99, error rate and throughput per scenario; the expiry lag under load measured from `blocbuy.team.expiry.teams`; environment, commit SHA and raw summary in `docs/perf/sprint3-baseline.md`; NFR-15 updated. Authenticated sessions come from a test-only fixture on a local stack or a seeded store; **no login bypass in any deployed profile**. If p95 exceeds 2,000 ms, the gap and the top suspect are recorded rather than hidden. |
| US24 | `/account` shows My Blocs from `GET /group-deals/me` with Active / Completed / Refunded chips and counts, rows per design-screens §4.4, paging, skeleton, empty and retry states. Status agrees with the order list for the same bloc. `pnpm check` is green with new tests. |
| US25 | An admin-only, authenticated API sets `team_target_size`, `team_lifetime_seconds` and the team price policy on a listing. Values are validated (size ≥ 2, lifetime within a configured range, team price below the individual price), use optimistic versioning (412 on a stale version), and are audited. `ProductTeamTerms.startable()` becomes true for configured listings. The dev database is seeded through this API (not SQL) with at least three startable listings and one admin grant to a named Google account (admin-plan Q11). The contract changes additively. |
| US26 | The decision on #183 is recorded (quote honoured or preview only). If honoured: `IntentRequest.quoteId` is optional, validated for owner, product and expiry under the intent's transaction, and START charges the quoted amount; an expired or foreign quote gives a 409 with a distinct code; the quote sheet sends it and its copy changes. Integration tests cover a policy change between quote and intent. |
| US27 | #200 merged. `Receipt` gains `productId` (additive) and the receipt links back to the listing. `/payments/$paymentId` and `/payments/return` follow §4.5/§4.6 (waiting state while `CREATED`, refund statement fields). Screenshots of each state are attached to the PR. |
| US28 | #96: the EC2 cookie is `Secure`, verified by a `Set-Cookie` capture through CloudFront; NFR-01 moves to `enforced`. NFR-33: the old `.map` objects are removed from the dev bucket and a `curl` 403 is recorded. #130: one Stripe sandbox gate in `StripeProperties`/`StripeClientProvider`. #134: `expireDueSessions` skips and backs off failing rows, and open review cases no longer crowd out expired sessions (test proves it). NFR-35 live board-snapshot run verified. NFR-37 has a dated decision (hostname plus ACM, or an accepted risk with an end date). |
| US29 | `DevPaymentRepositoryAdapter` and the placeholder `Payment` read path are deleted. The remaining payment consumers read `CheckoutPayment` through one port. `stripe-sprint2` is replaced by a named configuration flag (for example `blocbuy.payment.checkout.enabled`) with the same fail-closed default. ArchUnit and the full `./mvnw -B verify` stay green. Pre-live checklist item 1 is ticked. |
| US30 | A leased job (reusing `LeasedJobRunner`) marks unpaid intents past their deadline as expired in bounded batches, one transaction per intent, idempotently. An intent whose payment is settling is never touched (lock order: team, then intent). Metrics and a one-line INFO summary per non-empty run. Testcontainers IT covers the race with a late webhook. |
| US31 | #48 §6–§8 done: reviewed `terraform plan`, `apply`, post-apply checks (SSM online, 5432/6379 loopback-only, direct ALB blocked), GitHub variables backfilled from outputs. Release drill: tag `v0.1.0-rc1` with the deploy switch closed, then a real `v0.1.0` deploy, a health check, and a rollback to the previous tag. Every step is recorded in the day log. Checkout and scheduling stay **off** in production. |
| US32 | A seeded `e2e/.auth/dev.json` exists outside git (documented regeneration), and the `@auth` Playwright cases run on dev: sign in, open a listing, start Checkout up to the Stripe redirect, return, see the order on `/account`, sign out. The CI `e2e-dev` job runs the anonymous suite; the signed-in suite runs on demand with the storage state. |

## 6. Dependencies and integration order

1. **Day 1 (09-28):** freeze the backlog; record the #183 and #201 decisions on the issues; write the US25, US26 and US15 contract entries; take the dev backlog counts for US13.
2. **US25 is the critical path.** Without group terms no real listing can START, so US13, US15, US23 (join intent) and US32 have no data on dev. Target: merged and dev seeded by **10-01**.
3. **US29 before US13.** Retire the dev adapter before Checkout is switched on for shared use. Target: **10-01**.
4. **US13 rollout on 10-02**, after US25 and US29. The mid-sprint check confirms or delays it.
5. **US15 backend (#201) by 09-30** so the frontend share UI can merge by 10-06.
6. **US23 runs on 10-05 and 10-06, not the last day.** Rerun after fixes on 10-07 if needed.
7. **US31 prod apply by 10-06**, release drill on 10-07, so the freeze does not block it.
8. **Every PR stays below about 1,500 changed lines** and is reviewed within one working day. Stacked PRs are fine; a 10,000-line PR is not.

## 7. Burndown baseline

150 h over ten working days, 15 h per day ideal. **Each owner updates the board's estimate field with remaining hours at the end of every working day.** The snapshot workflow then produces the burndown with no reconstruction.

| Date | Ideal remaining h | Planned focus |
|---|---|---|
| Start (09-28) | 150 | Planning, contract entries, decisions on #183/#201 |
| 09-28 | 135 | Dev backlog counts; US29 and US30 design; perf script skeleton |
| 09-29 | 120 | US25 API; #201 endpoint; US26 backend |
| 09-30 | 105 | US25 seeding; #201 merged; US29 merged |
| 10-01 | 90 | US25 merged and dev seeded; #200 merged; #96 / NFR-33 closed |
| 10-02 | 75 | US13 switches on dev; mid-sprint check |
| 10-05 | 60 | US15 frontend; US24 page; US23 first run |
| 10-06 | 45 | US23 fixes; US31 apply; US26 frontend |
| 10-07 | 30 | US14 acceptance; US31 release drill; US32 on dev |
| 10-08 | 15 | US27 receipt; code freeze; gate and security fixes |
| 10-09 | 0 | Review, retro, report |

## 8. Definition of Done

The Sprint 2 Definition of Done carries forward, with three changes that answer the Sprint 2 retrospective:

- Acceptance criteria are satisfied and linked to the story issue.
- The endpoint is in `contracts/openapi.json` **before** its controller PR is opened, and the contract-drift check passes without a waiver.
- Implementation respects the COLA/DDD boundaries enforced by ArchUnit.
- Tests cover the main path, validation failures, and the replay and concurrency cases for the story.
- Every cross-domain reaction states its transaction phase, and a test proves it.
- Maven verify, Spotless, Checkstyle, SpotBugs, ArchUnit, Gitleaks, Trivy, both SonarCloud gates and `pnpm check` pass.
- Reviewed, with no secret, no unresolved blocking defect and no unapproved API break.
- **New:** where the story says "on dev", it is demonstrated on the deployed dev environment, with the evidence in the day log. A local run does not count.
- **New:** the PR is below about 1,500 changed lines, or it is one layer of a stacked series.
- **New:** remaining hours are in the board estimate field on the day the card moves.
- Board, day log, NFR register (where applicable) and contribution record are updated before completion is counted.

## 9. Risks and mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| US25 slips | No startable listing; US13, US15, US23 and US32 have no data on dev | Critical path from day 1 with its reviewer booked in advance; no SQL seeding fallback, so dev data always goes through the validated API | Liu Jin |
| Enabling scheduling on dev sends unexpected Sandbox refunds | Confusing demo data | Backlog counts first; soft-delete or resolve stale test teams through the API; the switch can be turned off, but in-flight work is drained first | Yu Qianshuo |
| Sandbox keys or the webhook secret are misconfigured on dev | Checkout fails silently | Capability endpoint returns `enabled=false` on bad config; smoke `POST /payments/intents` after the switch | Yu Qianshuo |
| 100 authenticated sessions cannot be created without Google | US23 cannot measure join intent or my orders | Test-only session fixture on a local stack, recorded as the environment; dev measured for anonymous browse only | Wang Ziang |
| Production apply changes shared AWS state | Cost or security exposure | Saved-plan review by two members, apply by the account holder, prod switches closed, cost alarm checked | Panpakorn |
| Review queue (about two merges a day in Sprint 2) | Late merges and another cliff-shaped burndown | Small PRs, a named reviewer per story, review within one working day | all |
| Planned owners drift from actual work again | Unfair contribution record, peer-assessment disputes | Mid-sprint check on 10-02; reassignment recorded on the card and in this file | Scrum Master for the sprint |

## 10. Carry-forward to Sprint 4 and backlog

| Item | Reason | Target |
|---|---|---|
| Admin console UI (`/admin`, admin-plan A1–A3) | 14.75 ideal days; Q1 answered "not in Sprint 3" (#202) | Sprint 4 (A1 read-only slice) |
| US15 reporting: sharer credit summary for buyers | Credits are written and derived; no read endpoint yet | Sprint 4 |
| US16 performance tuning against the Sprint 3 baseline | Needs US23 first | Sprint 4 |
| US17 final security and quality closure | Continuous; final pass before the presentation | Sprint 4 |
| Notification domain | All classes are stubs; no story requires it yet | Sprint 4 or backlog |
| Keyset paging for My Orders and My Blocs | Offset paging accepted; page drift is documented | Backlog |
| Scheduled share-credit reconciliation (NFR-39) | Manual query today; a job needs approval | Backlog |
| Origin TLS between CloudFront and ALB (NFR-37) | Needs a hostname and a regional ACM certificate | Decision in US28; implementation Sprint 4 or accepted risk |
| Live Stripe keys | Pre-live checklist item 5; not needed for the module | Out of scope |

## 11. Assignment by use case

| Member | Use cases | Sprint 3 work | h |
|---|---|---|---|
| Lim Jiechao | UC7 (FE), UC8 (FE), UC3/UC4 (FE), UC6 receipt | Share block and invite landing, My Blocs, quote-id wiring, join refusals (#200), receipt restyle and back-link | 30 |
| Liu Jin | UC9 group terms, UC7 resolve, UC3 quote honouring | Group-terms admin API and dev seed, #201, #183 backend, `Receipt.productId`, #96 | 30 |
| Panpakorn Siripanich | DevSecOps, release, security NFRs | Production apply and release drill, security NFR closure, signed-in E2E on dev, perf environment support | 30 |
| Wang Ziang (Eric) | UC3/UC4 intent lifecycle, performance | Performance baseline (lead), payment-model consolidation, unpaid-intent cleanup job | 30 |
| Yu Qianshuo (Yorik) | UC6 refund on expiry | Dev rollout of Checkout, scheduling and refunds; refund idempotency acceptance; #130, #134 | 30 |

## 12. Design focus for the Sprint 3 report

Each owner records the analysis, design and pattern for their use case as they build it, so the report is not reconstructed afterwards:

- **US25 group terms:** State (product lifecycle gates when terms may change), Optimistic Offline Lock (412 on a stale version), Specification for the validation rules.
- **US26 quote honouring:** Memento-like frozen quote, Idempotency Key, the lock order team → intent.
- **US29 payment consolidation:** Strangler Fig retirement of the dev adapter, Feature Toggle replacing the profile.
- **US30 intent cleanup:** Lease, Unit of Work per intent, Idempotent Receiver.
- **US13/US14 refunds live:** Idempotency Key, Retry with Backoff, Operator Review (Human-in-the-Loop) for uncertain outcomes.
- **US15 invite resolve:** Read Model, least-disclosure DTO for a public endpoint.
- **US23 performance:** Load test as executable evidence; baseline-and-compare.
- **US31 release:** Immutable Artifact, tag-only environments, Blue/Previous-tag rollback.
