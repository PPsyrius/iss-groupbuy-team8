import { HttpResponse } from "msw";
import { setupWorker } from "msw/browser";

import { OK_CODE } from "@/api/envelope";
import { createMswHandlers } from "@/api/generated/msw.gen";
import type {
  MyTeamResponse,
  OrderSummaryResponse,
  ProductDetailResponse,
  ProductSummaryResponse,
  QuoteResponse,
  Receipt,
} from "@/api/generated/types.gen";
import { readParkedInvite } from "@/features/share/invite-ref";
import { adminHandlers, createAdminStore } from "@/test/admin-msw";
import { keywordTerms, matchesKeyword } from "@/test/keyword-match";

// Offline UI work: `?msw=1` on the URL makes main.tsx start this worker before rendering, so the
// catalog and the listing detail render the artboards' sample content (docs/design-screens.md
// §4.1, §4.2) without a backend. Unmatched requests pass through to the Vite proxy. Never
// imported outside that gate. Deal deadlines are relative to page load so the countdowns tick
// the way the artboard's do (2h 14m, 47:25 urgent, 19h 05m).

const id = (n: number) => `5b1b9d5c-1a11-4e1e-9a10-${String(n).padStart(12, "0")}`;
const loadedAt = Date.now();
const inSeconds = (s: number) => new Date(loadedAt + s * 1000).toISOString();

interface Seed {
  n: number;
  title: string;
  subtitle: string;
  category: string;
  individualPrice: number;
  groupPrice?: number;
  description: string;
  stock: number;
  soldCount: number;
  deal?: { joined: number; target: number; endsIn: number };
}

const rice =
  "Fragrant long-grain rice from the 2026 harvest, milled in Bangkok and vacuum packed in 10 kg sacks. Keeps six months unopened. Sold by the sack only, one sack per seat.";

const seeds: Seed[] = [
  {
    n: 1,
    title: "Thai jasmine rice 10 kg",
    subtitle: "New crop, vacuum packed",
    category: "groceries",
    individualPrice: 89,
    groupPrice: 71,
    description: rice,
    stock: 38,
    soldCount: 142,
    deal: { joined: 3, target: 5, endsIn: 2 * 3600 + 14 * 60 },
  },
  {
    n: 2,
    title: "D24 durian, per kg",
    subtitle: "Pahang, delivered chilled",
    category: "groceries",
    individualPrice: 38,
    groupPrice: 32,
    description:
      "Pahang D24, harvested this week and delivered chilled the same day. Sold per kilogram; one seat is one kilogram.",
    stock: 24,
    soldCount: 61,
    deal: { joined: 4, target: 5, endsIn: 47 * 60 + 25 },
  },
  {
    n: 3,
    title: "Laundry detergent 5 L twin pack",
    subtitle: "Fragrance-free",
    category: "home",
    individualPrice: 42.9,
    groupPrice: 34.9,
    description:
      "Two 5 L bottles of fragrance-free liquid detergent, suitable for front and top loaders.",
    stock: 80,
    soldCount: 210,
    deal: { joined: 2, target: 6, endsIn: 19 * 3600 + 5 * 60 },
  },
  {
    n: 4,
    title: "Replacement HEPA filters for the Coway Airmega 150, twin pack",
    subtitle: "Fits AP-1512 and AP-1519",
    category: "electronics",
    individualPrice: 118,
    description:
      "Genuine Coway replacement set: one pre-filter and one HEPA cartridge. Recommended every twelve months.",
    stock: 12,
    soldCount: 9,
  },
  {
    n: 5,
    title: "Kopi powder 500 g",
    subtitle: "Traditional roast",
    category: "groceries",
    individualPrice: 12.8,
    groupPrice: 9.9,
    description:
      "Robusta beans roasted with margarine and sugar the traditional way, ground for the sock filter.",
    stock: 150,
    soldCount: 388,
    deal: { joined: 5, target: 8, endsIn: 5 * 3600 },
  },
  {
    n: 6,
    title: "Fold-out drying rack",
    subtitle: "Stainless, 3 tiers",
    category: "home",
    individualPrice: 56,
    description: "Three-tier stainless steel rack, folds flat to 6 cm.",
    stock: 30,
    soldCount: 17,
  },
  {
    n: 7,
    title: "Kids’ stationery bundle",
    subtitle: "Primary 1 to 3",
    category: "kids",
    individualPrice: 24,
    groupPrice: 19.5,
    description: "Pencils, erasers, rulers and exercise books for the first three primary years.",
    stock: 60,
    soldCount: 44,
    deal: { joined: 1, target: 4, endsIn: 30 * 3600 },
  },
  {
    n: 8,
    title: "Hokkaido milk, 12 cartons",
    subtitle: "3.6%, UHT",
    category: "groceries",
    individualPrice: 46.8,
    groupPrice: 39.6,
    description: "Twelve 1 L cartons of full-cream UHT milk from Hokkaido.",
    stock: 45,
    soldCount: 96,
    deal: { joined: 6, target: 10, endsIn: 8 * 3600 + 30 * 60 },
  },
  {
    n: 9,
    title: "Bak kwa, 500 g",
    subtitle: "Sliced, vacuum packed",
    category: "groceries",
    individualPrice: 32,
    groupPrice: 28,
    description: "Sliced pork bak kwa, vacuum packed the day it is grilled.",
    stock: 20,
    soldCount: 73,
    deal: { joined: 7, target: 8, endsIn: 3 * 3600 },
  },
  {
    n: 10,
    title: "Dish soap refill 3 L",
    subtitle: "Lemon",
    category: "home",
    individualPrice: 9.9,
    description: "Lemon dish soap refill pouch, 3 L.",
    stock: 200,
    soldCount: 51,
  },
];

