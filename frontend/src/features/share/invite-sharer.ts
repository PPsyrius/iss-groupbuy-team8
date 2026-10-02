import { type QueryClient, queryOptions, useQuery } from "@tanstack/react-query";

import { unwrapResult } from "@/api/envelope";
import { resolveInviteOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { InviteResponse } from "@/api/generated/types.gen";
import type { ParkedInvite } from "@/features/share/invite-ref";

// FB-19 (#230): the sharer's nickname for the invite banner, from the anonymous
// `GET /api/v1/invites/{ref}` (#201). The nickname is display text only: it never stands in for
// the ref, which still rides on the JOIN intent from sessionStorage. One try and no refetch: a
// failed or rate-limited (429) call leaves the generic lead (the loader's failure is not retried
// when the banner mounts), and a nickname does not change while the buyer is on the page.
const inviteSharerOptions = (ref: string) =>
  queryOptions({
    ...resolveInviteOptions({ path: { ref } }),
    select: (r) => unwrapResult(r, { allowVoid: false }),
    staleTime: "static",
    retry: false,
    retryOnMount: false,
  });

// How long the listing loader waits for the sharer before it shows the page with the generic
// lead. The lookup itself is not aborted: if it answers later, the banner's query picks the name
// up then, so a slow resolve costs at most this much on the listing's first paint.
export const SHARER_DEADLINE_MS = 1_500;

// The loader's wait on the sharer: settles when the lookup does, when it fails (swallowed), or at
// the deadline, whichever comes first. Never rejects, so the listing never waits on an error.
export function awaitSharer(queryClient: QueryClient, ref: string): Promise<void> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const deadline = new Promise<void>((settle) => {
    timer = setTimeout(settle, SHARER_DEADLINE_MS);
  });
  const lookup = queryClient.query(inviteSharerOptions(ref)).then(
    () => undefined,
    () => undefined,
  );
  return Promise.race([lookup, deadline]).finally(() => clearTimeout(timer));
}

// The nickname to lead with, or undefined for the generic lead: none to show (null or blank), or
// a ref that resolves to a bloc or listing other than the one the link named (a hand-edited link
// must not put one sharer's name on another's bloc).
function sharerNickname(
  resolved: InviteResponse | undefined,
  invite: ParkedInvite,
): string | undefined {
  if (resolved?.dealId !== invite.dealId || resolved.productId !== invite.productId) {
    return undefined;
  }
  const nickname = resolved.sharerNickname?.trim();
  return nickname || undefined;
}

export function useInviteSharer(invite: ParkedInvite | null): string | undefined {
  const { data } = useQuery({
    ...inviteSharerOptions(invite?.ref ?? ""),
    enabled: invite !== null,
  });
  return invite ? sharerNickname(data, invite) : undefined;
}
