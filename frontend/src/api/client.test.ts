import { QueryClient } from "@tanstack/react-query";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { server } from "@/test/msw";

import { installAuthInterceptor } from "./client";
import { ApiError, OK_CODE } from "./envelope";
import { meOptions, meQueryKey } from "./generated/@tanstack/react-query.gen";

// `installAuthInterceptor` registers a response interceptor on the module-level generated
// `client` (shared across the whole process), so it must be installed exactly once here rather
// than per test/per `QueryClient` — a second install would add a second interceptor that keeps
// firing (against a stale `QueryClient`) for the rest of this file's tests. `src/api/query-client.ts`
// installs its own copy at import time for the app; this file never imports that module.
const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
installAuthInterceptor(qc);

describe("installAuthInterceptor", () => {
  beforeEach(() => {
    qc.clear();
  });

  it("rejects with an ApiError on 401 and settles the me query on an anonymous envelope", async () => {
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json(
          { code: "AUTH_401", message: "unauthenticated", traceId: "t-1" },
          { status: 401 },
        ),
      ),
    );

    let caught: unknown;
    try {
      await qc.fetchQuery(meOptions());
    } catch (e) {
      caught = e;
    }

    expect(caught).toBeInstanceOf(ApiError);
    expect(caught).toMatchObject({ status: 401, code: "AUTH_401", traceId: "t-1" });
    expect(qc.getQueryData(meQueryKey())).toEqual({ code: OK_CODE, message: "anonymous" });
    expect(qc.getQueryState(meQueryKey())?.status).not.toBe("pending");
  });

  it("rejects with an ApiError on a non-JSON 403 and leaves the me query untouched", async () => {
    server.use(http.get("*/api/v1/auth/me", () => HttpResponse.text("forbidden", { status: 403 })));

    let caught: unknown;
    try {
      await qc.fetchQuery(meOptions());
    } catch (e) {
      caught = e;
    }

    expect(caught).toBeInstanceOf(ApiError);
    expect(caught).toMatchObject({ status: 403, code: "HTTP_403" });
    expect(qc.getQueryData(meQueryKey())).toBeUndefined();
  });
});