const details: ProductDetailResponse[] = seeds.map((s) => ({
  id: id(s.n),
  title: s.title,
  subtitle: s.subtitle,
  category: s.category,
  individualPrice: s.individualPrice,
  ...(s.groupPrice === undefined ? {} : { groupPrice: s.groupPrice }),
  currency: "sgd",
  description: s.description,
  images: [],
  stock: s.stock,
  soldCount: s.soldCount,
  ...(s.deal === undefined
    ? {}
    : {
        deal: {
          dealId: `deal-${s.n}`,
          currentSize: s.deal.joined,
          targetSize: s.deal.target,
          expireTime: inSeconds(s.deal.endsIn),
        },
      }),
}));

const toSummary = (s: Seed): ProductSummaryResponse => ({
  id: id(s.n),
  title: s.title,
  subtitle: s.subtitle,
  category: s.category,
  individualPrice: s.individualPrice,
  ...(s.groupPrice === undefined ? {} : { groupPrice: s.groupPrice }),
  currency: "sgd",
});

// The sample buyer's blocs for the §4.4 My blocs chips (`?msw=1&me=1`), after the artboard's
// rows: three active (one closing soon, one the buyer started), one filled, two closed unfilled.
// Each is its own bloc on the listing, not the live deal the listing page features (their counts
// differ), so joining that deal in the demo adds a row instead of duplicating one. An invite link
// copied from an open row still lands on its bloc: see `invitedBloc` below.
interface TeamSeed {
  n: number;
  status: "open" | "succeeded" | "expired";
  joined: number;
  target: number;
  endsIn: number;
  leader?: boolean;
  quantity?: number;
  paidDaysAgo: number;
}

const teamSeeds: TeamSeed[] = [
  { n: 1, status: "open", joined: 4, target: 5, endsIn: 52 * 60 + 9, paidDaysAgo: 1 },
  {
    n: 5,
    status: "open",
    joined: 5,
    target: 8,
    endsIn: 28 * 3600,
    leader: true,
    quantity: 2,
    paidDaysAgo: 2,
  },
  { n: 8, status: "open", joined: 6, target: 10, endsIn: 57 * 3600, paidDaysAgo: 3 },
  { n: 9, status: "succeeded", joined: 8, target: 8, endsIn: -86_400, paidDaysAgo: 4 },
  { n: 7, status: "expired", joined: 3, target: 5, endsIn: -2 * 86_400, paidDaysAgo: 5 },
  { n: 2, status: "expired", joined: 2, target: 5, endsIn: -5 * 86_400, paidDaysAgo: 7 },
];

