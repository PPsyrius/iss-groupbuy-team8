# BlocBuy buyer screens

This document is the implementation reference for the buyer-facing Claude Design artboards used during Sprint 2. It records the visual rules that matter to the frontend and the places where the current API contract limits the design.

**Design snapshot:** Claude Design project `BlocBuy Prototype`, local export dated 2026-09-17 and reviewed on 2026-09-18. The raw export was copied to `.claude/design-artboards/` for implementation work and is intentionally not tracked. If the upstream design changes, update this document rather than depending on the old export.

The admin canvases are covered in §4.7 (added 2026-10-01, admin plan A2-04). Checkout, invite, raw component assets, thumbnails, and generated icon/assets files were outside the buyer-screen pass.

## 1. Prototype structure

The export contains standalone `*.dc.html` canvases rather than a linked application. Navigation is described in `BlocBuy Flow Maps.dc.html`. Screen data and exact copy live in each artboard's `renderVals()` data.

Relevant buyer canvases (ASCII inventory — layout intentional):

```text
  ----------------------------------------------------------------------------
  File                                     States
---------------------------------------- -----------------------------------
  `BlocBuy Buyer Catalog.dc.html`          loading, populated, empty category,
                                           error, pagination at 390 and 1280

  `BlocBuy Buyer Listing Detail.dc.html`   loading, open bloc, no open bloc,
                                           unavailable, error at 390 and 1280

  `BlocBuy Buyer Sign In.dc.html`          default, gated-return, provider
                                           error, gated sheet/modal at 390 and
                                           1280

  `BlocBuy Buyer My Blocs.dc.html`         active, refunded, loading, empty,
                                           bloc detail at 390

  `BlocBuy Buyer Errors.dc.html`           not found, access denied, global
                                           error at 390 and 1280

  `BlocBuy Design System.dc.html`          design tokens and usage rules

  `BlocBuy HeroUI Kit.dc.html`             component reference

  `BlocBuy Flow Maps.dc.html`              buyer/admin flow descriptions
  ----------------------------------------------------------------------------
```

The artboards are drawn at 390px and 1280px. Intermediate responsive behavior is an implementation decision unless explicitly annotated.

## 2. Design tokens

`frontend/docs/design.md` is the maintained token reference. Product code should use semantic HeroUI/Tailwind tokens rather than raw values.

The shipped buyer UI is light-only. Although `design.md` contains a BlocBuy dark palette for completeness, the artboards and current implementation do not define dark-mode behavior.

### 2.1 Core colour usage

```text
  ----------------------------------------------------------------------------
  Token                       Light value              Use
--------------------------- ------------------------ -----------------------
  `background`                `#F5F5F5`                page canvas

  `surface`                   `#FFFFFF`                cards, panels, overlays

  `surface-secondary`         `#EFEFEF`                nested surfaces

  `surface-tertiary`          `#EAEAEA`                skeletons, progress
                                                       tracks, image fallback

  `foreground`                `#18181B`                primary text/icons

  `muted`                     `#727272`                secondary text, mainly
                                                       on surfaces

  `border`                    `#DEDEDE`                card/control borders

  `default`                   `#EBEBEB`                neutral controls and
                                                       chips

  `accent`                    `#FF385C`                progress and focus; not
                                                       body text

  `accent-soft`               `rgba(255,56,92,0.15)`   selected/soft emphasis

  `accent-soft-foreground`    `#B43648`                bloc price and accent
                                                       text

  `success-soft-foreground`   `#1F5357`                refund/safe-money copy

  `warning-soft-foreground`   `#8A422C`                urgent countdown/status

  `danger-soft-foreground`    `#802F20`                error copy

  `focus`                     `#FF385C`                keyboard focus ring
  ----------------------------------------------------------------------------
```

The raw accent does not meet the intended text contrast on the page background, so buyer screens use `accent-soft-foreground` for accent-coloured text and neutral button fills for primary actions.

### 2.2 Typography

Inter, weights 400/500/600/700, with `"Helvetica Neue", Helvetica, Arial, sans-serif` fallback.

Use tabular numbers for prices, countdowns, and counts. Main scale:

```text
  Token    Size / line-height   Typical use
-------- -------------------- ---------------------------
  `xs`     12 / 16              chips, labels
  `sm`     14 / 20              metadata
  `base`   16 / 24              body
  `lg`     18 / 28              buttons/section headings
  `xl`     20 / 28              headings
  `2xl`    24 / 32              phone detail/title
  `3xl`    30 / 36              sign-in/desktop detail
  `4xl`    36 / 40              desktop hero/detail price
