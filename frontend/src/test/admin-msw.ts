import { HttpResponse, http } from "msw";

import { OK_CODE } from "@/api/envelope";
import type { ListingContent, PolicyResponse } from "@/api/generated/types.gen";
import { toCents } from "@/features/admin/money";

// A stateful stand-in for the admin catalog API (admin plan A1-02), shared by the Vitest route
// tests (`serveAdmin()` in render-route.tsx) and the offline browser demo (`?msw=1&me=1&admin=1`).
// It mirrors the backend rules the console must handle (admin plan §2.2) so they are exercised at
// the UI boundary; it does not replace the backend's own tests. Error codes are copied from
// `backend/blocbuy-domain/src/main/java/com/blocbuy/catalog/exception/CatalogErrorCode.java` and
// `CommonErrorCode.java` (BAD_REQUEST, FORBIDDEN); keep them in step with those files.

const E = {
  badRequest: { code: "COMMON_400_001", status: 400, message: "Invalid request" },
  validationFailed: { code: "COMMON_400_002", status: 400, message: "Request validation failed" },
  forbidden: { code: "COMMON_403_001", status: 403, message: "Permission denied" },
  notFound: { code: "CATALOG_404_001", status: 404, message: "Product not found" },
  productInvalid: { code: "CATALOG_400_002", status: 400, message: "Invalid listing" },
  stateConflict: {
    code: "CATALOG_409_002",
    status: 409,
    message: "Listing state does not allow this operation",
  },
  numberConflict: {
    code: "CATALOG_409_003",
    status: 409,
    message: "Product number already exists",
  },
  referenced: {
    code: "CATALOG_409_004",
    status: 409,
    message: "Listing has business history and cannot be deleted",
  },
  versionConflict: {
    code: "CATALOG_412_002",
    status: 412,
    message: "Listing version precondition failed",
  },
  policyInvalid: { code: "CATALOG_400_001", status: 400, message: "Invalid team price policy" },
  policyUnavailable: { code: "CATALOG_409_001", status: 409, message: "Team price is unavailable" },
  policyVersionConflict: {
    code: "CATALOG_412_001",
    status: 412,
    message: "Team price policy version conflict",
  },
} as const;

type Failure = (typeof E)[keyof typeof E];

const fail = (e: Failure, errors?: unknown) =>
  HttpResponse.json(
    { code: e.code, message: e.message, ...(errors === undefined ? {} : { errors }) },
    { status: e.status },
  );
// A 412 names the version the server holds now (#286): the listing's for CATALOG_412_002, the
// latest policy revision's (0 if none) for CATALOG_412_001.
const staleAt = (e: Failure, currentVersion: number) => fail(e, { currentVersion });
const ok = (data?: unknown) =>
  HttpResponse.json(
    data === undefined ? { code: OK_CODE, message: "ok" } : { code: OK_CODE, message: "ok", data },
  );

type Status = "draft" | "on_sale" | "off_sale";

interface AdminRow {
  id: string;
  productNo: string;
  listing: ListingContent;
  status: Status;
  pricingMode: string;
  soldCount: number;
  version: number;
}

type Policy = Required<PolicyResponse>;

// #242 added `teamTerms` to every admin listing response. The terms endpoint itself is not mocked
// yet (admin plan A3-04), so every listing reads as never configured, the backend default. #293
// added `latestPolicy`: the latest rule revision without its `productId`, or null when none.
const view = (row: AdminRow, policy: Policy | undefined) => {
  const { productId: _productId, ...latestPolicy } = policy ?? {};
  return {
    ...row,
    teamTerms: { targetSize: null, lifetimeSeconds: null, configured: false },
    latestPolicy: policy ? latestPolicy : null,
  };
};

const rowId = (n: number) => `0192f0a0-0000-7000-a000-${String(n).padStart(12, "0")}`;
const daysFromNow = (d: number) => new Date(Date.now() + d * 86_400_000).toISOString();

