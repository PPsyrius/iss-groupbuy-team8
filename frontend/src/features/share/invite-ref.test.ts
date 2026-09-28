import { beforeEach, describe, expect, it } from "vitest";

import { discardInvite, parkedRefFor, parkInvite, readParkedInvite } from "./invite-ref";

const invite = { productId: "p-1", dealId: "deal-1", ref: "AbC_12-xyz" };

beforeEach(() => sessionStorage.clear());

describe("invite parking", () => {
  it("reads back what was parked", () => {
    parkInvite(invite);
    expect(readParkedInvite()).toEqual(invite);
  });

  it("keeps only the newest invite (last touch)", () => {
    parkInvite(invite);
    parkInvite({ ...invite, dealId: "deal-2", ref: "newer" });
    expect(readParkedInvite()).toEqual({ ...invite, dealId: "deal-2", ref: "newer" });
  });

  it("gives the ref only for the bloc it was minted for", () => {
    parkInvite(invite);
    expect(parkedRefFor("deal-1")).toBe("AbC_12-xyz");
    expect(parkedRefFor("deal-2")).toBeUndefined();
  });

  it("empties the slot when a newer link turns out invalid", () => {
    parkInvite(invite);
    discardInvite();
    expect(readParkedInvite()).toBeNull();
  });

  it("never returns a tampered or malformed value", () => {
    sessionStorage.setItem("blocbuy.invite", JSON.stringify({ ...invite, ref: "bad ref!" }));
    expect(readParkedInvite()).toBeNull();
    sessionStorage.setItem("blocbuy.invite", JSON.stringify({ ...invite, dealId: 7 }));
    expect(readParkedInvite()).toBeNull();
    sessionStorage.setItem("blocbuy.invite", "{not json");
    expect(readParkedInvite()).toBeNull();
    sessionStorage.setItem("blocbuy.invite", "null");
    expect(parkedRefFor("deal-1")).toBeUndefined();
  });
});
