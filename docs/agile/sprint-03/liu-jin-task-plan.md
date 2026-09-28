# Liu Jin (@l0sgAi) — Sprint 3 personal task plan

Draft for self-review. **Not committed.** Sprint 3: Mon 2026-09-28 to Fri 2026-10-09. Budget 30 h. Sources: [Sprint 3 Plan](https://docs.google.com/document/d/1YQhbCLgpFh5YfuR7eY8gHeosOUlSxHoyY3-m3W0VfXk/edit?usp=sharing), tracker #232, `origin/dev` at `b4c4d98`.

Ordering rule: importance/urgency first. On a tie, the higher-risk task goes first, so surprises surface while there is still slack.

## 1. Priority list

| # | Task | Issue | Pri | Due | h | Urgency / importance | Risk | Why here |
|---|---|---|---|---|---|---|---|---|
| 1 | Review Eric's bounded best-open-deal PR | PR #206 (#205) | Should | **overdue** (requested 09-26, 24 h rule) | ~1 (unbudgeted) | High urgency: blocks Eric, working agreement already broken | Low–med: SQL window/tie-break correctness on the listing hot path | Cheapest unblock; do it before deep work today |
| 2 | Planning-day decisions: #183 (honour quote?), #201 option, US25 API shape with Lim; file the dev admin-grant ask | #183, #201, #221, #222 | Must | **09-28** at Planning | 0.5 (inside tasks) | Blocks four downstream tasks and two other members | Med: wrong API shape = rework in FE admin console later | A decision is cheap now, expensive after code |
| 3 | **US25: admin sets team terms** | #221 | Must, critical path | 10-01 | 12 | Highest importance: no startable listing = no US13, US23 join, US32, demo | **High**: money-adjacent config; lock order with `lockStartQuote`; contract before controller; 412 versioning | Everything on the goal path waits on it |
| 4 | **US34: demo listings + admin account on dev** | #222 | Must, critical path | 10-02 | 4 | Blocks US13 switch-on (10-02) and US32 | **High (external)**: admin grant needs dev DB access from the account holder (Q11 unresolved since #68) | Ask on day 1; seeding itself is small |
| 5 | **US26: charge the quoted price (API)** | #183 | Must | 10-02 | 6 | Must, but nothing else waits on it except Lim's 2 h UI | **High**: payment path, lock order team → intent, idempotency, contract change | Same importance band as US34; runs after US25 because it shares the catalog code |
| 6 | US15b: invite resolve endpoint | #201 | Should | 09-30 → **propose 10-01** | 4 | Blocks Lim's #230 (due 10-02) | Med: public endpoint, least disclosure; a too-broad `PUBLIC_PATHS` pattern = data leak | Should > #96 because it blocks a teammate |
| 7 | #96: `Secure` cookie on EC2 | #96 | Should | 09-30 → **propose 10-05** | 2 | Low urgency: nothing waits on it; NFR-01 closure | Med: a wrong flag breaks Google login on dev for everyone | Small; do after the goal-path work, verify via CloudFront |
| 8 | Reviews: US13 switch-on, US31 prod apply | #223, #226 | — | when PRs open (~10-02, 10-06) | ~3 (unbudgeted) | 24 h review SLA | Med: US13 turns on real Sandbox refunds on dev; US31 touches prod AWS | Interrupt-driven; block time on 10-02 and 10-06 |
| 9 | Evidence: day-log entries, NFR-29 / NFR-01 rows, my report section (UC9-ext, UC3 quote honouring, UC7 resolve) | — | Must | daily; report 10-09 | 2 | Required by DoD | Low | Write while building, not after |
| 10 | #180 assignment check | #180 | — | 09-28 | 0 | Plan says Eric leads, Pan reviews; I am still an assignee | Low | Unassign myself or agree a support role (Sa-Token session fixture knowledge) at Planning |
| 11 | Sync repo `docs/agile/sprint-03/plan.md` with the Google Doc after Planning | — | Could | 09-29 | 0.5 | `dev` still has the Friday version | Low | Offer to SM (Lim); do it only if Lim prefers |
| 12 | #208 Redis proposal | #208 | Sprint 4 | after US23 data | 0 | Deferred: measure first | — | Update the issue with US23 numbers after 10-07 |

Budgeted: 12 + 4 + 6 + 4 + 2 + 2 = **30 h**. Unbudgeted: about 4 h of reviews and 0.5 h of plan sync, so the real total is ~34.5 h. **Raise this at Planning**: either drop #96 to the next sprint or accept the overrun.

## 2. Capacity problem to raise at Planning

The team plan front-loads me: US25 (12 h, due 10-01), #201 (4 h, due 09-30) and #96 (2 h, due 09-30) are 18 h in the first four working days, about 4.5 h/day against a ~3 h/day part-time pace. Proposal:

- #201 due 09-30 → **10-01** (Lim's #230 is due 10-02; one day is still enough).
- #96 due 09-30 → **10-05** (not on the goal path).
- US25 stays 10-01 (critical path).

## 3. Task notes (scope, design, risks)

### 3.1 US25 — admin team terms (#221)

What exists (so the job is smaller than "new API"):

- Admin listing CRUD: `AdminListingAppServiceImpl` (`create`, `revise` with `expectedVersion`, `changeStatus`, `delete`) behind `GET/POST/PUT/DELETE /api/v1/admin/products…`.
- Team price policy write: `PUT /api/v1/products/{productId}/team-price-policy` (`TeamPriceAppServiceImpl.configure`, versioned, IT covered).
- Bounds: `ProductTeamTerms` (`MIN_TARGET_SIZE=2`, lifetime 2400–86400 s) and `startable()`; read path `ProductRepository.findTeamTerms` → `BizProductMapper.selectTeamTerms`.
- Missing: the **write path** for `team_target_size` / `team_lifetime_seconds`, and the admin read model does not show them.

Design options (decide with Lim at Planning; admin-plan BA-7 asks for terms on `ListingContent`):

| Option | For | Against |
|---|---|---|
| A. Dedicated `PUT /api/v1/admin/products/{id}/team-terms` with `expectedVersion`; admin `get`/`page` responses gain `teamTerms` | `revise` untouched (2 callers + `AdminListingFlowIT`); own validation; smaller PR | Console needs two calls to save listing + terms |
| B. Add terms to `ListingDetails`/`ListingContent` in `revise` | One form, one call (what BA-7 asks for) | Touches the `ListingDetails` record used by create/revise and its tests; bigger blast radius |

Lean: **A**, and include `teamTerms` on admin reads so BA-7's display need is met.

Build steps (target PR under ~1,500 lines; split contract/domain/persistence from controller if needed):

1. Contract entry first (additive), shared with Lim.
2. Domain: a validating factory (reject instead of silently "not startable") — `ProductTeamTerms.configured(size, lifetime)` throwing `BizException` with field errors; reuse the bounds.
3. Aggregate/app service: `configureTeamTerms(id, terms, expectedVersion, actor)` → `lockForChange` → update with the version check → audit (`update_by`). Same lock as `lockStartQuote` (`products.lockById`), so START and a terms change serialize.
4. Persistence: mapper `updateTeamTerms` with `WHERE id=? AND version=? AND deleted=false`; 0 rows → 412 `PRODUCT_VERSION_CONFLICT`.
5. Controller: admin role (`catalog:product:write`), 401/403/400/412 tests; regenerate `contracts/openapi.json`.
6. Tests: unit (bounds, reject null half-set), PostgreSQL IT (stale version, concurrent write), flow IT (configure → START intent succeeds). Update NFR-29 and the ADR 0001 "Open" note.

Risks:

- **Changing terms while blocs are open.** Terms are read only at START and frozen into the intent/team, so open teams are unaffected. Assert this in a test.
- **Can terms be cleared?** Decide: allow null-out (makes the product unstartable) or not. Lean: allow, audited.
- **Should a DRAFT listing accept terms?** Lean: yes (configure before publish).

### 3.2 US34 — dev demo data (#222)

- **Day 1: ask the account holder (Pan) for the dev admin grant** on a named Google account (Q11), recorded without secrets. This is the highest external risk on my list.
- After #221 is deployed to dev: through the API only, create ≥3 listings (terms + team price policy + ON_SALE), plus one open bloc via a START on dev (needs Checkout on dev → coordinate with Yorick's #223, or seed the bloc right after his switch-on).
- Write a re-seed runbook section (target under 15 min) for the demo morning.
- Risk: US34 and US13 are both due 10-02 and depend on each other. Agree the order with Yorick: listings first (mine), switch-on (his), then the open bloc (mine).

### 3.3 US26 — quote honouring (#183)

What exists: `TeamPriceQuote(id, productId, ownerId, policyVersion, amount, currency, issuedAt, expiresAt)`, an owner-scoped snapshot; `TeamPriceAppService` Javadoc says "Quotes cannot yet be consumed by checkout". START re-prices in `CatalogPurchaseSupportImpl.lockStartQuote` from `findLatestPolicy`. `IntentRequest` is `{operation, businessId, ref}`.

Recommendation for Planning: **honour the quote** (the price shown is the price charged; NFR-28 business correctness).

Build:

1. Contract: optional `quoteId` on `IntentRequest` (START only; with JOIN → 400).
2. `lockStartQuote(productId, now, quoteId, owner)`: under the product lock, load the quote, check owner, product and `expiresAt > now` (database clock, per ADR 0001), and that terms are still startable and the product is sellable; use `quote.amount()`. A missing, foreign or expired quote → 409 with a distinct code (for example `CATALOG_409_00x QUOTE_INVALID`).
3. Without `quoteId`: behaviour unchanged (backward compatible for current clients).
4. IT: policy changed between quote and intent → charge = quoted amount; expired quote → 409; foreign owner → the same 409 as a missing quote (decide: one code for missing, foreign and expired, so the quote id is not an existence oracle); intent replay with the same idempotency key → same intent.

Risks:

- **Money path.** Keep the lock order team → intent → order → payment; the quote read happens under the product lock, before the intent row.
- **Quote reuse across several intents.** Allowed? Lean: allowed until expiry (it is a price promise, not a reservation, per the record's Javadoc).
- **Contract change affects Lim's quote sheet.** Share the contract entry on day 2.

### 3.4 US15b — invite resolve (#201)

- Option (a): `GET /api/v1/invites/{ref}` → `{dealId, productId, sharerNickname, expiresAt}`. Add to `PUBLIC_PATHS` in `SaTokenConfig` **for this exact pattern only**.
- Least disclosure: nickname only, never email or user id; an unknown or expired ref → 404, with the same shape for both (no oracle on expiry).
- Tests: anonymous 200, unknown 404, no PII in the JSON (assert the field set), ArchUnit; additive contract; regenerate.
- Risk: a public path pattern that is too broad. Review the `PUBLIC_PATHS` diff line by line.

### 3.5 #96 — Secure cookie on EC2

- `application-ec2.yml`: `sa-token.cookie.secure: false` → `true`, **and** `…state-cookie-secure: false` → `true` (they must flip together, per the comments there). Update the stale comment: TLS now terminates at CloudFront.
- Browser ↔ CloudFront is HTTPS, so Secure is valid even though CloudFront → ALB is HTTP (NFR-37 is separate).
- Verify after deploy: `curl -sI` the OAuth start through CloudFront shows `Secure; HttpOnly; SameSite=Lax`; a full Google login on dev still works; NFR-01 → `enforced`.
- Risk: the prod profile reuses `ec2`. Confirm the prod path is also HTTPS-only at the browser edge before #226's release drill.

## 4. Day-by-day

| Day | Plan | Hours |
|---|---|---|
| Mon 09-28 | Review PR #206; Planning (decisions §2 and #183/#201/US25 shape); send the admin-grant ask; US25 contract entry + domain factory | 3 |
| Tue 09-29 | US25 persistence + version check + IT; share the contract with Lim | 3.5 |
| Wed 09-30 | US25 controller + authz tests; open the PR; start #201 | 3.5 |
| Thu 10-01 | US25 review fixes → **merge**; #201 PR → merge; backlog refinement | 3.5 |
| Fri 10-02 | Deploy check; **US34 listings on dev**; US26 contract + service; mid-sprint check; review #223 when it opens | 3.5 |
| Mon 10-05 | US26 IT + PR → merge; #96 PR | 3 |
| Tue 10-06 | US34 open bloc + re-seed runbook; #96 verify on dev; review #226 apply | 3 |
| Wed 10-07 | Review #226 drill; buffer for review fixes | 2.5 |
| Thu 10-08 | Evidence and my report sections; freeze | 2.5 |
| Fri 10-09 | Review demo (US25/US34 part), retro | 1.5 |

## 5. Questions to settle at Planning

1. #183: honour the quote? (recommend yes)
2. #201: option (a) resolve endpoint? (recommend yes)
3. US25: dedicated `team-terms` endpoint (A) or on `ListingContent` (B)? (recommend A plus terms in admin reads)
4. Due-date shifts for #201 (→10-01) and #96 (→10-05).
5. #180: remove me as an assignee, or define a support role.
6. Who syncs `docs/agile/sprint-03/plan.md` with the Google Doc: SM or me.
7. Review hours (~4 h) are not budgeted: drop #96, or accept ~34 h.
