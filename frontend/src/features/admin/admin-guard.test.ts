import { describe, expect, it } from "vitest";

import type { SessionState } from "@/features/session/session";

import { AdminAccessDenied, requireAdmin } from "./admin-guard";

const signedIn = (roles: string[]): SessionState => ({
  status: "authenticated",
  userId: "u1",
  nickname: "Ada",
  avatarUrl: null,
  roles,
});

// The guard decides from the session alone (#282), so none of these cases touch the network:
// the Vitest server fails any request no test handler answers.
describe("requireAdmin", () => {
  it("lets a session with the admin role through", () => {
    expect(() => requireAdmin(signedIn(["buyer", "admin"]))).not.toThrow();
  });

  it("denies a session without the admin role", () => {
    expect(() => requireAdmin(signedIn(["buyer"]))).toThrow(AdminAccessDenied);
    expect(() => requireAdmin(signedIn([]))).toThrow(AdminAccessDenied);
  });

  it("denies an anonymous session", () => {
    expect(() => requireAdmin({ status: "anonymous" })).toThrow(AdminAccessDenied);
  });
});
