# US34 Seed startable demo listings and an admin account on dev — implementation plan

Date: 2026-10-01 · Branch: `feature/seed-dev-listings-202601001` · Issue: #222 (US34, Must, 4 h, due 2026-10-02) · Owner: los gai · Reviewer: @Yorick-Yu · Depends on: #221 (merged in #242) · Blocks: #223 (US13), #229 (US32), #180 join intent (US23), Sprint Review demo · Related: [Sprint 3 plan](plan.md), [backend ADR 0005](../../../backend/docs/adr/0005-stripe-checkout-env-flag.md), [`admin-listings.md`](../../../backend/docs/design/admin-listings.md), [`backend-log-observability.md` §3](../../deploy/backend-log-observability.md), [`frontend/docs/admin-plan.md`](../../../frontend/docs/admin-plan.md) Q11

> **Status (2026-10-01): D1–D7 confirmed by the owner; Phase A tooling implemented and verified locally on this branch. Dev execution (grant, baseline, seed) is pending the operator run; Phase B waits for #223.** Departures from the text below:
>
> - **Two more files.** The grant (§4.1) and the read-only baseline (V-A0) ship as `scripts/grant-dev-admin.sql` and `scripts/dev-demo-baseline.sql`, so the runbook runs reviewed files instead of pasted SQL. The grant also refuses a missing or inactive account.
> - **Tunnel on local port 15432**, not 5432, so it cannot collide with a developer's local PostgreSQL.
> - **R1 narrowed.** The local demo seed's deals (`DEMO-D-*`) expire on 2027-12-31 and carry `current_size` 1–6 with zero `biz_group_member` rows (checked against all migrations plus `R__demo_catalog_seed.sql` on a scratch PostgreSQL 18). Turning the scheduler on (#223) therefore does not sweep them yet; the nearer risk is a buyer joining one of those fake teams. Dev rows are confirmed by V-A0 on the operator run.
> - **Content drift is reported, not applied** (§4.2): listing content belongs to the admin console and some fields freeze after publish.

## 1. Goal and acceptance criteria

Dev gets one named admin and a small, repeatable set of listings that a buyer can actually start a team on, created only through the audited admin API (US25). The runbook re-seeds a clean dev in under 15 minutes.

| # | Criterion (issue #222) | Delivered by | Phase |
| --- | --- | --- | --- |
| AC1 | At least three listings on dev return a startable team price and accept a START intent | §4.2 seed script; §5 V-A2..V-A4 (policy + quote); V-B1 (START intent) | A (price, quote) + B (intent) |
| AC2 | One open bloc exists on dev and accepts a JOIN intent | §4.4; V-B2, V-B3 | B |
| AC3 | Admin grant recorded in the day log; runbook re-seeds a clean dev in < 15 min | §4.1, §4.3; V-A1, V-A5 (timed run) | A |
| AC4 | No secret, token or personal data in the repository or the log | §6 | A + B |

