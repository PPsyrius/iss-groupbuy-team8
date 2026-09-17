# Product

## Register

Group buy e-commerce platfom like Pinduoduo in its early day.

## Users

BlocBuy serves two platform roles, buyer and admin. There is no organiser, seller, or fulfilment role; the team leader is a buyer who paid first.

- **Buyer on a phone, arriving from an invite link.** Opened cold from a WhatsApp or Telegram group, has never seen BlocBuy, and wants to decide in under a minute whether to pay. Needs the leader's name, the listing, the team price against the solo price, seats left, the deadline, and one Join button.
- **Buyer browsing.** On phone or laptop, comparing solo and team prices, deciding whether to start a team or join an open one. Browses without signing in; signs in with Google only when tapping a paid action.
- **Team leader.** A buyer who started a team. Wants to know how many seats are left, wants to share the invite link again, and wants to be told the moment the team succeeds or expires.
- **Admin on a laptop.** Creates and publishes listings, sets the team price policy, and recovers when a colleague saved the same listing first. Works in a dense console under `/admin`, reached by direct URL.

Market is Singapore. Buyers pool purchases the way an HDB estate chat or an office group already does, so the sample content is jasmine rice, D24 durian, detergent twin packs, kopi powder, and drying racks rather than gadgets.

## Product Purpose

BlocBuy replaces group buys that are coordinated over chat messages and spreadsheets with pay-to-join team buying, in the spirit of early Pinduoduo: a lower price unlocks only when enough people commit, and the buyers themselves recruit the team through their own chat groups.

The mechanic:

- An admin publishes a listing with a solo price and a lower team price.
- A signed-in buyer starts a team by paying first, becomes its leader, and receives an invite link to paste into chat apps.
- Other buyers join by paying. The team succeeds the moment the last seat fills. If the deadline passes first, the team expires and every member is refunded.
- A listing can have several open teams at once; browse shows the best (lowest) team price.

The product succeeds when a stranger opening an invite link on a phone trusts it enough to pay within a minute, and when the leader finds it easier to fill a team on BlocBuy than to chase transfers in a chat thread. Trust comes from three cues shown plainly on every paid step: the price, the deadline, and the refund promise.

Tagline: "Bringing people together to buy better, one community at a time."

## Brand Personality

Warm, trustworthy, community-first, with a Singapore neighbourhood feel. Calm rather than urgent. It borrows Pinduoduo's social mechanic, not its visual noise: no flashing discounts, no gamified pressure. Trust cues (clear price, clear deadline, clear refund promise) over hype.

Expressed as:

- **One accent colour**, owned exclusively by the team price and team progress. Nothing else on the storefront uses the accent as a fill.
- **A neutral base scale** with distinct status colours for success, warning, danger, and info.
- **A sans-serif typeface** from Google Fonts with a real fallback stack, and tabular figures for prices and countdowns.
- **A simple typographic wordmark**. No mascot, no illustration system. Always "BlocBuy", never "Blocbuy" or "Bloc Buy".
- **Buyer-facing vocabulary derived from the product name** (for example a team may be a "bloc"), used identically in the storefront and the admin console. Raw backend codes such as `on_sale` or `succeeded` never appear in the UI.
- **Money always as `S$89.00`**, prefix and two decimals, never rounded or recomputed in the browser.

## Anti-References

- Discount-aggressive marketplace clones (Shopee, Lazada, and Pinduoduo's later visual style): percent-off badges, savings stickers, fake scarcity, flash-sale timers on the catalog grid.
- Generic e-commerce templates with organiser tooling, seller accounts, fulfilment, collection points, reviews, chat, or wishlists. None of these exist in BlocBuy and none should be designed.
- Email and password sign-in, registration forms, and profile editing. Sign-in is Google only.
- Dark mode artboards. The product is light mode only for now, with tokens defined so dark mode can be added later.

## Accessibility Needs

Maintain clear information hierarchy, keyboard-accessible interactions, readable copy, and sufficient contrast across product-critical flows.

## Product Context

- Name: BlocBuy
- Logo: https://api.heroui.pro/api/theme-assets/2a854899-bdc6-4dda-bfbe-550e9c176696/c2a9e43b-ed1b-4771-89e2-c01323ada009.png
- Mark: https://api.heroui.pro/api/theme-assets/2a854899-bdc6-4dda-bfbe-550e9c176696/66f6fcb5-e066-4840-a6e2-8f0548fbfb1f.png
- blocbuy-logo.png: https://api.heroui.pro/api/theme-assets/2a854899-bdc6-4dda-bfbe-550e9c176696/6c22f125-a7a4-4fc4-9b49-c3e08691b018.png
- blocbuy-logo-stacked.png: https://api.heroui.pro/api/theme-assets/2a854899-bdc6-4dda-bfbe-550e9c176696/f849210d-d4c5-459a-8c91-2b38b821de67.png
- blocbuy-logo-stacked-dark.png: https://api.heroui.pro/api/theme-assets/2a854899-bdc6-4dda-bfbe-550e9c176696/bfc1feff-cf65-46b6-90c6-df193646b475.png
- blocbuy-logo-dark.png: https://api.heroui.pro/api/theme-assets/2a854899-bdc6-4dda-bfbe-550e9c176696/1cf4b23b-1d60-484c-badd-b589f4ae3d0d.png
- blocbuy-isologo.png: https://api.heroui.pro/api/theme-assets/2a854899-bdc6-4dda-bfbe-550e9c176696/cc89c795-79a4-4473-82ee-a03fac0f39ed.png
- blocbuy-isologo-dark.png: https://api.heroui.pro/api/theme-assets/2a854899-bdc6-4dda-bfbe-550e9c176696/6de1a402-ee44-4908-a6c2-8fb93af97ed4.png
