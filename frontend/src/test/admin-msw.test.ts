import { describe, expect, it } from "vitest";

import { OK_CODE } from "@/api/envelope";
import { adminSeed, createAdminStore } from "@/test/admin-msw";
import { serveAdmin, serveNonAdmin } from "@/test/render-route";

const base = "http://localhost/api/v1";

async function call(method: string, path: string, body?: unknown) {
  const res = await fetch(`${base}${path}`, {
    method,
    headers: { "content-type": "application/json" },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  // A test reads whatever the mock answered; the assertions are the type check.
  // oxlint-disable-next-line typescript/no-unsafe-type-assertion
  const json = (await res.json()) as { code: string; data?: any; errors?: unknown };
  return { status: res.status, ...json };
}

const content = (over: Record<string, unknown> = {}) => ({
  title: "Pandan chiffon cake",
  originalPrice: "18.50",
  currency: "sgd",
  stock: 10,
  ...over,
});

const future = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString();

async function firstOf(status: string) {
  const page = await call("GET", `/admin/products?page=1&size=100&status=${status}`);
  return page.data.items[0];
}

// An on-sale listing with no bloc price rule yet.
async function unruled(store: ReturnType<typeof createAdminStore>) {
  const page = await call("GET", "/admin/products?page=1&size=100&status=on_sale");
  return page.data.items.find((r: { id: string }) => !store.policyOf(r.id));
}

describe("admin seed", () => {
  it("has 62 listings, newest first, led by the artboard's eight rows", () => {
    const rows = adminSeed();
    expect(rows).toHaveLength(62);
    expect(rows.slice(0, 8).map((r) => r.productNo)).toEqual([
      "BB-100481",
      "BB-100480",
      "BB-100479",
      "BB-100478",
      "BB-100477",
      "BB-100476",
      "BB-100474",
      "BB-100472",
    ]);
    expect(rows.slice(0, 8).map((r) => r.version)).toEqual([4, 7, 2, 1, 9, 3, 5, 0]);
    expect(rows[7]).toMatchObject({
      status: "draft",
      listing: { title: "Hokkaido milk, 12 cartons" },
    });
  });
});

describe("admin reads", () => {
  it("pages and filters by status, reporting the filtered total", async () => {
    serveAdmin();
    const all = await call("GET", "/admin/products?page=4&size=20");
    expect(all).toMatchObject({
      status: 200,
      code: OK_CODE,
      data: { total: 62, page: 4, size: 20 },
    });
    expect(all.data.items).toHaveLength(2);

    const drafts = await call("GET", "/admin/products?page=1&size=100&status=draft");
    expect(drafts.data.items.every((r: { status: string }) => r.status === "draft")).toBe(true);
    expect(drafts.data.total).toBe(drafts.data.items.length);
  });

  it("counts listings per status and the open blocs in one read (#290)", async () => {
    serveAdmin(createAdminStore(adminSeed(), { openDeals: 26 }));
    expect(await call("GET", "/admin/products/counts")).toMatchObject({
      status: 200,
      code: OK_CODE,
      data: { draft: 14, onSale: 34, offSale: 14, openDeals: 26 },
    });
    const draft = await firstOf("draft");
    await call("DELETE", `/admin/products/${draft.id}?expectedVersion=${draft.version}`);
    expect((await call("GET", "/admin/products/counts")).data).toMatchObject({ draft: 13 });
  });

  it("carries team terms on every admin read and write response, unset unless seeded", async () => {
    serveAdmin();
    const unset = { targetSize: null, lifetimeSeconds: null, configured: false };
    const page = await call("GET", "/admin/products?page=1&size=100");
    expect(page.data.items.every((r: { teamTerms?: unknown }) => r.teamTerms !== undefined)).toBe(
      true,
    );
    // The 4b.3 rice listing starts blocs of 5 for 2 hours; every other row was never configured.
    const [rice, durian] = page.data.items;
    expect(rice.teamTerms).toEqual({ targetSize: 5, lifetimeSeconds: 7200, configured: true });
    expect(durian.teamTerms).toEqual(unset);
    const one = await call("GET", `/admin/products/${durian.id}`);
    expect(one.data.teamTerms).toEqual(unset);
    const created = await call("POST", "/admin/products", {
      productNo: "BB-200009",
      listing: content(),
    });
    expect(created.data.teamTerms).toEqual(unset);
  });

  it("carries references and deletable on the single read only (#300)", async () => {
    const store = serveAdmin();
    const [rice] = store.rows;
    expect((await call("GET", `/admin/products/${rice?.id}`)).data).toMatchObject({
      references: { deals: 26, quotes: 61, policies: 3 },
      deletable: false,
    });
    const milk = store.rows.find((r) => r.productNo === "BB-100472");
    expect((await call("GET", `/admin/products/${milk?.id}`)).data).toMatchObject({
      references: { deals: 0, quotes: 0, policies: 0 },
      deletable: true,
    });

    const page = await call("GET", "/admin/products?page=1&size=100");
    expect(
      page.data.items.some(
        (r: object) => Object.hasOwn(r, "references") || Object.hasOwn(r, "deletable"),
      ),
    ).toBe(false);
    const created = await call("POST", "/admin/products", { listing: content() });
    expect(created.data).not.toHaveProperty("references");
    expect(created.data).not.toHaveProperty("deletable");
  });

  it("carries the latest policy on every admin read and write, null without one (#293)", async () => {
    const store = serveAdmin();
    const [rice, durian] = store.rows;
    const page = await call("GET", "/admin/products?page=1&size=100");
    const items: { id: string; latestPolicy?: unknown }[] = page.data.items;
    expect(items.every((r) => Object.hasOwn(r, "latestPolicy"))).toBe(true);
    const ruleOf = (id: string | undefined) => items.find((r) => r.id === id)?.latestPolicy;
    const { productId: _rice, ...riceRule } = store.policyOf(rice?.id ?? "") ?? {};
    expect(ruleOf(rice?.id)).toEqual(riceRule);
    expect(ruleOf(rice?.id)).toMatchObject({ amount: "71.00", currency: "sgd", version: 3 });
    expect(ruleOf(durian?.id)).toMatchObject({ amount: "32.00", version: 2 });
    expect(ruleOf(rice?.id)).not.toHaveProperty("productId");
    const milk = store.rows.find((r) => r.productNo === "BB-100472");
    expect(ruleOf(milk?.id)).toBeNull();

    expect((await call("GET", `/admin/products/${rice?.id}`)).data.latestPolicy).toEqual(riceRule);
    const revised = await call("PUT", `/admin/products/${rice?.id}`, {
      expectedVersion: rice?.version,
      listing: rice?.listing,
    });
    expect(revised.data.latestPolicy).toEqual(riceRule);
    const created = await call("POST", "/admin/products", { listing: content() });
    expect(created.data.latestPolicy).toBeNull();
  });

  it("shows a new rule revision on the next page read", async () => {
    const store = serveAdmin();
    const live = await unruled(store);
    const rule = { amount: "1.00", currency: "sgd", validFrom: future(0), validUntil: future(7) };
    await call("PUT", `/products/${live.id}/team-price-policy`, { ...rule, expectedVersion: 0 });
    const page = await call("GET", "/admin/products?page=1&size=100&status=on_sale");
    expect(page.data.items.find((r: { id: string }) => r.id === live.id).latestPolicy).toEqual({
      ...rule,
      version: 1,
    });
  });

  it("rejects bad paging with 400 COMMON_400_001", async () => {
    serveAdmin();
    expect(await call("GET", "/admin/products?page=0")).toMatchObject({
      status: 400,
      code: "COMMON_400_001",
    });
    expect(await call("GET", "/admin/products?size=101")).toMatchObject({ status: 400 });
    expect(await call("GET", "/admin/products?status=sold")).toMatchObject({ status: 400 });
  });

  it("answers an unknown listing with 404 CATALOG_404_001", async () => {
    serveAdmin();
    expect(await call("GET", "/admin/products/00000000-0000-0000-0000-000000000000")).toMatchObject(
      { status: 404, code: "CATALOG_404_001" },
    );
  });

  it("answers 403 COMMON_403_001 on every admin path for a buyer", async () => {
    serveNonAdmin();
    const id = adminSeed()[0]?.id;
    const replies = await Promise.all(
      (
        [
          ["GET", "/admin/products?page=1&size=1"],
          ["GET", "/admin/products/counts"],
          ["GET", `/admin/products/${id}`],
          ["POST", "/admin/products"],
          ["PUT", `/admin/products/${id}`],
          ["PUT", `/admin/products/${id}/status`],
          ["DELETE", `/admin/products/${id}?expectedVersion=4`],
          ["GET", `/products/${id}/team-price-policy`],
          ["PUT", `/products/${id}/team-price-policy`],
        ] as const
      ).map(([method, path]) => call(method, path, method === "GET" ? undefined : {})),
    );
    for (const reply of replies) {
      expect(reply).toMatchObject({ status: 403, code: "COMMON_403_001" });
    }
  });
});

describe("create", () => {
  it("creates a draft at version 0, newest first", async () => {
    serveAdmin();
    const res = await call("POST", "/admin/products", {
      productNo: "BB-200001",
      listing: content(),
    });
    expect(res).toMatchObject({
      status: 200,
      data: {
        productNo: "BB-200001",
        status: "draft",
        version: 0,
        pricingMode: "dual",
        soldCount: 0,
      },
    });
    const page = await call("GET", "/admin/products?page=1&size=1");
    expect(page.data).toMatchObject({ total: 63, items: [{ productNo: "BB-200001" }] });
  });

  it("numbers a draft sent without a product number, skipping taken numbers (#288)", async () => {
    serveAdmin();
    const omitted = await call("POST", "/admin/products", { listing: content() });
    expect(omitted).toMatchObject({ status: 200, data: { productNo: "BB-100001" } });
    await call("POST", "/admin/products", { productNo: "BB-100002", listing: content() });
    const nulled = await call("POST", "/admin/products", { productNo: null, listing: content() });
    expect(nulled).toMatchObject({ status: 200, data: { productNo: "BB-100003" } });
  });

  it("refuses a duplicate product number with 409 CATALOG_409_003", async () => {
    serveAdmin();
    expect(
      await call("POST", "/admin/products", { productNo: "BB-100481", listing: content() }),
    ).toMatchObject({ status: 409, code: "CATALOG_409_003" });
  });

  it("reports bean-validation failures as field violations with 400 COMMON_400_002", async () => {
    serveAdmin();
    const res = await call("POST", "/admin/products", {
      productNo: "bad no!",
      listing: content({ title: " ", originalPrice: "0" }),
    });
    expect(res).toMatchObject({ status: 400, code: "COMMON_400_002" });
    expect(res.errors).toEqual(
      expect.arrayContaining([
        { field: "productNo", message: expect.any(String) },
        { field: "listing.title", message: expect.any(String) },
        { field: "listing.originalPrice", message: expect.any(String) },
      ]),
    );
  });
});

describe("revise", () => {
  it("bumps the version on a matching expectedVersion", async () => {
    serveAdmin();
    const draft = await firstOf("draft");
    const res = await call("PUT", `/admin/products/${draft.id}`, {
      listing: { ...draft.listing, title: "Renamed" },
      expectedVersion: draft.version,
    });
    expect(res).toMatchObject({
      status: 200,
      data: { version: draft.version + 1, listing: { title: "Renamed" } },
    });
  });

  it("answers a stale version with 412 CATALOG_412_002", async () => {
    serveAdmin();
    const draft = await firstOf("draft");
    expect(
      await call("PUT", `/admin/products/${draft.id}`, {
        listing: draft.listing,
        expectedVersion: draft.version + 1,
      }),
    ).toMatchObject({
      status: 412,
      code: "CATALOG_412_002",
      errors: { currentVersion: draft.version },
    });
  });

  it("refuses a frozen-field change after publication with 409 CATALOG_409_002", async () => {
    serveAdmin();
    const live = await firstOf("on_sale");
    const refused = await Promise.all(
      [{ originalPrice: "1.00" }, { stock: live.listing.stock + 1 }].map((change) =>
        call("PUT", `/admin/products/${live.id}`, {
          listing: { ...live.listing, ...change },
          expectedVersion: live.version,
        }),
      ),
    );
    for (const reply of refused) {
      expect(reply).toMatchObject({ status: 409, code: "CATALOG_409_002" });
    }
    // The same price written differently is not a change.
    const same = await call("PUT", `/admin/products/${live.id}`, {
      listing: {
        ...live.listing,
        title: "Still on sale",
        originalPrice: live.listing.originalPrice.replace(/0$/, ""),
      },
      expectedVersion: live.version,
    });
    expect(same.status).toBe(200);
  });

  it("forces the next write to conflict when a conflict is armed", async () => {
    const store = serveAdmin();
    const draft = await firstOf("draft");
    store.conflictOnNextWrite();
    expect(
      await call("PUT", `/admin/products/${draft.id}`, {
        listing: draft.listing,
        expectedVersion: draft.version,
      }),
    ).toMatchObject({ status: 412, errors: { currentVersion: draft.version + 1 } });
    const fresh = await call("GET", `/admin/products/${draft.id}`);
    expect(fresh.data.version).toBe(draft.version + 1);
  });
});

describe("status", () => {
  it("publishes a draft with stock, then takes it off and back on sale", async () => {
    serveAdmin();
    const draft = await firstOf("draft");
    const move = (status: string, expectedVersion: number) =>
      call("PUT", `/admin/products/${draft.id}/status`, { status, expectedVersion });
    const v = draft.version;
    expect(await move("on_sale", v)).toMatchObject({
      status: 200,
      data: { status: "on_sale", version: v + 1 },
    });
    expect(await move("off_sale", v + 1)).toMatchObject({
      status: 200,
      data: { status: "off_sale", version: v + 2 },
    });
    expect(await move("on_sale", v + 2)).toMatchObject({
      status: 200,
      data: { status: "on_sale", version: v + 3 },
    });
    // A stale status change names the version the store holds now (#286).
    expect(await move("off_sale", v)).toMatchObject({
      status: 412,
      code: "CATALOG_412_002",
      errors: { currentVersion: v + 3 },
    });
  });

  it("refuses to publish without stock and any move back to draft", async () => {
    serveAdmin();
    const created = await call("POST", "/admin/products", {
      productNo: "BB-200002",
      listing: content({ stock: 0 }),
    });
    expect(
      await call("PUT", `/admin/products/${created.data.id}/status`, {
        status: "on_sale",
        expectedVersion: 0,
      }),
    ).toMatchObject({ status: 409, code: "CATALOG_409_002" });
    expect(
      await call("PUT", `/admin/products/${created.data.id}/status`, {
        status: "off_sale",
        expectedVersion: 0,
      }),
    ).toMatchObject({ status: 409, code: "CATALOG_409_002" });
    const live = await firstOf("on_sale");
    expect(
      await call("PUT", `/admin/products/${live.id}/status`, {
        status: "draft",
        expectedVersion: live.version,
      }),
    ).toMatchObject({ status: 409 });
  });
});

describe("delete", () => {
  it("soft-deletes a draft; a repeat is 404", async () => {
    serveAdmin();
    const draft = await firstOf("draft");
    const path = `/admin/products/${draft.id}?expectedVersion=${draft.version}`;
    expect(await call("DELETE", path)).toMatchObject({ status: 200, code: OK_CODE });
    expect(await call("DELETE", path)).toMatchObject({ status: 404, code: "CATALOG_404_001" });
  });

  it("refuses a non-draft with 409, a stale version with 412 and a missing version with 400", async () => {
    serveAdmin();
    const live = await firstOf("on_sale");
    expect(
      await call("DELETE", `/admin/products/${live.id}?expectedVersion=${live.version}`),
    ).toMatchObject({ status: 409, code: "CATALOG_409_002" });
    const draft = await firstOf("draft");
    expect(
      await call("DELETE", `/admin/products/${draft.id}?expectedVersion=${draft.version + 3}`),
    ).toMatchObject({
      status: 412,
      code: "CATALOG_412_002",
      errors: { currentVersion: draft.version },
    });
    expect(await call("DELETE", `/admin/products/${draft.id}`)).toMatchObject({
      status: 400,
      code: "COMMON_400_001",
    });
  });

  it("reads a draft with history as not deletable and refuses it with 409 CATALOG_409_004", async () => {
    const store = serveAdmin();
    const draft = await firstOf("draft");
    store.addHistory(draft.id, { quotes: 2 });
    expect((await call("GET", `/admin/products/${draft.id}`)).data).toMatchObject({
      references: { deals: 0, quotes: 2, policies: 0 },
      deletable: false,
    });
    expect(
      await call("DELETE", `/admin/products/${draft.id}?expectedVersion=${draft.version}`),
    ).toMatchObject({ status: 409, code: "CATALOG_409_004" });
  });

  it("counts every policy revision as a reference", async () => {
    const store = serveAdmin();
    const listing = await unruled(store);
    await call("PUT", `/products/${listing.id}/team-price-policy`, {
      amount: "1.00",
      currency: "sgd",
      validFrom: future(1),
      validUntil: future(2),
      expectedVersion: 0,
    });
    expect((await call("GET", `/admin/products/${listing.id}`)).data.references).toEqual({
      deals: 0,
      quotes: 0,
      policies: 1,
    });
  });
});

describe("team price policy", () => {
  it("answers 409 CATALOG_409_001 when a listing has no rule", async () => {
    serveAdmin();
    const draft = await firstOf("draft");
    expect(await call("GET", `/products/${draft.id}/team-price-policy`)).toMatchObject({
      status: 409,
      code: "CATALOG_409_001",
    });
  });

  it("creates revision 1, then refuses a stale revision with 412 CATALOG_412_001", async () => {
    const store = serveAdmin(createAdminStore(adminSeed()));
    const live = await unruled(store);
    const rule = { amount: "1.00", currency: "sgd", validFrom: future(0), validUntil: future(7) };
    const path = `/products/${live.id}/team-price-policy`;
    expect(await call("PUT", path, { ...rule, expectedVersion: 0 })).toMatchObject({
      status: 200,
      data: { productId: live.id, amount: "1.00", version: 1 },
    });
    expect(await call("PUT", path, { ...rule, expectedVersion: 0 })).toMatchObject({
      status: 412,
      code: "CATALOG_412_001",
      errors: { currentVersion: 1 },
    });
    expect(await call("GET", path)).toMatchObject({ status: 200, data: { version: 1 } });
  });

  it("keeps an armed conflict for a first rule, then accepts the retry", async () => {
    const store = serveAdmin(createAdminStore(adminSeed()));
    const live = await unruled(store);
    const rule = { amount: "1.00", currency: "sgd", validFrom: future(0), validUntil: future(7) };
    const path = `/products/${live.id}/team-price-policy`;
    store.conflictOnNextWrite();
    expect(await call("PUT", path, { ...rule, expectedVersion: 0 })).toMatchObject({
      status: 412,
      code: "CATALOG_412_001",
    });
    expect(await call("PUT", path, { ...rule, expectedVersion: 0 })).toMatchObject({
      status: 200,
      data: { version: 1 },
    });
  });

  it("refuses a rule on a listing that is not on sale with 409 CATALOG_409_001", async () => {
    serveAdmin();
    const draft = await firstOf("draft");
    expect(
      await call("PUT", `/products/${draft.id}/team-price-policy`, {
        amount: "1.00",
        currency: "sgd",
        validFrom: future(0),
        validUntil: future(7),
        expectedVersion: 0,
      }),
    ).toMatchObject({ status: 409, code: "CATALOG_409_001" });
  });

  it("refuses an amount not below the price, or a bad window, with 400 CATALOG_400_001", async () => {
    const store = serveAdmin();
    const live = await unruled(store);
    const path = `/products/${live.id}/team-price-policy`;
    const rule = {
      currency: "sgd",
      validFrom: future(0),
      validUntil: future(7),
      expectedVersion: 0,
    };
    const cases: [Record<string, unknown>, string, string][] = [
      [
        { ...rule, amount: live.listing.originalPrice },
        "amount",
        "Must be strictly below the solo price",
      ],
      [{ ...rule, amount: "0.00" }, "amount", "Must be positive and at most 9999999999999999.99"],
      [{ ...rule, amount: "0.50", validFrom: future(8) }, "validUntil", "Must be after the start"],
      [{ ...rule, amount: "0.50", validFrom: "" }, "validFrom", "Start time is required"],
      [
        { ...rule, amount: "0.50", validFrom: future(-9), validUntil: future(-1) },
        "validUntil",
        "Must be in the future",
      ],
      [{ ...rule, amount: "0.50", currency: "usd" }, "currency", "Must be SGD (sgd)"],
    ];
    const replies = await Promise.all(cases.map(([bad]) => call("PUT", path, bad)));
    // Field-specific domain 400s name the request path since #323 (BA-6).
    for (const [i, [, field, message]] of cases.entries()) {
      expect(replies[i]).toMatchObject({
        status: 400,
        code: "CATALOG_400_001",
        errors: [{ field, message }],
      });
    }
  });

  it("refuses a malformed amount through bean validation, before the rule's own checks", async () => {
    const store = serveAdmin();
    const live = await unruled(store);
    expect(
      await call("PUT", `/products/${live.id}/team-price-policy`, {
        amount: "1.234",
        currency: "sgd",
        validFrom: future(0),
        validUntil: future(7),
        expectedVersion: 0,
      }),
    ).toMatchObject({ status: 400, code: "COMMON_400_002", errors: [{ field: "amount" }] });
  });

  it("checks the version before whether the listing is on sale, as the backend does", async () => {
    serveAdmin();
    const draft = await firstOf("draft");
    expect(
      await call("PUT", `/products/${draft.id}/team-price-policy`, {
        amount: "1.00",
        currency: "sgd",
        validFrom: future(0),
        validUntil: future(7),
        expectedVersion: 2,
      }),
    ).toMatchObject({ status: 412, code: "CATALOG_412_001", errors: { currentVersion: 0 } });
  });
});

const terms = (id: string) => `/admin/products/${id}/team-terms`;

describe("team terms", () => {
  it("sets both terms on a draft, bumps the listing version and reads them back (#242)", async () => {
    serveAdmin();
    const draft = await firstOf("draft");
    expect(
      await call("PUT", terms(draft.id), {
        expectedVersion: draft.version,
        targetSize: 4,
        lifetimeSeconds: 2400,
      }),
    ).toMatchObject({
      status: 200,
      code: OK_CODE,
      data: {
        productId: draft.id,
        version: draft.version + 1,
        teamTerms: { targetSize: 4, lifetimeSeconds: 2400, configured: true },
      },
    });
    const one = await call("GET", `/admin/products/${draft.id}`);
    expect(one.data).toMatchObject({
      version: draft.version + 1,
      teamTerms: { targetSize: 4, lifetimeSeconds: 2400, configured: true },
    });
  });

  it("refuses out-of-range terms through bean validation, before the version", async () => {
    serveAdmin();
    const listing = await firstOf("on_sale");
    expect(
      await call("PUT", terms(listing.id), {
        expectedVersion: 999,
        targetSize: 1,
        lifetimeSeconds: 86_401,
      }),
    ).toMatchObject({
      status: 400,
      code: "COMMON_400_002",
      errors: [{ field: "targetSize" }, { field: "lifetimeSeconds" }],
    });
  });

  it("answers a stale version with 412 CATALOG_412_002 and the server's version", async () => {
    const store = serveAdmin();
    const listing = await firstOf("on_sale");
    expect(
      await call("PUT", terms(listing.id), {
        expectedVersion: listing.version - 1,
        targetSize: 5,
        lifetimeSeconds: 3600,
      }),
    ).toMatchObject({
      status: 412,
      code: "CATALOG_412_002",
      errors: { currentVersion: listing.version },
    });
    store.conflictOnNextWrite();
    expect(
      await call("PUT", terms(listing.id), {
        expectedVersion: listing.version,
        targetSize: 5,
        lifetimeSeconds: 3600,
      }),
    ).toMatchObject({ status: 412, errors: { currentVersion: listing.version + 1 } });
  });

  it("answers 404 for an unknown listing", async () => {
    serveAdmin();
    expect(
      await call("PUT", terms("0192f0a0-0000-7000-a000-999999999999"), {
        expectedVersion: 0,
        targetSize: 5,
        lifetimeSeconds: 3600,
      }),
    ).toMatchObject({ status: 404, code: "CATALOG_404_001" });
  });

  it("refuses a team price with the terms off sale with 409 CATALOG_409_001", async () => {
    serveAdmin();
    const draft = await firstOf("draft");
    expect(
      await call("PUT", terms(draft.id), {
        expectedVersion: draft.version,
        targetSize: 5,
        lifetimeSeconds: 3600,
        teamPrice: {
          amount: "1.00",
          currency: "sgd",
          validFrom: future(0),
          validUntil: future(7),
          expectedVersion: 0,
        },
      }),
    ).toMatchObject({ status: 409, code: "CATALOG_409_001" });
  });

  it("is refused to a buyer like every admin write", async () => {
    serveNonAdmin();
    expect(
      await call("PUT", terms("0192f0a0-0000-7000-a000-000000000001"), {
        expectedVersion: 0,
        targetSize: 5,
        lifetimeSeconds: 3600,
      }),
    ).toMatchObject({ status: 403, code: "COMMON_403_001" });
  });
});
