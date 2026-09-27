import { describe, expect, it } from "vitest";

import { ApiError } from "@/api/envelope";

import {
  displayLink,
  inviteUrl,
  seatsToGo,
  shareFailure,
  shareMessage,
  sharePitch,
} from "./share-link";

const url = "https://blocbuy.app/products/p-1?deal=deal-1&ref=AbC_12-xyz";

describe("inviteUrl", () => {
  it("builds the listing link with the bloc and the ref (backend plan D4)", () => {
    expect(
      inviteUrl("https://blocbuy.app", { productId: "p-1", dealId: "deal-1", ref: "AbC_12-xyz" }),
    ).toBe(url);
  });
});

describe("displayLink", () => {
  it("drops the scheme for the link field", () => {
    expect(displayLink(url)).toBe("blocbuy.app/products/p-1?deal=deal-1&ref=AbC_12-xyz");
  });
});

describe("seatsToGo", () => {
  it("says one seat or n seats", () => {
    expect(seatsToGo(1)).toBe("One seat to go.");
    expect(seatsToGo(3)).toBe("3 seats to go.");
  });
});

describe("shareMessage", () => {
  const base = {
    title: "Kopi powder 500 g",
    groupPrice: 9.9,
    individualPrice: 12.8,
    currency: "sgd",
    seatsLeft: 3,
    // Fri 18 Sep 2026, 2.40 pm SGT
    closesMs: Date.parse("2026-09-18T06:40:00Z"),
    url,
  };

  it("follows the §4.4 suggested message", () => {
    expect(shareMessage(base)).toBe(
      `Getting Kopi powder 500 g at S$9.90 instead of S$12.80 — need 3 more people by Fri, 2.40 pm. ${url}`,
    );
  });

  it("says one more person, and leaves out what it cannot state", () => {
    expect(
      shareMessage({
        ...base,
        seatsLeft: 1,
        individualPrice: undefined,
        closesMs: null,
      }),
    ).toBe(`Getting Kopi powder 500 g at S$9.90 — need 1 more person. ${url}`);
    expect(shareMessage({ ...base, groupPrice: undefined, closesMs: null })).toBe(
      `Getting Kopi powder 500 g — need 3 more people. ${url}`,
    );
  });

  it("leaves the link out of the pitch the native share sheet gets", () => {
    expect(sharePitch(base)).toBe(
      "Getting Kopi powder 500 g at S$9.90 instead of S$12.80 — need 3 more people by Fri, 2.40 pm.",
    );
  });
});

const api = (status: number, code: string) => new ApiError({ code, message: "x", status });

describe("shareFailure", () => {
  it("does not offer a retry for a buyer who is not in the bloc", () => {
    expect(shareFailure(api(404, "GROUPBUY_404_001"))).toEqual({
      message: "Only people in this bloc can share its invite link.",
      retry: false,
    });
  });

  it("explains a bloc that stopped taking members", () => {
    expect(shareFailure(api(409, "GROUPBUY_409_002")).retry).toBe(false);
  });

  it("asks a signed-out buyer to sign in again", () => {
    expect(shareFailure(api(401, "AUTH_401")).message).toMatch(/Sign in again/);
  });

  it("offers a retry for anything else, saying nothing was charged", () => {
    expect(shareFailure(api(500, "COMMON_500_000"))).toEqual({
      message: "We couldn’t make your invite link. Nothing has been charged.",
      retry: true,
    });
    expect(shareFailure(new TypeError("offline")).retry).toBe(true);
  });
});
