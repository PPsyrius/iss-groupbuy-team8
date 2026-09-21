# BlocBuy buyer screens

This document is the implementation reference for the buyer-facing Claude Design artboards used during Sprint 2. It records the visual rules that matter to the frontend and the places where the current API contract limits the design.

**Design snapshot:** Claude Design project `BlocBuy Prototype`, local export dated 2026-09-17 and reviewed on 2026-09-18. The raw export was copied to `.claude/design-artboards/` for implementation work and is intentionally not tracked. If the upstream design changes, update this document rather than depending on the old export.

Admin, checkout, invite, raw component assets, thumbnails, and generated icon/assets files were outside the buyer-screen pass.

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

The raw accent does not meet the intended text contrast on the page background, so buyer screens use `accent-soft-foreground` for accent-coloured text and neutral button fills for primary actions.

### 2.2 Typography

Inter, weights 400/500/600/700, with `"Helvetica Neue", Helvetica, Arial, sans-serif` fallback.

Use tabular numbers for prices, countdowns, and counts. Main scale:

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

Nothing in the buyer UI should be smaller than `xs`.

### 2.3 Spacing and responsive rules

-   Base spacing unit: 4px.
-   Phone gutter: 16px; desktop artboards: 32px.
-   Minimum phone touch target: 44px.
-   Surface/button/card radius: 8px; fields and overlays: 12px;
    chips/avatars/progress: full radius.
-   Breakpoints follow the project Tailwind/HeroUI setup. Catalog grid
    is 1 column at 390, 2 at 768, and 4 at 1280.
-   Use the documented `surface` and `overlay` shadows; do not stack
    custom shadows on HeroUI cards.

## 3. Shared chrome

-   Buyer artboards have no footer.
-   Catalog header is 56px on phone and 64px on desktop, with the
    BlocBuy wordmark on the left.
-   Signed-out state uses **Sign in**, styled as a neutral/secondary
    control with accent-soft-foreground text.
-   Signed-in state uses avatar/name in the current implementation.
    Notification/bell UI waits for contract-backed notification data.
-   Sub-pages use a back/crumb pattern on phone and breadcrumbs on
    desktop where appropriate.
-   Buyer primary actions use neutral `secondary` styling rather than an
    accent fill because of contrast.
-   Secondary actions use `outline`; tertiary actions use `ghost`.
-   Page canvas uses `background`; cards/panels use `surface`.

## 4. Screens

### 4.1 Catalog (`/`)

Implemented in T2-01/T2-03.

**Layout**

Phone uses a 16px gutter and a one-column card stack. Desktop uses 32px padding and a four-column grid. The hero copy is:

-   **Buy better together**
-   Phone: "Fill a bloc with your neighbours and the lower price
    unlocks."
-   Desktop adds: "Pay once, get refunded in full if the bloc doesn't
    fill."

Default page size is 20. Pagination uses Previous / "Page X of Y" / Next; there is no infinite scroll.

**Filters and search**

The artboard defines a single-select chip row: All, Groceries, Household, Electronics, Beauty, Kids. The implementation keeps that interaction and adds the contract-backed keyword `SearchField`. Category/keyword changes reset to page 1.

Category values are mapped centrally because the API contract does not publish a category dictionary. Current known values come from backend/demo data.

**Listing cards**

Cards use a bordered 8px surface, image/fallback area, title/subtitle, and price block.

-   Open bloc: bloc price is prominent in `accent-soft-foreground`; solo
    price is struck through.
-   No open bloc: solo price is prominent and the secondary line says
    "Buying alone".
-   No percent-off badges or countdowns on the catalog grid.
-   The current summary DTO does not provide bloc size/progress, so the
    artboard progress cue cannot be contract-driven yet.

SGD is displayed as `S$` with two decimals.

**States**

-   Loading: chip and card skeletons in final geometry.
-   Empty category: selected chips remain visible with a recovery
    action.
