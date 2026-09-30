import { test as base, type Route } from "@playwright/test";

import type {
  MeResponse,
  MyTeamResponse,
  OrderSummaryResponse,
  ProductDetailResponse,
  ProductSummaryResponse,
  QuoteResponse,
  Receipt,
} from "@/api/generated/types.gen";

// Shared `page.route` stubs for the e2e specs (backlog FB-02). The production build drops
// `?msw=1`, so a journey that needs data answers /api/v1 here instead. Only generated types come
// from the app; they are erased at run time, so the e2e folder still imports no app module.
//
// The `api` fixture is lazy: a test that does not ask for it (the @auth half against dev) talks
// to the real backend. A test that does gets one catch-all route over /api/v1, answered from a
// per-test `state` it may mutate before or during the journey. Every call is recorded, so a spec
// can assert that a request was never made. A path the state does not serve answers 501 with
// `E2E_501_UNSTUBBED` rather than falling through to `vite preview`'s index.html.

// `OK_CODE` in `src/api/envelope.ts`.
const OK = "COMMON_200_000";
const API = "/api/v1/";

interface Call {
  method: string;
  path: string;
  body: unknown;
}

type Handler = (route: Route, call: Call) => Promise<void>;

interface ApiState {
  /** `GET /auth/me`; null answers 401, the anonymous visitor. */
  me: MeResponse | null;
  /** `GET /payments/capabilities` (read only when signed in). */
  paymentsEnabled: boolean;
  /** `GET /products` lists their summaries; `GET /products/{id}` serves one. */
  listings: ProductDetailResponse[];
  /** `POST /team-price-quotes` issues one for a listing with a `groupPrice`; `GET` reads it. */
  quotes: Map<string, QuoteResponse>;
  /** `GET /group-deals/me` (My blocs, and the listing's membership check). */
  teams: MyTeamResponse[];
  /** `GET /orders/me` lists them; `GET /orders/{orderId}` serves one. */
  orders: OrderSummaryResponse[];
  /** `GET /payments/{id}`: successive reads walk the list, and the last one repeats. */
  receipts: Map<string, Receipt[]>;
  /** `paymentId` of `POST /payments/checkout-sessions`; a UUID, as `/payments/return` requires. */
  paymentId: string;
  /** `checkoutUrl` of `POST /payments/checkout-sessions`. */
  checkoutUrl: string;
}

interface Api {
  state: ApiState;
  /** A handler for `method` on paths matching `path`; checked before the state. */
  override: (method: string, path: RegExp, handler: Handler) => void;
  /** Recorded /api/v1 calls, optionally filtered by method and path. */
  calls: (method?: string, path?: RegExp) => Call[];
}

// A quote's validity, as the backend issues it (T3-01).
const QUOTE_TTL_MS = 15 * 60 * 1000;

const envelope = (data: unknown) => ({ code: OK, message: "ok", data });

const page1 = (items: unknown[], size = 100) =>
  envelope({ items, total: items.length, page: 1, size });

function fail(route: Route, status: number, code: string, message: string) {
  return route.fulfill({ status, json: { code, message } });
}

const SUMMARY_FIELDS = [
  "id",
  "title",
  "subtitle",
  "coverImage",
  "category",
  "individualPrice",
  "groupPrice",
  "currency",
] as const satisfies readonly (keyof ProductSummaryResponse)[];

// The fields `GET /products` shares with the detail; absent ones stay absent.
function summaryOf(p: ProductDetailResponse): ProductSummaryResponse {
  return Object.fromEntries(SUMMARY_FIELDS.filter((k) => p[k] !== undefined).map((k) => [k, p[k]]));
}

function bodyOf(route: Route): unknown {
  try {
    return route.request().postDataJSON();
  } catch {
    return route.request().postData();
  }
}

/** A listing with an open bloc (3 of 5, two hours left); `overrides` replace its fields. */
export function listing(overrides: ProductDetailResponse = {}): ProductDetailResponse {
  return {
    id: "a0000000-0000-7000-8000-0000000e2e01",
    title: "Thai jasmine rice 10 kg",
    subtitle: "New crop, vacuum packed",
    category: "home",
    individualPrice: 89,
    groupPrice: 71,
    currency: "sgd",
    description: "Fragrant long grain, 10 kg sack.",
    images: [],
    stock: 40,
    soldCount: 12,
    deal: {
      dealId: "a0000000-0000-7000-8000-0000000e2e01",
      currentSize: 3,
      targetSize: 5,
      expireTime: new Date(Date.now() + 2 * 60 * 60 * 1000).toISOString(),
    },
    ...overrides,
  };
}

// A quote at the listing's bloc price, as the backend issues it; a listing without one answers
// the backend's "team price unavailable" 409.
function issueQuote(state: ApiState, route: Route, call: Call): Promise<void> {
  const { body } = call;
  const productId =
    typeof body === "object" && body !== null && "productId" in body ? body.productId : undefined;
  const found = state.listings.find((l) => l.id === productId);
  // `groupPrice` is nullable in the contract (#268): no team price set answers the same 409.
  if (found?.id === undefined || found.groupPrice == null) {
    return fail(route, 409, "CATALOG_409_001", "team price is unavailable");
  }
  const now = Date.now();
  const quote: QuoteResponse = {
    id: crypto.randomUUID(),
    productId: found.id,
    policyVersion: 1,
    amount: found.groupPrice.toFixed(2),
    currency: found.currency ?? "sgd",
    issuedAt: new Date(now).toISOString(),
    expiresAt: new Date(now + QUOTE_TTL_MS).toISOString(),
  };
  state.quotes.set(quote.id ?? "", quote);
  return route.fulfill({ json: envelope(quote) });
}

