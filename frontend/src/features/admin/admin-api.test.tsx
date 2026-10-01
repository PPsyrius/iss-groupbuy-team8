import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { installAuthInterceptor } from "@/api/client";
import { ApiError, OK_CODE } from "@/api/envelope";
import { server } from "@/test/msw";

import {
  adminListingOptions,
  adminListingsOptions,
  adminPolicyOptions,
  adminServerVersionOptions,
  forgetListingPages,
  useChangeStatus,
  useConfigurePolicy,
  useCreateListing,
  useDeleteListing,
  useReviseListing,
} from "./admin-api";

// Non-2xx envelopes become `ApiError` only through the production interceptor.
installAuthInterceptor(new QueryClient());

const productId = "0192f0a0-0000-7000-8000-0000000000a1";
const ok = (data?: unknown) =>
  HttpResponse.json(
    data === undefined ? { code: OK_CODE, message: "ok" } : { code: OK_CODE, message: "ok", data },
  );
const listing = { title: "Pour-over kettle", originalPrice: "89", currency: "sgd", stock: 12 };
const row = { id: productId, productNo: "BB-100481", listing, status: "draft", version: 4 };

function hook<T>(use: () => T) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return { qc, ...renderHook(use, { wrapper }) };
}

describe("admin reads", () => {
  it("unwraps a listings page and sends page, size and status", async () => {
    let url: URL | undefined;
    server.use(
      http.get("*/api/v1/admin/products", ({ request }) => {
        url = new URL(request.url);
        return ok({ items: [row], total: 1, page: 1, size: 1 });
      }),
    );
    const qc = new QueryClient();
    const options = adminListingsOptions({ page: 1, size: 1, status: "draft" });
    const page = options.select?.(await qc.fetchQuery(options));
    expect(page).toEqual({ items: [row], total: 1, page: 1, size: 1 });
    expect(url?.searchParams.get("status")).toBe("draft");
    expect(url?.searchParams.get("size")).toBe("1");
  });

  it("unwraps one listing and its policy", async () => {
    const policy = { productId, amount: "46.80", currency: "sgd", version: 2 };
    server.use(
      http.get("*/api/v1/admin/products/:id", () => ok(row)),
      http.get("*/api/v1/products/:id/team-price-policy", () => ok(policy)),
    );
    const qc = new QueryClient();
    const one = adminListingOptions(productId);
    const rule = adminPolicyOptions(productId);
    expect(one.select?.(await qc.fetchQuery(one))).toEqual(row);
    expect(rule.select?.(await qc.fetchQuery(rule))).toEqual(policy);
  });

  it("reads the server's version under its own key, away from the editor's entry", async () => {
    server.use(http.get("*/api/v1/admin/products/:id", () => ok({ ...row, version: 5 })));
    const qc = new QueryClient();
    const peek = adminServerVersionOptions(productId);
    expect(peek.select?.(await qc.fetchQuery(peek))).toBe(5);
    expect(qc.getQueryData(adminListingOptions(productId).queryKey)).toBeUndefined();
  });

  it("rejects a 403 with an ApiError the guard can read", async () => {
    server.use(
      http.get("*/api/v1/admin/products", () =>
        HttpResponse.json({ code: "COMMON_403_001", message: "forbidden" }, { status: 403 }),
      ),
    );
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    await expect(qc.fetchQuery(adminListingsOptions({ page: 1, size: 1 }))).rejects.toMatchObject({
      status: 403,
    });
  });
});

describe("admin writes", () => {
  it("create posts the draft and resolves to the unwrapped listing", async () => {
    let body: unknown;
    server.use(
      http.post("*/api/v1/admin/products", async ({ request }) => {
        body = await request.json();
        return ok(row);
      }),
    );
    const { result } = hook(() => useCreateListing());
    const created = await result.current.mutateAsync({ productNo: "BB-100481", listing });
    expect(created).toEqual(row);
    expect(body).toEqual({ productNo: "BB-100481", listing });
  });

  it("revise and changeStatus send the loaded version", async () => {
    const bodies: unknown[] = [];
    server.use(
      http.put("*/api/v1/admin/products/:id", async ({ request }) => {
        bodies.push(await request.json());
        return ok({ ...row, version: 5 });
      }),
      http.put("*/api/v1/admin/products/:id/status", async ({ request }) => {
        bodies.push(await request.json());
        return ok({ ...row, status: "on_sale", version: 6 });
      }),
    );
    const revise = hook(() => useReviseListing()).result;
    const status = hook(() => useChangeStatus()).result;
    expect(
      await revise.current.mutateAsync({ productId, listing, expectedVersion: 4 }),
    ).toMatchObject({
      version: 5,
    });
    expect(
      await status.current.mutateAsync({ productId, status: "on_sale", expectedVersion: 5 }),
    ).toMatchObject({ status: "on_sale" });
    expect(bodies).toEqual([
      { listing, expectedVersion: 4 },
      { status: "on_sale", expectedVersion: 5 },
    ]);
  });

  it("delete sends expectedVersion as a query and resolves on an empty OK envelope", async () => {
    let version: string | null = null;
    server.use(
      http.delete("*/api/v1/admin/products/:id", ({ request }) => {
        version = new URL(request.url).searchParams.get("expectedVersion");
        return ok();
      }),
    );
    const { result } = hook(() => useDeleteListing());
    await expect(
      result.current.mutateAsync({ productId, expectedVersion: 3 }),
    ).resolves.toBeUndefined();
    expect(version).toBe("3");
  });

  it("configure puts the policy and surfaces a 412 as an ApiError", async () => {
    server.use(
      http.put("*/api/v1/products/:id/team-price-policy", () =>
        HttpResponse.json({ code: "CATALOG_412_001", message: "stale" }, { status: 412 }),
      ),
    );
    const { result } = hook(() => useConfigurePolicy());
    result.current.mutate({
      productId,
      amount: "46.80",
      currency: "sgd",
      validFrom: "2026-10-01T00:00:00Z",
      validUntil: "2026-10-31T00:00:00Z",
      expectedVersion: 2,
    });
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(ApiError);
    expect(result.current.error).toMatchObject({ status: 412, code: "CATALOG_412_001" });
  });

  it("forgetListingPages refetches pages on screen and drops the rest", async () => {
    let hits = 0;
    server.use(
      http.get("*/api/v1/admin/products", () => {
        hits += 1;
        return ok({ items: [row], total: 1, page: 1, size: 20 });
      }),
    );
    const { qc, result } = hook(() => useQuery(adminListingsOptions({ page: 1, size: 20 })));
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await qc.fetchQuery(adminListingsOptions({ page: 2, size: 20 }));
    expect(hits).toBe(2);
    forgetListingPages(qc);
    await waitFor(() => expect(hits).toBe(3));
    expect(qc.getQueryData(adminListingsOptions({ page: 2, size: 20 }).queryKey)).toBeUndefined();
    expect(qc.getQueryData(adminListingsOptions({ page: 1, size: 20 }).queryKey)).toBeDefined();
  });
});
