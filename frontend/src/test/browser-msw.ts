import { HttpResponse } from "msw";
import { setupWorker } from "msw/browser";

import { OK_CODE } from "@/api/envelope";
import { createMswHandlers } from "@/api/generated/msw.gen";
import type {
  OrderSummaryResponse,
  ProductDetailResponse,
  ProductSummaryResponse,
  QuoteResponse,
} from "@/api/generated/types.gen";
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

// The sample buyer's orders for `/account` (§4.4 rows): one per lifecycle state, paid over the
// last few days. `?msw=1&me=1` shows them; anonymous browsing never reaches the endpoint.
const orderSeeds: { n: number; quantity: number; status: string; daysAgo: number }[] = [
  { n: 1, quantity: 1, status: "pending", daysAgo: 0 },
  { n: 5, quantity: 2, status: "confirmed", daysAgo: 3 },
  { n: 8, quantity: 1, status: "cancelled", daysAgo: 6 },
];

const orders: OrderSummaryResponse[] = orderSeeds.map(({ n, quantity, status, daysAgo }) => {
  const s = seeds.find((x) => x.n === n);
  const unit = s?.groupPrice ?? s?.individualPrice ?? 0;
  const paid = inSeconds(-daysAgo * 86_400 - 3_600);
  return {
    orderId: `0192f0a0-0000-7000-8000-${String(n).padStart(12, "0")}`,
    orderNo: `BB-${2090 + n}`,
    dealId: `deal-${n}`,
    productId: id(n),
    productTitle: s?.title ?? "",
    productImage: null,
    quantity,
    unitPrice: unit.toFixed(2),
    totalAmount: (unit * quantity).toFixed(2),
    currency: "sgd",
    status,
    paidTime: paid,
    cancelTime: status === "cancelled" ? inSeconds(-daysAgo * 86_400 + 3_600) : null,
    createTime: paid,
  };
});

// First-team quotes (T3-01): the seed's bloc price, valid for 15 minutes; a listing without one
// answers the backend's "team price unavailable" 409. Issued quotes are kept for `getQuote`.
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

export async function startBrowserMsw() {
  // Read once at start-up: in-app navigation drops `me=1` from the URL, and a later `me`
  // refetch (root loader, callback) must not flip the sample session back to anonymous
  // (CodeRabbit r4050400123).
  const signedIn = new URLSearchParams(window.location.search).get("me") === "1";
  const worker = setupWorker(
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
                data: { userId: "u-ada", nickname: "Ada Lim", avatarUrl: null },
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
        // Payments are on for the sample buyer so Start a bloc opens the quote sheet; a START
        // intent is not mocked, so Continue ends on the page's "could not open Checkout" alert.
        // A JOIN always loses the last-seat race (GROUPBUY_409_004) and fills the bloc, so the
        // refetched row shows the "This bloc is full" state (T3-04).
        createPaymentIntent: async ({ request }) => {
          const body = (await request.json()) as { operation?: string; businessId?: string };
          const deal = details.find((p) => p.deal?.dealId === body.businessId)?.deal;
          if (body.operation !== "JOIN" || !deal) {
            return HttpResponse.json(
              { code: "PAYMENT_503_001", message: "Checkout is not mocked" },
              { status: 503 },
            );
          }
          deal.currentSize = deal.targetSize ?? 0;
          return HttpResponse.json(
            { code: "GROUPBUY_409_004", message: "Group deal is full" },
            { status: 409 },
          );
        },
        getPaymentCapabilities: () =>
          HttpResponse.json({ code: OK_CODE, message: "ok", data: { enabled: signedIn } }),
        issue: async ({ request }) => {
          const body = (await request.json()) as { productId?: string };
          const quote = issueQuote(body.productId ?? "");
          return quote
            ? HttpResponse.json({ code: OK_CODE, message: "ok", data: quote })
            : HttpResponse.json(
                { code: "CATALOG_409_001", message: "Team price is unavailable" },
                { status: 409 },
              );
        },
        getQuote: ({ params }) => {
          const quote = quotes.get(params.quoteId);
          return quote
            ? HttpResponse.json({ code: OK_CODE, message: "ok", data: quote })
            : HttpResponse.json(
                { code: "CATALOG_404_002", message: "Team price quote not found" },
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
          return HttpResponse.json({ code: OK_CODE, message: "ok", data: found });
        },
      },
    }),
  );
  await worker.start({ onUnhandledRequest: "bypass" });
}
