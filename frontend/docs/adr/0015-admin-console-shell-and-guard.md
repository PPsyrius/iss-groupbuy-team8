# 0015. Admin console: second shell, roles guard, pessimistic versioned writes

Date: 2026-10-01 (records the decisions shipped by admin plan A1-01 to A2-03 from #294 on)

## Status

Accepted

## Context

UC9 needs a console for listings and bloc price rules (`frontend/docs/admin-plan.md`, artboards `BlocBuy Admin Console.dc.html` 4a and `BlocBuy Admin Editor.dc.html` 4b). The artboards draw a dense, desktop-only shell with a dark rail that must never be confused with the storefront. The backend enforces RBAC on every `/api/v1/admin/**` call and makes every listing and policy write conditional on a version: a stale `expectedVersion` answers 412, a forbidden state change 409, and publishing freezes price, currency and stock for good (`backend/docs/design/admin-listings.md`). When the console was planned, `me` carried no roles (CR-9).

## Decision

1. **Second shell, same SPA.** The console is the route subtree `src/routes/_authenticated/admin*` with its own `AdminShell` layout. Route `staticData: { shell: 'admin' }` tells `__root.tsx` to skip the storefront header; the storefront routes are not moved under a new layout. Nesting under `_authenticated` reuses the login redirect and `returnTo`. Code lives in `src/features/admin/`.
2. **Roles guard, UX only.** The layout's `beforeLoad` reads `session.roles` and throws `AdminAccessDenied` without the `admin` role; the layout's error boundary renders the Access denied card in place at `/admin`, not a redirect. The first version probed `GET /admin/products?page=1&size=1` and turned its 403 into the card; #282 (BA-1) added `roles` to `me` and A3-01 (#318) replaced the probe, so the guard makes no request. Every admin read also rethrows a 403 (`isAdminDenied`) to the same boundary, so a role revoked mid-session reaches Access denied instead of stale data (A2-02 #328, FU-01 #329).
3. **Pessimistic versioned writes.** Every write sends the loaded `version` (`expectedVersion` for policies, 0 when none exists). There are no optimistic cache writes: on success the response is written to the read cache and page queries and counts are invalidated; buttons are disabled while pending. The editor reads fresh (no `staleTime: 'static'`) and keys the form by version.
4. **Conflict means Reload.** A 412 always ends in the warning banner ("You loaded v4; the server now has v5", version from the error since #286) and Reload; there is no merge and no auto-retry. A 409 maps its code to copy. Field-specific 400s attach to fields through `fieldViolations` (#323); the rest read in a form alert.
5. **Generated client, wrapped once.** `admin-api.ts` is the only file that names the generated admin operations; other files use its named wrappers (ADR 0003, 0009).

Alternatives rejected: a separate admin app or build (a second artifact and deploy for a few screens, ADR 0004); a pathless storefront layout to host the header (renames every storefront route for no behaviour); optimistic updates (every write can fail on version, state or freeze rules, and publish is irreversible); auto-merging on 412 (the backend doc says clients must re-read, not overwrite).

## Consequences

- The guard is not a security boundary; the backend's role check is. A buyer who forges the client state sees only 403s, which reach the same card.
- Two shells share one bundle and one router; the admin subtree is code-split by route.
- Any concurrent edit costs the second editor their unsaved changes after Reload. The banner says so; this is accepted for a small admin team.
- A future-dated policy revision replaces the active one at once (latest wins, no fallback); the form warns but does not block (admin plan Q15). The rule end is exclusive: the form sends the exact instant and every display shows it minus 1 minute (Q13).
- Undrawn admin states (plan §11 D1 to D8) carry proposed copy until the design owner answers; `design-screens.md` §4.7 lists them.

2026-10-27 addendum (#636/#641): image URL validation in `src/features/admin/image-policy.ts` derives exact HTTPS origins from the canonical CloudFront CSP JSON rather than maintaining a second host list or accepting arbitrary HTTPS images that browsers will block. Unsupported existing cover/gallery URLs remain visible with an immediate warning and must be replaced or removed before saving; approved URLs alone are previewed. Silently discarding legacy values was rejected because it would hide a content change. Backend/API validation and historical snapshots are unchanged; this is a compatibility guard, not a security boundary. Operators must inventory all stored origins before edge apply, and source changes to the image allowlist require rebuilding the SPA as well as publishing the Terraform policy.