// The 4a.2 table's first page: product numbers, statuses and versions from the artboard; titles,
// prices and stock follow the storefront sample listings (browser-msw.ts). BB-100472 is the 4b.2
// draft and BB-100481 the 4b.3 on-sale listing ("v4 · 142 sold").
const artboardRows: [string, string, string, Status, number, number, number][] = [
  ["BB-100481", "Thai jasmine rice 10 kg", "89.00", "on_sale", 4, 38, 142],
  ["BB-100480", "D24 durian, per kg", "38.00", "on_sale", 7, 24, 61],
  ["BB-100479", "Laundry detergent 5 L twin pack", "42.90", "on_sale", 2, 80, 210],
  [
    "BB-100478",
    "Replacement HEPA filters for the Coway Airmega 150, twin pack",
    "118.00",
    "on_sale",
    1,
    12,
    9,
  ],
  ["BB-100477", "Kopi powder 500 g", "12.80", "on_sale", 9, 150, 388],
  ["BB-100476", "Fold-out drying rack", "56.00", "off_sale", 3, 30, 17],
  ["BB-100474", "Kids’ stationery bundle", "24.00", "on_sale", 5, 60, 44],
  ["BB-100472", "Hokkaido milk, 12 cartons", "46.80", "draft", 0, 45, 0],
];

const fillerTitles = [
  "Pandan kaya jar",
  "Rattan laundry basket",
  "Soy sauce 1.8 L",
  "Bath towel set",
];
const fillerStatuses: Status[] = ["on_sale", "on_sale", "off_sale", "draft"];

// 62 rows, newest first, as the 4a.2 header counts them: the artboard's eight, then generated
// filler with a mix of statuses so every tab has rows.
export function adminSeed(): AdminRow[] {
  const rows: AdminRow[] = artboardRows.map(
    ([productNo, title, originalPrice, status, version, stock, soldCount], i) => ({
      id: rowId(i + 1),
      productNo,
      listing: { title, originalPrice, currency: "sgd", stock, category: "groceries", images: [] },
      status,
      pricingMode: "dual",
      soldCount,
      version,
    }),
  );
  for (let i = 0; rows.length < 62; i++) {
    const status = fillerStatuses[i % fillerStatuses.length] ?? "draft";
    rows.push({
      id: rowId(rows.length + 1),
      productNo: `BB-${100_471 - i}`,
      listing: {
        title: `${fillerTitles[i % fillerTitles.length] ?? "Listing"} (${i + 1})`,
        originalPrice: `${10 + (i % 40)}.50`,
        currency: "sgd",
        stock: 20 + i,
        images: [],
      },
      status,
      pricingMode: "dual",
      soldCount: status === "draft" ? 0 : i * 3,
      version: status === "draft" ? 0 : status === "on_sale" ? 1 + (i % 4) : 2 + (i % 4),
    });
  }
  return rows;
}

// The 4b.5 rule states for the two listings that carry one: rice active at v3, durian expired v2.
function seedPolicies(rows: AdminRow[]) {
  const policies = new Map<string, Policy>();
  const [rice, durian] = rows;
  if (rice?.productNo === "BB-100481") {
    policies.set(rice.id, {
      productId: rice.id,
      amount: "71.00",
      currency: "sgd",
      validFrom: daysFromNow(-10),
      validUntil: daysFromNow(20),
      version: 3,
    });
  }
  if (durian?.productNo === "BB-100480") {
    policies.set(durian.id, {
      productId: durian.id,
      amount: "32.00",
      currency: "sgd",
      validFrom: daysFromNow(-30),
      validUntil: daysFromNow(-1),
      version: 2,
    });
  }
  return policies;
}

type History = { deals: number; quotes: number };

// The bloc and quote rows the mock does not model, as counts per listing (#300): the 4b.3 rice
// listing "has 26 bloc records, 61 quotes and 3 price rules". Its 3 rules are its policy revisions.
function seedHistory(rows: AdminRow[]) {
  const history = new Map<string, History>();
  const [rice] = rows;
  if (rice?.productNo === "BB-100481") history.set(rice.id, { deals: 26, quotes: 61 });
  return history;
}

