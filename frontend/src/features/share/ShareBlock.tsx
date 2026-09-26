import { Button, Input, Skeleton } from "@heroui/react";
import { useRef } from "react";

import { displayLink, inviteUrl, seatsToGo, shareFailure, shareMessage } from "./share-link";
import { useShareActions, useShareInvite } from "./use-share-invite";

// The §4.4 share block (`BlocBuy Buyer My Blocs.dc.html`, Bloc detail › starter and › member),
// placed on the listing for a buyer who is in the bloc it shows (UC7, #30; plan T3-05 step 2).
// Card padding 16, gap 14: heading 16/24/600 with a 14/20 helper, the link field (radius 12,
// mono 12) beside a `secondary` Copy, the suggested message on `surface-secondary`, then an
// `outline` Share (48px) where the browser has the Web Share API. The starter gets "Share your
// bloc" and the seats-to-go line; a member gets the member view's "anyone in this bloc can
// share" line, without the starter's name, which the contract does not carry.
export function ShareBlock({
  userId,
  dealId,
  productId,
  leader,
  title,
  groupPrice,
  individualPrice,
  currency,
  seatsLeft,
  closesMs,
}: {
  userId: string;
  dealId: string;
  productId: string;
  leader: boolean;
  title: string;
  groupPrice: number | undefined;
  individualPrice: number | undefined;
  currency: string;
  seatsLeft: number;
  closesMs: number | null;
}) {
  return (
    <section
      aria-labelledby="share-heading"
      className="border-border bg-surface flex flex-col gap-3.5 rounded-lg border p-4 xl:p-5"
    >
      <div className="flex flex-col gap-1">
        <h2 id="share-heading" className="text-base font-semibold">
          {leader
            ? "Share your bloc"
            : seatsLeft === 1
              ? "Help fill the last seat"
              : "Help fill this bloc"}
        </h2>
        <p className="text-sm text-zinc-600">
          {leader ? seatsToGo(seatsLeft) : "Anyone in this bloc can share the link."}
        </p>
      </div>
      <ShareInvitePanel
        userId={userId}
        dealId={dealId}
        productId={productId}
        message={{ title, groupPrice, individualPrice, currency, seatsLeft, closesMs }}
        compact={false}
      />
    </section>
  );
}

export type ShareMessageFacts = Omit<Parameters<typeof shareMessage>[0], "url">;

// The share block's body: it mints the invite when it mounts (so a caller that mounts it only
// when asked mints lazily), then shows the link field and Copy with the inline confirmation, and
// Share where the Web Share API exists. `compact` is the My blocs row's variant, after the
// artboard's member-view block (link field and Copy, no suggested-message panel, 44px Share);
// the native share sheet still gets the suggested message.
export function ShareInvitePanel({
  userId,
  dealId,
  productId,
  message: facts,
  compact,
}: {
  userId: string;
  dealId: string;
  productId: string;
  message: ShareMessageFacts;
  compact: boolean;
}) {
  const invite = useShareInvite(userId, dealId);
  const field = useRef<HTMLInputElement>(null);
  const ref = invite.data?.ref;
  const url = ref ? inviteUrl(window.location.origin, { productId, dealId, ref }) : undefined;
  const actions = useShareActions(url, () => field.current?.select());
  const message = url ? shareMessage({ ...facts, url }) : undefined;
  const failure = invite.isError ? shareFailure(invite.error) : null;

  if (failure) {
    return (
      <div role="alert" className="flex flex-col items-start gap-2">
        <p className="text-danger text-sm">{failure.message}</p>
        {failure.retry ? (
          <Button
            variant="outline"
            className="min-h-11 px-4"
            onPress={() => {
              void invite.refetch();
            }}
          >
            Try again
          </Button>
        ) : null}
      </div>
    );
  }
  if (!url || !message) {
    return (
      <div aria-busy="true" className="flex gap-2">
        <Skeleton className="h-11 flex-1 rounded-xl" />
        <Skeleton className="h-11 w-24 rounded-lg" />
      </div>
    );
  }
  return (
    <>
      <div className="flex gap-2">
        <Input
          ref={field}
          readOnly
          aria-label="Invite link"
          value={displayLink(url)}
          onFocus={(e) => e.currentTarget.select()}
          className="min-h-11 min-w-0 flex-1 rounded-xl font-mono text-xs text-zinc-600"
        />
        <Button
          variant="secondary"
          className="text-accent-soft-foreground min-h-11 shrink-0 px-3.5 text-sm font-semibold"
          onPress={() => {
            void actions.copy();
          }}
        >
          <CopyIcon />
          Copy
        </Button>
      </div>
      <p aria-live="polite" className="-mt-1.5 text-xs text-zinc-600 empty:hidden">
        {actions.copyState === "copied"
          ? "Invite link copied"
          : actions.copyState === "manual"
            ? "Copy didn’t work here. The link is selected so you can copy it yourself."
            : ""}
      </p>
      {compact ? null : (
        <div className="bg-surface-secondary flex flex-col gap-1.5 rounded-lg px-3.5 py-3">
          <span className="text-xs text-zinc-600">Suggested message</span>
          <p className="text-sm text-pretty">“{message}”</p>
        </div>
      )}
      {actions.canShare ? (
        <Button
          variant="outline"
          className={
            compact
              ? "min-h-11 w-full text-sm font-semibold"
              : "min-h-12 w-full text-base font-semibold"
          }
          onPress={() => {
            void actions.share({ title: facts.title, text: message });
          }}
        >
          <ShareIcon />
          Share
        </Button>
      ) : null}
    </>
  );
}

// `CopyStyleRegularKeywordsDuplicate` and `ArrowUpRightFromSquareStyleRegularKeywordsExportExternal`
// at 16px (design-screens §6), drawn on the icon set's 16×16 grid.
function CopyIcon() {
  return (
    <svg aria-hidden="true" width="16" height="16" viewBox="0 0 16 16" fill="none">
      <rect x="5.5" y="5.5" width="8" height="8" rx="1.5" stroke="currentColor" strokeWidth="1.5" />
      <path
        d="M10.5 3.5v-.25A1.25 1.25 0 0 0 9.25 2h-5.5A1.25 1.25 0 0 0 2.5 3.25v5.5A1.25 1.25 0 0 0 3.75 10h.25"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />
    </svg>
  );
}

export function ShareIcon() {
  return (
    <svg aria-hidden="true" width="16" height="16" viewBox="0 0 16 16" fill="none">
      <path
        d="M9.5 2.5h4v4M13.5 2.5 7.5 8.5M11.5 9.5v2.75a1.25 1.25 0 0 1-1.25 1.25h-6.5A1.25 1.25 0 0 1 2.5 12.25v-6.5A1.25 1.25 0 0 1 3.75 4.5H6.5"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
