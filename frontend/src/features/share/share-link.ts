import { ApiError } from "@/api/envelope";
import { formatDeadlineShort } from "@/features/catalog/deal-time";
import { formatPrice } from "@/features/catalog/Price";

// The share block's pure parts (UC7, #30): the link the frontend builds from the minted invite
// (backend plan D4), the shortened text shown in the link field, the §4.4 suggested message and
// the copy for a refused mint.

export function inviteUrl(
  origin: string,
  invite: { productId: string; dealId: string; ref: string },
): string {
  const search = new URLSearchParams({ deal: invite.dealId, ref: invite.ref });
  return `${origin}/products/${encodeURIComponent(invite.productId)}?${search.toString()}`;
}

// The artboard's field shows the link without its scheme ("blocbuy.app/b/2094-m2p").
export function displayLink(url: string): string {
  return url.replace(/^https?:\/\//, "");
}

export function seatsToGo(left: number): string {
  return left === 1 ? "One seat to go." : `${left} seats to go.`;
}

// §4.4 starter view: "Getting kopi powder at S$9.90 instead of S$12.80 — need 3 more people by
// Friday 2.40 pm. <link>". Rebuilt from the live seats and deadline on every render, so a
// re-share never pastes a stale claim. The title keeps its case ("Thai jasmine rice").
export function shareMessage(args: ShareFacts & { url: string }): string {
  return `${sharePitch(args)} ${args.url}`;
}

export interface ShareFacts {
  title: string;
  groupPrice: number | undefined;
  individualPrice: number | undefined;
  currency: string;
  seatsLeft: number;
  closesMs: number | null;
}

// The suggested message without its link, for the Web Share API: the link goes in the share's
// own `url` field, which some targets read instead of the text, and a link in both would show
// twice.
export function sharePitch(facts: ShareFacts): string {
  const { title, groupPrice, individualPrice, currency, seatsLeft, closesMs } = facts;
  const price = priceClause(groupPrice, individualPrice, currency);
  const people = seatsLeft === 1 ? "1 more person" : `${seatsLeft} more people`;
  const by = closesMs === null ? "" : ` by ${formatDeadlineShort(closesMs)}`;
  return `Getting ${title}${price} — need ${people}${by}.`;
}

// " at S$9.90 instead of S$12.80": the saving is claimed only when buying alone costs more.
function priceClause(
  groupPrice: number | undefined,
  individualPrice: number | undefined,
  currency: string,
): string {
  if (groupPrice === undefined) return "";
  const at = ` at ${formatPrice(groupPrice, currency)}`;
  if (individualPrice === undefined || individualPrice <= groupPrice) return at;
  return `${at} instead of ${formatPrice(individualPrice, currency)}`;
}

// POST /group-deals/{dealId}/invites refusals, in the page's "nothing was charged" tone. 404 is
// "no such bloc, or you are not in it"; 409 is a bloc that no longer takes members.
export function shareFailure(error: unknown): { message: string; retry: boolean } {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return { message: "Sign in again to get your invite link.", retry: false };
    }
    if (error.status === 404 || error.code === "GROUPBUY_404_001") {
      return { message: "Only people in this bloc can share its invite link.", retry: false };
    }
    if (error.status === 409 || error.code === "GROUPBUY_409_002") {
      return {
        message: "This bloc is no longer taking members, so there is no link to share.",
        retry: false,
      };
    }
  }
  return {
    message: "We couldn’t make your invite link. Nothing has been charged.",
    retry: true,
  };
}