// One membership: the bloc row and the order it paid for are both built from it, so the two
// lists agree. `orderN` numbers the order (the listing's number for the seeds).
interface Membership {
  n: number;
  orderN: number;
  dealId: string;
  status: TeamSeed["status"];
  joined: number;
  target: number;
  expireTime: string;
  successTime: string | null;
  leader: boolean;
  quantity: number;
  paidAt: string;
}

const fromSeed = (t: TeamSeed): Membership => ({
  n: t.n,
  orderN: t.n,
  dealId: `deal-${t.n}-bloc-${t.n}`,
  status: t.status,
  joined: t.joined,
  target: t.target,
  expireTime: inSeconds(t.endsIn),
  successTime: t.status === "succeeded" ? inSeconds(t.endsIn - 3_600) : null,
  leader: t.leader ?? false,
  quantity: t.quantity ?? 1,
  paidAt: inSeconds(-t.paidDaysAgo * 86_400 - 3_600),
});

const unitPriceOf = (n: number) => {
  const s = seeds.find((x) => x.n === n);
  return s?.groupPrice ?? s?.individualPrice ?? 0;
};
const orderIdOf = (n: number) => `0192f0a0-0000-7000-8000-${String(n).padStart(12, "0")}`;

const teamOf = (m: Membership): MyTeamResponse => ({
  dealId: m.dealId,
  productId: id(m.n),
  productTitle: seeds.find((x) => x.n === m.n)?.title ?? null,
  productImage: null,
  dealPrice: unitPriceOf(m.n).toFixed(2),
  currency: "sgd",
  targetSize: m.target,
  currentSize: m.joined,
  status: m.status,
  expireTime: m.expireTime,
  successTime: m.successTime,
  role: m.leader ? "leader" : "member",
  joinTime: m.paidAt,
  orderId: orderIdOf(m.orderN),
});

// The order is paid when the buyer joins, "Waiting for the bloc" while the bloc is open,
// confirmed once it fills and cancelled when it closes unfilled (`OrderStatus.java`).
const orderStatusOf = { open: "pending", succeeded: "confirmed", expired: "cancelled" } as const;

const orderOf = (m: Membership): OrderSummaryResponse => {
  const unit = unitPriceOf(m.n);
  return {
    orderId: orderIdOf(m.orderN),
    orderNo: `BB-${2090 + m.orderN}`,
    dealId: m.dealId,
    productId: id(m.n),
    productTitle: seeds.find((x) => x.n === m.n)?.title ?? "",
    productImage: null,
    quantity: m.quantity,
    unitPrice: unit.toFixed(2),
    totalAmount: (unit * m.quantity).toFixed(2),
    currency: "sgd",
    status: orderStatusOf[m.status],
    paidTime: m.paidAt,
    cancelTime: m.status === "expired" ? m.expireTime : null,
    createTime: m.paidAt,
  };
};

// Newest first, as the endpoints answer. A mock checkout that settles adds to the front.
const memberships = teamSeeds.map(fromSeed);
const teams: MyTeamResponse[] = memberships.map(teamOf);
const orders: OrderSummaryResponse[] = memberships.map(orderOf);

// The open seeded blocs as listing deals. The listing page features one bloc per listing; when the
// visitor arrived on an invite to one of these (Share invite link on an Active My blocs row), the
// mock features that bloc instead, with the row's counts, so the landing shows it open and
// joinable. A JOIN of it is then refused as "already joined" for the sample buyer (409_003).
const seededBlocs = memberships
  .filter((m) => m.status === "open")
  .map((m) => ({
    n: m.n,
    deal: {
      dealId: m.dealId,
      currentSize: m.joined,
      targetSize: m.target,
      expireTime: m.expireTime,
    },
  }));

function invitedBloc(productId: string) {
  const invite = readParkedInvite();
  if (invite?.productId !== productId) return undefined;
  return seededBlocs.find((b) => id(b.n) === productId && b.deal.dealId === invite.dealId)?.deal;
}