```

Nothing in the buyer UI should be smaller than `xs`.

### 2.3 Spacing and responsive rules

- Base spacing unit: 4px.
- Phone gutter: 16px; desktop artboards: 32px.
- Minimum phone touch target: 44px.
- Surface/button/card radius: 8px; fields and overlays: 12px; chips/avatars/progress: full radius.
- Breakpoints follow the project Tailwind/HeroUI setup. Catalog grid is 1 column at 390, 2 at 768, and 4 at 1280.
- Use the documented `surface` and `overlay` shadows; do not stack custom shadows on HeroUI cards.

## 3. Shared chrome

- Buyer artboards have no footer.
- Catalog header is 56px on phone and 64px on desktop, with the BlocBuy wordmark on the left.
- Signed-out state uses **Sign in**, styled as a neutral/secondary control with accent-soft-foreground text.
- Signed-in state uses avatar/name in the current implementation. Notification/bell UI waits for contract-backed notification data.
- Sub-pages use a back/crumb pattern on phone and breadcrumbs on desktop where appropriate.
- Buyer primary actions use neutral `secondary` styling rather than an accent fill because of contrast.
- Secondary actions use `outline`; tertiary actions use `ghost`.
- Page canvas uses `background`; cards/panels use `surface`.

## 4. Screens

### 4.1 Catalog (`/`)

Implemented in T2-01/T2-03.

**Layout**

Phone uses a 16px gutter and a one-column card stack. Desktop uses 32px padding and a four-column grid. The hero copy is:

- **Buy better together**
- Phone: "Fill a bloc with your neighbours and the lower price unlocks."
- Desktop adds: "Pay once, get refunded in full if the bloc doesn't fill."

Default page size is 20. Pagination uses Previous / "Page X of Y" / Next; there is no infinite scroll.

**Filters and search**

The artboard defines a single-select chip row: All, Groceries, Household, Electronics, Beauty, Kids. The implementation keeps that interaction and adds the contract-backed keyword `SearchField`. Category/keyword changes reset to page 1.

Category values are mapped centrally because the API contract does not publish a category dictionary. Current known values come from backend/demo data.

**Listing cards**

Cards use a bordered 8px surface, image/fallback area, title/subtitle, and price block.

- Open bloc: bloc price is prominent in `accent-soft-foreground`; solo price is struck through.
- Neither a bloc price nor valid open-bloc progress: solo price is prominent and the secondary line says "Buying alone". With valid progress but no bloc price, keep the solo price unstruck and omit "Buying alone"; do not invent a bloc price.
- No percent-off badges or countdowns on the catalog grid.
- Open bloc progress (FB-16): "N of M joined", seats left (the last seat in semibold) and a 10px accent bar, from the summary's nullable `deal` (#375); no row when `deal` is null. No countdown on the grid. Missing, fractional, negative or non-finite sizes do not produce invented counts or suppress the no-bloc fallback when there is no bloc price; the progress row remains part of the card's detail-link click area.

SGD is displayed as `S$` with two decimals.

**States**

- Loading: chip and card skeletons in final geometry.
- Empty category: selected chips remain visible with a recovery action.
- Empty keyword result: same empty-state treatment with a clear-search action.
- Error: danger alert with retry.
- Out-of-range `page` redirects to the last valid page.

### 4.2 Listing detail (`/products/$productId`)

Implemented in T2-02, with gated actions added in T2-04.

Phone is a single-column layout with gallery, listing information, open-bloc section, about card, trust copy, and sticky action bar. Desktop uses a two-column layout with gallery/about on the left and price/bloc actions on the right.

**Price and availability**

With a bloc, show the bloc price prominently and strike the solo price. Without one, show the solo price and:

> Buying alone. No bloc is open yet.

The artboard includes an off-sale state, but `ProductDetailResponse` currently has no status field, so the frontend cannot implement that state from the contract.

On phone the gallery dots sit below the image, rather than in the artboard's bottom-right overlay, so each retains a 44px touch target even with up to 20 images (#204, 2026-09-29).

**Open bloc**

The current contract exposes a single `ActiveDealResponse` with `dealId`, target/current size, and expiry. It does not expose starter identity, bloc number, or a list of open blocs. The implementation therefore labels the row **Open bloc** rather than inventing a starter.

Progress shows joined/target, seats left, progress bar, and deadline/countdown. Invalid or missing expiry values must not throw.

**Actions**

Anonymous paid actions open the sign-in sheet. Signed-in Start opens the quote sheet (T3-01), and START is charged the quoted price (US26); JOIN starts a server-owned purchase intent (T3-04), but full and ended blocs disable the button with a reason. Buy alone uses the same checkout boundary (ADR 0014).

**Invite landing and sharing (T3-05, #30):** A share link uses `/products/$productId?deal={dealId}&ref={ref}`; the listing stays the landing. Validated `deal` and `ref` are parked in `sessionStorage` (last link wins), then removed from the URL; an invalid new link clears the parked invitation. The reference is used twice: on page load the listing resolves it through `GET /api/v1/invites/{ref}` (display only), and the purchase flow sends it with JOIN for that bloc only, where it remains available if the buyer must replace a stale intent. A matching open bloc displays an invite card with primary "Join this bloc" and downgrades Start a bloc to an outline action; a closed invitation offers recovery. Since FB-19 that page-load resolution (#201) runs beside the listing request, and the loader waits for it at most 1.5 s: with a nickname the card leads with the sharer's initials avatar and "{nickname} started this bloc / and invited you to join it" (5b.1), and a closed invitation reads "{nickname}’s bloc has closed"; no nickname, a ref for another bloc or listing, a failed call, or one past the deadline keeps the generic lead.

A signed-in member of the listing's open bloc sees a share block below Open blocs: a link field, Copy with inline confirmation, and Share where the browser supports `navigator.share`. The link is minted by `POST /group-deals/{dealId}/invites`; closed/full blocs do not offer sharing. The share block distinguishes a non-member (404), a bloc no longer taking members (409), and other retryable failures.

**Errors**

HTTP/not-found shapes map to the designed 404 treatment where possible; other failures use the listing error alert. Missing detail data in a successful envelope is treated as an API error rather than allowed to fail inside rendering.

### 4.3 Sign-in and gated action (`/login?returnTo=...`)

Implemented in T2-04/T2-06.

The sign-in page uses **Continue with Google** and explains that browsing is public while paid actions require an account. There is no email/password path.

Anonymous paid actions open a bottom sheet on phone and a modal on desktop. Dismissing the gate leaves the listing in place.

The backend does not carry the original return URL through OAuth, so the frontend validates the return path and stores it in `sessionStorage` before navigating to `/api/v1/auth/oauth2/google`. `/auth/callback` confirms the session and restores that path.

Backend provider failures return to `/login?error={errorCode}` and are mapped to user-facing retry/cancellation messages.

### 4.4 My Blocs / account shell

The artboard defines active/refunded/loading/empty My Blocs states and detailed bloc views. T3-03 (#210) now reads the owner-scoped `GET /api/v1/group-deals/me` and shows Active / Completed / Refunded chips; the endpoint does not provide refund status, payment IDs or bloc numbers, so closed rows say "refund due" rather than claiming a refund was sent.

`/account` shows My blocs above the paginated buyer order history (T3-02). It fetches up to 100 recent blocs and filters those rows client-side; any excess is disclosed. Starter metadata and payment-specific refund chips remain unimplemented because they are not in the team-list response.

An active row with seats and time left offers a 44px "Share invite link" action. It mints an invitation on first expansion and reuses it on reopen; the expanded block has Copy and, when supported, Share. Filled, closed, ended or full blocs and removed listings have no share action. The bloc-detail screen itself remains unbuilt; the listing and account reuse the share UI.

### 4.5 Errors

The buyer error composition is shared across 404, 403, and 500 states: compact card, clear heading/body, money-safety line, recovery actions, and optional hint.

- **404:** "We can't find that page"; Browse listings; My blocs when available.
- **403:** admin access denied; buyer account/blocs remain unaffected.
- **500:** "Something went wrong on our side"; Try again / Browse listings.
- **401:** no dedicated page. Anonymous paid actions go through the gated sign-in flow.

### 4.6 Payment receipt and return (`/payments/$paymentId`, `/payments/return`) — no artboard

The payment slice shipped in PR #126 without a dedicated receipt artboard. On 2026-09-24 the design owner answered Q23–Q26 for T3-06: use §4.5's terminal card for the receipt, with a payment-status chip (`Paid`, `Waiting`, `Failed`, `Closed`, `Refund pending`, `Refunded`, `Needs review` or `Unavailable`) and Payment reference, Order reference and Bloc reference rows when those IDs are available. The money line carries the amount and the buyer order's `paidTime` (the receipt has no issue time), or the fixed refund window (usually 5–10 business days). The contract supplies no Stripe `pi_…` reference.

While `CREATED`, copy says the seat is held once Stripe confirms and provides "Refresh status"; the receipt continues to poll backend state. A settled receipt leads with "My blocs" and can link back to its listing via the buyer's order. The return page redirects to the receipt when a valid reference exists; otherwise its §4.5 fallback uses a neutral `Payment` chip and says not to pay again to check status. ADR 0014 owns the payment behavior; the dated Sprint 2 log records the verification limits.

### 4.7 Admin console (`/admin`, `/admin/listings`, `/admin/listings/new`, `/admin/listings/$productId`)

Implemented in admin plan A1-01 to A1-08, A2-01 to A2-03 and A3-01 to A3-09 (`admin-plan.md`; ADR 0015). Spec: `BlocBuy Admin Console.dc.html` (batch 4a) and `BlocBuy Admin Editor.dc.html` (batch 4b), plus the admin parts of `BlocBuy Flow Maps.dc.html` (Flow 2) and `BlocBuy Buyer Errors.dc.html`. Where the artboards and the brief differ, the artboards win. Citations below read `file › artboard`.

**Shell and access** (`Admin Console › 4a.1`, `Buyer Errors › Access denied`)

- The console is a second shell in the same SPA: a 240px rail on `background-inverse` with the isologo, an `ADMIN` tag, Dashboard and Listings links, and the user block (initials, `nickname ?? userId`, "Admin" from `me.roles`). The storefront header is hidden through route `staticData.shell`.
- Desktop only and dense: the content column keeps 1024px and scrolls sideways below that (Q12). All times are SGT.
- Anonymous visitors go to `/login?returnTo=%2Fadmin`. A signed-in session without the `admin` role gets the Access denied card in place at `/admin` ("You don’t have access to the admin console", Browse listings / My blocs), under a wordmark-only bar. A 403 from any later admin read (a role revoked mid-session) reaches the same card, from the dashboard, the listings table, the tab counts and the editor (FU-01). The guard is UX only; the backend checks the role on every call.
- Not built: the "Bloc price rules" rail item (Q3, decided 2026-10-05: a count and a link to the listings filtered to an active rule, waiting on BA-13, #382) and "Payments" (BA-8, artboard 4b.6), so the rail shows no counts (D1).

**Dashboard** (`Admin Console › 4a.1`)

- "Dashboard", the date line "Wednesday 16 September 2026 · all times SGT" on a one-minute clock, and a secondary "New listing".
- Four neutral stat cards from the `counts` read: Drafts, On sale, Off sale, Open blocs (`openDeals`). No accent, no trend; the artboard's "filling across N listings" has no source and is left out. Counts show a skeleton while loading and "—" if the read fails.
- Recent listings: the listings table in a seven-column variant (no Sold, no rule line, no row menu), five newest rows, "View all" into the table.
- Empty: "No listings yet" with "New listing" (D4). Error: "We couldn’t load the dashboard" with Try again.

**Listings table** (`Admin Console › 4a.2`, `4a.3`, `4a.4`)

- Tabs All / Draft / On sale / Off sale with counts from one `counts` read; server pagination (`page`, `size` 20) with "Showing 1–8 of 62 · Page 1 of 4"; no sorting.
- Each title carries the latest bloc price rule from `latestPolicy`: "Bloc price S$71.00 · valid to 30 Sep", "… · expired 14 Sep", "… · starts 1 Oct", "No bloc price rule yet" on a published listing and "Draft · not published" on a draft. The day is the SGT day of the last valid minute.
- The row menu (HeroUI `Dropdown`) offers Edit plus the one lifecycle action the status allows, reusing the editor's dialogs (Q14).
- Loading: skeleton rows with the tab counts withheld (4a.4). Empty per tab: "No drafts" (4a.3, the header CTA drops to outline), "Nothing on sale", "Nothing off sale", "No listings yet" (D4). Error: "We couldn’t load the listings" with Try again.

**Listing form and editor** (`Admin Editor › 4b.1`, `4b.2`, `4b.3`)

- New draft (4b.1): Title, Subtitle, Category, Description (D7), Solo price, Stock, Cover image URL and up to 20 Image URLs (`https://` only). The server assigns the `BB-` product number on save (#288).
- Editor (4b.2): header "BB-100472 · draft · v0", Save draft and Publish, a sidebar with the status, version and lifecycle trail. The editor reads fresh and the form is keyed by version.
- Published (4b.3): Solo price, Currency and Stock are frozen with a chip and reason; Sold is read-only. "Take off sale" and "Put back on sale" use confirm dialogs with the D2 copy.
- Delete: a clean draft offers "Delete draft" ("Delete this draft?", danger); otherwise the reason reads with counts from `references`, for example "Not available. This listing has been published and has 26 bloc records, 61 quotes and 3 price rules."
- Leaving with unsaved edits, in the listing form or the rule form, asks "Discard your changes?" (D5). A deleted draft raises a "Draft deleted" toast.

