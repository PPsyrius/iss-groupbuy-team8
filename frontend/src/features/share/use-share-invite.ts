import { queryOptions, useQuery } from "@tanstack/react-query";
import { useState } from "react";

import { unwrapResult } from "@/api/envelope";
import { createShareInvite } from "@/api/generated/sdk.gen";

// `POST /group-deals/{dealId}/invites` mints one invite per bloc and member and returns the same
// one on every later call (#198), so it is read like a query: one call when the share block
// mounts, cached for the page's life. The key carries the buyer, so a different account signing
// in on the same tab never reuses another member's ref. A refusal is final (not a member, bloc
// closed), so it is not retried.
function shareInviteQuery(userId: string, dealId: string) {
  return queryOptions({
    queryKey: ["shareInvite", userId, dealId] as const,
    queryFn: async ({ signal }) =>
      (
        await createShareInvite({
          path: { dealId },
          headers: { "X-BlocBuy-Request": "invite" },
          signal,
          throwOnError: true,
        })
      ).data,
    select: (r) => unwrapResult(r, { allowVoid: false }),
    staleTime: Number.POSITIVE_INFINITY,
    retry: false,
  });
}

export function useShareInvite(userId: string, dealId: string) {
  return useQuery(shareInviteQuery(userId, dealId));
}

export type CopyState = "idle" | "copied" | "manual";

// Copy with the async Clipboard API; where it is missing or refused (older WebViews, an insecure
// origin, a denied permission) the caller selects the link so it can be copied by hand. Share
// uses the Web Share API when the browser has one, with the link in `url` (the caller's text
// leaves it out); a dismissed share sheet is not an error, and any other failure falls back to
// copying.
export function useShareActions(url: string | undefined, select: () => void) {
  const [copyState, setCopyState] = useState<CopyState>("idle");
  const canShare = typeof navigator !== "undefined" && "share" in navigator;

  async function copy() {
    if (!url) return;
    try {
      await navigator.clipboard.writeText(url);
      setCopyState("copied");
    } catch {
      select();
      setCopyState("manual");
    }
  }

  async function share(data: { title: string; text: string }) {
    if (!url) return;
    try {
      await navigator.share({ title: data.title, text: data.text, url });
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      await copy();
    }
  }

  return { copyState, canShare, copy, share };
}
