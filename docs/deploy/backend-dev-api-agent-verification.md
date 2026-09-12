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