-   Empty keyword result: same empty-state treatment with a clear-search
    action.
-   Error: danger alert with retry.
-   Out-of-range `page` redirects to the last valid page.

### 4.2 Listing detail (`/products/$productId`)

Implemented in T2-02, with gated actions added in T2-04.

Phone is a single-column layout with gallery, listing information, open-bloc section, about card, trust copy, and sticky action bar. Desktop uses a two-column layout with gallery/about on the left and price/bloc actions on the right.

**Price and availability**

With a bloc, show the bloc price prominently and strike the solo price. Without one, show the solo price and:

> Buying alone. No bloc is open yet.

The artboard includes an off-sale state, but `ProductDetailResponse` currently has no status field, so the frontend cannot implement that state from the contract.

**Open bloc**

The current contract exposes a single `ActiveDealResponse` with `dealId`, target/current size, and expiry. It does not expose starter identity, bloc number, or a list of open blocs. The implementation therefore labels the row **Open bloc** rather than inventing a starter.

Progress shows joined/target, seats left, progress bar, and deadline/countdown. Invalid or missing expiry values must not throw.

**Actions**

Start a bloc and Buy alone are gated for anonymous buyers. Signed-in behavior remains owned by their later use cases. Join remains disabled until a supported per-bloc action exists.

**Errors**

HTTP/not-found shapes map to the designed 404 treatment where possible; other failures use the listing error alert. Missing detail data in a successful envelope is treated as an API error rather than allowed to fail inside rendering.

### 4.3 Sign-in and gated action (`/login?returnTo=...`)

Implemented in T2-04/T2-06.

The sign-in page uses **Continue with Google** and explains that browsing is public while paid actions require an account. There is no email/password path.

Anonymous paid actions open a bottom sheet on phone and a modal on desktop. Dismissing the gate leaves the listing in place.

The backend does not carry the original return URL through OAuth, so the frontend validates the return path and stores it in `sessionStorage` before navigating to `/api/v1/auth/oauth2/google`. `/auth/callback` confirms the session and restores that path.

Backend provider failures return to `/login?error={errorCode}` and are mapped to user-facing retry/cancellation messages.

### 4.4 My Blocs / account shell

The artboard defines active/refunded/loading/empty My Blocs states and detailed bloc views, but the current frontend contract does not yet provide the per-user bloc data needed to implement them.

`/account` therefore uses the My Blocs visual language as an interim authenticated shell: avatar/name and the empty-state treatment. It should not invent rows or profile data absent from the API.

### 4.5 Errors

The buyer error composition is shared across 404, 403, and 500 states: compact card, clear heading/body, money-safety line, recovery actions, and optional hint.

-   **404:** "We can't find that page"; Browse listings; My blocs when
    available.
-   **403:** admin access denied; buyer account/blocs remain unaffected.
-   **500:** "Something went wrong on our side"; Try again / Browse
    listings.
-   **401:** no dedicated page. Anonymous paid actions go through the
    gated sign-in flow.

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

  Card progress                       Omitted until the summary contract
                                      provides size/progress

  Detail hierarchy                    Artboard hierarchy/copy implemented

  Unsupported detail actions          Disabled or auth-gated rather than
                                      pretending to work

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

  `/account`                          Interim My Blocs-style
                                      authenticated shell

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

Remaining design/contract gaps:

-   category values are not contract-driven;
-   listing summary does not expose bloc progress;
-   listing detail does not expose off-sale status;
-   the API exposes one active deal rather than a list with starter
    metadata;
-   per-user blocs, notifications, checkout, start/join actions, and
    invite flows need later contracts;
-   the signed-in storefront menu/header is still provisional;
-   CSP must continue to account for the chosen font/image asset hosts
    until those assets are self-hosted or narrowed.

When a later design or API contract resolves one of these gaps, update this document and the owning ADR/contract rather than preserving the old workaround as historical narrative.
