import { beforeEach, describe, expect, it, vi } from "vitest";

import { GOOGLE_LOGIN_URL, rememberReturnTo, startGoogleLogin, takeReturnTo } from "./return-to";

describe("return-to", () => {
  beforeEach(() => sessionStorage.clear());

  it("hands the remembered path back once", () => {
    rememberReturnTo("/products/1");
    expect(takeReturnTo()).toBe("/products/1");
    expect(takeReturnTo()).toBe("/account");
  });

  it("never hands back an off-origin value", () => {
    sessionStorage.setItem("blocbuy.returnTo", "https://evil.example/");
    expect(takeReturnTo()).toBe("/account");
  });

  it("remembers the path and navigates to the backend login start", () => {
    const navigate = vi.fn<(url: string) => void>();
    startGoogleLogin("/products/1", navigate);
    expect(takeReturnTo()).toBe("/products/1");
    expect(navigate).toHaveBeenCalledWith(GOOGLE_LOGIN_URL);
    expect(GOOGLE_LOGIN_URL).toBe("/api/v1/auth/oauth2/google");
  });
});