function nextReceipt(reads: Receipt[] | undefined): Receipt | undefined {
  if (reads === undefined || reads.length === 0) return undefined;
  return reads.length > 1 ? reads.shift() : reads[0];
}

// `found` or the backend's 404 for that resource.
function oneOf(route: Route, found: unknown, code: string): Promise<void> {
  return found === undefined
    ? fail(route, 404, code, "not found")
    : route.fulfill({ json: envelope(found) });
}

type Stub = (state: ApiState, route: Route, call: Call, id: string) => Promise<void>;

// One row per endpoint the state serves: method, path (a capture group names the id) and answer.
const STUBS: [string, RegExp, Stub][] = [
  [
    "GET",
    /^auth\/me$/u,
    (s, route) =>
      s.me ? route.fulfill({ json: envelope(s.me) }) : fail(route, 401, "AUTH_401", "anonymous"),
  ],
  [
    "GET",
    /^payments\/capabilities$/u,
    (s, route) => route.fulfill({ json: envelope({ enabled: s.paymentsEnabled }) }),
  ],
  [
    "GET",
    /^products$/u,
    (s, route) => route.fulfill({ json: page1(s.listings.map(summaryOf), 20) }),
  ],
  [
    "GET",
    /^products\/([^/]+)$/u,
    (s, route, _, id) =>
      oneOf(
        route,
        s.listings.find((l) => l.id === id),
        "CATALOG_404_001",
      ),
  ],
  ["POST", /^team-price-quotes$/u, (s, route, call) => issueQuote(s, route, call)],
  [
    "GET",
    /^team-price-quotes\/([^/]+)$/u,
    (s, route, _, id) => oneOf(route, s.quotes.get(id), "CATALOG_404_002"),
  ],
  ["GET", /^group-deals\/me$/u, (s, route) => route.fulfill({ json: page1(s.teams) })],
  ["GET", /^orders\/me$/u, (s, route) => route.fulfill({ json: page1(s.orders) })],
  [
    "GET",
    /^orders\/([^/]+)$/u,
    (s, route, _, id) =>
      oneOf(
        route,
        s.orders.find((o) => o.orderId === id),
        "ORDER_404_001",
      ),
  ],
  [
    "POST",
    /^payments\/intents$/u,
    (_, route) => route.fulfill({ json: envelope({ intentId: crypto.randomUUID() }) }),
  ],
  [
    "POST",
    /^payments\/checkout-sessions$/u,
    (s, route) =>
      route.fulfill({
        json: envelope({ paymentId: s.paymentId, checkoutUrl: s.checkoutUrl, status: "CREATED" }),
      }),
  ],
  [
    "GET",
    /^payments\/([^/]+)$/u,
    (s, route, _, id) => oneOf(route, nextReceipt(s.receipts.get(id)), "PAYMENT_404_001"),
  ],
];

function serveFromState(state: ApiState, route: Route, call: Call): Promise<void> {
  for (const [method, path, stub] of STUBS) {
    const match = method === call.method ? path.exec(call.path) : null;
    if (match) return stub(state, route, call, match[1] ?? "");
  }
  return fail(route, 501, "E2E_501_UNSTUBBED", `no e2e stub for ${call.method} ${API}${call.path}`);
}

// A spec may pass a /g or /y pattern; reset its lastIndex so repeated requests match every time.
function matches(pattern: RegExp, path: string): boolean {
  pattern.lastIndex = 0;
  return pattern.test(path);
}

export const test = base.extend<{ api: Api }>({
  // `provide` is Playwright's `use`, renamed so the React hooks lint does not mistake it for one.
  api: async ({ page }, provide) => {
    const state: ApiState = {
      me: null,
      paymentsEnabled: true,
      listings: [],
      quotes: new Map(),
      teams: [],
      orders: [],
      receipts: new Map(),
      paymentId: "0e2e0000-0000-4000-8000-000000000001",
      checkoutUrl: "https://checkout.stripe.com/c/pay/e2e",
    };
    const recorded: Call[] = [];
    const overrides: { method: string; path: RegExp; handler: Handler }[] = [];
    await page.route(
      (url) => url.pathname.startsWith(API),
      async (route) => {
        const request = route.request();
        const call: Call = {
          method: request.method(),
          path: new URL(request.url()).pathname.slice(API.length),
          body: bodyOf(route),
        };
        recorded.push(call);
        const hit = overrides.findLast(
          (o) => o.method === call.method && matches(o.path, call.path),
        );
        await (hit ? hit.handler(route, call) : serveFromState(state, route, call));
      },
    );
    await provide({
      state,
      override: (method, path, handler) => overrides.push({ method, path, handler }),
      calls: (method, path) =>
        recorded.filter(
          (c) =>
            (method === undefined || c.method === method) &&
            (path === undefined || matches(path, c.path)),
        ),
    });
  },
});

export { expect } from "@playwright/test";
