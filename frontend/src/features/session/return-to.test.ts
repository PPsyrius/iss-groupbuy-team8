import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { GOOGLE_LOGIN_URL, rememberReturnTo, startGoogleLogin, takeReturnTo } from "./return-to";

describe("return-to", () => {
  beforeEach(() => sessionStorage.clear());
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

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

  it.each(["getItem", "removeItem"] as const)("falls back when storage %s is blocked", (method) => {
    rememberReturnTo("/products/1");
    const storage = sessionStorage;
    vi.stubGlobal("sessionStorage", {
      getItem: storage.getItem.bind(storage),
      removeItem: storage.removeItem.bind(storage),
      [method]: () => {
        throw new DOMException("Storage blocked", "SecurityError");
      },
    });
    expect(takeReturnTo()).toBe("/account");
  });

  it("still starts login when saving the return path is blocked", () => {
    vi.stubGlobal("sessionStorage", {
      setItem: () => {
        throw new DOMException("Storage blocked", "SecurityError");
      },
    });
    const navigate = vi.fn<(url: string) => void>();
    startGoogleLogin("/products/1", navigate);
    expect(navigate).toHaveBeenCalledExactlyOnceWith(GOOGLE_LOGIN_URL);
  });

  it("uses a full-page navigation by default", () => {
    const assign = vi.spyOn(window.location, "assign").mockImplementation(() => {});
    startGoogleLogin("/products/1");
    expect(assign).toHaveBeenCalledExactlyOnceWith(GOOGLE_LOGIN_URL);
    expect(takeReturnTo()).toBe("/products/1");
  });
});