// `openDeals` stands in for the bloc tables the mock does not model: the `counts` read (#290)
// reports it as is. 26 is the artboard's Open blocs figure (4a.1).
export function createAdminStore(seed: AdminRow[] = adminSeed(), { openDeals = 26 } = {}) {
  const rows = seed.map((r) => ({ ...r, listing: { ...r.listing } }));
  const policies = seedPolicies(rows);
  const history = seedHistory(rows);
  // Every retained row the backend's DELETE veto counts; each policy revision is one rule.
  const references = (id: string) => ({
    deals: history.get(id)?.deals ?? 0,
    quotes: history.get(id)?.quotes ?? 0,
    policies: policies.get(id)?.version ?? 0,
  });
  let conflictArmed = false;
  let created = 0;
  // The backend's `seq_biz_product_product_no` (V21, #288): starts at 100001 and skips numbers
  // already taken.
  let sequence = 100_000;
  return {
    rows,
    policies,
    openDeals,
    policyOf: (id: string) => policies.get(id),
    references,
    // Tests give a listing bloc or quote history the mock cannot create itself.
    addHistory: (id: string, more: Partial<History>) => {
      const was = history.get(id) ?? { deals: 0, quotes: 0 };
      history.set(id, {
        deals: was.deals + (more.deals ?? 0),
        quotes: was.quotes + (more.quotes ?? 0),
      });
    },
    find: (id: string) => rows.find((r) => r.id === id),
    // `&conflict=1` and the 4b.4 tests: the next write finds the server one version ahead, as if
    // another admin had saved first.
    conflictOnNextWrite: () => {
      conflictArmed = true;
    },
    takeConflict: () => {
      const armed = conflictArmed;
      conflictArmed = false;
      return armed;
    },
    nextId: () => rowId(1000 + ++created),
    nextProductNo: () => {
      let no: string;
      do no = `BB-${++sequence}`;
      while (rows.some((r) => r.productNo === no));
      return no;
    },
    remove: (id: string) => {
      const i = rows.findIndex((r) => r.id === id);
      if (i >= 0) rows.splice(i, 1);
    },
  };
}

type AdminStore = ReturnType<typeof createAdminStore>;

// Only a never-published, unsold draft with no history may be deleted (#300).
function historyBlocks(store: AdminStore, row: AdminRow) {
  const { deals, quotes, policies } = store.references(row.id);
  return row.soldCount > 0 || deals + quotes + policies > 0;
}

// The single admin read adds the retained history and the snapshot eligibility (#300); page and
// mutation responses omit both.
const viewOf = (store: AdminStore, row: AdminRow) => view(row, store.policyOf(row.id));

const detail = (store: AdminStore, row: AdminRow) => ({
  ...viewOf(store, row),
  references: store.references(row.id),
  deletable: row.status === "draft" && !historyBlocks(store, row),
});

const PRODUCT_NO = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;

// Bean validation on `ListingContent`, reported the way GlobalExceptionHandler does: 400
// COMMON_400_002 with `errors: [{field, message}]` under the request's field path. The domain's
// own CATALOG_400_002 (#323) repeats these rules with the same paths, so it is not reached here.
function listingViolations(listing: Partial<ListingContent> | undefined) {
  const errors: { field: string; message: string }[] = [];
  const add = (field: string, message: string) =>
    errors.push({ field: `listing.${field}`, message });
  const l = listing ?? {};
  if (typeof l.title !== "string" || l.title.trim() === "") add("title", "must not be blank");
  else if (l.title.length > 256) add("title", "size must be between 0 and 256");
  if ((l.subtitle?.length ?? 0) > 256) add("subtitle", "size must be between 0 and 256");
  if ((l.description?.length ?? 0) > 10_000) add("description", "size must be between 0 and 10000");
  if ((l.coverImage?.length ?? 0) > 1024) add("coverImage", "size must be between 0 and 1024");
  if ((l.images?.length ?? 0) > 20) add("images", "size must be between 0 and 20");
  if ((l.category?.length ?? 0) > 64) add("category", "size must be between 0 and 64");
  const cents = typeof l.originalPrice === "string" ? toCents(l.originalPrice) : null;
  if (cents === null || cents <= 0n) add("originalPrice", "must be a positive amount");
  if (l.currency !== "sgd") add("currency", "must be sgd");
  if (!Number.isInteger(l.stock) || (l.stock ?? -1) < 0) add("stock", "must be at least 0");
  return errors;
}

const param = (v: string | readonly string[] | undefined) => (typeof v === "string" ? v : "");
const isStatus = (v: unknown): v is Status => v === "draft" || v === "on_sale" || v === "off_sale";

const sameCents = (a: string, b: string) => toCents(a) !== null && toCents(a) === toCents(b);

// The mock trusts only the shape: every handler checks the fields it reads, as the backend's
// validation would, so a malformed body still ends in the backend's answer.
async function bodyOf<T>(request: Request): Promise<Partial<T>> {
  try {
    // oxlint-disable-next-line typescript/no-unsafe-type-assertion
    return ((await request.json()) ?? {}) as Partial<T>;
  } catch {
    return {};
  }
}

// Whether the listing must be refused as stale: an armed conflict bumps the server copy first.
function stale(store: AdminStore, row: AdminRow, expected: unknown) {
  if (store.takeConflict()) row.version += 1;
  return expected !== row.version;
}

