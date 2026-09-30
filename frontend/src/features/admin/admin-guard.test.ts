import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { ApiError, OK_CODE } from "@/api/envelope";
import { queryClient } from "@/api/query-client";
import { adminHandlers, createAdminStore } from "@/test/admin-msw";
import { server } from "@/test/msw";

import { AdminAccessDenied, requireAdmin } from "./admin-guard";

// The app's own QueryClient: importing it installs the interceptor that turns a non-2xx
// envelope into an `ApiError`, which is what the guard inspects.
beforeEach(() => {
  queryClient.clear();
});

describe("requireAdmin", () => {
  it("lets an admin through", async () => {
    server.use(...adminHandlers(createAdminStore(), { role: "admin" }));
    await expect(requireAdmin(queryClient)).resolves.toBeUndefined();
  });

  it("turns the probe's 403 into AdminAccessDenied", async () => {
    server.use(...adminHandlers(createAdminStore(), { role: "buyer" }));
    await expect(requireAdmin(queryClient)).rejects.toBeInstanceOf(AdminAccessDenied);
  });

  it("rethrows any other failure unchanged", async () => {
    server.use(
      http.get("*/api/v1/admin/products", () =>
        HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 }),
      ),
    );
    const error: unknown = await requireAdmin(queryClient).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).not.toBeInstanceOf(AdminAccessDenied);
  });

  it("reuses a probe answered within the last 30 seconds", async () => {
    let calls = 0;
    server.use(
      http.get("*/api/v1/admin/products", () => {
        calls += 1;
        return HttpResponse.json({
          code: OK_CODE,
          message: "ok",
          data: { items: [], total: 0, page: 1, size: 1, nextCursor: null },
        });
      }),
    );
    await requireAdmin(queryClient);
    await requireAdmin(queryClient);
    expect(calls).toBe(1);
  });
});
