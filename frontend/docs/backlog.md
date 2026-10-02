# Frontend backlog

- [`plan.md`](plan.md) was frozen for new tasks on 2026-09-25. Its existing tasks are still ticked there.
- New frontend buyer tasks live here, with IDs `FB-NN`.
- The admin console lives in [`admin-plan.md`](admin-plan.md) (A-tiers).
- Each task is one PR.
- Decisions go in [`design-screens.md`](design-screens.md) or the ADRs ([`adr/`](adr/README.md)), not here.

Priority: P1 = presentation or submission, P2 = quality, P3 = nice-to-have. `FE` = `frontend/`. Commands run from the repository root unless prefixed `cd frontend &&`.

## 1. Task table

| id | priority | task | purpose | files touched | verification command | depends-on |
|---|---|---|---|---|---|---|
| FB-01 | P1 | Demo readiness (steps 1–5 merged in #214 on 2026-09-27; done 2026-09-29) | Mock mode walks the whole buyer journey for the demo and submission. | `FE/src/dev/devtools.tsx`, `FE/src/test/browser-msw.ts`, `FE/src/routes/_authenticated/account.tsx`, `FE/e2e/login.spec.ts`, `FE/README.md` | `cd frontend && pnpm check && pnpm build && pnpm e2e` | — |
| FB-02 | P1 | Shared Playwright API stubs (done 2026-09-29) | One set of `page.route` stubs for the e2e specs, reusing the pattern in `FE/e2e/login.spec.ts`. The production build drops `?msw=1`, so e2e must stub with `page.route`. | `FE/e2e/fixtures/api.ts` (new), `FE/e2e/login.spec.ts` | `cd frontend && pnpm e2e` | — |
| FB-03 | P1 | Buyer-journey e2e (done 2026-09-29) | Catalog → listing → quote → checkout → an intercepted Stripe 302 to `/payments/return` → receipt Waiting then Paid → My blocs. | `FE/e2e/buyer-journey.spec.ts` (new) | `cd frontend && pnpm e2e buyer-journey` | FB-02 |
| FB-04 | P1 | JOIN refusal e2e (done 2026-09-29) | `GROUPBUY_409_004`, `GROUPBUY_409_002`, `GROUPBUY_409_003` and `GROUPBUY_404_001` each name the reason, and no checkout-session call is made. | `FE/e2e/join-refusal.spec.ts` (new) | `cd frontend && pnpm e2e join-refusal` | FB-02 |
| FB-05 | P1 | Quote e2e (done 2026-09-29) | 15-minute expiry with `page.clock`, then re-issue; a 409 `CATALOG_409_001` shows no price. | `FE/e2e/quote.spec.ts` (new) | `cd frontend && pnpm e2e quote` | FB-02 |
| FB-06 | P2 | CI e2e comment and dev-safe specs | Fix the stale "Only the home test runs here" comment in the `e2e-preview` job; keep the new specs safe to run against dev. | `.github/workflows/ci-frontend.yml` | `docker run --rm -v "$PWD:/repo:ro" -w /repo rhysd/actionlint:latest -color` | FB-03, FB-04, FB-05 |
| FB-07 | P2 | SonarCloud coverage exclusions | Exclude `src/main.tsx`, `src/router.tsx` and `src/dev/**` from coverage in the `sonar-frontend` job args, or add a router test instead. | `.github/workflows/ci-frontend.yml` | `sonar-frontend` job green on the PR | — |
| FB-08 | P2 | Unit gaps | Catalog listings 500 alert and recovery; an unknown route renders the 404 card through the route tree; `/account` loading skeletons (`AccountLoading`, `BlocsSkeleton`). | `FE/src/routes/index.test.tsx`, `FE/src/routes/account.test.tsx` | `cd frontend && pnpm vitest run src/routes` | — |
| FB-09 | P1 | Login error variant has no h1 (done 2026-09-29) | The `?error=` variant of `/login` needs a page heading. | `FE/src/routes/login.tsx`, `FE/src/routes/login.test.tsx` | `cd frontend && pnpm vitest run src/routes/login.test.tsx` | — |
| FB-10 | P2 | PressResponder console warning | "PressResponder was rendered without a pressable child" from the controlled HeroUI Modals. | `FE/src/features/session/GatedActionSheet.tsx`, `FE/src/features/payment/QuoteSheet.tsx` | `cd frontend && pnpm vitest run src/features` shows no warning | — |
| FB-11 | P2 | Gated sheet trust triptych | The gated sheet lacks the artboard's trust triptych. | `FE/src/features/session/GatedActionSheet.tsx` | `cd frontend && pnpm vitest run src/features/session` plus a screenshot | — |
| FB-12 | P2 | Per-route document titles | Each route sets `document.title`, e.g. "Thai jasmine rice · BlocBuy". | `FE/src/routes/*.tsx`, `FE/src/routes/_authenticated/*.tsx` | `cd frontend && pnpm vitest run src/routes` | — |
| FB-13 | P3 | Google "G" mark | Replace the placeholder tile with the official brand asset plus an attribution note (CR-16). | `FE/src/features/session/GoogleSignInButton.tsx`, `FE/public/` | `cd frontend && pnpm check` plus a screenshot | — |
| FB-14 | P3 | Pagination demo | More catalog seeds so `?page=2` exists in mock mode. | `FE/src/test/browser-msw.ts` | `cd frontend && pnpm dev`, open `/?msw=1&page=2` | — |
| FB-15 | P3 | Refund-pending receipt heading | The heading reads "Payment received"; consider "Refund on its way". Needs the design owner. | `FE/src/features/payment/receipt-view.ts` | `cd frontend && pnpm vitest run src/features/payment` | design owner |
| FB-16 | P3 | Catalog cards at 1280 | Cards have an empty gap because the summary has no bloc size fields. | `FE/src/features/catalog/ProductCard.tsx` | screenshot at 1280 px | backend ask CR-11 |
| FB-17 | P3 | Tooling hygiene | `routeFileIgnorePattern` for `*.test.tsx` (the dev server warns 5 times); clear the 4 knip config hints. | `FE/vite.config.ts`, `FE/knip.json` | `cd frontend && pnpm dev` (no route warnings) and `cd frontend && pnpm knip` (no hints) | — |
| FB-18 | P3 | Buyer-screen documentation | Audit `design-screens.md` §7 against shipped UI and the current contract; link remaining gaps to the owning backlog item or issue without recreating the historical Q1–Q22 list. Repository licensing is a separate, repo-level decision. | `FE/docs/design-screens.md` | Review §7 against `FE/src/`, `contracts/openapi.json` and backlog links; `git diff --check` | — |
| FB-19 | P2 | Sharer nickname on the invite banner (done 2026-10-02, #344) | Backend #201 (closed 2026-09-30) resolves an invite ref to the sharer's nickname, the invite banner leads with the Invite artboard's "{sharerNickname} started this bloc" (5b.1–5b.3, design-screens §4.2) instead of the generic "You’ve been invited to join this bloc". | `FE/src/features/share/InviteBanner.tsx`, `FE/src/routes/products.$productId.tsx`, `FE/src/test/browser-msw.ts`, `FE/src/routes/products.$productId.test.tsx` | `cd frontend && pnpm vitest run src/routes/products.\$productId.test.tsx` | — (backend #201 closed 2026-09-30) |
| FB-20 | P2 | Hide Join from a member of the open bloc (done 2026-09-29) | A buyer already in the open bloc (a `GET /group-deals/me` row matches `p.deal.dealId`; the listing computes `myTeam`) still sees an enabled "Join this bloc" on the Open bloc card beside "Share your bloc", because `onJoin` never checks `myTeam`; pressing it only earns `GROUPBUY_409_003` (no charge). With payments on, a member sees Join disabled with the reason "You're in this bloc" and no `POST /payments/intents` is sent; a non-member keeps an enabled Join. | `FE/src/routes/products.$productId.tsx`, `FE/src/features/catalog/DealProgress.tsx`, `FE/src/routes/products.$productId.test.tsx`, `FE/src/features/catalog/DealProgress.test.tsx` | `cd frontend && pnpm vitest run src/routes/products.\$productId.test.tsx src/features/catalog/DealProgress.test.tsx` | — |
| FB-21 | P3 | In-session smoke of the Claude Code hooks (from `plan.md` T1-10 step 5) | The three frontend hooks in `.claude/settings.json` were only driven by piping their stdin payloads to the scripts (plan §12 note 31(a)); nobody has confirmed them in a real Claude Code session at the repo root. | `docs/agile/sprint-03/log/` (results only; no code change expected) | Claude Code session at the repo root, steps below | — |
| FB-22 | P2 | Verify `Secure` on the auth cookies once backend #96 lands (from `plan.md` B3) | The `ec2` profile still sets `sa-token.cookie.secure: false` and `blocbuy.frontend.state-cookie-secure: false`, so on dev `blocbuy-oauth-state` (and `blocbuy-token`) go out without `Secure` although browsers reach the backend only over HTTPS. The fix is backend (#96, @l0sgAi); the frontend part is verification. | none in `FE/` (backend: `backend/blocbuy-bootstrap/src/main/resources/application-ec2.yml`) | `curl -sSI https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google \| grep -i set-cookie` shows `Secure` on `blocbuy-oauth-state` | backend #96 |

## 2. Steps

### FB-01: Demo readiness

- [x] Step 1 (2026-09-25): dev devtools mount only with `?devtools=1` (`bf3791a`).
- [x] Step 2 (2026-09-25): mock blocs, orders and receipts come from one set of seeds (`6ef346a`).
- [x] Step 3 (2026-09-25): the duplicate name row is gone from the `/account` header (`48a6275`).
- [x] Step 4 (2026-09-25): the mock START/JOIN checkout reaches a receipt that goes Waiting then Paid (`28e0a83`).
- [x] Step 5 (2026-09-25): `frontend/README.md` gained a ten-step demo script (`384ad1d`); the current README condenses it to five journeys while retaining the mock-mode instructions.
- [x] Step 6 (2026-09-29): rerun the `@auth` e2e against dev (`E2E_TARGET=dev E2E_BASE_URL=https://d1b3np88z657cp.cloudfront.net pnpm e2e --grep @auth`), because `48a6275` changed its assertions: 2 passed from `ddba7d3`, on a storage state seeded from a normal-Chrome `blocbuy-token`.

### FB-02: Shared Playwright API stubs

- [x] Step 1 (2026-09-29): move the `page.route` stubs out of `e2e/login.spec.ts` into `e2e/fixtures/api.ts` (me, listings, listing, intents, receipts).
- [x] Step 2 (2026-09-29): `login.spec.ts` uses the fixture; `pnpm e2e` still passes.

### FB-03: Buyer-journey e2e

- [x] Step 1 (2026-09-29): catalog → listing → quote sheet → Continue to payment.
- [x] Step 2 (2026-09-29): intercept the Stripe 302 and land on `/payments/return`; the receipt shows Waiting then Paid; My blocs lists the bloc.

### FB-04: JOIN refusal e2e

- [x] Step 1 (2026-09-29): one case per code (`GROUPBUY_409_004`, `_002`, `_003`, `GROUPBUY_404_001`): the reason and "nothing was charged" show, and no checkout-session request is made.

### FB-05: Quote e2e

- [x] Step 1 (2026-09-29): `page.clock` moves past 15 minutes; payment is disabled and "Get a new price" re-issues.
- [x] Step 2 (2026-09-29): a 409 `CATALOG_409_001` shows no price and no pay button.

### FB-06 to FB-18

- [ ] One step each: the task row is the whole task; tick it with the date and PR when it merges.

### FB-19: Sharer nickname on the invite banner

- [x] Step 1 (done 2026-10-02, #344): regenerate the API client and read the sharer's nickname for the parked ref through `GET /api/v1/invites/{ref}` (#201); the mock (`?msw=1`) returns one.
- [x] Step 2 (done 2026-10-02, #344): the banner leads with "{sharerNickname} started this bloc" and keeps the generic lead when the ref has no nickname, resolves to another bloc, or the call fails; tests cover both.

### FB-20: Hide Join from a member of the open bloc

- [x] Step 1 (2026-09-29): `onJoin` on the listing is undefined when `myTeam` is set, and `DealProgress` gains a member reason, "You're in this bloc", beside its full and ended ones (today it names a reason only for those); frontend only, since the listing's `ActiveDealResponse` has no membership field and `/group-deals/me` is already fetched.
- [x] Step 2 (2026-09-29): tests: with payments on, a member gets a disabled Join and no `POST /payments/intents`; a non-member still gets an enabled Join.

### FB-21: In-session smoke of the Claude Code hooks

Preconditions: `jq` on `PATH` (the PostToolUse and Stop scripts exit 0 without it) and `frontend/node_modules` installed (both scripts skip without it).

- [ ] Step 1: edit `frontend/src/routes/index.tsx` with a badly formatted line; confirm the PostToolUse `Edit|Write` hook (`.claude/hooks/frontend-fix.sh`: `oxfmt`, then `oxlint --fix` for `.ts`/`.tsx`) reformats it.
- [ ] Step 2: end the turn with that change still in the tree; confirm the Stop hook (`.claude/hooks/frontend-typecheck.sh`) runs `pnpm exec tsc --noEmit` (it runs only when `git status` shows frontend changes outside `frontend/docs/` and `frontend/e2e/.auth/`, and exits 2 with the errors on stderr).
- [ ] Step 3: delete `frontend/src/api/generated` and start a new session; confirm the SessionStart hook (`.claude/hooks/frontend-bootstrap.sh`, matcher `startup|resume`) regenerates it and prints "generated client was missing".
- [ ] Step 4: note the results in the sprint log.

### FB-22: Verify `Secure` on the auth cookies (backend #96)

- [ ] Step 1: after #96 merges and deploys (the `ec2` profile sets both `sa-token.cookie.secure: true` and `blocbuy.frontend.state-cookie-secure: true`), `curl -sSI https://d1b3np88z657cp.cloudfront.net/api/v1/auth/oauth2/google | grep -i set-cookie` shows `Secure` on `blocbuy-oauth-state`.
- [ ] Step 2: after a Google login on dev, the `blocbuy-token` cookie shows `Secure` in DevTools; note both results in the sprint log.

## 3. Blocked / external

- T3-05 (share attribution, tracked in `plan.md`) merged in #219 on 2026-09-28 and closed #30; it builds on #198 (merged 2026-09-26: `POST /api/v1/group-deals/{dealId}/invites` and attribution at purchase). The sharer's nickname on the invite banner no longer waits on the backend: #201 closed on 2026-09-30 with `GET /api/v1/invites/{ref}` (anonymous, `no-store`), and FB-19 reads it (done 2026-10-02, #344).
- B3 (the `Secure` flag on `blocbuy-oauth-state` and `blocbuy-token`, tracked in `plan.md` §10) waits on the backend: the `ec2` profile still sets both cookie flags to `false` (backend #96, open, @l0sgAi); the frontend verification is FB-22.
- The admin console: see [`admin-plan.md`](admin-plan.md); its Q1 was answered on 2026-09-26 (#202): only A0-00 is in Sprint 3, and every A1 and A2 row is deferred.