Out of scope: seed data in Flyway migrations or deployed profiles; production data; enabling Checkout or scheduling on dev (#223); fixing #264 (only guarded against, see D5); an admin console UI.

## 2. Baseline (read on 2026-10-01 from `dev` 74a21fa and the public dev API)

| Piece | State | Consequence |
| --- | --- | --- |
| Admin role | `sys_role.admin` exists (V3, "assigned manually"); V6 grants `catalog:product:write` to it. No API grants roles. #68 closed without a dev grant. | The grant is a one-time manual SQL row on dev through the SSM 5432 tunnel (D2). |
| Role loading | `StpInterfaceImpl.getRoleList` reads `rel_user_role` per request; no role cache | A grant takes effect on the next request; no re-login needed. |
| Admin API | `POST /admin/products` (optional `productNo`), `PUT /{id}/status`, `PUT /{id}/team-terms` (terms + optional `teamPrice`), `GET /admin/products` (page ≤ 100, `latestPolicy` + `teamTerms` per row). No CSRF marker on admin endpoints. | Enough to seed without any backend change. |
| START / JOIN intent | `POST /payments/intents` calls `StripeFeatureSwitch.requireEnabled()`; `BLOCBUY_STRIPE_CHECKOUT_ENABLED=false` on dev | **No START or JOIN intent is possible on dev until #223 switches Checkout on.** #223 also lists #222 as a dependency: a cycle (D1). |
| Dev catalog today | `GET /api/v1/products` returns 10 listings with the local demo ids `a0000000-…-0001..0010` (`DEMO-P-*`); five show a `groupPrice`, so open deals exist | Rows from `R__demo_catalog_seed.sql` (or an older copy) reached the shared dev DB, against ADR 0005. How and whether those deals hold fake paid members is unknown (D4, R1). |
| Team terms bounds | `targetSize ≥ 2`, `lifetimeSeconds` 2400–86400 (`ProductTeamTerms`) | A bloc lives at most 24 h, so the demo bloc must be re-created on Review day (D6). |
| Policy window check | `TeamPriceAppServiceImpl.configure` rejects only `validUntil <= now`; START needs `now + 2400 s < validUntil` (#264) | The script must enforce the usable window itself (D5). |

## 3. Decisions for review

| ID | Decision | Recommendation | Alternative |
| --- | --- | --- | --- |
| D1 | Break the #222 ↔ #223 cycle | **Two phases on this issue.** Phase A (today): admin grant, listings, terms, policies, runbook, quote proof — this is all #223 needs to start. Phase B (after #223 enables Checkout, target 10-02): START → paid bloc → JOIN intent. Comment the split on #222 and #223. | Close #222 after Phase A and move AC2 to #223 (changes accepted scope; needs Scrum Master agreement). |
| D2 | How the admin is granted | One idempotent `INSERT … SELECT` into `rel_user_role` keyed by the Google account's email, run once through the SSM 5432 tunnel. The email is typed at the psql prompt, never committed. The log records GitHub handle, UTC time, method and the resulting row id. | A Flyway migration (rejected: grants must not travel with git, #68) or a new role-grant API (out of scope, no approval). |
| D3 | Which account is admin | @l0sgAi's own Google account (owner of this story; also the demo driver for admin steps). | A shared team account (rejected: shared credentials). |
| D4 | Existing `DEMO-P-*` rows on dev | **Leave untouched in Phase A**; record their deal and member counts read-only (V-A0) and hand them to #223, because enabling the scheduler will sweep those deals. Seed new listings with their own numbers `US34-01..04`. | Set them `off_sale` through the API now (tidier demo, but changes state #223 has not inspected yet). |
| D5 | Policy window | `validFrom = now`, `validUntil = now + 30 days` (covers Review on 10-09 and US23 runs). The script refuses any window shorter than 2400 s + 1 h and re-appends a policy when the latest one has less than 7 days left. | Wait for #264 (blocks the critical path). |
| D6 | Listing set | Four listings, SGD, stock 100: three demo listings (`targetSize` 3, lifetime 86400 s) and one short-lived listing (`targetSize` 2, lifetime 2400 s) for #223's expiry test. Exact titles/prices in `scripts/dev-demo-listings.json`, reviewed in the PR. | Three listings only (AC minimum), #223 configures its own. |
| D7 | Images | `coverImage` null; the frontend card falls back to its placeholder. | Upload demo images to the frontend S3 bucket (outward-facing change; needs a separate approval). |

## 4. Implementation

### 4.1 Admin grant (Phase A, one-time, manual)

Run through the SSM tunnel (local port 15432) as `scripts/grant-dev-admin.sql`, inside one transaction. Core statement:

```sql
BEGIN;
INSERT INTO domain.rel_user_role (user_id, role_id)
SELECT u.id, r.id
FROM domain.sys_user u
JOIN domain.sys_role r ON r.role_code = 'admin' AND r.deleted = false
WHERE u.email = :'admin_email' AND u.deleted = false
ON CONFLICT (user_id, role_id) WHERE deleted = false DO NOTHING
RETURNING id, user_id, create_time;
-- expect exactly one row on first run, zero on re-run; anything else → ROLLBACK
COMMIT;
```

The account must have signed in once (row in `sys_user`). Revoke = `UPDATE … SET deleted = true` on that row (documented in the runbook, not executed).

### 4.2 Seed script (Phase A)

New files, no backend code change, no contract change:

- `scripts/dev-demo-listings.json` — the four listings (D6): `productNo`, listing content, `targetSize`, `lifetimeSeconds`, `teamPrice.amount`.
- `scripts/seed-dev-demo-listings.sh` — bash + `curl` + `jq`, `set -euo pipefail`. Inputs only from the environment: `BLOCBUY_BASE_URL` (default dev CloudFront `/api/v1`) and `BLOCBUY_TOKEN` (admin `blocbuy-token` cookie value; never echoed, never written to disk, handed to curl as a config on a file descriptor, so never in argv).

Per listing, idempotent and convergent:

1. Read all managed listings once (`GET /admin/products?size=100`, bounded loop over `total`), index by `productNo`.
2. Missing → `POST /admin/products` with the fixed `productNo` (draft).
3. Not `on_sale` → `PUT /{id}/status` `on_sale` with the current `version`.
4. Terms differ, or `latestPolicy` absent / not in force / `< 7 days` left → `PUT /{id}/team-terms` with terms and `teamPrice` (`expectedVersion` = latest policy version or 0).
5. Any non-2xx → print `code`, `message`, `traceId` and exit non-zero (no retry loops; 412 means someone else edited — rerun).
6. End with a table: `productNo`, id, status, terms, policy `validUntil`.

A rerun on a seeded dev makes zero writes. A "clean dev" is one where `US34-*` rows are absent or deleted; the script never deletes anything.

### 4.3 Runbook

New section "Seed demo listings on dev (US34)" in `docs/deploy/backend-dev-api-agent-verification.md` (it already owns "get a dev token" and dev API verification): prerequisites (tunnel, signed-in admin, token), §4.1 grant, script run, Phase B steps, the Review-day bloc refresh (D6, bloc ≤ 24 h), revoke and clean-up. Timed run recorded for AC3.

### 4.4 Open bloc (Phase B, after #223 enables Checkout)

1. Admin (as buyer) `POST /team-price-quotes` on `US34-01` → `POST /payments/intents` `START` with `quoteId` → `POST /payments/checkout-sessions` → the account owner completes Stripe Hosted Checkout in Sandbox with Stripe's published test card (done by the human in the browser, not by the script).
2. Webhook settles → `GET /group-deals/me` shows the bloc `open`, 1/3.
3. A second team member's account `POST /payments/intents` `JOIN` on that `dealId` → 200 with `intentId` (AC2 needs acceptance, not payment). Abandon the intent; #225's cleanup expires it.
4. Repeat steps 1–3 on 2026-10-09 before 20:00 (bloc lifetime ≤ 24 h).

## 5. Verification

| ID | Check | Proves | Phase |
| --- | --- | --- | --- |
| V-A0 | Read-only SQL: counts of `DEMO-P-*` deals by status and their member/payment rows; `flyway_schema_history` for `R__demo_catalog_seed` | D4 facts for #223 | A |
| V-A1 | `GET /auth/me` with the admin token shows `roles` containing `admin`; `GET /admin/products` → 200 | grant works | A |
| V-A2 | Script run against **local** backend (`local` profile, fresh DB) twice: first run creates 4, second run makes 0 writes | idempotency before touching dev | A |
| V-A3 | Script run on dev; per listing `GET /products/{id}/team-price-policy` in force with `validUntil` ≥ now + 29 days | AC1 price half | A |
| V-A4 | Per listing `POST /team-price-quotes` → 200 with amount = policy amount | startable through the real START pricing path | A |
| V-A5 | Timed rerun of the whole runbook on dev (grant re-run is a no-op) < 15 min | AC3 | A |
| V-B1 | `POST /payments/intents` START on each of the three demo listings → 200 | AC1 intent half | B |
| V-B2 | Paid START → bloc `open` on `/account` and `GET /products/{id}` `deal` 1/3 | AC2 bloc | B |
| V-B3 | Second account JOIN intent → 200 | AC2 join | B |
| V-X | `bash -n`; `shellcheck` if installed (otherwise "not checked"); `git grep` for token/email patterns in the diff | AC4 | A |

No backend Java changes → no `./mvnw -B verify`, no JaCoCo or contract regeneration needed. If review asks for any backend change, it follows the backend constraints skill gates in full.

### 5.1 Results so far (2026-10-01, local only)

Environment: `blocbuy-backend.jar` built from this branch over `dev` 74a21fa, `local` profile, throwaway `postgres:18` and `redis:8.10` containers, test users `us34-admin@example.test` / `us34-buyer@example.test` with Sa-Token sessions written straight into the throwaway Redis. All removed afterwards.

| Check | Result |
| --- | --- |
| Grant SQL | First run `granted = 1`; rerun `granted = 0, already_admin = 1`; unknown email: message, no write. `GET /auth/me` then lists `admin`. |
| V-A2 seed, run 1 / run 2 | 12 admin writes, 4 listings `on_sale` with terms and a 30-day policy, 1.6 s / `admin writes: 0` |
| Refresh and drift | `BLOCBUY_REFRESH_DAYS=31` re-appends 4 policies; a changed `teamPrice` re-appends 1; a changed subtitle prints the warning and writes nothing |
| Failures | buyer token → 403 `COMMON_403_001` + traceId; bad token → 401 `COMMON_401_001`, exit 1; missing token / bad day settings → exit 1 / 2 before any request |
| V-A4 `--check` | 4 quotes equal the seed prices (89.00, 29.00, 49.00, 22.00) |
| START intent (local stand-in for V-B1) | With `BLOCBUY_STRIPE_CHECKOUT_ENABLED=true` and a placeholder `sk_test_` key (no Stripe call is made for an intent): 4 × 200; each `biz_purchase_intent` froze the quoted amount, the listing's target size, and a deadline of 24 h (40 min for `US34-04`) |
| Baseline SQL | Ran against a scratch PostgreSQL 18 with every migration and the local demo seed; output as in the R1 amendment |
| V-X | `bash -n` and `jq empty` pass; `shellcheck` not installed (not checked) |

Not yet proven: anything on dev (V-A0, V-A1, V-A3, V-A4, V-A5), and Phase B. No Java changed, so `./mvnw -B verify` was not run.

## 6. Security and privacy (AC4)

- Token and admin email only in the operator's shell; the script reads the token from env and never prints it; `set -x` is never enabled.
- The token travels only over HTTPS: curl is restricted with `--proto`, and plain HTTP is accepted only for an explicit loopback root (local verification).
- Register row: [NFR-48](../../quality/nfr-register.md) (token handling, HTTPS only, no tracing, no personal data committed).
- The day log names the admin by GitHub handle, not email; no user ids beyond the grant row id.
- Listing data is synthetic. The JSON file holds no URLs to third-party assets.
- Sandbox only: Phase B uses Stripe test mode on dev; no live keys (ADR 0005).

## 7. Risks

| ID | Risk | Mitigation |
| --- | --- | --- |
| R1 | `DEMO-D-*` open deals on dev claim members that have no `biz_group_member` rows (expire 2027-12-31). A real JOIN could complete such a team, and a later expiry would refund payments that never existed | V-A0 counts go to #223 and the Daily Scrum; the demo uses only `US34-*` listings. Taking the `DEMO-P-*` listings off sale is a separate owner decision (D4). |
| R2 | Phase B slips if #223 slips past 10-02 | Phase A alone unblocks #223; Phase B is ~30 min once Checkout is on. Escalate in the Daily Scrum on #232 if #223 is not switched by 10-05. |
| R3 | Demo bloc expires before Review | Runbook step 4.4.4 on 10-09; owner named in the log. |
| R4 | #264 lets a short policy through if someone edits by hand | Script guard (D5); #264 fixes the server side. |
| R5 | A teammate edits a `US34-*` listing between reads (412) | Script stops with the trace id; rerun converges. |

## 8. Delivery

One PR into `dev`: plan, JSON, script, runbook section, day-log entry. Phase A evidence (V-A0..V-A5, V-X) in the PR body and `docs/agile/sprint-03/log/2026-10-01.md`; Phase B evidence appended to the next day log and as a comment on #222 before closing it. Estimate: Phase A 3 h, Phase B 1 h (within the 4 h budget).
