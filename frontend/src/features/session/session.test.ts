import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";

import { ApiError } from "@/api/envelope";
import { queryClient } from "@/api/query-client";
import { server } from "@/test/msw";

import { sessionFromQuery, toSessionState } from "./session";

const me = (status: number, body: Record<string, unknown>) =>
  server.use(http.get("*/api/v1/auth/me", () => HttpResponse.json(body, { status })));

describe("toSessionState", () => {
  it("maps a missing or empty payload to anonymous", () => {
    expect(toSessionState(undefined)).toEqual({ status: "anonymous" });
    expect(toSessionState({})).toEqual({ status: "anonymous" });
  });

  it("maps a user to authenticated, nulling the optional profile fields", () => {
    expect(toSessionState({ userId: "u1" })).toEqual({
      status: "authenticated",
      userId: "u1",
      nickname: null,
      avatarUrl: null,
      roles: [],
    });
    expect(toSessionState({ userId: "u1", nickname: "Ada", avatarUrl: "https://x/a.png" })).toEqual(
      {
        status: "authenticated",
        userId: "u1",
        nickname: "Ada",
        avatarUrl: "https://x/a.png",
        roles: [],
      },
    );
  });

  it("carries the role codes from /auth/me (#282)", () => {
    expect(toSessionState({ userId: "u1", roles: ["buyer", "admin"] })).toMatchObject({
      roles: ["buyer", "admin"],
    });
  });
});

describe("sessionFromQuery", () => {
  beforeEach(() => queryClient.clear());

  it("is anonymous when /auth/me answers 401", async () => {
    me(401, { code: "AUTH_401", message: "unauthenticated" });
    await expect(sessionFromQuery(queryClient)).resolves.toEqual({ status: "anonymous" });
  });

  it("is authenticated when /auth/me answers 200", async () => {
    me(200, {
      code: "COMMON_200_000",
      message: "ok",
      data: { userId: "u1", nickname: "Ada", avatarUrl: null, roles: ["buyer"] },
    });
    await expect(sessionFromQuery(queryClient)).resolves.toEqual({
      status: "authenticated",
      userId: "u1",
      nickname: "Ada",
      avatarUrl: null,
      roles: ["buyer"],
    });
  });

  it("rethrows other failures instead of pretending to be anonymous", async () => {
    me(500, { code: "COMMON_500_000", message: "boom" });
    await expect(sessionFromQuery(queryClient)).rejects.toBeInstanceOf(ApiError);
  });
});