// The listing and the bloc a JOIN names: a listing's featured deal or an open seeded bloc.
function joinTarget(dealId: string | undefined) {
  const featured = details.find((p) => p.deal?.dealId === dealId);
  if (featured) return { listing: featured, deal: featured.deal };
  const seeded = seededBlocs.find((b) => b.deal.dealId === dealId);
  const listing = seeded && details.find((p) => p.id === id(seeded.n));
  return listing ? { listing, deal: seeded.deal } : undefined;
}

const orderFor = (n: number) => orders.find((o) => o.orderId === orderIdOf(n));

// One receipt per §4.6 state; the last digits name the state for `?msw=1&me=1`: 5 paid (the
// filled bak kwa bloc's confirmed order), 9 refund pending and 8 refunded (the cancelled orders
// of the two blocs that closed unfilled), and 1 waiting, which is a mock checkout (below).
const receiptId = (n: number) => `0192f0a0-0000-7000-9000-${String(n).padStart(12, "0")}`;
const receiptFor = (order: OrderSummaryResponse | undefined) => ({
  amount: order?.totalAmount ?? "0.00",
  currency: order?.currency ?? "sgd",
  dealId: order?.dealId ?? "",
  orderId: order?.orderId ?? "",
  productId: order?.productId ?? "",
});

const settledReceipts: Receipt[] = [
  {
    paymentId: receiptId(5),
    status: "SUCCEEDED",
    ...receiptFor(orderFor(9)),
    refundStatus: "NONE",
  },
  { paymentId: receiptId(9), status: "SUCCEEDED", ...receiptFor(orderFor(7)), refundPending: true },
  {
    paymentId: receiptId(8),
    status: "SUCCEEDED",
    ...receiptFor(orderFor(2)),
    refundStatus: "SUCCEEDED",
  },
];

// Mock Stripe Checkout. The checkout session answers with a payment and no `checkoutUrl`, which
// use-checkout already treats as "go to the receipt" in-app, so the demo reaches a receipt
// without the Stripe host check being relaxed. Each receipt then reads "Waiting" for its first
// `WAITING_READS` reads (about 4 s of the page's 2 s poll) and turns "Paid" as if the webhook had
// landed; that adds the bloc and its pending order to My blocs. Receipt 1 is the first checkout
// (a direct visit plays Start a bloc on the rice listing); later checkouts get fresh ids.
const WAITING_READS = 2;

interface Checkout {
  n: number;
  deal: ProductDetailResponse["deal"] | undefined;
  reads: number;
  claimed: boolean;
  order?: OrderSummaryResponse;
}

const checkouts = new Map<string, Checkout>([
  [receiptId(1), { n: 1, deal: undefined, reads: 0, claimed: false }],
]);
const intents = new Map<string, Pick<Checkout, "n" | "deal"> & { paymentId?: string }>();
let nextN = 11;

function settle(c: Checkout): OrderSummaryResponse {
  const orderN = nextN++;
  const now = Date.now();
  const joined = (c.deal?.currentSize ?? 0) + 1;
  const target = c.deal?.targetSize ?? seeds.find((x) => x.n === c.n)?.deal?.target ?? 5;
  if (c.deal) c.deal.currentSize = joined;
  const m: Membership = {
    n: c.n,
    orderN,
    dealId: c.deal?.dealId ?? `deal-${c.n}-bloc-${orderN}`,
    status: joined >= target ? "succeeded" : "open",
    joined,
    target,
    expireTime: c.deal?.expireTime ?? new Date(now + 24 * 3_600_000).toISOString(),
    successTime: joined >= target ? new Date(now).toISOString() : null,
    leader: !c.deal,
    quantity: 1,
    paidAt: new Date(now).toISOString(),
  };
  teams.unshift(teamOf(m));
  const order = orderOf(m);
  orders.unshift(order);
  return order;
}

function readCheckout(paymentId: string, c: Checkout): Receipt {
  if (!c.order && ++c.reads > WAITING_READS) c.order = settle(c);
  return c.order
    ? { paymentId, status: "SUCCEEDED", ...receiptFor(c.order), refundStatus: "NONE" }
    : {
        paymentId,
        status: "CREATED",
        amount: unitPriceOf(c.n).toFixed(2),
        currency: "sgd",
        productId: id(c.n),
      };
}