// A rule the backend's `TeamPricePolicy` refuses on its own, with the field path and message it
// sends since #323 (BA-6).
function policyProblem(
  body: Partial<{ currency: string; validFrom: string; validUntil: string }>,
  amount: bigint,
): { field: string; message: string } | null {
  const from = Date.parse(body.validFrom ?? "");
  const until = Date.parse(body.validUntil ?? "");
  if (Number.isNaN(from)) return { field: "validFrom", message: "Start time is required" };
  if (Number.isNaN(until) || !(from < until))
    return { field: "validUntil", message: "Must be after the start" };
  if (body.currency !== "sgd") return { field: "currency", message: "Must be SGD (sgd)" };
  if (amount <= 0n)
    return { field: "amount", message: "Must be positive and at most 9999999999999999.99" };
  return null;
}

export function adminHandlers(store: AdminStore, { role }: { role: "admin" | "buyer" }) {
  const admin = "*/api/v1/admin/products";
  const policy = "*/api/v1/products/:productId/team-price-policy";

  // Sa-Token checks the role before anything else runs, reads included (§2.2 "Admin
  // authorisation today"); a buyer, or a disabled account, gets the same 403.
  const guarded =
    <
      A extends {
        request: Request;
        params: Record<string, string | readonly string[] | undefined>;
      },
    >(
      handler: (info: A) => Response | Promise<Response>,
    ) =>
    (info: A) =>
      role === "admin" ? handler(info) : fail(E.forbidden);

  return [
    http.get(
      admin,
      guarded(({ request }) => {
        const url = new URL(request.url);
        const page = Number(url.searchParams.get("page") ?? 1);
        const size = Number(url.searchParams.get("size") ?? 20);
        const status = url.searchParams.get("status");
        const errors: string[] = [];
        if (!Number.isInteger(page) || page < 1)
          errors.push("page must be greater than or equal to 1");
        if (!Number.isInteger(size) || size < 1 || size > 100)
          errors.push("size must be between 1 and 100");
        if (status !== null && !/^(draft|on_sale|off_sale)$/.test(status))
          errors.push('status must match "^(draft|on_sale|off_sale)$"');
        if (errors.length > 0) return fail(E.badRequest, errors);
        const hits = status === null ? store.rows : store.rows.filter((r) => r.status === status);
        return ok({
          items: hits.slice((page - 1) * size, page * size).map((row) => viewOf(store, row)),
          total: hits.length,
          page,
          size,
        });
      }),
    ),

    http.post(
      admin,
      guarded(async ({ request }) => {
        const body = await bodyOf<{ productNo?: string | null; listing: ListingContent }>(request);
        const errors = listingViolations(body.listing);
        // #288: an omitted or null number is generated; a supplied one is validated and unique.
        const supplied = body.productNo ?? null;
        if (supplied !== null && (typeof supplied !== "string" || !PRODUCT_NO.test(supplied)))
          errors.unshift({ field: "productNo", message: "must match the product number pattern" });
        if (errors.length > 0 || !body.listing) return fail(E.validationFailed, errors);
        if (supplied !== null && store.rows.some((r) => r.productNo === supplied))
          return fail(E.numberConflict);
        const row: AdminRow = {
          id: store.nextId(),
          productNo: supplied ?? store.nextProductNo(),
          listing: body.listing,
          status: "draft",
          pricingMode: "dual",
          soldCount: 0,
          version: 0,
        };
        store.rows.unshift(row);
        return ok(viewOf(store, row));
      }),
    ),

    // Registered ahead of `/:productId`, which would read "counts" as an id. One snapshot of the
    // non-deleted rows per status, as the page filters see them (#290).
    http.get(
      `${admin}/counts`,
      guarded(() =>
        ok({
          draft: store.rows.filter((r) => r.status === "draft").length,
          onSale: store.rows.filter((r) => r.status === "on_sale").length,
          offSale: store.rows.filter((r) => r.status === "off_sale").length,
          openDeals: store.openDeals,
        }),
      ),
    ),

    http.get(
      `${admin}/:productId`,
      guarded(({ params }) => {
        const row = store.find(param(params.productId));
        return row ? ok(detail(store, row)) : fail(E.notFound);
      }),
    ),

    // Full replacement: an omitted optional field is cleared. After the first publication the
    // price, currency and stock must equal the stored values (409 otherwise).
    http.put(
      `${admin}/:productId`,
      guarded(async ({ request, params }) => {
        const body = await bodyOf<{ listing: ListingContent; expectedVersion: number }>(request);
        const next = body.listing;
        const errors = listingViolations(next);
        if (errors.length > 0 || !next) return fail(E.validationFailed, errors);
        const row = store.find(param(params.productId));
        if (!row) return fail(E.notFound);
        if (stale(store, row, body.expectedVersion)) return staleAt(E.versionConflict, row.version);
        if (
          row.status !== "draft" &&
          (!sameCents(next.originalPrice, row.listing.originalPrice) ||
            next.currency !== row.listing.currency ||
            next.stock !== row.listing.stock)
        )
          return fail(E.stateConflict);
        row.listing = { ...next };
        row.version += 1;
        return ok(viewOf(store, row));
      }),
    ),

    // draft → on_sale ↔ off_sale; no way back to draft. Going on sale needs positive stock and
    // price, which the backend checks before the state dispatch (still 409).
    http.put(
      `${admin}/:productId/status`,
      guarded(async ({ request, params }) => {
        const body = await bodyOf<{ status: string; expectedVersion: number }>(request);
        const row = store.find(param(params.productId));
        if (!row) return fail(E.notFound);
        if (stale(store, row, body.expectedVersion)) return staleAt(E.versionConflict, row.version);
        const allowed =
          (body.status === "on_sale" && row.status !== "on_sale") ||
          (body.status === "off_sale" && row.status === "on_sale");
        const sellable = row.listing.stock > 0 && (toCents(row.listing.originalPrice) ?? 0n) > 0n;
        if (!allowed || !isStatus(body.status) || (body.status === "on_sale" && !sellable))
          return fail(E.stateConflict);
        row.status = body.status;
        row.version += 1;
        return ok(viewOf(store, row));
      }),
    ),

    // Only a never-published draft with no history is deleted (soft delete; a repeat is 404).
    http.delete(
      `${admin}/:productId`,
      guarded(({ request, params }) => {
        const raw = new URL(request.url).searchParams.get("expectedVersion");
        if (raw === null || !/^\d+$/.test(raw))
          return fail(E.badRequest, ["expectedVersion must not be null"]);
        const row = store.find(param(params.productId));
        if (!row) return fail(E.notFound);
        if (stale(store, row, Number(raw))) return staleAt(E.versionConflict, row.version);
        if (row.status !== "draft") return fail(E.stateConflict);
        if (historyBlocks(store, row)) return fail(E.referenced);
        store.remove(row.id);
        return ok();
      }),
    ),

    // The latest revision only; no rule is a 409, not a 404.
    http.get(
      policy,
      guarded(({ params }) => {
        const id = param(params.productId);
        if (!store.find(id)) return fail(E.notFound);
        const found = store.policyOf(id);
        return found ? ok(found) : fail(E.policyUnavailable);
      }),
    ),

    http.put(
      policy,
      guarded(async ({ request, params }) => {
        const id = param(params.productId);
        const row = store.find(id);
        if (!row) return fail(E.notFound);
        const body = await bodyOf<{
          amount: string;
          currency: string;
          validFrom: string;
          validUntil: string;
          expectedVersion: number;
        }>(request);
        const current = store.policyOf(id);
        // The backend's order (TeamPriceAppServiceImpl.configure): the version under the row
        // lock, then the rule itself, then the listing it prices, then the end against now.
        // An armed conflict always refuses, even for a first rule (absent policy = version 0).
        const conflicted = store.takeConflict();
        if (conflicted && current) current.version += 1;
        if (conflicted || body.expectedVersion !== (current?.version ?? 0))
          return staleAt(E.policyVersionConflict, current?.version ?? 0);
        const amount = typeof body.amount === "string" ? toCents(body.amount) : null;
        if (amount === null)
          return fail(E.validationFailed, [
            { field: "amount", message: "must be a decimal amount" },
          ]);
        const problem = policyProblem(body, amount);
        if (problem) return fail(E.policyInvalid, [problem]);
        if (row.status !== "on_sale") return fail(E.policyUnavailable);
        if (amount >= (toCents(row.listing.originalPrice) ?? 0n))
          return fail(E.policyInvalid, [
            { field: "amount", message: "Must be strictly below the solo price" },
          ]);
        if (!(Date.parse(body.validUntil ?? "") > Date.now()))
          return fail(E.policyInvalid, [{ field: "validUntil", message: "Must be in the future" }]);
        const next: Policy = {
          productId: id,
          amount: body.amount ?? "",
          currency: "sgd",
          validFrom: body.validFrom ?? "",
          validUntil: body.validUntil ?? "",
          version: (current?.version ?? 0) + 1,
        };
        store.policies.set(id, next);
        return ok(next);
      }),
    ),
  ];
}
