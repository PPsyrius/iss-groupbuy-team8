# Verifying Backend APIs in the Dev Environment with an Agent

Standard workflow for backend developers: after a deploy CI passes, let your coding agent
(Claude Code, etc.) exercise your API on the real dev environment instead of hand-writing
curl commands. You do one manual step (get a token), then hand the agent a prompt.

```text
you (browser)                you                     agent (Claude Code etc.)
─────────────                ───                     ─────────────────────────
Google OAuth login ──► copy blocbuy-token ──► read controller code for contracts
                                             curl happy path + error cases
                                             verify Result envelope / ErrorCode
                                             clean up test data
        ▲ failure: paste traceId into CloudWatch Logs Insights
```

## Step 1 — Get a token (manual, browser only)

The login flow needs Google's redirect, so it must happen in a browser — the agent cannot
do it for you.

1. Open: <https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google>
2. Complete Google sign-in with the account that owns the role your API needs
   (e.g. an **admin** account for UC9 `/admin/**` endpoints, a buyer account for
   quotation endpoints).
3. Copy the `blocbuy-token` value from the success response.

Notes:

- Tokens are session-scoped (Sa-Token). If calls start returning `401 COMMON_401_001`,
  repeat this step.
- The token is a credential. Paste it into your agent session only — **never commit it**
  to the repo, docs, or chat logs.
- If you need an admin account, ask the team to grant your Google identity the `ADMIN`
  role first (admin endpoints check both `StpUtil.checkRole(ADMIN)` and the
  `catalog:product:write` permission).

## Step 2 — Paste this prompt to your agent

Fill in the three bracketed fields, then send:

```text
Deployment CI just passed. Verify the [UCx / feature name] backend APIs on the dev
environment with curl and report anything wrong.

- Base URL: https://d1b3np88z657cp.cloudfront.net/api/v1
- Token (blocbuy-token header): [paste token from step 1]
- Scope: [e.g. "UC9 admin listing management — AdminListingController",
  or "the endpoints I added in this branch"]

Method:
1. Read the relevant controller/DTO code first (use codegraph) to derive the exact
   contract: paths, headers, request shapes, validation regexes, expected status codes
   and ErrorCodes.
2. Exercise the happy path end-to-end (create → read → update → lifecycle → delete),
   threading server-assigned ids and version fields between calls.
3. Exercise the error contract deliberately: missing/invalid token (401), validation
   failures (400), unknown id (404), conflict rules (409), stale expectedVersion (412).
   Assert both the HTTP status AND the ErrorCode in the Result envelope.
4. Clean up test data you created (delete drafts, delist listings). Use a recognizable
   productNo/name prefix so leftovers are easy to spot.
5. Report a compact pass/fail table. For any failure, quote the traceId from the
   response so I can look it up in CloudWatch.

Dev-environment quirks:
- All API responses use the Result envelope: {"code": "...", "data": ..., "message": ...};
  errors add "traceId".
- Auth is the blocbuy-token header (not Authorization Bearer).
- Money fields are strict decimal JSON strings ("19.90"), not numbers.
- Money/size/lifecycle validation is enforced server-side — probe it, don't assume.
```

## Step 3 — When the agent reports a failure

Every error response carries a `traceId`. Search it in CloudWatch Logs Insights
(log group `/blocbuy/backend/dev`):

```sql
fields @timestamp, @message
| filter @message like /04eb739b-a076-49df-be50-75226f4bda4e/
| sort @timestamp desc
```

Full log access, SSM port forwarding (direct backend / PostgreSQL / Redis from your
laptop), and local reproduction: see
[backend-log-observability.md](backend-log-observability.md).

## What good agent output looks like

For reference, a verified run produces evidence like this (UC9 example):

| Check | Expected | Got |
|---|---|---|
| Create draft | 200, server-assigned id/version 0 | ✅ |
| Missing/invalid token | 401 `COMMON_401_001` | ✅ |
| Duplicate productNo | 409 `CATALOG_409_003` | ✅ |
| Zero price / numeric price / wrong currency | 400 `COMMON_400_001/002` | ✅ |
| Stale expectedVersion on revise | 412 `CATALOG_412_002`, no mutation | ✅ |
| Illegal lifecycle (→draft, publish 0 stock) | 400/409 | ✅ |
| Delete published listing | 409 `CATALOG_409_002` | ✅ |
| GET after soft-delete | 404, excluded from page | ✅ |

Treat anything less explicit than this (status + ErrorCode + no side effect) as
unverified and ask the agent to probe deeper.

## Seed demo listings on dev (US34)