function openCheckout(purchase: Pick<Checkout, "n" | "deal">): string {
  const first = checkouts.get(receiptId(1));
  const paymentId = first && !first.claimed && !first.order ? receiptId(1) : receiptId(nextN++);
  checkouts.set(paymentId, { n: purchase.n, deal: purchase.deal, reads: 0, claimed: true });
  return paymentId;
}

// The one bloc whose JOIN loses the last-seat race (GROUPBUY_409_004): the closing-soon durian.
// The deal is left as it is, so the refusal can be shown again.
const FULL_ON_JOIN = "deal-2";

// First-team quotes (T3-01): the seed's bloc price, valid for 15 minutes; a listing without one
// answers the backend's "team price unavailable" 409. Issued quotes are kept for `getTeamPriceQuote`.
const quotes = new Map<string, QuoteResponse>();

function issueQuote(productId: string): QuoteResponse | null {
  const s = seeds.find((x) => id(x.n) === productId);
  if (s?.groupPrice === undefined) return null;
  const now = Date.now();
  const quote = {
    id: crypto.randomUUID(),
    productId,
    policyVersion: 1,
    amount: s.groupPrice.toFixed(2),
    currency: "sgd",
    issuedAt: new Date(now).toISOString(),
    expiresAt: new Date(now + 15 * 60_000).toISOString(),
  };
  quotes.set(quote.id, quote);
  return quote;
}

// A START that carries a quote pays the quoted amount (US26, #183), so the quote must be this
// listing's and still valid, as `lockQuotedStart` checks; a JOIN with a quote is a client bug.
function quoteRefusal(body: {
  operation?: string;
  businessId?: string;
  quoteId?: string | null;
}): Response | null {
  if (!body.quoteId) return null;
  if (body.operation === "JOIN") {
    return HttpResponse.json(
      { code: "ORDER_400_001", message: "Purchase intent terms are invalid" },
      { status: 400 },
    );
  }
  const quote = quotes.get(body.quoteId);
  if (!quote || quote.productId !== body.businessId) {
    return HttpResponse.json(
      { code: "CATALOG_404_002", message: "Team price quote not found" },
      { status: 404 },
    );
  }
  if (Date.parse(quote.expiresAt ?? "") <= Date.now()) {
    return HttpResponse.json(
      { code: "CATALOG_409_007", message: "Team price quote has expired" },
      { status: 409 },
    );
  }
  return null;
}

// UC7 invites (T3-05): one ref per bloc for the sample buyer, reused on every later mint as the
// backend does. Only an open bloc the buyer is in can be shared: Share invite link on an Active
// My blocs row, or the share block on a listing after joining its bloc, copies a link that opens
// the invite landing (`?msw=1` in a fresh tab).
const invites = new Map<string, string>();

function mintRef(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  // Base64 uses "=" only as trailing padding, so dropping every "=" strips just the padding.
  return btoa(String.fromCodePoint(...bytes))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replaceAll("=", "");
}

