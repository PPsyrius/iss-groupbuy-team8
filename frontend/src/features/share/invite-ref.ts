import { SHARE_DEAL_PATTERN, SHARE_REF_PATTERN } from "@/features/catalog/products-search";

// The invite a buyer arrived with (UC7, #30). The listing route parks it here and drops it from
// the URL, so it survives the Google sign-in hop (sessionStorage is per tab and outlives the two
// cross-origin redirects, as `return-to.ts` relies on) and the quote sheet. The newest link wins
// (backend plan D9, last touch per session). `use-checkout` sends the ref on a JOIN of the same
// bloc and forgets it once the server has frozen it on the intent.
const KEY = "blocbuy.invite";

export interface ParkedInvite {
  productId: string;
  dealId: string;
  ref: string;
}

export function parkInvite(invite: ParkedInvite): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(invite));
  } catch {
    // storage blocked (private mode, quota): the join goes ahead unattributed
  }
}

// Re-checked on every read: storage is writable by anything on the origin, and an invalid ref
// must never reach the intent.
export function readParkedInvite(): ParkedInvite | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) return null;
    const value: unknown = JSON.parse(raw);
    if (
      typeof value === "object" &&
      value !== null &&
      "productId" in value &&
      "dealId" in value &&
      "ref" in value &&
      typeof value.productId === "string" &&
      typeof value.dealId === "string" &&
      typeof value.ref === "string" &&
      SHARE_DEAL_PATTERN.test(value.dealId) &&
      SHARE_REF_PATTERN.test(value.ref)
    ) {
      return { productId: value.productId, dealId: value.dealId, ref: value.ref };
    }
  } catch {
    // unreadable storage or a malformed value: no invite
  }
  return null;
}

// The ref to send with a JOIN of `dealId`, if the parked invite is for that bloc. The server
// ignores it on START, so it is only ever asked for on JOIN.
export function parkedRefFor(dealId: string): string | undefined {
  const invite = readParkedInvite();
  return invite?.dealId === dealId ? invite.ref : undefined;
}

export function forgetInvite(dealId: string): void {
  if (readParkedInvite()?.dealId !== dealId) return;
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // nothing to clean up if storage is blocked
  }
}