Repeatable runbook for the Sprint 3 demo data (#222). Everything except the one-time admin grant
goes through the audited admin API; nothing here deletes data. Plan and decisions:
[seed-dev-listings-plan-20261001.md](../agile/sprint-03/seed-dev-listings-plan-20261001.md).
A converged rerun takes under five minutes; a clean dev (no `US34-*` rows) under fifteen.

| File | Purpose |
|---|---|
| `scripts/grant-dev-admin.sql` | One-time, idempotent `admin` grant for one signed-in account |
| `scripts/dev-demo-baseline.sql` | Read-only counts: demo seed history, listing families, demo deals and member rows, `US34-*` terms, admin count |
| `scripts/dev-demo-listings.json` | The four `US34-*` listings: content, team terms, team price |
| `scripts/seed-dev-demo-listings.sh` | Converges the listings; `--check` issues one quote per listing and writes nothing else |

### 1. Admin grant (once per dev database)

1. Sign in once through CloudFront with the Google account that becomes admin (Step 1 above), so
   its `sys_user` row exists.
2. Open the PostgreSQL tunnel on local port 15432, so it cannot collide with a local PostgreSQL
   (tunnel details: [backend-log-observability.md](backend-log-observability.md) §3):

   ```bash
   aws ssm start-session --target i-0852b6c3475b19f05 \
     --document-name AWS-StartPortForwardingSession \
     --parameters '{"portNumber":["5432"],"localPortNumber":["15432"]}'
   ```

3. In a second terminal, record the baseline, then grant. Type the email at the prompt; do not
   put it in shell history, a file or the log:

   ```bash
   psql -h 127.0.0.1 -p 15432 -U <db user> -d BlocBuy -f scripts/dev-demo-baseline.sql
   read -r -p 'admin email: ' ADMIN_EMAIL
   psql -h 127.0.0.1 -p 15432 -U <db user> -d BlocBuy -v admin_email="$ADMIN_EMAIL" \
     -f scripts/grant-dev-admin.sql
   unset ADMIN_EMAIL
   ```

   First run prints `granted = 1` and the grant row id; a rerun prints `granted = 0`,
   `already_admin = 1`. Record GitHub handle, UTC time, method ("SQL via SSM tunnel") and the
   grant row id in the day log, never the email. Roles are read per request, so the next API
   call already sees `admin`; no new sign-in is needed.

Revoke: `UPDATE domain.rel_user_role SET deleted = true WHERE id = '<grant row id>';`

### 2. Seed or refresh the listings

```bash
read -rs -p 'blocbuy-token: ' BLOCBUY_TOKEN; echo; export BLOCBUY_TOKEN
scripts/seed-dev-demo-listings.sh            # create, publish, configure terms and price
scripts/seed-dev-demo-listings.sh --check    # one quote per listing; exits non-zero on a mismatch
unset BLOCBUY_TOKEN
```

- The token is read from the environment only and passed to curl through a file descriptor, so
  it never appears in the process list or output.
- First run on a clean dev: 12 admin writes (create, publish, team terms per listing). A rerun
  prints `admin writes: 0` unless a policy has fewer than 7 days left, its price changed in the
  JSON, or a listing was set off sale; those are re-converged with one audited command each.
- New policies run from now (minus 60 s for clock skew) for 30 days. Rerun the script at least
  once a fortnight while the demo data is needed.
- Content edits in the JSON are reported as `WARNING content differs` and not applied: listing
  content belongs to the admin console, and some fields freeze after publish.
- Any non-2xx stops the script with the HTTP status, `code`, `message` and `traceId`. A 412 means
  someone edited the listing in between: rerun.

| Listing | Team | Lifetime | Price (individual → team) | Use |
|---|---|---|---|---|
| `US34-01` | 3 | 24 h | 119.00 → 89.00 | demo bloc (step 3) |
| `US34-02` | 3 | 24 h | 39.00 → 29.00 | demo START |
| `US34-03` | 3 | 24 h | 69.00 → 49.00 | demo START |
| `US34-04` | 2 | 40 min | 29.00 → 22.00 | US13 expiry and refund check |

### 3. Open demo bloc (needs Checkout on dev, #223)

START and JOIN intents return an error while `BLOCBUY_STRIPE_CHECKOUT_ENABLED=false`. Once #223
has enabled Sandbox Checkout on dev:

1. Sign in as account A, open `US34-01` on the storefront and start a team. Pay on Stripe's
   hosted page in test mode with one of Stripe's published test cards.
2. After the redirect, `/account` shows the bloc `open` at 1 of 3, and the listing shows it.
3. Sign in as account B (a different teammate), open the invite link or the listing, and press
   Join until Stripe's page opens: the JOIN intent was accepted. Close the page without paying;
   the unpaid intent expires on its own (#225).

A bloc lives at most 24 hours (`team_lifetime_seconds` ≤ 86400). Repeat step 3 on Review day
(2026-10-09) before the demo, and run the baseline query again afterwards for the log.

## Caveats

- Dev data is shared. Prefix test entities (e.g. `UC9-SMOKE-...`) and clean up.
- Non-idempotent POSTs (create listing, issue quote) create a new row per call —
  don't let retry loops spam the dev database.
- Soft-deleted business numbers may be reusable by design (unique only among
  non-deleted rows); check the endpoint's documented contract before reporting it
  as a bug.
- The OAuth redirect URI is bound to the CloudFront URL — always log in through
  CloudFront, even when you hit the backend directly via SSM port forwarding.

## Related

- [backend-log-observability.md](backend-log-observability.md) — logs, traceId, SSM tunnels
- [backend-ec2-pipeline-operator-guide.md](backend-ec2-pipeline-operator-guide.md) — deploy pipeline