export async function startBrowserMsw() {
  // Read once at start-up: in-app navigation drops `me=1` from the URL, and a later `me`
  // refetch (root loader, callback) must not flip the sample session back to anonymous
  // (CodeRabbit r4050400123).
  const signedIn = new URLSearchParams(window.location.search).get("me") === "1";
  // The admin console (admin plan A1-02): with `me=1`, `admin=1` signs in as an admin over a
  // stateful store and anything else is a buyer (403 on every admin path), and `me` names that
  // role (#282); `conflict=1` makes the
  // next admin write find the server a version ahead (4b.4).
  const adminParams = new URLSearchParams(window.location.search);
  const adminStore = createAdminStore();
  if (adminParams.get("conflict") === "1") adminStore.conflictOnNextWrite();
  const role = adminParams.get("admin") === "1" ? "admin" : "buyer";
  const admin = signedIn ? adminHandlers(adminStore, { role }) : [];
  const worker = setupWorker(
    ...admin,
    ...createMswHandlers().all({
      pick: {
        // `?msw=1` browses anonymously (401, the interceptor's normal path); `?msw=1&me=1` is
        // signed in as the artboards' sample buyer, so the header, /account and the gated
        // sheet can be walked through offline. Browser seed only; the vitest handlers are set
        // per test.
        me: () =>
          signedIn
            ? HttpResponse.json({
                code: OK_CODE,
                message: "ok",
                data: { userId: "u-ada", nickname: "Ada Lim", avatarUrl: null, roles: [role] },
              })
            : HttpResponse.json({ code: "AUTH_401", message: "unauthenticated" }, { status: 401 }),
        // Sign out succeeds; the app settles `me` on the anonymous envelope itself, so the header
        // flips without a refetch. A reload with `&me=1` still signs back in (seed only).
        logout: () => HttpResponse.json({ code: OK_CODE, message: "ok" }),
        pageListings: ({ request }) => {
          const url = new URL(request.url);
          const page = Math.max(1, Number(url.searchParams.get("page") ?? 1));
          const size = Math.max(1, Number(url.searchParams.get("size") ?? 20));
          const category = url.searchParams.get("category");
          // The matcher runs over the full seed record (title, subtitle, description) as the
          // backend does, then the hits become summaries.
          const terms = keywordTerms(url.searchParams.get("keyword"));
          const filtered = seeds
            .filter((s) => (!category || s.category === category) && matchesKeyword(s, terms))
            .map(toSummary);
          return HttpResponse.json({
            code: OK_CODE,
            message: "ok",
            data: {
              items: filtered.slice((page - 1) * size, page * size),
              total: filtered.length,
              page,
              size,
            },
          });
        },
        myOrders: ({ request }) => {
          const url = new URL(request.url);
          const page = Math.max(1, Number(url.searchParams.get("page") ?? 1));
          const size = Math.max(1, Number(url.searchParams.get("size") ?? 20));
          return HttpResponse.json({
            code: OK_CODE,
            message: "ok",
            data: {
              items: orders.slice((page - 1) * size, page * size),
              total: orders.length,
              page,
              size,
            },
          });
        },
        myTeams: () =>
          HttpResponse.json({
            code: OK_CODE,
            message: "ok",
            data: { items: teams, total: teams.length, page: 1, size: 100 },
          }),
        // Payments are on for the sample buyer. START and JOIN freeze an intent and the checkout
        // session sends the buyer to the mock receipt; one designated JOIN is refused (above).
        createPaymentIntent: async ({ request }) => {
          const body = (await request.json()) as {
            operation?: string;
            businessId?: string;
            quoteId?: string | null;
          };
          const refused = quoteRefusal(body);
          if (refused) return refused;
          const target =
            body.operation === "START"
              ? {
                  listing: details.find(
                    (p) => p.id === body.businessId && p.groupPrice !== undefined,
                  ),
                  deal: undefined,
                }
              : joinTarget(body.businessId);
          const listing = target?.listing;
          if (!listing || (body.operation !== "START" && body.operation !== "JOIN")) {
            return HttpResponse.json(
              { code: "GROUPBUY_404_001", message: "Group deal not found" },
              { status: 404 },
            );
          }
          if (body.operation === "JOIN" && body.businessId === FULL_ON_JOIN) {
            return HttpResponse.json(
              { code: "GROUPBUY_409_004", message: "Group deal is full" },
              { status: 409 },
            );
          }
          if (body.operation === "JOIN" && teams.some((t) => t.dealId === body.businessId)) {
            return HttpResponse.json(
              { code: "GROUPBUY_409_003", message: "Already joined this group deal" },
              { status: 409 },
            );
          }
          const n = seeds.find((x) => id(x.n) === listing.id)?.n ?? 1;
          const intentId = crypto.randomUUID();
          intents.set(intentId, { n, deal: target?.deal });
          return HttpResponse.json({ code: OK_CODE, message: "ok", data: { intentId } });
        },
        createPaymentCheckout: async ({ request }) => {
          const body = (await request.json()) as { intentId?: string };
          const purchase = intents.get(body.intentId ?? "");
          if (!purchase) {
            return HttpResponse.json(
              { code: "PAYMENT_409_001", message: "Payment intent is no longer valid" },
              { status: 409 },
            );
          }
          // Same intent, same payment: a retried Continue must not open a second checkout.
          purchase.paymentId ??= openCheckout(purchase);
          const { paymentId } = purchase;
          return HttpResponse.json({
            code: OK_CODE,
            message: "ok",
            data: { paymentId, status: "CREATED" },
          });
        },
        createShareInvite: ({ params }) => {
          const team = teams.find((t) => t.dealId === params.dealId);
          if (!team) {
            return HttpResponse.json(
              { code: "GROUPBUY_404_001", message: "Group deal not found" },
              { status: 404 },
            );
          }
          if (team.status !== "open") {
            return HttpResponse.json(
              { code: "GROUPBUY_409_002", message: "Group deal is not accepting members" },
              { status: 409 },
            );
          }
          const ref = invites.get(params.dealId) ?? mintRef();
          invites.set(params.dealId, ref);
          return HttpResponse.json({
            code: OK_CODE,
            message: "ok",
            data: {
              ref,
              dealId: params.dealId,
              productId: team.productId,
              expiresAt: team.expireTime,
            },
          });
        },
        // FB-19: a ref resolves to its bloc and the sharer, the sample buyer (the only one who
        // can mint here). A fresh tab has no minted refs yet, so the ref parked from the link it
        // opened resolves to the bloc that link named; anything else is an unknown ref.
        resolveInvite: ({ params }) => {
          const parked = readParkedInvite();
          const minted = [...invites].find(([, ref]) => ref === params.ref)?.[0];
          const dealId = minted ?? (parked?.ref === params.ref ? parked.dealId : undefined);
          const team = teams.find((t) => t.dealId === dealId);
          if (!dealId || !team) {
            return HttpResponse.json(
              { code: "GROUPBUY_404_002", message: "Invite not found" },
              { status: 404 },
            );
          }
          return HttpResponse.json({
            code: OK_CODE,
            message: "ok",
            data: {
              dealId,
              productId: team.productId,
              sharerNickname: "Ada Lim",
              expiresAt: team.expireTime,
            },
          });
        },
        getPaymentCapabilities: () =>
          HttpResponse.json({ code: OK_CODE, message: "ok", data: { enabled: signedIn } }),
        issueTeamPriceQuote: async ({ request }) => {
          const body = (await request.json()) as { productId?: string };
          const quote = issueQuote(body.productId ?? "");
          return quote
            ? HttpResponse.json({ code: OK_CODE, message: "ok", data: quote })
            : HttpResponse.json(
                { code: "CATALOG_409_001", message: "Team price is unavailable" },
                { status: 409 },
              );
        },
        getTeamPriceQuote: ({ params }) => {
          const quote = quotes.get(params.quoteId);
          return quote
            ? HttpResponse.json({ code: OK_CODE, message: "ok", data: quote })
            : HttpResponse.json(
                { code: "CATALOG_404_002", message: "Team price quote not found" },
                { status: 404 },
              );
        },
        myOrder: ({ params }) => {
          const found = orders.find((o) => o.orderId === params.orderId);
          return found
            ? HttpResponse.json({ code: OK_CODE, message: "ok", data: found })
            : HttpResponse.json(
                { code: "ORDER_404_001", message: "Order not found" },
                { status: 404 },
              );
        },
        // Sample receipts for the §4.6 card states (T3-06): `/payments/<id>` with the settled ids
        // or a mock checkout's; any other id is not the buyer's.
        getPaymentReceipt: ({ params }) => {
          const checkout = checkouts.get(params.id);
          const receipt = checkout
            ? readCheckout(params.id, checkout)
            : settledReceipts.find((r) => r.paymentId === params.id);
          return receipt
            ? HttpResponse.json({ code: OK_CODE, message: "ok", data: receipt })
            : HttpResponse.json(
                { code: "PAYMENT_404_001", message: "Payment not found" },
                { status: 404 },
              );
        },
        getListing: ({ params }) => {
          const found = details.find((p) => p.id === params.id);
          if (!found) {
            return HttpResponse.json(
              { code: "PRODUCT_404_001", message: "Listing not found" },
              { status: 404 },
            );
          }
          const bloc = invitedBloc(found.id ?? "");
          const data = bloc ? { ...found, deal: bloc } : found;
          return HttpResponse.json({ code: OK_CODE, message: "ok", data });
        },
      },
    }),
  );
  await worker.start({ onUnhandledRequest: "bypass" });
}