**Errors and conflicts** (`Admin Editor › 4b.4`)

- Bean-validation and domain 400s that name a field (`COMMON_400_002`, `CATALOG_400_001`/`_002` since #323) attach their message to that field; a path with no form field, or a 400 without `errors`, reads in the form alert ("Some fields need another look").
- A 412 shows the page-level warning "This listing was updated by someone else" with "You loaded v4; the server now has v5" (from the error's `currentVersion`, #286) and Reload. There is no merge and no auto-retry.
- A 409 on a lifecycle action maps its code to copy ("The listing was not changed").

**Bloc price rule panel** (`Admin Editor › 4b.5`, `4b.3` footnote, `4b.4` field errors)

- Under the Status card in the editor sidebar. States: No rule yet, Scheduled ("Starts 1 Oct", D3), Active, Expired, conflict ("Not saved", Reload) and unavailable. A draft reads "Add a rule after you publish. The bloc price must be strictly below the solo price." (D8, BA-11) and an off-sale listing "Put the listing back on sale to add a revision."
- The bloc price shows in `accent-soft-foreground` over the struck solo price, with the window "Valid 1 Sep 2026, 12.00 am → 30 Sep 2026, 11.59 pm SGT" (4b.3's full form, so a start that is not midnight keeps its time).
- "Add a rule" or "New revision" opens the inline form: Bloc price, Valid from and Valid until as minute-granularity SGT date pickers. The amount must be strictly below the solo price and the end after the start and in the future. The footnote reads "Saving appends a revision. Prices already locked into a quote never change."
- Q13: the backend's `validUntil` is exclusive. The form sends the exact end instant and every display, the Valid until picker included, shows it minus 1 minute (4b.4's "11.59 pm").
- Q15: when a rule is active and the new start is in the future, the form warns "Saving replaces the active rule now. Buyers see no bloc price until …". There is no blocking confirm.
- A 412 (`CATALOG_412_001`) turns the panel to the conflict state; a 409 to the unavailable state; a domain 400 lands on `amount`, `validFrom` or `validUntil`, else the form alert.

**Group terms panel** (no artboard; §11 D6, decided 2026-10-05, A3-04)

- A "Group terms" card under the bloc price rule panel, on every status. The chip reads "Ready to start blocs" when both terms are set (`configured`), else "Not set"; the body reads "New blocs use these terms. Blocs already started keep theirs." or "Buyers can’t start a bloc on this listing until both are set."
- Fields: Seats per bloc ("Paid members a bloc needs to complete. At least 2.") and Bloc lifetime as Hours and Minutes ("40 minutes to 24 hours, from the bloc’s start."), sent as `lifetimeSeconds`. Save terms writes through `configureTeamTerms` with the listing version and never sends `teamPrice`; "Terms saved." confirms it.
- The write bumps the listing version, so Save waits while the listing form has unsaved edits ("Save your listing changes first."), and a 412 raises the page's conflict banner. Field 400s land on Seats per bloc or Hours.

**Design gaps still open** (`admin-plan.md` §11)

- D1 rail counts (Bloc price rules links to the filtered listings once BA-13, #382, merges; Payments has no page); D2 dialog copy is the proposed text, not approved (Q7); D3 the scheduled state and replace-now warning are undrawn; D4 dashboard and per-tab empty, table error; D5 unsaved prompt and toasts; D6 the group terms panel (decided 2026-10-05: a card below the price rule panel; built in A3-04, #399); D7 Description is missing from 4b.1; D8 the "Add a rule after you publish" copy waits on BA-11.
- Not built: Payments needing attention (4b.6, BA-8), the "Bloc price rules" rail link (Q3, BA-13), policy history (BA-10) and policy on drafts (BA-11).

## 5. Component guidance

Use HeroUI v3 components where they fit rather than reproducing the Figma kit implementation (ASCII inventory — layout intentional):

```text
  -----------------------------------------------------------------------
  Need                                Preferred implementation
----------------------------------- -----------------------------------
  status/error banner                 `Alert`

  avatar                              `Avatar`

  breadcrumbs                         router links / HeroUI breadcrumbs
                                      where navigation semantics fit

  actions                             `Button`, router `Link` + button
                                      variants where required

  category/status pill                `Chip`

  gated phone action                  `Modal`/sheet behavior used by
                                      `GatedActionSheet`

  gated desktop action                `Modal`

  forms/search                        HeroUI field components /
                                      `SearchField`

  progress                            `ProgressBar`/HeroUI progress
                                      primitives

  loading                             HeroUI `Skeleton` in final geometry

  dividers                            `Separator`

  tables/tabs                         reserved for admin/later screens
  -----------------------------------------------------------------------
```

Do not depend on generated Claude Design component globals in application code.

## 6. Icons and assets

The raw export includes generated icon components, but the frontend should use maintainable application assets/components rather than importing the design runtime.

Buyer-screen icon needs include back, close, bell, copy, and external/share. The catalog card's arrow is text.

Wordmark/logo and Google branding should use approved project assets. Do not redraw the Google mark.

## 7. Implemented decisions and remaining limitations

The original artboard review ended with 22 open questions. T2-01 through T2-06 resolved most of them (ASCII inventory — layout intentional):

```text
  -----------------------------------------------------------------------
  Topic                               Current decision
----------------------------------- -----------------------------------
  Primary actions                     Neutral `secondary` styling; no
                                      low-contrast accent fill

  Catalog page size                   20

  Filter UI                           Category chips plus contract-backed
                                      keyword search

  Category values                     Central mapping; contract still
                                      lacks a category dictionary

  Card prices                         `S$` formatting; bloc price +
                                      struck solo price

  Card progress                       Joined/target, seats left and bar
                                      from the summary `deal` (FB-16)

  Detail hierarchy                    Artboard hierarchy/copy implemented

  Paid detail actions                 Auth-gated; full/ended JOIN disabled

  Single deal vs bloc list            Render one **Open bloc** without
                                      invented starter data

  Deadline                            SGT display and defensive parsing;
                                      live countdown where appropriate

  Not found                           Designed error treatment rather
                                      than bare text

  Off sale                            Still blocked by missing detail
                                      status

  Login                               Dedicated `/login` plus gated
                                      action sheet/modal

  OAuth return                        Validated return path stored in
                                      `sessionStorage`

  `/account`                          My blocs plus order history

  Signed-in header/logout             Avatar/name plus explicit Sign out
                                      control; final menu design remains
                                      open

  Grid                                1 / 2 / 4 columns at the
                                      established breakpoints

  Muted contrast                      Avoid `muted` on the canvas where
                                      it misses the target contrast

  Skeletons                           HeroUI `Skeleton` in artboard
                                      geometry

  Font                                Inter is loaded for the styled
                                      buyer UI
  -----------------------------------------------------------------------
```

Remaining design/contract gaps (audited 2026-10-02, FB-18, against `frontend/src/` and `contracts/openapi.json`):

- category values are not contract-driven: `category` is a free string on both listing schemas and there is no dictionary endpoint; `features/catalog/categories.ts` holds the mapping (ask CR-10 in [`admin-plan.md`](admin-plan.md), no issue yet);
- listing detail does not expose off-sale status (`ProductDetailResponse` has no `status`): no owner yet;
- the API exposes one active deal (`ActiveDealResponse`: id, sizes, expiry) rather than a list with starter metadata; only an invite link names its sharer (FB-19, #201): no owner yet;
- My Blocs shows Active / Completed / Refunded from `GET /group-deals/me`, but `MyTeamResponse` carries only the buyer's own `role`, not the starter; no owner yet. Notifications wait on backend #215 (first persisted use case, open);
- the refund-pending receipt heading is "Refund on its way", as decided by the design owner on 2026-10-05 (FB-15, #392); completed refunds retain "Refund sent" and failed/reviewed refunds retain "Payment received";
- the gated sheet's copy is written for Start a bloc ("Starting a bloc means paying first") and is reused for Buy alone, which no artboard draws (the bloc terms are kept off that sheet, FB-11): no owner yet;
- the signed-in storefront menu/header is still provisional (a neutral Sign out button, `plan.md` T2-05; no artboard draws a menu): no owner yet;
- CSP still permits HTTPS listing and avatar image hosts (`img-src 'self' https:`, ADR 0002) until the contract pins an image host; Inter is already self-hosted (#158): no owner yet.

When a later design or API contract resolves one of these gaps, update this document and the owning ADR/contract rather than preserving the old workaround as historical narrative.
