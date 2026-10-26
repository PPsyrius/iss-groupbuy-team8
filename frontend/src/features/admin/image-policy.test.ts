import { afterEach, describe, expect, it, vi } from "vitest";

import { isApprovedImageUrl } from "./image-policy";

describe("image-policy", () => {
  afterEach(() => vi.unstubAllGlobals());

  it.each([
    ["https://store.example/a.jpg", true],
    ["http://store.example/a.jpg", false],
    ["https://other.example/a.jpg", false],
    ["https://store.example:444/a.jpg", false],
    ["https://sub.store.example/a.jpg", false],
    ["https://user:password@store.example/a.jpg", false],
  ] as const)("applies 'self' to the exact HTTPS page origin: %s", (url, approved) => {
    vi.stubGlobal("location", { origin: "https://store.example" });
    expect(isApprovedImageUrl(url)).toBe(approved);
  });

  it.each([
    "https://dcsjrkmummxi7.cloudfront.net/a.jpg",
    "https://lh3.googleusercontent.com/avatar.jpg",
    "https://dcsjrkmummxi7.cloudfront.net:443/a.jpg",
  ])("accepts an exact CSP origin: %s", (url) => {
    expect(isApprovedImageUrl(url)).toBe(true);
  });

  it.each([
    "https://other.example/a.jpg",
    "https://dcsjrkmummxi7.cloudfront.net.evil.test/a.jpg",
    "https://dcsjrkmummxi7.cloudfront.net:444/a.jpg",
    "https://user:password@dcsjrkmummxi7.cloudfront.net/a.jpg",
    "https://dcsjrkmummxi7.cloudfront.net@evil.test/a.jpg",
    "http://dcsjrkmummxi7.cloudfront.net/a.jpg",
    "data:image/svg+xml,test",
    "/relative.jpg",
    "invalid",
  ])("rejects unsupported sources and URL tricks: %s", (url) => {
    expect(isApprovedImageUrl(url)).toBe(false);
  });
});
