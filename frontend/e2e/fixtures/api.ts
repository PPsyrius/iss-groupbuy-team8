import { test as base, type Route } from "@playwright/test";

import type {
  MeResponse,
  ProductDetailResponse,
  ProductSummaryResponse,
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
  /** `GET /payments/{id}`: successive reads walk the list, and the last one repeats. */
  receipts: Map<string, Receipt[]>;
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

const envelope = (data: unknown) => ({ code: OK, message: "ok", data });

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

function serveFromState(state: ApiState, route: Route, call: Call, seq: number): Promise<void> {
  const { method, path } = call;
  if (method === "GET" && path === "auth/me") {
    return state.me
      ? route.fulfill({ json: envelope(state.me) })
      : fail(route, 401, "AUTH_401", "unauthenticated");
  }
  if (method === "GET" && path === "payments/capabilities") {
    return route.fulfill({ json: envelope({ enabled: state.paymentsEnabled }) });
  }
  if (method === "GET" && path === "products") {
    const items = state.listings.map(summaryOf);
    return route.fulfill({ json: envelope({ items, total: items.length, page: 1, size: 20 }) });
  }
  const product = /^products\/([^/]+)$/u.exec(path)?.[1];
  if (method === "GET" && product !== undefined) {
    const found = state.listings.find((l) => l.id === product);
    return found
      ? route.fulfill({ json: envelope(found) })
      : fail(route, 404, "CATALOG_404_001", "listing not found");
  }
  if (method === "POST" && path === "payments/intents") {
    return route.fulfill({ json: envelope({ intentId: `intent-e2e-${seq}` }) });
  }
  if (method === "POST" && path === "payments/checkout-sessions") {
    const paymentId = `payment-e2e-${seq}`;
    return route.fulfill({
      json: envelope({ paymentId, checkoutUrl: state.checkoutUrl, status: "CREATED" }),
    });
  }
  const payment = /^payments\/([^/]+)$/u.exec(path)?.[1];
  const reads = payment === undefined ? undefined : state.receipts.get(payment);
  if (method === "GET" && reads !== undefined && reads.length > 0) {
    const next = reads.length > 1 ? reads.shift() : reads[0];
    return route.fulfill({ json: envelope(next) });
  }
  return fail(route, 501, "E2E_501_UNSTUBBED", `no e2e stub for ${method} ${API}${path}`);
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
      receipts: new Map(),
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
        await (hit
          ? hit.handler(route, call)
          : serveFromState(state, route, call, recorded.length));
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
