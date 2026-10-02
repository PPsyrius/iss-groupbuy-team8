import { queryOptions, useQuery } from "@tanstack/react-query";

import { unwrapResult } from "@/api/envelope";
import { resolveInviteOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { InviteResponse } from "@/api/generated/types.gen";
import type { ParkedInvite } from "@/features/share/invite-ref";

// FB-19 (#230): the sharer's nickname for the invite banner, from the anonymous
// `GET /api/v1/invites/{ref}` (#201). The nickname is display text only: it never stands in for
// the ref, which still rides on the JOIN intent from sessionStorage. One try and no refetch: a
// failed or rate-limited (429) call leaves the generic lead (the loader's failure is not retried
// when the banner mounts), and a nickname does not change while the buyer is on the page.
export const inviteSharerOptions = (ref: string) =>
  queryOptions({
    ...resolveInviteOptions({ path: { ref } }),
    select: (r) => unwrapResult(r, { allowVoid: false }),
    staleTime: "static",
    retry: false,
    retryOnMount: false,
  });

// The nickname to lead with, or undefined for the generic lead: none to show (null or blank), or
// a ref that resolves to a bloc other than the one the link named (a hand-edited link must not
// put one sharer's name on another's bloc).
function sharerNickname(resolved: InviteResponse | undefined, dealId: string): string | undefined {
  if (resolved?.dealId !== dealId) return undefined;
  const nickname = resolved.sharerNickname?.trim();
  return nickname || undefined;
}

export function useInviteSharer(invite: ParkedInvite | null): string | undefined {
  const { data } = useQuery({
    ...inviteSharerOptions(invite?.ref ?? ""),
    enabled: invite !== null,
  });
  return invite ? sharerNickname(data, invite.dealId) : undefined;
}
