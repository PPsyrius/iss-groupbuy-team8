# BlocBuy UI design brief for Claude Design

Paste everything below the line into Claude Design as the opening message. It is self-contained. If the tool can read files, the "Source files" section at the end lists where the facts came from.

Decisions in this brief were settled in a grilling session on 2026-09-16 and are recorded here as report evidence. Tier labels indicate backend readiness, not design priority.

---

## 1. Your role

You are designing the complete user interface for **BlocBuy**, a community group-buying web app built by NUS-ISS Team 8 for the SWE5006 practice module. The frontend is greenfield: no code, logo, palette, or wireframes exist. The backend exists and constrains what the UI can show. Design against the real backend described in this brief, not against a generic e-commerce template.

Produce, in this order:

1. A **design system sheet**.
2. A **flow map** for the buyer journey and the admin journey.
3. **Screen artboards** for every screen in the inventory in section 4, with their states.
4. **Written rationale** and a **backend change request list** (section 12).

Assessment for this project is 80% written report and 20% code. Every visual decision needs a one-paragraph written trace, so annotations and rationale are deliverables, not extras.

## 2. What BlocBuy is, as actually built

BlocBuy replaces group buys that are coordinated over chat messages and spreadsheets. Tagline: "Bringing people together to buy better, one community at a time." Market: Singapore. Currency: Singapore dollars only.

The mechanic is pay-to-join team buying:

- An **admin** publishes a listing with a solo price and a lower team price.
- Anyone can **browse** listings without signing in.
- A signed-in **buyer** can **start a team** for a listing. They pay first, become the team's leader, and receive an invite link to share into chat apps.
- Other buyers **join** an open team by paying. The team **succeeds** the moment the last seat fills. If the deadline passes first, the team **expires** and members are refunded.
- Every listing can have several open teams at once. Browse shows the best (lowest) team price.
- A buyer sees their **own teams**, each team's progress, and later the order status.

There are exactly two platform roles: **buyer** (granted at first sign-in) and **admin** (assigned manually). "Organiser" and "seller" are not roles. The team leader is just a buyer who paid first. There is no fulfilment, collection-point, review, or messaging feature. Do not design those.

Sign-in is **Google only**. There are no email or password forms, no registration form, and no profile editing.

## 3. Personas

- **Buyer on a phone**, arriving from an invite link pasted in a WhatsApp or Telegram group, has never seen BlocBuy, wants to decide in under a minute whether to pay.
- **Buyer browsing**, on phone or laptop, comparing solo and team prices, deciding whether to start a team or join one.
- **Team leader**, wants to know how many seats are left, wants to share the link again, wants to know when the team succeeded.
- **Admin on a laptop**, creating and publishing listings, setting the team price, fixing a listing after a colleague edited it at the same time.

## 4. Screen inventory and tiers

Tier 1: backend exists today. Tier 2: backend endpoint pending but the domain is specified. Tier 3: design only, documents a planned feature.

Viewports: buyer Tier 1 screens get a **phone frame (390 px wide) and a desktop frame (1280 px wide)**. Buyer Tier 2 and Tier 3 screens get **phone only**. Admin screens get **desktop only (1440 px wide)**. Mobile first: design the phone frame first and adapt up.

### Buyer storefront

| Screen | Tier | States to design |
| --- | --- | --- |
| Home / catalog grid | 1 | loading (skeleton cards), populated, empty category, error, pagination |
| Listing detail | 1 | loading, with open teams, no open team (solo price only), off sale / not found, error |
| Sign-in page | 1 | default, returning from a gated action (explains why), error from provider |
| Gated-action sheet | 1 | shown when a signed-out user taps a paid action |
| Checkout | 2 | quote locked with countdown, paying, payment error, quote expired |
| Payment result | 2 | confirming (polling), admitted to an open team, team succeeded (you filled the last seat), paid but seat not secured with refund pending, quote expired |
| Invite landing | 2 | open team, team full, team expired, invalid link |
| My teams | 2 | loading, empty, filtered list (Active / Completed / Refunded) |
| Team detail | 2 | member view, leader view, succeeded, expired with refund status |
| Notifications | 2 | empty, list with unread |
| Buy alone (solo checkout) | 2 | reuse checkout with no team, mark clearly as Tier 2 |
| Not found, access denied, global error | 1 | one each |

### Admin console

| Screen | Tier | States to design |
| --- | --- | --- |
| Dashboard | 1 | counts of drafts, on sale, off sale, open teams; recent listings |
| Listings table | 1 | tabs All / Draft / On sale / Off sale, empty, loading, paginated |
| Listing editor | 1 | new draft, editing a draft, editing a published listing (frozen fields), validation errors, publish confirmation, delist confirmation, delete confirmation, delete blocked |
| Version conflict banner | 1 | shown on any 412 response |
| Team price policy panel | 1 | no policy yet, active policy, expired policy, version conflict |
| Payments needing attention | 3 | queue of participations marked compensation required |
| Access denied | 1 | non-admin opens `/admin` |

## 5. Settled technical constraints

These are fixed. Do not propose alternatives.

- React 19 single-page app, TanStack Router, TanStack Query, TanStack Form, Tailwind CSS v4, **HeroUI v3** (built on React Aria Components). Static build on S3 and CloudFront. Browser floor: Safari 16.4+, Chrome 111+, Firefox 128+.
- Two shells in one codebase: a consumer storefront and a dense admin console under `/admin`. They share design tokens but differ in layout and density.
- **Light mode only.** Define tokens so dark mode can be added later, but produce no dark artboards.
- **Component precedence**, strictly in this order:
  1. Compose the screen from a **HeroUI Pro** block (https://heroui.pro/) if a suitable one exists.
  2. Otherwise every element is a **named HeroUI v3 component** (Button, Card, Chip, Modal, Drawer, Tabs, Table, TextField, NumberField, Select, Progress, Skeleton, Toast, Avatar, Breadcrumbs, and so on).
  3. Otherwise build the element from **React Aria Components** styled with Tailwind, using **only HeroUI theme variables** for colour, spacing, radius, and typography.
- **Annotate every screen** with the component names used, and express every colour and spacing value as a HeroUI theme token, not a raw hex or pixel value. The design system sheet defines the token values once.
- Custom Tailwind layout is acceptable for the storefront hero and listing cards; everything interactive comes from the precedence list.
- Fonts may be loaded from Google Fonts. Images come from arbitrary external URLs entered by admins, so every image needs a designed fallback tile.
- The app runs on a bare `*.cloudfront.net` URL with no custom domain for now.

## 6. Brand direction

Only the name and tagline exist. Propose the rest within these guardrails:

- Personality: warm, trustworthy, community-first, a Singapore neighbourhood feel. Calm rather than urgent. Not a discount-aggressive marketplace clone. Trust cues (clear price, clear deadline, clear refund promise) over hype.
- **One accent colour**, owned exclusively by the team price and team progress. Nothing else on the storefront uses the accent as a fill. The accent must pass 4.5:1 as text on the base background; state the ratio.
- One neutral base scale. Status colours for success, warning, danger, and info, distinct from the accent.
- A sans-serif typeface from Google Fonts with a real fallback stack. Tabular figures for prices and countdowns.
- A simple typographic wordmark for "BlocBuy". No mascot, no illustration system.
- Write "BlocBuy" exactly so, never "Blocbuy" or "Bloc Buy".

## 7. Vocabulary

Propose the buyer-facing vocabulary yourself and **derive it from the product name BlocBuy** (for example, a team could be called a "bloc"; decide what reads naturally in buttons and status pills). Deliver a glossary table on the design system sheet mapping each chosen term to the backend concept it represents, so code and copy stay traceable.

The **same vocabulary is used in the admin console**. Do not expose raw backend codes such as `on_sale` or `succeeded` anywhere in the UI.

Backend concepts that need a name:

| Backend concept | Meaning |
| --- | --- |
| Product / listing | The thing for sale, with a solo price |
| Group deal | One team instance on a listing, with a target size and deadline |
| Individual price | Price when buying alone |
| Group price | Lower price when a team fills |
| Leader / member | The buyer who started the team / a buyer who joined |
| Deal status `open` | Team is filling |
| Deal status `succeeded` | Team filled, purchase confirmed |
| Deal status `expired` or `failed` | Deadline passed before filling; refunds follow |
| Deal status `canceled` | Withdrawn |
| Team price quote | A server-issued price valid for at most 15 minutes, used at checkout |
| Listing status `draft`, `on_sale`, `off_sale` | Admin lifecycle |

## 8. Data available to each screen

Design only with fields that exist. Where a screen is Tier 2 the fields come from the domain design, marked "planned".

**Listing card** (catalog): id, title, subtitle, cover image URL, category, individual price (number), group price (number or **null when no open team**), currency (always `sgd`).

**Listing detail**: everything above plus description, up to 20 image URLs, stock, sold count, and the best open team: team id, target size, current size, expiry time (UTC instant). Planned: the full list of open teams, each with leader nickname and avatar.

**Session**: after sign-in the app knows the user's id, nickname, and avatar URL (avatar may be missing). It does **not** know whether the user is an admin. Sessions last 24 hours.

**Quote** (checkout): amount (decimal string like `"89.00"`), currency, issued at, expires at. Valid at most 15 minutes. Requesting a quote does not reserve stock or a seat.

**Payment**: Stripe Payment Element embedded in the checkout page (card form inline, no redirect to a hosted checkout page). After payment the app must **poll server state** until the participation outcome is known. Never treat "Stripe said success" as "seat secured".

**Participation outcome**: `applied` (seat secured, team may have succeeded) or `compensation_required` (payment taken but seat not secured, for example the last seat was lost in a race or the team expired mid-payment; a refund follows and may sit as pending).

**Team** (planned, for My teams and Team detail): team id, human-readable team number, listing snapshot (title, image, unit price), status, target size, current size, start time, expiry time, success time, your role (leader or member), your payment status (`created`, `succeeded`, `failed`, `canceled`), members list with nickname, avatar, join time. Planned order status: `created`, `paid`, `shipped`, `finished`, `canceled`, `refunded`.

**Notifications** (planned): type (`deal_succeeded`, `deal_failed`, `payment_succeeded`), reference to a team or order, read flag, time.

**Pagination**: page number starting at 1, page size up to 100 (default 20), total count. No sorting. No keyword search yet. Category filter is exact match and there is no category list endpoint, so use the fixed set in section 10.

**Admin listing**: product number (read-only, server assigned), title, subtitle, description, cover image URL, image URLs (up to 20, URL text fields, not file upload), category, original price (decimal string), currency (`sgd`), stock, sold count (read-only), status, version (read-only integer). Rules the editor must make visible:

- New listings are created as drafts at version 0.
- Publishing requires a positive price and positive stock.
- After a listing has ever been published, **original price, currency, and stock are frozen**. Render them read-only with a note.
- Lifecycle: draft → on sale, on sale → off sale, off sale → on sale. **There is no way back to draft.**
- Delete is allowed only for a draft that was never published and has no team, quote, or policy history. Otherwise show why delete is blocked.
- Every save sends the version the admin loaded. If someone else saved first, the server answers 412 and the UI shows the conflict banner: "This listing was updated by someone else. Reload to see the latest version." with a reload action. No merge UI.

**Team price policy** (admin, per listing): amount (decimal string, must be strictly below the original price), currency, valid from, valid until, version. Saving appends a new revision; old quotes are never changed.

## 9. Hard presentation rules

These come from backend constraints and are not open to interpretation.

- Money is always shown as `S$89.00`: currency prefix `S$`, two decimals, never rounded, never recomputed in the browser. The group price is set in the accent colour with the solo price beside it in a struck-through neutral.
- When the group price is null, the card shows the solo price only and a quiet "start a team" affordance. **Never show `S$0`, a placeholder discount, or a percentage off.** Do not invent savings badges anywhere.
- Countdowns: above one hour show hours and minutes ("2h 14m left"); under one hour show minutes and seconds; at zero switch to an "Ended" pill. Show the absolute deadline in Singapore time on detail, checkout, and team pages.
- Team progress is a bar plus "3 of 5 joined". When one seat remains, emphasise "1 seat left". Progress and countdown must be readable without colour.
- The 15-minute quote timer appears **only on checkout**, never on browse or detail.
- Dates and times in Singapore local time, English only, no language switcher.
- Status is always a text label (optionally with a colour), never colour alone.
- Every list has designed loading, empty, and error states. Detail and card views also have an "unavailable" state, meaning the listing is off sale or the team is no longer open.

## 10. Sample content

Use realistic Singapore neighbourhood group-buy content, the kind an HDB estate chat or an office group would pool. Examples: 10 kg bag of Thai jasmine rice, D24 durian by the kilo, laundry detergent 5 L twin pack, air purifier replacement filters, a kids' stationery bundle, kopi powder, a fold-out drying rack. Use varied title lengths, including one long title that wraps, and varied image aspect ratios. Use real-looking nicknames and avatars for leaders and members.

Fixed categories: **Groceries, Household, Electronics, Beauty, Kids**.

Include at least one listing with no open team, one with a team at "1 seat left", and one with a team ending within the hour.

## 11. Flow details

### Browse and detail (Tier 1)

Home is the catalog grid with a category chip row and page controls. Cards show image, title, subtitle, group price with struck solo price (or solo only), and a compact progress cue when a team is open. Detail shows the image gallery, description, sold count, stock, and three actions in this order of prominence: **Start a team** (accent), **Join a team** (per open team, with each team's progress, countdown, and leader), and **Buy alone** (secondary, Tier 2). List open teams explicitly; do not auto-join.

### Sign-in (Tier 1)

A dedicated `/login` route with one "Continue with Google" button, a short trust line, and a `returnTo` so the user lands back where they were. When a signed-out user taps a paid action, show a bottom sheet (phone) or modal (desktop) explaining that sign-in is needed to hold a seat, with the same single button. No other sign-in methods.

### Checkout and result (Tier 2)

Checkout shows the listing snapshot, which team is being started or joined, the locked quote with its countdown, the refund promise in plain words, and the Stripe Payment Element. If the quote expires, disable payment and offer to get a fresh price. After payment, the result page starts in **confirming** while the app polls, then resolves to one of: admitted (show progress, deadline, and the share moment), team succeeded (celebratory but calm), or **paid but seat not secured** (plain explanation, refund pending state, link to My teams).

The **share moment** is a designed component: the invite link with a copy button and a native share action, with a suggested message written for pasting into WhatsApp or Telegram.

### Invite landing (Tier 2)

A single-purpose page opened cold from chat: leader's nickname and avatar ("Sarah started this"), listing image and title, group price versus solo price, seats left, countdown, and one primary "Join" button. Sign-in happens after tapping join, then returns here. Variants: team full (offer to start a new team), team expired (offer to start a new team), invalid link.

### My teams and team detail (Tier 2)

One list, filter chips Active / Completed / Refunded. Each row: listing image and title, role badge for leader, progress, countdown or end state, payment status. Team detail: members with avatars and join times, invite link and share for open teams, order status when it exists, refund status for expired teams. The leader view adds a nudge to share and a clear "you started this team" marker. Notifications are a bell in the header with an unread badge and a plain list.

### Admin console (Tier 1 and 3)

Dashboard, listings table with status tabs and a create button, and the listing editor with a right sidebar that shows status, version, and the lifecycle actions available in the current state, plus the team price policy panel on the same page. The conflict banner sits at the top of the editor. The Tier 3 "payments needing attention" queue lists participations marked compensation required with the payment id, buyer, team, and reason. Admin entry is a direct URL; do not rely on the header knowing whether the user is an admin. Non-admins see an access-denied page.

## 12. Deliverables, format, and rationale

1. **Design system sheet**: token table (name, value, purpose) mapped to HeroUI theme variables; type scale; accent, neutral, and status usage rules; wordmark; image fallback tile; glossary table from section 7.
2. **Flow map**: two diagrams, buyer and admin, with each node labelled by screen name and tier. Buyer nodes cover UC1 browse, UC2 sign-in, UC3 start a team, UC4 join a team, UC5 team outcome, UC6 refund, UC7 invite and share, UC8 my teams. Admin nodes cover UC9 listing lifecycle and the price policy.
3. **Screens**: one artboard per screen and state from section 4, at the viewports in section 4. Each artboard annotated with HeroUI Pro block or component names and tokens.
4. **Rationale**: for the design system and for each Tier 1 screen, a short block: the problem, the choice made, one alternative rejected and why.
5. **Backend change requests**: a list of anything the design needs that the backend does not provide, so the team can schedule it. Known items to include and extend: the Google callback should redirect to the app with `returnTo` instead of returning JSON; the session endpoint should return roles (nickname and avatar landed with B4, PR #89, and section 8 already relies on them); a category list or an agreed fixed set; endpoints to start a team, join a team, create a payment, list my teams, list notifications, and resolve an invite token; keyword search and sorting on browse.

## 13. Do not

- Do not design organiser tooling, seller accounts, fulfilment, collection points, reviews, chat, or wishlists.
- Do not produce dark mode artboards.
- Do not use percent-off badges, fake scarcity, or countdowns on the catalog grid.
- Do not add email or password sign-in.
- Do not design file upload for images.
- Do not use raw hex colours or pixel values in annotations; use tokens.
- Do not invent components when a HeroUI Pro block or HeroUI v3 component fits.

## 14. Accessibility

Target **WCAG 2.2 AA**. Concretely: 4.5:1 for all text including the accent on the base background; 44 px minimum touch targets on phone; visible focus rings on every interactive element; progress, countdown, and status never conveyed by colour alone; form errors announced next to the field; modals and sheets dismissible by keyboard.

## 15. Source files (if you can read the repository)

- `contracts/openapi.json`: the API contract with exact DTO fields.
- `backend/docs/design/backend-skeleton-plan.md`: use cases UC1 to UC9 and ownership.
- `backend/docs/design/admin-listings.md`: listing lifecycle and version conflict rules.
- `backend/docs/design/paid-group-participation.md`: start and join semantics, outcomes.
- `backend/docs/design/team-price-quotes.md`: quote validity and pricing rules.
- `frontend/docs/blueprint.md`: the settled frontend stack.
- `README.md`: product vision (broader than what is built; sections 2 and 13 of this brief take precedence).
